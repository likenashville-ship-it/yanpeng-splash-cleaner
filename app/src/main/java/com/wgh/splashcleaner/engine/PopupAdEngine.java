package com.wgh.splashcleaner.engine;

import android.accessibilityservice.AccessibilityService;
import android.graphics.Rect;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Applies package-bound popup-ad rules without gestures or global back actions. */
public final class PopupAdEngine {
    private static final String TAG = "PopupAdEngine";
    private static final int MAX_NODES = 1_800;
    private static final long RULE_COOLDOWN_MILLIS = 12_000L;

    private final AccessibilityService service;
    private final PopupAdRuleCatalog catalog;
    private final Map<String, Long> firstSeen = new HashMap<>();
    private final Map<String, Long> lastFired = new HashMap<>();
    private String activePackage;

    public PopupAdEngine(AccessibilityService accessibilityService, PopupAdRuleCatalog ruleCatalog) {
        service = accessibilityService;
        catalog = ruleCatalog;
    }

    public void resetForPackage(String packageName) {
        if (packageName == null || !packageName.equals(activePackage)) {
            activePackage = packageName;
            firstSeen.clear();
            lastFired.clear();
        }
    }

    public Attempt attempt(
            String packageName,
            long now,
            boolean withinLaunchWindow,
            boolean allowPersistentRules) {
        List<PopupAdRuleCatalog.Rule> rules = allowPersistentRules
                ? catalog.rulesForPackage(packageName)
                : Collections.emptyList();
        boolean hasLaunchWindowRule = withinLaunchWindow
                && PackageSpecificRules.hasLaunchWindowRule(packageName);
        if (rules.isEmpty() && !hasLaunchWindowRule) {
            return Attempt.none();
        }
        resetForPackage(packageName);
        List<NodeRecord> nodes = collectNodes(packageName);
        if (nodes.isEmpty()) {
            return Attempt.none();
        }

        if (hasLaunchWindowRule) {
            Attempt packageSpecific = attemptPackageSpecificRule(packageName, nodes, now);
            if (packageSpecific.clicked()) {
                return packageSpecific;
            }
        }

        long minimumPendingDelay = Long.MAX_VALUE;
        for (PopupAdRuleCatalog.Rule rule : rules) {
            Long lastAction = lastFired.get(rule.getKey());
            if (lastAction != null && now - lastAction < RULE_COOLDOWN_MILLIS) {
                continue;
            }

            List<NodeRecord> triggerNodes = findTriggerNodes(nodes, rule.getTrigger());
            if (triggerNodes.isEmpty()) {
                firstSeen.remove(rule.getKey());
                continue;
            }

            long seenAt = firstSeen.containsKey(rule.getKey())
                    ? firstSeen.get(rule.getKey())
                    : now;
            firstSeen.put(rule.getKey(), seenAt);
            long remainingDelay = rule.getDelayMillis() - (now - seenAt);
            if (remainingDelay > 0L) {
                minimumPendingDelay = Math.min(minimumPendingDelay, remainingDelay);
                continue;
            }

            NodeRecord action = findBestAction(nodes, triggerNodes, rule.getAction());
            if (action == null) {
                if (now - seenAt < 5_000L) {
                    minimumPendingDelay = Math.min(minimumPendingDelay, 250L);
                }
                continue;
            }
            AccessibilityNodeInfo clickable = findActionNode(action.node);
            if (clickable == null) {
                continue;
            }
            try {
                if (clickable.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    lastFired.put(rule.getKey(), now);
                    firstSeen.remove(rule.getKey());
                    Log.i(TAG, "Closed popup ad with rule " + rule.getKey());
                    return Attempt.clicked(rule.getKey());
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Popup rule click failed", error);
            }
        }
        return minimumPendingDelay == Long.MAX_VALUE
                ? Attempt.none()
                : Attempt.pending(minimumPendingDelay);
    }

    private Attempt attemptPackageSpecificRule(
            String packageName,
            List<NodeRecord> nodes,
            long now) {
        for (NodeRecord record : nodes) {
            PackageSpecificRules.Match match = PackageSpecificRules.matchLaunchWindowNode(
                    packageName,
                    record.signals,
                    record.node.getChildCount(),
                    isOwnClickable(record.node),
                    ancestorShapes(record.node, 4));
            if (!match.matched()) {
                continue;
            }
            Long lastAction = lastFired.get(match.getRuleKey());
            if (lastAction != null && now - lastAction < RULE_COOLDOWN_MILLIS) {
                continue;
            }
            try {
                if (record.node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    lastFired.put(match.getRuleKey(), now);
                    Log.i(TAG, "Closed startup ad with built-in rule " + match.getRuleKey());
                    return Attempt.clicked(match.getRuleKey());
                }
            } catch (RuntimeException error) {
                Log.w(TAG, "Built-in startup-ad click failed", error);
            }
        }
        return Attempt.none();
    }

    private static List<PackageSpecificRules.NodeShape> ancestorShapes(
            AccessibilityNodeInfo node,
            int maximumDepth) {
        List<PackageSpecificRules.NodeShape> result = new ArrayList<>();
        AccessibilityNodeInfo current = node.getParent();
        for (int depth = 0; current != null && depth < maximumDepth; depth++) {
            result.add(new PackageSpecificRules.NodeShape(
                    characters(current.getClassName()),
                    current.getViewIdResourceName(),
                    current.getChildCount()));
            current = current.getParent();
        }
        return result;
    }

    private static boolean isOwnClickable(AccessibilityNodeInfo node) {
        return node.isEnabled()
                && (node.isClickable()
                || (node.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0);
    }

    private List<NodeRecord> collectNodes(String targetPackage) {
        List<NodeRecord> result = new ArrayList<>();
        List<AccessibilityNodeInfo> roots = collectRoots();
        DisplayMetrics metrics = service.getResources().getDisplayMetrics();
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        int visited = 0;
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
                if (!belongsToTarget(node, targetPackage) || !node.isVisibleToUser() || !node.isEnabled()) {
                    continue;
                }
                Rect bounds = new Rect();
                node.getBoundsInScreen(bounds);
                if (!bounds.intersect(0, 0, width, height)) {
                    continue;
                }
                CandidateSignals signals = CandidateSignals.builder()
                        .text(characters(node.getText()))
                        .contentDescription(characters(node.getContentDescription()))
                        .viewId(node.getViewIdResourceName())
                        .className(characters(node.getClassName()))
                        .clickable(findActionNode(node) != null)
                        .enabled(true)
                        .visible(true)
                        .bounds(
                                bounds.left / (float) width,
                                bounds.top / (float) height,
                                bounds.right / (float) width,
                                bounds.bottom / (float) height)
                        .build();
                result.add(new NodeRecord(node, signals, bounds));
            }
            if (visited >= MAX_NODES) {
                Log.w(TAG, "Popup rule node scan limit reached");
                break;
            }
        }
        return result;
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
            for (AccessibilityWindowInfo window : service.getWindows()) {
                AccessibilityNodeInfo root = window.getRoot();
                if (root != null && windowIds.add(root.getWindowId())) {
                    result.add(root);
                }
            }
        } catch (RuntimeException error) {
            Log.w(TAG, "Unable to retrieve popup windows", error);
        }
        return result;
    }

    private static List<NodeRecord> findTriggerNodes(
            List<NodeRecord> nodes,
            PopupSelector trigger) {
        List<NodeRecord> matches = new ArrayList<>();
        for (int term = 0; term < trigger.getTermCount(); term++) {
            boolean found = false;
            int retainedForTerm = 0;
            for (NodeRecord node : nodes) {
                if (trigger.matchesTerm(term, node.signals)) {
                    found = true;
                    if (retainedForTerm < 24) {
                        matches.add(node);
                        retainedForTerm++;
                    }
                }
            }
            if (!found) {
                return Collections.emptyList();
            }
        }
        return matches;
    }

    private static NodeRecord findBestAction(
            List<NodeRecord> nodes,
            List<NodeRecord> triggerNodes,
            PopupSelector action) {
        NodeRecord best = null;
        int bestRelationship = Integer.MIN_VALUE;
        long bestArea = Long.MAX_VALUE;
        for (NodeRecord candidate : nodes) {
            if (!action.matches(candidate.signals) || findActionNode(candidate.node) == null) {
                continue;
            }
            int relationship = relationshipScore(candidate.node, triggerNodes);
            long area = Math.max(1L, (long) candidate.bounds.width() * candidate.bounds.height());
            if (best == null
                    || relationship > bestRelationship
                    || (relationship == bestRelationship && area < bestArea)) {
                best = candidate;
                bestRelationship = relationship;
                bestArea = area;
            }
        }
        return best;
    }

    private static int relationshipScore(
            AccessibilityNodeInfo action,
            List<NodeRecord> triggers) {
        int best = 0;
        for (NodeRecord trigger : triggers) {
            if (action.equals(trigger.node)) {
                return 100;
            }
            AccessibilityNodeInfo actionAncestor = action;
            for (int actionDepth = 0; actionAncestor != null && actionDepth <= 5; actionDepth++) {
                AccessibilityNodeInfo triggerAncestor = trigger.node;
                for (int triggerDepth = 0;
                        triggerAncestor != null && triggerDepth <= 5;
                        triggerDepth++) {
                    if (actionAncestor.equals(triggerAncestor)) {
                        best = Math.max(best, 80 - (actionDepth + triggerDepth) * 8);
                    }
                    triggerAncestor = triggerAncestor.getParent();
                }
                actionAncestor = actionAncestor.getParent();
            }
        }
        return best;
    }

    private static AccessibilityNodeInfo findActionNode(AccessibilityNodeInfo source) {
        AccessibilityNodeInfo current = source;
        for (int depth = 0; current != null && depth <= 3; depth++) {
            boolean hasClickAction = (current.getActions() & AccessibilityNodeInfo.ACTION_CLICK) != 0;
            if (current.isVisibleToUser()
                    && current.isEnabled()
                    && (current.isClickable() || hasClickAction)) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static boolean belongsToTarget(AccessibilityNodeInfo node, String targetPackage) {
        CharSequence owner = node.getPackageName();
        return owner == null || targetPackage.contentEquals(owner);
    }

    private static String characters(CharSequence value) {
        return value == null ? "" : value.toString();
    }

    private static final class NodeRecord {
        private final AccessibilityNodeInfo node;
        private final CandidateSignals signals;
        private final Rect bounds;

        private NodeRecord(AccessibilityNodeInfo nodeInfo, CandidateSignals nodeSignals, Rect nodeBounds) {
            node = nodeInfo;
            signals = nodeSignals;
            bounds = new Rect(nodeBounds);
        }
    }

    public static final class Attempt {
        private final boolean clicked;
        private final String ruleKey;
        private final long retryAfterMillis;

        private Attempt(boolean didClick, String key, long retryAfter) {
            clicked = didClick;
            ruleKey = key;
            retryAfterMillis = retryAfter;
        }

        static Attempt none() {
            return new Attempt(false, "", 0L);
        }

        static Attempt clicked(String key) {
            return new Attempt(true, key, 0L);
        }

        static Attempt pending(long delay) {
            return new Attempt(false, "", Math.max(50L, delay));
        }

        public boolean clicked() {
            return clicked;
        }

        public String getRuleKey() {
            return ruleKey;
        }

        public long getRetryAfterMillis() {
            return retryAfterMillis;
        }
    }
}
