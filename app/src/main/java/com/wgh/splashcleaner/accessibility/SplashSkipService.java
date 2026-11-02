package com.wgh.splashcleaner.accessibility;

import android.accessibilityservice.AccessibilityService;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import com.wgh.splashcleaner.engine.CandidateScorer;
import com.wgh.splashcleaner.engine.LaunchSession;
import com.wgh.splashcleaner.engine.PopupAdEngine;
import com.wgh.splashcleaner.engine.PopupAdRuleCatalog;
import com.wgh.splashcleaner.engine.RuleCatalog;
import com.wgh.splashcleaner.engine.SkipEngine;
import com.wgh.splashcleaner.prefs.AppPreferences;

import java.util.Set;

public final class SplashSkipService extends AccessibilityService {
    private static final String TAG = "SplashSkipService";
    private static final long[] SCAN_DELAYS_MS = {
            0L, 80L, 180L, 320L, 550L, 900L, 1_400L, 2_100L,
            3_000L, 4_200L, 5_800L, 7_600L, 9_400L, 11_500L, 14_000L
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final LaunchSession session = new LaunchSession();
    private int generation;
    private final Runnable eventScan = () -> scan(session.getPackageName(), generation);

    private AppPreferences preferences;
    private SkipEngine engine;
    private PopupAdEngine popupEngine;
    private Set<String> homePackages;

    @Override
    protected void onServiceConnected() {
        preferences = new AppPreferences(this);
        homePackages = PackageSafety.findHomePackages(this);
        CandidateScorer scorer = new CandidateScorer(RuleCatalog.load(this));
        engine = new SkipEngine(this, scorer);
        popupEngine = new PopupAdEngine(this, PopupAdRuleCatalog.load(this));
        Log.i(TAG, "Splash skip service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (preferences == null || engine == null || popupEngine == null || event == null) {
            return;
        }
        CharSequence owner = event.getPackageName();
        if (owner == null) {
            scheduleEventScan();
            return;
        }

        String packageName = owner.toString();
        if (getPackageName().equals(packageName) || homePackages.contains(packageName)) {
            clearSession();
            return;
        }
        if (PackageSafety.isSystemSurface(packageName, homePackages)) {
            return;
        }
        if (!preferences.isMasterEnabled() || preferences.isPackageIgnored(packageName)) {
            if (session.isFor(packageName)) {
                clearSession();
            }
            return;
        }

        if (!session.isFor(packageName)) {
            startSession(packageName);
        } else {
            scheduleEventScan();
        }
    }

    private void startSession(String packageName) {
        generation++;
        session.begin(packageName, SystemClock.elapsedRealtime());
        popupEngine.resetForPackage(packageName);
        int sessionGeneration = generation;
        int scanWindow = preferences.getScanWindowMillis();
        for (long delay : SCAN_DELAYS_MS) {
            if (delay > scanWindow) {
                break;
            }
            handler.postDelayed(() -> scan(packageName, sessionGeneration), delay);
        }
    }

    private void scheduleEventScan() {
        if (session.getPackageName() == null) {
            return;
        }
        handler.removeCallbacks(eventScan);
        handler.postDelayed(eventScan, 55L);
    }

    private void scan(String packageName, int expectedGeneration) {
        if (packageName == null
                || expectedGeneration != generation
                || !preferences.isMasterEnabled()
                || preferences.isPackageIgnored(packageName)
                || engine.isGestureInFlight()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        boolean sensitiveApp = PackageSafety.isSensitiveApp(this, packageName);
        boolean withinLaunchWindow = session.isWithinWindow(
                packageName,
                now,
                preferences.getScanWindowMillis());
        boolean persistentPopupRules = preferences.isPopupAdsEnabled();
        if (!sensitiveApp && (withinLaunchWindow || persistentPopupRules)) {
            PopupAdEngine.Attempt popupAttempt = popupEngine.attempt(
                    packageName,
                    now,
                    withinLaunchWindow,
                    persistentPopupRules);
            if (popupAttempt.clicked()) {
                recordPopupSuccess(packageName, popupAttempt.getRuleKey());
                handler.postDelayed(() -> scan(packageName, expectedGeneration), 280L);
                return;
            }
            if (popupAttempt.getRetryAfterMillis() > 0L) {
                handler.postDelayed(
                        () -> scan(packageName, expectedGeneration),
                        popupAttempt.getRetryAfterMillis());
            }
        }

        if (!session.canScan(packageName, now, preferences.getScanWindowMillis())) {
            return;
        }
        SkipEngine.Result result = engine.attempt(
                packageName,
                sensitiveApp,
                preferences.isGestureEnabled(),
                (success, reason) -> {
                    if (success && session.canScan(
                            packageName,
                            SystemClock.elapsedRealtime(),
                            preferences.getScanWindowMillis())) {
                        recordSuccess(packageName, reason, "触控补点");
                    }
                });
        if (result == SkipEngine.Result.CLICKED) {
            recordSuccess(packageName, "高置信度控件", "无障碍点击");
        }
    }

    private void recordSuccess(String packageName, String reason, String method) {
        session.markActionPerformed();
        String label = applicationLabel(packageName);
        preferences.recordSkip(packageName, label, reason, method, System.currentTimeMillis());
    }

    private void recordPopupSuccess(String packageName, String ruleKey) {
        String label = applicationLabel(packageName);
        preferences.recordSkip(
                packageName,
                label,
                "包级广告规则 " + ruleKey,
                "弹窗广告关闭",
                System.currentTimeMillis());
    }

    private String applicationLabel(String packageName) {
        try {
            PackageManager packageManager = getPackageManager();
            ApplicationInfo info = packageManager.getApplicationInfo(packageName, 0);
            return packageManager.getApplicationLabel(info).toString();
        } catch (PackageManager.NameNotFoundException ignored) {
            return packageName;
        }
    }

    private void clearSession() {
        generation++;
        session.clear();
        if (popupEngine != null) {
            popupEngine.resetForPackage(null);
        }
        handler.removeCallbacks(eventScan);
    }

    @Override
    public void onInterrupt() {
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }
}
