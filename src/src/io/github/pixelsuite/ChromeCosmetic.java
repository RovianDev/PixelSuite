package io.github.pixelsuite;

import android.app.Application;
import android.content.Context;
import android.provider.Settings;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

final class ChromeCosmetic {
    private ChromeCosmetic() {}

    static final String PACKAGE = "com.android.chrome";
    private static boolean sInstalled;
    private static Class<?> sWebContents, sRfh, sCallback;
    private static Method sExec;
    private static final List<Method> sFrames = new ArrayList<Method>();
    private static Object sNoop;

    static void install(ClassLoader cl) {
        if (!mainProcess()) return;
        try { split(cl); } catch (Throwable ignored) { }
        try {
            Class<?> app = Xp.findClass("org.chromium.chrome.browser.base.SplitChromeApplication", cl);
            Xp.hookAllMethods(app, "onCreate", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Context c = (Context) p.thisObject;
                        split(((Context) Context.class.getMethod("createContextForSplit", String.class).invoke(c, "chrome")).getClassLoader());
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static synchronized void split(ClassLoader l) throws Throwable {
        if (sInstalled) return;
        Class<?> proxy = l.loadClass("org.chromium.content.browser.webcontents.WebContentsObserverProxy");
        sWebContents = l.loadClass("org.chromium.content_public.browser.WebContents");
        sRfh = l.loadClass("org.chromium.content_public.browser.RenderFrameHost");
        sCallback = l.loadClass("org.chromium.content_public.browser.JavaScriptCallback");
        for (Method m : sRfh.getMethods()) {
            Class<?>[] t = m.getParameterTypes();
            if (t.length == 2 && t[0] == String.class && t[1] == sCallback && m.getReturnType() == void.class) { sExec = m; break; }
        }
        for (Method m : sWebContents.getMethods()) {
            if (m.getParameterTypes().length == 0 && m.getReturnType() == sRfh) sFrames.add(m);
        }
        if (sExec == null || sFrames.isEmpty()) return;
        sNoop = Proxy.newProxyInstance(l, new Class<?>[]{sCallback}, (o, m, a) ->
                "hashCode".equals(m.getName()) ? (Object) System.identityHashCode(o) : "equals".equals(m.getName()) ? (Object) (o == a[0]) : "toString".equals(m.getName()) ? "PixelSuiteNoop" : null);
        Xp.Callback cb = new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try { inject(p.thisObject); } catch (Throwable ignored) { }
            }
        };
        Xp.hookAllMethods(proxy, "primaryMainDocumentElementAvailable", cb);
        Xp.hookAllMethods(proxy, "documentLoadedInPrimaryMainFrame", cb);
        sInstalled = true;
        try { ChromeShield.install(l); } catch (Throwable ignored) { }
        try { ChromeDarkPages.install(l); } catch (Throwable ignored) { }
    }

    private static void inject(Object proxy) throws Throwable {
        Context app = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
        if (app == null) return;
        boolean on = !"0".equals(Settings.Secure.getString(app.getContentResolver(), AdBlock.KEY)) && PatronGate.ok(app);
        Object wc = webContents(proxy);
        if (wc == null) return;
        String host = host(wc);
        int level = on && ChromeShield.filtering(app) ? ChromeShield.level(app, host) : 0;
        ChromeShield.loaded(app, wc, on);
        if (level == 0) return;
        List<Object> done = new ArrayList<Object>(2);
        for (Method m : sFrames) {
            Object rfh = m.invoke(wc);
            if (rfh == null || done.contains(rfh)) continue;
            done.add(rfh);
            if (level >= 2) sExec.invoke(rfh, SCRIPT, sNoop);
            if (level >= 3) sExec.invoke(rfh, ChromeShield.ANNOY, sNoop);
        }
        lists(app, host, done, ChromeShield.hidden(app, host), !(level == 1 && ChromeShield.googleSearch(host)));
    }

    static boolean alive(Object rfh) {
        if (rfh == null) return false;
        try {
            boolean any = false;
            for (Class<?> k = rfh.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                    if (f.getType() != long.class || java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                    f.setAccessible(true);
                    any = true;
                    if (f.getLong(rfh) != 0L) return true;
                }
                if (any) return false;
            }
            return true;
        } catch (Throwable t) { return true; }
    }

    static Object mainFrame(Object wc) {
        for (Method m : sFrames) {
            try { Object rfh = m.invoke(wc); if (rfh != null) return rfh; } catch (Throwable ignored) { }
        }
        return null;
    }

    static void run(Object wc, String js, final java.util.function.Consumer<String> done) {
        try {
            Object rfh = wc == null || sExec == null ? null : mainFrame(wc);
            if (rfh == null || !alive(rfh)) return;
            Object cb = done == null ? sNoop : Proxy.newProxyInstance(sCallback.getClassLoader(), new Class<?>[]{sCallback}, (o, m, a) -> {
                if ("hashCode".equals(m.getName())) return System.identityHashCode(o);
                if ("equals".equals(m.getName())) return o == a[0];
                if ("toString".equals(m.getName())) return "PixelSuiteResult";
                if (a != null && a.length == 1 && (a[0] == null || a[0] instanceof String)) { try { done.accept((String) a[0]); } catch (Throwable ignored) { } }
                return null;
            });
            sExec.invoke(rfh, js, cb);
        } catch (Throwable ignored) { }
    }

    static void runAll(Object wc, String js) {
        if (wc == null || sExec == null) return;
        List<Object> done = new ArrayList<Object>(2);
        for (Method m : sFrames) {
            try {
                Object rfh = m.invoke(wc);
                if (rfh == null || done.contains(rfh) || !alive(rfh)) continue;
                done.add(rfh);
                sExec.invoke(rfh, js, sNoop);
            } catch (Throwable ignored) { }
        }
    }

    static Class<?> webContentsClass() { return sWebContents; }

    static String hostOf(Object wc) { return wc == null || sWebContents == null ? null : host(wc); }

    private static final android.net.Uri LISTS = android.net.Uri.parse("content://" + Cosmetic.AUTHORITY);
    private static final ExecutorService sWorker = Executors.newSingleThreadExecutor();
    private static final android.os.Handler sMain = new android.os.Handler(android.os.Looper.getMainLooper());
    private static String[] sGeneric;
    private static String sGenericCss;
    private static long sFetchedAt = -1;
    private static List<Method> sUrls;

    private static void lists(final Context app, final String host, final List<Object> frames, final Set<String> custom, final boolean filters) {
        sWorker.execute(new Runnable() {
            @Override public void run() {
                try {
                    long now = android.os.SystemClock.uptimeMillis();
                    if (filters && (sGeneric == null || sGeneric.length == 0 || now - sFetchedAt > 30L * 60 * 1000)) {
                        List<String> all = new ArrayList<String>();
                        int parts = 1;
                        for (int i = 0; i < parts && i < 40; i++) {
                            android.os.Bundle g = app.getContentResolver().call(LISTS, "generic", String.valueOf(i), null);
                            if (g == null) break;
                            parts = g.getInt("parts", 0);
                            for (String v : split(g.getString("rules"))) if (!v.isEmpty()) all.add(v);
                        }
                        if (!all.isEmpty()) { sGeneric = all.toArray(new String[0]); sGenericCss = css(sGeneric, null, null); sFetchedAt = now; }
                    }
                    String[] generic = filters ? sGeneric : null;
                    if (generic == null && custom.isEmpty()) return;
                    String[] extra = null; Set<String> skip = null;
                    if (filters && host != null) {
                        android.os.Bundle h = app.getContentResolver().call(LISTS, "host", host, null);
                        if (h != null) {
                            extra = split(h.getString("rules"));
                            String[] x = split(h.getString("except"));
                            if (x.length > 0) { skip = new HashSet<String>(); for (String v : x) skip.add(v); }
                        }
                    }
                    StringBuilder sheet = new StringBuilder(generic == null ? "" : skip == null ? sGenericCss : css(generic, null, skip));
                    if (extra != null) sheet.append(css(extra, null, skip));
                    for (String c : custom) if (!c.isEmpty()) sheet.append(c).append('\n');
                    if (sheet.length() == 0) return;
                    final String js = "(function () { \n"
                            + "if (window.__pixelSuiteLists) return; \n"
                            + "window.__pixelSuiteLists = 1; \n"
                            + "var sels = " + quote(sheet.toString()) + ".split('\\n'); window.__psSels = sels; \n"
                            + "var css = sels.map(function (s) { return s ? s + '{display:none!important}' : ''; }).join('\\n'); \n"
                            + "try { var s = new CSSStyleSheet(); s.replaceSync(css); \n"
                            + "var add = function () { try { if (document.adoptedStyleSheets.indexOf(s) < 0) document.adoptedStyleSheets = document.adoptedStyleSheets.concat([s]); } catch (e) { } }; \n"
                            + "add(); document.addEventListener('DOMContentLoaded', add, true); return; } catch (e) { } \n"
                            + "try { var st = document.createElement('style'); st.textContent = css; (document.head || document.documentElement).appendChild(st); } catch (e) { } \n"
                            + "})();\n";
                    sMain.post(new Runnable() {
                        @Override public void run() {
                            for (Object rfh : frames) { try { if (alive(rfh)) sExec.invoke(rfh, js, sNoop); } catch (Throwable ignored) { } }
                        }
                    });
                } catch (Throwable ignored) { }
            }
        });
    }

    private static String[] split(String rules) {
        if (rules == null || rules.isEmpty()) return new String[0];
        return rules.split("\n");
    }

    private static String css(String[] selectors, String unused, Set<String> skip) {
        StringBuilder b = new StringBuilder(selectors.length * 40);
        for (String s : selectors) {
            if (s.isEmpty() || (skip != null && skip.contains(s))) continue;
            b.append(s).append('\n');
        }
        return b.toString();
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder(s.length() + 64);
        b.append('"');
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\u2028': b.append("\\u2028"); break;
                case '\u2029': b.append("\\u2029"); break;
                default:
                    if (ch < 0x20) b.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) ch)); else b.append(ch);
            }
        }
        return b.append('"').toString();
    }

    private static String host(Object wc) {
        try {
            if (sUrls == null) {
                List<Method> found = new ArrayList<Method>();
                for (Method m : sWebContents.getMethods())
                    if (m.getParameterTypes().length == 0 && "org.chromium.url.GURL".equals(m.getReturnType().getName())) found.add(m);
                sUrls = found;
            }
            for (Method m : sUrls) {
                Object gurl = m.invoke(wc);
                if (gurl == null) continue;
                for (Class<?> k = gurl.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
                    for (Field f : k.getDeclaredFields()) {
                        if (f.getType() != String.class || Modifier.isStatic(f.getModifiers())) continue;
                        f.setAccessible(true);
                        Object v = f.get(gurl);
                        if (!(v instanceof String) || !((String) v).startsWith("http")) continue;
                        String h = android.net.Uri.parse((String) v).getHost();
                        if (h != null && !h.isEmpty()) return h.toLowerCase(java.util.Locale.ROOT);
                    }
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    static Object webContentsOf(Object proxy) {
        try { return webContents(proxy); } catch (Throwable t) { return null; }
    }

    private static Object webContents(Object proxy) throws Throwable {
        for (Field f : proxy.getClass().getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
            f.setAccessible(true);
            Object v = f.get(proxy);
            if (!(v instanceof Iterable)) continue;
            for (Object observer : (Iterable<?>) v) {
                for (Class<?> c = observer.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                    for (Field g : c.getDeclaredFields()) {
                        if (Modifier.isStatic(g.getModifiers()) || !sWebContents.isAssignableFrom(g.getType())) continue;
                        g.setAccessible(true);
                        Object wc = g.get(observer);
                        if (wc != null) return wc;
                    }
                }
            }
        }
        return null;
    }

    private static boolean mainProcess() {
        try { String n = (String) Application.class.getMethod("getProcessName").invoke(null); return n == null || !n.contains(":"); } catch (Throwable t) { return true; }
    }

    static final String SCRIPT = ""
            + "(function () { \n"
            + "var W = window, D = document; \n"
            + "if (W.__pixelSuiteCosmetic) return; \n"
            + "W.__pixelSuiteCosmetic = 1; \n"
            + "var ROOTS = ':not(html):not(body):not(main):not(article)'; \n"
            + "var STRONG = [ \n"
            + "'[id^=\"google_ads_iframe\"]', '[id^=\"google_ads_frame\"]', '[id^=\"div-gpt-ad\"]', '[id^=\"gpt-ad\"]', '[id^=\"dfp-ad\"]', \n"
            + "'ins.adsbygoogle', '[data-ad-slot]', '[data-ad-client]', '[data-ad-unit]', '[data-adunit]', '[data-ad-unit-path]', \n"
            + "'[data-google-query-id]', '[data-dfp-id]', '[data-ad-region]', '[data-ad-placement]', \n"
            + "'iframe[src*=\"doubleclick.net\"]', 'iframe[src*=\"googlesyndication.com\"]', 'iframe[src*=\"amazon-adsystem.com\"]', \n"
            + "'iframe[src*=\"adnxs.com\"]', 'iframe[src*=\"criteo\"]', 'iframe[id^=\"google_ads\"]', 'iframe[name^=\"google_ads\"]', \n"
            + "'amp-ad', 'amp-embed', 'amp-sticky-ad', 'amp-auto-ads', \n"
            + "'[data-testid*=\"adslot\" i]', '[data-testid*=\"ad-slot\" i]', '[data-testid*=\"ad_slot\" i]', '[data-testid*=\"advert\" i]', \n"
            + "'[data-testid*=\"adunit\" i]', '[data-testid*=\"ad-unit\" i]', '[data-test-id*=\"adslot\" i]', '[data-qa*=\"ad-slot\" i]', \n"
            + "'[class*=\"adslot\" i]', '[class*=\"adunit\" i]', '[class*=\"adsbygoogle\" i]', '[class*=\"adbanner\" i]', \n"
            + "'[class*=\"dfp-ad\" i]', '[class*=\"gpt-ad\" i]', \n"
            + "'[id*=\"taboola\" i]', '[class*=\"taboola\" i]', '[id*=\"outbrain\" i]', '[class*=\"outbrain\" i]', '[class*=\"OUTBRAIN\"]', \n"
            + "'[class*=\"trc_related_container\"]', '[class*=\"mgid\" i][class*=\"widget\" i]', '[id^=\"mgid\" i]', \n"
            + "'[aria-label=\"advertisement\" i]', '[aria-label=\"advertising\" i]', '[aria-label=\"ad\" i]', '[aria-label=\"sponsored\" i]', \n"
            + "'[class~=\"adsbox\" i]', '[class~=\"ad-box\" i]', '[class~=\"adbox\" i]', '[class~=\"sticky-ad\" i]', '[class~=\"top-ad\" i]', \n"
            + "'[class~=\"bottom-ad\" i]', '[class~=\"banner-ad\" i]', '[class~=\"native-ad\" i]', '[class~=\"inline-ad\" i]', \n"
            + "'[class~=\"in-article-ad\" i]', '[class~=\"google-ad\" i]', '[class~=\"googlead\" i]', '[id=\"ad\" i]', '[id=\"ads\" i]', \n"
            + "'*:has(> [id^=\"google_ads_iframe\"])', '*:has(> ins.adsbygoogle)', '*:has(> [id^=\"div-gpt-ad\"])' \n"
            + "]; \n"
            + "var WEAK = [ \n"
            + "'[class~=\"ad\" i]', '[class~=\"ads\" i]', '[class~=\"advert\" i]', '[class~=\"adverts\" i]', '[class~=\"advertisement\" i]', \n"
            + "'[class~=\"advertisements\" i]', '[class~=\"advertising\" i]', '[class*=\"advert\" i]', '[class~=\"sponsored\" i]', \n"
            + "'[class~=\"sponsored-content\" i]', '[class~=\"sponsor\" i]', '[class~=\"promoted\" i]', '[id*=\"advert\" i]', \n"
            + "'[id^=\"ad-\" i]', '[id^=\"ad_\" i]', '[id^=\"ads-\" i]', '[id^=\"ads_\" i]', '[id$=\"-ad\" i]', '[id$=\"_ad\" i]' \n"
            + "]; \n"
            + "var LABEL = /^(advertisement|advertisements|advertising|advert|ad|ads|sponsored|sponsored content|sponsored links|promoted|paid content|paid post|partner content|anzeige|werbung|publicit[\u00e9e]|publicidad|pubblicit[\u00e0a]|reclame|\u0440\u0435\u043a\u043b\u0430\u043c\u0430|\u0625\u0639\u0644\u0627\u0646|\u0627\u0639\u0644\u0627\u0646)(\\s*[\u00b7\u2022|:\\-\u2013\u2014]\\s*(scroll to continue( with content)?|continue reading|story continues below|article continues below|content continues below))?$/i; \n"
            + "var LABEL2 = /^(scroll to continue( with content)?|story continues below( advertisement)?|article continues below( advertisement)?|continue reading below)$/i; \n"
            + "var ADISH = /(^|[\\s_\\-])(ad|ads|advert\\w*|sponsor\\w*|promo\\w*|dfp|gpt|banner|teads)([\\s_\\-]|$)|adslot|ad-?slot|adunit|ad-?unit|ad-?container|ad-?wrap|advert/i; \n"
            + "var KEEP = 'data-ps-keep'; \n"
            + "var PARTS = '(slot|unit|container|wrapper|wrap|placeholder|banner|box|holder|space|zone|area|block|frame|leaderboard|mpu|rail|sticky|inline|label|position|spot)'; \n"
            + "var TOKEN = new RegExp('(^|[-_])ads?[-_]' + PARTS + '([-_]|$)|(^|[-_])(top|bottom|side|inline|sticky|native|banner|header|footer|sidebar|rail|mid|article|in-?article)[-_]ads?([-_]|$)', 'i'); \n"
            + "var CAMEL = new RegExp('Ads?' + PARTS.replace(/\\|(\\w)/g, function (m, c) { return '|' + c.toUpperCase(); }).replace('(s', '(S')); \n"
            + "function valid(list) { \n"
            + "var out = [], probe = D.createElement('div'); \n"
            + "for (var i = 0; i < list.length; i++) { \n"
            + "try { probe.matches(list[i]); out.push(list[i]); } catch (e) { } \n"
            + "} \n"
            + "return out; \n"
            + "} \n"
            + "var strong = valid(STRONG), weak = valid(WEAK); \n"
            + "var all = strong.concat(weak); \n"
            + "var css = all.map(function (s) { return s + ROOTS + ':not([' + KEEP + '])'; }).join(',\\n') + \n"
            + "'{display:none!important;visibility:hidden!important;height:0!important;min-height:0!important;margin:0!important;padding:0!important}' + \n"
            + "'\\n[data-ps-wall]{display:none!important;visibility:hidden!important;opacity:0!important;pointer-events:none!important}'; \n"
            + "try { \n"
            + "var sheet = new CSSStyleSheet(); \n"
            + "sheet.replaceSync(css); \n"
            + "var add = function () { \n"
            + "try { if (D.adoptedStyleSheets.indexOf(sheet) < 0) D.adoptedStyleSheets = D.adoptedStyleSheets.concat([sheet]); } catch (e) { } \n"
            + "}; \n"
            + "add(); \n"
            + "D.addEventListener('DOMContentLoaded', add, true); \n"
            + "} catch (e) { } \n"
            + "try { \n"
            + "var st = D.createElement('style'); \n"
            + "st.textContent = css; \n"
            + "st.setAttribute('data-ps', '1'); \n"
            + "(D.head || D.documentElement).appendChild(st); \n"
            + "} catch (e) { } \n"
            + "var strongSel = strong.join(','), weakSel = weak.join(','); \n"
            + "function hide(el) { \n"
            + "if (!el || el === D.body || el === D.documentElement || el.hasAttribute(KEEP)) return; \n"
            + "var t = el.tagName; \n"
            + "if (t === 'MAIN' || t === 'ARTICLE' || t === 'BODY' || t === 'HTML') return; \n"
            + "el.style.setProperty('display', 'none', 'important'); \n"
            + "el.setAttribute('data-ps-hidden', '1'); \n"
            + "} \n"
            + "function words(el) { \n"
            + "return (el.textContent || '').replace(/\\s+/g, ' ').trim(); \n"
            + "} \n"
            + "function real(el) { \n"
            + "if (strongSel && el.matches(strongSel)) return false; \n"
            + "var text = words(el); \n"
            + "if (el.querySelectorAll('p').length >= 2 || el.querySelector('h1,h2,[itemprop=\"articleBody\"]')) return true; \n"
            + "return text.replace(LABEL, '').length > 220; \n"
            + "} \n"
            + "function label(el) { \n"
            + "if (!el || el.nodeType !== 1 || el.childElementCount > 3) return; \n"
            + "var text = words(el); \n"
            + "if (text.length === 0 || text.length > 70) return; \n"
            + "if (!LABEL.test(text) && !LABEL2.test(text)) return; \n"
            + "var box = el; \n"
            + "for (var i = 0; i < 6; i++) { \n"
            + "var p = box.parentElement; \n"
            + "if (!p || p === D.body || p === D.documentElement) break; \n"
            + "var tag = p.tagName; \n"
            + "if (tag === 'MAIN' || tag === 'ARTICLE' || tag === 'SECTION' && !ADISH.test(attrs(p))) break; \n"
            + "var rest = words(p).replace(text, '').replace(LABEL2, '').trim(); \n"
            + "if (rest.length > 40 || p.querySelector('p,h1,h2,h3,video,picture,figure')) break; \n"
            + "if (!ADISH.test(attrs(p)) && i > 1) break; \n"
            + "box = p; \n"
            + "} \n"
            + "hide(box); \n"
            + "} \n"
            + "function attrs(el) { \n"
            + "return (el.id || '') + ' ' + (typeof el.className === 'string' ? el.className : '') + ' ' + \n"
            + "(el.getAttribute('data-testid') || '') + ' ' + (el.getAttribute('aria-label') || ''); \n"
            + "} \n"
            + "function marked(el) { \n"
            + "var c = el.className; \n"
            + "if (typeof c !== 'string' || !c) return false; \n"
            + "var tokens = c.split(/\\s+/); \n"
            + "for (var i = 0; i < tokens.length; i++) { \n"
            + "var k = tokens[i]; \n"
            + "if (k.length > 2 && k.length < 60 && (TOKEN.test(k) || CAMEL.test(k))) return true; \n"
            + "} \n"
            + "return false; \n"
            + "} \n"
            + "function check(el) { \n"
            + "if (el.hasAttribute(KEEP) || el.hasAttribute('data-ps-hidden')) return; \n"
            + "if (marked(el)) hide(el); \n"
            + "} \n"
            + "function scan(root) { \n"
            + "if (!root || root.nodeType !== 1) return; \n"
            + "check(root); \n"
            + "label(root); \n"
            + "var list = root.querySelectorAll('*'), n = Math.min(list.length, 600); \n"
            + "for (var i = 0; i < n; i++) { \n"
            + "var e = list[i]; \n"
            + "check(e); \n"
            + "if (e.childElementCount <= 3) label(e); \n"
            + "if (e.childElementCount <= 8) wall(e); \n"
            + "} \n"
            + "} \n"
            + "var WALL = /(please (consider )?(allow(ing)?|disabl(e|ing)|turn(ing)? off|whitelist(ing)?) [^.]{0,40}ad|allow(ing)? ads|ad ?-?block(er|ing)? (is |was )?(detected|enabled|active|installed|on)|(disable|pause|turn off) (your |the )?ad ?-?block|whitelist (us|this site|our site)|continue without supporting|(you('| a)re|you are) using an? ad ?-?block)/i; \n"
            + "var ADHREF = /doubleclick\\.net|googleadservices|googlesyndication|adclick|aclk\\?|adurl=|clickserve|adsrvr|adnxs|criteo|taboola|outbrain|\\/ads?\\//i; \n"
            + "var PLAYER = /(primis|connatix|aniview|vidazoo|teads|outstream|ex-?co\\b|minutemedia|jwplayer|jw-?float|float(ing)?[-_ ]?(player|video)|sticky[-_ ]?(player|video)|(player|video)[-_ ]?(float|sticky))/i; \n"
            + "function pinned(el) { \n"
            + "var top = null; \n"
            + "for (var n = el; n && n !== D.body && n !== D.documentElement; n = n.parentElement || (n.parentNode && n.parentNode.host)) { \n"
            + "var cs = getComputedStyle(n); \n"
            + "if (cs.position === 'fixed' || cs.position === 'sticky') top = n; \n"
            + "} \n"
            + "return top; \n"
            + "} \n"
            + "function unlock() { \n"
            + "[D.documentElement, D.body].forEach(function (n) { \n"
            + "if (!n) return; \n"
            + "var cs = getComputedStyle(n); \n"
            + "if (cs.overflow === 'hidden' || cs.overflowY === 'hidden') n.style.setProperty('overflow', 'auto', 'important'); \n"
            + "if (n === D.body && cs.position === 'fixed') n.style.setProperty('position', 'static', 'important'); \n"
            + "}); \n"
            + "} \n"
            + "function hideWall(el) { \n"
            + "if (!el || el === D.body || el === D.documentElement) return; \n"
            + "el.removeAttribute(KEEP); \n"
            + "el.setAttribute('data-ps-wall', '1'); \n"
            + "['display', 'visibility', 'opacity', 'pointer-events'].forEach(function (k, i) { el.style.setProperty(k, ['none', 'hidden', '0', 'none'][i], 'important'); }); \n"
            + "try { el.remove(); } catch (e) { } \n"
            + "} \n"
            + "function walls() { \n"
            + "var left = D.querySelectorAll('[data-ps-wall]'); \n"
            + "for (var i = 0; i < left.length; i++) { try { left[i].remove(); } catch (e) { } } \n"
            + "if (left.length) unlock(); \n"
            + "} \n"
            + "function backdrops() { \n"
            + "var vw = W.innerWidth, vh = W.innerHeight, list = D.querySelectorAll('body > *, body > * > *'); \n"
            + "for (var i = 0; i < list.length && i < 400; i++) { \n"
            + "var e = list[i]; \n"
            + "if (e.hasAttribute('data-ps-wall') || words(e).length > 0) continue; \n"
            + "var cs = getComputedStyle(e); \n"
            + "if (cs.position !== 'fixed') continue; \n"
            + "var r = e.getBoundingClientRect(); \n"
            + "if (r.width >= vw * 0.9 && r.height >= vh * 0.9) hideWall(e); \n"
            + "} \n"
            + "} \n"
            + "function wall(el, text) { \n"
            + "if (!el || el.nodeType !== 1) return; \n"
            + "var t = text || words(el); \n"
            + "if (t.length < 12 || t.length > 600 || !WALL.test(t)) return; \n"
            + "var box = pinned(el); \n"
            + "if (!box || box.querySelector('article, main') || words(box).length > 1500) return; \n"
            + "hideWall(box); \n"
            + "backdrops(); unlock(); \n"
            + "setTimeout(function () { backdrops(); unlock(); }, 600); \n"
            + "setTimeout(unlock, 2000); \n"
            + "} \n"
            + "function shadows(root) { \n"
            + "var list = root.querySelectorAll('*'); \n"
            + "for (var i = 0; i < list.length && i < 800; i++) { \n"
            + "var e = list[i]; \n"
            + "if (!e.shadowRoot || e.hasAttribute('data-ps-wall')) continue; \n"
            + "var t = (e.shadowRoot.textContent || '').replace(/\\s+/g, ' ').trim(); \n"
            + "if (t.length >= 12 && t.length <= 600 && WALL.test(t)) { hideWall(e); backdrops(); unlock(); setTimeout(unlock, 2000); } \n"
            + "} \n"
            + "} \n"
            + "function adish(el) { \n"
            + "var frames = el.querySelectorAll('iframe'); \n"
            + "for (var i = 0; i < frames.length; i++) { var f = frames[i]; try { if (!f.contentDocument) return true; } catch (e) { return true; } } \n"
            + "var links = el.querySelectorAll('a[href]'); \n"
            + "for (var j = 0; j < links.length; j++) { var a = links[j]; if (ADHREF.test(a.href) || (a.host && a.host !== location.host && a.querySelector('img'))) return true; } \n"
            + "return ADISH.test(attrs(el)) || (strongSel && !!el.querySelector(strongSel)); \n"
            + "} \n"
            + "function sticky() { \n"
            + "if (!D.body) return; \n"
            + "var vw = W.innerWidth, vh = W.innerHeight, list = D.querySelectorAll('body > *, body > * > *, body > * > * > *'); \n"
            + "for (var i = 0; i < list.length && i < 500; i++) { \n"
            + "var e = list[i]; \n"
            + "if (e.hasAttribute('data-ps-hidden') || e.hasAttribute('data-ps-wall')) continue; \n"
            + "var cs = getComputedStyle(e); \n"
            + "if (cs.position !== 'fixed' && cs.position !== 'sticky') continue; \n"
            + "var r = e.getBoundingClientRect(); \n"
            + "if (r.width < 40 || r.height < 30) continue; \n"
            + "var t = words(e); \n"
            + "if (e.shadowRoot) { var st = (e.shadowRoot.textContent || '').replace(/\\s+/g, ' ').trim(); if (st.length >= 12 && st.length <= 600 && WALL.test(st)) { hideWall(e); unlock(); continue; } } \n"
            + "if (t.length >= 12 && t.length <= 1500 && WALL.test(t) && !e.querySelector('article, main')) { hideWall(e); backdrops(); unlock(); continue; } \n"
            + "if (e.hasAttribute(KEEP)) continue; \n"
            + "if (e.querySelector('nav, input, form, [role=\"navigation\"], [role=\"search\"]') || t.length > 140) continue; \n"
            + "var edge = r.bottom >= vh - 6 || r.top <= 6; \n"
            + "if (edge && r.width >= vw * 0.6 && r.height <= vh * 0.4 && adish(e)) { hide(e); continue; } \n"
            + "if (r.width < vw * 0.85 && (e.querySelector('video, iframe') || PLAYER.test(attrs(e))) && (PLAYER.test(attrs(e) + ' ' + (e.firstElementChild ? attrs(e.firstElementChild) : '')) || adish(e))) hide(e); \n"
            + "} \n"
            + "} \n"
            + "var mo = new MutationObserver(function (records) { \n"
            + "for (var r = 0; r < records.length; r++) { \n"
            + "var added = records[r].addedNodes; \n"
            + "for (var a = 0; a < added.length; a++) { \n"
            + "var node = added[a]; \n"
            + "if (node.nodeType === 3) { label(node.parentElement); wall(node.parentElement); } \n"
            + "else if (node.nodeType === 1) { if (node.hasAttribute('data-ps-wall')) { try { node.remove(); } catch (e) { } unlock(); continue; } scan(node); } \n"
            + "} \n"
            + "} \n"
            + "}); \n"
            + "mo.observe(D.documentElement || D, { childList: true, subtree: true }); \n"
            + "scan(D.documentElement); \n"
            + "var ticks = 0; \n"
            + "var sweep = function () { try { walls(); sticky(); if (D.body) shadows(D.body); } catch (e) { } }; \n"
            + "var timer = setInterval(function () { sweep(); if (++ticks >= 30) { clearInterval(timer); var slow = 0, later = setInterval(function () { sweep(); if (++slow >= 150) clearInterval(later); }, 4000); } }, 1000); \n"
            + "W.addEventListener('load', function () { try { sticky(); } catch (e) { } }, true); \n"
            + "function verify() { \n"
            + "if (!weakSel) return; \n"
            + "var list = D.querySelectorAll(weakSel); \n"
            + "for (var i = 0; i < list.length; i++) if (real(list[i])) list[i].setAttribute(KEEP, '1'); \n"
            + "var hidden = D.querySelectorAll('[data-ps-hidden]'); \n"
            + "for (var j = 0; j < hidden.length; j++) { \n"
            + "var h = hidden[j]; \n"
            + "if (real(h)) { h.style.removeProperty('display'); h.removeAttribute('data-ps-hidden'); h.setAttribute(KEEP, '1'); } \n"
            + "} \n"
            + "} \n"
            + "D.addEventListener('DOMContentLoaded', verify, true); \n"
            + "W.addEventListener('load', verify, true); \n"
            + "})();\n";
}
