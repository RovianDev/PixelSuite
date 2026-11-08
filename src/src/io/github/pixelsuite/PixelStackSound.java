package io.github.pixelsuite;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

final class PixelStackSound {
    private PixelStackSound() { }

    private static final int RATE = 44100;
    private static SoundPool sPool;
    private static final Map<String, Integer> sIds = new HashMap<String, Integer>();
    private static final Map<Integer, Boolean> sReady = new HashMap<Integer, Boolean>();
    private static Context sApp;
    private static volatile boolean sPreparing;

    static final String RISE = "rise", POP = "pop", BOING = "boing", THUD = "thud", DROP = "drop",
            PERFECT = "perfect", CHIP = "chip", OVER = "over", SPARKLE = "sparkle", SUCCESS = "success", FAIL = "fail",
            BOOP = "boop", SEND = "send", SOFT = "soft";

    static void prepare(Context c) {
        if (sPool != null || sPreparing) return;
        sPreparing = true;
        sApp = c.getApplicationContext();
        new Thread(() -> {
            try {
                SoundPool pool = new SoundPool.Builder().setMaxStreams(10)
                        .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()).build();
                pool.setOnLoadCompleteListener((p, id, status) -> { synchronized (sReady) { sReady.put(id, status == 0); } });
                File dir = new File(sApp.getCacheDir(), "pixelstack-sounds-4");
                for (String was : new String[]{"pixelstack-sounds-1", "pixelstack-sounds-2", "pixelstack-sounds-3"}) { File old = new File(sApp.getCacheDir(), was); File[] gone = old.listFiles(); if (gone != null) for (File g : gone) g.delete(); old.delete(); }
                dir.mkdirs();
                for (String name : new String[]{RISE, POP, BOING, THUD, DROP, PERFECT, CHIP, OVER, SPARKLE, SUCCESS, FAIL, BOOP, SEND, SOFT}) {
                    File f = new File(dir, name + ".wav");
                    if (!f.exists() || f.length() < 100) wav(f, make(name));
                    int id = pool.load(f.getAbsolutePath(), 1);
                    synchronized (sIds) { sIds.put(name, id); }
                }
                sPool = pool;
            } catch (Throwable t) { android.util.Log.w("PixelStack", "sounds: " + t); }
            sPreparing = false;
        }, "PixelStackSound").start();
    }

    private static boolean allowed() {
        try { return ((AudioManager) sApp.getSystemService(Context.AUDIO_SERVICE)).getRingerMode() == AudioManager.RINGER_MODE_NORMAL; } catch (Throwable t) { return false; }
    }

    static int play(String name, float volume, float rate) {
        try {
            SoundPool p = sPool;
            if (p == null || sApp == null || !allowed()) return 0;
            if (!POP.equals(name)) volume *= .9f;
            if (!POP.equals(name) && !RISE.equals(name) && !BOOP.equals(name)) volume *= .95f;
            if (DROP.equals(name) || CHIP.equals(name) || PERFECT.equals(name)) volume *= .95f;
            Integer id; synchronized (sIds) { id = sIds.get(name); }
            if (id == null) return 0;
            Boolean ok; synchronized (sReady) { ok = sReady.get(id); }
            if (ok == null || !ok) return 0;
            float v = Math.max(0f, Math.min(1f, volume));
            return p.play(id, v, v, 1, 0, Math.max(.5f, Math.min(2f, rate)));
        } catch (Throwable t) { return 0; }
    }
    static int play(String name) { return play(name, .7f, 1f); }
    static void stop(int stream) { try { if (sPool != null && stream != 0) sPool.stop(stream); } catch (Throwable ignored) { } }

    private static float[] buf(float seconds) { return new float[Math.round(seconds * RATE)]; }

    private static void tone(float[] b, float start, float dur, float f0, float f1, int shape, float amp, float attack, float decayRate) {
        int s0 = Math.round(start * RATE), n = Math.round(dur * RATE);
        double ph = 0;
        for (int i = 0; i < n && s0 + i < b.length; i++) {
            float t = i / (float) RATE, k = i / (float) n;
            double f = f0 * Math.pow(f1 / f0, k);
            ph += 2 * Math.PI * f / RATE;
            double w = shape == 0 ? Math.sin(ph) : (2 / Math.PI) * Math.asin(Math.sin(ph));
            float env = Math.min(1f, t / Math.max(.0005f, attack)) * (float) Math.exp(-decayRate * t);
            float tail = Math.min(1f, (n - i) / (RATE * .008f));
            b[s0 + i] += (float) (w * amp * env * tail);
        }
    }
    private static void noise(float[] b, float start, float dur, float c0, float c1, float amp, boolean bell, float decayRate, long seed) {
        Random r = new Random(seed);
        int s0 = Math.round(start * RATE), n = Math.round(dur * RATE);
        float y = 0, y2 = 0;
        for (int i = 0; i < n && s0 + i < b.length; i++) {
            float k = i / (float) n, a = c0 + (c1 - c0) * k;
            float x = r.nextFloat() * 2 - 1;
            y += a * (x - y); y2 += a * (y - y2);
            float env = bell ? (float) Math.sin(Math.PI * k) : (float) Math.exp(-decayRate * i / (float) RATE);
            b[s0 + i] += y2 * amp * env * 2.2f;
        }
    }

    private static float[] make(String name) {
        float[] b;
        switch (name) {
            case RISE:
                b = buf(.86f);
                for (int i = 0; i < b.length; i++) {
                    float t = i / (float) RATE, k = t / .86f;
                    double f = 180 * Math.pow(4.2, k), trem = 1 - .35 * k * (.5 + .5 * Math.sin(2 * Math.PI * (6 + 14 * k) * t));
                    double v = Math.sin(2 * Math.PI * f * t) * .6 + Math.sin(2 * Math.PI * f * 2.01 * t) * .18 * k + Math.sin(2 * Math.PI * f * 3.02 * t) * .07 * k;
                    float env = Math.min(1f, t / .08f) * (.35f + .65f * k);
                    b[i] = (float) (v * trem * env * .5);
                }
                noise(b, 0, .86f, .02f, .25f, .05f, true, 0, 3);
                break;
            case POP:
                b = buf(.2f);
                tone(b, 0, .16f, 360, 760, 0, .55f, .004f, 20);
                tone(b, .012f, .12f, 720, 1100, 0, .07f, .006f, 26);
                break;
            case BOING:
                b = buf(.42f);
                float[] up = {523.25f, 659.25f, 783.99f};
                for (int i = 0; i < 3; i++) {
                    tone(b, i * .06f, .3f, up[i], up[i] * 1.004f, 0, .24f, .008f, 11);
                    tone(b, i * .06f, .2f, up[i] * 2, up[i] * 2, 0, .024f, .008f, 16);
                }
                break;
            case THUD:
                b = buf(.24f);
                tone(b, 0, .22f, 170, 80, 0, .5f, .005f, 16);
                noise(b, 0, .03f, .12f, .04f, .06f, false, 90, 13);
                break;
            case DROP:
                b = buf(.3f);
                tone(b, 0, .28f, 523, 520, 1, .45f, .002f, 14);
                tone(b, 0, .2f, 1046, 1044, 0, .18f, .002f, 22);
                tone(b, 0, .05f, 160, 90, 0, .4f, .001f, 60);
                break;
            case PERFECT:
                b = buf(.6f);
                tone(b, 0, .5f, 1046, 1046, 0, .38f, .002f, 7);
                tone(b, 0, .5f, 2093, 2093, 0, .1f, .002f, 10);
                tone(b, .07f, .52f, 1568, 1568, 0, .36f, .002f, 6);
                tone(b, .07f, .5f, 3136, 3136, 0, .08f, .002f, 10);
                break;
            case CHIP:
                b = buf(.08f);
                noise(b, 0, .06f, .8f, .4f, .4f, false, 70, 17);
                tone(b, 0, .05f, 1900, 1200, 0, .2f, .001f, 60);
                break;
            case OVER:
                b = buf(1.05f);
                tone(b, 0, .26f, 392, 388, 1, .4f, .004f, 5);
                tone(b, .24f, .26f, 330, 326, 1, .4f, .004f, 5);
                tone(b, .48f, .55f, 262, 200, 1, .42f, .004f, 3.2f);
                break;
            case SPARKLE:
                b = buf(.16f);
                tone(b, 0, .15f, 2093, 2093, 0, .3f, .001f, 30);
                tone(b, 0, .1f, 4186, 4186, 0, .06f, .001f, 45);
                break;
            case SUCCESS:
                b = buf(.72f);
                float[] notes = {523, 659, 784, 1046};
                for (int i = 0; i < 4; i++) tone(b, i * .07f, .5f, notes[i], notes[i], 0, .3f, .002f, 6);
                break;
            case FAIL:
                b = buf(.32f);
                tone(b, 0, .14f, 330, 300, 1, .35f, .003f, 10);
                tone(b, .13f, .18f, 262, 220, 1, .35f, .003f, 9);
                break;
            case BOOP:
                b = buf(.16f);
                tone(b, 0, .14f, 520, 900, 0, .45f, .002f, 16);
                break;
            case SOFT:
                b = buf(.22f);
                tone(b, 0, .2f, 620, 700, 0, .32f, .018f, 12);
                tone(b, 0, .16f, 1240, 1400, 0, .05f, .02f, 16);
                break;
            case SEND: default:
                b = buf(.3f);
                tone(b, 0, .28f, 560, 1120, 0, .3f, .02f, 7);
                tone(b, 0, .22f, 1120, 2240, 0, .05f, .02f, 10);
        }
        float peak = .001f; for (float v : b) peak = Math.max(peak, Math.abs(v));
        if (peak > .95f) for (int i = 0; i < b.length; i++) b[i] = b[i] / peak * .95f;
        return b;
    }

    private static void wav(File f, float[] s) throws Exception {
        int n = s.length, bytes = n * 2;
        byte[] out = new byte[44 + bytes];
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.wrap(out).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        bb.put("RIFF".getBytes("US-ASCII")).putInt(36 + bytes).put("WAVE".getBytes("US-ASCII"));
        bb.put("fmt ".getBytes("US-ASCII")).putInt(16).putShort((short) 1).putShort((short) 1).putInt(RATE).putInt(RATE * 2).putShort((short) 2).putShort((short) 16);
        bb.put("data".getBytes("US-ASCII")).putInt(bytes);
        for (float v : s) bb.putShort((short) Math.round(Math.max(-1f, Math.min(1f, v)) * 32767));
        FileOutputStream o = new FileOutputStream(f); o.write(out); o.close();
    }
}
