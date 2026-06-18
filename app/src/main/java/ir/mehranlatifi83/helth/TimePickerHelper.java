package ir.mehranlatifi83.helth;

import android.content.Context;
import android.view.Gravity;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.NumberPicker;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.google.android.material.timepicker.TimeFormat;

import java.util.Locale;

/**
 * Time picker with two switchable modes:
 *  - Scroll mode (default): two NumberPicker spinners, no keyboard
 *  - Clock mode:            MaterialTimePicker circular clock face
 */
public class TimePickerHelper {

    public interface OnTimeSetListener {
        void onTimeSet(int hour, int minute);
    }

    /** Show the picker starting in scroll (NumberPicker) mode. */
    public static void show(FragmentManager fm, Context ctx, String title,
                            int initialHour, int initialMin,
                            OnTimeSetListener listener) {
        showScrollMode(fm, ctx, title, initialHour, initialMin, listener);
    }

    // ── Scroll mode (NumberPicker) ────────────────────────────────────────────

    private static void showScrollMode(FragmentManager fm, Context ctx, String title,
                                       int initialHour, int initialMin,
                                       OnTimeSetListener listener) {
        int pad = dp(ctx, 24);

        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);

        // Hour : Minute pickers row
        LinearLayout pickersRow = new LinearLayout(ctx);
        pickersRow.setOrientation(LinearLayout.HORIZONTAL);
        pickersRow.setGravity(Gravity.CENTER);
        pickersRow.setPadding(pad, dp(ctx, 8), pad, 0);

        NumberPicker npHour = makeNumberPicker(ctx, 0, 23, initialHour);
        TextView colon = new TextView(ctx);
        colon.setText(":");
        colon.setTextSize(28);
        colon.setPadding(dp(ctx, 16), 0, dp(ctx, 16), 0);
        NumberPicker npMin = makeNumberPicker(ctx, 0, 59, initialMin);

        pickersRow.addView(npHour);
        pickersRow.addView(colon);
        pickersRow.addView(npMin);

        // Toggle button
        TextView toggleBtn = new TextView(ctx);
        toggleBtn.setText("🕐  حالت ساعت دایره‌ای");
        toggleBtn.setTextSize(13);
        toggleBtn.setGravity(Gravity.CENTER);
        toggleBtn.setPadding(0, dp(ctx, 4), 0, dp(ctx, 12));

        content.addView(pickersRow);
        content.addView(toggleBtn);

        AlertDialog dialog = new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, (d, w) ->
                        listener.onTimeSet(npHour.getValue(), npMin.getValue()))
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        toggleBtn.setOnClickListener(v -> {
            dialog.dismiss();
            showClockMode(fm, ctx, title, npHour.getValue(), npMin.getValue(), listener);
        });

        dialog.show();
    }

    // ── Clock mode (MaterialTimePicker) ───────────────────────────────────────

    private static void showClockMode(FragmentManager fm, Context ctx, String title,
                                      int initialHour, int initialMin,
                                      OnTimeSetListener listener) {
        MaterialTimePicker picker = new MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                .setHour(initialHour)
                .setMinute(initialMin)
                .setTitleText(title)
                .build();

        picker.addOnPositiveButtonClickListener(v ->
                listener.onTimeSet(picker.getHour(), picker.getMinute()));

        // If user taps the keyboard icon inside the clock picker, intercept and
        // switch back to our scroll mode so they never land on a letter keyboard.
        picker.addOnNegativeButtonClickListener(v ->
                showScrollMode(fm, ctx, title, picker.getHour(), picker.getMinute(), listener));

        picker.show(fm, "clock_picker");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static NumberPicker makeNumberPicker(Context ctx, int min, int max, int value) {
        NumberPicker np = new NumberPicker(ctx);
        np.setMinValue(min);
        np.setMaxValue(max);
        np.setValue(value);
        np.setWrapSelectorWheel(true);
        np.setFormatter(i -> String.format(Locale.getDefault(), "%02d", i));
        np.invalidate();
        return np;
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
