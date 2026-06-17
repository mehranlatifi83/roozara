package ir.mehranlatifi83.helth;

import android.Manifest;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.media.AudioManager;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.util.Calendar;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS = "helth_prefs";
    private static final String KEY_SLEEP_ACTIVE = "sleep_active";

    private MaterialButton btnToggle;
    private TextView textStatus;
    private MaterialCardView cardCircle;
    private ImageView iconSleep;
    private boolean isSleepActive = false;

    private final ActivityResultLauncher<Intent> vpnLauncher = registerForActivityResult(
        new ActivityResultContracts.StartActivityForResult(),
        result -> {
            if (result.getResultCode() == RESULT_OK) {
                startSleepMode();
            }
        }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btn_toggle);
        textStatus = findViewById(R.id.text_status);
        cardCircle = findViewById(R.id.card_circle);
        iconSleep = findViewById(R.id.icon_sleep);
        TextView textDate = findViewById(R.id.text_date);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);

        textDate.setText(getPersianDate());
        bottomNav.setSelectedItemId(R.id.nav_sleep);

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        isSleepActive = prefs.getBoolean(KEY_SLEEP_ACTIVE, false);
        updateUI();

        btnToggle.setOnClickListener(v -> toggleSleepMode());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        }
    }

    private void toggleSleepMode() {
        if (isSleepActive) {
            stopSleepMode();
        } else {
            Intent vpnIntent = VpnService.prepare(this);
            if (vpnIntent != null) {
                vpnLauncher.launch(vpnIntent);
            } else {
                startSleepMode();
            }
        }
    }

    private void startSleepMode() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !nm.isNotificationPolicyAccessGranted()) {
            showDndPermissionDialog();
            return;
        }
        startService(new Intent(this, SleepVpnService.class));
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        isSleepActive = true;
        saveState();
        updateUI();
    }

    private void stopSleepMode() {
        stopService(new Intent(this, SleepVpnService.class));
        SleepVpnService.disconnect();
        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        isSleepActive = false;
        saveState();
        updateUI();
    }

    private void updateUI() {
        int primaryColor = ContextCompat.getColor(this, R.color.colorPrimary);
        int variantColor = ContextCompat.getColor(this, R.color.colorOnSurfaceVariant);
        int surfaceColor = ContextCompat.getColor(this, R.color.colorSurface);

        if (isSleepActive) {
            textStatus.setText(R.string.status_active);
            textStatus.setTextColor(primaryColor);
            btnToggle.setText(R.string.btn_disable_sleep);
            cardCircle.setStrokeColor(primaryColor);
            iconSleep.setImageTintList(ColorStateList.valueOf(primaryColor));
        } else {
            textStatus.setText(R.string.status_inactive);
            textStatus.setTextColor(variantColor);
            btnToggle.setText(R.string.btn_enable_sleep);
            cardCircle.setStrokeColor(surfaceColor);
            iconSleep.setImageTintList(ColorStateList.valueOf(variantColor));
        }
    }

    private void saveState() {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SLEEP_ACTIVE, isSleepActive)
            .apply();
    }

    private void showDndPermissionDialog() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.dnd_dialog_title)
            .setMessage(R.string.dnd_dialog_message)
            .setPositiveButton(R.string.go_to_settings, (d, w) ->
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)))
            .setNegativeButton(R.string.cancel, null)
            .setCancelable(false)
            .show();
    }

    private String getPersianDate() {
        Calendar cal = Calendar.getInstance();
        String[] persianDays = {"یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه"};
        String[] persianMonths = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
                                   "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
        String dayName = persianDays[cal.get(Calendar.DAY_OF_WEEK) - 1];
        int[] jalali = JalaliCalendar.toJalali(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH)
        );
        return dayName + "،  " + jalali[2] + " " + persianMonths[jalali[1] - 1] + " " + jalali[0];
    }
}
