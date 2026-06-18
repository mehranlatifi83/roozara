package ir.mehranlatifi83.helth.ui;

import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import ir.mehranlatifi83.helth.R;
import ir.mehranlatifi83.helth.manager.ScheduleManager;
import ir.mehranlatifi83.helth.service.SleepVpnService;
import ir.mehranlatifi83.helth.service.WakeAlarmService;
import ir.mehranlatifi83.helth.util.JalaliCalendar;

import java.util.Calendar;
import java.util.Locale;
import java.util.Random;

public class SleepLockActivity extends AppCompatActivity {

    private static final String PREFS           = "helth_prefs";
    private static final String KEY_LOCK_MODE   = "lock_exit_mode";
    public static final  String KEY_SLEEP_START = "sleep_start_time";

    public static final String MODE_MATH   = "math";
    public static final String MODE_MEMORY = "memory";
    public static final String MODE_TIMED  = "timed";

    // Math / memory section views (section_math is shared for both)
    private LinearLayout      sectionMath;
    private TextView          textChallengePrompt;
    private TextView          textMathProblem;
    private TextView          textMemoryEnterPrompt;
    private TextInputEditText editAnswer;
    private TextInputLayout   inputLayoutAnswer;
    private TextView          textError;

    // Timed mode views
    private LinearLayout   sectionTimed;
    private TextView       textCountdown;
    private TextView       textNoExitHint;
    private TextView       textCountdownLabel;
    private MaterialButton btnConfirmAwake;
    private MaterialButton btnEarlyExit;
    private View           dividerEarlyExit;
    private TextView       textEarlyExitHint;

    // Clock views (shared)
    private TextView textClock;
    private TextView textLockDate;

    // State
    private String  currentMode;
    private int     mathAnswer;
    private String  memorySequence;
    private int     wrongCount       = 0;
    private boolean exitCalled       = false;
    private boolean screenTurningOff = false;

    private CountDownTimer countDownTimer;
    private final Handler  handler    = new Handler(Looper.getMainLooper());
    private final Runnable clockTick  = new Runnable() {
        @Override public void run() {
            updateClock();
            handler.postDelayed(this, 1000);
        }
    };

    private final BroadcastReceiver screenOffReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context ctx, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                screenTurningOff = true;
            }
        }
    };

    // ─── Entry point ─────────────────────────────────────────────────────────

    public static void launch(Context ctx) {
        ctx.startActivity(new Intent(ctx, SleepLockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_CLEAR_TOP
                        | Intent.FLAG_ACTIVITY_SINGLE_TOP));
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setupWindowFlags();
        setContentView(R.layout.activity_sleep_lock);
        hideSystemBars();
        blockBackButton();
        bindViews();

        currentMode = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_LOCK_MODE, MODE_MATH);

        updateClock();

        switch (currentMode) {
            case MODE_TIMED:  setupTimedMode();  break;
            case MODE_MEMORY: setupMemoryMode(); break;
            default:          setupMathMode();   break;
        }
    }

    private void bindViews() {
        textClock             = findViewById(R.id.text_clock);
        textLockDate          = findViewById(R.id.text_lock_date);
        sectionMath           = findViewById(R.id.section_math);
        textChallengePrompt   = findViewById(R.id.text_challenge_prompt);
        textMathProblem       = findViewById(R.id.text_math_problem);
        textMemoryEnterPrompt = findViewById(R.id.text_memory_enter_prompt);
        sectionTimed          = findViewById(R.id.section_timed);
        textCountdown         = findViewById(R.id.text_countdown);
        textNoExitHint        = findViewById(R.id.text_no_exit_hint);
        textCountdownLabel    = findViewById(R.id.text_countdown_label);
        btnConfirmAwake       = findViewById(R.id.btn_confirm_awake);
        btnEarlyExit          = findViewById(R.id.btn_early_exit);
        dividerEarlyExit      = findViewById(R.id.divider_early_exit);
        textEarlyExitHint     = findViewById(R.id.text_early_exit_hint);
    }

    @Override
    protected void onResume() {
        super.onResume();
        screenTurningOff = false;
        registerReceiver(screenOffReceiver, new IntentFilter(Intent.ACTION_SCREEN_OFF));
        handler.post(clockTick);
        hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
        try { unregisterReceiver(screenOffReceiver); } catch (IllegalArgumentException ignored) {}
        // Re-launch to prevent HOME-button escape, but not when screen turns off.
        if (!exitCalled && !isFinishing() && !screenTurningOff) {
            SleepLockActivity.launch(this);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (countDownTimer != null) countDownTimer.cancel();
        if (!exitCalled) WakeAlarmService.stop(this);
    }

    // ─── Window / immersive mode ─────────────────────────────────────────────

    private void setupWindowFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        }
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
    }

    private void keepScreenOn() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void hideSystemBars() {
        WindowInsetsControllerCompat ctrl =
                WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        ctrl.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        ctrl.hide(WindowInsetsCompat.Type.systemBars());
    }

    private void blockBackButton() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { /* intentionally empty */ }
        });
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // ─── Clock ───────────────────────────────────────────────────────────────

    private void updateClock() {
        Calendar cal = Calendar.getInstance();
        textClock.setText(String.format(Locale.getDefault(),
                "%02d:%02d", cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE)));
        textLockDate.setText(buildPersianDate(cal));
    }

    private String buildPersianDate(Calendar cal) {
        String[] days   = {"یکشنبه","دوشنبه","سه‌شنبه","چهارشنبه","پنجشنبه","جمعه","شنبه"};
        String[] months = {"فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور",
                           "مهر","آبان","آذر","دی","بهمن","اسفند"};
        int[] j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));
        return days[cal.get(Calendar.DAY_OF_WEEK) - 1] + "،  " + j[2] + " " + months[j[1] - 1];
    }

    // ─── Math mode ───────────────────────────────────────────────────────────

    private void setupMathMode() {
        sectionMath.setVisibility(View.VISIBLE);
        sectionTimed.setVisibility(View.GONE);

        inputLayoutAnswer = findViewById(R.id.input_layout_answer);
        editAnswer        = findViewById(R.id.edit_answer);
        textError         = findViewById(R.id.text_error);
        MaterialButton btnConfirm = findViewById(R.id.btn_confirm);

        textChallengePrompt.setText(R.string.math_challenge_prompt);
        textMemoryEnterPrompt.setVisibility(View.GONE);

        generateMathProblem();

        btnConfirm.setOnClickListener(v -> checkMathAnswer());
        editAnswer.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) { checkMathAnswer(); return true; }
            return false;
        });
    }

    /**
     * Random arithmetic problem. Difficulty scales with wrong-answer count:
     *  Level 0: simple (×, +, or −)  Level 1: chained  Level 2: double multiplication
     */
    private void generateMathProblem() {
        Random rnd = new Random();
        String problem;
        int difficulty = Math.min(wrongCount / 2, 2);

        switch (difficulty) {
            case 0: {
                int type = rnd.nextInt(3);
                if (type == 0) {
                    int a = 12 + rnd.nextInt(29); int b = 3 + rnd.nextInt(7);
                    mathAnswer = a * b; problem = a + " × " + b + " = ؟";
                } else if (type == 1) {
                    int a = 30 + rnd.nextInt(60); int b = 20 + rnd.nextInt(40);
                    mathAnswer = a + b; problem = a + " + " + b + " = ؟";
                } else {
                    int b = 10 + rnd.nextInt(40); int a = b + 20 + rnd.nextInt(40);
                    mathAnswer = a - b; problem = a + " - " + b + " = ؟";
                }
                break;
            }
            case 1: {
                int a = 3 + rnd.nextInt(7); int b = 3 + rnd.nextInt(7);
                int c = 11 + rnd.nextInt(29); int base = a * b;
                if (rnd.nextBoolean() || base <= c) {
                    mathAnswer = base + c; problem = "(" + a + " × " + b + ") + " + c + " = ؟";
                } else {
                    mathAnswer = base - c; problem = "(" + a + " × " + b + ") - " + c + " = ؟";
                }
                break;
            }
            default: {
                int a = 3 + rnd.nextInt(7); int b = 3 + rnd.nextInt(7);
                int c = 3 + rnd.nextInt(6); int d = 3 + rnd.nextInt(6);
                mathAnswer = a * b + c * d;
                problem = "(" + a + " × " + b + ") + (" + c + " × " + d + ") = ؟";
                break;
            }
        }

        textMathProblem.setVisibility(View.VISIBLE);
        textMathProblem.setText(problem);
        if (editAnswer != null) editAnswer.setText("");
        if (textError   != null) textError.setVisibility(View.INVISIBLE);
    }

    private void checkMathAnswer() {
        String raw = editAnswer.getText() == null ? "" : editAnswer.getText().toString().trim();
        if (raw.isEmpty()) return;
        try {
            if (Integer.parseInt(raw) == mathAnswer) {
                exitSleepMode();
            } else {
                wrongCount++;
                onWrongMathAnswer();
            }
        } catch (NumberFormatException e) {
            wrongCount++;
            onWrongMathAnswer();
        }
    }

    private void onWrongMathAnswer() {
        String msg;
        if      (wrongCount < 3) msg = "اشتباهه! دوباره امتحان کن";
        else if (wrongCount < 6) msg = "نه! بذار بخوابی 😴  (" + wrongCount + " بار اشتباه)";
        else                     msg = "برو بخواب! " + wrongCount + " بار اشتباه زدی 🌙";

        textError.setText(msg);
        textError.setVisibility(View.VISIBLE);
        editAnswer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
        editAnswer.setText("");
        editAnswer.postDelayed(this::generateMathProblem, 900);
    }

    // ─── Memory mode ─────────────────────────────────────────────────────────

    private void setupMemoryMode() {
        sectionMath.setVisibility(View.VISIBLE);
        sectionTimed.setVisibility(View.GONE);

        inputLayoutAnswer = findViewById(R.id.input_layout_answer);
        editAnswer        = findViewById(R.id.edit_answer);
        textError         = findViewById(R.id.text_error);
        MaterialButton btnConfirm = findViewById(R.id.btn_confirm);

        // Hide input until after the reveal phase
        inputLayoutAnswer.setVisibility(View.GONE);
        btnConfirm.setVisibility(View.GONE);
        textMemoryEnterPrompt.setVisibility(View.GONE);

        startMemoryReveal(btnConfirm);
    }

    private void startMemoryReveal(MaterialButton btnConfirm) {
        Random rnd = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5; i++) sb.append(rnd.nextInt(10));
        memorySequence = sb.toString();

        textChallengePrompt.setText(R.string.memory_show_prompt);
        textMathProblem.setText(memorySequence);
        textMathProblem.setVisibility(View.VISIBLE);
        textError.setVisibility(View.INVISIBLE);

        // After 4 seconds: hide sequence, show input
        handler.postDelayed(() -> {
            textMathProblem.setVisibility(View.GONE);
            textChallengePrompt.setText(R.string.memory_enter_prompt);
            textMemoryEnterPrompt.setVisibility(View.GONE); // prompt is now in textChallengePrompt
            inputLayoutAnswer.setVisibility(View.VISIBLE);
            inputLayoutAnswer.setHint(getString(R.string.memory_hint));
            editAnswer.setInputType(InputType.TYPE_CLASS_NUMBER);
            editAnswer.requestFocus();
            btnConfirm.setVisibility(View.VISIBLE);

            btnConfirm.setOnClickListener(v -> checkMemoryAnswer(btnConfirm));
            editAnswer.setOnEditorActionListener((v, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_DONE) { checkMemoryAnswer(btnConfirm); return true; }
                return false;
            });
        }, 4000);
    }

    private void checkMemoryAnswer(MaterialButton btnConfirm) {
        if (editAnswer.getText() == null) return;
        String typed = editAnswer.getText().toString().trim();
        if (typed.isEmpty()) return;

        if (typed.equals(memorySequence)) {
            exitSleepMode();
        } else {
            textError.setText("اشتباهه! دوباره امتحان کن");
            textError.setVisibility(View.VISIBLE);
            editAnswer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.shake));
            editAnswer.setText("");
            inputLayoutAnswer.setVisibility(View.GONE);
            btnConfirm.setVisibility(View.GONE);
            editAnswer.postDelayed(() -> startMemoryReveal(btnConfirm), 1000);
        }
    }

    // ─── Timed mode ──────────────────────────────────────────────────────────

    private void setupTimedMode() {
        sectionMath.setVisibility(View.GONE);
        sectionTimed.setVisibility(View.VISIBLE);
        startCountdown();
        scheduleEarlyExitUnlock();
    }

    private void startCountdown() {
        int[] wake = ScheduleManager.getWakeTime(this);
        if (wake == null) {
            textCountdown.setText(R.string.no_wake_time_set);
            return;
        }

        long wakeMs    = nextWakeMs(wake[0], wake[1]);
        long remaining = wakeMs - System.currentTimeMillis();

        countDownTimer = new CountDownTimer(remaining, 1000) {
            @Override public void onTick(long ms) {
                long h = ms / 3_600_000;
                long m = (ms % 3_600_000) / 60_000;
                long s = (ms % 60_000) / 1_000;
                textCountdown.setText(
                        String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s));
            }
            @Override public void onFinish() {
                textCountdown.setText("00:00:00");
                onWakeTimeReached();
            }
        }.start();
    }

    /** Reveals the "early exit" button once the user has slept at least half the planned duration. */
    private void scheduleEarlyExitUnlock() {
        long sleepStartMs = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getLong(KEY_SLEEP_START, 0);
        long now = System.currentTimeMillis();

        int[] wake = ScheduleManager.getWakeTime(this);
        long wakeMs = (wake != null) ? nextWakeMs(wake[0], wake[1]) : 0;

        long halfSleepMs;
        if (sleepStartMs > 0 && wakeMs > sleepStartMs) {
            halfSleepMs = (wakeMs - sleepStartMs) / 2;
        } else {
            halfSleepMs = 4 * 60 * 60 * 1000L;
        }

        long unlockAt = sleepStartMs + halfSleepMs;
        long delay = unlockAt - now;

        if (delay <= 0) {
            showEarlyExitButton();
        } else {
            handler.postDelayed(this::showEarlyExitButton, delay);
        }
    }

    private void showEarlyExitButton() {
        if (exitCalled) return;
        dividerEarlyExit.setVisibility(View.VISIBLE);
        textEarlyExitHint.setVisibility(View.VISIBLE);
        btnEarlyExit.setVisibility(View.VISIBLE);
        btnEarlyExit.setOnClickListener(v -> showEarlyExitChallenge());
    }

    /** Shows a one-shot math dialog; loops until correct or user cancels. */
    private void showEarlyExitChallenge() {
        Random rnd = new Random();
        int[] ops = {0, 1, 2};  // 0=multiply, 1=add, 2=subtract
        int op = ops[rnd.nextInt(3)];
        int answer; String problem;
        if (op == 0) {
            int a = 12 + rnd.nextInt(19); int b = 3 + rnd.nextInt(7);
            answer = a * b; problem = a + " × " + b + " = ؟";
        } else if (op == 1) {
            int a = 30 + rnd.nextInt(50); int b = 20 + rnd.nextInt(40);
            answer = a + b; problem = a + " + " + b + " = ؟";
        } else {
            int b = 10 + rnd.nextInt(30); int a = b + 20 + rnd.nextInt(40);
            answer = a - b; problem = a + " - " + b + " = ؟";
        }

        EditText et = new EditText(this);
        et.setHint("جواب");
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setGravity(Gravity.CENTER);
        et.setPadding(64, 24, 64, 24);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.early_exit_challenge))
                .setMessage(problem)
                .setView(et)
                .setCancelable(true)
                .setPositiveButton("تأیید", (d, w) -> {
                    String typed = et.getText().toString().trim();
                    try {
                        if (Integer.parseInt(typed) == answer) exitSleepMode();
                        else handler.post(this::showEarlyExitChallenge);
                    } catch (NumberFormatException e) {
                        handler.post(this::showEarlyExitChallenge);
                    }
                })
                .setNegativeButton("لغو", null)
                .show();
    }

    private void onWakeTimeReached() {
        keepScreenOn();
        WakeAlarmService.start(this);
        textNoExitHint.setText(R.string.wake_time_reached);
        textCountdownLabel.setVisibility(View.GONE);
        dividerEarlyExit.setVisibility(View.GONE);
        textEarlyExitHint.setVisibility(View.GONE);
        btnEarlyExit.setVisibility(View.GONE);
        btnConfirmAwake.setVisibility(View.VISIBLE);
        btnConfirmAwake.setOnClickListener(v -> exitSleepMode());
    }

    private long nextWakeMs(int hour, int min) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    // ─── Exit ────────────────────────────────────────────────────────────────

    private void exitSleepMode() {
        exitCalled = true;
        WakeAlarmService.stop(this);

        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();

        ((AudioManager) getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("sleep_active", false)
                .remove(KEY_SLEEP_START)
                .apply();

        getSystemService(NotificationManager.class).cancel(2);
        finish();
    }
}
