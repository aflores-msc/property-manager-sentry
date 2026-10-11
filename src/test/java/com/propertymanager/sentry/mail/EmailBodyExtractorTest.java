package com.propertymanager.sentry.mail;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import com.google.api.services.gmail.model.MessagePart;
import com.google.api.services.gmail.model.MessagePartBody;
import com.google.api.services.gmail.model.MessagePartHeader;
import org.junit.jupiter.api.Test;

class EmailBodyExtractorTest {

    @Test
    void prefersPlainTextOverHtml() {
        var payload = multipart(
                part("text/html", "<p>HTML version</p>"),
                part("text/plain", "Plain version"));

        assertThat(EmailBodyExtractor.extractText(payload)).isEqualTo("Plain version");
    }

    @Test
    void fallsBackToHtmlWithEntitiesDecodedAndScriptsRemoved() {
        var payload = multipart(part("text/html", """
                <html><head><style>p { color: red }</style></head><body>
                <script>alert('x')</script>
                <p>Water leak in unit&nbsp;4B &amp; hallway</p><p>Call me at 555-0100</p>
                </body></html>"""));

        String text = EmailBodyExtractor.extractText(payload);

        assertThat(text).isEqualTo("Water leak in unit 4B & hallway\nCall me at 555-0100");
    }

    @Test
    void findsPartsNestedInMultipartAlternative() {
        var payload = multipart(
                multipart(part("text/plain", "Nested body")),
                part("application/pdf", "binary"));

        assertThat(EmailBodyExtractor.extractText(payload)).isEqualTo("Nested body");
    }

    @Test
    void decodesDeclaredCharset() {
        var latin1 = Charset.forName("ISO-8859-1");
        var payload = part("text/plain", "Señor García", latin1)
                .setHeaders(List.of(new MessagePartHeader().setName("Content-Type").setValue("text/plain; charset=\"ISO-8859-1\"")));

        assertThat(EmailBodyExtractor.extractText(payload)).isEqualTo("Señor García");
    }

    @Test
    void returnsEmptyWhenThereIsNoText() {
        assertThat(EmailBodyExtractor.extractText(multipart(part("image/png", "png")))).isEmpty();
        assertThat(EmailBodyExtractor.extractText(null)).isEmpty();
    }

    private static MessagePart multipart(MessagePart... parts) {
        return new MessagePart().setMimeType("multipart/mixed").setParts(List.of(parts));
    }

    private static MessagePart part(String mimeType, String content) {
        return part(mimeType, content, StandardCharsets.UTF_8);
    }

    private static MessagePart part(String mimeType, String content, Charset charset) {
        String data = Base64.getUrlEncoder().encodeToString(content.getBytes(charset));
        return new MessagePart().setMimeType(mimeType).setBody(new MessagePartBody().setData(data));
    }
}
