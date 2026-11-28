package io.github.pixelsuite;

import android.content.Context;
import android.graphics.Color;
import android.provider.Settings;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

final class PixelSearch {
    static final String PAGE = "pixel_search";
    static final String KEY = "pixelsuite_search_bar";
    static final String THEME_KEY = "pixelsuite_search_theme";
    static final String COLOR_KEY = "pixelsuite_search_color";
    static final String SHELL_KEY = "pixelsuite_search_shell";
    static final String PILL_KEY = "pixelsuite_search_pill";
    static final String GLASS_KEY = "pixelsuite_search_glass";
    static final String SLOTS_KEY = "pixelsuite_search_slots";
    static final String COLORFUL_KEY = "pixelsuite_search_colorful";
    static final String WIDTH_KEY = "pixelsuite_search_width";
    static final String HEIGHT_KEY = "pixelsuite_search_height";
    static final java.util.Map<String, String> TEXT = new java.util.concurrent.ConcurrentHashMap<String, String>();
    static final String[] SYSTEM_COLORS = {"auto", "system2", "system3"};
    static final String SCOPES_KEY = "pixelsuite_search_scopes";
    static final String FAVS_KEY = "pixelsuite_search_favs";
    static final String DARK_KEY = "pixelsuite_search_dark";
    static final String LOCK_KEY = "pixelsuite_search_lock";
    static final String BACKDROP_KEY = "pixelsuite_search_backdrop";
    static final String FROST_KEY = "pixelsuite_search_frost";
    static final String FEED_BACKDROP_KEY = "pixelsuite_search_feed_backdrop";
    static final String HEARTS_KEY = "pixelsuite_search_hearts";
    static final String LEAD_KEY = "pixelsuite_search_lead";
    static final String STARRED = "pixelsuitestarredcontacts";

    static List<String[]> hearts(Context c) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        String raw = string(c, HEARTS_KEY);
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) { String[] f = line.split("\t", -1); if (f.length >= 3 && !f[2].isEmpty()) out.add(f); }
        return out;
    }

    static String encodeHearts(List<String[]> list) {
        StringBuilder b = new StringBuilder();
        for (String[] f : list) { if (b.length() > 0) b.append('\n'); b.append(clean(f[0])).append('\t').append(clean(f[1])).append('\t').append(clean(f[2])); }
        return b.toString();
    }

    static String darkChoice(Context c) { String v = string(c, DARK_KEY); return "dark".equals(v) || "black".equals(v) ? v : ""; }

    static String clean(String v) { return v == null ? "" : v.replace('\t', ' ').replace('\n', ' '); }

    static List<String[]> favorites(Context c) {
        ArrayList<String[]> out = new ArrayList<String[]>();
        String raw = string(c, FAVS_KEY);
        if (raw == null || raw.isEmpty()) return out;
        for (String line : raw.split("\n")) {
            String[] f = line.split("\t", -1);
            if (f.length >= 7 && !f[0].isEmpty()) out.add(f);
        }
        return out;
    }

    static String encodeFavorites(List<String[]> list) {
        StringBuilder b = new StringBuilder();
        for (String[] f : list) {
            if (b.length() > 0) b.append('\n');
            for (int i = 0; i < 7; i++) { if (i > 0) b.append('\t'); b.append(clean(f[i])); }
        }
        return b.toString();
    }

    static String scopeGlyph(String scope) {
        if (scope == null || scope.isEmpty()) return SearchGlyph.ALL;
        switch (scope) {
            case "favorites": return SearchGlyph.STAR;
            case "apps": return SearchGlyph.APPS;
            case "shortcuts": return SearchGlyph.BOLT;
            case "contacts": return SearchGlyph.PERSON;
            case "settings": return SearchGlyph.SETTINGS;
            case "files": return SearchGlyph.FOLDER;
            case "content": return SearchGlyph.PAGE;
            case "calendar": return SearchGlyph.EVENT;
            case "tools": return SearchGlyph.CALC;
            case "web": return SearchGlyph.WEB;
            default: return SearchGlyph.SEARCH;
        }
    }
    static final String ENGINE_KEY = "pixelsuite_search_engine";
    static final String ASK_KEY = "pixelsuite_search_ask";
    static final String LAUNCHER = "com.google.android.apps.nexuslauncher";
    static final String SUITE = "io.github.roviandev.pixelsuite";
    static final String AUTHORITY = "io.github.roviandev.pixelsuite.search";
    static final String FILE_AUTHORITY = "io.github.roviandev.pixelsuite.searchfile";
    static final String DEFAULT_SLOTS = "I|action|voice|~I|action|lens|~R|action|song|";
    static final String[] SCOPES = {"favorites", "tools", "apps", "shortcuts", "contacts", "settings", "files", "content", "calendar", "web"};
    static final String[] SCOPE_NAMES = {"Favorites", "Calculator and units", "Apps", "Shortcuts", "Contacts", "Settings", "Files and media", "Inside files", "Calendar", "Web"};
    static final String[] ENGINES = {"google", "duckduckgo", "brave", "perplexity", "bing"};
    static final String[] ENGINE_NAMES = {"Google", "DuckDuckGo", "Brave", "Perplexity", "Bing"};
    static final String[] THEMES = {"auto", "light", "dark", "black"};
    static final String[] THEME_NAMES = {"Automatic", "Light", "Dark", "Super Dark"};
    static final String[] ACTIONS = {"voice", "lens", "song", "assistant", "camera", "qr", "scan", "hearts"};
    static final String[] ACTION_NAMES = {"Voice search", "Google Lens", "Song search", "Assistant", "Camera", "QR code scanner", "Scan to PDF", "Favorite contacts"};
    static final int[] SWATCHES = {0xff3f5f91, 0xff2f6b5e, 0xff006a6a, 0xff5a5f8f, 0xff7d4f8f, 0xff8f4a5c, 0xff8f5a2e, 0xff6b6f2e, 0xff4d5a63, 0xff202124};
    static final int MAX_LEFT = 2, MAX_IN = 3, MAX_RIGHT = 2;
    static final java.util.Map<String, Integer> LIVE = new java.util.concurrent.ConcurrentHashMap<String, Integer>();

    private PixelSearch() {}

    static final class Slot {
        char side; String type, value, label;
        Slot(char side, String type, String value, String label) { this.side = side; this.type = type; this.value = value == null ? "" : value; this.label = label == null ? "" : label; }
        String encode() { return side + "|" + enc(type) + "|" + enc(value) + "|" + enc(label); }
        String pkg() { int i = value.indexOf('/'); return i > 0 ? value.substring(0, i) : value; }
        String rest() { int i = value.indexOf('/'); return i > 0 ? value.substring(i + 1) : ""; }
        boolean same(Slot o) { return o != null && side == o.side && type.equals(o.type) && value.equals(o.value); }
    }

    static String enc(String s) { try { return URLEncoder.encode(s == null ? "" : s, "UTF-8"); } catch (Throwable t) { return ""; } }
    static String dec(String s) { try { return URLDecoder.decode(s == null ? "" : s, "UTF-8"); } catch (Throwable t) { return s == null ? "" : s; } }

    static List<Slot> slots(String raw) {
        ArrayList<Slot> out = new ArrayList<Slot>();
        if (raw == null) raw = DEFAULT_SLOTS;
        raw = raw.trim();
        if (raw.isEmpty() || "none".equals(raw)) return out;
        int left = 0, in = 0, right = 0;
        for (String part : raw.split("~")) {
            String[] f = part.split("\\|", -1);
            if (f.length < 3 || f[0].length() != 1) continue;
            char side = f[0].charAt(0);
            if (side != 'L' && side != 'I' && side != 'R') continue;
            String type = dec(f[1]);
            if (!"action".equals(type) && !"app".equals(type) && !"shortcut".equals(type) && !"contact".equals(type)) continue;
            if (side == 'L' && ++left > MAX_LEFT) continue;
            if (side == 'I' && ++in > MAX_IN) continue;
            if (side == 'R' && ++right > MAX_RIGHT) continue;
            out.add(new Slot(side, type, dec(f[2]), f.length > 3 ? dec(f[3]) : ""));
        }
        return out;
    }

    static String encode(List<Slot> slots) {
        if (slots.isEmpty()) return "none";
        StringBuilder b = new StringBuilder();
        for (Slot s : slots) { if (b.length() > 0) b.append('~'); b.append(s.encode()); }
        return b.toString();
    }

    static int count(List<Slot> slots, char side) { int n = 0; for (Slot s : slots) if (s.side == side) n++; return n; }
    static int max(char side) { return side == 'I' ? MAX_IN : side == 'L' ? MAX_LEFT : MAX_RIGHT; }

    static String string(Context c, String key) {
        String live = TEXT.get(key);
        if (live != null) return live;
        try { return Settings.Secure.getString(c.getContentResolver(), key); } catch (Throwable t) { return null; }
    }

    static int number(Context c, String key, int fallback) {
        Integer live = LIVE.get(key);
        if (live != null) return live;
        String text = TEXT.get(key);
        if (text != null) try { return Integer.parseInt(text); } catch (Throwable ignored) { }
        try { return Settings.Secure.getInt(c.getContentResolver(), key, fallback); } catch (Throwable t) { return fallback; }
    }

    static boolean on(Context c) { return number(c, KEY, 0) == 1; }

    static volatile String liveSlots;

    static final String SCAN_KEY = "pixelsuite_search_scan", FEATURED_KEY = "pixelsuite_search_featured", RECENTS_KEY = "pixelsuite_search_recents", ENTER_APP_KEY = "pixelsuite_search_enter_app", FEED_PULL_KEY = "pixelsuite_search_feed_pull", FEED_LOCK_KEY = "pixelsuite_search_feed_lock";
    static final String USED_KEY = "pixelsuite_search_used", PAST_KEY = "pixelsuite_search_past", ALL_AT_KEY = "pixelsuite_search_all_at";

    static List<String> recents(Context c) {
        List<String> out = new ArrayList<String>();
        String raw = string(c, PAST_KEY);
        if (raw != null) for (String s : raw.split("\n")) { String t = s.trim(); if (!t.isEmpty() && out.size() < 8) out.add(t); }
        return out;
    }

    static void recents(Context c, List<String> all) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < all.size() && i < 8; i++) { if (i > 0) b.append('\n'); b.append(all.get(i)); }
        try { Settings.Secure.putString(c.getContentResolver(), PAST_KEY, b.toString()); } catch (Throwable ignored) { }
    }

    static java.util.LinkedHashMap<String, Integer> used(Context c) {
        java.util.LinkedHashMap<String, Integer> out = new java.util.LinkedHashMap<String, Integer>();
        String raw = string(c, USED_KEY);
        if (raw != null) for (String s : raw.split("\n")) {
            int cut = s.lastIndexOf('=');
            if (cut <= 0) continue;
            try { out.put(s.substring(0, cut), Integer.parseInt(s.substring(cut + 1))); } catch (Throwable ignored) { }
        }
        return out;
    }

    static void used(Context c, String key) {
        java.util.LinkedHashMap<String, Integer> all = used(c);
        Integer n = all.get(key);
        all.put(key, n == null ? 1 : Math.min(100000, n + 1));
        List<java.util.Map.Entry<String, Integer>> rows = new ArrayList<java.util.Map.Entry<String, Integer>>(all.entrySet());
        java.util.Collections.sort(rows, new java.util.Comparator<java.util.Map.Entry<String, Integer>>() { public int compare(java.util.Map.Entry<String, Integer> x, java.util.Map.Entry<String, Integer> y) { return y.getValue() - x.getValue(); } });
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < rows.size() && i < 40; i++) { if (i > 0) b.append('\n'); b.append(rows.get(i).getKey()).append('=').append(rows.get(i).getValue()); }
        try { Settings.Secure.putString(c.getContentResolver(), USED_KEY, b.toString()); } catch (Throwable ignored) { }
    }

    static boolean scanOn(Context c) { return true; }

    static boolean feedOn(Context c) { return number(c, FEED_PULL_KEY, 0) == 1; }
    static boolean lockOn(Context c) { return feedOn(c) && number(c, FEED_LOCK_KEY, 0) == 1; }
    static final String LOCK_CLIP_KEY = "pixelsuite_search_lock_clip";
    static boolean lockClipOn(Context c) { return lockOn(c) && number(c, LOCK_CLIP_KEY, 0) == 1; }

    static boolean active(Context c) { return on(c) || feedOn(c); }

    static List<Slot> slots(Context c) {
        String live = liveSlots;
        List<Slot> all = slots(live != null ? live : string(c, SLOTS_KEY));
        if (!scanOn(c)) for (java.util.Iterator<Slot> it = all.iterator(); it.hasNext(); ) { Slot s = it.next(); if ("action".equals(s.type) && "scan".equals(s.value)) it.remove(); }
        return all;
    }

    static String engine(Context c) {
        String e = string(c, ENGINE_KEY);
        for (String x : ENGINES) if (x.equals(e)) return x;
        return "google";
    }

    static String engineName(String e) { for (int i = 0; i < ENGINES.length; i++) if (ENGINES[i].equals(e)) return ENGINE_NAMES[i]; return "Google"; }

    static String webUrl(String engine, String q) {
        String e = enc(q);
        if ("duckduckgo".equals(engine)) return "https://duckduckgo.com/?q=" + e;
        if ("brave".equals(engine)) return "https://search.brave.com/search?q=" + e;
        if ("perplexity".equals(engine)) return "https://www.perplexity.ai/search?q=" + e;
        if ("bing".equals(engine)) return "https://www.bing.com/search?q=" + e;
        return "https://www.google.com/search?q=" + e;
    }

    static String theme(Context c) {
        String t = string(c, THEME_KEY);
        for (String x : THEMES) if (x.equals(t)) return x;
        return "auto";
    }

    static String colorChoice(Context c) {
        String v = string(c, COLOR_KEY);
        return v == null || v.isEmpty() ? "auto" : v;
    }

    static boolean systemColor(Context c) { return colorChoice(c).startsWith("system") || "auto".equals(colorChoice(c)); }

    static int systemTone(Context c, String choice) {
        boolean dark = FrostedSurface.dark(c);
        if ("system2".equals(choice)) return LauncherFeatures.surface(c, dark ? "system_accent2_700" : "system_accent2_200", dark ? 0xff3f4759 : 0xffbfc6dc);
        if ("system3".equals(choice)) return LauncherFeatures.surface(c, dark ? "system_accent3_700" : "system_accent3_200", dark ? 0xff5b3d5a : 0xffdebcdf);
        return LauncherFeatures.surface(c, dark ? "system_accent1_700" : "system_accent1_200", dark ? 0xff2a4778 : 0xffaac7ff);
    }

    static int systemShade(Context c, String choice) {
        int tone = systemTone(c, choice);
        return mix(tone, lum(tone) > 0.55 ? Color.BLACK : Color.WHITE, 0.22f);
    }

    static int width(Context c) { return Math.max(70, Math.min(110, number(c, WIDTH_KEY, 100))); }

    static int height(Context c) { return Math.max(70, Math.min(110, number(c, HEIGHT_KEY, 100))); }

    static int color(Context c) {
        String v = string(c, COLOR_KEY);
        if (v == null || v.isEmpty() || "auto".equals(v)) return 0;
        if (v.startsWith("system")) return systemTone(c, v) | 0xff000000;
        try { return (int) Long.parseLong(v, 16) | 0xff000000; } catch (Throwable t) { return 0; }
    }

    static List<String> scopes(String raw) {
        ArrayList<String> out = new ArrayList<String>();
        if (raw != null) for (String s : raw.split(",")) {
            String id = s.trim(); boolean off = id.startsWith("-"); if (off) id = id.substring(1);
            if (index(id) >= 0 && !out.contains(id) && !out.contains("-" + id)) out.add(off ? "-" + id : id);
        }
        for (String id : SCOPES) if (!out.contains(id) && !out.contains("-" + id)) { if ("favorites".equals(id)) out.add(0, id); else out.add(id); }
        return out;
    }

    static List<String> scopes(Context c) { return scopes(string(c, SCOPES_KEY)); }

    static List<String> enabledScopes(Context c) {
        ArrayList<String> out = new ArrayList<String>();
        for (String s : scopes(c)) if (!s.startsWith("-")) out.add(s);
        return out;
    }

    static String encodeScopes(List<String> scopes) {
        StringBuilder b = new StringBuilder();
        for (String s : scopes) { if (b.length() > 0) b.append(','); b.append(s); }
        return b.toString();
    }

    static int index(String scope) { for (int i = 0; i < SCOPES.length; i++) if (SCOPES[i].equals(scope)) return i; return -1; }

    static String scopeName(String scope) { int i = index(scope); return i < 0 ? scope : SCOPE_NAMES[i]; }

    static String actionName(String id) { for (int i = 0; i < ACTIONS.length; i++) if (ACTIONS[i].equals(id)) return ACTION_NAMES[i]; return id; }

    static String slotName(Context c, Slot s) {
        if ("action".equals(s.type)) return I18n.t(actionName(s.value));
        if (!s.label.isEmpty()) return s.label;
        if ("app".equals(s.type)) {
            try { return String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(s.pkg(), 0))); } catch (Throwable ignored) { }
        }
        return s.value;
    }

    static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * t), Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t), Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    static double lum(int c) { return (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0; }

    static int alpha(int color, int percent) {
        percent = Math.max(0, Math.min(100, percent));
        return (color & 0xffffff) | (Math.round(Color.alpha(color) * percent / 100f) << 24);
    }

    static final class Look {
        final boolean dark, black, glass, colorful;
        final int shell, pill, ink, muted, accent, surface, card, text, subText, chip, chipOn, onChip, line, panel;
        final boolean rimmed;
        final int shellOpacity, pillOpacity;
        final float width, height;
        Look(Context c) {
            width = width(c) / 100f;
            height = height(c) / 100f;
            String theme = theme(c);
            boolean systemDark = FrostedSurface.dark(c);
            boolean superDark = SuperDark.inEffect(c);
            String night = darkChoice(c);
            black = "black".equals(theme) || (superDark && !"light".equals(theme)) || ("auto".equals(theme) && "black".equals(night) && systemDark);
            dark = black || "dark".equals(theme) || ("auto".equals(theme) && systemDark);
            glass = number(c, GLASS_KEY, 0) == 1;
            colorful = number(c, COLORFUL_KEY, 0) == 1;
            shellOpacity = Math.max(0, Math.min(100, number(c, SHELL_KEY, 100)));
            pillOpacity = Math.max(0, Math.min(100, number(c, PILL_KEY, 100)));
            int custom = color(c);
            int a1 = LauncherFeatures.surface(c, dark ? "system_accent1_200" : "system_accent1_600", dark ? 0xffaac7ff : 0xff3f5f91);
            if (black) {
                shell = Color.BLACK; pill = Color.BLACK; ink = 0xffe6e6e6; accent = 0xffe6e6e6;
            } else if (custom != 0) {
                boolean lightColor = lum(custom) > 0.55;
                pill = custom;
                shell = mix(custom, lightColor ? Color.BLACK : Color.WHITE, lightColor ? 0.12f : 0.14f);
                ink = lightColor ? 0xff1b1b1f : 0xffffffff;
                accent = ink;
            } else if (dark) {
                pill = LauncherFeatures.surface(c, "system_accent2_900", 0xff0f2b33);
                shell = mix(LauncherFeatures.surface(c, "system_accent2_800", 0xff23393f), pill, 0.35f);
                ink = LauncherFeatures.surface(c, "system_accent1_100", 0xffd6e3ff);
                accent = a1;
            } else {
                pill = LauncherFeatures.surface(c, "system_accent1_10", 0xfffdfcff);
                shell = LauncherFeatures.surface(c, "system_accent2_100", 0xffdae2f9);
                ink = LauncherFeatures.surface(c, "system_accent1_700", 0xff2a4778);
                accent = a1;
            }
            muted = mix(ink, pill, 0.35f);
            if (black) { surface = Color.BLACK; card = 0xff161616; text = 0xffededed; subText = 0xffa0a0a0; chip = 0xff1c1c1c; chipOn = 0xffe6e6e6; onChip = Color.BLACK; line = 0xff222222; }
            else if (dark) {
                surface = LauncherFeatures.surface(c, "system_surface_container_dark", 0xff1d2024);
                card = LauncherFeatures.surface(c, "system_surface_container_high_dark", 0xff272a2f);
                text = LauncherFeatures.surface(c, "system_on_surface_dark", 0xffe2e2e9);
                subText = LauncherFeatures.surface(c, "system_on_surface_variant_dark", 0xffc4c6d0);
                chip = LauncherFeatures.surface(c, "system_surface_container_highest_dark", 0xff32353a);
                chipOn = a1; onChip = LauncherFeatures.surface(c, "system_accent1_800", 0xff0a305f); line = 0x1fffffff;
            } else {
                surface = LauncherFeatures.surface(c, "system_surface_container_light", 0xffeeedf4);
                card = LauncherFeatures.surface(c, "system_surface_container_lowest_light", 0xffffffff);
                text = LauncherFeatures.surface(c, "system_on_surface_light", 0xff1a1b20);
                subText = LauncherFeatures.surface(c, "system_on_surface_variant_light", 0xff44474f);
                chip = LauncherFeatures.surface(c, "system_surface_container_high_light", 0xffe8e7ee);
                chipOn = a1; onChip = 0xffffffff; line = 0x14000000;
            }
            rimmed = black && superDark;
            panel = rimmed ? FrostedSurface.SUPER_DARK_PANEL : card;
        }
        boolean lightInk() { return lum(ink) > 0.5; }
    }
}
