package com.teachhub.prayeralarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS = "prayer_times_prefs";
    public static final String[] KEYS = {"fajr", "zuhr", "asr", "maghrib", "isha", "jumma"};
    public static final String[] LABELS = {"Fajr", "Zuhr", "Asr", "Maghrib", "Isha", "Jumma"};

    private final TextView[] timeLabels = new TextView[6];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannel();

        int[] buttonIds = {R.id.btnFajr, R.id.btnZuhr, R.id.btnAsr, R.id.btnMaghrib, R.id.btnIsha, R.id.btnJumma};
        int[] labelIds = {R.id.lblFajr, R.id.lblZuhr, R.id.lblAsr, R.id.lblMaghrib, R.id.lblIsha, R.id.lblJumma};
        int[] switchIds = {R.id.swFajr, R.id.swZuhr, R.id.swAsr, R.id.swMaghrib, R.id.swIsha, R.id.swJumma};

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        for (int i = 0; i < KEYS.length; i++) {
            final int idx = i;
            timeLabels[i] = findViewById(labelIds[i]);
            String saved = prefs.getString(KEYS[i], null);
            timeLabels[i].setText(saved != null ? saved : "-- : --");

            Button btn = findViewById(buttonIds[i]);
            btn.setText(LABELS[i]);
            btn.setOnClickListener(v -> pickTime(idx));

            Switch sw = findViewById(switchIds[i]);
            boolean enabled = prefs.getBoolean(KEYS[i] + "_enabled", true);
            sw.setChecked(enabled);
            sw.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean(KEYS[idx] + "_enabled", isChecked).apply();
                if (isChecked) {
                    AlarmScheduler.scheduleAll(this);
                } else {
                    AlarmScheduler.cancelOne(this, 1000 + idx);
                }
            });
        }

        requestPermissionsIfNeeded();
    }

    private void pickTime(int idx) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String saved = prefs.getString(KEYS[idx], "05:00");
        String[] parts = saved.split(":");
        int hh = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);

        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            String value = String.format("%02d:%02d", hourOfDay, minute);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString(KEYS[idx], value);
            editor.apply();
            timeLabels[idx].setText(value);
            AlarmScheduler.scheduleAll(this);
            Toast.makeText(this, LABELS[idx] + " time set: " + value, Toast.LENGTH_SHORT).show();
        }, hh, mm, true);
        dialog.show();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    AlarmReceiver.CHANNEL_ID,
                    "Prayer Alarms",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Loud alarms for namaz times");
            channel.enableVibration(true);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(channel);
        }
    }

    private void requestPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }
        if (Build.VERSION.SDK_INT >= 31) {
            AlarmManager am = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (am != null && !am.canScheduleExactAlarms()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        }
        String pkg = getPackageName();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(pkg)) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + pkg));
            try {
                startActivity(intent);
            } catch (Exception ignored) {
            }
        }
        AlarmScheduler.scheduleAll(this);
    }
}
