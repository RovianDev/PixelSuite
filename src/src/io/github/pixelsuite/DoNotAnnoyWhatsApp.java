package io.github.pixelsuite;

import android.app.Notification;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

final class DoNotAnnoyWhatsApp {
    private DoNotAnnoyWhatsApp() { }

    static final String KEY = "pixelsuite_dna_wa";
    static final String[] APPS = {"com.whatsapp", "com.whatsapp.w4b"};
    static final String ACTION = "io.github.pixelsuite.action.DNA_WA";
    private static final String TAG = "PixelSuite";

    private static final String REASON = "uncallable";

    static boolean owns(String pkg) { for (String a : APPS) if (a.equals(pkg)) return true; return false; }

    private static volatile ClassLoader sCl;
    private static volatile long sReadAt; private static volatile boolean sActive;
    private static String sLastId; private static long sLastAt;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static Context app() {
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); } catch (Throwable t) { return null; }
    }

    static boolean active() {
        long now = android.os.SystemClock.uptimeMillis();
        if (now - sReadAt < 800) return sActive;
        sReadAt = now;
        Context c = app();
        try { sActive = c != null && "1".equals(Settings.Secure.getString(c.getContentResolver(), DoNotAnnoy.KEY)) && Settings.Secure.getInt(c.getContentResolver(), KEY, 0) == 1; }
        catch (Throwable t) { sActive = false; }
        return sActive;
    }

    private static volatile Class<?> sVoip, sCallInfo;
    private static volatile Object sManager, sLatest;

    private static final String[] VOIP = {"com.whatsapp.calling.voipcalling.Voip", "com.whatsapp.voipcalling.Voip"};
    private static final String[] CALL_INFO = {"com.whatsapp.calling.infra.voipcalling.CallInfo", "com.whatsapp.calling.voipcalling.CallInfo", "com.whatsapp.voipcalling.CallInfo"};
    private static final java.util.List<Object> sCatch = new java.util.ArrayList<>();
    private static volatile Object sStateOwner;

    private static Class<?> first(String[] names) { for (String n : names) { Class<?> k = load(n); if (k != null) return k; } return null; }

    static void install(ClassLoader cl) {
        sCl = cl;
        sVoip = first(VOIP);
        sCallInfo = first(CALL_INFO);
        if (sVoip != null) { hookManager(); hookOffer(); }
        if (sCallInfo != null) hookCallInfo();

        hookStanzas();
        Xp.Callback posted = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Notification n = null; for (Object a : p.args) if (a instanceof Notification) { n = (Notification) a; break; }
                    if (n == null || !incoming(n) || !active()) return;
                    if (turnAway(sLatest, n, "notification")) p.setResult(null);
                } catch (Throwable t) { Log.w(TAG, "dna whatsapp: notification", t); }
            }
        };
        for (String m : new String[]{"notify", "notifyAsUser", "notifyAsPackage"}) Xp.hookAllMethods(android.app.NotificationManager.class, m, posted);
        Xp.hookAllMethods(android.app.Service.class, "startForeground", new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) {
                try {
                    Notification n = null; for (Object a : p.args) if (a instanceof Notification) { n = (Notification) a; break; }
                    if (n == null || !incoming(n) || !active()) return;
                    turnAway(sLatest, n, "service");
                } catch (Throwable t) { Log.w(TAG, "dna whatsapp: service", t); }
            }
        }).size();
        Xp.hookAllMethods(android.app.Activity.class, "onResume", new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                try {
                    final android.app.Activity a = (android.app.Activity) p.thisObject;
                    String an = a.getClass().getName();
                    if (!(an.contains("voipcalling") || (an.contains(".calling.") && an.endsWith("Activity"))) || !active()) return;
                    if (turnAway(sLatest, null, "call screen")) a.finish();
                } catch (Throwable t) { Log.w(TAG, "dna whatsapp: screen", t); }
            }
        }).size();

        new Thread(() -> {
            try {
                Context c = waitForApp();
                if (c == null) return;
                if (sVoip == null) { String n = DexPeek.classEndingWith(c, ".voipcalling.Voip"); if (n == null) n = DexPeek.classEndingWith(c, ".Voip"); sVoip = load(n); if (sVoip != null) { hookManager(); hookOffer(); } }
                if (sCallInfo == null) { String n = DexPeek.classEndingWith(c, ".voipcalling.CallInfo"); if (n == null) n = DexPeek.classEndingWith(c, ".CallInfo"); sCallInfo = load(n); if (sCallInfo != null) hookCallInfo(); }
                String[] m = DexPeek.cachedMethodWithStringContaining(c, "wa_call_state", "voip/callStateChangedOnUIThread");
                if (m != null) hookStateChanged(m);
                if (sVoip != null && sManager == null) catchManager();
                if (sStanzas == null) { String n = DexPeek.classEndingWith(c, ".OutgoingSignalingHandler"); if (n != null) hookStanzasOn(load(n), "by its name in WhatsApp's code"); }
                if (sStanzas == null && sManager != null) stanzasFrom(sManager);
                rejectMethod();
            } catch (Throwable t) { Log.w(TAG, "dna whatsapp: looking up WhatsApp's call engine", t); }
        }, "PixelSuiteDNAwaScan").start();
    }

    private static Class<?> load(String name) {
        if (name == null) return null;
        try { return Class.forName(name, false, sCl); } catch (Throwable t) { return null; }
    }

    private static Context waitForApp() {
        for (int i = 0; i < 100; i++) { Context c = app(); if (c != null) return c; try { Thread.sleep(100); } catch (InterruptedException e) { return null; } }
        return app();
    }

    private static int hookManager() {
        return Xp.hookAllConstructors(sVoip, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) { if (p.thisObject != null) found(p.thisObject, "made"); }
        }).size();
    }

    private static void found(Object manager, String how) {
        if (sManager != null || manager == null || (sVoip != null && !sVoip.isInstance(manager))) return;
        sManager = manager;
        MAIN.post(DoNotAnnoyWhatsApp::release);
        if (sStanzas == null) new Thread(() -> stanzasFrom(manager), "PixelSuiteDNAwaStanzas").start();
    }

    private static int catchManager() {
        Xp.Callback cb = new Xp.Callback() {
            @Override protected void beforeHookedMethod(Xp.Param p) { if (sManager == null && p.thisObject != null) found(p.thisObject, p.method.getName()); }
        };
        int n = 0;
        java.util.List<Method> natives = new java.util.ArrayList<>(), others = new java.util.ArrayList<>();
        for (Method m : sVoip.getDeclaredMethods()) {
            int mod = m.getModifiers();
            if (Modifier.isStatic(mod) || Modifier.isAbstract(mod) || m.isSynthetic()) continue;
            (Modifier.isNative(mod) ? natives : others).add(m);
        }
        java.util.List<Method> watch = natives.isEmpty() ? others : natives;
        synchronized (sCatch) {
            for (Method m : watch) {
                if (n >= 400 || sManager != null) break;
                Object h = Xp.hookMethod(m, cb);
                if (h != null) { sCatch.add(h); n++; }
            }
        }
        if (sManager != null) MAIN.post(DoNotAnnoyWhatsApp::release);
        return n;
    }

    private static void release() {
        synchronized (sCatch) {
            for (Object h : sCatch) try { if (h instanceof io.github.libxposed.api.XposedInterface.HookHandle) ((io.github.libxposed.api.XposedInterface.HookHandle) h).unhook(); } catch (Throwable ignored) { }
            sCatch.clear();
        }
    }

    private static volatile boolean sOfferLogged;
    private static final java.util.regex.Pattern CALL_ID = java.util.regex.Pattern.compile("[0-9A-Fa-f]{16,64}");

    private static int hookOffer() {
        int n = 0;
        for (Class<?> k = sVoip; k != null && k != Object.class; k = k.getSuperclass())
            for (Method m : k.getDeclaredMethods()) {
                if (!"nativeHandleIncomingXmppOffer".equals(m.getName()) || Modifier.isAbstract(m.getModifiers())) continue;
                Object h = Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param p) { if (p.thisObject != null) found(p.thisObject, "offer"); }
                    @Override protected void afterHookedMethod(Xp.Param p) { try { offer(p); } catch (Throwable t) { Log.w(TAG, "dna whatsapp: offer", t); } }
                });
                if (h != null) n++;
            }
        return n;
    }

    private static void offer(Xp.Param p) {
        if (!active()) return;
        String id = null; Object jid = null;
        String nodeId = null;
        for (Object a : p.args) {
            if (a instanceof String) { if (id == null && CALL_ID.matcher((String) a).matches()) id = (String) a; }
            else if (a != null && nodeId == null && a.getClass().getName().endsWith("VoipStanzaChildNode")) nodeId = attr(a, "call-id");
            else if (a != null && jid == null) { Object r = get(a, "getRawString"); if (r instanceof String && ((String) r).indexOf('@') > 0) jid = a; }
        }
        if (id == null) id = nodeId;
        Object info = callInfo();
        Object infoId = info == null ? null : callIdOf(info);
        boolean infoIncoming = info != null && (ringing(info) || Boolean.FALSE.equals(get(info, "isCaller")));
        if (!sOfferLogged) {
            sOfferLogged = true;
            StringBuilder b = new StringBuilder();
            for (Object a : p.args) b.append(a == null ? "null" : a.getClass().getSimpleName()).append(' ');
        }
        if (id == null && infoIncoming && infoId != null) id = infoId.toString();
        if (id == null) return;
        if (jid == null && info != null && id.equals(String.valueOf(infoId))) jid = peerOf(info);
        synchronized (DoNotAnnoyWhatsApp.class) {
            long now = android.os.SystemClock.uptimeMillis();
            if (id.equals(sLastId) && now - sLastAt < 60000) return;
            sTurned.put(id, now);
            if (!reject(id)) { sTurned.remove(id); return; }
        }
        Object raw = jid == null ? null : get(jid, "getRawString");
        String r = raw == null ? "" : raw.toString();
        tellOnce(id, null, r);
    }

    private static final java.util.concurrent.ConcurrentHashMap<String, Long> sTurned = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> sRejectOut = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());
    private static volatile Class<?> sStanzas;
    private static final String[] STANZA_CLASSES = {"com.whatsapp.calling.service.OutgoingSignalingHandler"};

    private static final Xp.Callback STANZA = new Xp.Callback() {
        @Override protected void beforeHookedMethod(Xp.Param p) {
            try {
                Object node = null; String id = null;
                for (Object a : p.args) {
                    if (a instanceof String) { if (id == null) id = (String) a; }
                    else if (a != null && node == null && a.getClass().getName().contains("StanzaChildNode")) node = a;
                }
                if (node == null && p.args.length > 0) node = p.args[p.args.length - 1];
                if (node == null) return;
                String nodeId = attr(node, "call-id");
                if (id == null || (!sTurned.containsKey(id) && nodeId != null && sTurned.containsKey(nodeId))) id = nodeId;
                String tag = tag(node);
                if ("preaccept".equals(tag) && active()) {
                    p.setResult(null);
                    return;
                }
                if (id == null) return;
                Long turned = sTurned.get(id);
                if (turned == null || android.os.SystemClock.uptimeMillis() - turned >= 20000) return;
                if ("reject".equals(tag) && sRejectOut.add(id)) { return; }
                p.setResult(null);
            } catch (Throwable t) { Log.w(TAG, "dna whatsapp: stanza", t); }
        }
    };

    private static int hookStanzas() {
        for (String n : STANZA_CLASSES) { int k = hookStanzasOn(load(n), "by name"); if (k > 0) return k; }
        return 0;
    }

    private static synchronized int hookStanzasOn(Class<?> k, String how) {
        if (k == null || sStanzas != null) return 0;
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
            boolean has = false;
            for (Method m : c.getDeclaredMethods()) if ("sendCallStanza".equals(m.getName()) && !Modifier.isAbstract(m.getModifiers())) { has = true; break; }
            if (!has) continue;
            int n = Xp.hookAllMethods(c, "sendCallStanza", STANZA).size();
            if (n > 0) sStanzas = c;
            return n;
        }
        return 0;
    }

    private static void stanzasFrom(Object root) {
        if (sStanzas != null || root == null) return;
        try {
            java.util.List<Object> next = new java.util.ArrayList<>();
            for (Object o : fieldValues(root)) { if (hasStanzaMethod(o.getClass()) && hookStanzasOn(o.getClass(), "held by the call manager") > 0) return; next.add(o); }
            for (Object o : next) {
                String n = o.getClass().getName();
                if (n.startsWith("java.") || n.startsWith("android.") || n.startsWith("kotlin") || o.getClass().isArray()) continue;
                for (Object x : fieldValues(o)) if (hasStanzaMethod(x.getClass()) && hookStanzasOn(x.getClass(), "near the call manager") > 0) return;
            }
        } catch (Throwable t) { Log.w(TAG, "dna whatsapp: stanzas", t); }
    }

    private static boolean hasStanzaMethod(Class<?> k) {
        for (Class<?> c = k; c != null && c != Object.class; c = c.getSuperclass()) {
            if (c.getName().startsWith("java.") || c.getName().startsWith("android.")) return false;
            for (Method m : c.getDeclaredMethods()) if ("sendCallStanza".equals(m.getName()) && !Modifier.isAbstract(m.getModifiers())) return true;
        }
        return false;
    }

    private static String tag(Object node) {
        if (node == null) return "?";
        Object t = get(node, "getTag"); if (t == null) t = field(node, "tag");
        if (t == null) for (Object v : fieldValues(node)) if (v instanceof String && ((String) v).matches("[a-z_]{2,24}")) { t = v; break; }
        return t == null ? "?" : t.toString();
    }

    private static String attr(Object node, String name) {
        if (node == null) return null;
        try {
            Method m = node.getClass().getMethod("getFirstAttributeByName", String.class);
            String v = otherString(m.invoke(node, name), name);
            if (v != null) return v;
        } catch (Throwable ignored) { }
        try {
            for (Object v : fieldValues(node)) {
                if (!(v instanceof Object[])) continue;
                for (Object a : (Object[]) v) {
                    if (a == null) continue;
                    boolean named = false;
                    for (Object x : fieldValues(a)) if (name.equals(x)) { named = true; break; }
                    if (named) return otherString(a, name);
                }
            }
        } catch (Throwable ignored) { }
        return null;
    }

    private static String otherString(Object a, String name) {
        if (a == null) return null;
        for (Object v : fieldValues(a)) if (v instanceof String && !name.equals(v)) return (String) v;
        return null;
    }

    static String plainJid(String raw) { return raw == null ? "" : raw.replaceFirst("[.:][\\d:.]*@", "@"); }

    private static Object managerNear(Object root) {
        Class<?> v = sVoip;
        if (root == null || v == null) return null;
        if (v.isInstance(root)) return root;
        java.util.List<Object> next = new java.util.ArrayList<>();
        for (Object o : fieldValues(root)) { if (v.isInstance(o)) return o; next.add(o); }
        for (Object o : next) {
            String n = o.getClass().getName();
            if (n.startsWith("java.") || n.startsWith("android.") || n.startsWith("kotlin") || o.getClass().isArray()) continue;
            for (Object x : fieldValues(o)) if (v.isInstance(x)) return x;
        }
        return null;
    }

    private static java.util.List<Object> fieldValues(Object o) {
        java.util.List<Object> out = new java.util.ArrayList<>();
        for (Class<?> k = o.getClass(); k != null && k != Object.class && k != Handler.class; k = k.getSuperclass())
            for (java.lang.reflect.Field f : k.getDeclaredFields()) {
                if (f.getType().isPrimitive() || Modifier.isStatic(f.getModifiers())) continue;
                try { f.setAccessible(true); Object x = f.get(o); if (x != null) out.add(x); } catch (Throwable ignored) { }
            }
        return out;
    }

    private static int hookCallInfo() {
        return Xp.hookAllConstructors(sCallInfo, new Xp.Callback() {
            @Override protected void afterHookedMethod(Xp.Param p) {
                final Object info = p.thisObject; if (info == null) return;
                sLatest = info;
                try {
                    if (!active() || !ringing(info)) return;
                    MAIN.post(() -> { try { turnAway(info, null, "call info"); } catch (Throwable t) { Log.w(TAG, "dna whatsapp: call info", t); } });
                } catch (Throwable ignored) { }
            }
        }).size();
    }

    private static int hookStateChanged(String[] m) {
        Class<?> k = load(m[0]); if (k == null) return 0;
        int n = 0;
        for (final Method x : k.getDeclaredMethods()) {
            if (!x.getName().equals(m[1]) || x.getParameterTypes().length != m.length - 2) continue;
            boolean same = true; Class<?>[] t = x.getParameterTypes();
            for (int i = 0; i < t.length; i++) if (!descriptor(t[i]).equals(m[2 + i])) { same = false; break; }
            if (!same) continue;
            Xp.hookMethod(x, new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Object info = null;
                        for (Object a : p.args) {
                            if (a instanceof android.os.Message) a = ((android.os.Message) a).obj;
                            if (a != null && sCallInfo != null && sCallInfo.isInstance(a)) { info = a; break; }
                            if (a != null && sCallInfo == null && a.getClass().getName().endsWith(".CallInfo")) { sCallInfo = a.getClass(); info = a; break; }
                        }
                        if (info == null) return;
                        sLatest = info;
                        if (p.thisObject != null) sStateOwner = p.thisObject;
                        if (!active() || !ringing(info)) return;
                        if (turnAway(info, null, "call state")) {
                            Class<?> r = x.getReturnType();
                            p.setResult(r == boolean.class ? (Object) Boolean.TRUE : r == int.class ? (Object) 0 : null);
                        }
                    } catch (Throwable t) { Log.w(TAG, "dna whatsapp: call state", t); }
                }
            });
            n++;
        }
        return n;
    }

    private static String descriptor(Class<?> c) {
        if (c.isArray()) return "[" + descriptor(c.getComponentType());
        if (c == int.class) return "I"; if (c == boolean.class) return "Z"; if (c == long.class) return "J"; if (c == void.class) return "V";
        if (c == byte.class) return "B"; if (c == char.class) return "C"; if (c == short.class) return "S"; if (c == float.class) return "F"; if (c == double.class) return "D";
        return "L" + c.getName().replace('.', '/') + ";";
    }

    private static boolean ringing(Object info) {
        Object st = stateOf(info);
        if (st != null) return "RECEIVED_CALL".equals(st.toString()) || (st instanceof Enum && "RECEIVED_CALL".equals(((Enum<?>) st).name()));
        return Boolean.FALSE.equals(get(info, "isCaller")) && Boolean.FALSE.equals(get(info, "isCallActive"));
    }

    private static Object stateOf(Object info) {
        Object st = field(info, "callState");
        if (st == null) st = get(info, "getCallState");
        if (st != null) return st;
        for (Object v : fieldValues(info)) {
            if (!(v instanceof Enum)) continue;
            Object[] all = ((Enum<?>) v).getDeclaringClass().getEnumConstants();
            if (all != null) for (Object e : all) if ("RECEIVED_CALL".equals(((Enum<?>) e).name())) return v;
        }
        return null;
    }

    private static String callIdOf(Object info) {
        Object id = get(info, "getCallId"); if (id == null) id = field(info, "callId");
        if (id == null) for (Object v : fieldValues(info)) if (v instanceof String && CALL_ID.matcher((String) v).matches()) { id = v; break; }
        return id == null ? null : id.toString();
    }

    private static Object peerOf(Object info) {
        Object j = get(info, "getPeerJid"); if (j == null) j = field(info, "peerJid");
        if (j == null) for (Object v : fieldValues(info)) { Object r = v instanceof String ? null : get(v, "getRawString"); if (r instanceof String && ((String) r).indexOf('@') > 0) { j = v; break; } }
        return j;
    }

    private static Object field(Object o, String name) {
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass())
            try { java.lang.reflect.Field f = k.getDeclaredField(name); f.setAccessible(true); return f.get(o); } catch (NoSuchFieldException ignored) { } catch (Throwable t) { return null; }
        return null;
    }

    static boolean incoming(Notification n) {
        Bundle x = n.extras;
        boolean call = Notification.CATEGORY_CALL.equals(n.category) || (x != null && x.containsKey("android.callType"));
        if (!call) return false;
        int type = x == null ? -1 : x.getInt("android.callType", -1);
        if (type >= 0) return type == 1;
        return n.fullScreenIntent != null;
    }

    static synchronized boolean turnAway(Object info, Notification n, String from) {
        if (info == null) info = callInfo();
        if (info == null) { return false; }
        if (!ringing(info)) { if (n == null) return false; return false; }
        String id = callIdOf(info);
        if (id == null) { return false; }
        long now = android.os.SystemClock.uptimeMillis();
        if (id.equals(sLastId) && now - sLastAt < 60000) return true;
        sTurned.put(id, now);
        if (!reject(id)) { sTurned.remove(id); return false; }
        sLastId = id; sLastAt = now;
        Object jid = peerOf(info);
        Object rawObj = jid == null ? null : get(jid, "getRawString");
        String raw = jid == null ? "" : String.valueOf(rawObj != null ? rawObj : jid);
        tellOnce(id, name(n), raw);
        return true;
    }

    private static Object callInfo() {
        Class<?> v = sVoip; if (v == null) return sLatest;
        for (Method m : v.getDeclaredMethods()) {
            if (!"getCallInfo".equals(m.getName()) || m.getParameterTypes().length != 0) continue;
            try { m.setAccessible(true); Object o = m.invoke(Modifier.isStatic(m.getModifiers()) ? null : sManager); if (o != null) return o; } catch (Throwable ignored) { }
        }
        return sLatest;
    }

    private static volatile Method sReject;
    private static Method rejectMethod() {
        Method m = sReject;
        if (m != null) return m;
        Object mgr = sManager;

        for (int pass = 0; pass < 2 && m == null; pass++)
            for (Class<?> start : new Class<?>[]{sVoip, mgr == null ? null : mgr.getClass()})
                for (Class<?> k = start; k != null && k != Object.class && m == null; k = k.getSuperclass())
                    for (Method x : k.getDeclaredMethods()) {
                        String n = x.getName(); Class<?>[] t = x.getParameterTypes();
                        boolean ok = pass == 0 ? "rejectCall".equals(n)
                                : n.toLowerCase(java.util.Locale.ROOT).contains("reject") && !n.contains("Pending") && !n.contains("Without") && t.length >= 2 && t[0] == String.class && t[1] == String.class;
                        if (ok) { m = x; break; }
                    }
        if (m != null && mgr != null) sReject = m;
        return m;
    }

    private static boolean reject(String id) {
        Method m = rejectMethod();
        if (m == null) { Log.w(TAG, "dna whatsapp: no rejectCall in WhatsApp's call engine"); return false; }
        boolean isStatic = Modifier.isStatic(m.getModifiers());
        if (!isStatic && sManager == null) try { found(managerNear(sStateOwner), "call state's fields"); } catch (Throwable ignored) { }
        Object target = isStatic ? null : sManager;
        if (!isStatic && target == null) { Log.w(TAG, "dna whatsapp: the call manager isn't known yet"); return false; }
        Class<?>[] t = m.getParameterTypes(); Object[] a = new Object[t.length];
        for (int i = 0; i < t.length; i++) {
            if (t[i] == int.class) a[i] = 0; else if (t[i] == long.class) a[i] = 0L; else if (t[i] == boolean.class) a[i] = false;
            else if (t[i] == float.class) a[i] = 0f; else if (t[i] == double.class) a[i] = 0d; else if (t[i] == short.class) a[i] = (short) 0;
            else if (t[i] == byte.class) a[i] = (byte) 0; else if (t[i] == char.class) a[i] = (char) 0; else a[i] = null;
        }
        if (t.length > 0) a[0] = id;
        if (t.length > 1 && t[1] == String.class) a[1] = REASON;
        try { m.setAccessible(true); m.invoke(target, a); return true; }
        catch (Throwable e) { Log.w(TAG, "dna whatsapp: rejectCall", e); return false; }
    }

    private static Object get(Object o, String method) {
        if (o == null) return null;
        for (Class<?> k = o.getClass(); k != null; k = k.getSuperclass())
            try { Method m = k.getDeclaredMethod(method); m.setAccessible(true); return m.invoke(o); } catch (NoSuchMethodException ignored) { } catch (Throwable t) { return null; }
        return null;
    }

    private static String[] fromWhatsApp(Context c, String jid) {
        String id = jid.replaceFirst("\\.[\\d:]+@", "@"), phone = null, name = null;
        java.io.File dir = c.getFilesDir().getParentFile();
        try {
            java.io.File sw = new java.io.File(dir, "app_account_switching/active_account");
            if (sw.exists()) { String a = new String(java.nio.file.Files.readAllBytes(sw.toPath()), "UTF-8").trim(); java.io.File d = new java.io.File(dir, "accounts/" + a); if (!a.isEmpty() && d.isDirectory()) dir = d; }
        } catch (Throwable ignored) { }
        if (id.endsWith("@lid")) {
            android.database.sqlite.SQLiteDatabase db = open(new java.io.File(dir, "databases/msgstore.db"));
            if (db != null) try {
                String q = "SELECT phone.raw_string FROM jid_map INNER JOIN jid lid ON lid._id = jid_map.lid_row_id INNER JOIN jid phone ON phone._id = jid_map.jid_row_id"
                        + " WHERE lid.raw_string = ? AND phone.server = 's.whatsapp.net'";
                phone = one(db, q + " ORDER BY jid_map.sort_id DESC LIMIT 1", id);
                if (phone == null) phone = one(db, q + " LIMIT 1", id);
            } finally { db.close(); }
        }
        android.database.sqlite.SQLiteDatabase db = open(new java.io.File(dir, "databases/wa.db"));
        if (db != null) try {
            for (String j : new String[]{id, phone}) {
                if (j == null || name != null) continue;
                name = one(db, "SELECT display_name FROM wa_address_book WHERE jid = ? AND display_name IS NOT NULL AND display_name != '' LIMIT 1", j);
                if (name == null) name = one(db, "SELECT display_name FROM wa_contacts WHERE jid = ? AND display_name IS NOT NULL AND display_name != '' LIMIT 1", j);
            }
            for (String j : new String[]{id, phone}) {
                if (j == null || name != null) continue;
                name = one(db, "SELECT wa_name FROM wa_contacts WHERE jid = ? AND wa_name IS NOT NULL AND wa_name != '' LIMIT 1", j);
            }
        } finally { db.close(); }
        return new String[]{phone, name == null ? null : name.trim()};
    }

    private static android.database.sqlite.SQLiteDatabase open(java.io.File f) {
        if (!f.exists()) return null;
        try { return android.database.sqlite.SQLiteDatabase.openDatabase(f.getPath(), null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY | android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS); }
        catch (Throwable t) { return null; }
    }

    private static String one(android.database.sqlite.SQLiteDatabase db, String sql, String arg) {
        Cursor q = null;
        try { q = db.rawQuery(sql, new String[]{arg}); return q.moveToFirst() && !q.isNull(0) ? q.getString(0) : null; }
        catch (Throwable t) { return null; }
        finally { if (q != null) q.close(); }
    }

    private static String name(Notification n) {
        if (n == null || n.extras == null) return null;
        try {
            Object person = n.extras.get("android.callPerson");
            if (person != null) { Object nm = person.getClass().getMethod("getName").invoke(person); if (nm != null && nm.toString().trim().length() > 0) return nm.toString().trim(); }
        } catch (Throwable ignored) { }
        try { CharSequence t = n.extras.getCharSequence(Notification.EXTRA_TITLE); if (t != null && t.toString().trim().length() > 0) return t.toString().trim(); } catch (Throwable ignored) { }
        return null;
    }

    private static final java.util.Set<String> sTold = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private static void tellOnce(String id, String name, String rawJid) { if (id == null || sTold.add(id)) tell(name, rawJid); }

    private static void tell(final String name, final String rawJid) {
        final Context c = app(); if (c == null) return;
        final String jid = plainJid(rawJid);
        new Thread(() -> {
            String pkg = c.getPackageName(), mime = "vnd.android.cursor.item/vnd." + pkg + ".voip.call";

            String[] wa = {null, null};
            try { wa = fromWhatsApp(c, jid); } catch (Throwable ignored) { }
            String phoneJid = jid.endsWith("@lid") ? wa[0] : jid;
            if (phoneJid == null) phoneJid = "";
            String number = phoneJid.endsWith("@s.whatsapp.net") ? phoneJid.substring(0, phoneJid.indexOf('@')).replaceAll("[^0-9]", "") : "";
            long row = -1; String title = name;

            Cursor q = null;
            try {
                if (phoneJid.length() > 0) q = c.getContentResolver().query(ContactsContract.Data.CONTENT_URI, new String[]{ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME},
                        ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.Data.DATA1 + "=?", new String[]{mime, phoneJid}, null);
                if ((q == null || q.getCount() == 0) && name != null) {
                    if (q != null) q.close();
                    q = c.getContentResolver().query(ContactsContract.Data.CONTENT_URI, new String[]{ContactsContract.Data._ID, ContactsContract.Data.DISPLAY_NAME},
                            ContactsContract.Data.MIMETYPE + "=? AND " + ContactsContract.Data.DISPLAY_NAME + "=?", new String[]{mime, name}, null);
                }
                if (q != null && q.moveToFirst()) { row = q.getLong(0); if (title == null) title = q.getString(1); }
            } catch (Throwable ignored) { }
            finally { if (q != null) q.close(); }
            if (title == null) title = wa[1];
            if (title == null && !number.isEmpty()) {
                Cursor p = null;
                try { p = c.getContentResolver().query(android.net.Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode("+" + number)), new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null); if (p != null && p.moveToFirst()) title = p.getString(0); }
                catch (Throwable ignored) { } finally { if (p != null) p.close(); }
            }
            if (title == null) title = number.isEmpty() ? "WhatsApp" : "+" + number;
            Intent i = new Intent(ACTION).setClassName("io.github.roviandev.pixelsuite", "io.github.pixelsuite.DoNotAnnoyWaReceiver")
                    .putExtra("title", title).putExtra("number", number).putExtra("row", row).putExtra("mime", mime).putExtra("app", pkg)
                    .putExtra("time", System.currentTimeMillis())
                    .addFlags(0x01000000 | Intent.FLAG_RECEIVER_FOREGROUND | Intent.FLAG_INCLUDE_STOPPED_PACKAGES);

            try {
                Class<?> bo = Class.forName("android.app.BroadcastOptions");
                Object o = bo.getMethod("makeBasic").invoke(null);
                bo.getMethod("setShareIdentityEnabled", boolean.class).invoke(o, true);
                Context.class.getMethod("sendBroadcast", Intent.class, String.class, Bundle.class).invoke(c, i, null, bo.getMethod("toBundle").invoke(o));
            } catch (Throwable t) { c.sendBroadcast(i); }
        }, "PixelSuiteDNAwa").start();
    }
}
