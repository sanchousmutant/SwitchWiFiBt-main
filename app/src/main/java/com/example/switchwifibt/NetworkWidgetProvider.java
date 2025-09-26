package com.example.switchwifibt;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.widget.RemoteViews;

public class NetworkWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);

            // Wi-Fi Button Setup
            Intent wifiIntent = new Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY);
            PendingIntent wifiPendingIntent = PendingIntent.getActivity(context, 0, wifiIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.wifi_button, wifiPendingIntent);

            // Bluetooth Button Setup
            Intent bluetoothIntent = new Intent(context, PermissionActivity.class);
            bluetoothIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent bluetoothPendingIntent = PendingIntent.getActivity(context, 1, bluetoothIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.bluetooth_button, bluetoothPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
    }
}