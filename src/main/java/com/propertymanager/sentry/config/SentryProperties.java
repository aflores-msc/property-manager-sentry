package com.propertymanager.sentry.config;

import java.nio.file.Path;
import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
     */
    public record Gmail(
            @NotBlank String applicationName,
            @NotNull Path credentialsFile,
            @NotNull Path tokensDir,
            @NotBlank String query,
            @Min(1) @Max(500) int maxMessages) {
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
