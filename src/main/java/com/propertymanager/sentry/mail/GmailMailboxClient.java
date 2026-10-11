package com.propertymanager.sentry.mail;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.MessagePartHeader;
import com.propertymanager.sentry.config.SentryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Gmail access through the official Java client, authorized with the OAuth2 "installed app"
 * flow: the first call opens the browser for consent, then the refresh token is kept in
 * {@code sentry.gmail.tokens-dir} for every later run.
 *
 * <p>Only the read-only scope is requested; the app never modifies the mailbox. That's why
 * de-duplication is done locally (see {@code ProcessedEmail}) instead of marking messages read.
 */
@Component
public class GmailMailboxClient implements MailboxClient {

    private static final Logger log = LoggerFactory.getLogger(GmailMailboxClient.class);
    private static final JsonFactory JSON = GsonFactory.getDefaultInstance();
    private static final List<String> SCOPES = List.of(GmailScopes.GMAIL_READONLY);
    private static final String USER = "me";

    private final SentryProperties.Gmail settings;
    // Plain JDK HTTP transport: honors the JVM's trust store and proxy settings.
    private final NetHttpTransport transport = new NetHttpTransport();
    private volatile Gmail gmail;

    public GmailMailboxClient(SentryProperties properties) {
        this.settings = properties.gmail();
    }

    @Override
    public List<String> listCandidateIds() throws IOException {
        var ids = new ArrayList<String>();
        String pageToken = null;
        do {
            var response = client().users().messages().list(USER)
                    .setQ(settings.query())
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
                .collect(Collectors.toMap(h -> h.getName().toLowerCase(), MessagePartHeader::getValue, (first, second) -> first));
        Instant receivedAt = message.getInternalDate() != null ? Instant.ofEpochMilli(message.getInternalDate()) : null;
        return new InboundEmail(
                message.getId(),
                headers.get("from"),
                headers.get("subject"),
                receivedAt,
                EmailBodyExtractor.extractText(message.getPayload()));
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
        var flow = new GoogleAuthorizationCodeFlow.Builder(transport, JSON, secrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(settings.tokensDir().toFile()))
                .setAccessType("offline")
                .build();
        var receiver = new LocalServerReceiver.Builder().setHost("localhost").setPort(-1).build();
        log.info("Authorizing Gmail access (a browser window opens on first use)");
        return new AuthorizationCodeInstalledApp(flow, receiver).authorize("user");
    }
}
