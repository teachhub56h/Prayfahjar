package com.teachhub.prayeralarm;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import androidx.core.app.NotificationCompat;

public class AlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_ID = "prayer_alarms_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        String key = intent.getStringExtra("prayer_key");
        int requestCode = intent.getIntExtra("request_code", 1000);
        if (key == null) return;

        String label = labelFor(key);

        Intent fullScreenIntent = new Intent(context, AlarmRingActivity.class);
        fullScreenIntent.putExtra("prayer_label", label);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context, requestCode, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label + " ki Namaz ka Waqt")
                .setContentText("Namaz ke liye tayyar ho jayein")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(fullScreenPendingIntent, true)
                .setAutoCancel(true)
                .setOngoing(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(requestCode, builder.build());
        }

        try {
            context.startActivity(fullScreenIntent);
        } catch (Exception ignored) {
        }

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String time = prefs.getString(key, null);
        if (time != null) {
            AlarmScheduler.scheduleOne(context, key, time, requestCode);
        }
    }

    private String labelFor(String key) {
        switch (key) {
            case "fajr": return "Fajr";
            case "zuhr": return "Zuhr";
            case "asr": return "Asr";
            case "maghrib": return "Maghrib";
            case "isha": return "Isha";
            default: return "Namaz";
        }
    }
}
