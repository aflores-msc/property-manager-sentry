package com.propertymanager.sentry;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot configuration root. It is started by the JavaFX application ({@code
 * SentryFxApplication#init}) rather than by its own {@code main}, so the Spring context lives
 * exactly as long as the desktop window.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class SentryApplication {}
