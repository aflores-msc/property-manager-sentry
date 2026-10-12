package com.propertymanager.sentry;

import com.propertymanager.sentry.ui.SentryFxApplication;
import javafx.application.Application;

/**
 * Process entry point.
 *
 * <p>JavaFX refuses to start when the main class itself extends {@link Application} and JavaFX is
 * on the classpath rather than the module path, so this plain class hands off to {@link
 * SentryFxApplication}, which in turn boots the Spring context.
 */
public final class SentryLauncher {

    private SentryLauncher() {}

    public static void main(String[] args) {
        Application.launch(SentryFxApplication.class, args);
    }
}
