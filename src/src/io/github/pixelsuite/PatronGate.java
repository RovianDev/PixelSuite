package io.github.pixelsuite;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Iterator;

final class PatronGate {
    private PatronGate() {}

    static final String KEY = "pixelsuite_patron";
    private static final String SPKI =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE4ocnJHRXyk3q1n1c9EC+5RIVJIdb"
            + "deObXFd6VIJDxpxcy9LdIa7jvnXh9TANY6lpvgen2c3/Hgm1hetm6f1OZg==";
    private static final String ISS = "pixelsuite-patreon";
    private static final String AUD = "io.github.roviandev.pixelsuite";
    private static final long CAMPAIGN = 16907904L;
    private static final long SKEW = 300L;
    private static final long MAX_LIFE = 35L * 86400L;
    private static final long MAX_OFFLINE = 7L * 86400L;

    private static String sPass;
    private static boolean sValid;
    private static long sExp, sOffline;
    private static long sCheckedAt = -1;
    private static boolean sLast;

    static boolean ok(Context c) {
        long up = android.os.SystemClock.uptimeMillis();
        synchronized (PatronGate.class) {
            if (sCheckedAt >= 0 && up - sCheckedAt < 3000L) return sLast;
        }
        boolean v = check(c);
        synchronized (PatronGate.class) { sLast = v; sCheckedAt = up; }
        return v;
    }

    private static boolean check(Context c) {
        try {
            if (c == null) c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c == null) return false;
            return passOk(c, Settings.Secure.getString(c.getContentResolver(), KEY));
        } catch (Throwable t) {
            return false;
        }
    }

    static boolean passOk(Context c, String pass) {
        try {
            if (pass == null) return false;
            pass = pass.trim();
            if (pass.isEmpty() || "null".equals(pass)) return false;
            long exp, off;
            synchronized (PatronGate.class) {
                if (!pass.equals(sPass)) {
                    sPass = pass;
                    sValid = false;
                    long[] t = verify(pass);
                    if (t != null) { sValid = true; sExp = t[0]; sOffline = t[1]; }
                }
                if (!sValid) return false;
                exp = sExp; off = sOffline;
            }
            long now = System.currentTimeMillis() / 1000L;
            return now < (online(c) ? exp : off);
        } catch (Throwable t) {
            return false;
        }
    }

    static final String ESIM_KEY = "pixelsuite_esim", DEVICE_KEY = "pixelsuite_device_id";
    private static final String STORE_ISS = "pixelsuite-store";
    private static String sEsimPass, sEsimDid; private static boolean sEsimValid;
    private static long sEsimAt = -1; private static boolean sEsimLast;

    static boolean esimOk(Context c) {
        long up = android.os.SystemClock.uptimeMillis();
        synchronized (PatronGate.class) { if (sEsimAt >= 0 && up - sEsimAt < 3000L) return sEsimLast; }
        boolean v = false;
        try {
            if (c == null) c = (Context) Class.forName("android.app.ActivityThread").getMethod("currentApplication").invoke(null);
            if (c != null) {
                String pass = Settings.Secure.getString(c.getContentResolver(), ESIM_KEY);
                String did = Settings.Secure.getString(c.getContentResolver(), DEVICE_KEY);
                if (pass != null && !pass.isEmpty() && did != null && b64id(did)) {
                    synchronized (PatronGate.class) {
                        if (!pass.equals(sEsimPass) || !did.equals(sEsimDid)) {
                            sEsimPass = pass; sEsimDid = did; sEsimValid = false;
                            JSONObject p = signed(pass);
                            if (p != null && isInt(p.opt("v"), 1) && STORE_ISS.equals(p.opt("iss")) && AUD.equals(p.opt("aud"))
                                    && "esim".equals(p.opt("product")) && Boolean.TRUE.equals(p.opt("entitled"))
                                    && p.opt("sub") instanceof String && ((String) p.opt("sub")).matches("[A-Za-z0-9_-]{6,64}")
                                    && did.equals(p.opt("did"))) {
                                Long iat = seconds(p.opt("iat"));
                                sEsimValid = iat != null && iat <= System.currentTimeMillis() / 1000L + SKEW;
                            }
                        }
                        v = sEsimValid;
                    }
                }
            }
        } catch (Throwable ignored) { v = false; }
        synchronized (PatronGate.class) { sEsimLast = v; sEsimAt = up; }
        return v;
    }

    private static JSONObject signed(String jwt) throws Exception {
        String[] parts = jwt.trim().split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) return null;
        int flags = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;
        JSONObject header = new JSONObject(new String(Base64.decode(parts[0], flags), "UTF-8"));
        if (!"ES256".equals(header.opt("alg"))) return null;
        Object typ = header.opt("typ");
        if (typ != null && !"JWT".equals(typ)) return null;
        for (Iterator<String> it = header.keys(); it.hasNext(); ) { String k = it.next(); if (!k.equals("alg") && !k.equals("typ")) return null; }
        byte[] sig = Base64.decode(parts[2], flags);
        if (sig.length != 64) return null;
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decode(SPKI, Base64.DEFAULT)));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(key);
        s.update((parts[0] + "." + parts[1]).getBytes("US-ASCII"));
        if (!s.verify(der(sig))) return null;
        return new JSONObject(new String(Base64.decode(parts[1], flags), "UTF-8"));
    }

    private static long[] verify(String jwt) throws Exception {
        String[] parts = jwt.trim().split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) return null;
        int flags = Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP;

        JSONObject header = new JSONObject(new String(Base64.decode(parts[0], flags), "UTF-8"));
        if (!"ES256".equals(header.opt("alg"))) return null;
        Object typ = header.opt("typ");
        if (typ != null && !"JWT".equals(typ)) return null;
        for (Iterator<String> it = header.keys(); it.hasNext(); ) {
            String k = it.next();
            if (!k.equals("alg") && !k.equals("typ")) return null;
        }

        byte[] sig = Base64.decode(parts[2], flags);
        if (sig.length != 64) return null;
        PublicKey key = KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(Base64.decode(SPKI, Base64.DEFAULT)));
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initVerify(key);
        s.update((parts[0] + "." + parts[1]).getBytes("US-ASCII"));
        if (!s.verify(der(sig))) return null;

        JSONObject p = new JSONObject(new String(Base64.decode(parts[1], flags), "UTF-8"));
        if (!isInt(p.opt("v"), 1)) return null;
        if (!ISS.equals(p.opt("iss")) || !AUD.equals(p.opt("aud"))) return null;
        Object campaign = p.opt("campaign");
        if (!(isInt(campaign, CAMPAIGN) || String.valueOf(CAMPAIGN).equals(campaign))) return null;
        Object sub = p.opt("sub");
        if (!(sub instanceof String) || !((String) sub).matches("[0-9]{1,20}")) return null;
        if (!b64id(p.opt("sid")) || !b64id(p.opt("did"))) return null;
        if (!Boolean.TRUE.equals(p.opt("entitled"))) return null;
        Long iat = seconds(p.opt("iat")), exp = seconds(p.opt("exp")), off = seconds(p.opt("offline_until"));
        if (iat == null || exp == null || off == null) return null;
        long now = System.currentTimeMillis() / 1000L;
        if (iat > now + SKEW) return null;
        if (exp <= iat || exp - iat > MAX_LIFE) return null;
        if (off < exp || off - exp > MAX_OFFLINE) return null;
        return new long[]{exp, off};
    }

    private static byte[] der(byte[] raw) {
        byte[] r = derInt(raw, 0), s = derInt(raw, 32);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0x30);
        o.write(r.length + s.length);
        o.write(r, 0, r.length);
        o.write(s, 0, s.length);
        return o.toByteArray();
    }

    private static byte[] derInt(byte[] raw, int at) {
        int start = at, end = at + 32;
        while (start < end - 1 && raw[start] == 0) start++;
        boolean pad = (raw[start] & 0x80) != 0;
        int len = end - start + (pad ? 1 : 0);
        byte[] out = new byte[2 + len];
        out[0] = 0x02;
        out[1] = (byte) len;
        int i = 2;
        if (pad) out[i++] = 0;
        System.arraycopy(raw, start, out, i, end - start);
        return out;
    }

    private static boolean isInt(Object o, long want) {
        return (o instanceof Integer || o instanceof Long) && ((Number) o).longValue() == want;
    }

    private static Long seconds(Object o) {
        if (!(o instanceof Integer || o instanceof Long)) return null;
        long v = ((Number) o).longValue();
        return v > 0 ? v : null;
    }

    private static boolean b64id(Object o) {
        return o instanceof String && ((String) o).matches("[A-Za-z0-9_-]{43}");
    }

    private static boolean online(Context c) {
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm == null) return true;
            Network n = cm.getActiveNetwork();
            if (n == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(n);
            return nc == null || nc.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
        } catch (Throwable t) {
            return true;
        }
    }
}
