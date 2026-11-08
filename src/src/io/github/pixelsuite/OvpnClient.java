package io.github.pixelsuite;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.*;

final class OvpnClient extends VpnTunnel {
    private static final int HARD_RESET_CLIENT_V2 = 7, HARD_RESET_SERVER_V2 = 8, CONTROL_V1 = 4, ACK_V1 = 5, DATA_V1 = 6, SOFT_RESET_V1 = 3;
    private static final byte[] PING = {0x2a, 0x18, 0x7b, (byte) 0xf3, 0x64, 0x1e, (byte) 0xb4, (byte) 0xcb, 0x07, (byte) 0xed, 0x2d, 0x0a, (byte) 0x98, 0x1f, (byte) 0xc7, 0x48};
    private static final SecureRandom RANDOM = new SecureRandom();

    static final class Config {
        String cipher = "AES-128-CBC", auth = "SHA1";
        boolean comp, tls12;
        long waitMs = 10000;
        String cert, key;
        static Config parse(String base64) {
            Config c = new Config();
            try {
                String t = new String(Base64.decode(base64, Base64.DEFAULT), "UTF-8").replace("\r", "");
                for (String line : t.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("cipher ")) c.cipher = line.substring(7).trim().toUpperCase(Locale.ROOT);
                    else if (line.startsWith("auth ")) c.auth = line.substring(5).trim().toUpperCase(Locale.ROOT);
                    else if (line.startsWith("comp-lzo")) c.comp = true;
                }
                c.cert = block(t, "cert"); c.key = block(t, "key");
            } catch (Exception ignored) { }
            return c;
        }
        private static String block(String t, String tag) {
            int a = t.indexOf("<" + tag + ">"), b = t.indexOf("</" + tag + ">");
            return a < 0 || b < a ? null : t.substring(a + tag.length() + 2, b);
        }
        int keyBytes() { return cipher.contains("256") ? 32 : cipher.contains("192") ? 24 : 16; }
        String hmac() { return auth.contains("512") ? "HmacSHA512" : auth.contains("256") ? "HmacSHA256" : "HmacSHA1"; }
        int hmacBytes() { return auth.contains("512") ? 64 : auth.contains("256") ? 32 : 20; }
        boolean supported() { return cipher.endsWith("-CBC") && cipher.startsWith("AES-"); }
    }

    private DatagramSocket sock;
    private final byte[] localSid = new byte[8];
    private byte[] remoteSid;
    private int sendPid, recvNext, dataPid = 1, keyId;
    private final TreeMap<Integer, byte[]> rxPending = new TreeMap<Integer, byte[]>();
    private final ArrayList<Integer> toAck = new ArrayList<Integer>();
    private final LinkedHashMap<Integer, Out> unacked = new LinkedHashMap<Integer, Out>();
    private SSLEngine engine;
    private ByteBuffer netIn = ByteBuffer.allocate(65536);
    private final ByteArrayOutputStream app = new ByteArrayOutputStream();
    private Config cfg;
    private volatile boolean closed, established;
    private volatile Sink sink;
    private Cipher encrypt, decrypt;
    private Mac encMac, decMac;
    private SecretKeySpec encKey, decKey;
    private int macLen;
    private long pingEvery = 10000;
    private final Object sendLock = new Object();

    private static final class Out { final int op; final byte[] payload; long at; Out(int op, byte[] p) { this.op = op; payload = p; } }

    String protocol() { return "OpenVPN UDP"; }
    private static long now() { return android.os.SystemClock.elapsedRealtime(); }

    static long probe(String ip, int port, Protector protector, int timeout) {
        DatagramSocket s = null;
        try {
            s = new DatagramSocket();
            if (protector != null) protector.protect(s);
            s.connect(new InetSocketAddress(ip, port));
            s.setSoTimeout(timeout);
            byte[] sid = new byte[8]; RANDOM.nextBytes(sid);
            byte[] p = new byte[14]; p[0] = (byte) (HARD_RESET_CLIENT_V2 << 3); System.arraycopy(sid, 0, p, 1, 8); p[9] = 0;
            long t0 = now();
            s.send(new DatagramPacket(p, p.length));
            byte[] in = new byte[2048]; DatagramPacket r = new DatagramPacket(in, in.length);
            s.receive(r);
            return ((in[0] & 255) >> 3) == HARD_RESET_SERVER_V2 ? Math.max(1, now() - t0) : -1;
        } catch (Exception e) { return -1; }
        finally { if (s != null) s.close(); }
    }

    void connect(String ip, int port, Config config, String user, String pass, Protector protector) throws IOException {
        cfg = config;
        if (!cfg.supported()) throw new IOException("Cipher " + cfg.cipher + " not supported");
        RANDOM.nextBytes(localSid);
        sock = new DatagramSocket();
        try { sock.setReceiveBufferSize(4 << 20); sock.setSendBufferSize(1 << 20); } catch (Exception ignored) { }
        if (protector != null) protector.protect(sock);
        sock.connect(new InetSocketAddress(ip, port));
        sock.setSoTimeout(200);
        lastRx = now();
        try {
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(keyManagers(cfg), new TrustManager[]{new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] c, String a) { }
                public void checkServerTrusted(X509Certificate[] c, String a) { }
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            }}, RANDOM);
            engine = tls.createSSLEngine();
            engine.setUseClientMode(true);
            ArrayList<String> wanted = new ArrayList<String>();
            List<String> have = Arrays.asList(engine.getSupportedProtocols());
            for (String v : new String[]{"TLSv1.2", "TLSv1.1", "TLSv1"}) if (have.contains(v)) wanted.add(v);
            if (!wanted.isEmpty()) try { engine.setEnabledProtocols(wanted.toArray(new String[0])); } catch (Exception e) { engine.setEnabledProtocols(new String[]{"TLSv1.2"}); }
        } catch (Exception e) { throw new IOException("TLS: " + e.getMessage()); }

        control(HARD_RESET_CLIENT_V2, new byte[0]);
        long deadline = now() + cfg.waitMs;
        boolean tlsStarted = false, kmSent = false, keys = false, pushed = false;
        long pushAt = 0;
        byte[] clientKm = null;
        while (!pushed) {
            if (now() > deadline) {
                throw new IOException(!tlsStarted ? "No answer over UDP" : !handshakeDone() ? "TLS handshake timed out" : !keys ? "Key exchange timed out" : "Server sent no address");
            }
            retransmit();
            DatagramPacket r = receive();
            if (r != null) packet(r.getData(), r.getLength());
            if (remoteSid != null && !tlsStarted) { tlsStarted = true; engine.beginHandshake(); }
            if (tlsStarted) pumpTls();
            if (tlsStarted && !kmSent && handshakeDone()) {
                clientKm = keyMethod(user, pass);
                appWrite(clientKm);
                kmSent = true;
            }
            if (kmSent && !keys) {
                byte[] a = app.toByteArray();
                int used = serverKm(a, clientKm);
                if (used > 0) { keys = true; app.reset(); app.write(a, used, a.length - used); }
            }
            if (keys && now() - pushAt > 2000) { appWrite("PUSH_REQUEST\0".getBytes("US-ASCII")); pushAt = now(); }
            if (keys) {
                String msg;
                while ((msg = message()) != null) {
                    if (msg.startsWith("AUTH_FAILED")) throw new IOException("Server rejected the sign-in");
                    if (msg.startsWith("PUSH_REPLY")) { push(msg); pushed = true; }
                }
            }
            flushAcks();
        }
        if (localIp == null) throw new IOException("Server gave no address");
        sock.setSoTimeout(0);
        established = true;
    }

    private DatagramPacket receive() throws IOException {
        byte[] b = new byte[65536];
        DatagramPacket r = new DatagramPacket(b, b.length);
        try { sock.receive(r); lastRx = now(); return r; } catch (SocketTimeoutException t) { return null; }
    }


    private void packet(byte[] b, int n) throws IOException {
        if (n < 1) return;
        int op = (b[0] & 255) >> 3;
        if (op == DATA_V1) { data(b, n, 1); return; }
        if (op == 9) { data(b, n, 4); return; }
        if (op != HARD_RESET_SERVER_V2 && op != CONTROL_V1 && op != ACK_V1 && op != SOFT_RESET_V1) return;
        if (n < 10) return;
        if (remoteSid == null) { remoteSid = Arrays.copyOfRange(b, 1, 9); }
        int i = 9, acks = b[i++] & 255;
        for (int k = 0; k < acks && i + 4 <= n; k++, i += 4) { synchronized (sendLock) { unacked.remove(u32(b, i)); } }
        if (acks > 0) i += 8;
        if (op == ACK_V1) return;
        if (i + 4 > n) return;
        int pid = u32(b, i); i += 4;
        synchronized (sendLock) { if (!toAck.contains(pid)) toAck.add(pid); }
        if (op == SOFT_RESET_V1 && established) { finish("Server renewed its keys"); return; }
        if (pid < recvNext || rxPending.containsKey(pid)) return;
        rxPending.put(pid, Arrays.copyOfRange(b, i, n));
        while (rxPending.containsKey(recvNext)) {
            byte[] payload = rxPending.remove(recvNext++);
            if (netIn.remaining() < payload.length) { ByteBuffer bigger = ByteBuffer.allocate(netIn.capacity() * 2 + payload.length); netIn.flip(); bigger.put(netIn); netIn = bigger; }
            netIn.put(payload);
        }
        if (established) { flushAcks(); }
    }

    private void control(int op, byte[] payload) throws IOException {
        Out o;
        synchronized (sendLock) { o = new Out(op, payload); unacked.put(sendPid, o); sendRaw(sendPid++, o); }
    }

    private void sendRaw(int pid, Out o) throws IOException {
        ByteArrayOutputStream p = new ByteArrayOutputStream();
        p.write((o.op << 3) | keyId); p.write(localSid, 0, 8);
        ackField(p);
        p.write(int4(pid), 0, 4);
        p.write(o.payload, 0, o.payload.length);
        byte[] d = p.toByteArray();
        sock.send(new DatagramPacket(d, d.length));
        o.at = now();
    }

    private void ackField(ByteArrayOutputStream p) {
        int n = remoteSid == null ? 0 : Math.min(8, toAck.size());
        p.write(n);
        for (int k = 0; k < n; k++) p.write(int4(toAck.remove(0)), 0, 4);
        if (n > 0) p.write(remoteSid, 0, 8);
    }

    private void flushAcks() throws IOException {
        synchronized (sendLock) {
            while (!toAck.isEmpty() && remoteSid != null) {
                ByteArrayOutputStream p = new ByteArrayOutputStream();
                p.write((ACK_V1 << 3) | keyId); p.write(localSid, 0, 8); ackField(p);
                byte[] d = p.toByteArray(); sock.send(new DatagramPacket(d, d.length));
            }
        }
    }

    private void retransmit() throws IOException {
        synchronized (sendLock) {
            long t = now();
            for (Map.Entry<Integer, Out> e : unacked.entrySet()) if (t - e.getValue().at > 1500) sendRaw(e.getKey(), e.getValue());
        }
    }

    private boolean handshakeDone() {
        SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
        return (hs == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING || hs == SSLEngineResult.HandshakeStatus.FINISHED) && !"NONE".equals(engine.getSession().getProtocol());
    }

    private void pumpTls() throws IOException {
        for (int guard = 0; guard < 64; guard++) {
            SSLEngineResult.HandshakeStatus hs = engine.getHandshakeStatus();
            if (hs == SSLEngineResult.HandshakeStatus.NEED_TASK) { Runnable r; while ((r = engine.getDelegatedTask()) != null) r.run(); continue; }
            if (hs == SSLEngineResult.HandshakeStatus.NEED_WRAP) { wrap(ByteBuffer.allocate(0)); continue; }
            if (netIn.position() > 0 || hs.name().startsWith("NEED_UNWRAP")) { if (unwrap()) continue; }
            break;
        }
    }

    private boolean unwrap() throws IOException {
        netIn.flip();
        ByteBuffer out = ByteBuffer.allocate(Math.max(engine.getSession().getApplicationBufferSize(), 32768));
        SSLEngineResult r;
        try { r = engine.unwrap(netIn, out); } catch (SSLException e) { netIn.compact(); throw new IOException("TLS: " + e.getMessage()); }
        netIn.compact();
        if (out.position() > 0) app.write(out.array(), 0, out.position());
        if (r.getStatus() == SSLEngineResult.Status.CLOSED) throw new IOException("Server closed TLS");
        if (r.getStatus() == SSLEngineResult.Status.BUFFER_UNDERFLOW) return false;
        return r.bytesConsumed() > 0 || r.bytesProduced() > 0;
    }

    private void wrap(ByteBuffer src) throws IOException {
        do {
            ByteBuffer out = ByteBuffer.allocate(Math.max(engine.getSession().getPacketBufferSize(), 32768));
            SSLEngineResult r;
            try { r = engine.wrap(src, out); } catch (SSLException e) { throw new IOException("TLS: " + e.getMessage()); }
            out.flip();
            while (out.hasRemaining()) { byte[] chunk = new byte[Math.min(1100, out.remaining())]; out.get(chunk); control(CONTROL_V1, chunk); }
            if (r.getStatus() == SSLEngineResult.Status.CLOSED) throw new IOException("TLS closed");
            if (r.bytesConsumed() == 0 && r.bytesProduced() == 0) break;
        } while (src.hasRemaining());
    }

    private void appWrite(byte[] data) throws IOException { wrap(ByteBuffer.wrap(data)); }

    private byte[] preMaster, cRandom1, cRandom2;

    private byte[] keyMethod(String user, String pass) throws IOException {
        preMaster = new byte[48]; cRandom1 = new byte[32]; cRandom2 = new byte[32];
        RANDOM.nextBytes(preMaster); RANDOM.nextBytes(cRandom1); RANDOM.nextBytes(cRandom2);
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0); o.write(0); o.write(0); o.write(0); o.write(2);
        o.write(preMaster, 0, 48); o.write(cRandom1, 0, 32); o.write(cRandom2, 0, 32);
        str(o, "V4,dev-type tun,link-mtu 1541,tun-mtu 1500,proto UDPv4" + (cfg.comp ? ",comp-lzo" : "") + ",cipher " + cfg.cipher + ",auth " + cfg.auth + ",keysize " + cfg.keyBytes() * 8 + ",key-method 2,tls-client");
        str(o, user); str(o, pass);
        str(o, "IV_VER=2.5.8\nIV_PLAT=android\nIV_GUI_VER=PixelSuite\n");
        return o.toByteArray();
    }

    private static void str(ByteArrayOutputStream o, String s) throws IOException {
        byte[] b = s.getBytes("UTF-8"); int n = b.length + 1;
        o.write(n >> 8); o.write(n); o.write(b); o.write(0);
    }

    private int serverKm(byte[] a, byte[] clientKm) throws IOException {
        if (a.length < 4 + 1 + 64 + 2) return 0;
        if ((a[4] & 255) != 2) throw new IOException("Unsupported key method");
        int optLen = ((a[69] & 255) << 8) | (a[70] & 255);
        int used = 71 + optLen;
        if (a.length < used) return 0;
        byte[] sRandom1 = Arrays.copyOfRange(a, 5, 37), sRandom2 = Arrays.copyOfRange(a, 37, 69);
        try {
            byte[] master = prf(preMaster, "OpenVPN master secret", cat(cRandom1, sRandom1), 48);
            byte[] keys = prf(master, "OpenVPN key expansion", cat(cRandom2, sRandom2, localSid, remoteSid), 256);
            int kb = cfg.keyBytes(); macLen = cfg.hmacBytes();
            encKey = new SecretKeySpec(Arrays.copyOfRange(keys, 0, kb), "AES");
            decKey = new SecretKeySpec(Arrays.copyOfRange(keys, 128, 128 + kb), "AES");
            encMac = Mac.getInstance(cfg.hmac()); encMac.init(new SecretKeySpec(Arrays.copyOfRange(keys, 64, 64 + macLen), cfg.hmac()));
            decMac = Mac.getInstance(cfg.hmac()); decMac.init(new SecretKeySpec(Arrays.copyOfRange(keys, 192, 192 + macLen), cfg.hmac()));
            encrypt = Cipher.getInstance("AES/CBC/PKCS5Padding"); decrypt = Cipher.getInstance("AES/CBC/PKCS5Padding");
        } catch (Exception e) { throw new IOException("Keys: " + e.getMessage()); }
        Arrays.fill(preMaster, (byte) 0);
        return used;
    }

    private String message() {
        byte[] a = app.toByteArray();
        for (int i = 0; i < a.length; i++) if (a[i] == 0) {
            String m = new String(a, 0, i);
            app.reset(); app.write(a, i + 1, a.length - i - 1);
            return m;
        }
        return null;
    }

    private void push(String msg) {
        ArrayList<String> d = new ArrayList<String>();
        for (String o : msg.split(",")) {
            o = o.trim(); String[] w = o.split("\\s+");
            if (w[0].equals("ifconfig") && w.length > 1) localIp = w[1];
            else if (w[0].equals("dhcp-option") && w.length > 2 && w[1].equalsIgnoreCase("DNS")) d.add(w[2]);
            else if (w[0].equals("ping") && w.length > 1) try { pingEvery = Math.max(1000, Math.min(10000, Integer.parseInt(w[1]) * 1000L)); } catch (NumberFormatException ignored) { }
            else if (w[0].startsWith("comp-lzo")) cfg.comp = true;
        }
        dns = d.toArray(new String[0]);
    }

    void start(Sink s) {
        sink = s;
        Thread reader = new Thread(new Runnable() { public void run() {
            String why = "Connection closed";
            try {
                byte[] b = new byte[65536];
                while (!closed) {
                    DatagramPacket r = new DatagramPacket(b, b.length);
                    sock.receive(r); lastRx = now();
                    packet(b, r.getLength());
                }
            } catch (IOException e) { if (e.getMessage() != null) why = e.getMessage(); }
            finish(why);
        }}, "PixelSuite-OpenVPN");
        reader.setDaemon(true); reader.start();
        Thread ping = new Thread(new Runnable() { public void run() {
            try { while (!closed) { Thread.sleep(pingEvery); if (!closed) sendData(PING, 0, PING.length); } } catch (Exception ignored) { }
        }}, "PixelSuite-OpenVPN-ping");
        ping.setDaemon(true); ping.start();
    }

    private void data(byte[] b, int n, int h) {
        if (decrypt == null || n < h + macLen + 16 + 16) return;
        try {
            decMac.update(b, h + macLen, n - h - macLen);
            byte[] want = decMac.doFinal();
            if (!MessageDigest.isEqual(want, Arrays.copyOfRange(b, h, h + macLen))) return;
            decrypt.init(Cipher.DECRYPT_MODE, decKey, new IvParameterSpec(b, h + macLen, 16));
            byte[] plain = decrypt.doFinal(b, h + macLen + 16, n - h - macLen - 16);
            int off = 4, len = plain.length - 4;
            if (cfg.comp) { if (len < 1 || (plain[off] & 255) != 0xFA) return; off++; len--; }
            if (len == PING.length && Arrays.equals(Arrays.copyOfRange(plain, off, off + len), PING)) return;
            Sink s = sink;
            if (s != null && len > 0) s.ip(plain, off, len);
        } catch (Exception ignored) { }
    }

    void sendIp(byte[] buf, int len) throws IOException { sendData(buf, 8, len); }

    private void sendData(byte[] d, int off, int len) throws IOException {
        if (closed) throw new IOException("Closed");
        try {
            byte[] packet;
            synchronized (encrypt) {
                byte[] plain = new byte[4 + (cfg.comp ? 1 : 0) + len];
                put32(plain, 0, dataPid++);
                int at = 4; if (cfg.comp) plain[at++] = (byte) 0xFA;
                System.arraycopy(d, off, plain, at, len);
                byte[] iv = new byte[16]; RANDOM.nextBytes(iv);
                encrypt.init(Cipher.ENCRYPT_MODE, encKey, new IvParameterSpec(iv));
                byte[] ct = encrypt.doFinal(plain);
                encMac.update(iv); encMac.update(ct);
                byte[] mac = encMac.doFinal();
                packet = new byte[1 + macLen + 16 + ct.length];
                packet[0] = (byte) ((DATA_V1 << 3) | keyId);
                System.arraycopy(mac, 0, packet, 1, macLen);
                System.arraycopy(iv, 0, packet, 1 + macLen, 16);
                System.arraycopy(ct, 0, packet, 1 + macLen + 16, ct.length);
            }
            sock.send(new DatagramPacket(packet, packet.length));
        } catch (IOException e) { throw e; } catch (Exception e) { throw new IOException("Encrypt: " + e.getMessage()); }
    }

    void echo() { }
    boolean closed() { return closed; }

    private void finish(String why) {
        if (closed) return;
        closed = true;
        try { if (sock != null) sock.close(); } catch (Exception ignored) { }
        Sink s = sink; if (s != null) s.closed(why);
    }

    void close() { sink = null; finish("Disconnected"); }

    private static KeyManager[] keyManagers(Config c) {
        if (c.cert == null || c.key == null) return null;
        try {
            Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(new java.io.ByteArrayInputStream(c.cert.trim().getBytes("US-ASCII")));
            boolean pkcs1 = c.key.contains("BEGIN RSA PRIVATE KEY");
            String body = c.key.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
            byte[] der = Base64.decode(body, Base64.DEFAULT);
            if (pkcs1) der = pkcs8(der);
            PrivateKey key = KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
            KeyStore ks = KeyStore.getInstance("PKCS12"); ks.load(null, null);
            ks.setKeyEntry("client", key, new char[0], new Certificate[]{cert});
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, new char[0]);
            return kmf.getKeyManagers();
        } catch (Exception e) { return null; }
    }

    private static byte[] pkcs8(byte[] pkcs1) {
        byte[] alg = {0x30, 0x0D, 0x06, 0x09, 0x2A, (byte) 0x86, 0x48, (byte) 0x86, (byte) 0xF7, 0x0D, 0x01, 0x01, 0x01, 0x05, 0x00};
        byte[] octet = der(0x04, pkcs1);
        byte[] body = cat(new byte[]{0x02, 0x01, 0x00}, alg, octet);
        return der(0x30, body);
    }
    private static byte[] der(int tag, byte[] v) {
        ByteArrayOutputStream o = new ByteArrayOutputStream(); o.write(tag);
        int n = v.length;
        if (n < 128) o.write(n); else if (n < 256) { o.write(0x81); o.write(n); } else if (n < 65536) { o.write(0x82); o.write(n >> 8); o.write(n); } else { o.write(0x83); o.write(n >> 16); o.write(n >> 8); o.write(n); }
        o.write(v, 0, v.length); return o.toByteArray();
    }

    static byte[] prf(byte[] secret, String label, byte[] seed, int n) throws Exception {
        byte[] ls = cat(label.getBytes("US-ASCII"), seed);
        int half = (secret.length + 1) / 2;
        byte[] s1 = Arrays.copyOfRange(secret, 0, half), s2 = Arrays.copyOfRange(secret, secret.length - half, secret.length);
        byte[] a = pHash("HmacMD5", s1, ls, n), b = pHash("HmacSHA1", s2, ls, n);
        for (int i = 0; i < n; i++) a[i] ^= b[i];
        return a;
    }
    private static byte[] pHash(String alg, byte[] secret, byte[] seed, int n) throws Exception {
        Mac m = Mac.getInstance(alg); m.init(new SecretKeySpec(secret, alg));
        byte[] out = new byte[n], a = seed; int at = 0;
        while (at < n) { a = m.doFinal(a); m.update(a); byte[] h = m.doFinal(seed); int k = Math.min(h.length, n - at); System.arraycopy(h, 0, out, at, k); at += k; }
        return out;
    }

    private static byte[] cat(byte[]... parts) { int n = 0; for (byte[] p : parts) n += p.length; byte[] r = new byte[n]; int at = 0; for (byte[] p : parts) { System.arraycopy(p, 0, r, at, p.length); at += p.length; } return r; }
    private static byte[] int4(int v) { return new byte[]{(byte) (v >>> 24), (byte) (v >>> 16), (byte) (v >>> 8), (byte) v}; }
    private static void put32(byte[] b, int i, int v) { b[i] = (byte) (v >>> 24); b[i + 1] = (byte) (v >>> 16); b[i + 2] = (byte) (v >>> 8); b[i + 3] = (byte) v; }
    private static int u32(byte[] b, int i) { return ((b[i] & 255) << 24) | ((b[i + 1] & 255) << 16) | ((b[i + 2] & 255) << 8) | (b[i + 3] & 255); }
}
