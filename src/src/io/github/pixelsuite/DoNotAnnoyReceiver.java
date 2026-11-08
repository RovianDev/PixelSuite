package io.github.pixelsuite;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CallLog;
import android.widget.RemoteViews;

public final class DoNotAnnoyReceiver extends BroadcastReceiver {
    static final String CHANNEL = "pixelsuite_dna";

    @Override
    public void onReceive(Context c, Intent in) {
        if (WatchCell.ACTION_STATE.equals(in.getAction())) { WatchCell.want(c, in.getBooleanExtra("watch_off", false)); return; }
        if (!"io.github.pixelsuite.action.DNA_DEFLECTED".equals(in.getAction())) return;
        try {
            String number = in.getStringExtra("number");
            String title = in.getStringExtra("title");
            long when = in.getLongExtra("time", System.currentTimeMillis());
            if (number == null) number = "";
            if (title == null || title.isEmpty()) title = number.isEmpty() ? "Unknown caller" : number;
            String body = in.getStringExtra("body");
            if (body == null || body.isEmpty()) body = "Incoming call deflected (device reported as switched off)";

            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null || !(Boolean) NotificationManager.class.getMethod("areNotificationsEnabled").invoke(nm)) { setResultCode(0); return; }
            Class<?> chType = Class.forName("android.app.NotificationChannel");
            if (NotificationManager.class.getMethod("getNotificationChannel", String.class).invoke(nm, CHANNEL) == null) {
                Object ch = chType.getConstructor(String.class, CharSequence.class, int.class).newInstance(CHANNEL, "Do Not Annoy", 4 );
                chType.getMethod("setDescription", String.class).invoke(ch, "Calls deflected by Do Not Annoy");
                NotificationManager.class.getMethod("createNotificationChannel", chType).invoke(nm, ch);
            }
            int id = (int) (when & 0x7fffffff);

            RemoteViews v = view(c, title, body, number, id, true);
            Notification.Builder b = new Notification.Builder(c);
            Notification.Builder.class.getMethod("setChannelId", String.class).invoke(b, CHANNEL);
            int small = c.getResources().getIdentifier("ic_ps_do_not_annoy", "drawable", c.getPackageName());
            if (small == 0) small = c.getApplicationInfo().icon;
            b.setSmallIcon(small)
                    .setContentTitle(I18n.cs(title))
                    .setContentText(I18n.cs(body))
                    .setSubText(I18n.cs("Do Not Annoy"))
                    .setCategory("missed_call")
                    .setWhen(when)
                    .setShowWhen(true)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH)
                    .setContentIntent(PendingIntent.getActivity(c, id,
                            new Intent(Intent.ACTION_VIEW, CallLog.Calls.CONTENT_URI).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            Object style = Class.forName("android.app.Notification$DecoratedCustomViewStyle").newInstance();
            Notification.Builder.class.getMethod("setStyle", Notification.Style.class).invoke(b, style);
            Notification.Builder.class.getMethod("setCustomContentView", RemoteViews.class).invoke(b, v);
            Notification.Builder.class.getMethod("setCustomBigContentView", RemoteViews.class).invoke(b, view(c, title, body, number, id, false));
            Notification.Builder.class.getMethod("setCustomHeadsUpContentView", RemoteViews.class).invoke(b, view(c, title, body, number, id, true));
            wearCall(c, b, number, id);
            nm.notify("pixelsuite_dna", id, b.build());
            setResultCode(1);
        } catch (Throwable t) {
            setResultCode(0);
        }
    }

    static void wearCall(Context c, Notification.Builder b, String number, int id) {
        if (number == null || number.isEmpty()) return;
        try {
            int icon = c.getResources().getIdentifier("ps_dna_call_wear", "drawable", c.getPackageName());
            Notification.Action action = new Notification.Action.Builder(icon != 0 ? icon : android.R.drawable.sym_action_call, "Call", callIntent(c, number, id)).build();
            b.extend(new Notification.WearableExtender().addAction(action));
        } catch (Throwable ignored) { }
    }

    static PendingIntent callIntent(Context c, String number, int id) {
        boolean direct = c.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        return PendingIntent.getActivity(c, id + 1,
                new Intent(direct ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static RemoteViews view(Context c, String title, String body, String number, int id, boolean compact) {
        String pkg = c.getPackageName();
        android.content.res.Resources r = c.getResources();
        RemoteViews v = new RemoteViews(pkg, r.getIdentifier(compact ? "ps_dna_notification_small" : "ps_dna_notification", "layout", pkg));
        int titleId = r.getIdentifier("ps_dna_title", "id", pkg), textId = r.getIdentifier("ps_dna_text", "id", pkg), callId = r.getIdentifier("ps_dna_call", "id", pkg);
        v.setTextViewText(titleId, title);
        v.setTextViewText(textId, body);
        if (number.isEmpty()) {
            v.setViewVisibility(callId, android.view.View.GONE);
        } else {
            v.setOnClickPendingIntent(callId, callIntent(c, number, id));
        }
        return v;
    }
}
