package com.propertymanager.sentry.mail;

import com.google.api.services.gmail.model.MessagePart;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.TextNode;

/**
 * Turns a Gmail MIME tree into plain text: the first {@code text/plain} part if there is one,
 * otherwise the first {@code text/html} part converted to text with jsoup (a real HTML parser, so
 * entities, scripts and styles are handled properly).
 */
public final class EmailBodyExtractor {

    private static final Pattern CHARSET =
            Pattern.compile("charset=\"?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);

    private EmailBodyExtractor() {}

    public static String extractText(MessagePart payload) {
        if (payload == null) {
            return "";
        }
        return findPart(payload, "text/plain")
                .or(() -> findPart(payload, "text/html").map(EmailBodyExtractor::htmlToText))
                .map(String::strip)
                .orElse("");
    }

    static String htmlToText(String html) {
        var document = Jsoup.parse(html);
        document.select("script, style, head").remove();
        // Keep line structure from <br> and block elements: it helps the LLM read addresses and
        // lists.
        document.select("br").forEach(br -> br.after(new TextNode("\n")));
        document.select("p, div, li, tr, h1, h2, h3, h4, h5, h6")
                .forEach(block -> block.prependChild(new TextNode("\n")));
        return document.body()
                .wholeText()
                .replace(' ', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\n *", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .strip();
    }

    private static Optional<String> findPart(MessagePart part, String mimeType) {
        if (mimeType.equalsIgnoreCase(part.getMimeType())) {
            var body = part.getBody();
            if (body != null && body.getData() != null) {
                String text = new String(body.decodeData(), charsetOf(part));
                if (!text.isBlank()) {
                    return Optional.of(text);
                }
            }
        }
        List<MessagePart> children = part.getParts();
        if (children == null) {
            return Optional.empty();
        }
        return children.stream()
                .map(child -> findPart(child, mimeType))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Charset charsetOf(MessagePart part) {
        if (part.getHeaders() == null) {
            return StandardCharsets.UTF_8;
        }
        return part.getHeaders().stream()
                .filter(header -> "Content-Type".equalsIgnoreCase(header.getName()))
                .map(header -> CHARSET.matcher(header.getValue()))
                .filter(matcher -> matcher.find())
                .map(matcher -> matcher.group(1))
                .filter(Charset::isSupported)
                .map(Charset::forName)
                .findFirst()
                .orElse(StandardCharsets.UTF_8);
    }
}
