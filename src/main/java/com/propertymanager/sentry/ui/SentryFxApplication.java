package com.propertymanager.sentry.ui;

import atlantafx.base.theme.PrimerDark;
import com.propertymanager.sentry.SentryApplication;
import com.propertymanager.sentry.inbox.SyncEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.PayloadApplicationEvent;

/**
 * JavaFX application that owns the Spring context.
 *
 * <ul>
 *   <li>{@link #init()} runs on the JavaFX launcher thread, before any window exists, and starts
 *       Spring Boot (database, migrations, Gemini client, scheduler).</li>
 *   <li>{@link #start(Stage)} runs on the JavaFX Application Thread and has Spring build the
 *       {@link MainWindow}.</li>
 *   <li>{@link #stop()} closes the Spring context when the window closes.</li>
 * </ul>
 */
public class SentryFxApplication extends Application {

    private ConfigurableApplicationContext context;
    private Throwable startupFailure;

    @Override
    public void init() {
        try {
            context = new SpringApplicationBuilder(SentryApplication.class)
                    .headless(false) // the OAuth flow opens the system browser through java.awt.Desktop
                    .run(getParameters().getRaw().toArray(String[]::new));
        } catch (Throwable e) {
            startupFailure = e;
        }
    }

    @Override
    public void start(Stage stage) {
        if (startupFailure != null) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            showStartupError(startupFailure);
            Platform.exit();
            return;
        }
        MainWindow window = context.getAutowireCapableBeanFactory().createBean(MainWindow.class);
        // Forward sync progress (published as Spring events by InboxSyncService) to the window.
        context.addApplicationListener((ApplicationEvent event) -> {
            if (event instanceof PayloadApplicationEvent<?> payload && payload.getPayload() instanceof SyncEvent sync) {
                window.onSyncEvent(sync);
            }
        });
        window.show(stage);
    }

    @Override
    public void stop() {
        if (context != null) {
            context.close();
        }
    }

    private static void showStartupError(Throwable error) {
        Throwable root = error;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        var alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Property Manager Sentry");
        alert.setHeaderText("Sentry could not start");
        alert.setContentText("""
                Check ~/.property-manager-sentry/application.yml (see the README).
                The most common cause is a missing Gemini API key: set spring.ai.google.genai.api-key \
                there, or the GOOGLE_API_KEY environment variable.""");
        var details = new TextArea(root.getClass().getName() + ": " + root.getMessage());
        details.setEditable(false);
        details.setWrapText(true);
        alert.getDialogPane().setExpandableContent(details);
        alert.getDialogPane().setExpanded(true);
        alert.showAndWait();
    }
}
