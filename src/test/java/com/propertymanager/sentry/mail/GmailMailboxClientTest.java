package com.propertymanager.sentry.mail;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.api.services.gmail.Gmail;
import com.propertymanager.sentry.config.SentryProperties;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

/** Drives the real Gmail client against a fake HTTP transport and checks the API calls it makes. */
class GmailMailboxClientTest {

    private final FakeGmailApi api = new FakeGmailApi();

    @Test
    void queryExcludesProcessedLabelWhenLabelsAreEnabled() throws Exception {
        api.reply("/messages?", "{\"messages\":[{\"id\":\"a\"},{\"id\":\"b\"}]}");

        List<String> ids = client(true).listCandidateIds();

        assertThat(ids).containsExactly("a", "b");
        assertThat(api.decodedUrls())
                .anyMatch(url -> url.contains("q=(in:inbox is:unread) -label:sentry-processed"));
    }

    @Test
    void queryIsUnchangedWhenLabelsAreDisabled() {
        assertThat(client(false).effectiveQuery()).isEqualTo("in:inbox is:unread");
    }

    @Test
    void createsMissingLabelsOnceThenBatchModifies() throws Exception {
        api.reply(
                "GET /labels",
                "{\"labels\":[{\"id\":\"INBOX\",\"name\":\"INBOX\"},{\"id\":\"L0\",\"name\":\"Sentry\"}]}");
        api.reply("POST /labels", "{\"id\":\"NEW\"}");
        api.reply("/messages/batchModify", "");
        var client = client(true);

        client.markTriaged(Set.of("m1"), "City Notice");
        client.markTriaged(Set.of("m2"), "City Notice");

        List<String> created =
                api.requests.stream()
                        .filter(r -> r.startsWith("POST") && r.contains("/labels "))
                        .toList();
        assertThat(created)
                .hasSize(2); // Sentry/Processed and Sentry/City Notice; parent "Sentry" already
        // existed
        assertThat(created)
                .anyMatch(r -> r.contains("\"name\":\"Sentry/Processed\""))
                .anyMatch(r -> r.contains("\"name\":\"Sentry/City Notice\""));
        List<String> modifies =
                api.requests.stream().filter(r -> r.contains("batchModify")).toList();
        assertThat(modifies).hasSize(2);
        assertThat(modifies.getFirst())
                .contains("\"ids\":[\"m1\"]", "\"addLabelIds\":[\"NEW\",\"NEW\"]");
        assertThat(api.requests)
                .filteredOn(r -> r.startsWith("GET") && r.contains("/labels"))
                .hasSize(1);
    }

    @Test
    void createsParentLabelWhenMissing() throws Exception {
        api.reply("GET /labels", "{\"labels\":[]}");
        api.reply("POST /labels", "{\"id\":\"NEW\"}");
        api.reply("/messages/batchModify", "");

        client(true).markTriaged(Set.of("m1"), null);

        assertThat(api.requests)
                .filteredOn(r -> r.startsWith("POST") && r.contains("/labels "))
                .satisfiesExactly(
                        parent -> assertThat(parent).contains("\"name\":\"Sentry\""),
                        child -> assertThat(child).contains("\"name\":\"Sentry/Processed\""));
    }

    @Test
    void markTriagedDoesNothingWhenLabelsAreDisabled() throws Exception {
        client(false).markTriaged(Set.of("m1"), "Maintenance");

        assertThat(api.requests).isEmpty();
    }

    @Test
    void searchNameFollowsGmailConventions() {
        assertThat(GmailMailboxClient.searchName("Sentry/City Notice"))
                .isEqualTo("sentry-city-notice");
    }

    private GmailMailboxClient client(boolean labels) {
        var gmailSettings =
                new SentryProperties.Gmail(
                        "test",
                        Path.of("credentials.json"),
                        Path.of("tokens"),
                        "in:inbox is:unread",
                        50,
                        new SentryProperties.Labels(labels, "Sentry"));
        var properties =
                new SentryProperties(
                        Path.of("."),
                        gmailSettings,
                        new SentryProperties.Triage(1, 1000, Duration.ofSeconds(10)));
        var gmail =
                new Gmail.Builder(api, GsonFactory.getDefaultInstance(), null)
                        .setApplicationName("test")
                        .build();
        return new GmailMailboxClient(properties, gmail);
    }

    /** Answers by matching "METHOD path" against registered prefixes and records every request. */
    static final class FakeGmailApi extends MockHttpTransport {

        final List<String> requests = new CopyOnWriteArrayList<>();
        private final List<String[]> replies = new CopyOnWriteArrayList<>();

        void reply(String match, String json) {
            replies.add(new String[] {match, json});
        }

        List<String> decodedUrls() {
            return requests.stream()
                    .map(r -> URLDecoder.decode(r, StandardCharsets.UTF_8))
                    .toList();
        }

        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) {
            return new MockLowLevelHttpRequest(url) {
                @Override
                public LowLevelHttpResponse execute() throws java.io.IOException {
                    String path = url.replaceFirst("^https://[^/]+/gmail/v1/users/me", "");
                    requests.add(method + " " + path + " " + getContentAsString());
                    String json =
                            replies.stream()
                                    .filter(r -> (method + " " + path).contains(r[0]))
                                    .map(r -> r[1])
                                    .findFirst()
                                    .orElse("{}");
                    return new MockLowLevelHttpResponse()
                            .setContentType("application/json")
                            .setContent(json);
                }
            };
        }
    }
}
