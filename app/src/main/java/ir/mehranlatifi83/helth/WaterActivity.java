package ir.mehranlatifi83.helth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

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

        ((TextView) findViewById(R.id.text_water_date)).setText(buildPersianDate());
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
            return true; // nav_water: stay
        });
    }

    private void setupSwitch() {
        switchWater.setChecked(WaterReminderManager.isEnabled(this));
        switchWater.setOnCheckedChangeListener((btn, checked) -> {
            WaterReminderManager.setEnabled(this, checked);
            refreshUI();
        });
    }

    private void setupMealRows() {
        findViewById(R.id.row_breakfast).setOnClickListener(v -> showMealPicker(
                R.string.meal_breakfast,
                WaterReminderManager.getBreakfast(this),
                (h, m) -> {
                    WaterReminderManager.saveBreakfast(this, h, m);
                    refreshSchedule();
                }));

        findViewById(R.id.row_lunch).setOnClickListener(v -> showMealPicker(
                R.string.meal_lunch,
                WaterReminderManager.getLunch(this),
                (h, m) -> {
                    WaterReminderManager.saveLunch(this, h, m);
                    refreshSchedule();
                }));

        findViewById(R.id.row_dinner).setOnClickListener(v -> showMealPicker(
                R.string.meal_dinner,
                WaterReminderManager.getDinner(this),
                (h, m) -> {
                    WaterReminderManager.saveDinner(this, h, m);
                    refreshSchedule();
                }));
    }

    // ─── Time picker ─────────────────────────────────────────────────────────

    private void showMealPicker(int titleRes, int[] current, TimePickerCallback cb) {
        int h = (current != null) ? current[0] : 8;
        int m = (current != null) ? current[1] : 0;

        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(h)
                .setMinute(m)
                .setTitleText(titleRes)
                .build();

        picker.addOnPositiveButtonClickListener(v ->
                cb.onTimePicked(picker.getHour(), picker.getMinute()));
        picker.show(getSupportFragmentManager(), "meal_picker");
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

    private void refreshSchedule() {
        if (WaterReminderManager.isEnabled(this)) {
            WaterReminderManager.scheduleAll(this);
        }
        updateMealTimeViews();
        updateReminderList();
    }

    private void updateMealTimeViews() {
        textBreakfastTime.setText(formatMeal(WaterReminderManager.getBreakfast(this)));
        textLunchTime.setText(formatMeal(WaterReminderManager.getLunch(this)));
        textDinnerTime.setText(formatMeal(WaterReminderManager.getDinner(this)));
    }

    private String formatMeal(int[] hm) {
        if (hm == null) return getString(R.string.schedule_not_set);
        return String.format(Locale.getDefault(), "%02d:%02d", hm[0], hm[1]);
    }

    private void updateReminderList() {
        List<int[]> slots = WaterReminderManager.computeReminderTimes(this);
        if (slots.isEmpty()) {
            textReminderList.setText(R.string.water_no_times_yet);
            return;
        }
        StringBuilder sb = new StringBuilder();
        String[] typeLabels = getResources().getStringArray(R.array.water_reminder_types);
        for (int[] slot : slots) {
            sb.append(String.format(Locale.getDefault(), "%02d:%02d", slot[0], slot[1]));
            if (slot[2] < typeLabels.length) {
                sb.append("  ").append(typeLabels[slot[2]]);
            }
            sb.append("\n");
        }
        textReminderList.setText(sb.toString().trim());
    }

    // ─── Date ────────────────────────────────────────────────────────────────

    private String buildPersianDate() {
        Calendar cal    = Calendar.getInstance();
        String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
                           "مهر","آبان","آذر","دی","بهمن","اسفند"};
        int[] j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        return j[2] + " " + months[j[1] - 1] + " " + j[0];
    }
}
