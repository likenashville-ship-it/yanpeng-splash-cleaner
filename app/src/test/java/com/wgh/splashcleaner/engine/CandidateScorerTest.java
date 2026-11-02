package com.wgh.splashcleaner.engine;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class CandidateScorerTest {
    private CandidateScorer scorer;

    @Before
    public void setUp() {
        scorer = new CandidateScorer(RuleCatalog.defaults());
    }

    @Test
    public void countdownSkipTextIsAccepted() {
        CandidateSignals signals = CandidateSignals.builder()
                .text("3 秒  跳过")
                .className("android.widget.Button")
                .clickable(true)
                .bounds(0.78f, 0.05f, 0.97f, 0.12f)
                .build();

        CandidateScorer.Score score = scorer.score(signals, false);

        assertTrue(score.shouldClick());
        assertTrue(score.isGestureEligible());
    }

    @Test
    public void genericCloseWithoutAdContextIsRejected() {
        CandidateSignals signals = CandidateSignals.builder()
                .text("关闭")
                .viewId("com.example:id/iv_close")
                .className("android.widget.ImageButton")
                .clickable(true)
                .bounds(0.82f, 0.04f, 0.95f, 0.12f)
                .build();

        assertFalse(scorer.score(signals, false).shouldClick());
    }

    @Test
    public void genericCloseInsideAdContainerIsAcceptedForDirectClick() {
        CandidateSignals signals = CandidateSignals.builder()
                .text("关闭")
                .viewId("com.example:id/iv_close")
                .clickable(true)
                .hasAdContext(true)
                .bounds(0.82f, 0.04f, 0.95f, 0.12f)
                .build();

        CandidateScorer.Score score = scorer.score(signals, false);

        assertTrue(score.shouldClick());
        assertFalse(score.isGestureEligible());
    }

    @Test
    public void dangerousCallToActionAlwaysWinsOverId() {
        CandidateSignals signals = CandidateSignals.builder()
                .text("立即下载")
                .viewId("com.example:id/ad_skip_btn")
                .clickable(true)
                .hasAdContext(true)
                .bounds(0.72f, 0.05f, 0.98f, 0.13f)
                .build();

        assertFalse(scorer.score(signals, false).shouldClick());
    }

    @Test
    public void commonAdSdkIdIsAccepted() {
        CandidateSignals signals = CandidateSignals.builder()
                .viewId("com.example:id/ksad_splash_circle_skip_view")
                .clickable(true)
                .hasAdContext(true)
                .bounds(0.78f, 0.04f, 0.97f, 0.14f)
                .build();

        CandidateScorer.Score score = scorer.score(signals, false);

        assertTrue(score.shouldClick());
        assertTrue(score.isGestureEligible());
    }

    @Test
    public void sensitiveAppRequiresExplicitText() {
        CandidateSignals idOnly = CandidateSignals.builder()
                .viewId("com.example:id/splash_skip_btn")
                .clickable(true)
                .hasAdContext(true)
                .bounds(0.8f, 0.04f, 0.97f, 0.12f)
                .build();
        CandidateSignals explicitText = CandidateSignals.builder()
                .text("跳过")
                .clickable(true)
                .bounds(0.8f, 0.04f, 0.97f, 0.12f)
                .build();

        assertFalse(scorer.score(idOnly, true).shouldClick());
        assertTrue(scorer.score(explicitText, true).shouldClick());
    }

    @Test
    public void invisibleNodeIsRejected() {
        CandidateSignals signals = CandidateSignals.builder()
                .text("跳过")
                .visible(false)
                .clickable(true)
                .bounds(0.8f, 0.04f, 0.97f, 0.12f)
                .build();

        assertFalse(scorer.score(signals, false).shouldClick());
    }

    @Test
    public void videoSkipButtonIsRejected() {
        CandidateSignals signals = CandidateSignals.builder()
                .text("跳过视频")
                .viewId("com.example:id/skip_video_btn")
                .clickable(true)
                .bounds(0.78f, 0.05f, 0.97f, 0.12f)
                .build();

        assertFalse(scorer.score(signals, false).shouldClick());
    }

    @Test
    public void updateDismissExactTextIsAcceptedDespiteDangerousSubstring() {
        CandidateSignals later = CandidateSignals.builder()
                .text("稍后更新")
                .className("android.widget.Button")
                .clickable(true)
                .bounds(0.20f, 0.60f, 0.80f, 0.70f)
                .build();
        assertTrue(scorer.score(later, false).shouldClick());

        CandidateSignals forceUpdate = CandidateSignals.builder()
                .text("立即更新")
                .className("android.widget.Button")
                .clickable(true)
                .bounds(0.20f, 0.72f, 0.80f, 0.82f)
                .build();
        assertFalse(scorer.score(forceUpdate, false).shouldClick());
    }
}
