package com.wgh.splashcleaner.engine;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PackageSpecificRulesTest {
    @Test
    public void xiaomiAnonymousCloseInLinearDialogIsAccepted() {
        CandidateSignals node = compactImage("");

        PackageSpecificRules.Match match = PackageSpecificRules.matchLaunchWindowNode(
                "com.xiaomi.shop",
                node,
                0,
                true,
                Arrays.asList(
                        shape("android.view.ViewGroup", "", 1),
                        shape("android.widget.LinearLayout", "", 2),
                        shape("android.widget.RelativeLayout", "", 1),
                        shape("android.widget.FrameLayout", "android:id/content", 1)));

        assertTrue(match.matched());
    }

    @Test
    public void xiaomiAnonymousCloseInFrameDialogIsAccepted() {
        PackageSpecificRules.Match match = PackageSpecificRules.matchLaunchWindowNode(
                "com.xiaomi.shop",
                compactImage("关闭"),
                0,
                true,
                Arrays.asList(
                        shape("android.widget.FrameLayout", "", 1),
                        shape("android.widget.FrameLayout", "", 2),
                        shape("android.widget.FrameLayout", "android:id/content", 1)));

        assertTrue(match.matched());
    }

    @Test
    public void sameAnonymousHierarchyInAnotherAppIsRejected() {
        PackageSpecificRules.Match match = PackageSpecificRules.matchLaunchWindowNode(
                "com.example.shop",
                compactImage(""),
                0,
                true,
                Arrays.asList(
                        shape("android.view.ViewGroup", "", 1),
                        shape("android.widget.LinearLayout", "", 2),
                        shape("android.widget.RelativeLayout", "", 1),
                        shape("android.widget.FrameLayout", "android:id/content", 1)));

        assertFalse(match.matched());
    }

    @Test
    public void largeOrCallToActionImageIsRejected() {
        CandidateSignals large = CandidateSignals.builder()
                .className("android.widget.ImageView")
                .clickable(true)
                .bounds(0.05f, 0.1f, 0.95f, 0.85f)
                .build();
        CandidateSignals callToAction = compactImage("立即购买");

        assertFalse(matchFrame(large).matched());
        assertFalse(matchFrame(callToAction).matched());
    }

    @Test
    public void exactXiaomiFloatingCloseIdIsAccepted() {
        CandidateSignals node = CandidateSignals.builder()
                .viewId("com.xiaomi.shop.plugin.homepage:id/home_bottom_float_close_btn")
                .className("android.widget.ImageView")
                .clickable(true)
                .bounds(0.85f, 0.7f, 0.95f, 0.8f)
                .build();

        PackageSpecificRules.Match match = PackageSpecificRules.matchLaunchWindowNode(
                "com.xiaomi.shop", node, 0, true, Collections.emptyList());

        assertTrue(match.matched());
    }

    private static PackageSpecificRules.Match matchFrame(CandidateSignals node) {
        return PackageSpecificRules.matchLaunchWindowNode(
                "com.xiaomi.shop",
                node,
                0,
                true,
                Arrays.asList(
                        shape("android.view.ViewGroup", "", 1),
                        shape("android.widget.FrameLayout", "", 2),
                        shape("android.widget.FrameLayout", "android:id/content", 1)));
    }

    private static CandidateSignals compactImage(String description) {
        return CandidateSignals.builder()
                .contentDescription(description)
                .className("android.widget.ImageView")
                .clickable(true)
                .bounds(0.82f, 0.06f, 0.94f, 0.14f)
                .build();
    }

    private static PackageSpecificRules.NodeShape shape(
            String className,
            String viewId,
            int childCount) {
        return new PackageSpecificRules.NodeShape(className, viewId, childCount);
    }
}
