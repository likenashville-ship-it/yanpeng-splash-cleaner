package com.wgh.splashcleaner.engine;

/** Tracks one foreground-app launch and prevents repeated or late clicks. */
public final class LaunchSession {
    private String packageName;
    private long startedAt;
    private boolean actionPerformed;

    public void begin(String nextPackage, long now) {
        packageName = nextPackage;
        startedAt = now;
        actionPerformed = false;
    }

    public void clear() {
        packageName = null;
        startedAt = 0L;
        actionPerformed = false;
    }

    public boolean isFor(String candidatePackage) {
        return packageName != null && packageName.equals(candidatePackage);
    }

    public boolean canScan(String candidatePackage, long now, long scanWindowMillis) {
        return !actionPerformed
                && isWithinWindow(candidatePackage, now, scanWindowMillis);
    }

    public boolean isWithinWindow(String candidatePackage, long now, long scanWindowMillis) {
        return isFor(candidatePackage)
                && now >= startedAt
                && now - startedAt <= scanWindowMillis;
    }

    public void markActionPerformed() {
        actionPerformed = true;
    }

    public String getPackageName() {
        return packageName;
    }

    public long getStartedAt() {
        return startedAt;
    }
}
