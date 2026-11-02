package com.wgh.splashcleaner.engine;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class LaunchSessionTest {
    @Test
    public void onlyCurrentPackageWithinWindowCanScan() {
        LaunchSession session = new LaunchSession();
        session.begin("com.example.video", 1_000L);

        assertTrue(session.canScan("com.example.video", 5_000L, 10_000L));
        assertFalse(session.canScan("com.example.other", 5_000L, 10_000L));
        assertFalse(session.canScan("com.example.video", 11_001L, 10_000L));
    }

    @Test
    public void actionStopsRemainingScans() {
        LaunchSession session = new LaunchSession();
        session.begin("com.example.video", 2_000L);
        session.markActionPerformed();

        assertFalse(session.canScan("com.example.video", 2_500L, 10_000L));
        assertTrue(session.isWithinWindow("com.example.video", 2_500L, 10_000L));
    }

    @Test
    public void newSessionResetsActionState() {
        LaunchSession session = new LaunchSession();
        session.begin("com.example.one", 1_000L);
        session.markActionPerformed();
        session.begin("com.example.two", 3_000L);

        assertTrue(session.canScan("com.example.two", 3_100L, 10_000L));
    }
}
