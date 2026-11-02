package com.wgh.splashcleaner.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;

import com.wgh.splashcleaner.prefs.AppPreferences;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class IgnoredAppsActivity extends Activity {
    private final List<InstalledApp> allApps = new ArrayList<>();
    private final List<InstalledApp> visibleApps = new ArrayList<>();
    private AppPreferences preferences;
    private AppsAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferences = new AppPreferences(this);
        UiStyles.configureSystemBars(getWindow());
        setContentView(buildContent());
        loadApps();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiStyles.PAGE_BACKGROUND);
        applySystemInsets(root);

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(12), dp(12), dp(20), dp(8));

        TextView back = text("‹", 38, UiStyles.TEXT_PRIMARY);
        back.setGravity(Gravity.CENTER);
        back.setContentDescription("返回");
        back.setOnClickListener(view -> finish());
        toolbar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(48)));

        LinearLayout headings = new LinearLayout(this);
        headings.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("忽略应用", 22, UiStyles.TEXT_PRIMARY);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        headings.addView(title, matchWrap());
        headings.addView(text("开启开关后，该应用将完全不处理", 12, UiStyles.TEXT_SECONDARY), matchWrap());
        toolbar.addView(headings, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(toolbar, matchWrap());

        EditText search = new EditText(this);
        search.setSingleLine(true);
        search.setHint("搜索应用名称或包名");
        search.setTextSize(14);
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(UiStyles.outlined(
                Color.WHITE,
                Color.rgb(224, 228, 236),
                12,
                this));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48));
        searchParams.leftMargin = dp(20);
        searchParams.rightMargin = dp(20);
        searchParams.topMargin = dp(8);
        searchParams.bottomMargin = dp(12);
        root.addView(search, searchParams);

        ListView listView = new ListView(this);
        listView.setDividerHeight(0);
        listView.setClipToPadding(false);
        listView.setPadding(dp(12), 0, dp(12), dp(20));
        adapter = new AppsAdapter();
        listView.setAdapter(adapter);
        root.addView(listView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f));

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence value, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence value, int start, int before, int count) {
                filter(value == null ? "" : value.toString());
            }

            @Override
            public void afterTextChanged(Editable value) {
            }
        });
        return root;
    }

    private void loadApps() {
        PackageManager packageManager = getPackageManager();
        Intent launcher = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> activities = packageManager.queryIntentActivities(launcher, 0);
        Map<String, InstalledApp> unique = new HashMap<>();
        for (ResolveInfo activity : activities) {
            if (activity.activityInfo == null
                    || activity.activityInfo.packageName == null
                    || getPackageName().equals(activity.activityInfo.packageName)) {
                continue;
            }
            String packageName = activity.activityInfo.packageName;
            String label = activity.loadLabel(packageManager).toString();
            unique.put(packageName, new InstalledApp(label, packageName));
        }
        allApps.clear();
        allApps.addAll(unique.values());
        Collator collator = Collator.getInstance(Locale.CHINA);
        Collections.sort(allApps, (left, right) -> collator.compare(left.label, right.label));
        filter("");
    }

    private void filter(String query) {
        String normalized = query.trim().toLowerCase(Locale.ROOT);
        visibleApps.clear();
        for (InstalledApp app : allApps) {
            if (normalized.isEmpty()
                    || app.label.toLowerCase(Locale.ROOT).contains(normalized)
                    || app.packageName.toLowerCase(Locale.ROOT).contains(normalized)) {
                visibleApps.add(app);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private final class AppsAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return visibleApps.size();
        }

        @Override
        public InstalledApp getItem(int position) {
            return visibleApps.get(position);
        }

        @Override
        public long getItemId(int position) {
            return getItem(position).packageName.hashCode();
        }

        @Override
        public View getView(int position, View reusable, ViewGroup parent) {
            RowHolder holder;
            if (reusable == null) {
                LinearLayout row = new LinearLayout(IgnoredAppsActivity.this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(12), dp(8), dp(12));
                row.setBackground(UiStyles.rounded(Color.WHITE, 12, IgnoredAppsActivity.this));

                LinearLayout copy = new LinearLayout(IgnoredAppsActivity.this);
                copy.setOrientation(LinearLayout.VERTICAL);
                TextView label = text("", 15, UiStyles.TEXT_PRIMARY);
                TextView packageName = text("", 11, UiStyles.TEXT_SECONDARY);
                LinearLayout.LayoutParams packageParams = matchWrap();
                packageParams.topMargin = dp(3);
                copy.addView(label, matchWrap());
                copy.addView(packageName, packageParams);
                row.addView(copy, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

                Switch ignored = new Switch(IgnoredAppsActivity.this);
                row.addView(ignored, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
                holder = new RowHolder(row, label, packageName, ignored);
                row.setTag(holder);
                reusable = row;
            } else {
                holder = (RowHolder) reusable.getTag();
            }

            InstalledApp app = getItem(position);
            holder.label.setText(app.label);
            holder.packageName.setText(app.packageName);
            holder.toggle.setOnCheckedChangeListener(null);
            holder.toggle.setChecked(preferences.isPackageIgnored(app.packageName));
            holder.toggle.setContentDescription("忽略 " + app.label);
            holder.toggle.setOnCheckedChangeListener((button, checked) ->
                    preferences.setPackageIgnored(app.packageName, checked));
            holder.row.setOnClickListener(view -> holder.toggle.setChecked(!holder.toggle.isChecked()));

            ListView.LayoutParams rowParams = new ListView.LayoutParams(
                    ListView.LayoutParams.MATCH_PARENT,
                    ListView.LayoutParams.WRAP_CONTENT);
            reusable.setLayoutParams(rowParams);
            reusable.setPadding(dp(14), dp(12), dp(8), dp(12));
            return reusable;
        }
    }

    private static final class InstalledApp {
        private final String label;
        private final String packageName;

        private InstalledApp(String appLabel, String appPackage) {
            label = appLabel;
            packageName = appPackage;
        }
    }

    private static final class RowHolder {
        private final LinearLayout row;
        private final TextView label;
        private final TextView packageName;
        private final Switch toggle;

        private RowHolder(LinearLayout rowView, TextView labelView, TextView packageView, Switch switchView) {
            row = rowView;
            label = labelView;
            packageName = packageView;
            toggle = switchView;
        }
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

    private int dp(float value) {
        return UiStyles.dp(this, value);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }
}
