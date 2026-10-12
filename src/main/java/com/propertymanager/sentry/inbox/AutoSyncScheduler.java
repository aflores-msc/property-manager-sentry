package com.propertymanager.sentry.inbox;

import com.propertymanager.sentry.config.UserPreferences;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * Runs a sync every N minutes, where N comes from {@link UserPreferences} and can be changed at
 * runtime from the settings dialog. Uses Spring's {@link TaskScheduler} directly because the
 * interval isn't known at compile time (so {@code @Scheduled} doesn't fit).
 */
@Component
public class AutoSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(AutoSyncScheduler.class);

    private final TaskScheduler scheduler;
    private final InboxSyncService syncService;
    private final UserPreferences preferences;
    private ScheduledFuture<?> task;

    public AutoSyncScheduler(
            TaskScheduler scheduler, InboxSyncService syncService, UserPreferences preferences) {
        this.scheduler = scheduler;
        this.syncService = syncService;
        this.preferences = preferences;
    }

    @EventListener(ContextRefreshedEvent.class)
    void onStartup() {
        apply(preferences.autoSyncMinutes());
    }

    /** Saves the new interval and applies it immediately; 0 turns auto-sync off. */
    public void reschedule(int minutes) {
        preferences.setAutoSyncMinutes(minutes);
        apply(minutes);
    }

    private synchronized void apply(int minutes) {
        if (task != null) {
            task.cancel(false);
            task = null;
        }
        if (minutes > 0) {
            Duration interval = Duration.ofMinutes(minutes);
            task =
                    scheduler.scheduleWithFixedDelay(
                            () -> syncService.startSync(SyncTrigger.SCHEDULED).join(),
                            Instant.now().plus(interval),
                            interval);
            log.info("Auto-sync every {} minute(s)", minutes);
        } else {
            log.info("Auto-sync disabled");
        }
    }
}
