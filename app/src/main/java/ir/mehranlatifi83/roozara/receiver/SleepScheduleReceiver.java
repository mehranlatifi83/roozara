package ir.mehranlatifi83.roozara.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import ir.mehranlatifi83.roozara.R;
import ir.mehranlatifi83.roozara.manager.ScheduleManager;
import ir.mehranlatifi83.roozara.manager.SleepModeController;
import ir.mehranlatifi83.roozara.util.ActivityLog;
import ir.mehranlatifi83.roozara.util.Notifications;
import ir.mehranlatifi83.roozara.service.WakeAlarmService;
import ir.mehranlatifi83.roozara.ui.MainActivity;
import ir.mehranlatifi83.roozara.ui.SleepLockActivity;

public class SleepScheduleReceiver extends BroadcastReceiver {

    public static final String ACTION_SLEEP          = "ir.mehranlatifi83.roozara.ACTION_SLEEP";
    public static final String ACTION_WAKE           = "ir.mehranlatifi83.roozara.ACTION_WAKE";
    public static final String ACTION_SLEEP_REMINDER = "ir.mehranlatifi83.roozara.ACTION_SLEEP_REMINDER";

    private static final String CHANNEL_ID  = "schedule_channel";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (ACTION_SLEEP_REMINDER.equals(action)) {
            showSleepReminderNotification(ctx);
            ScheduleManager.scheduleSleepReminderAlarm(ctx);
        } else if (ACTION_SLEEP.equals(action)) {
            activateSleepMode(ctx, "bedtime_alarm");
            ScheduleManager.scheduleSleepAlarm(ctx);
        } else if (ACTION_WAKE.equals(action)) {
            deactivateSleepMode(ctx);
            ScheduleManager.scheduleWakeAlarm(ctx);
        }
    }

    /**
     * Starts the night. Public so the main screen can start one the moment the schedule
     * is switched on inside an already-running sleep window, rather than waiting for the
     * next lifecycle callback to notice.
     */
    public static void activateSleepMode(Context ctx) {
        activateSleepMode(ctx, "bedtime_alarm");
    }

    /**
     * @param trigger why the night is starting. Four different things call this now —
     *                the bedtime alarm, a reboot, the screen coming on, and the switch
     *                being turned on mid-window — and the log used to claim "bedtime
     *                reached" for all of them.
     */
    public static void activateSleepMode(Context ctx, String trigger) {
        ActivityLog.log(ctx, "sleep mode starting", "trigger=" + trigger);

        // Marked active before anything else. The guard service, the VPN watchdog and
        // the screen-on receiver all key off this flag, and starting the lock screen
        // first left a window in which they all believed the night had not begun.
        ctx.getSharedPreferences(SleepModeController.PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(SleepModeController.KEY_SLEEP_ACTIVE, true)
                .putLong(SleepLockActivity.KEY_SLEEP_START, System.currentTimeMillis())
                .apply();

        // Silencing and the internet block both live in the controller, so every path
        // that ends the night undoes exactly what this put in place. It reports whether
        // the internet could actually be blocked, so there is no need to ask the system
        // the same question a second time here.
        if (!SleepModeController.applySystemState(ctx)) {
            showVpnPermissionMissingNotification(ctx);
        }

        boolean canOverlay = Settings.canDrawOverlays(ctx);

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
        boolean wasActive = SleepModeController.isSleepActive(ctx);
        ActivityLog.log(ctx, "wake time reached", "sleep_was_active=" + ActivityLog.yesNo(wasActive));

        // When a challenge is still to come, the ringer restore is deliberately left to
        // whoever ends it. Restoring it here and then forcing NORMAL for the alarm threw
        // the remembered pre-sleep mode away, so a phone kept on vibrate was handed back
        // with the ringer switched on.
        SleepModeController.releaseSystemState(ctx, "wake_time", !wasActive);

        if (wasActive) {
            // The alarm has to be audible even if the phone was on silent before bedtime.
            SleepModeController.unsilenceForAlarm(ctx);
            // Only ring the wake alarm if the lock screen is not already the visible
            // foreground activity, which handles the challenge inline and restores the
            // ringer itself once it is passed.
            if (!SleepLockActivity.isActivityInForeground()) {
                WakeAlarmService.start(ctx);
            }
        }
    }

    private static void showVpnPermissionMissingNotification(Context ctx) {
        ensureChannel(ctx);
        PendingIntent openApp = PendingIntent.getActivity(ctx, 30,
                new Intent(ctx, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        ctx.getSystemService(NotificationManager.class).notify(Notifications.VPN_PERMISSION_MISSING,
                new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setContentTitle(ctx.getString(R.string.vpn_permission_missing_title))
                        .setContentText(ctx.getString(R.string.vpn_permission_missing_text))
                        .setSmallIcon(R.drawable.ic_moon)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setContentIntent(openApp)
                        .setAutoCancel(true)
                        .build());
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
                .notify(Notifications.SLEEP, new NotificationCompat.Builder(ctx, CHANNEL_ID)
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

        ctx.getSystemService(NotificationManager.class).notify(Notifications.SLEEP_REMINDER,
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
