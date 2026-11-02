package com.wgh.splashcleaner;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import com.wgh.splashcleaner.accessibility.SplashSkipService;
import com.wgh.splashcleaner.engine.PopupAdRuleCatalog;
import com.wgh.splashcleaner.prefs.AppPreferences;
import com.wgh.splashcleaner.ui.IgnoredAppsActivity;
import com.wgh.splashcleaner.ui.UiStyles;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MainActivity extends Activity {
    private AppPreferences preferences;
    private TextView serviceBadge;
    private TextView serviceDescription;
    private TextView serviceIndicator;
    private Button accessibilityButton;
    private TextView scanWindowValue;
    private TextView totalSkips;
    private TextView lastSkip;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        UiStyles.configureSystemBars(getWindow());
        setContentView(buildContent());
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
    }

    private View buildContent() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setClipToPadding(false);
        scrollView.setBackgroundColor(UiStyles.PAGE_BACKGROUND);
        applySystemInsets(scrollView);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(16), dp(20), dp(36));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));

        TextView privacyPill = text("离线运行 · 不申请联网权限", 12, UiStyles.BRAND_BLUE);
        privacyPill.setGravity(Gravity.CENTER);
        privacyPill.setPadding(dp(12), dp(6), dp(12), dp(6));
        privacyPill.setBackground(UiStyles.rounded(Color.rgb(231, 239, 255), 99, this));
        LinearLayout.LayoutParams pillParams = wrap();
        pillParams.bottomMargin = dp(14);
        content.addView(privacyPill, pillParams);

        TextView title = text("打开应用，广告自动跳过", 28, UiStyles.TEXT_PRIMARY);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title, matchWrap());

        TextView subtitle = text(
                "服务开启后，只在应用启动后的短时间内识别高置信度开屏广告控件。",
                15,
                UiStyles.TEXT_SECONDARY);
        subtitle.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams subtitleParams = matchWrap();
        subtitleParams.topMargin = dp(8);
        subtitleParams.bottomMargin = dp(22);
        content.addView(subtitle, subtitleParams);

        content.addView(buildServiceCard(), cardParams());
        addSectionTitle(content, "自动跳过设置");
        content.addView(buildSettingsCard(), cardParams());
        addSectionTitle(content, "运行记录");
        content.addView(buildStatsCard(), cardParams());
        addSectionTitle(content, "安全边界");
        content.addView(buildSafetyCard(), cardParams());
        return scrollView;
    }

    private View buildServiceCard() {
        LinearLayout card = card();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        serviceBadge = text("正在检查", 18, UiStyles.TEXT_PRIMARY);
        serviceBadge.setTypeface(serviceBadge.getTypeface(), android.graphics.Typeface.BOLD);
        serviceDescription = text("", 13, UiStyles.TEXT_SECONDARY);
        LinearLayout.LayoutParams descriptionParams = matchWrap();
        descriptionParams.topMargin = dp(4);
        labels.addView(serviceBadge, matchWrap());
        labels.addView(serviceDescription, descriptionParams);
        row.addView(labels, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        serviceIndicator = text("●", 20, UiStyles.SUCCESS_GREEN);
        row.addView(serviceIndicator, wrap());
        card.addView(row, matchWrap());

        accessibilityButton = primaryButton("开启无障碍服务");
        accessibilityButton.setOnClickListener(view -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        });
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(16);
        card.addView(accessibilityButton, buttonParams);

        TextView restrictedHint = text(
                "若服务显示为灰色：进入本应用的系统“应用信息”页，点右上角菜单并允许受限设置。",
                12,
                UiStyles.TEXT_SECONDARY);
        restrictedHint.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams hintParams = matchWrap();
        hintParams.topMargin = dp(12);
        card.addView(restrictedHint, hintParams);
        return card;
    }

    private View buildSettingsCard() {
        LinearLayout card = card();
        Switch masterSwitch = addSwitchRow(
                card,
                "自动跳过",
                "切换到新应用后立即启动识别",
                preferences.isMasterEnabled());
        masterSwitch.setOnCheckedChangeListener((button, checked) ->
                preferences.setMasterEnabled(checked));

        addDivider(card);
        Switch gestureSwitch = addSwitchRow(
                card,
                "触控补点",
                "仅在明确跳过控件无法直接点击时使用",
                preferences.isGestureEnabled());
        gestureSwitch.setOnCheckedChangeListener((button, checked) ->
                preferences.setGestureEnabled(checked));

        addDivider(card);
        PopupAdRuleCatalog popupCatalog = PopupAdRuleCatalog.load(this);
        Switch popupSwitch = addSwitchRow(
                card,
                "应用内弹窗广告",
                popupCatalog.getPackageCount() + " 组包级规则 · "
                        + popupCatalog.getRuleCount() + " 条高置信度关闭规则",
                preferences.isPopupAdsEnabled());
        popupSwitch.setOnCheckedChangeListener((button, checked) ->
                preferences.setPopupAdsEnabled(checked));

        addDivider(card);
        LinearLayout windowHeader = new LinearLayout(this);
        windowHeader.setOrientation(LinearLayout.HORIZONTAL);
        windowHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView windowTitle = text("启动识别时长", 15, UiStyles.TEXT_PRIMARY);
        scanWindowValue = text(formatSeconds(preferences.getScanWindowMillis()), 14, UiStyles.BRAND_BLUE);
        scanWindowValue.setTypeface(scanWindowValue.getTypeface(), android.graphics.Typeface.BOLD);
        windowHeader.addView(windowTitle, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        windowHeader.addView(scanWindowValue, wrap());
        card.addView(windowHeader, matchWrap());

        SeekBar seekBar = new SeekBar(this);
        seekBar.setMax(11);
        seekBar.setProgress(preferences.getScanWindowMillis() / 1_000 - 4);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int milliseconds = (progress + 4) * 1_000;
                scanWindowValue.setText(formatSeconds(milliseconds));
                if (fromUser) {
                    preferences.setScanWindowMillis(milliseconds);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                preferences.setScanWindowMillis((bar.getProgress() + 4) * 1_000);
            }
        });
        LinearLayout.LayoutParams seekParams = matchWrap();
        seekParams.topMargin = dp(8);
        card.addView(seekBar, seekParams);

        Button ignoredApps = secondaryButton("管理忽略应用");
        ignoredApps.setOnClickListener(view ->
                startActivity(new Intent(this, IgnoredAppsActivity.class)));
        LinearLayout.LayoutParams ignoredParams = matchWrap();
        ignoredParams.topMargin = dp(14);
        card.addView(ignoredApps, ignoredParams);
        return card;
    }

    private View buildStatsCard() {
        LinearLayout card = card();
        totalSkips = text("0", 34, UiStyles.BRAND_BLUE);
        totalSkips.setTypeface(totalSkips.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(totalSkips, matchWrap());

        TextView totalCaption = text("累计自动跳过", 13, UiStyles.TEXT_SECONDARY);
        LinearLayout.LayoutParams captionParams = matchWrap();
        captionParams.topMargin = dp(2);
        card.addView(totalCaption, captionParams);

        addDivider(card);
        lastSkip = text("还没有跳过记录", 14, UiStyles.TEXT_SECONDARY);
        lastSkip.setLineSpacing(dp(3), 1f);
        card.addView(lastSkip, matchWrap());
        return card;
    }

    private View buildSafetyCard() {
        LinearLayout card = card();
        card.addView(bullet("只处理当前前台应用，离开应用后立即结束本次识别"), matchWrap());
        card.addView(bullet("系统、权限、安装器和桌面界面永不自动点击"), spacedTextParams());
        card.addView(bullet("银行、支付、政务类应用只接受明确的“跳过”文字"), spacedTextParams());
        card.addView(bullet("“下载、安装、支付、同意、确认”等操作始终拒绝"), spacedTextParams());
        card.addView(bullet("应用内弹窗只执行按包绑定、带广告证据的关闭规则"), spacedTextParams());

        Button batterySettings = secondaryButton("打开电池优化设置");
        batterySettings.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (RuntimeException ignored) {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            }
        });
        LinearLayout.LayoutParams buttonParams = matchWrap();
        buttonParams.topMargin = dp(16);
        card.addView(batterySettings, buttonParams);
        return card;
    }

    private void refreshStatus() {
        boolean enabled = isAccessibilityServiceEnabled();
        serviceBadge.setText(enabled ? "自动跳过已就绪" : "需要开启无障碍服务");
        serviceBadge.setTextColor(enabled ? UiStyles.SUCCESS_GREEN : UiStyles.WARNING_AMBER);
        serviceIndicator.setTextColor(enabled ? UiStyles.SUCCESS_GREEN : UiStyles.WARNING_AMBER);
        serviceDescription.setText(enabled
                ? "打开其他应用时会自动工作"
                : "首次使用只需授权一次");
        accessibilityButton.setText(enabled ? "管理无障碍权限" : "开启无障碍服务");

        totalSkips.setText(String.valueOf(preferences.getTotalSkips()));
        long lastTime = preferences.getLastTime();
        if (lastTime == 0L) {
            lastSkip.setText("还没有跳过记录");
        } else {
            String label = preferences.getLastAppLabel();
            if (TextUtils.isEmpty(label)) {
                label = preferences.getLastPackage();
            }
            SimpleDateFormat format = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
            lastSkip.setText(getString(
                    R.string.last_skip_format,
                    label,
                    preferences.getLastMethod(),
                    format.format(new Date(lastTime))));
        }
    }

    private boolean isAccessibilityServiceEnabled() {
        AccessibilityManager manager = (AccessibilityManager)
                getSystemService(Context.ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> services = manager.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_ALL_MASK);
        for (AccessibilityServiceInfo info : services) {
            if (info.getResolveInfo() == null || info.getResolveInfo().serviceInfo == null) {
                continue;
            }
            String packageName = info.getResolveInfo().serviceInfo.packageName;
            String className = info.getResolveInfo().serviceInfo.name;
            if (getPackageName().equals(packageName)
                    && SplashSkipService.class.getName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    private Switch addSwitchRow(
            LinearLayout parent,
            String title,
            String summary,
            boolean checked) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.addView(text(title, 15, UiStyles.TEXT_PRIMARY), matchWrap());
        TextView summaryView = text(summary, 12, UiStyles.TEXT_SECONDARY);
        LinearLayout.LayoutParams summaryParams = matchWrap();
        summaryParams.topMargin = dp(3);
        copy.addView(summaryView, summaryParams);
        row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        row.addView(toggle, wrap());
        row.setOnClickListener(view -> toggle.setChecked(!toggle.isChecked()));
        parent.addView(row, matchWrap());
        return toggle;
    }

    private void addSectionTitle(LinearLayout content, String title) {
        TextView label = text(title, 14, UiStyles.TEXT_PRIMARY);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(24);
        params.bottomMargin = dp(10);
        content.addView(label, params);
    }

    private void addDivider(LinearLayout parent) {
        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(234, 236, 240));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1));
        params.topMargin = dp(16);
        params.bottomMargin = dp(16);
        parent.addView(divider, params);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(UiStyles.outlined(
                Color.WHITE,
                Color.rgb(232, 236, 244),
                18,
                this));
        card.setElevation(dp(1));
        return card;
    }

    private Button primaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(15);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setBackground(UiStyles.rounded(UiStyles.BRAND_BLUE, 12, this));
        return button;
    }

    private Button secondaryButton(String label) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setTextColor(UiStyles.BRAND_BLUE);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(46));
        button.setBackground(UiStyles.outlined(
                Color.rgb(248, 250, 255),
                Color.rgb(191, 211, 255),
                12,
                this));
        return button;
    }

    private TextView bullet(String value) {
        TextView textView = text("•  " + value, 13, UiStyles.TEXT_SECONDARY);
        textView.setLineSpacing(dp(2), 1f);
        return textView;
    }

    private TextView text(String value, float sizeSp, int color) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sizeSp);
        textView.setTextColor(color);
        return textView;
    }

    private void applySystemInsets(View view) {
        view.setOnApplyWindowInsetsListener((target, insets) -> {
            int top;
            int bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            target.setPadding(0, top, 0, bottom);
            return insets;
        });
    }

    private String formatSeconds(int milliseconds) {
        return milliseconds / 1_000 + " 秒";
    }

    private int dp(float value) {
        return UiStyles.dp(this, value);
    }

    private LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams cardParams() {
        return matchWrap();
    }

    private LinearLayout.LayoutParams spacedTextParams() {
        LinearLayout.LayoutParams params = matchWrap();
        params.topMargin = dp(10);
        return params;
    }
}
