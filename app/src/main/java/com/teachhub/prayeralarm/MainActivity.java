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
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    public static final String PREFS = "prayer_times_prefs";
    public static final String[] KEYS = {"fajr", "zuhr", "asr", "maghrib", "isha", "jumma"};
    public static final String[] LABELS = {"Fajr", "Zuhr", "Asr", "Maghrib", "Isha", "Jumma"};

    private static final int RINGTONE_PICKER_REQUEST = 501;

    private final TextView[] timeLabels = new TextView[6];

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        createNotificationChannels();

        int[] buttonIds = {R.id.btnFajr, R.id.btnZuhr, R.id.btnAsr, R.id.btnMaghrib, R.id.btnIsha, R.id.btnJumma};
        int[] labelIds = {R.id.lblFajr, R.id.lblZuhr, R.id.lblAsr, R.id.lblMaghrib, R.id.lblIsha, R.id.lblJumma};
        int[] switchIds = {R.id.swFajr, R.id.swZuhr, R.id.swAsr, R.id.swMaghrib, R.id.swIsha, R.id.swJumma};

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);

        for (int i = 0; i < KEYS.length; i++) {
            final int idx = i;
            timeLabels[i] = findViewById(labelIds[i]);
            String saved = prefs.getString(KEYS[i], null);
            timeLabels[i].setText(saved != null ? formatDisplayTime(saved) : "-- : --");

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

        Button ringtoneBtn = findViewById(R.id.btnChangeRingtone);
        ringtoneBtn.setOnClickListener(v -> pickRingtone());

        Button addAlarmBtn = findViewById(R.id.btnAddCustomAlarm);
        addAlarmBtn.setOnClickListener(v -> addCustomAlarm());

        renderCustomAlarms();
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
            timeLabels[idx].setText(formatDisplayTime(value));
            AlarmScheduler.scheduleAll(this);
            Toast.makeText(this, LABELS[idx] + " time set: " + formatDisplayTime(value), Toast.LENGTH_SHORT).show();
        }, hh, mm, false);
        dialog.show();
    }

    private String formatDisplayTime(String hhmm) {
        String[] parts = hhmm.split(":");
        int hh24 = Integer.parseInt(parts[0]);
        int mm = Integer.parseInt(parts[1]);
        String ampm = hh24 >= 12 ? "PM" : "AM";
        int hh12 = hh24 % 12;
        if (hh12 == 0) hh12 = 12;
        return String.format("%02d:%02d %s", hh12, mm, ampm);
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            NotificationChannel mainChannel = new NotificationChannel(
                    AlarmReceiver.CHANNEL_ID,
                    "Prayer Alarms",
                    NotificationManager.IMPORTANCE_HIGH
            );
            mainChannel.setDescription("Loud alarms for namaz times");
            mainChannel.enableVibration(true);
            nm.createNotificationChannel(mainChannel);

            NotificationChannel reminderChannel = new NotificationChannel(
                    AlarmReceiver.REMINDER_CHANNEL_ID,
                    "Prayer Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
            );
            reminderChannel.setDescription("15 minute pehle ki reminder notification");
            nm.createNotificationChannel(reminderChannel);
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

    // ---- Custom ringtone ----

    private void pickRingtone() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String existing = prefs.getString("custom_ringtone_uri", null);

        Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true);
        intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false);
        if (existing != null) {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existing));
        } else {
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM));
        }
        startActivityForResult(intent, RINGTONE_PICKER_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RINGTONE_PICKER_REQUEST && data != null) {
            Uri uri = data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            if (uri != null) {
                editor.putString("custom_ringtone_uri", uri.toString());
                Toast.makeText(this, "Ringtone set ho gayi", Toast.LENGTH_SHORT).show();
            } else {
                editor.remove("custom_ringtone_uri");
                Toast.makeText(this, "Default ringtone use hogi", Toast.LENGTH_SHORT).show();
            }
            editor.apply();
        }
    }

    // ---- Custom (extra) alarms ----

    private JSONArray loadCustomAlarms(SharedPreferences prefs) {
        try {
            return new JSONArray(prefs.getString("custom_alarms_json", "[]"));
        } catch (JSONException e) {
            return new JSONArray();
        }
    }

    private void addCustomAlarm() {
        Calendar now = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
            int nextId = prefs.getInt("next_custom_id", 9000);
            String time = String.format("%02d:%02d", hourOfDay, minute);
            String label = "Alarm " + (nextId - 9000 + 1);

            JSONArray arr = loadCustomAlarms(prefs);
            try {
                JSONObject obj = new JSONObject();
                obj.put("id", nextId);
                obj.put("time", time);
                obj.put("label", label);
                arr.put(obj);
            } catch (JSONException ignored) {
            }

            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("custom_alarms_json", arr.toString());
            editor.putInt("next_custom_id", nextId + 1);
            editor.apply();

            AlarmScheduler.scheduleCustom(this, nextId, time, label);
            renderCustomAlarms();
            Toast.makeText(this, label + " set: " + formatDisplayTime(time), Toast.LENGTH_SHORT).show();
        }, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
        dialog.show();
    }

    private void renderCustomAlarms() {
        LinearLayout container = findViewById(R.id.customAlarmsContainer);
        container.removeAllViews();
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray arr = loadCustomAlarms(prefs);

        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                int id = obj.getInt("id");
                String time = obj.getString("time");
                String label = obj.optString("label", "Alarm");

                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setPadding(dp(14), dp(14), dp(14), dp(14));
                row.setBackgroundColor(Color.WHITE);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(10);
                row.setLayoutParams(rowParams);

                TextView tv = new TextView(this);
                tv.setText(formatDisplayTime(time) + "   " + label);
                tv.setTextColor(ContextCompat.getColor(this, R.color.textDark));
                tv.setTextSize(17);
                LinearLayout.LayoutParams tvParams =
                        new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                tv.setLayoutParams(tvParams);
                row.addView(tv);

                Button delBtn = new Button(this);
                delBtn.setText("Delete");
                delBtn.setBackgroundColor(ContextCompat.getColor(this, R.color.navy));
                delBtn.setTextColor(ContextCompat.getColor(this, R.color.gold));
                delBtn.setOnClickListener(v -> deleteCustomAlarm(id));
                row.addView(delBtn);

                container.addView(row);
            } catch (JSONException ignored) {
            }
        }
    }

    private void deleteCustomAlarm(int id) {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        JSONArray arr = loadCustomAlarms(prefs);
        JSONArray newArr = new JSONArray();
        for (int i = 0; i < arr.length(); i++) {
            try {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.getInt("id") != id) newArr.put(obj);
            } catch (JSONException ignored) {
            }
        }
        prefs.edit().putString("custom_alarms_json", newArr.toString()).apply();
        AlarmScheduler.cancelCustom(this, id);
        renderCustomAlarms();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
