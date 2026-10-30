package io.github.pixelsuite;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WatchCell {
    private WatchCell() {}

    static final String ACTION_STATE = "io.github.pixelsuite.action.DNA_STATE";
    static final UUID ID = UUID.fromString("6f3a2c1e-8d4b-4f7a-9c2e-5b1d0a7e3f91");
    private static final int JOB = 0x50535743;
    private static final String CHANNEL = "pixelsuite_dna_watch";
    private static final int NOTE = 0x5053574e;

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("pixelsuite_dna_watch", Context.MODE_PRIVATE); }

    static void want(Context c, boolean off) {
        SharedPreferences p = prefs(c);
        String w = off ? "off" : "on";
        if (!w.equals(p.getString("want", null))) p.edit().putString("want", w).putBoolean("done", false).apply();
        if (!p.getBoolean("done", false)) schedule(c);
    }

    static void schedule(Context c) {
        try {
            JobScheduler js = (JobScheduler) c.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            js.schedule(new JobInfo.Builder(JOB, new ComponentName(c, Job.class)).setOverrideDeadline(0).build());
        } catch (Throwable ignored) { }
    }

    public static final class Receiver extends android.content.BroadcastReceiver {
        @Override public void onReceive(Context c, Intent i) {
            if (!BluetoothDevice.ACTION_ACL_CONNECTED.equals(i.getAction())) return;
            SharedPreferences p = prefs(c);
            if (p.getString("want", null) != null && !p.getBoolean("done", false)) schedule(c);
        }
    }

    public static final class Job extends JobService {
        @Override public boolean onStartJob(final JobParameters params) {
            new Thread(() -> {
                try { run(getApplicationContext()); } catch (Throwable ignored) { }
                jobFinished(params, false);
            }, "pixelsuite-watch-cell").start();
            return true;
        }
        @Override public boolean onStopJob(JobParameters params) { return false; }
    }

    static void run(Context c) {
        SharedPreferences p = prefs(c);
        String want = p.getString("want", null);
        if (want == null || p.getBoolean("done", false)) return;
        if (c.checkSelfPermission("android.permission.BLUETOOTH_CONNECT") != PackageManager.PERMISSION_GRANTED) {
            note(c, "Allow Nearby devices for Pixel Suite so Do Not Annoy can switch your watch's mobile network.", true);
            return;
        }
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null || !a.isEnabled()) { note(c, failText(want), false); return; }
        for (int round = 0; round < 3; round++) {
            if (!want.equals(p.getString("want", null))) return;
            for (BluetoothDevice d : watches(a, p.getString("addr", null))) {
                String reply = send(a, d, want);
                if (reply != null && reply.startsWith("OK")) {
                    if (want.equals(p.getString("want", null))) p.edit().putBoolean("done", true).putString("addr", d.getAddress()).apply();
                    cancelNote(c);
                    return;
                }
                if (reply != null && reply.startsWith("ERR permission")) {
                    note(c, "Pixel Suite on your watch needs its one-time permission (the adb command) to switch the mobile network.", false);
                    return;
                }
            }
            try { Thread.sleep(4000); } catch (InterruptedException ignored) { }
        }
        note(c, failText(want), false);
    }

    private static String failText(String want) {
        return "Couldn't reach your watch. Its mobile network will be turned " + want + " when it reconnects.";
    }

    private static List<BluetoothDevice> watches(BluetoothAdapter a, String last) {
        List<BluetoothDevice> out = new ArrayList<>();
        try {
            for (BluetoothDevice d : a.getBondedDevices()) {
                boolean wearable = false;
                try { BluetoothClass k = d.getBluetoothClass(); wearable = k != null && k.getMajorDeviceClass() == BluetoothClass.Device.Major.WEARABLE; } catch (Throwable ignored) { }
                String name = d.getName();
                if (!wearable && (name == null || !name.toLowerCase(java.util.Locale.ROOT).contains("watch"))) continue;
                if (d.getAddress().equals(last)) out.add(0, d); else out.add(d);
            }
        } catch (Throwable ignored) { }
        return out;
    }

    private static String send(BluetoothAdapter a, BluetoothDevice d, String want) {
        BluetoothSocket s = null;
        try {
            try { a.cancelDiscovery(); } catch (Throwable ignored) { }
            s = d.createRfcommSocketToServiceRecord(ID);
            s.connect();
            OutputStream out = s.getOutputStream();
            out.write(("PSDNA1 " + want + "\n").getBytes("UTF-8"));
            out.flush();
            InputStream in = s.getInputStream();
            StringBuilder line = new StringBuilder();
            int ch;
            while (line.length() < 64 && (ch = in.read()) >= 0 && ch != '\n') line.append((char) ch);
            return line.toString().trim();
        } catch (Throwable t) {
            return null;
        } finally {
            try { if (s != null) s.close(); } catch (Throwable ignored) { }
        }
    }

    private static void note(Context c, String text, boolean openSettings) {
        try {
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            Class<?> chType = Class.forName("android.app.NotificationChannel");
            if (NotificationManager.class.getMethod("getNotificationChannel", String.class).invoke(nm, CHANNEL) == null) {
                Object ch = chType.getConstructor(String.class, CharSequence.class, int.class).newInstance(CHANNEL, "Do Not Annoy: watch", 2 );
                NotificationManager.class.getMethod("createNotificationChannel", chType).invoke(nm, ch);
            }
            Notification.Builder b = new Notification.Builder(c);
            Notification.Builder.class.getMethod("setChannelId", String.class).invoke(b, CHANNEL);
            int small = c.getResources().getIdentifier("ic_ps_do_not_annoy", "drawable", c.getPackageName());
            b.setSmallIcon(small != 0 ? small : c.getApplicationInfo().icon)
                    .setContentTitle("Do Not Annoy")
                    .setContentText(text)
                    .setStyle(new Notification.BigTextStyle().bigText(text))
                    .setSubText("Watch")
                    .setAutoCancel(true);
            if (openSettings) {
                Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS", Uri.fromParts("package", c.getPackageName(), null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                b.setContentIntent(PendingIntent.getActivity(c, NOTE, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            }
            nm.notify("pixelsuite_dna_watch", NOTE, b.build());
        } catch (Throwable ignored) { }
    }

    private static void cancelNote(Context c) {
        try { ((NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE)).cancel("pixelsuite_dna_watch", NOTE); } catch (Throwable ignored) { }
    }
}
