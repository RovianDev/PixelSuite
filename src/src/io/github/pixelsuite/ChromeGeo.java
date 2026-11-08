package io.github.pixelsuite;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

final class ChromeGeo {
    private ChromeGeo() {}

    private static final String ADAPTER = "org.chromium.device.geolocation.LocationProviderAdapter";
    private static final String HEADER = "org.chromium.chrome.browser.omnibox.geo.GeolocationHeader";
    private static final String MONITOR = "org.chromium.device.time_zone_monitor.TimeZoneMonitor";
    private static final String OBSERVER = "org.chromium.content.browser.webcontents.WebContentsObserverProxy";
    private static final String MARK = "pixelsuite_vpn_zone";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Random sJitter = new Random();
    private static boolean sInstalled;
    private static String sLogged, sSearchLogged;

    static synchronized void install(ClassLoader l) {
        if (sInstalled) return;
        sInstalled = true;
        swap(l, ADAPTER, void.class);
        swap(l, HEADER, String.class);
        provider(l); search(l); zones(l);
        MAIN.post(new Runnable() { public void run() {
            try { Context app = app(); if (app != null) { ChromeVpn.prime(app); ChromeVpn.watch(ZONE); } } catch (Throwable ignored) { }
            ZONE.run();
        }});
    }

    private static Context app() throws Throwable {
        return (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
    }

    private static String country() {
        try {
            Context app = app();
            if (app == null) return null;
            String code = ChromeVpn.exitCountry(app);
            if (code == null) return null;
            code = code.toUpperCase(Locale.ROOT);
            if (!CITY.containsKey(code)) {
                if (!code.equals(sLogged)) { sLogged = code; }
                return null;
            }
            return code;
        } catch (Throwable t) { return null; }
    }

    private static Location fake(Location base, boolean precise) {
        String code = country();
        if (code == null) return null;
        double[] at = CITY.get(code);
        Random day = new Random(code.hashCode() * 1000003L + System.currentTimeMillis() / 86400000L);
        double dist = 400 + day.nextDouble() * 1800, angle = day.nextDouble() * 2 * Math.PI;
        double noise = precise ? 3 : 20;
        double north = dist * Math.cos(angle) + sJitter.nextGaussian() * noise;
        double east = dist * Math.sin(angle) + sJitter.nextGaussian() * noise;
        double lat = at[0] + north / 111320d, lon = at[1] + east / (111320d * Math.cos(Math.toRadians(at[0])));
        Location f = base != null ? new Location(base) : new Location("fused");
        f.setLatitude(lat);
        f.setLongitude(lon);
        f.setAccuracy(precise ? 6f + sJitter.nextFloat() * 10f : 40f + sJitter.nextFloat() * 60f);
        f.removeAltitude();
        f.removeBearing();
        f.removeSpeed();
        f.setTime(System.currentTimeMillis());
        try { f.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos()); } catch (Throwable ignored) { }
        if (!code.equals(sLogged)) {
            sLogged = code;
        }
        return f;
    }


    private static Method sDeliver, sStart;
    private static volatile boolean sDelivering;
    private static WeakReference<Object> sSpoofed = new WeakReference<Object>(null);
    private static boolean sPrecise;

    private static int swap(ClassLoader l, String name, Class<?> returns) {
        int n = 0;
        try {
            Class<?> k = l.loadClass(name);
            for (Method m : k.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!Modifier.isStatic(m.getModifiers()) || p.length != 2 || p[0] != Location.class || p[1] != boolean.class || m.getReturnType() != returns) continue;
                if (returns == void.class) { m.setAccessible(true); sDeliver = m; }
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void beforeHookedMethod(Xp.Param param) {
                        if (sDelivering) return;
                        try {
                            Location f = fake((Location) param.args[0], Boolean.TRUE.equals(param.args[1]));
                            if (f != null) param.args[0] = f;
                        } catch (Throwable ignored) { }
                    }
                });
                n++;
            }
        } catch (Throwable t) { }
        return n;
    }

    private static int provider(ClassLoader l) {
        int n = 0;
        try {
            Class<?> k = l.loadClass(ADAPTER);
            for (Method m : k.getDeclaredMethods()) {
                if (Modifier.isStatic(m.getModifiers()) || m.getReturnType() != void.class) continue;
                Class<?>[] p = m.getParameterTypes();
                if ("start".equals(m.getName()) && p.length == 1 && p[0] == boolean.class) {
                    m.setAccessible(true);
                    sStart = m;
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param param) {
                            if (sDeliver == null || country() == null) return;
                            param.setResult(null);
                            final Object adapter = param.thisObject;
                            final boolean precise = Boolean.TRUE.equals(param.args[0]);
                            MAIN.post(new Runnable() { public void run() {
                                sSpoofed = new WeakReference<Object>(adapter);
                                sPrecise = precise;
                                MAIN.removeCallbacks(TICK);
                                MAIN.postDelayed(TICK, 150);
                            }});
                        }
                    });
                    n++;
                } else if ("stop".equals(m.getName()) && p.length == 0) {
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void beforeHookedMethod(Xp.Param param) {
                            final Object adapter = param.thisObject;
                            MAIN.post(new Runnable() { public void run() {
                                if (sSpoofed.get() == adapter) { sSpoofed = new WeakReference<Object>(null); MAIN.removeCallbacks(TICK); }
                            }});
                        }
                    });
                    n++;
                }
            }
        } catch (Throwable t) { }
        return n;
    }

    private static final Runnable TICK = new Runnable() {
        public void run() {
            Object adapter = sSpoofed.get();
            if (adapter == null) return;
            Location f = fake(null, sPrecise);
            if (f == null) {
                sSpoofed = new WeakReference<Object>(null);
                try { if (sStart != null) sStart.invoke(adapter, sPrecise); } catch (Throwable ignored) { }
                return;
            }
            sDelivering = true;
            try { sDeliver.invoke(null, f, sPrecise); } catch (Throwable t) { } finally { sDelivering = false; }
            MAIN.postDelayed(this, sPrecise ? 1000 : 3000);
        }
    };

    private static int search(ClassLoader l) {
        int n = 0;
        try {
            Class<?> k = l.loadClass(HEADER);
            for (Method m : k.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] p = m.getParameterTypes();
                if (m.getReturnType() == int.class && p.length == 3 && p[0] == String.class) {
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void afterHookedMethod(Xp.Param param) {
                            Object r = param.getResult();
                            if (!(r instanceof Integer) || (Integer) r != 4) return;
                            String code = country();
                            if (code == null) return;
                            param.setResult(0);
                            if (!code.equals(sSearchLogged)) { sSearchLogged = code; }
                        }
                    });
                    n++;
                } else if (m.getReturnType() == Location.class && p.length == 1 && p[0] == boolean.class) {
                    Xp.hookMethod(m, new Xp.Callback() {
                        @Override protected void afterHookedMethod(Xp.Param param) {
                            Location f = fake(null, Boolean.TRUE.equals(param.args[0]));
                            if (f != null) param.setResult(f);
                        }
                    });
                    n++;
                }
            }
        } catch (Throwable t) { }
        return n;
    }

    private static WeakReference<Object> sMonitor = new WeakReference<Object>(null);
    private static String sZone;
    private static final HashSet<Integer> sProcesses = new HashSet<Integer>();
    private static boolean sReceiverHooked, sMissingLogged;

    private static int zones(ClassLoader l) {
        int n = 0;
        try {
            final Class<?> k = l.loadClass(MONITOR);
            for (Method m : k.getDeclaredMethods()) {
                if (!Modifier.isStatic(m.getModifiers()) || m.getReturnType() != k) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param param) {
                        final Object monitor = param.getResult();
                        if (monitor == null) return;
                        MAIN.post(new Runnable() { public void run() {
                            sMonitor = new WeakReference<Object>(monitor);
                            hookReceiver(monitor);
                            sZone = null;
                            ZONE.run();
                        }});
                    }
                });
                n++;
            }
        } catch (Throwable t) { }
        try {
            Class<?> w = l.loadClass(OBSERVER);
            for (Method m : w.getDeclaredMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (!"renderFrameCreated".equals(m.getName()) || p.length != 2 || p[0] != int.class) continue;
                Xp.hookMethod(m, new Xp.Callback() {
                    @Override protected void afterHookedMethod(Xp.Param param) {
                        final int process = (Integer) param.args[0];
                        MAIN.post(new Runnable() { public void run() { newProcess(process); } });
                    }
                });
                n++;
            }
        } catch (Throwable t) { }
        return n;
    }

    private static final Runnable ZONE = new Runnable() {
        public void run() {
            try {
                String code = country();
                String want = code == null ? null : zoneOf(code);
                if (want == null ? sZone == null : want.equals(sZone)) return;
                String to = want != null ? want : TimeZone.getDefault().getID();
                if (!send(to)) return;
                sZone = want;
                sProcesses.clear();
            } catch (Throwable ignored) { }
        }
    };

    private static void newProcess(int process) {
        if (sZone == null || !sProcesses.add(process)) return;
        if (sProcesses.size() > 400) sProcesses.clear();
        int off = TimeZone.getTimeZone(sZone).getOffset(System.currentTimeMillis()) / 60000;
        String same = off == 0 ? "GMT" : String.format(Locale.ROOT, "GMT%c%02d:%02d", off < 0 ? '-' : '+', Math.abs(off) / 60, Math.abs(off) % 60);
        if (send(same)) send(sZone);
    }

    private static String zoneOf(String code) {
        String all = ZONES.get(code);
        if (all == null) return null;
        for (String z : all.split("\\|")) if (TimeZone.getTimeZone(z).getID().equals(z)) return z;
        return null;
    }

    private static boolean send(String zone) {
        Object monitor = sMonitor.get();
        BroadcastReceiver receiver = monitor == null ? null : (BroadcastReceiver) field(monitor, BroadcastReceiver.class);
        Object nativePtr = monitor == null ? null : field(monitor, long.class);
        if (receiver == null || !(nativePtr instanceof Long) || (Long) nativePtr == 0L) {
            if (!sMissingLogged && monitor == null && country() != null) { sMissingLogged = true; }
            return false;
        }
        try {
            Intent i = new Intent(Intent.ACTION_TIMEZONE_CHANGED).putExtra("time-zone", zone).putExtra(MARK, true);
            receiver.onReceive(app(), i);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private static void hookReceiver(Object monitor) {
        if (sReceiverHooked) return;
        Object r = field(monitor, BroadcastReceiver.class);
        if (r == null) return;
        sReceiverHooked = true;
        try {
            Xp.hookAllMethods(r.getClass(), "onReceive", new Xp.Callback() {
                @Override protected void beforeHookedMethod(Xp.Param param) {
                    if (param.args.length < 2 || !(param.args[1] instanceof Intent)) return;
                    Intent i = (Intent) param.args[1];
                    final String zone = sZone;
                    if (zone == null || i.getBooleanExtra(MARK, false)) return;
                    param.args[1] = new Intent(i).putExtra("time-zone", zone);
                }
            });
        } catch (Throwable ignored) { }
    }

    private static Object field(Object o, Class<?> type) {
        for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
            for (Field f : k.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers())) continue;
                if (type.isPrimitive() ? f.getType() != type : !type.isAssignableFrom(f.getType())) continue;
                try { f.setAccessible(true); return f.get(o); } catch (Throwable ignored) { }
            }
        }
        return null;
    }

    private static final HashMap<String, double[]> CITY = new HashMap<String, double[]>();
    private static final HashMap<String, String> ZONES = new HashMap<String, String>();
    private static void c(String code, double lat, double lon, String zone) { CITY.put(code, new double[]{lat, lon}); ZONES.put(code, zone); }
    static {
        c("JP", 35.6895, 139.6917, "Asia/Tokyo"); c("KR", 37.5665, 126.9780, "Asia/Seoul");
        c("US", 40.7128, -74.0060, "America/New_York"); c("GB", 51.5074, -0.1278, "Europe/London");
        c("TH", 13.7563, 100.5018, "Asia/Bangkok"); c("VN", 21.0278, 105.8342, "Asia/Ho_Chi_Minh");
        c("RU", 55.7558, 37.6173, "Europe/Moscow"); c("ID", -6.2088, 106.8456, "Asia/Jakarta");
        c("IN", 28.6139, 77.2090, "Asia/Kolkata"); c("RO", 44.4268, 26.1025, "Europe/Bucharest");
        c("LC", 14.0101, -60.9875, "America/St_Lucia"); c("AE", 25.2048, 55.2708, "Asia/Dubai");
        c("DE", 52.5200, 13.4050, "Europe/Berlin"); c("FR", 48.8566, 2.3522, "Europe/Paris");
        c("CA", 43.6532, -79.3832, "America/Toronto"); c("NL", 52.3676, 4.9041, "Europe/Amsterdam");
        c("TW", 25.0330, 121.5654, "Asia/Taipei"); c("HK", 22.3193, 114.1694, "Asia/Hong_Kong");
        c("CN", 39.9042, 116.4074, "Asia/Shanghai"); c("SG", 1.3521, 103.8198, "Asia/Singapore");
        c("PH", 14.5995, 120.9842, "Asia/Manila"); c("MY", 3.1390, 101.6869, "Asia/Kuala_Lumpur");
        c("AU", -33.8688, 151.2093, "Australia/Sydney"); c("NZ", -36.8485, 174.7633, "Pacific/Auckland");
        c("BR", -23.5505, -46.6333, "America/Sao_Paulo"); c("ES", 40.4168, -3.7038, "Europe/Madrid");
        c("IT", 41.9028, 12.4964, "Europe/Rome"); c("PL", 52.2297, 21.0122, "Europe/Warsaw");
        c("UA", 50.4501, 30.5234, "Europe/Kyiv|Europe/Kiev"); c("TR", 41.0082, 28.9784, "Europe/Istanbul");
        c("SE", 59.3293, 18.0686, "Europe/Stockholm"); c("NO", 59.9139, 10.7522, "Europe/Oslo");
        c("FI", 60.1699, 24.9384, "Europe/Helsinki"); c("DK", 55.6761, 12.5683, "Europe/Copenhagen");
        c("CH", 47.3769, 8.5417, "Europe/Zurich"); c("AT", 48.2082, 16.3738, "Europe/Vienna");
        c("BE", 50.8503, 4.3517, "Europe/Brussels"); c("CZ", 50.0755, 14.4378, "Europe/Prague");
        c("HU", 47.4979, 19.0402, "Europe/Budapest"); c("GR", 37.9838, 23.7275, "Europe/Athens");
        c("PT", 38.7223, -9.1393, "Europe/Lisbon"); c("IE", 53.3498, -6.2603, "Europe/Dublin");
        c("MX", 19.4326, -99.1332, "America/Mexico_City"); c("AR", -34.6037, -58.3816, "America/Argentina/Buenos_Aires");
        c("CL", -33.4489, -70.6693, "America/Santiago"); c("CO", 4.7110, -74.0721, "America/Bogota");
        c("PE", -12.0464, -77.0428, "America/Lima"); c("ZA", -26.2041, 28.0473, "Africa/Johannesburg");
        c("EG", 30.0444, 31.2357, "Africa/Cairo"); c("IL", 32.0853, 34.7818, "Asia/Jerusalem");
        c("SA", 24.7136, 46.6753, "Asia/Riyadh"); c("QA", 25.2854, 51.5310, "Asia/Qatar");
        c("KW", 29.3759, 47.9774, "Asia/Kuwait"); c("BH", 26.2285, 50.5860, "Asia/Bahrain");
        c("OM", 23.5880, 58.3829, "Asia/Muscat"); c("JO", 31.9454, 35.9284, "Asia/Amman");
        c("IR", 35.6892, 51.3890, "Asia/Tehran"); c("PK", 24.8607, 67.0011, "Asia/Karachi");
        c("BD", 23.8103, 90.4125, "Asia/Dhaka"); c("LK", 6.9271, 79.8612, "Asia/Colombo");
        c("NP", 27.7172, 85.3240, "Asia/Kathmandu"); c("KH", 11.5564, 104.9282, "Asia/Phnom_Penh");
        c("MM", 16.8409, 96.1735, "Asia/Yangon"); c("LA", 17.9757, 102.6331, "Asia/Vientiane");
        c("MN", 47.8864, 106.9057, "Asia/Ulaanbaatar"); c("KZ", 43.2220, 76.8512, "Asia/Almaty");
        c("UZ", 41.2995, 69.2401, "Asia/Tashkent"); c("GE", 41.7151, 44.8271, "Asia/Tbilisi");
        c("AM", 40.1792, 44.4991, "Asia/Yerevan"); c("AZ", 40.4093, 49.8671, "Asia/Baku");
        c("BY", 53.9045, 27.5615, "Europe/Minsk"); c("LT", 54.6872, 25.2797, "Europe/Vilnius");
        c("LV", 56.9496, 24.1052, "Europe/Riga"); c("EE", 59.4370, 24.7536, "Europe/Tallinn");
        c("BG", 42.6977, 23.3219, "Europe/Sofia"); c("RS", 44.7866, 20.4489, "Europe/Belgrade");
        c("HR", 45.8150, 15.9819, "Europe/Zagreb"); c("SK", 48.1486, 17.1077, "Europe/Bratislava");
        c("SI", 46.0569, 14.5058, "Europe/Ljubljana"); c("IS", 64.1466, -21.9426, "Atlantic/Reykjavik");
        c("LU", 49.6116, 6.1319, "Europe/Luxembourg"); c("MD", 47.0105, 28.8638, "Europe/Chisinau");
        c("CY", 35.1856, 33.3823, "Asia/Nicosia"); c("MT", 35.8989, 14.5146, "Europe/Malta");
        c("NG", 6.5244, 3.3792, "Africa/Lagos"); c("KE", -1.2921, 36.8219, "Africa/Nairobi");
        c("MA", 33.5731, -7.5898, "Africa/Casablanca"); c("DZ", 36.7538, 3.0588, "Africa/Algiers");
        c("TN", 36.8065, 10.1815, "Africa/Tunis"); c("VE", 10.4806, -66.9036, "America/Caracas");
        c("EC", -0.1807, -78.4678, "America/Guayaquil"); c("PA", 8.9824, -79.5199, "America/Panama");
        c("CR", 9.9281, -84.0907, "America/Costa_Rica"); c("DO", 18.4861, -69.9312, "America/Santo_Domingo");
        c("PR", 18.4655, -66.1057, "America/Puerto_Rico"); c("UY", -34.9011, -56.1645, "America/Montevideo");
        c("PY", -25.2637, -57.5759, "America/Asuncion"); c("BO", -16.4897, -68.1193, "America/La_Paz");
        c("GT", 14.6349, -90.5069, "America/Guatemala"); c("JM", 17.9712, -76.7936, "America/Jamaica");
        c("TT", 10.6549, -61.5019, "America/Port_of_Spain"); c("MO", 22.1987, 113.5439, "Asia/Macau");
        c("BN", 4.9031, 114.9398, "Asia/Brunei"); c("FJ", -18.1416, 178.4419, "Pacific/Fiji");
    }
}
