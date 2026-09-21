package ir.mehranlatifi83.roozara.receiver;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

import androidx.core.app.NotificationCompat;

import java.util.Calendar;

import ir.mehranlatifi83.roozara.R;
import ir.mehranlatifi83.roozara.util.ActivityLog;
import ir.mehranlatifi83.roozara.util.Notifications;
import ir.mehranlatifi83.roozara.manager.SleepModeController;
import ir.mehranlatifi83.roozara.manager.WaterReminderManager;
import ir.mehranlatifi83.roozara.ui.WaterActivity;
import ir.mehranlatifi83.roozara.ui.WaterOverlayActivity;

public class WaterReminderReceiver extends BroadcastReceiver {

    public  static final String ACTION_WATER = "ir.mehranlatifi83.roozara.ACTION_WATER";
    public  static final String EXTRA_SLOT   = "slot";
    public  static final String EXTRA_HOUR   = "hour";
    public  static final String EXTRA_MIN    = "min";

    private static final String CHANNEL_ID = "water_reminder_channel";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION_WATER.equals(intent.getAction())) return;
        if (!WaterReminderManager.isEnabled(ctx)) {
            ActivityLog.log(ctx, "water reminder skipped", "reason=reminders_switched_off");
            return;
        }
        ActivityLog.log(ctx, "water reminder fired");

        int slot = intent.getIntExtra(EXTRA_SLOT, 0);
        int h    = intent.getIntExtra(EXTRA_HOUR, 0);
        int m    = intent.getIntExtra(EXTRA_MIN,  0);

        // Nothing is shown during the night. A hydration popup at 3am wakes the person
        // it is meant to be looking after, and — because it is an activity of our own —
        // it came up over the lock screen, pushed it into the background and set off the
        // relaunch-and-cover cycle behind it. Tomorrow's reminder is still scheduled
        // below, so the routine picks up again the next day.
        if (SleepModeController.isSleepActive(ctx)) {
            ActivityLog.log(ctx, "water reminder suppressed", "reason=sleep_mode_active");
        } else if (Settings.canDrawOverlays(ctx)) {
            ctx.startActivity(new Intent(ctx, WaterOverlayActivity.class)
                    .putExtra(EXTRA_SLOT, slot)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                            | Intent.FLAG_ACTIVITY_SINGLE_TOP
                            | Intent.FLAG_ACTIVITY_NO_HISTORY));
        } else {
            showNotification(ctx, slot);
        }

        // Reschedule for exactly tomorrow at the same h:m (not relative to "now" to avoid drift).
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, h);
        cal.set(Calendar.MINUTE, m);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, 1);
        long tomorrow = cal.getTimeInMillis();
        android.app.AlarmManager am =
                (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent reschedule = new Intent(ctx, WaterReminderReceiver.class)
                .setAction(ACTION_WATER)
                .putExtra(EXTRA_SLOT, slot)
                .putExtra(EXTRA_HOUR, h)
                .putExtra(EXTRA_MIN,  m);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 200 + slot, reschedule,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, tomorrow, pi);
        } catch (SecurityException ignored) {
            // Exact alarm permission revoked; tomorrow's alarm will not fire
        }
    }

    static void showNotification(Context ctx, int slot) {
        ensureChannel(ctx);

        int safeSlot = WaterReminderManager.safeSlot(slot);

        PendingIntent openApp = PendingIntent.getActivity(ctx, 300 + slot,
                new Intent(ctx, WaterActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        ctx.getSystemService(NotificationManager.class).notify(Notifications.water(safeSlot),
                new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setContentTitle(ctx.getString(WaterReminderManager.TITLES[safeSlot]))
                        .setContentText(ctx.getString(WaterReminderManager.TEXTS[safeSlot]))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(ctx.getString(WaterReminderManager.TEXTS[safeSlot])))
                        .setSmallIcon(R.drawable.ic_water)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(openApp)
                        .setAutoCancel(true)
                        .build());
    }

    private static void ensureChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.water_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
}
