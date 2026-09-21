package ir.mehranlatifi83.roozara.util;

import android.content.Context;

import java.util.Calendar;
import java.util.Locale;

/**
 * The date as the user asked to see it, in one place.
 *
 * Three screens each built this themselves, each with its own copy of the Jalali day and
 * month names — and they did not agree. The main screen and the water screen read the
 * calendar the user picked from the menu; the lock screen looked only at the phone's
 * language. So a Persian speaker who chose the Gregorian calendar got it everywhere
 * except the one screen they stare at all night, and an English speaker who chose Jalali
 * got the opposite. The setting is meant to be independent of the app's language, which
 * is exactly what the lock screen was not honouring.
 */
public final class DateLabel {

    private static final String PREFS           = "helth_prefs";
    private static final String KEY_USE_JALALI  = "use_jalali_calendar";

    /**
     * Sunday first, matching {@link Calendar#DAY_OF_WEEK}, which is 1-based from Sunday.
     * Not translated: these are the Persian names of Persian weekdays, and they are only
     * ever shown beside a Jalali date.
     */
    private static final String[] JALALI_DAYS = {
            "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه",
    };

    private static final String[] JALALI_MONTHS = {
            "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
            "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    };

    private DateLabel() {}

    /**
     * Which calendar to draw. The stored choice wins; with nothing stored, the phone's
     * language decides, so a Persian phone starts on Jalali without being asked.
     */
    public static boolean useJalali(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_USE_JALALI, "fa".equals(Locale.getDefault().getLanguage()));
    }

    public static void setUseJalali(Context ctx, boolean useJalali) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_USE_JALALI, useJalali).apply();
    }

    /** Today, in the user's calendar. */
    public static String today(Context ctx, boolean withWeekday, boolean withYear) {
        return format(ctx, Calendar.getInstance(), withWeekday, withYear);
    }

    /** A given day, in the user's calendar. */
    public static String format(Context ctx, Calendar cal, boolean withWeekday, boolean withYear) {
        return useJalali(ctx)
                ? jalali(cal, withWeekday, withYear)
                : gregorian(cal, withWeekday, withYear);
    }

    private static String jalali(Calendar cal, boolean withWeekday, boolean withYear) {
        int[] j = JalaliCalendar.toJalali(
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH) + 1,
                cal.get(Calendar.DAY_OF_MONTH));

        StringBuilder sb = new StringBuilder();
        if (withWeekday) {
            sb.append(JALALI_DAYS[cal.get(Calendar.DAY_OF_WEEK) - 1]).append("،  ");
        }
        sb.append(j[2]).append(' ').append(JALALI_MONTHS[j[1] - 1]);
        if (withYear) sb.append(' ').append(j[0]);
        return sb.toString();
    }

    private static String gregorian(Calendar cal, boolean withWeekday, boolean withYear) {
        Locale locale = Locale.getDefault();
        StringBuilder sb = new StringBuilder();
        if (withWeekday) {
            sb.append(cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, locale))
                    .append(",  ");
        }
        sb.append(cal.getDisplayName(Calendar.MONTH, Calendar.LONG, locale))
                .append(' ').append(cal.get(Calendar.DAY_OF_MONTH));
        if (withYear) sb.append(", ").append(cal.get(Calendar.YEAR));
        return sb.toString();
    }
}
