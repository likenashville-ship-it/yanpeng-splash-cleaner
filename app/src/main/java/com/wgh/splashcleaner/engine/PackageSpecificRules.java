package com.wgh.splashcleaner.engine;

import java.util.Collections;
import java.util.List;

/** Small, audited structural rules for apps whose close control has no text or resource ID. */
final class PackageSpecificRules {
    static final String XIAOMI_SHOP_PACKAGE = "com.xiaomi.shop";
    private static final String XIAOMI_FLOAT_CLOSE_ID =
            "com.xiaomi.shop.plugin.homepage:id/home_bottom_float_close_btn";

    private PackageSpecificRules() {
    }

    static boolean hasLaunchWindowRule(String packageName) {
        return XIAOMI_SHOP_PACKAGE.equals(packageName);
    }

    static Match matchLaunchWindowNode(
            String packageName,
            CandidateSignals node,
            int nodeChildCount,
            boolean nodeOwnClickable,
            List<NodeShape> ancestors) {
        if (!XIAOMI_SHOP_PACKAGE.equals(packageName)
                || node == null
                || !node.isVisible()
                || !node.isEnabled()) {
            return Match.none();
        }

        String viewId = safe(node.getViewId());
        if (XIAOMI_FLOAT_CLOSE_ID.equals(viewId) && nodeOwnClickable) {
            return Match.matched("xiaomi-shop-floating-ad-close");
        }

        if (!nodeOwnClickable
                || nodeChildCount != 0
                || !endsWithClass(node.getClassName(), "ImageView")
                || !isCompact(node)
                || !hasSafeCloseLabel(node)) {
            return Match.none();
        }

        List<NodeShape> chain = ancestors == null ? Collections.emptyList() : ancestors;
        // Public Xiaomi Store snapshots show the anonymous close ImageView two levels below
        // either a two-child LinearLayout or FrameLayout rooted directly under android content.
        boolean linearDialog = isClassWithChildren(shapeAt(chain, 1), "LinearLayout", 2)
                && isClass(shapeAt(chain, 2), "RelativeLayout")
                && isAndroidContent(shapeAt(chain, 3));
        boolean frameDialog = isClassWithChildren(shapeAt(chain, 1), "FrameLayout", 2)
                && isAndroidContent(shapeAt(chain, 2));
        return linearDialog || frameDialog
                ? Match.matched("xiaomi-shop-fullscreen-ad-close")
                : Match.none();
    }

    private static boolean isCompact(CandidateSignals node) {
        float width = node.getRight() - node.getLeft();
        float height = node.getBottom() - node.getTop();
        return width > 0f && height > 0f && width <= 0.28f && height <= 0.28f;
    }

    private static boolean hasSafeCloseLabel(CandidateSignals node) {
        String label = RuleCatalog.normalize(
                safe(node.getText()) + " " + safe(node.getContentDescription()));
        if (label.isEmpty()) {
            return true;
        }
        return label.equals("关闭")
                || label.equals("关闭广告")
                || label.equals("关闭按钮")
                || label.equals("跳过")
                || label.equals("跳过广告")
                || label.equals("close")
                || label.equals("close ad")
                || label.equals("close button")
                || label.equals("skip")
                || label.equals("skip ad")
                || label.equals("x")
                || label.equals("×")
                || label.equals("✕");
    }

    private static NodeShape shapeAt(List<NodeShape> chain, int index) {
        return index >= 0 && index < chain.size() ? chain.get(index) : null;
    }

    private static boolean isClass(NodeShape shape, String simpleName) {
        return shape != null && endsWithClass(shape.className, simpleName);
    }

    private static boolean isClassWithChildren(
            NodeShape shape,
            String simpleName,
            int childCount) {
        return isClass(shape, simpleName) && shape.childCount == childCount;
    }

    private static boolean isAndroidContent(NodeShape shape) {
        return shape != null && "android:id/content".equals(shape.viewId);
    }

    private static boolean endsWithClass(String className, String simpleName) {
        String value = safe(className);
        return value.equals(simpleName) || value.endsWith("." + simpleName);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    static final class NodeShape {
        private final String className;
        private final String viewId;
        private final int childCount;

        NodeShape(String nodeClassName, String nodeViewId, int children) {
            className = safe(nodeClassName);
            viewId = safe(nodeViewId);
            childCount = Math.max(0, children);
        }
    }

    static final class Match {
        private static final Match NONE = new Match(false, "");

        private final boolean matched;
        private final String ruleKey;

        private Match(boolean didMatch, String key) {
            matched = didMatch;
            ruleKey = key;
        }

        static Match none() {
            return NONE;
        }

        static Match matched(String key) {
            return new Match(true, key);
        }

        boolean matched() {
            return matched;
        }

        String getRuleKey() {
            return ruleKey;
        }
    }
}
