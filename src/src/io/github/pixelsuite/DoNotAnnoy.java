package io.github.pixelsuite;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.CallLog;
import android.provider.ContactsContract;
import android.provider.Settings;

final class DoNotAnnoy {
    private DoNotAnnoy() {}

    static final String PACKAGE = "com.android.imsstack";
    static final String PHONE = "com.android.phone";
    static final String KEY = "pixelsuite_dna";
    private static final String CHANNEL = "pixelsuite_dna";

    private static final String RELIABLE_RINGING = "imsvoice.require_prack_for_alert_bool";

    private static void installReliableRinging(ClassLoader cl) {
        try {
            Class<?> cc = Xp.findClass("com.android.imsstack.core.config.CarrierConfig", cl);
            Xp.hookAllMethods(cc, "writeToParcel", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (!on()) return;
                        android.os.PersistableBundle b = (android.os.PersistableBundle) Xp.getObjectField(p.thisObject, "mConfig");
                        Object[] saved = {b.containsKey(RELIABLE_RINGING), b.getBoolean(RELIABLE_RINGING, false)};
                        Xp.setAdditionalInstanceField(p.thisObject, "ps_saved", saved);
                        b.putBoolean(RELIABLE_RINGING, true);
                    } catch (Throwable ignored) { }
                }
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try {
                        Object[] saved = (Object[]) Xp.getAdditionalInstanceField(p.thisObject, "ps_saved");
                        if (saved == null) return;
                        Xp.setAdditionalInstanceField(p.thisObject, "ps_saved", null);
                        android.os.PersistableBundle b = (android.os.PersistableBundle) Xp.getObjectField(p.thisObject, "mConfig");
                        if ((Boolean) saved[0]) b.putBoolean(RELIABLE_RINGING, (Boolean) saved[1]); else b.remove(RELIABLE_RINGING);
                    } catch (Throwable ignored) { }
                }
            });
            Xp.hookAllMethods(cc, "getBoolean", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    if (p.args.length >= 1 && RELIABLE_RINGING.equals(p.args[0]) && on()) p.setResult(Boolean.TRUE);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static void refreshConfig(ClassLoader cl) {
        try {
            Class<?> factory = Xp.findClass("com.android.imsstack.core.agents.AgentFactory", cl);
            Class<?> ci = Xp.findClass("com.android.imsstack.core.agents.ConfigInterface", cl);
            Object f = Xp.callStaticMethod(factory, "getInstance");
            for (int slot = 0; slot < 2; slot++) {
                Object agent = Xp.callMethod(f, "getAgent", ci, slot);
                if (agent != null) Xp.callMethod(agent, "notifyCarrierConfigChangedForNative");
            }
        } catch (Throwable ignored) { }
    }

    private static ClassLoader sLoader;

    static void install(ClassLoader cl) {
        sLoader = cl;
        try {
            Xp.hookAllMethods(android.app.Application.class, "onCreate", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { watch((Context) p.thisObject); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        try {
            Class<?> manager = Xp.findClass("com.android.imsstack.imsservice.mmtel.ImsCallManager", cl);
            Xp.hookAllMethods(manager, "onCallIncomingReceived", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        Context c0 = app();
                        if (c0 != null) watch(c0);
                        if (p.args.length != 1 || p.args[0] == null || !on()) return;
                        Object session = p.args[0];
                        if ("true".equals(Xp.callMethod(session, "getProperty", "ussd"))) return;
                        if (emergency(session)) return;
                        final String number = number(session);
                        final Object callManager = p.thisObject;
                        final Object call = session;
                        p.setResult(null);
                        try { Xp.callMethod(callManager, "takeSession", call); } catch (Throwable ignored) { }
                        Handler h = handler(session);
                        h.post(() -> {
                            try { Xp.callMethod(Xp.callMethod(call, "getMtcCall"), "alertUser"); } catch (Throwable ignored) { }
                        });
                        h.postDelayed(() -> {
                            try { Xp.callMethod(callManager, "rejectAndDestroyCall", call, false); } catch (Throwable ignored) { }
                        }, ALERT_MS);
                        deflected(number, false);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static final long FOUR_FIVE_G = (1L << 12) | (1L << 18) | (1L << 19) | (1L << 17);
    private static final int REASON_POWER = 1;
    private static boolean sWatching;

    private static synchronized void watch(final Context c) {
        if (sWatching) return;
        sWatching = true;
        observeState(c);
        final Handler h = new Handler(Looper.getMainLooper());
        try {
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, new android.database.ContentObserver(h) {
                @Override public void onChange(boolean self) { networks(c); }
            });
        } catch (Throwable ignored) { }
        final Runnable again = new Runnable() { public void run() { networks(c); h.postDelayed(this, 15 * 60 * 1000L); } };
        h.postDelayed(again, 5000);
    }

    private static void networks(Context c) {
        boolean on = on();
        android.content.SharedPreferences prefs = c.getSharedPreferences("pixelsuite_dna", Context.MODE_PRIVATE);
        try {
            int[] subs = subscriptions(c);
            StringBuilder status = new StringBuilder(on ? "on" : "off");
            android.telephony.TelephonyManager base = (android.telephony.TelephonyManager) c.getSystemService(Context.TELEPHONY_SERVICE);
            for (int sub : subs) {
                Object tm = android.telephony.TelephonyManager.class.getMethod("createForSubscriptionId", int.class).invoke(base, sub);
                java.lang.reflect.Method get = tm.getClass().getMethod("getAllowedNetworkTypesForReason", int.class);
                java.lang.reflect.Method set = tm.getClass().getMethod("setAllowedNetworkTypesForReason", int.class, long.class);
                long current = (Long) get.invoke(tm, REASON_POWER);
                String saved = "power_" + sub;
                if (on) {
                    long want = (prefs.contains(saved) ? prefs.getLong(saved, current) : current) & FOUR_FIVE_G;
                    if (want == 0) want = FOUR_FIVE_G;
                    if (!prefs.contains(saved)) prefs.edit().putLong(saved, current).apply();
                    if (current != want) set.invoke(tm, REASON_POWER, want);
                } else if (prefs.contains(saved)) {
                    long back = prefs.getLong(saved, current);
                    if (current != back) set.invoke(tm, REASON_POWER, back);
                    prefs.edit().remove(saved).apply();
                }
                status.append(" sub").append(sub).append('=').append(Long.toHexString((Long) get.invoke(tm, REASON_POWER)));
            }
            status(c, status.toString());
        } catch (Throwable t) {
            Throwable cause = t instanceof java.lang.reflect.InvocationTargetException && t.getCause() != null ? t.getCause() : t;
            status(c, "error " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
        }
    }

    private static final long ALERT_MS = 150;

    private static Handler handler(Object session) {
        try { Object h = Xp.callMethod(session, "getCallHandler"); if (h instanceof Handler) return (Handler) h; } catch (Throwable ignored) { }
        return new Handler(Looper.getMainLooper());
    }

    private static void status(Context c, String s) {
        try { Settings.Secure.putString(c.getContentResolver(), "pixelsuite_dna_status", s); } catch (Throwable ignored) { }
    }

    private static int[] subscriptions(Context c) {
        android.telephony.SubscriptionManager sm = (android.telephony.SubscriptionManager) c.getSystemService("telephony_subscription_service");
        try { return (int[]) sm.getClass().getMethod("getActiveSubscriptionIdList").invoke(sm); } catch (Throwable ignored) { }
        java.util.List<android.telephony.SubscriptionInfo> list = sm.getActiveSubscriptionInfoList();
        if (list == null) return new int[0];
        int[] out = new int[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i).getSubscriptionId();
        return out;
    }

    private static Context app() {
        try { return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null); }
        catch (Throwable t) { return null; }
    }

    private static boolean on() {
        Context c = app();
        try { return c != null && "1".equals(Settings.Secure.getString(c.getContentResolver(), KEY)); } catch (Throwable t) { return false; }
    }

    private static String number(Object session) {
        try {
            Object mtc = Xp.callMethod(session, "getMtcCall");
            Object n = Xp.callMethod(mtc, "getRemoteNumber");
            if (n instanceof String && !((String) n).isEmpty()) return (String) n;
        } catch (Throwable ignored) { }
        try {
            Object profile = Xp.getObjectField(session, "mCallProfile");
            Object n = Xp.callMethod(profile, "getCallExtra", "oi");
            if (n instanceof String) return (String) n;
        } catch (Throwable ignored) { }
        return "";
    }

    private static boolean emergency(Object session) {
        try {
            Object profile = Xp.getObjectField(session, "mCallProfile");
            Object type = Xp.callMethod(profile, "getServiceType");
            return type instanceof Integer && (Integer) type == 2;
        } catch (Throwable t) {
            return false;
        }
    }

    private static final String BODY_OFF = "Incoming call deflected (device reported as switched off)";
    private static final String BODY_DECLINED = "Incoming call declined by Do Not Annoy";

    private static void deflected(final String number, final boolean declined) {
        final Context c = app();
        if (c == null) return;
        new Thread(() -> {
            if (!declined) networks(c);
            String name = contact(c, number);
            log(c, number, name);
            final String title = name != null ? name : number.isEmpty() ? "Unknown caller" : number;
            final String contact = name;
            final String body = declined ? BODY_DECLINED : BODY_OFF;
            new Handler(Looper.getMainLooper()).post(() -> show(c, title, number, contact, body));
        }, "PixelSuiteDNA").start();
    }

    private static String contact(Context c, String number) {
        if (number == null || number.isEmpty()) return null;
        Cursor q = null;
        try {
            q = c.getContentResolver().query(Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
                    new String[]{ContactsContract.PhoneLookup.DISPLAY_NAME}, null, null, null);
            if (q != null && q.moveToFirst()) {
                String n = q.getString(0);
                if (n != null && !n.isEmpty()) return n;
            }
        } catch (Throwable ignored) {
        } finally {
            if (q != null) q.close();
        }
        return null;
    }

    private static void log(Context c, String number, String name) {
        try {
            ContentValues v = new ContentValues();
            v.put(CallLog.Calls.NUMBER, number == null ? "" : number);
            v.put(CallLog.Calls.DATE, System.currentTimeMillis());
            v.put(CallLog.Calls.DURATION, 0);
            v.put(CallLog.Calls.TYPE, 6);
            v.put(CallLog.Calls.NEW, 1);
            v.put("presentation", number == null || number.isEmpty() ? 2 : 1);
            if (name != null) v.put(CallLog.Calls.CACHED_NAME, name);
            v.put("block_reason", 1);
            v.put("call_screening_app_name", "Do Not Annoy");
            v.put("call_screening_component_name", "io.github.roviandev.pixelsuite/.DoNotAnnoy");
            c.getContentResolver().insert(CallLog.Calls.CONTENT_URI, v);
        } catch (Throwable ignored) { }
    }

    static final String ACTION = "io.github.pixelsuite.action.DNA_DEFLECTED";
    private static void show(final Context c, final String title, final String number, String name, final String body) {
        try {
            Intent i = new Intent(ACTION)
                    .setClassName("io.github.roviandev.pixelsuite", "io.github.pixelsuite.DoNotAnnoyReceiver")
                    .putExtra("number", number == null ? "" : number)
                    .putExtra("name", name)
                    .putExtra("title", title)
                    .putExtra("body", body)
                    .putExtra("time", System.currentTimeMillis())
                    .addFlags(0x01000000  | Intent.FLAG_RECEIVER_FOREGROUND);
            c.sendOrderedBroadcast(i, null, new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context x, Intent r) {
                    if (getResultCode() != 1) DoNotAnnoy.notify(c, title, number, body);
                }
            }, new Handler(Looper.getMainLooper()), 0, null, null);
        } catch (Throwable t) {
            notify(c, title, number, body);
        }
    }

    private static void notify(Context c, String title, String number, String body) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Class<?> chType = Class.forName("android.app.NotificationChannel");
            if (NotificationManager.class.getMethod("getNotificationChannel", String.class).invoke(nm, CHANNEL) == null) {
                Object ch = chType.getConstructor(String.class, CharSequence.class, int.class).newInstance(CHANNEL, "Do Not Annoy", 4 );
                chType.getMethod("setDescription", String.class).invoke(ch, "Calls deflected by Pixel Suite's Do Not Annoy");
                NotificationManager.class.getMethod("createNotificationChannel", chType).invoke(nm, ch);
            }
            int id = (int) (System.currentTimeMillis() & 0x7fffffff);
            Notification.Builder b = new Notification.Builder(c);
            Notification.Builder.class.getMethod("setChannelId", String.class).invoke(b, CHANNEL);
            b.setSmallIcon(android.R.drawable.stat_notify_missed_call)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setSubText("Do Not Annoy")
                    .setCategory("missed_call")
                    .setWhen(System.currentTimeMillis())
                    .setShowWhen(true)
                    .setAutoCancel(true)
                    .setColor(0xff4285f4);
            PendingIntent log = PendingIntent.getActivity(c, id,
                    new Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setContentIntent(log);
            if (number != null && !number.isEmpty()) {
                PendingIntent call = PendingIntent.getActivity(c, id + 1,
                        new Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                b.addAction(new Notification.Action.Builder(android.R.drawable.sym_action_call, "Call", call).build());
                try { b.extend(new Notification.WearableExtender().addAction(new Notification.Action.Builder(android.R.drawable.sym_action_call, "Call", call).build())); } catch (Throwable ignored) { }
            }
            nm.notify("pixelsuite_dna", id, b.build());
        } catch (Throwable ignored) { }
    }

    private static boolean sDeclineInstalled;

    static synchronized void installDecline(ClassLoader cl) {
        if (sDeclineInstalled) return;
        sDeclineInstalled = true;
        try {
            Xp.hookAllMethods(android.app.Application.class, "onCreate", new Xp.Callback() {
                @Override protected void afterHookedMethod(Xp.Param p) {
                    try { observeState((Context) p.thisObject); } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
        try {
            Class<?> phone = Xp.findClass("com.android.internal.telephony.Phone", cl);
            Xp.hookAllMethods(phone, "notifyNewRingingConnectionP", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param p) {
                    try {
                        if (p.args.length != 1 || p.args[0] == null || !on()) return;
                        final Object conn = p.args[0];
                        try { if (Boolean.TRUE.equals(Xp.callMethod(p.thisObject, "isInEcm"))) return; } catch (Throwable ignored) { }
                        try { if (Boolean.TRUE.equals(Xp.callMethod(conn, "isEmergencyCall"))) return; } catch (Throwable ignored) { }
                        try { if (Boolean.FALSE.equals(Xp.callMethod(conn, "isIncoming"))) return; } catch (Throwable ignored) { }
                        final String number = address(conn);
                        p.setResult(null);
                        new Handler(Looper.getMainLooper()).postDelayed(() -> decline(conn), DECLINE_MS);
                        deflected(number, true);
                    } catch (Throwable ignored) { }
                }
            });
        } catch (Throwable ignored) { }
    }

    private static final long DECLINE_MS = 300;

    private static void decline(Object conn) {
        try {
            Object call = Xp.callMethod(conn, "getCall");
            Xp.callMethod(call, "hangup");
            return;
        } catch (Throwable ignored) { }
        try { Xp.callMethod(conn, "hangup"); } catch (Throwable ignored) { }
    }

    private static String address(Object conn) {
        try {
            Object pres = Xp.callMethod(conn, "getNumberPresentation");
            if (pres instanceof Integer && (Integer) pres != 1) return "";
        } catch (Throwable ignored) { }
        try {
            Object a = Xp.callMethod(conn, "getAddress");
            if (a instanceof String) return (String) a;
        } catch (Throwable ignored) { }
        return "";
    }

    static final String KEY_WATCH = "pixelsuite_dna_watch";
    private static boolean sStateWatching;

    static synchronized void observeState(final Context c) {
        if (sStateWatching || c == null) return;
        sStateWatching = true;
        final Handler h = new Handler(Looper.getMainLooper());
        android.database.ContentObserver o = new android.database.ContentObserver(h) {
            @Override public void onChange(boolean self) { sendState(c); }
        };
        try {
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY), false, o);
            c.getContentResolver().registerContentObserver(Settings.Secure.getUriFor(KEY_WATCH), false, o);
        } catch (Throwable ignored) { }
        h.postDelayed(() -> sendState(c), 20000);
    }

    private static void sendState(Context c) {
        try {
            boolean off = on() && "1".equals(Settings.Secure.getString(c.getContentResolver(), KEY_WATCH));
            c.sendBroadcast(new Intent("io.github.pixelsuite.action.DNA_STATE")
                    .setClassName("io.github.roviandev.pixelsuite", "io.github.pixelsuite.DoNotAnnoyReceiver")
                    .putExtra("watch_off", off)
                    .addFlags(0x01000000  | Intent.FLAG_RECEIVER_FOREGROUND));
        } catch (Throwable ignored) { }
    }
}
