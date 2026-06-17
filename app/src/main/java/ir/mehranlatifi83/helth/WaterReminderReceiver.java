package ir.mehranlatifi83.helth;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.NotificationCompat;

public class WaterReminderReceiver extends BroadcastReceiver {

    static final String ACTION_WATER = "ir.mehranlatifi83.helth.ACTION_WATER";
    static final String EXTRA_SLOT   = "slot";
    static final String EXTRA_HOUR   = "hour";
    static final String EXTRA_MIN    = "min";
    static final String EXTRA_TYPE   = "type";  // 0–7, see WaterReminderManager

    private static final String CHANNEL_ID = "water_reminder_channel";

    // Notification titles and texts indexed by type
    private static final int[] TITLES = {
        R.string.water_before_breakfast_title,
        R.string.water_after_breakfast_title,
        R.string.water_before_lunch_title,
        R.string.water_after_lunch_title,
        R.string.water_before_dinner_title,
        R.string.water_after_dinner_title,
        R.string.water_morning_title,
        R.string.water_evening_title,
    };

    private static final int[] TEXTS = {
        R.string.water_before_breakfast_text,
        R.string.water_after_breakfast_text,
        R.string.water_before_lunch_text,
        R.string.water_after_lunch_text,
        R.string.water_before_dinner_text,
        R.string.water_after_dinner_text,
        R.string.water_morning_text,
        R.string.water_evening_text,
    };

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!ACTION_WATER.equals(intent.getAction())) return;
        if (!WaterReminderManager.isEnabled(ctx)) return;

        int slot = intent.getIntExtra(EXTRA_SLOT, 0);
        int h    = intent.getIntExtra(EXTRA_HOUR, 0);
        int m    = intent.getIntExtra(EXTRA_MIN,  0);
        int type = intent.getIntExtra(EXTRA_TYPE, 6);

        showNotification(ctx, slot, type);

        // Reschedule same slot for tomorrow
        long tomorrow = WaterReminderManager.nextTriggerMs(h, m);
        android.app.AlarmManager am =
                (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);

        Intent reschedule = new Intent(ctx, WaterReminderReceiver.class)
                .setAction(ACTION_WATER)
                .putExtra(EXTRA_SLOT, slot)
                .putExtra(EXTRA_HOUR, h)
                .putExtra(EXTRA_MIN, m)
                .putExtra(EXTRA_TYPE, type);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 200 + slot, reschedule,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, tomorrow, pi);
    }

    private void showNotification(Context ctx, int slot, int type) {
        ensureChannel(ctx);

        PendingIntent openApp = PendingIntent.getActivity(ctx, 300 + slot,
                new Intent(ctx, WaterActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        int titleRes = (type >= 0 && type < TITLES.length) ? TITLES[type] : R.string.water_morning_title;
        int textRes  = (type >= 0 && type < TEXTS.length)  ? TEXTS[type]  : R.string.water_morning_text;

        ctx.getSystemService(NotificationManager.class).notify(100 + slot,
                new NotificationCompat.Builder(ctx, CHANNEL_ID)
                        .setContentTitle(ctx.getString(titleRes))
                        .setContentText(ctx.getString(textRes))
                        .setStyle(new NotificationCompat.BigTextStyle()
                                .bigText(ctx.getString(textRes)))
                        .setSmallIcon(R.drawable.ic_water)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                        .setContentIntent(openApp)
                        .setAutoCancel(true)
                        .build());
    }

    private void ensureChannel(Context ctx) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                ctx.getString(R.string.water_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
        );
        ctx.getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
}
