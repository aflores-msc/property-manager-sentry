package com.propertymanager.sentry.config;

import java.util.prefs.Preferences;

import org.springframework.stereotype.Component;

/**
 * Small per-user settings changed from the UI, persisted with {@link Preferences} (the Java
 * equivalent of Qt's QSettings: the registry on Windows, a plist on macOS, a file on Linux).
 */
@Component
public class UserPreferences {

    private static final String AUTO_SYNC_MINUTES = "autoSyncMinutes";
    private static final String DARK_THEME = "darkTheme";

    private final Preferences node = Preferences.userRoot().node("com/propertymanager/sentry");

    /** Minutes between automatic syncs; 0 disables auto-sync. */
    public int autoSyncMinutes() {
        return Math.clamp(node.getInt(AUTO_SYNC_MINUTES, 0), 0, 1440);
    }

    public void setAutoSyncMinutes(int minutes) {
        node.putInt(AUTO_SYNC_MINUTES, Math.clamp(minutes, 0, 1440));
    }

    public boolean darkTheme() {
        return node.getBoolean(DARK_THEME, true);
    }

    public void setDarkTheme(boolean dark) {
        node.putBoolean(DARK_THEME, dark);
    }
}
