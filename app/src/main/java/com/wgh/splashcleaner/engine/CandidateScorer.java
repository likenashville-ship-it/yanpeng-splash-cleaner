package com.wgh.splashcleaner.engine;

/** Conservative scoring: broad enough for common ad SDKs, strict enough to avoid normal buttons. */
public final class CandidateScorer {
    public static final int MIN_SCORE = 100;
    private static final int REJECTED_SCORE = -1000;

    private final RuleCatalog rules;

    public CandidateScorer(RuleCatalog ruleCatalog) {
        rules = ruleCatalog;
    }

    public Score score(CandidateSignals signals, boolean sensitiveApp) {
        if (!signals.isVisible() || !signals.isEnabled()) {
            return Score.rejected("控件不可用");
        }

        String text = RuleCatalog.normalize(signals.getText());
        String description = RuleCatalog.normalize(signals.getContentDescription());
        String combinedLabel = (text + " " + description).trim();
        String viewId = RuleCatalog.normalize(signals.getViewId());

        boolean exactText = rules.isExactText(text)
                || rules.isExactText(description)
                || rules.matchesTextPattern(text)
                || rules.matchesTextPattern(description);
        boolean strongId = rules.hasStrongId(viewId);
        boolean genericCloseId = rules.hasGenericCloseId(viewId);
        boolean adContext = signals.hasAdContext()
                || rules.hasAdContextToken(combinedLabel)
                || rules.hasAdContextToken(viewId);

        // Exact dismiss labels such as "稍后更新" / "暂不开启" may contain
        // dangerous substrings; allow them only when the full label is exactText.
        if (rules.hasDangerousActionToken(combinedLabel) && !exactText) {
            return Score.rejected("疑似广告跳转按钮");
        }
        if (rules.hasExcludedSkipContextToken(combinedLabel)
                || rules.hasExcludedSkipContextToken(viewId)) {
            return Score.rejected("疑似非广告跳过操作");
        }

        if (sensitiveApp && !exactText) {
            return Score.rejected("敏感应用只允许明确跳过文字");
        }

        int value = 0;
        String reason = "";
        if (exactText) {
            value += 120;
            reason = "明确的跳过文字";
        }
        if (strongId) {
            value += 88;
            if (reason.isEmpty()) {
                reason = "广告 SDK 控件 ID";
            }
        } else if (genericCloseId) {
            value += 32;
            if (reason.isEmpty()) {
                reason = "关闭控件 ID";
            }
        }

        if (isGenericCloseLabel(text) || isGenericCloseLabel(description)) {
            value += 15;
        }
        if (adContext) {
            value += 28;
        }
        if (signals.isClickable()) {
            value += 12;
        }

        float width = Math.max(0f, signals.getRight() - signals.getLeft());
        float height = Math.max(0f, signals.getBottom() - signals.getTop());
        boolean compact = width > 0f && height > 0f && width <= 0.45f && height <= 0.22f;
        boolean topRight = signals.getRight() >= 0.65f && signals.getTop() <= 0.45f;
        if (topRight) {
            value += 18;
        }
        if (compact) {
            value += 8;
        }

        String className = RuleCatalog.normalize(signals.getClassName());
        if (className.contains("button")) {
            value += 6;
        }

        // A generic close icon is not enough on its own; it must live in an ad-like container.
        if (genericCloseId && !exactText && !strongId && !adContext) {
            value = Math.min(value, MIN_SCORE - 1);
        }
        if (!exactText && !strongId && !adContext) {
            value = Math.min(value, MIN_SCORE - 1);
        }

        boolean shouldClick = value >= MIN_SCORE;
        boolean gestureEligible = shouldClick
                && compact
                && (exactText || (strongId && adContext));
        return new Score(value, shouldClick, gestureEligible, reason.isEmpty() ? "低置信度" : reason);
    }

    private static boolean isGenericCloseLabel(String value) {
        return "关闭".equals(value)
                || "關閉".equals(value)
                || "close".equals(value)
                || "×".equals(value)
                || "✕".equals(value);
    }

    public static final class Score {
        private final int value;
        private final boolean shouldClick;
        private final boolean gestureEligible;
        private final String reason;

        private Score(int score, boolean click, boolean gesture, String matchReason) {
            value = score;
            shouldClick = click;
            gestureEligible = gesture;
            reason = matchReason;
        }

        static Score rejected(String reason) {
            return new Score(REJECTED_SCORE, false, false, reason);
        }

        public int getValue() {
            return value;
        }

        public boolean shouldClick() {
            return shouldClick;
        }

        public boolean isGestureEligible() {
            return gestureEligible;
        }

        public String getReason() {
            return reason;
        }
    }
}
