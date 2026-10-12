package com.propertymanager.sentry.config;

import java.nio.file.Path;
import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed view of everything under {@code sentry.*} in {@code application.yml}.
 *
 * @param dataDir directory holding the user's config, OAuth files and database
 * @param gmail   Gmail fetch settings
 * @param triage  AI triage settings
 */
@Validated
@ConfigurationProperties("sentry")
public record SentryProperties(
        @NotNull Path dataDir,
        @Valid @NotNull Gmail gmail,
        @Valid @NotNull Triage triage) {

    /**
     * @param applicationName name reported to Google APIs
     * @param credentialsFile OAuth client secrets downloaded from Google Cloud Console
     * @param tokensDir       where the OAuth refresh token is stored
     * @param query           Gmail search query selecting the messages to triage
     * @param maxMessages     upper bound on messages fetched per sync, so a huge unread
     *                        backlog can't trigger hundreds of LLM calls at once
     * @param labels          whether and how triaged emails are labeled in Gmail
     */
    public record Gmail(
            @NotBlank String applicationName,
            @NotNull Path credentialsFile,
            @NotNull Path tokensDir,
            @NotBlank String query,
            @Min(1) @Max(500) int maxMessages,
            @Valid @NotNull Labels labels) {
    }

    /**
     * @param enabled label triaged emails in Gmail and leave labeled ones out of later syncs.
     *                Needs the {@code gmail.modify} scope; when false the app stays read-only.
     * @param prefix  parent label, e.g. {@code Sentry} gives {@code Sentry/Processed},
     *                {@code Sentry/Maintenance}, ...
     */
    public record Labels(
            boolean enabled,
            @NotBlank @Pattern(regexp = "[^/]+", message = "must not contain '/'") String prefix) {
    }

    /**
     * @param concurrency  how many emails are triaged in parallel
     * @param maxBodyChars email bodies are truncated to this length before being sent to the LLM
     * @param timeout      upper bound for triaging a single email
     */
    public record Triage(
            @Min(1) @Max(16) int concurrency,
            @Min(500) int maxBodyChars,
            @NotNull Duration timeout) {
    }
}
