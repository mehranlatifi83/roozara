package ir.mehranlatifi83.salemzi.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;

import ir.mehranlatifi83.salemzi.R;
import ir.mehranlatifi83.salemzi.manager.WaterReminderManager;
import ir.mehranlatifi83.salemzi.util.JalaliCalendar;
import ir.mehranlatifi83.salemzi.util.TimePickerHelper;

import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WaterActivity extends AppCompatActivity {

    private MaterialSwitch switchWater;
    private TextView       textWaterStatus;
    private TextView       textBreakfastTime;
    private TextView       textLunchTime;
    private TextView       textDinnerTime;
    private TextView       textReminderList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_water);
        bindViews();
        setupBottomNav();
        setupMealRows();
        setupSwitch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshUI();
    }

    // ─── View wiring ─────────────────────────────────────────────────────────

    private void bindViews() {
        switchWater       = findViewById(R.id.switch_water);
        textWaterStatus   = findViewById(R.id.text_water_status);
        textBreakfastTime = findViewById(R.id.text_breakfast_time);
        textLunchTime     = findViewById(R.id.text_lunch_time);
        textDinnerTime    = findViewById(R.id.text_dinner_time);
        textReminderList  = findViewById(R.id.text_reminder_list);

        ((TextView) findViewById(R.id.text_water_date)).setText(buildLocalizedDate());
        ((ImageButton) findViewById(R.id.btn_more)).setOnClickListener(v -> showMoreOptionsMenu(v));
    }

    private void setupBottomNav() {
        BottomNavigationView nav = findViewById(R.id.bottom_nav);
        nav.setSelectedItemId(R.id.nav_water);
        nav.setOnItemSelectedListener(item -> {
            if (item.getItemId() == R.id.nav_sleep) {
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                finish();
                return true;
            }
            return true;
        });
    }

    private void setupSwitch() {
        switchWater.setChecked(WaterReminderManager.isEnabled(this));
        switchWater.setOnCheckedChangeListener((btn, checked) -> {
            if (checked && !WaterReminderManager.canScheduleExact(this)) {
                btn.setChecked(false);
                showAlarmPermissionDialog();
                return;
            }
            WaterReminderManager.setEnabled(this, checked);
            refreshUI();
        });
    }

    private void showAlarmPermissionDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.alarm_permission_title)
                .setMessage(R.string.alarm_permission_message)
                .setPositiveButton(R.string.go_to_settings, (d, w) -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM));
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void setupMealRows() {
        findViewById(R.id.row_breakfast).setOnClickListener(v ->
                showMealPicker(R.string.meal_breakfast, WaterReminderManager.getBreakfast(this),
                        (h, m) -> { WaterReminderManager.saveBreakfast(this, h, m); onMealChanged(); }));

        findViewById(R.id.row_lunch).setOnClickListener(v ->
                showMealPicker(R.string.meal_lunch, WaterReminderManager.getLunch(this),
                        (h, m) -> { WaterReminderManager.saveLunch(this, h, m); onMealChanged(); }));

        findViewById(R.id.row_dinner).setOnClickListener(v ->
                showMealPicker(R.string.meal_dinner, WaterReminderManager.getDinner(this),
                        (h, m) -> { WaterReminderManager.saveDinner(this, h, m); onMealChanged(); }));
    }

    private void onMealChanged() {
        if (WaterReminderManager.isEnabled(this)) WaterReminderManager.scheduleAll(this);
        updateMealTimeViews();
        updateReminderList();
    }

    // ─── Time picker ─────────────────────────────────────────────────────────

    private void showMealPicker(int titleRes, int[] current, TimePickerCallback cb) {
        int h = (current != null) ? current[0] : 8;
        int m = (current != null) ? current[1] : 0;
        TimePickerHelper.show(getSupportFragmentManager(), this, getString(titleRes), h, m,
                (hour, min) -> cb.onTimePicked(hour, min));
    }

    @FunctionalInterface
    interface TimePickerCallback {
        void onTimePicked(int hour, int minute);
    }

    // ─── UI refresh ──────────────────────────────────────────────────────────

    private void refreshUI() {
        boolean enabled = WaterReminderManager.isEnabled(this);

        switchWater.setOnCheckedChangeListener(null);
        switchWater.setChecked(enabled);
        switchWater.setOnCheckedChangeListener((btn, checked) -> {
            WaterReminderManager.setEnabled(this, checked);
            refreshUI();
        });

        textWaterStatus.setText(enabled ? R.string.status_active : R.string.status_inactive);
        updateMealTimeViews();
        updateReminderList();
    }

    private void updateMealTimeViews() {
        textBreakfastTime.setText(fmtMeal(WaterReminderManager.getBreakfast(this)));
        textLunchTime.setText(fmtMeal(WaterReminderManager.getLunch(this)));
        textDinnerTime.setText(fmtMeal(WaterReminderManager.getDinner(this)));
    }

    private String fmtMeal(int[] hm) {
        if (hm == null) return getString(R.string.tap_to_set);
        return String.format(Locale.getDefault(), "%02d:%02d", hm[0], hm[1]);
    }

    private void updateReminderList() {
        List<int[]> slots = WaterReminderManager.computeReminderTimes(this);
        if (slots.isEmpty()) {
            textReminderList.setText(R.string.water_no_times_yet);
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < slots.size(); i++) {
            int[] s = slots.get(i);
            sb.append(String.format(Locale.getDefault(), "%02d:%02d", s[0], s[1]));
            if (i < slots.size() - 1) sb.append("\n");
        }
        textReminderList.setText(sb.toString());
    }

    // ─── More options menu ────────────────────────────────────────────────────

    private void showMoreOptionsMenu(android.view.View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor, Gravity.END);
        popup.getMenu().add(0, 1, 0, getString(R.string.menu_language));
        popup.getMenu().add(0, 2, 1, getString(R.string.menu_calendar));
        popup.getMenu().add(0, 3, 2, getString(R.string.menu_guide));
        popup.getMenu().add(0, 4, 3, getString(R.string.menu_privacy));
        popup.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1: showLanguagePicker(); return true;
                case 2: showCalendarPicker(); return true;
                case 3: showGuide();          return true;
                case 4: showPrivacyPolicy();  return true;
            }
            return false;
        });
        popup.show();
    }

    private void showGuide() {
        String lang = Locale.getDefault().getLanguage();
        String file = "fa".equals(lang) ? "guide_fa.html" : "guide_en.html";
        android.webkit.WebView wv = new android.webkit.WebView(this);
        wv.loadUrl("file:///android_asset/" + file);
        new AlertDialog.Builder(this)
                .setTitle(R.string.guide_title)
                .setView(wv)
                .setPositiveButton(R.string.got_it, null)
                .show();
    }

    private void showPrivacyPolicy() {
        String lang = Locale.getDefault().getLanguage();
        String file = "fa".equals(lang) ? "privacy_policy_fa.html" : "privacy_policy_en.html";
        android.webkit.WebView wv = new android.webkit.WebView(this);
        wv.loadUrl("file:///android_asset/" + file);
        new AlertDialog.Builder(this)
                .setTitle(R.string.privacy_policy_title)
                .setView(wv)
                .setPositiveButton(R.string.got_it, null)
                .show();
    }

    private void showLanguagePicker() {
        String[] labels = { getString(R.string.lang_persian), getString(R.string.lang_english) };
        String[] tags   = { "fa", "en" };
        LocaleListCompat current = AppCompatDelegate.getApplicationLocales();
        String currentTag = current.isEmpty()
                ? Locale.getDefault().getLanguage()
                : current.get(0).getLanguage();
        int checked = "fa".equals(currentTag) ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.language_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    AppCompatDelegate.setApplicationLocales(
                            LocaleListCompat.forLanguageTags(tags[which]));
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCalendarPicker() {
        SharedPreferences prefs = getSharedPreferences("helth_prefs", Context.MODE_PRIVATE);
        boolean useJalali = prefs.getBoolean("use_jalali_calendar",
                "fa".equals(Locale.getDefault().getLanguage()));
        String[] labels = { getString(R.string.calendar_jalali), getString(R.string.calendar_gregorian) };
        int checked = useJalali ? 0 : 1;
        new AlertDialog.Builder(this)
                .setTitle(R.string.calendar_title)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    prefs.edit().putBoolean("use_jalali_calendar", which == 0).apply();
                    ((TextView) findViewById(R.id.text_water_date)).setText(buildLocalizedDate());
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ─── Date ────────────────────────────────────────────────────────────────

    private String buildLocalizedDate() {
        Calendar cal = Calendar.getInstance();
        boolean useJalali = getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .getBoolean("use_jalali_calendar", "fa".equals(Locale.getDefault().getLanguage()));
        if (useJalali) {
            String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
                               "مهر","آبان","آذر","دی","بهمن","اسفند"};
            int[] j = JalaliCalendar.toJalali(
                    cal.get(Calendar.YEAR),
                    cal.get(Calendar.MONTH) + 1,
                    cal.get(Calendar.DAY_OF_MONTH));
            return j[2] + " " + months[j[1] - 1] + " " + j[0];
        }
        return cal.getDisplayName(Calendar.MONTH, Calendar.LONG, java.util.Locale.getDefault())
                + " " + cal.get(Calendar.DAY_OF_MONTH) + ", " + cal.get(Calendar.YEAR);
    }
}
