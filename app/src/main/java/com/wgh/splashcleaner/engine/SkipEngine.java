package com.wgh.splashcleaner.engine;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Inspects accessible UI nodes and performs at most one high-confidence action per invocation. */
public final class SkipEngine {
    private static final String TAG = "SplashSkipEngine";
    private static final int MAX_NODES = 1_800;

    private final AccessibilityService service;
    private final CandidateScorer scorer;
    private boolean gestureInFlight;

    public SkipEngine(AccessibilityService accessibilityService, CandidateScorer candidateScorer) {
        service = accessibilityService;
        scorer = candidateScorer;
    }

    public Result attempt(
            String targetPackage,
            boolean sensitiveApp,
            boolean allowGesture,
            GestureCallback callback) {
        if (gestureInFlight) {
            return Result.NONE;
        }
        NodeCandidate candidate = findBestCandidate(targetPackage, sensitiveApp);
        if (candidate == null) {
            return Result.NONE;
        }

        try {
            if (candidate.actionNode != null
                    && candidate.actionNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.i(TAG, "Skipped splash with accessibility action; score="
                        + candidate.score.getValue());
                return Result.CLICKED;
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Accessibility click failed", error);
        }

        if (!allowGesture
                || Build.VERSION.SDK_INT < Build.VERSION_CODES.N
                || !candidate.score.isGestureEligible()) {
            return Result.NONE;
        }

        int centerX = candidate.bounds.centerX();
        int centerY = candidate.bounds.centerY();
        Path tapPath = new Path();
        tapPath.moveTo(centerX, centerY);
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(tapPath, 0L, 45L))
                .build();
        gestureInFlight = true;
        boolean accepted = service.dispatchGesture(
                gesture,
                new AccessibilityService.GestureResultCallback() {
                    @Override
                    public void onCompleted(GestureDescription gestureDescription) {
                        gestureInFlight = false;
                        Log.i(TAG, "Skipped splash with gesture; score=" + candidate.score.getValue());
                        callback.onFinished(true, candidate.score.getReason());
                    }

                    @Override
                    public void onCancelled(GestureDescription gestureDescription) {
                        gestureInFlight = false;
                        callback.onFinished(false, candidate.score.getReason());
                    }
                },
                null);
        if (!accepted) {
            gestureInFlight = false;
            return Result.NONE;
        }
        return Result.GESTURE_STARTED;
    }

    public boolean isGestureInFlight() {
        return gestureInFlight;
    }

    private NodeCandidate findBestCandidate(String targetPackage, boolean sensitiveApp) {
        List<AccessibilityNodeInfo> roots = collectRoots();
        NodeCandidate best = null;
        int visited = 0;
        DisplayMetrics metrics = service.getResources().getDisplayMetrics();
        int screenWidth = Math.max(1, metrics.widthPixels);
        int screenHeight = Math.max(1, metrics.heightPixels);

        for (AccessibilityNodeInfo root : roots) {
            if (root == null || !belongsToTarget(root, targetPackage)) {
                continue;
            }
            ArrayDeque<AccessibilityNodeInfo> queue = new ArrayDeque<>();
            queue.add(root);
            while (!queue.isEmpty() && visited < MAX_NODES) {
                AccessibilityNodeInfo node = queue.removeFirst();
                visited++;

                for (int index = 0; index < node.getChildCount(); index++) {
                    AccessibilityNodeInfo child = node.getChild(index);
                    if (child != null) {
                        queue.addLast(child);
                    }
                }

                if (!belongsToTarget(node, targetPackage)) {
                    continue;
                }

                AccessibilityNodeInfo actionNode = findActionNode(node);
                Rect bounds = visibleBounds(node, actionNode, screenWidth, screenHeight);
                if (bounds.isEmpty()) {
                    continue;
                }

                CandidateSignals signals = CandidateSignals.builder()
                        .text(characters(node.getText()))
                        .contentDescription(characters(node.getContentDescription()))
                        .viewId(node.getViewIdResourceName())
                        .className(characters(node.getClassName()))
                        .clickable(actionNode != null)
                        .enabled(node.isEnabled())
                        .visible(node.isVisibleToUser())
                        .hasAdContext(hasNearbyAdContext(node))
                        .bounds(
                                bounds.left / (float) screenWidth,
                                bounds.top / (float) screenHeight,
                                bounds.right / (float) screenWidth,
                                bounds.bottom / (float) screenHeight)
                        .build();
                CandidateScorer.Score score = scorer.score(signals, sensitiveApp);
                if (!score.shouldClick()) {
                    continue;
                }
                if (best == null || score.getValue() > best.score.getValue()) {
                    best = new NodeCandidate(actionNode, bounds, score);
                }
            }
            if (visited >= MAX_NODES) {
                Log.w(TAG, "Node scan limit reached");
                break;
            }
        }
        return best;
    }

    private List<AccessibilityNodeInfo> collectRoots() {
        List<AccessibilityNodeInfo> result = new ArrayList<>();
        Set<Integer> windowIds = new HashSet<>();
        AccessibilityNodeInfo activeRoot = service.getRootInActiveWindow();
        if (activeRoot != null) {
            result.add(activeRoot);
            windowIds.add(activeRoot.getWindowId());
        }
        try {
            List<AccessibilityWindowInfo> windows = service.getWindows();
            for (AccessibilityWindowInfo window : windows) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && windowIds.add(root.getWindowId())) {
                    result.add(root);
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to retrieve interactive windows", error);
        }
        return result;
    }

    private static boolean belongsToTarget(AccessibilityNodeInfo node, String targetPackage) {
        CharSequence owner = node.getPackageName();
        return owner == null || targetPackage.contentEquals(owner);
    }

    private static AccessibilityNodeInfo findActionNode(AccessibilityNodeInfo source) {
        AccessibilityNodeInfo current = source;
        for (int depth = 0; current != null && depth <= 3; depth++) {
            boolean hasClickAction = (current.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0;
            if (current.isEnabled() && (current.isClickable() || hasClickAction)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static Rect visibleBounds(
            AccessibilityNodeInfo source,
            AccessibilityNodeInfo actionNode,
            int width,
            int height) {
        Rect result = new Rect();
        source.getBoundsInScreen(result);
        if (result.isEmpty() && actionNode != null) {
            actionNode.getBoundsInScreen(result);
        }
        if (!result.intersect(0, 0, width, height)) {
            result.setEmpty();
        }
        return result;
    }

    private static boolean hasNearbyAdContext(AccessibilityNodeInfo node) {
        if (looksLikeAd(node)) {
            return true;
        }
        AccessibilityNodeInfo current = node.getParent();
        for (int depth = 0; current != null && depth < 3; depth++) {
            if (looksLikeAd(current)) {
                return true;
            }
            int siblingLimit = Math.min(current.getChildCount(), 12);
            for (int index = 0; index < siblingLimit; index++) {
                AccessibilityNodeInfo sibling = current.getChild(index);
                if (sibling != null && looksLikeAd(sibling)) {
                    return true;
                }
            }
            current = current.getParent();
        }
        return false;
    }

    private static boolean looksLikeAd(AccessibilityNodeInfo node) {
        String value = (characters(node.getText()) + " "
                + characters(node.getContentDescription()) + " "
                + characters(node.getViewIdResourceName())).toLowerCase(Locale.ROOT);
        return value.contains("广告")
                || value.contains("廣告")
                || value.contains("推广")
                || value.contains("推廣")
                || value.contains("splash")
                || value.contains("advert")
                || value.contains("native_ad")
                || value.contains("ad_container")
                || value.contains("ad_layout")
                || value.contains("ad_logo")
                || value.contains("gdt_splash")
                || value.contains("ksad_splash");
    }

    private static String characters(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static final class NodeCandidate {
        private final AccessibilityNodeInfo actionNode;
        private final Rect bounds;
        private final CandidateScorer.Score score;

        private NodeCandidate(
                AccessibilityNodeInfo action,
                Rect nodeBounds,
                CandidateScorer.Score candidateScore) {
            actionNode = action;
            bounds = new Rect(nodeBounds);
            score = candidateScore;
        }
    }

    public enum Result {
        NONE,
        CLICKED,
        GESTURE_STARTED
    }

    public interface GestureCallback {
        void onFinished(boolean success, String reason);
    }
}
