package com.wgh.splashcleaner.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A deliberately small, auditable subset of LiTiaoTiao selector syntax. */
public final class PopupSelector {
    private static final int MAX_TERMS = 6;

    private final List<Term> terms;

    private PopupSelector(List<Term> selectorTerms) {
        terms = Collections.unmodifiableList(selectorTerms);
    }

    public static PopupSelector trigger(String raw) {
        String[] parts = raw == null ? new String[0] : raw.split("&");
        List<Term> parsed = new ArrayList<>();
        for (String part : parts) {
            if (parsed.size() >= MAX_TERMS) {
                break;
            }
            Term term = Term.parse(part);
            if (term != null) {
                parsed.add(term);
            }
        }
        return new PopupSelector(parsed);
    }

    public static PopupSelector action(String raw) {
        List<Term> parsed = new ArrayList<>();
        Term term = Term.parse(raw);
        if (term != null) {
            parsed.add(term);
        }
        return new PopupSelector(parsed);
    }

    public boolean isValid() {
        return !terms.isEmpty();
    }

    public int getTermCount() {
        return terms.size();
    }

    public boolean matchesTerm(int index, CandidateSignals signals) {
        return index >= 0 && index < terms.size() && terms.get(index).matches(signals);
    }

    public boolean matches(CandidateSignals signals) {
        return terms.size() == 1 && terms.get(0).matches(signals);
    }

    private static final class Term {
        private final String value;
        private final boolean exact;

        private Term(String termValue, boolean exactMatch) {
            value = termValue;
            exact = exactMatch;
        }

        static Term parse(String raw) {
            String cleaned = raw == null ? "" : raw.trim();
            if (cleaned.isEmpty() || cleaned.startsWith("+") || cleaned.startsWith("|")) {
                return null;
            }
            boolean exact = cleaned.startsWith("=");
            if (exact) {
                cleaned = cleaned.substring(1).trim();
            }
            String normalized = RuleCatalog.normalize(cleaned);
            if (normalized.isEmpty() || normalized.length() > 120) {
                return null;
            }
            return new Term(normalized, exact);
        }

        boolean matches(CandidateSignals signals) {
            String text = RuleCatalog.normalize(signals.getText());
            String description = RuleCatalog.normalize(signals.getContentDescription());
            String fullId = RuleCatalog.normalize(signals.getViewId());
            String shortId = shortId(fullId);
            if (exact) {
                return value.equals(text)
                        || value.equals(description)
                        || value.equals(fullId)
                        || value.equals(shortId);
            }
            return contains(text, value)
                    || contains(description, value)
                    || contains(fullId, value)
                    || contains(shortId, value);
        }

        private static boolean contains(String candidate, String expected) {
            return !candidate.isEmpty() && candidate.contains(expected);
        }

        private static String shortId(String viewId) {
            int slash = viewId.lastIndexOf('/');
            return slash >= 0 && slash + 1 < viewId.length()
                    ? viewId.substring(slash + 1)
                    : viewId;
        }
    }
}
