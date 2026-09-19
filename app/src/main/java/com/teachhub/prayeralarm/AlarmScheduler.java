package com.teachhub.prayeralarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public class AlarmScheduler {

    public static final int REMINDER_OFFSET = 500;
    public static final int REMINDER_MINUTES_BEFORE = 15;

    public static void scheduleAll(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        for (int i = 0; i < MainActivity.KEYS.length; i++) {
            String key = MainActivity.KEYS[i];
            String value = prefs.getString(key, null);
            boolean enabled = prefs.getBoolean(key + "_enabled", true);
            if (value != null && enabled) {
                scheduleOne(context, key, value, 1000 + i);
            } else {
                cancelOne(context, 1000 + i);
            }
        }
    }

    public static void cancelOne(Context context, int requestCode) {
        cancelPending(context, requestCode);
        cancelPending(context, requestCode + REMINDER_OFFSET);
    }

    private static void cancelPending(Context context, int requestCode) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pendingIntent);
    }

    private static Calendar computeNextOccurrence(String key, int hh, int mm) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hh);
        cal.set(Calendar.MINUTE, mm);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if ("jumma".equals(key)) {
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY || cal.getTimeInMillis() <= System.currentTimeMillis()) {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            }
        } else if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal;
    }

    public static void scheduleOne(Context context, String key, String time, int requestCode) {
        String[] parts = time.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);

        Calendar cal = computeNextOccurrence(key, hh, mm);
        scheduleExact(context, buildPrayerIntent(key, requestCode, false), requestCode, cal.getTimeInMillis());

        Calendar reminderCal = (Calendar) cal.clone();
        reminderCal.add(Calendar.MINUTE, -REMINDER_MINUTES_BEFORE);
        int reminderCode = requestCode + REMINDER_OFFSET;
        scheduleExact(context, buildPrayerIntent(key, requestCode, true), reminderCode, reminderCal.getTimeInMillis());
    }

    public static void rescheduleReminderForNextCycle(Context context, String key, String time, int mainRequestCode) {
        String[] parts = time.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hh);
        cal.set(Calendar.MINUTE, mm);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);

        if ("jumma".equals(key)) {
            do {
                cal.add(Calendar.DAY_OF_YEAR, 1);
            } while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.FRIDAY);
        } else {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        cal.add(Calendar.MINUTE, -REMINDER_MINUTES_BEFORE);
        int reminderCode = mainRequestCode + REMINDER_OFFSET;
        scheduleExact(context, buildPrayerIntent(key, mainRequestCode, true), reminderCode, cal.getTimeInMillis());
    }

    private static Intent buildPrayerIntent(String key, int mainRequestCode, boolean isReminder) {
        Intent intent = new Intent();
        intent.putExtra("prayer_key", key);
        intent.putExtra("main_request_code", mainRequestCode);
        intent.putExtra("is_reminder", isReminder);
        return intent;
    }

    private static void scheduleExact(Context context, Intent extrasIntent, int requestCode, long triggerAt) {
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtras(extrasIntent);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    // ---- Custom (non-prayer) alarms ----

    public static void scheduleCustom(Context context, int id, String time, String label) {
        String[] parts = time.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hh);
        cal.set(Calendar.MINUTE, mm);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra("is_custom", true);
        intent.putExtra("custom_id", id);
        intent.putExtra("time", time);
        intent.putExtra("label", label);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
            }
        } else {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pendingIntent);
        }
    }

    public static void cancelCustom(Context context, int id) {
        cancelPending(context, id);
    }
}
