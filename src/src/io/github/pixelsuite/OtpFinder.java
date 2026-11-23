package io.github.pixelsuite;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class OtpFinder {
    private OtpFinder() {}

    private static final String[] WORDS = {
        "otp", "code", "passcode", "password", "pin", "verification", "verify", "one-time", "one time", "token", "2fa", "authentication",

        "código", "codigo", "clave", "contraseña", "verificación", "verificacion", "senha", "verificação", "verificacao",
        "mot de passe", "vérification", "codice", "verifica", "passwort", "kennwort", "bestätigung", "bestaetigung", "tan", "einmal",
        "kod", "kodu", "şifre", "sifre", "doğrulama", "dogrulama", "parola", "kode", "sandi", "verifikasi",

        "код", "пароль", "подтвержд", "проверочн",

        "رمز", "كود", "التحقق", "تحقق", "كلمة المرور", "كلمة السر", "لمرة واحدة", "الرقم السري",
        "کد", "تایید", "تأیید", "گذرواژه", "یکبار", "یک بار",
        "קוד", "סיסמה", "סיסמא", "אימות", "חד פעמי", "חד-פעמי",

        "कोड", "ओटीपी", "पासवर्ड", "सत्यापन", "पिन", "কোড", "ওটিপি", "পাসওয়ার্ড", "যাচাই", "পিন",

        "验证码", "驗證碼", "校验码", "校驗碼", "动态码", "動態碼", "密码", "密碼", "验证", "驗證", "口令",
        "コード", "認証", "確認番号", "暗証", "パスワード", "ワンタイム", "인증", "코드", "비밀번호",
    };

    private static final String[] MONEY = {
        "$", "€", "£", "¥", "₹", "₽", "₺", "₩", "kwd", "kd", "usd", "eur", "gbp", "inr", "rs", "sar", "aed", "qar", "bhd", "omr", "egp",
        "rub", "try", "tl", "idr", "rp", "bdt", "tk", "jpy", "cny", "rmb", "ils", "nis", "irr", "د.ك", "ر.س", "د.إ", "ريال", "دينار",
        "درهم", "جنيه", "تومان", "ریال", "руб", "元", "円", "원", "₪", "टका", "रुपये", "৳", "টাকা",
    };

    private static final String[] NOT_A_CODE = {
        "ending", "ends with", "ends in", "card no", "a/c", "acct", "account no", "ref", "call", "tel", "termina", "terminada", "finissant",
        "endet", "biten", "berakhiran", "оканчива", "تنتهي", "المنتهية", "آخر", "حساب", "منتهی", "המסתיים", "שמסתיים", "समाप्त", "শেষ",
        "尾号", "尾號", "末尾", "下4桁", "끝자리",
    };

    private static final Pattern MIXED = Pattern.compile("(?<![\\p{L}\\p{N}-])([A-Za-z0-9]{3,5}-[A-Za-z0-9]{3,5}|[A-Za-z0-9]{4,10})(?![\\p{L}\\p{N}-])");

    static String find(CharSequence message) {
        if (message == null || message.length() < 4 || message.length() > 4000) return null;
        String original = ascii(message);
        String s = original.toLowerCase(java.util.Locale.ROOT);
        ArrayList<int[]> words = words(s);
        if (words.isEmpty()) return null;

        String best = null;
        int bestScore = Integer.MAX_VALUE, count = 0;
        for (int a = 0; a < s.length(); a++) {
            if (!digit(s, a)) continue;
            int b = a;
            while (digit(s, b)) b++;
            int run = b - a;
            if (run == 3 && b + 3 < s.length() + 1 && (s.charAt(b) == ' ' || s.charAt(b) == '-') && digit(s, b + 1) && digit(s, b + 2) && digit(s, b + 3) && !digit(s, b + 4)) b += 4;
            else if (run < 4 || run > 8) { a = b; continue; }
            int from = a >= 2 && s.charAt(a - 1) == '-' && s.charAt(a - 2) == 'g' ? a - 2 : a;
            boolean alone = !joins(s, from - 1) && !joins(s, b);
            int start = a;
            a = b;
            if (!alone || !plausible(s, from, b)) continue;
            String code = s.substring(start, b).replace(" ", "").replace("-", "");
            a = start;
            count++;
            int len = code.length();
            int far = distance(words, a, b);
            if (len == 4 && code.compareTo("1990") >= 0 && code.compareTo("2099") <= 0 && far > 12) { count--; a = b; continue; }
            int score = far + (len == 6 ? 0 : len == 4 || len == 5 || len == 8 ? 6 : 12);
            if (score < bestScore) { bestScore = score; best = code; }
            a = b;
        }

        String mixed = null;
        int mixedScore = Integer.MAX_VALUE;
        Matcher x = MIXED.matcher(original);
        while (x.find()) {
            String code = x.group(1);
            int digits = 0, letters = 0, capitals = 0;
            for (int i = 0; i < code.length(); i++) { char c = code.charAt(i); if (c >= '0' && c <= '9') digits++; else if (c != '-') { letters++; if (c <= 'Z') capitals++; } }
            if (letters == 0) continue;
            boolean allCaps = capitals == letters;
            if (digits == 0 && !(allCaps && code.length() >= 6 && code.length() <= 8 && code.indexOf('-') < 0)) continue;
            if (digits == 1 && !allCaps) continue;
            if (!plausible(s, x.start(1), x.end(1))) continue;
            int after = Integer.MAX_VALUE, before = Integer.MAX_VALUE;
            for (int[] w : words) {
                if (w[1] <= x.start(1)) after = Math.min(after, x.start(1) - w[1]);
                else if (w[0] >= x.end(1)) before = Math.min(before, w[0] - x.end(1));
                else { after = before = Integer.MAX_VALUE; break; }
            }
            int reach = digits == 0 ? 4 : 30;

            boolean codeLike = digits >= 2 || (digits >= 1 && allCaps && code.length() >= 6 && code.length() <= 8 && code.indexOf('-') < 0);
            int score = after <= reach ? after : codeLike && before <= 28 ? before + 10 : Integer.MAX_VALUE;

            if (digits == 0 && score != Integer.MAX_VALUE) {
                String raw = s.substring(x.start(1) - after, x.start(1));
                String gap = raw.trim();
                if (raw.indexOf('\n') >= 0 || raw.indexOf('\r') >= 0 || !(gap.equals(":") || gap.equals("is") || gap.equals("is:") || gap.equals("-"))) score = Integer.MAX_VALUE;
                else if (x.end(1) < s.length() && (s.charAt(x.end(1)) == ',' || s.charAt(x.end(1)) == '!')) score = Integer.MAX_VALUE;
            }
            if (score < mixedScore) { mixedScore = score; mixed = code; }
        }
        if (mixed != null && (best == null || mixedScore < bestScore)) return mixed;
        if (best != null && (bestScore <= 160 || count == 1 && bestScore <= 600)) return best;
        return null;
    }

    private static boolean digit(String s, int i) { return i >= 0 && i < s.length() && s.charAt(i) >= '0' && s.charAt(i) <= '9'; }

    private static boolean joins(String s, int i) {
        if (i < 0 || i >= s.length()) return false;
        char c = s.charAt(i);
        if (c >= '0' && c <= '9') return true;
        if (!Character.isLetter(c)) return false;
        return !(Character.isIdeographic(c) || (c >= 0x3040 && c <= 0x30ff) || (c >= 0xac00 && c <= 0xd7af) || (c >= 0x1100 && c <= 0x11ff) || (c >= 0x0e00 && c <= 0x0e7f));
    }

    static boolean mentionsCode(CharSequence text) {
        return text != null && text.length() > 0 && text.length() < 200 && !words(ascii(text).toLowerCase(java.util.Locale.ROOT)).isEmpty();
    }

    private static String ascii(CharSequence message) {
        StringBuilder out = new StringBuilder(message.length());
        for (int i = 0; i < message.length(); i++) {
            char c = message.charAt(i);
            if (c > '9' && Character.isDigit(c)) { int d = Character.digit(c, 10); out.append(d >= 0 ? (char) ('0' + d) : c); }
            else out.append(c == ' ' || c == ' ' ? ' ' : c);
        }
        return out.toString();
    }

    private static ArrayList<int[]> words(String s) {
        ArrayList<int[]> out = new ArrayList<int[]>();
        for (String w : WORDS) {
            boolean whole = w.length() <= 5 && w.charAt(0) < 0x250;
            for (int at = s.indexOf(w); at >= 0; at = s.indexOf(w, at + 1)) {
                int end = at + w.length();
                if (whole && ((at > 0 && Character.isLetter(s.charAt(at - 1))) || (end < s.length() && Character.isLetter(s.charAt(end))))) continue;
                out.add(new int[]{at, end});
            }
        }
        return out;
    }

    private static int distance(ArrayList<int[]> words, int a, int b) {
        int best = Integer.MAX_VALUE;
        for (int[] w : words) {
            int d = w[1] <= a ? a - w[1] : w[0] >= b ? (w[0] - b) + 10 : 0;
            best = Math.min(best, d);
        }
        return best;
    }

    private static boolean plausible(String s, int a, int b) {
        char before = a > 0 ? s.charAt(a - 1) : ' ', before2 = a > 1 ? s.charAt(a - 2) : ' ';
        char after = b < s.length() ? s.charAt(b) : ' ', after2 = b + 1 < s.length() ? s.charAt(b + 1) : ' ';
        if (before == '*' || before == '#' || before == '+' || before == 'x' || before == '/' || before == ':' || before == '@' || before == '=' || before == '_') return false;
        if ((before == '.' || before == ',' || before == '-') && Character.isDigit(before2)) return false;
        if ((after == '.' || after == ',' || after == '-' || after == '/' || after == ':') && Character.isDigit(after2)) return false;
        if (after == '%' || after == '*' || after == '@' || after == '_' || after == '/') return false;
        String left = s.substring(Math.max(0, a - 16), a), right = s.substring(b, Math.min(s.length(), b + 8));
        String nearLeft = left.length() > 5 ? left.substring(left.length() - 5) : left, nearRight = right.length() > 5 ? right.substring(0, 5) : right;
        for (String money : MONEY) {
            boolean letters = Character.isLetter(money.charAt(0)) && money.charAt(0) < 0x250;
            if (near(nearLeft, money, letters) || near(nearRight, money, letters)) return false;
        }
        for (String w : NOT_A_CODE) if (left.contains(w)) return false;
        return true;
    }

    private static boolean near(String where, String what, boolean wholeWord) {
        int at = where.indexOf(what);
        if (at < 0) return false;
        if (!wholeWord) return true;
        int end = at + what.length();
        return !(at > 0 && Character.isLetter(where.charAt(at - 1))) && !(end < where.length() && Character.isLetter(where.charAt(end)));
    }
}
