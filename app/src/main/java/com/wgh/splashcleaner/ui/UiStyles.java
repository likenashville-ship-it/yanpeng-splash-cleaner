package com.wgh.splashcleaner.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.View;
import android.view.Window;

public final class UiStyles {
    public static final int PAGE_BACKGROUND = Color.rgb(245, 247, 251);
    public static final int TEXT_PRIMARY = Color.rgb(23, 32, 51);
    public static final int TEXT_SECONDARY = Color.rgb(102, 112, 133);
    public static final int BRAND_BLUE = Color.rgb(37, 99, 235);
    public static final int SUCCESS_GREEN = Color.rgb(22, 163, 74);
    public static final int WARNING_AMBER = Color.rgb(217, 119, 6);

    private UiStyles() {
    }

    public static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    public static GradientDrawable rounded(int color, float radiusDp, Context context) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(context, radiusDp));
        return drawable;
    }

    public static GradientDrawable outlined(
            int fillColor,
            int strokeColor,
            float radiusDp,
            Context context) {
        GradientDrawable drawable = rounded(fillColor, radiusDp, context);
        drawable.setStroke(dp(context, 1), strokeColor);
        return drawable;
    }

    public static void configureSystemBars(Window window) {
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(PAGE_BACKGROUND);
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }
}
