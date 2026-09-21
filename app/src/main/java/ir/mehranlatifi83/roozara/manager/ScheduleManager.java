package ir.mehranlatifi83.roozara.manager;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import ir.mehranlatifi83.roozara.receiver.ScreenStateReceiver;
import ir.mehranlatifi83.roozara.receiver.SleepScheduleReceiver;

import ir.mehranlatifi83.roozara.util.ActivityLog;

import java.util.Calendar;
import java.util.Locale;

public class ScheduleManager {

    static final String KEY_SLEEP_HOUR      = "sleep_hour";
    static final String KEY_SLEEP_MIN       = "sleep_min";
    static final String KEY_WAKE_HOUR       = "wake_hour";
    static final String KEY_WAKE_MIN        = "wake_min";
    static final String KEY_SCHEDULE_ENABLED = "schedule_enabled";

    private static final String PREFS       = "helth_prefs";
    private static final int    REQ_SLEEP   = 100;
    private static final int    REQ_WAKE    = 101;
    private static final int    REQ_REMINDER = 102;

    private static final long   REMINDER_LEAD_MS = 15 * 60 * 1000L;
    private static final long   DAY_MS           = 24 * 60 * 60 * 1000L;

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
            // The screen watcher lives exactly as long as the schedule does. Tying it to
            // any one component meant switching that component off silently disabled the
            // instant re-lock for every other component that depended on it.
            ScreenStateReceiver.register(ctx);
        } else {
            cancelAlarms(ctx);
            ScreenStateReceiver.unregister(ctx);
        }
    }

    /** Rebuilds all alarms after reboot, app update, clock/time-zone changes or permission grant. */
    public static void rescheduleIfEnabled(Context ctx) {
        if (!isScheduleEnabled(ctx) || !canScheduleExact(ctx)) return;
        ScreenStateReceiver.register(ctx);
        scheduleSleepAlarm(ctx);
        scheduleWakeAlarm(ctx);
        scheduleSleepReminderAlarm(ctx);
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
        // Fifteen minutes before bedtime, anchored to the bedtime alarm rather than
        // computed on its own. A bedtime just after midnight wrapped to 23:45, and
        // because the next 23:45 is later today, the reminder landed *after* the bedtime
        // it was supposed to announce.
        long reminderAt = nextTriggerMs(t[0], t[1]) - REMINDER_LEAD_MS;
        if (reminderAt <= System.currentTimeMillis()) {
            // Bedtime is less than fifteen minutes away. Announcing it now would be
            // noise on top of the lock screen that is about to appear.
            reminderAt += DAY_MS;
        }
        setAlarm(ctx, reminderAt, SleepScheduleReceiver.ACTION_SLEEP_REMINDER, REQ_REMINDER);
    }

    public static void cancelAlarms(Context ctx) {
        ActivityLog.log(ctx, "schedule alarms cancelled");
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

    /** True when the current local time is inside the configured bedtime window. */
    public static boolean isInsideSleepWindow(Context ctx) {
        int[] sleep = getSleepTime(ctx);
        int[] wake = getWakeTime(ctx);
        if (sleep == null || wake == null) return false;
        Calendar now = Calendar.getInstance();
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        return isInsideWindow(current, sleep[0] * 60 + sleep[1], wake[0] * 60 + wake[1]);
    }

    /**
     * The window test with the clock taken out, so it can be tested directly.
     *
     * All three arguments are minutes past local midnight. The window is half-open —
     * bedtime is inside it, wake time is not — which is what stops the wake alarm and
     * the "should we be asleep?" check disagreeing at the exact minute they meet.
     * A window that does not wrap past midnight is the simple case; one that does is
     * every ordinary bedtime, so it is the one that has to be right.
     */
    public static boolean isInsideWindow(int currentMin, int startMin, int endMin) {
        if (startMin == endMin) return false;   // Zero-length window: never inside.
        return startMin < endMin
                ? currentMin >= startMin && currentMin < endMin
                : currentMin >= startMin || currentMin < endMin;
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

    /**
     * Install one alarm, and say so.
     *
     * Every alarm the app depends on goes through here, and until now none of them left
     * any trace. "The alarm did not ring" could not be told apart from "the alarm was
     * never set", which is the first question anyone would ask of the log.
     */
    private static void setAlarm(Context ctx, long triggerMs, String action, int reqCode) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        // Exact-alarm access can be revoked at any time. Never crash a receiver or leave
        // only part of the schedule installed when that happens.
        if (!canScheduleExact(ctx)) {
            ActivityLog.log(ctx, alarmName(action) + " alarm NOT set",
                    "reason=no_exact_alarm_permission");
            return;
        }
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs,
                    pendingIntent(ctx, action, reqCode));
            ActivityLog.log(ctx, alarmName(action) + " alarm set",
                    "for=" + clockAndDelay(triggerMs));
        } catch (Exception e) {
            // A SecurityException here means the permission went away between the check
            // above and this call. Anything else is an OEM alarm quota.
            ActivityLog.log(ctx, alarmName(action) + " alarm NOT set",
                    "error=" + e.getClass().getSimpleName());
        }
    }

    /** The alarm's name as a person would say it, for the log. */
    private static String alarmName(String action) {
        if (SleepScheduleReceiver.ACTION_SLEEP.equals(action))    return "bedtime";
        if (SleepScheduleReceiver.ACTION_WAKE.equals(action))     return "wake";
        if (SleepScheduleReceiver.ACTION_SLEEP_REMINDER.equals(action)) return "bedtime reminder";
        return "schedule";
    }

    /**
     * "23:00 (in 5h 12m)" — the wall-clock time the alarm will fire and how far away
     * that is. The clock time alone cannot show that an alarm was set for the wrong day,
     * and the delay alone cannot be checked against what the user configured.
     */
    private static String clockAndDelay(long triggerMs) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(triggerMs);
        long minutes = Math.max(0, (triggerMs - System.currentTimeMillis()) / 60000L);
        return String.format(Locale.US, "%02d:%02d (in %dh %02dm)",
                cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE),
                minutes / 60, minutes % 60);
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
