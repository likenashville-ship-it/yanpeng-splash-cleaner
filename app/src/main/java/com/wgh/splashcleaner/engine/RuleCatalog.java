package com.wgh.splashcleaner.engine;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Loads the small, auditable selector catalog bundled with the APK. */
public final class RuleCatalog {
    private static final String TAG = "SplashRuleCatalog";

    private final Set<String> exactTexts;
    private final List<Pattern> textPatterns;
    private final List<String> strongIdTokens;
    private final List<String> genericCloseIdTokens;
    private final List<String> adContextTokens;
    private final List<String> dangerousActionTokens;
    private final List<String> excludedSkipContextTokens;

    private RuleCatalog(
            List<String> exactTextValues,
            List<String> textPatternValues,
            List<String> strongIds,
            List<String> genericIds,
            List<String> adTokens,
            List<String> dangerousTokens,
            List<String> excludedTokens) {
        exactTexts = Collections.unmodifiableSet(normalizedSet(exactTextValues));
        textPatterns = Collections.unmodifiableList(compiledPatterns(textPatternValues));
        strongIdTokens = immutableNormalizedList(strongIds);
        genericCloseIdTokens = immutableNormalizedList(genericIds);
        adContextTokens = immutableNormalizedList(adTokens);
        dangerousActionTokens = immutableNormalizedList(dangerousTokens);
        excludedSkipContextTokens = immutableNormalizedList(excludedTokens);
    }

    public static RuleCatalog load(Context context) {
        try (InputStream input = context.getAssets().open("skip_rules.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            if (root.optInt("schema", -1) != 1) {
                throw new IllegalArgumentException("Unsupported rule schema");
            }
            return new RuleCatalog(
                    strings(root.getJSONArray("exactTexts")),
                    strings(root.getJSONArray("textPatterns")),
                    strings(root.getJSONArray("strongIdTokens")),
                    strings(root.getJSONArray("genericCloseIdTokens")),
                    strings(root.getJSONArray("adContextTokens")),
                    strings(root.getJSONArray("dangerousActionTokens")),
                    strings(root.getJSONArray("excludedSkipContextTokens")));
        } catch (Exception error) {
            Log.e(TAG, "Invalid bundled rule catalog; using safe defaults", error);
            return defaults();
        }
    }

    public static RuleCatalog defaults() {
        return new RuleCatalog(
                Arrays.asList(
                        "跳过", "跳過", "略过", "略過", "跳过广告", "跳過廣告",
                        "关闭广告", "關閉廣告", "skip", "skip ad", "close ad",
                        "以后再说", "下次再说", "暂不升级", "稍后更新", "暂不开启",
                        "暂不", "我知道了", "知道了", "不再提示", "忽略",
                        "关闭此条广告", "不感兴趣"),
                Arrays.asList(
                        "^(?:\\d{1,2}\\s*(?:秒|s)?\\s*)?(?:跳过|跳過|略过|略過)(?:广告|廣告)?(?:\\s*\\d{1,2}\\s*(?:秒|s)?)?$",
                        "^(?:\\d{1,2}\\s*s?\\s*)?skip(?:\\s*ad)?(?:\\s*\\d{1,2}\\s*s?)?$"),
                Arrays.asList(
                        "splash_skip", "splashskip", "ad_skip", "adskip", "skip_ad", "skipad",
                        "skip_btn", "skip_button", "skip_view", "skipview", "tt_splash_skip",
                        "ksad_splash_circle_skip", "ksad_splash_skip", "gdt_splash_skip",
                        "baidu_splash_skip", "sig_splash_skip", "tanx_splash_skip",
                        "splash_close", "splashclose", "ad_close", "adclose", "close_ad",
                        "closead", "close_splash"),
                Arrays.asList(
                        "iv_close", "btn_close", "close_btn", "close_button", "close_icon",
                        "image_close", "img_close"),
                Arrays.asList(
                        "广告", "廣告", "推广", "推廣", "splash", "advert", "native_ad",
                        "ad_container", "ad_layout", "ad_logo", "gdt_splash", "ksad_splash"),
                Arrays.asList(
                        "立即下载", "下载", "安装", "打开", "进入", "查看详情", "了解详情",
                        "领取", "购买", "支付", "允许", "同意", "授权", "开启", "提交",
                        "确认", "确定", "继续", "更新", "升级", "注册", "登录", "试用",
                        "预约", "抢购"),
                Arrays.asList(
                        "视频", "視頻", "片头", "片頭", "片尾", "章节", "章節", "歌曲",
                        "引导", "引導", "教程", "动画", "動畫", "剧情", "劇情", "帮助",
                        "幫助", "取消", "退出", "广告设置", "廣告設定"));
    }

    private static List<String> strings(JSONArray array) {
        List<String> result = new ArrayList<>();
        int limit = Math.min(array.length(), 256);
        for (int index = 0; index < limit; index++) {
            String value = array.optString(index, "").trim();
            if (!value.isEmpty() && value.length() <= 160) {
                result.add(value);
            }
        }
        return result;
    }

    private static Set<String> normalizedSet(List<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return result;
    }

    private static List<String> immutableNormalizedList(List<String> values) {
        List<String> result = new ArrayList<>();
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isEmpty()) {
                result.add(normalized);
            }
        }
        return Collections.unmodifiableList(result);
    }

    private static List<Pattern> compiledPatterns(List<String> expressions) {
        List<Pattern> result = new ArrayList<>();
        for (String expression : expressions) {
            if (expression.length() > 200) {
                continue;
            }
            try {
                result.add(Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
            } catch (PatternSyntaxException ignored) {
                Log.w(TAG, "Ignoring invalid text pattern");
            }
        }
        return result;
    }

    static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String compatible = Normalizer.normalize(value, Normalizer.Form.NFKC);
        return compatible.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    boolean isExactText(String value) {
        return exactTexts.contains(normalize(value));
    }

    boolean matchesTextPattern(String value) {
        String normalized = normalize(value);
        if (normalized.isEmpty() || normalized.length() > 80) {
            return false;
        }
        for (Pattern pattern : textPatterns) {
            if (pattern.matcher(normalized).matches()) {
                return true;
            }
        }
        return false;
    }

    boolean hasStrongId(String viewId) {
        return containsAny(normalize(viewId), strongIdTokens);
    }

    boolean hasGenericCloseId(String viewId) {
        return containsAny(normalize(viewId), genericCloseIdTokens);
    }

    boolean hasAdContextToken(String value) {
        return containsAny(normalize(value), adContextTokens);
    }

    boolean hasDangerousActionToken(String value) {
        return containsAny(normalize(value), dangerousActionTokens);
    }

    boolean hasExcludedSkipContextToken(String value) {
        return containsAny(normalize(value), excludedSkipContextTokens);
    }

    private static boolean containsAny(String value, List<String> tokens) {
        if (value.isEmpty()) {
            return false;
        }
        for (String token : tokens) {
            if (value.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
