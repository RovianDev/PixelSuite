package io.github.pixelsuite;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SearchMoney {
    static final String CURRENCY_KEY = "pixelsuite_search_currency";
    static final String RATES_KEY = "pixelsuite_search_rates";
    static final String AUTO_KEY = "pixelsuite_search_rates_auto";
    private static final String[] SOURCES = {
            "https://cdn.jsdelivr.net/npm/@fawazahmed0/currency-api@latest/v1/currencies/usd.min.json",
            "https://latest.currency-api.pages.dev/v1/currencies/usd.min.json"};
    private static final Pattern AMOUNT_FIRST = Pattern.compile("^([0-9][0-9., ]*?)\\s*([A-Za-z]{3}|[$€£¥₹])(?:\\s*(?:to|in|=)?\\s*([A-Za-z]{3}|[$€£¥₹]))?$");
    private static final Pattern CODE_FIRST = Pattern.compile("^([A-Za-z]{3}|[$€£¥₹])\\s*([0-9][0-9.,]*)(?:\\s*(?:to|in|=)?\\s*([A-Za-z]{3}|[$€£¥₹]))?$");
    private static volatile String sRaw;
    private static volatile Map<String, Double> sRates = new HashMap<String, Double>();
    private static volatile long sWhen, sTried;

    static String home(Context c) {
        String v = PixelSearch.string(c, CURRENCY_KEY);
        if (v != null && v.length() == 3) return v.toUpperCase(Locale.ROOT);
        try { return Currency.getInstance(Locale.getDefault()).getCurrencyCode(); } catch (Throwable t) { return "USD"; }
    }

    static boolean auto(Context c) { return PixelSearch.number(c, AUTO_KEY, 1) == 1; }

    static Map<String, Double> rates(Context c) {
        String raw = PixelSearch.string(c, RATES_KEY);
        if (raw == null) raw = "";
        if (raw.equals(sRaw)) return sRates;
        HashMap<String, Double> out = new HashMap<String, Double>();
        long when = 0;
        try {
            int cut = raw.indexOf(';');
            if (cut > 0) {
                when = Long.parseLong(raw.substring(0, cut));
                for (String part : raw.substring(cut + 1).split(",")) {
                    int colon = part.indexOf(':');
                    if (colon == 3) out.put(part.substring(0, 3), Double.parseDouble(part.substring(4)));
                }
            }
        } catch (Throwable ignored) { }
        sRates = out; sWhen = when; sRaw = raw;
        return out;
    }

    static long updatedAt(Context c) { rates(c); return sWhen * 1000L; }

    private static String code(String token) {
        if (token == null) return null;
        switch (token) {
            case "$": return "USD";
            case "€": return "EUR";
            case "£": return "GBP";
            case "¥": return "JPY";
            case "₹": return "INR";
            default: return token.toUpperCase(Locale.ROOT);
        }
    }

    static String flag(String code) {
        if (code == null || code.length() != 3) return "";
        String two = code.substring(0, 2);
        if ("EUR".equals(code)) two = "EU";
        else if ("ANG".equals(code)) two = "CW";
        else if (code.charAt(0) == 'X') return "🌐";
        return new String(Character.toChars(0x1F1E6 + two.charAt(0) - 'A')) + new String(Character.toChars(0x1F1E6 + two.charAt(1) - 'A'));
    }

    static String name(String code) {
        try { return Currency.getInstance(code).getDisplayName(Locale.getDefault()); } catch (Throwable t) { return code; }
    }

    static List<String> codes(Context c) {
        ArrayList<String> out = new ArrayList<String>();
        for (String code : rates(c).keySet()) if (real(code)) out.add(code);
        if (out.isEmpty()) for (String s : new String[]{"USD", "EUR", "GBP", "KWD", "SAR", "AED", "QAR", "BHD", "OMR", "JPY", "CNY", "INR", "TRY", "EGP", "CAD", "AUD", "CHF"}) out.add(s);
        Collections.sort(out);
        return out;
    }

    static String format(double value, String code) {
        int digits = 2;
        try { digits = Math.max(0, Currency.getInstance(code).getDefaultFractionDigits()); } catch (Throwable ignored) { }
        double abs = Math.abs(value);
        if (abs > 0 && abs < 1) digits = Math.max(digits, 4);
        StringBuilder pattern = new StringBuilder("#,##0");
        if (digits > 0) { pattern.append('.'); for (int i = 0; i < digits; i++) pattern.append(i < 2 ? '0' : '#'); }
        return new DecimalFormat(pattern.toString(), DecimalFormatSymbols.getInstance(Locale.US)).format(value);
    }

    static String[] convert(Context c, String query) {
        String q = query.trim();
        String amount, from, to;
        Matcher m = AMOUNT_FIRST.matcher(q);
        if (m.matches()) { amount = m.group(1); from = code(m.group(2)); to = code(m.group(3)); }
        else {
            m = CODE_FIRST.matcher(q);
            if (!m.matches()) return null;
            from = code(m.group(1)); amount = m.group(2); to = code(m.group(3));
        }
        Map<String, Double> rates = rates(c);
        boolean symbol = from.length() == 3 && !Character.isLetter(q.charAt(0)) && !Character.isDigit(q.charAt(0));
        if (rates.isEmpty()) {
            if (!symbol && !known(from)) return null;
            return new String[]{q, I18n.t("Open Pixel Suite once to download currency rates")};
        }
        Double a = rates.get(from);
        if (a == null) return null;
        String home = home(c);
        if (to == null) to = from.equals(home) ? ("USD".equals(from) ? "EUR" : "USD") : home;
        Double b = rates.get(to);
        if (b == null) return null;
        double value;
        try { value = Double.parseDouble(amount.replace(",", "").replace(" ", "")); } catch (Throwable t) { return null; }
        double result = value / a * b;
        return new String[]{flag(from) + " " + format(value, from) + " " + from + " =", flag(to) + " " + format(result, to) + " " + to};
    }

    private static boolean real(String code) {
        try { String n = Currency.getInstance(code).getDisplayName(Locale.US); return n != null && !n.equals(code); } catch (Throwable t) { return false; }
    }

    private static boolean known(String code) {
        try { return Currency.getInstance(code) != null; } catch (Throwable t) { return false; }
    }

    static void refreshSoon(final Context c) {
        long now = System.currentTimeMillis();
        if (now - sTried < 30 * 60 * 1000L) return;
        if (!auto(c) || PixelSearch.number(c, PixelSearch.KEY, 0) != 1) return;
        if (now - updatedAt(c) < 12 * 60 * 60 * 1000L) return;
        sTried = now;
        new Thread(() -> update(c), "pixelsuite-rates").start();
    }

    static boolean update(Context c) {
        sTried = System.currentTimeMillis();
        for (String source : SOURCES) {
            try {
                String body = fetch(source);
                org.json.JSONObject usd = new org.json.JSONObject(body).getJSONObject("usd");
                StringBuilder b = new StringBuilder();
                b.append(System.currentTimeMillis() / 1000L).append(';');
                int n = 0;
                ArrayList<String> keys = new ArrayList<String>();
                for (Iterator<String> it = usd.keys(); it.hasNext(); ) keys.add(it.next());
                Collections.sort(keys);
                for (String k : keys) {
                    if (k.length() != 3) continue;
                    String code = k.toUpperCase(Locale.ROOT);
                    if (!code.matches("[A-Z]{3}") || !real(code)) continue;
                    double rate = usd.optDouble(k, 0);
                    if (!(rate > 0) || Double.isInfinite(rate)) continue;
                    if (n++ > 0) b.append(',');
                    b.append(code).append(':').append(rate);
                }
                if (n < 20) continue;
                if (Root.run("settings put secure " + RATES_KEY + " '" + b + "'") == null) return false;
                PixelSearch.TEXT.remove(RATES_KEY);
                return true;
            } catch (Throwable t) { android.util.Log.w("PixelSuite", "search: rates " + t); }
        }
        return false;
    }

    private static String fetch(String address) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
        try {
            con.setConnectTimeout(7000); con.setReadTimeout(9000);
            if (con.getResponseCode() != 200) throw new java.io.IOException("HTTP " + con.getResponseCode());
            InputStream in = con.getInputStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) { out.write(buf, 0, n); if (out.size() > 400000) break; }
            return out.toString("UTF-8");
        } finally { con.disconnect(); }
    }
}
