package com.wgh.splashcleaner.engine;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/** Package-bound, ad-only rules derived from the attributed upstream ruleset. */
public final class PopupAdRuleCatalog {
    private static final String TAG = "PopupAdRuleCatalog";
    private static final int MAX_RULES_PER_PACKAGE = 64;

    private final Map<String, List<Rule>> packageRules;
    private final int ruleCount;
    private final String sourceCommit;

    private PopupAdRuleCatalog(
            Map<String, List<Rule>> rules,
            int includedRules,
            String commit) {
        packageRules = Collections.unmodifiableMap(rules);
        ruleCount = includedRules;
        sourceCommit = commit;
    }

    public static PopupAdRuleCatalog load(Context context) {
        try (InputStream input = context.getAssets().open("popup_ad_rules.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            JSONObject root = new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
            if (root.optInt("schema", -1) != 1) {
                throw new IllegalArgumentException("Unsupported popup rule schema");
            }
            JSONObject source = root.getJSONObject("source");
            JSONObject packageObject = root.getJSONObject("packageRules");
            Map<String, List<Rule>> parsed = new HashMap<>();
            int includedRules = 0;
            Iterator<String> hashes = packageObject.keys();
            while (hashes.hasNext()) {
                String hash = hashes.next();
                JSONArray values = packageObject.optJSONArray(hash);
                if (values == null) {
                    continue;
                }
                List<Rule> rules = new ArrayList<>();
                int limit = Math.min(values.length(), MAX_RULES_PER_PACKAGE);
                for (int index = 0; index < limit; index++) {
                    JSONObject value = values.optJSONObject(index);
                    if (value == null) {
                        continue;
                    }
                    Rule rule = Rule.parse(value);
                    if (rule != null) {
                        rules.add(rule);
                        includedRules++;
                    }
                }
                if (!rules.isEmpty()) {
                    parsed.put(hash, Collections.unmodifiableList(rules));
                }
            }
            return new PopupAdRuleCatalog(
                    parsed,
                    includedRules,
                    source.optString("commit", "unknown"));
        } catch (Exception error) {
            Log.e(TAG, "Popup ad rules are invalid; disabling them", error);
            return new PopupAdRuleCatalog(Collections.emptyMap(), 0, "invalid");
        }
    }

    public List<Rule> rulesForPackage(String packageName) {
        if (packageName == null) {
            return Collections.emptyList();
        }
        List<Rule> rules = packageRules.get(String.valueOf(packageName.hashCode()));
        return rules == null ? Collections.emptyList() : rules;
    }

    public int getRuleCount() {
        return ruleCount;
    }

    public int getPackageCount() {
        return packageRules.size();
    }

    public String getSourceCommit() {
        return sourceCommit;
    }

    public static final class Rule {
        private final String key;
        private final PopupSelector trigger;
        private final PopupSelector action;
        private final long delayMillis;

        private Rule(
                String ruleKey,
                PopupSelector triggerSelector,
                PopupSelector actionSelector,
                long delay) {
            key = ruleKey;
            trigger = triggerSelector;
            action = actionSelector;
            delayMillis = delay;
        }

        static Rule parse(JSONObject value) {
            String key = value.optString("key", "").trim();
            PopupSelector trigger = PopupSelector.trigger(value.optString("trigger", ""));
            PopupSelector action = PopupSelector.action(value.optString("action", ""));
            if (key.isEmpty() || !trigger.isValid() || !action.isValid()) {
                return null;
            }
            long delay = Math.max(0L, Math.min(15_000L, value.optLong("delayMs", 0L)));
            return new Rule(key, trigger, action, delay);
        }

        public String getKey() {
            return key;
        }

        public PopupSelector getTrigger() {
            return trigger;
        }

        public PopupSelector getAction() {
            return action;
        }

        public long getDelayMillis() {
            return delayMillis;
        }
    }
}
