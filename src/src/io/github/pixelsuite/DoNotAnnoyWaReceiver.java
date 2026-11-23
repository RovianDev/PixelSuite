package io.github.pixelsuite;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.RemoteViews;

public final class DoNotAnnoyWaReceiver extends BroadcastReceiver {
    static final String BODY = "WhatsApp call turned away by Do Not Annoy";

    @Override
    public void onReceive(Context c, Intent in) {
        if (!DoNotAnnoyWhatsApp.ACTION.equals(in.getAction())) return;
        if (!fromWhatsApp()) return;
        try {
            String title = in.getStringExtra("title"), number = in.getStringExtra("number"), mime = in.getStringExtra("mime"), app = in.getStringExtra("app");
            long row = in.getLongExtra("row", -1), when = in.getLongExtra("time", System.currentTimeMillis());
            if (number == null) number = "";
            if (app == null || !DoNotAnnoyWhatsApp.owns(app)) app = DoNotAnnoyWhatsApp.APPS[0];
            if (title == null || title.isEmpty()) title = "WhatsApp";
            NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Class<?> chType = Class.forName("android.app.NotificationChannel");
            if (NotificationManager.class.getMethod("getNotificationChannel", String.class).invoke(nm, DoNotAnnoyReceiver.CHANNEL) == null) {
                Object ch = chType.getConstructor(String.class, CharSequence.class, int.class).newInstance(DoNotAnnoyReceiver.CHANNEL, "Do Not Annoy", 4);
                chType.getMethod("setDescription", String.class).invoke(ch, "Calls deflected by Do Not Annoy");
                NotificationManager.class.getMethod("createNotificationChannel", chType).invoke(nm, ch);
            }
            int id = (int) (when & 0x7fffffff);
            PendingIntent call = callBack(c, app, row, mime, number, id);
            Intent open = c.getPackageManager().getLaunchIntentForPackage(app);
            Notification.Builder b = new Notification.Builder(c);
            Notification.Builder.class.getMethod("setChannelId", String.class).invoke(b, DoNotAnnoyReceiver.CHANNEL);
            int small = c.getResources().getIdentifier("ic_ps_dna_wa", "drawable", c.getPackageName());
            if (small == 0) small = c.getApplicationInfo().icon;
            b.setSmallIcon(small)
                    .setContentTitle(I18n.cs(title))
                    .setContentText(I18n.cs(BODY))
                    .setSubText(I18n.cs("Do Not Annoy"))
                    .setCategory("missed_call")
                    .setWhen(when)
                    .setShowWhen(true)
                    .setAutoCancel(true)
                    .setPriority(Notification.PRIORITY_HIGH);
            if (open != null) b.setContentIntent(PendingIntent.getActivity(c, id, open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            Object style = Class.forName("android.app.Notification$DecoratedCustomViewStyle").newInstance();
            Notification.Builder.class.getMethod("setStyle", Notification.Style.class).invoke(b, style);
            Notification.Builder.class.getMethod("setCustomContentView", RemoteViews.class).invoke(b, view(c, title, call, true));
            Notification.Builder.class.getMethod("setCustomBigContentView", RemoteViews.class).invoke(b, view(c, title, call, false));
            Notification.Builder.class.getMethod("setCustomHeadsUpContentView", RemoteViews.class).invoke(b, view(c, title, call, true));
            if (call != null) try {
                int icon = c.getResources().getIdentifier("ps_dna_call_wear", "drawable", c.getPackageName());
                b.extend(new Notification.WearableExtender().addAction(new Notification.Action.Builder(icon != 0 ? icon : android.R.drawable.sym_action_call, I18n.cs("Call"), call).build()));
            } catch (Throwable ignored) { }
            nm.notify("pixelsuite_dna_wa", id, b.build());
        } catch (Throwable t) { android.util.Log.w("PixelSuite", "dna whatsapp: notification", t); }
    }

    private boolean fromWhatsApp() {
        if (android.os.Build.VERSION.SDK_INT < 34) return true;
        try {
            Object pkg = BroadcastReceiver.class.getMethod("getSentFromPackage").invoke(this);
            return pkg != null && DoNotAnnoyWhatsApp.owns(pkg.toString());
        } catch (Throwable t) { return false; }
    }

    private static PendingIntent callBack(Context c, String app, long row, String mime, String number, int id) {
        Intent i = null;
        if (row > 0 && mime != null) i = new Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://com.android.contacts/data/" + row), mime).setPackage(app);
        else if (number.length() > 0) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + number)).setPackage(app);
        if (i == null) i = c.getPackageManager().getLaunchIntentForPackage(app);
        if (i == null) return null;
        return PendingIntent.getActivity(c, id + 1, i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static RemoteViews view(Context c, String title, PendingIntent call, boolean compact) {
        String pkg = c.getPackageName(); android.content.res.Resources r = c.getResources();
        RemoteViews v = new RemoteViews(pkg, r.getIdentifier(compact ? "ps_dna_wa_notification_small" : "ps_dna_wa_notification", "layout", pkg));
        int titleId = r.getIdentifier("ps_dna_wa_title", "id", pkg), textId = r.getIdentifier("ps_dna_wa_text", "id", pkg), callId = r.getIdentifier("ps_dna_wa_call", "id", pkg);
        v.setTextViewText(titleId, title);
        v.setTextViewText(textId, I18n.cs(BODY));
        v.setTextViewText(callId, I18n.cs("Call"));
        if (call == null) v.setViewVisibility(callId, android.view.View.GONE); else v.setOnClickPendingIntent(callId, call);
        return v;
    }
}
