package ir.mehranlatifi83.helth;

import androidx.appcompat.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import android.content.Intent;
import android.net.VpnService;
import android.os.Bundle;
import android.widget.Switch;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

	private static final int VPN_REQUEST_CODE = 123;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		Switch sleepSwitch = findViewById(R.id.switch_sleep_mode);

		sleepSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
			if (isChecked) {
				Intent intent = VpnService.prepare(this);
				if (intent != null) {
					startActivityForResult(intent, VPN_REQUEST_CODE);
				} else {
					startVpnService();
				}
				muteDevice(); // گوشی رو بی‌صدا کن
			} else {
				stopVpnService();
				unmuteDevice(); // گوشی رو به حالت عادی برگردون
			}
		});
	}

	private void startVpnService() {
		startService(new Intent(this, SleepVpnService.class));
	}

	private void stopVpnService() {
		stopService(new Intent(this, SleepVpnService.class));
		SleepVpnService.disconnect();  // VPN رو واقعا ببند
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == VPN_REQUEST_CODE && resultCode == RESULT_OK) {
			startVpnService();
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	private void muteDevice() {
		NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !notificationManager.isNotificationPolicyAccessGranted()) {
			// اگر اجازه نداره، کاربر رو بفرست به تنظیمات تا دسترسی بده
			showPermissionDialog();
			return;
		}

		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		audioManager.setRingerMode(AudioManager.RINGER_MODE_SILENT);
	}

	private void unmuteDevice() {
		AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
		audioManager.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
	}

	private void showPermissionDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle("نیاز به دسترسی")
				.setMessage("برای فعال کردن حالت سکوت کامل در هنگام خواب، لطفاً دسترسی 'مزاحم نشوید (Do Not Disturb)' را به برنامه بدهید.\n\nبا رفتن به تنظیمات، این دسترسی را فعال کنید.")
				.setPositiveButton("رفتن به تنظیمات", (dialog, which) -> {
					Intent intent = new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS);
					startActivity(intent);
				})
				.setNegativeButton("لغو", (dialog, which) -> {
					dialog.dismiss();
				})
				.setCancelable(false)
				.show();
	}
}
