package dev.aller.command;

/** Fuzzy matching shared by the palette and the launcher. */
public final class Search {
    private Search() {}

    /**
     * Scores how well {@code q} (lower case) matches {@code text}: a contiguous match beats scattered
     * letters, and matches at the start of a word beat matches in the middle. Zero means no match.
     *
     * @param hits set to the matched characters of {@code text}, for highlighting; may be null
     */
    public static float match(String q, String text, boolean[] hits) {
        String t = text.toLowerCase();
        int idx = t.indexOf(q);
        if (idx >= 0) {
            if (hits != null) for (int i = 0; i < q.length(); i++) hits[idx + i] = true;
            boolean wordStart = idx == 0 || !Character.isLetterOrDigit(t.charAt(idx - 1));
            return 100 + (idx == 0 ? 40 : wordStart ? 25 : 0) - idx * 0.5f - t.length() * 0.05f;
        }
        boolean[] tmp = new boolean[t.length()];
        float score = 30;
        int from = 0, last = -2;
        for (int i = 0; i < q.length(); i++) {
            char ch = q.charAt(i);
            if (ch == ' ') continue;
            int found = t.indexOf(ch, from);
            if (found < 0) return 0;
            boolean wordStart = found == 0 || !Character.isLetterOrDigit(t.charAt(found - 1));
            score += found == last + 1 ? 6 : wordStart ? 8 : 0;
            score -= (found - from) * 0.8f;
            tmp[found] = true;
            last = found;
            from = found + 1;
        }
        if (hits != null) System.arraycopy(tmp, 0, hits, 0, tmp.length);
        return Math.max(score, 1);
    }
}
