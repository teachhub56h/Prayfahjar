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
    public static final String REMINDER_CHANNEL_ID = "prayer_reminders_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        boolean isCustom = intent.getBooleanExtra("is_custom", false);
        if (isCustom) {
            handleCustomAlarm(context, intent);
            return;
        }

        boolean isReminder = intent.getBooleanExtra("is_reminder", false);
        String key = intent.getStringExtra("prayer_key");
        int mainRequestCode = intent.getIntExtra("main_request_code", 1000);
        if (key == null) return;

        String label = labelFor(key);

        if (isReminder) {
            showReminderNotification(context, label, mainRequestCode);
            SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
            String time = prefs.getString(key, null);
            if (time != null) {
                AlarmScheduler.rescheduleReminderForNextCycle(context, key, time, mainRequestCode);
            }
            return;
        }

        triggerFullAlarm(context, label, mainRequestCode);

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        String time = prefs.getString(key, null);
        if (time != null) {
            AlarmScheduler.scheduleOne(context, key, time, mainRequestCode);
        }
    }

    private void handleCustomAlarm(Context context, Intent intent) {
        int id = intent.getIntExtra("custom_id", 0);
        String time = intent.getStringExtra("time");
        String label = intent.getStringExtra("label");
        if (label == null) label = "Alarm";

        triggerFullAlarm(context, label, id);

        if (time != null) {
            AlarmScheduler.scheduleCustom(context, id, time, label);
        }
    }

    private void triggerFullAlarm(Context context, String label, int requestCode) {
        Intent fullScreenIntent = new Intent(context, AlarmRingActivity.class);
        fullScreenIntent.putExtra("prayer_label", label);
        fullScreenIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent fullScreenPendingIntent = PendingIntent.getActivity(
                context, requestCode, fullScreenIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label + " ka Waqt")
                .setContentText("Tayyar ho jayein")
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
    }

    private void showReminderNotification(Context context, String label, int mainRequestCode) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(label + " ki Namaz 15 minute mein")
                .setContentText("Tayyar ho jayein")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(mainRequestCode + AlarmScheduler.REMINDER_OFFSET, builder.build());
        }
    }

    private String labelFor(String key) {
        switch (key) {
            case "fajr": return "Fajr";
            case "zuhr": return "Zuhr";
            case "asr": return "Asr";
            case "maghrib": return "Maghrib";
            case "isha": return "Isha";
            case "jumma": return "Jumma";
            default: return "Namaz";
        }
    }
}
