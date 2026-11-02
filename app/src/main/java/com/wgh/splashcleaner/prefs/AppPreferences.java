package com.wgh.splashcleaner.prefs;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

public final class AppPreferences {
    private static final String FILE_NAME = "splash_cleaner_preferences";
    private static final String KEY_MASTER_ENABLED = "master_enabled";
    private static final String KEY_GESTURE_ENABLED = "gesture_enabled";
    private static final String KEY_POPUP_ADS_ENABLED = "popup_ads_enabled";
    private static final String KEY_SCAN_WINDOW_MS = "scan_window_ms";
    private static final String KEY_IGNORED_PACKAGES = "ignored_packages";
    private static final String KEY_TOTAL_SKIPS = "total_skips";
    private static final String KEY_LAST_PACKAGE = "last_package";
    private static final String KEY_LAST_APP_LABEL = "last_app_label";
    private static final String KEY_LAST_REASON = "last_reason";
    private static final String KEY_LAST_METHOD = "last_method";
    private static final String KEY_LAST_TIME = "last_time";

    public static final int MIN_SCAN_WINDOW_MS = 4_000;
    public static final int MAX_SCAN_WINDOW_MS = 15_000;
    public static final int DEFAULT_SCAN_WINDOW_MS = 10_000;

    private final SharedPreferences preferences;

    public AppPreferences(Context context) {
        preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public boolean isMasterEnabled() {
        return preferences.getBoolean(KEY_MASTER_ENABLED, true);
    }

    public void setMasterEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_MASTER_ENABLED, enabled).apply();
    }

    public boolean isGestureEnabled() {
        return preferences.getBoolean(KEY_GESTURE_ENABLED, true);
    }

    public void setGestureEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_GESTURE_ENABLED, enabled).apply();
    }

    public boolean isPopupAdsEnabled() {
        return preferences.getBoolean(KEY_POPUP_ADS_ENABLED, true);
    }

    public void setPopupAdsEnabled(boolean enabled) {
        preferences.edit().putBoolean(KEY_POPUP_ADS_ENABLED, enabled).apply();
    }

    public int getScanWindowMillis() {
        return clampWindow(preferences.getInt(KEY_SCAN_WINDOW_MS, DEFAULT_SCAN_WINDOW_MS));
    }

    public void setScanWindowMillis(int milliseconds) {
        preferences.edit().putInt(KEY_SCAN_WINDOW_MS, clampWindow(milliseconds)).apply();
    }

    private static int clampWindow(int milliseconds) {
        return Math.max(MIN_SCAN_WINDOW_MS, Math.min(MAX_SCAN_WINDOW_MS, milliseconds));
    }

    public boolean isPackageIgnored(String packageName) {
        return getIgnoredPackages().contains(packageName);
    }

    public Set<String> getIgnoredPackages() {
        Set<String> stored = preferences.getStringSet(KEY_IGNORED_PACKAGES, null);
        return stored == null ? new HashSet<>() : new HashSet<>(stored);
    }

    public void setPackageIgnored(String packageName, boolean ignored) {
        Set<String> packages = getIgnoredPackages();
        if (ignored) {
            packages.add(packageName);
        } else {
            packages.remove(packageName);
        }
        preferences.edit().putStringSet(KEY_IGNORED_PACKAGES, packages).apply();
    }

    public void recordSkip(String packageName, String appLabel, String reason, String method, long time) {
        int total = preferences.getInt(KEY_TOTAL_SKIPS, 0);
        preferences.edit()
                .putInt(KEY_TOTAL_SKIPS, total + 1)
                .putString(KEY_LAST_PACKAGE, packageName)
                .putString(KEY_LAST_APP_LABEL, appLabel)
                .putString(KEY_LAST_REASON, reason)
                .putString(KEY_LAST_METHOD, method)
                .putLong(KEY_LAST_TIME, time)
                .apply();
    }

    public int getTotalSkips() {
        return preferences.getInt(KEY_TOTAL_SKIPS, 0);
    }

    public String getLastPackage() {
        return preferences.getString(KEY_LAST_PACKAGE, "");
    }

    public String getLastAppLabel() {
        return preferences.getString(KEY_LAST_APP_LABEL, "");
    }

    public String getLastReason() {
        return preferences.getString(KEY_LAST_REASON, "");
    }

    public String getLastMethod() {
        return preferences.getString(KEY_LAST_METHOD, "");
    }

    public long getLastTime() {
        return preferences.getLong(KEY_LAST_TIME, 0L);
    }
}
