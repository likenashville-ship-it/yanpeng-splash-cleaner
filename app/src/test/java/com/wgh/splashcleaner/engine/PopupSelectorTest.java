package com.wgh.splashcleaner.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class PopupSelectorTest {
    @Test
    public void packageHashMatchesUpstreamRuleKey() {
        assertEquals("847156324", String.valueOf("com.coolapk.market".hashCode()));
        assertEquals("1536737232", String.valueOf("com.sina.weibo".hashCode()));
    }

    @Test
    public void namespacedViewIdMatchesSimpleSelector() {
        CandidateSignals node = CandidateSignals.builder()
                .viewId("com.example:id/tt_splash_skip_btn")
                .build();

        assertTrue(PopupSelector.action("tt_splash_skip_btn").matches(node));
    }

    @Test
    public void nonExactSelectorCanMatchResourceIdFragment() {
        CandidateSignals node = CandidateSignals.builder()
                .viewId("com.example:id/dialog_ad_close_button")
                .build();

        assertTrue(PopupSelector.action("ad_close").matches(node));
    }

    @Test
    public void exactSelectorDoesNotMatchLongerText() {
        CandidateSignals exact = CandidateSignals.builder().text("关闭").build();
        CandidateSignals longer = CandidateSignals.builder().text("关闭广告").build();
        PopupSelector selector = PopupSelector.action("=关闭");

        assertTrue(selector.matches(exact));
        assertFalse(selector.matches(longer));
    }

    @Test
    public void ampersandCreatesIndependentTriggerTerms() {
        PopupSelector selector = PopupSelector.trigger("不感兴趣&举报广告");
        CandidateSignals first = CandidateSignals.builder().text("不感兴趣").build();
        CandidateSignals second = CandidateSignals.builder().text("举报广告").build();

        assertEquals(2, selector.getTermCount());
        assertTrue(selector.matchesTerm(0, first));
        assertTrue(selector.matchesTerm(1, second));
        assertFalse(selector.matchesTerm(1, first));
    }

    @Test
    public void unsupportedRelativeSelectorIsRejected() {
        assertFalse(PopupSelector.action("+close").isValid());
        assertFalse(PopupSelector.action("| 跳过").isValid());
    }
}
