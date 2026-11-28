package io.github.pixelsuite;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

public class SearchWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) {
        try {
            RemoteViews v = new RemoteViews(c.getPackageName(), c.getResources().getIdentifier("ps_search_widget", "layout", c.getPackageName()));
            Intent open = new Intent(c, SettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("page", "pixel_search");
            v.setOnClickPendingIntent(c.getResources().getIdentifier("ps_search_widget_text", "id", c.getPackageName()), PendingIntent.getActivity(c, 7301, open, PendingIntent.FLAG_UPDATE_CURRENT | 0x04000000));
            for (int id : ids) manager.updateAppWidget(id, v);
        } catch (Throwable ignored) { }
    }
}
