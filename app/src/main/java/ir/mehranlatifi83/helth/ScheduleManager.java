package ir.mehranlatifi83.helth;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.Calendar;

public class ScheduleManager {

    static final String KEY_SLEEP_HOUR = "sleep_hour";
    static final String KEY_SLEEP_MIN  = "sleep_min";
    static final String KEY_WAKE_HOUR  = "wake_hour";
    static final String KEY_WAKE_MIN   = "wake_min";
    static final String KEY_SCHEDULE_ENABLED = "schedule_enabled";
    private static final String PREFS  = "helth_prefs";

    private static final int REQ_SLEEP     = 100;
    private static final int REQ_WAKE      = 101;
    private static final int REQ_REMINDER  = 102;

    public static void saveSleepTime(Context ctx, int hour, int min) {
        prefs(ctx).edit().putInt(KEY_SLEEP_HOUR, hour).putInt(KEY_SLEEP_MIN, min).apply();
    }

    public static void saveWakeTime(Context ctx, int hour, int min) {
        prefs(ctx).edit().putInt(KEY_WAKE_HOUR, hour).putInt(KEY_WAKE_MIN, min).apply();
    }

    public static int[] getSleepTime(Context ctx) {
        SharedPreferences p = prefs(ctx);
        int h = p.getInt(KEY_SLEEP_HOUR, -1);
        if (h == -1) return null;
        return new int[]{h, p.getInt(KEY_SLEEP_MIN, 0)};
    }

    public static int[] getWakeTime(Context ctx) {
        SharedPreferences p = prefs(ctx);
        int h = p.getInt(KEY_WAKE_HOUR, -1);
        if (h == -1) return null;
        return new int[]{h, p.getInt(KEY_WAKE_MIN, 0)};
    }

    public static boolean isScheduleEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SCHEDULE_ENABLED, false);
    }

    public static void setScheduleEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply();
        if (enabled) {
            scheduleSleepAlarm(ctx);
            scheduleWakeAlarm(ctx);
            scheduleSleepReminderAlarm(ctx);
        } else {
            cancelAlarms(ctx);
        }
    }

    public static void scheduleSleepAlarm(Context ctx) {
        int[] t = getSleepTime(ctx);
        if (t == null) return;
        setAlarm(ctx, nextTriggerMs(t[0], t[1]), SleepScheduleReceiver.ACTION_SLEEP, REQ_SLEEP);
    }

    public static void scheduleWakeAlarm(Context ctx) {
        int[] t = getWakeTime(ctx);
        if (t == null) return;
        setAlarm(ctx, nextTriggerMs(t[0], t[1]), SleepScheduleReceiver.ACTION_WAKE, REQ_WAKE);
    }

    public static void scheduleSleepReminderAlarm(Context ctx) {
        int[] t = getSleepTime(ctx);
        if (t == null) return;
        // Fire 15 minutes before sleep time
        int totalMin = t[0] * 60 + t[1] - 15;
        if (totalMin < 0) totalMin += 24 * 60;
        setAlarm(ctx, nextTriggerMs(totalMin / 60, totalMin % 60),
                SleepScheduleReceiver.ACTION_SLEEP_REMINDER, REQ_REMINDER);
    }

    public static void cancelAlarms(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingIntent(ctx, SleepScheduleReceiver.ACTION_SLEEP, REQ_SLEEP));
        am.cancel(pendingIntent(ctx, SleepScheduleReceiver.ACTION_WAKE, REQ_WAKE));
        am.cancel(pendingIntent(ctx, SleepScheduleReceiver.ACTION_SLEEP_REMINDER, REQ_REMINDER));
    }

    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am.canScheduleExactAlarms();
        }
        return true;
    }

    public static boolean hasSchedule(Context ctx) {
        return getSleepTime(ctx) != null && getWakeTime(ctx) != null;
    }

    private static long nextTriggerMs(int hour, int min) {
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

    private static void setAlarm(Context ctx, long triggerMs, String action, int reqCode) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs,
                pendingIntent(ctx, action, reqCode));
    }

    private static PendingIntent pendingIntent(Context ctx, String action, int reqCode) {
        Intent i = new Intent(ctx, SleepScheduleReceiver.class);
        i.setAction(action);
        return PendingIntent.getBroadcast(ctx, reqCode, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
