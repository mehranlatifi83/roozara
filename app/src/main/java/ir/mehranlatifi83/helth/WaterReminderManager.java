package ir.mehranlatifi83.helth;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages water reminder scheduling based on meal times.
 *
 * Reminder strategy (research-based):
 *  - 30 min before each meal  → hydrates stomach lining, improves digestion
 *  - 90 min after each meal   → avoids diluting digestive enzymes
 *  - Morning anchor (7:00)    → replenish overnight loss
 *  - Evening anchor (20:00)   → avoid dehydration before sleep
 */
public class WaterReminderManager {

    static final String KEY_BREAKFAST_H = "breakfast_hour";
    static final String KEY_BREAKFAST_M = "breakfast_min";
    static final String KEY_LUNCH_H     = "lunch_hour";
    static final String KEY_LUNCH_M     = "lunch_min";
    static final String KEY_DINNER_H    = "dinner_hour";
    static final String KEY_DINNER_M    = "dinner_min";
    static final String KEY_WATER_ON    = "water_reminders_enabled";

    private static final String PREFS = "helth_prefs";

    // Request codes 200–219 reserved for water reminders
    private static final int REQ_BASE = 200;
    private static final int MAX_SLOTS = 20;

    // ─── Meal time storage ───────────────────────────────────────────────────

    public static void saveBreakfast(Context ctx, int h, int m) {
        prefs(ctx).edit().putInt(KEY_BREAKFAST_H, h).putInt(KEY_BREAKFAST_M, m).apply();
    }

    public static void saveLunch(Context ctx, int h, int m) {
        prefs(ctx).edit().putInt(KEY_LUNCH_H, h).putInt(KEY_LUNCH_M, m).apply();
    }

    public static void saveDinner(Context ctx, int h, int m) {
        prefs(ctx).edit().putInt(KEY_DINNER_H, h).putInt(KEY_DINNER_M, m).apply();
    }

    public static int[] getBreakfast(Context ctx) { return getMeal(ctx, KEY_BREAKFAST_H, KEY_BREAKFAST_M); }
    public static int[] getLunch(Context ctx)      { return getMeal(ctx, KEY_LUNCH_H,     KEY_LUNCH_M);     }
    public static int[] getDinner(Context ctx)     { return getMeal(ctx, KEY_DINNER_H,    KEY_DINNER_M);    }

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_WATER_ON, false);
    }

    public static void setEnabled(Context ctx, boolean on) {
        prefs(ctx).edit().putBoolean(KEY_WATER_ON, on).apply();
        if (on) scheduleAll(ctx);
        else    cancelAll(ctx);
    }

    // ─── Schedule computation ─────────────────────────────────────────────────

    /**
     * Returns all reminder times for the day as [hour, minute, type] triples.
     * type: 0=before breakfast, 1=after breakfast, 2=before lunch, 3=after lunch,
     *       4=before dinner, 5=after dinner, 6=morning anchor, 7=evening anchor
     */
    public static List<int[]> computeReminderTimes(Context ctx) {
        List<int[]> list = new ArrayList<>();

        int[] breakfast = getBreakfast(ctx);
        int[] lunch     = getLunch(ctx);
        int[] dinner    = getDinner(ctx);

        // Morning anchor at 07:00 — only if no breakfast or breakfast is after 8:00
        boolean hasEarlyBreakfast = breakfast != null && (breakfast[0] * 60 + breakfast[1]) <= 8 * 60;
        if (!hasEarlyBreakfast) list.add(new int[]{7, 0, 6});

        if (breakfast != null) {
            addMealReminders(list, breakfast, 0);
        }
        if (lunch != null) {
            addMealReminders(list, lunch, 2);
        }
        if (dinner != null) {
            addMealReminders(list, dinner, 4);
        }

        // Evening anchor at 20:00 — only if no dinner or dinner is before 18:30
        boolean hasLateDinner = dinner != null && (dinner[0] * 60 + dinner[1]) >= 18 * 60 + 30;
        if (!hasLateDinner) list.add(new int[]{20, 0, 7});

        // Sort and deduplicate (remove times within 20 min of each other)
        Collections.sort(list, (a, b) -> (a[0] * 60 + a[1]) - (b[0] * 60 + b[1]));
        return deduplicate(list, 20);
    }

    private static void addMealReminders(List<int[]> list, int[] meal, int typeBase) {
        int totalMin = meal[0] * 60 + meal[1];

        int beforeMin = totalMin - 30;
        if (beforeMin >= 0) {
            list.add(new int[]{beforeMin / 60, beforeMin % 60, typeBase});
        }

        int afterMin = totalMin + 90;
        if (afterMin < 24 * 60) {
            list.add(new int[]{afterMin / 60, afterMin % 60, typeBase + 1});
        }
    }

    private static List<int[]> deduplicate(List<int[]> sorted, int minGapMin) {
        List<int[]> result = new ArrayList<>();
        int lastMin = -minGapMin - 1;
        for (int[] slot : sorted) {
            int m = slot[0] * 60 + slot[1];
            if (m - lastMin >= minGapMin) {
                result.add(slot);
                lastMin = m;
            }
        }
        return result;
    }

    // ─── Alarm scheduling ─────────────────────────────────────────────────────

    public static void scheduleAll(Context ctx) {
        cancelAll(ctx); // Clear previous alarms before rescheduling
        List<int[]> slots = computeReminderTimes(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < slots.size() && i < MAX_SLOTS; i++) {
            int[] slot = slots.get(i);
            long triggerMs = nextTriggerMs(slot[0], slot[1]);
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMs,
                    buildPendingIntent(ctx, i, slot[0], slot[1], slot[2]));
        }
    }

    public static void cancelAll(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < MAX_SLOTS; i++) {
            am.cancel(buildCancelIntent(ctx, i));
        }
    }

    public static boolean canScheduleExact(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return ((AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE)).canScheduleExactAlarms();
        }
        return true;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    static long nextTriggerMs(int hour, int min) {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.HOUR_OF_DAY, hour);
        cal.set(java.util.Calendar.MINUTE, min);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
        }
        return cal.getTimeInMillis();
    }

    private static PendingIntent buildPendingIntent(Context ctx, int slot, int h, int m, int type) {
        Intent i = new Intent(ctx, WaterReminderReceiver.class)
                .setAction(WaterReminderReceiver.ACTION_WATER)
                .putExtra(WaterReminderReceiver.EXTRA_SLOT, slot)
                .putExtra(WaterReminderReceiver.EXTRA_HOUR, h)
                .putExtra(WaterReminderReceiver.EXTRA_MIN, m)
                .putExtra(WaterReminderReceiver.EXTRA_TYPE, type);
        return PendingIntent.getBroadcast(ctx, REQ_BASE + slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent buildCancelIntent(Context ctx, int slot) {
        Intent i = new Intent(ctx, WaterReminderReceiver.class)
                .setAction(WaterReminderReceiver.ACTION_WATER);
        return PendingIntent.getBroadcast(ctx, REQ_BASE + slot, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int[] getMeal(Context ctx, String hKey, String mKey) {
        SharedPreferences p = prefs(ctx);
        int h = p.getInt(hKey, -1);
        if (h == -1) return null;
        return new int[]{h, p.getInt(mKey, 0)};
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
