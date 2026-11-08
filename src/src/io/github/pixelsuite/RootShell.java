package io.github.pixelsuite;

import android.os.SystemClock;

import java.io.InputStream;
import java.io.OutputStream;

final class RootShell {
    private RootShell() { }
    private static Process sProc;
    private static OutputStream sIn;
    private static InputStream sOut;

    static synchronized boolean warm() {
        try { if (!alive()) start(); return alive(); } catch (Throwable t) { stop(); return false; }
    }

    static synchronized boolean run(String command, long timeoutMs) {
        try {
            if (!alive()) start();
            String mark = "__pixelsuite_" + System.nanoTime();
            sIn.write((command + " ; echo " + mark + " $?\n").getBytes("UTF-8"));
            sIn.flush();
            StringBuilder got = new StringBuilder();
            byte[] buf = new byte[512];
            long end = SystemClock.uptimeMillis() + timeoutMs;
            while (SystemClock.uptimeMillis() < end) {
                int n = sOut.available();
                if (n > 0) {
                    int r = sOut.read(buf, 0, Math.min(n, buf.length));
                    if (r > 0) got.append(new String(buf, 0, r, "UTF-8"));
                    int at = got.indexOf(mark);
                    if (at >= 0) {
                        int nl = got.indexOf("\n", at);
                        if (nl >= 0) return got.substring(at + mark.length(), nl).trim().equals("0");
                    }
                    continue;
                }
                if (!alive()) break;
                Thread.sleep(1);
            }
        } catch (Throwable ignored) { }
        stop();
        return false;
    }

    private static boolean alive() {
        if (sProc == null) return false;
        try { sProc.exitValue(); return false; } catch (IllegalThreadStateException running) { return true; }
    }

    private static void start() throws Exception {
        stop();
        ProcessBuilder b = new ProcessBuilder("su");
        b.redirectErrorStream(true);
        sProc = b.start();
        sIn = sProc.getOutputStream();
        sOut = sProc.getInputStream();
    }

    private static void stop() {
        try { if (sProc != null) sProc.destroy(); } catch (Throwable ignored) { }
        sProc = null; sIn = null; sOut = null;
    }
}
