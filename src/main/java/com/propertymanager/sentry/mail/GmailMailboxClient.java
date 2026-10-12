package com.propertymanager.sentry.mail;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.BatchModifyMessagesRequest;
import com.google.api.services.gmail.model.Label;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.propertymanager.sentry.config.SentryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Gmail access through the official Java client, authorized with the OAuth2 "installed app"
 * flow: the first call opens the browser for consent, then the refresh token is kept in
 * {@code sentry.gmail.tokens-dir} for every later run.
 *
 * <p>With {@code sentry.gmail.labels.enabled} (the default) the app asks for the
 * {@code gmail.modify} scope and labels every triaged message, e.g. {@code Sentry/Processed} and
 * {@code Sentry/Maintenance}. The search query excludes {@code Sentry/Processed}, so Gmail only
 * returns mail the app hasn't seen. Labels are the only change the app makes: it never marks
 * messages read, moves, deletes or sends anything. With labels disabled it requests only
 * {@code gmail.readonly}, and de-duplication relies on the local {@code processed_email} table alone
 * (which is kept either way as a safety net).
 */
@Component
public class GmailMailboxClient implements MailboxClient {

    private static final Logger log = LoggerFactory.getLogger(GmailMailboxClient.class);
    private static final JsonFactory JSON = GsonFactory.getDefaultInstance();
    private static final String USER = "me";
    private static final String PROCESSED = "Processed";
    /** Gmail's limit for ids in one batchModify call. */
    private static final int BATCH_LIMIT = 1000;

    private final SentryProperties.Gmail settings;
    // Plain JDK HTTP transport: honors the JVM's trust store and proxy settings.
    private final NetHttpTransport transport = new NetHttpTransport();
    /** Label name (lower case: Gmail names are case-insensitive) to label id. Guarded by {@code this}. */
    private final Map<String, String> labelIds = new HashMap<>();
    private volatile Gmail gmail;

    @Autowired
    public GmailMailboxClient(SentryProperties properties) {
        this.settings = properties.gmail();
    }

    /** For tests: use a ready-made Gmail client instead of running the OAuth flow. */
    GmailMailboxClient(SentryProperties properties, Gmail gmail) {
        this(properties);
        this.gmail = gmail;
    }

    @Override
    public List<String> listCandidateIds() throws IOException {
        var ids = new ArrayList<String>();
        String pageToken = null;
        do {
            var response = client().users().messages().list(USER)
                    .setQ(effectiveQuery())
                    .setMaxResults((long) Math.min(100, settings.maxMessages() - ids.size()))
                    .setPageToken(pageToken)
                    .execute();
            if (response.getMessages() != null) {
                response.getMessages().forEach(message -> ids.add(message.getId()));
            }
            pageToken = response.getNextPageToken();
        } while (pageToken != null && ids.size() < settings.maxMessages());
        return List.copyOf(ids);
    }

    @Override
    public InboundEmail fetch(String messageId) throws IOException {
        Message message = client().users().messages().get(USER, messageId).setFormat("full").execute();
        Map<String, String> headers = message.getPayload().getHeaders().stream()
                .collect(Collectors.toMap(h -> h.getName().toLowerCase(Locale.ROOT), MessagePartHeader::getValue, (first, second) -> first));
        Instant receivedAt = message.getInternalDate() != null ? Instant.ofEpochMilli(message.getInternalDate()) : null;
        return new InboundEmail(
                message.getId(),
                headers.get("from"),
                headers.get("subject"),
                receivedAt,
                EmailBodyExtractor.extractText(message.getPayload()));
    }

    @Override
    public void markTriaged(Collection<String> messageIds, String category) throws IOException {
        if (!settings.labels().enabled() || messageIds.isEmpty()) {
            return;
        }
        var addLabelIds = new ArrayList<String>();
        addLabelIds.add(labelId(labelName(PROCESSED)));
        if (category != null) {
            addLabelIds.add(labelId(labelName(category)));
        }
        List<String> ids = List.copyOf(messageIds);
        for (int from = 0; from < ids.size(); from += BATCH_LIMIT) {
            var request = new BatchModifyMessagesRequest()
                    .setIds(ids.subList(from, Math.min(ids.size(), from + BATCH_LIMIT)))
                    .setAddLabelIds(addLabelIds);
            client().users().messages().batchModify(USER, request).execute();
        }
    }

    /** The configured query, minus messages already labeled as processed. */
    String effectiveQuery() {
        if (!settings.labels().enabled()) {
            return settings.query();
        }
        return "(" + settings.query() + ") -label:" + searchName(labelName(PROCESSED));
    }

    private String labelName(String child) {
        return settings.labels().prefix() + "/" + child;
    }

    /**
     * How Gmail search refers to a label: lower case, with spaces and the nesting slash written as
     * dashes ({@code Sentry/City Notice} is {@code sentry-city-notice}).
     */
    static String searchName(String labelName) {
        return labelName.toLowerCase(Locale.ROOT).replaceAll("[/\\s]+", "-");
    }

    /** Id of the label with this name, creating it (and its parent) the first time it's needed. */
    private synchronized String labelId(String name) throws IOException {
        if (labelIds.isEmpty()) {
            var existing = client().users().labels().list(USER).execute().getLabels();
            if (existing != null) {
                existing.forEach(label -> labelIds.put(label.getName().toLowerCase(Locale.ROOT), label.getId()));
            }
        }
        String id = labelIds.get(name.toLowerCase(Locale.ROOT));
        if (id != null) {
            return id;
        }
        int slash = name.lastIndexOf('/');
        if (slash > 0) {
            labelId(name.substring(0, slash)); // Gmail shows "A/B" nested under "A" only if "A" exists
        }
        var created = client().users().labels().create(USER, new Label()
                        .setName(name)
                        .setLabelListVisibility("labelShow")
                        .setMessageListVisibility("show"))
                .execute();
        log.info("Created Gmail label {}", name);
        labelIds.put(name.toLowerCase(Locale.ROOT), created.getId());
        return created.getId();
    }

    private Gmail client() throws IOException {
        Gmail current = gmail;
        if (current == null) {
            synchronized (this) {
                if (gmail == null) {
                    gmail = new Gmail.Builder(transport, JSON, authorize())
                            .setApplicationName(settings.applicationName())
                            .build();
                }
                current = gmail;
            }
        }
        return current;
    }

    private Credential authorize() throws IOException {
        GoogleClientSecrets secrets;
        try (Reader reader = Files.newBufferedReader(settings.credentialsFile())) {
            secrets = GoogleClientSecrets.load(JSON, reader);
        } catch (NoSuchFileException missing) {
            throw new MailboxSetupException("""
                    Gmail OAuth client file not found at %s.
                    Create an OAuth client ID of type "Desktop app" in Google Cloud Console \
                    (with the Gmail API enabled), download its JSON and save it at that path."""
                    .formatted(settings.credentialsFile()));
        }
        boolean writeAccess = settings.labels().enabled();
        var scopes = List.of(writeAccess ? GmailScopes.GMAIL_MODIFY : GmailScopes.GMAIL_READONLY);
        var flow = new GoogleAuthorizationCodeFlow.Builder(transport, JSON, secrets, scopes)
                .setDataStoreFactory(new FileDataStoreFactory(settings.tokensDir().toFile()))
                .setAccessType("offline")
                .build();
        var receiver = new LocalServerReceiver.Builder().setHost("localhost").setPort(-1).build();
        log.info("Authorizing Gmail {} access (a browser window opens if consent is needed)",
                writeAccess ? "label (modify)" : "read-only");
        // One stored token per scope: switching labels on or off asks for consent once,
        // instead of reusing a token that lacks (or has more than) the needed permission.
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize(writeAccess ? "user-modify" : "user");
    }
}
