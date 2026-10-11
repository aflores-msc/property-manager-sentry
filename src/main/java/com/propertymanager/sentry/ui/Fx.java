package com.propertymanager.sentry.ui;

import java.util.concurrent.Callable;
import java.util.function.Consumer;

import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs blocking work (database, network) off the JavaFX Application Thread, then hands the result
 * back to it. The JavaFX equivalent of the QThread workers in the original Python app, in a few lines.
 */
final class Fx {

    private static final Logger log = LoggerFactory.getLogger(Fx.class);

    private Fx() {
    }

    static <T> void background(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Thread.ofVirtual().name("fx-background").start(() -> {
            try {
                T result = work.call();
                Platform.runLater(() -> onSuccess.accept(result));
            } catch (Throwable e) {
                log.warn("Background task failed", e);
                Platform.runLater(() -> onFailure.accept(e));
            }
        });
    }

    static void background(Runnable work, Consumer<Throwable> onFailure) {
        background(() -> {
            work.run();
            return null;
        }, _ -> { }, onFailure);
    }
}
