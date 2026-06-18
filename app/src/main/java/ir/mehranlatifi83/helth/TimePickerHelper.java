package ir.mehranlatifi83.helth;

import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * Shows a simple numeric-only time picker dialog (HH:MM).
 * Uses EditText with TYPE_CLASS_NUMBER to guarantee a digits-only keyboard
 * regardless of the user's active IME.
 */
public class TimePickerHelper {

    public interface OnTimeSetListener {
        void onTimeSet(int hour, int minute);
    }

    public static void show(Context ctx, String title,
                            int initialHour, int initialMin,
                            OnTimeSetListener listener) {

        // ── Layout ──────────────────────────────────────────────────────────
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        int pad = dp(ctx, 24);
        root.setPadding(pad, pad, pad, 8);

        EditText etHour = makeField(ctx, initialHour, 23);
        TextView colon  = new TextView(ctx);
        colon.setText(":");
        colon.setTextSize(28);
        colon.setPadding(dp(ctx, 8), 0, dp(ctx, 8), 0);

        EditText etMin  = makeField(ctx, initialMin, 59);

        root.addView(etHour);
        root.addView(colon);
        root.addView(etMin);

        // Auto-advance focus from hour → minute after 2 digits
        etHour.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) {}
            public void afterTextChanged(Editable s) {
                if (s.length() == 2) etMin.requestFocus();
            }
        });

        // ── Dialog ───────────────────────────────────────────────────────────
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setView(root)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    int h = parseField(etHour, 0, 23, initialHour);
                    int m = parseField(etMin,  0, 59, initialMin);
                    listener.onTimeSet(h, m);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();

        // Open keyboard immediately on hour field
        etHour.postDelayed(() -> {
            etHour.requestFocus();
            etHour.selectAll();
        }, 100);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static EditText makeField(Context ctx, int value, int max) {
        EditText et = new EditText(ctx);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        et.setGravity(Gravity.CENTER);
        et.setTextSize(28);
        et.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});
        et.setText(String.format(java.util.Locale.getDefault(), "%02d", value));
        et.setSelectAllOnFocus(true);
        int w = dp(ctx, 72);
        et.setLayoutParams(new LinearLayout.LayoutParams(w, LinearLayout.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private static int parseField(EditText et, int min, int max, int fallback) {
        try {
            int v = Integer.parseInt(et.getText().toString().trim());
            return Math.max(min, Math.min(max, v));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static int dp(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
