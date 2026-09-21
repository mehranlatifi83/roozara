package ir.mehranlatifi83.roozara;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

import ir.mehranlatifi83.roozara.util.JalaliCalendar;

/**
 * The lock screen shows the date all night, so a conversion that is a day out is visible
 * to the one person who cannot do anything about it. The cases here are the ones the
 * arithmetic can actually get wrong: year boundaries, both leap rules, and the month
 * lengths changing from 31 to 30 to 29.
 */
public class JalaliCalendarTest {

    private static void assertJalali(int gy, int gm, int gd, int jy, int jm, int jd) {
        assertArrayEquals(new int[]{jy, jm, jd}, JalaliCalendar.toJalali(gy, gm, gd));
    }

    // ─── Year boundaries ─────────────────────────────────────────────────────

    /** Nowruz: the first day of the Jalali year. */
    @Test
    public void nowruz() {
        assertJalali(2024, 3, 20, 1403, 1, 1);
        assertJalali(2025, 3, 21, 1404, 1, 1);
        assertJalali(2026, 3, 21, 1405, 1, 1);
    }

    /** The last day of the year, immediately before each of those. */
    @Test
    public void lastDayOfTheJalaliYear() {
        assertJalali(2024, 3, 19, 1402, 12, 29);
        assertJalali(2025, 3, 20, 1403, 12, 30);
        assertJalali(2026, 3, 20, 1404, 12, 29);
    }

    /** The Gregorian year rolls over in the middle of Dey, which must not disturb it. */
    @Test
    public void gregorianNewYearIsMidJalaliMonth() {
        assertJalali(2024, 12, 31, 1403, 10, 11);
        assertJalali(2025, 1, 1, 1403, 10, 12);
    }

    // ─── Leap years ──────────────────────────────────────────────────────────

    /** 1403 is a Jalali leap year, so Esfand has 30 days rather than 29. */
    @Test
    public void jalaliLeapYearHasThirtyDaysInEsfand() {
        assertJalali(2025, 3, 19, 1403, 12, 29);
        assertJalali(2025, 3, 20, 1403, 12, 30);
    }

    /** 29 February only exists in a Gregorian leap year, and must not shift the result. */
    @Test
    public void gregorianLeapDay() {
        assertJalali(2024, 2, 28, 1402, 12, 9);
        assertJalali(2024, 2, 29, 1402, 12, 10);
        assertJalali(2024, 3, 1, 1402, 12, 11);
    }

    /** 2000 was a leap year under the 400-year rule; 1900 was not. */
    @Test
    public void centuryLeapRules() {
        assertJalali(2000, 2, 29, 1378, 12, 10);
        assertJalali(1900, 3, 1, 1278, 12, 10);
    }

    // ─── Month lengths ───────────────────────────────────────────────────────

    /** The first six months have 31 days; the seventh starts the 30-day half. */
    @Test
    public void firstHalfOfTheYearHasThirtyOneDayMonths() {
        assertJalali(2024, 6, 20, 1403, 3, 31);
        assertJalali(2024, 6, 21, 1403, 4, 1);
        assertJalali(2024, 9, 21, 1403, 6, 31);
        assertJalali(2024, 9, 22, 1403, 7, 1);
    }

    /** Each day of a month boundary in the 30-day half. */
    @Test
    public void secondHalfOfTheYearHasThirtyDayMonths() {
        assertJalali(2024, 10, 21, 1403, 7, 30);
        assertJalali(2024, 10, 22, 1403, 8, 1);
    }
}
