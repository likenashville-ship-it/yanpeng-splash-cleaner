package com.wgh.splashcleaner.engine;

/** A platform-independent snapshot of the properties used to score one UI node. */
public final class CandidateSignals {
    private final String text;
    private final String contentDescription;
    private final String viewId;
    private final String className;
    private final boolean clickable;
    private final boolean enabled;
    private final boolean visible;
    private final boolean hasAdContext;
    private final float left;
    private final float top;
    private final float right;
    private final float bottom;

    private CandidateSignals(Builder builder) {
        text = safe(builder.text);
        contentDescription = safe(builder.contentDescription);
        viewId = safe(builder.viewId);
        className = safe(builder.className);
        clickable = builder.clickable;
        enabled = builder.enabled;
        visible = builder.visible;
        hasAdContext = builder.hasAdContext;
        left = clamp(builder.left);
        top = clamp(builder.top);
        right = clamp(builder.right);
        bottom = clamp(builder.bottom);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    public String getText() {
        return text;
    }

    public String getContentDescription() {
        return contentDescription;
    }

    public String getViewId() {
        return viewId;
    }

    public String getClassName() {
        return className;
    }

    public boolean isClickable() {
        return clickable;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean hasAdContext() {
        return hasAdContext;
    }

    public float getLeft() {
        return left;
    }

    public float getTop() {
        return top;
    }

    public float getRight() {
        return right;
    }

    public float getBottom() {
        return bottom;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String text;
        private String contentDescription;
        private String viewId;
        private String className;
        private boolean clickable;
        private boolean enabled = true;
        private boolean visible = true;
        private boolean hasAdContext;
        private float left;
        private float top;
        private float right = 1f;
        private float bottom = 1f;

        public Builder text(String value) {
            text = value;
            return this;
        }

        public Builder contentDescription(String value) {
            contentDescription = value;
            return this;
        }

        public Builder viewId(String value) {
            viewId = value;
            return this;
        }

        public Builder className(String value) {
            className = value;
            return this;
        }

        public Builder clickable(boolean value) {
            clickable = value;
            return this;
        }

        public Builder enabled(boolean value) {
            enabled = value;
            return this;
        }

        public Builder visible(boolean value) {
            visible = value;
            return this;
        }

        public Builder hasAdContext(boolean value) {
            hasAdContext = value;
            return this;
        }

        public Builder bounds(float leftValue, float topValue, float rightValue, float bottomValue) {
            left = leftValue;
            top = topValue;
            right = rightValue;
            bottom = bottomValue;
            return this;
        }

        public CandidateSignals build() {
            return new CandidateSignals(this);
        }
    }
}
