package com.wgh.splashcleaner.accessibility;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class PackageSafety {
    private static final Set<String> SENSITIVE_PACKAGES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
            "com.eg.android.alipaygphone",
            "com.tencent.mm",
            "com.unionpay",
            "com.unionpay.tsmservice",
            "com.icbc",
            "com.chinamworld.main"
    )));

    private static final List<String> SENSITIVE_LABEL_TOKENS = Arrays.asList(
            "银行", "銀行", "支付", "钱包", "錢包", "证券", "證券", "期货", "期貨",
            "保险", "保險", "政务", "政務", "税务", "稅務", "医保", "身份"
    );

    private PackageSafety() {
    }

    static Set<String> findHomePackages(Context context) {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
        List<ResolveInfo> homes = context.getPackageManager().queryIntentActivities(homeIntent, 0);
        Set<String> result = new HashSet<>();
        for (ResolveInfo home : homes) {
            if (home.activityInfo != null && home.activityInfo.packageName != null) {
                result.add(home.activityInfo.packageName);
            }
        }
        return result;
    }

    static boolean isSystemSurface(String packageName, Set<String> homePackages) {
        if (packageName == null || packageName.isEmpty()) {
            return true;
        }
        String value = packageName.toLowerCase(Locale.ROOT);
        if (homePackages.contains(packageName)) {
            return true;
        }
        return value.equals("android")
                || value.equals("com.android.systemui")
                || value.equals("com.android.settings")
                || value.contains("permissioncontroller")
                || value.contains("packageinstaller")
                || value.contains("inputmethod")
                || value.equals("com.miui.securitycenter")
                || value.equals("com.lbe.security.miui")
                || value.equals("com.coloros.safecenter")
                || value.equals("com.oplus.safecenter")
                || value.equals("com.huawei.systemmanager")
                || value.equals("com.vivo.permissionmanager");
    }

    static boolean isSensitiveApp(Context context, String packageName) {
        String normalizedPackage = packageName == null
                ? ""
                : packageName.toLowerCase(Locale.ROOT);
        if (SENSITIVE_PACKAGES.contains(normalizedPackage)
                || normalizedPackage.contains("bank")
                || normalizedPackage.contains("wallet")) {
            return true;
        }
        try {
            PackageManager packageManager = context.getPackageManager();
            ApplicationInfo applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            String label = packageManager.getApplicationLabel(applicationInfo).toString();
            for (String token : SENSITIVE_LABEL_TOKENS) {
                if (label.contains(token)) {
                    return true;
                }
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            // Package visibility can hide labels on recent Android versions. Package rules still apply.
        }
        return false;
    }
}
