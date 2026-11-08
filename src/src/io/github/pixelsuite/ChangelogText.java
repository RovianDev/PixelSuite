package io.github.pixelsuite;

final class ChangelogText {
    private ChangelogText() { }
    static String[] split(String line) {
        String s = line.trim();
        if (s.startsWith("\u2022")) return new String[]{"\u2022", s.substring(1).trim()};
        int space = s.indexOf(' ');
        if (space > 0 && space <= 8) {
            int first = s.codePointAt(0);
            if (!Character.isLetterOrDigit(first) && first > 0x2000) return new String[]{s.substring(0, space), s.substring(space + 1).trim()};
        }
        return new String[]{"\u2022", s};
    }

    static CharSequence item(String source) {
        int colon = source.indexOf(": ");
        if (colon < 0) colon = source.indexOf("\uff1a");
        if (colon <= 0 || colon > 48 || source.indexOf("**") >= 0 || source.substring(0, colon).indexOf(". ") >= 0) return format(source);
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder();
        text.append(source.substring(0, colon + 1));
        text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), 0, colon + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.append(format(source.substring(colon + 1)));
        return text;
    }

    static CharSequence format(String source) {
        android.text.SpannableStringBuilder text = new android.text.SpannableStringBuilder();
        int at = 0;
        while (at < source.length()) {
            int open = source.indexOf("**", at);
            if (open < 0) { text.append(source.substring(at)); break; }
            int close = source.indexOf("**", open + 2);
            if (close < 0) { text.append(source.substring(at)); break; }
            text.append(source.substring(at, open));
            int start = text.length();
            text.append(source.substring(open + 2, close));
            if (text.length() > start) text.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.BOLD), start, text.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            at = close + 2;
        }
        return text;
    }
}
