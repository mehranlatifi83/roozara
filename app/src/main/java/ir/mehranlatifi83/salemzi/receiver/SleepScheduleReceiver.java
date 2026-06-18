package ir.mehranlatifi83.salemzi.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import ir.mehranlatifi83.salemzi.R;
import ir.mehranlatifi83.salemzi.manager.ScheduleManager;
import ir.mehranlatifi83.salemzi.service.SleepVpnService;
import ir.mehranlatifi83.salemzi.service.WakeAlarmService;
import ir.mehranlatifi83.salemzi.ui.MainActivity;
import ir.mehranlatifi83.salemzi.ui.SleepLockActivity;

public class SleepScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION_SLEEP          = "ir.mehranlatifi83.salemzi.ACTION_SLEEP";
    public static final String ACTION_WAKE           = "ir.mehranlatifi83.salemzi.ACTION_WAKE";
    public static final String ACTION_SLEEP_REMINDER = "ir.mehranlatifi83.salemzi.ACTION_SLEEP_REMINDER";

    private static final String CHANNEL_ID  = "schedule_channel";
    private static final int    NOTIF_SLEEP = 2;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_SLEEP_REMINDER.equals(action)) {
            showSleepReminderNotification(ctx);
            ScheduleManager.scheduleSleepReminderAlarm(ctx);
        } else if (ACTION_SLEEP.equals(action)) {
            activateSleepMode(ctx);
            ScheduleManager.scheduleSleepAlarm(ctx);
        } else if (ACTION_WAKE.equals(action)) {
            deactivateSleepMode(ctx);
            ScheduleManager.scheduleWakeAlarm(ctx);
        }
    }

    private void activateSleepMode(Context ctx) {
        ((AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_SILENT);

        // Only start VPN if the user has already pre-authorized it (prepare returns null
        // when authorized). Without authorization, the tunnel silently fails to establish.
        if (android.net.VpnService.prepare(ctx) == null) {
            ctx.startForegroundService(new Intent(ctx, SleepVpnService.class));
        }

        ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit()
                .putBoolean("sleep_active", true)
                .putLong(ir.mehranlatifi83.salemzi.ui.SleepLockActivity.KEY_SLEEP_START,
                        System.currentTimeMillis())
                .apply();

        boolean canOverlay = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && Settings.canDrawOverlays(ctx);

        if (canOverlay) {
            // Overlay path: show the lock screen directly; no alarm-priority notification
            // so there is no double sound and no competing fullScreenIntent that would
            // restart the activity with FLAG_ACTIVITY_CLEAR_TASK.
            SleepLockActivity.launch(ctx);
        } else {
            // No overlay: fall back to a full-screen-intent notification which is the
            // only reliable way to show an activity over any foreground app on API 29+.
            showSleepNotification(ctx);
        }
    }

    private void deactivateSleepMode(Context ctx) {
        boolean wasActive = ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .getBoolean("sleep_active", false);

        ((AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE))
                .setRingerMode(AudioManager.RINGER_MODE_NORMAL);

        ctx.stopService(new Intent(ctx, SleepVpnService.class));
        SleepVpnService.disconnect();

        ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("sleep_active", false).apply();

        // Only ring the wake alarm if sleep was still active.
        // If the user already exited early, sleep_active=false and we skip the alarm.
        if (wasActive) {
            WakeAlarmService.start(ctx);
        }
    }

    /** Posts a high-priority notification with a full-screen intent that opens the sleep lock
     *  screen. Static so it can also be called from MainActivity for manual sleep activation. */
    public static void showSleepNotification(Context ctx) {
        ensureChannel(ctx);

        PendingIntent lockScreenPi = PendingIntent.getActivity(
                ctx, 10,
                new Intent(ctx, SleepLockActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        ctx.getSystemService(NotificationManager.class)
                .notify(NOTIF_SLEEP, new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setContentTitle(ctx.getString(R.string.notif_sleep_time_title))
                        .setContentText(ctx.getString(R.string.notif_sleep_time_text))
                        .setSmallIcon(R.drawable.ic_moon)
                        .setPriority(NotificationCompat.PRIORITY_MAX)
                        .setCategory(NotificationCompat.CATEGORY_ALARM)
                        .setFullScreenIntent(lockScreenPi, true)
                        .setOngoing(true)
                        .setAutoCancel(false)
                        .build());
    }

    private void showSleepReminderNotification(Context ctx) {
        ensureChannel(ctx);
        PendingIntent openApp = PendingIntent.getActivity(
                ctx, 20,
                new Intent(ctx, MainActivity.class)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        ctx.getSystemService(NotificationManager.class).notify(5,
                new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setContentTitle(ctx.getString(R.string.notif_sleep_reminder_title))
                        .setContentText(ctx.getString(R.string.notif_sleep_reminder_text))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(ctx.getString(R.string.notif_sleep_reminder_text)))
                        .setSmallIcon(R.drawable.ic_moon)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(openApp)
                        .setAutoCancel(true)
                        .build());
    }

    public static void ensureChannel(Context ctx) {
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel(CHANNEL_ID,
                        ctx.getString(R.string.channel_schedule_name),
                        NotificationManager.IMPORTANCE_HIGH));
    }
}
