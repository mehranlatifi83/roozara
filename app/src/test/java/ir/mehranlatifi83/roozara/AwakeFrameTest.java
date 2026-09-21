package ir.mehranlatifi83.roozara;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ir.mehranlatifi83.roozara.manager.WaterReminderManager;

/**
 * Meal times have to be expressed in the same frame as an awake window that runs past
 * midnight, or the blocked interval around a meal is subtracted from nothing and a water
 * reminder lands in the middle of that meal.
 */
public class AwakeFrameTest {

    private static int at(int hour, int min) {
        return hour * 60 + min;
    }

    private static final int DAY = 24 * 60;

    /** A meal already inside the window is left exactly where it is. */
    @Test
    public void timeInsideTheWindowIsUnchanged() {
        int wake = at(7, 0);
        assertEquals(at(8, 0), WaterReminderManager.toAwakeFrame(at(8, 0), wake));
        assertEquals(at(13, 30), WaterReminderManager.toAwakeFrame(at(13, 30), wake));
        assertEquals(at(21, 0), WaterReminderManager.toAwakeFrame(at(21, 0), wake));
    }

    /** The start of the window maps to itself, never to a day later. */
    @Test
    public void windowStartMapsToItself() {
        assertEquals(at(7, 0), WaterReminderManager.toAwakeFrame(at(7, 0), at(7, 0)));
    }

    /**
     * The case that was broken: waking at 07:00 with a meal after midnight. 00:30 is
     * 17.5 hours *after* waking, not 6.5 hours before it.
     */
    @Test
    public void mealAfterMidnightLandsInTheFollowingDay() {
        int wake = at(7, 0);
        assertEquals(at(0, 30) + DAY, WaterReminderManager.toAwakeFrame(at(0, 30), wake));
        assertEquals(at(1, 0) + DAY, WaterReminderManager.toAwakeFrame(at(1, 0), wake));
        assertEquals(at(6, 59) + DAY, WaterReminderManager.toAwakeFrame(at(6, 59), wake));
    }

    /** A late waker: anything before 13:00 belongs to the next day's frame. */
    @Test
    public void lateWakeTimePushesMorningMealsForward() {
        int wake = at(13, 0);
        assertEquals(at(9, 0) + DAY, WaterReminderManager.toAwakeFrame(at(9, 0), wake));
        assertEquals(at(14, 0), WaterReminderManager.toAwakeFrame(at(14, 0), wake));
    }

    /** Whatever the input, the result is always one day's worth at or after the start. */
    @Test
    public void resultIsAlwaysWithinOneDayOfTheStart() {
        for (int wake = 0; wake < DAY; wake += 37) {
            for (int meal = 0; meal < DAY; meal += 13) {
                int framed = WaterReminderManager.toAwakeFrame(meal, wake);
                assertTrue("framed=" + framed + " wake=" + wake,
                        framed >= wake && framed < wake + DAY);
                assertEquals("same time of day", meal, framed % DAY);
            }
        }
    }

    /** Midnight itself is not a special case. */
    @Test
    public void midnight() {
        assertEquals(DAY, WaterReminderManager.toAwakeFrame(0, at(7, 0)));
        assertEquals(0, WaterReminderManager.toAwakeFrame(0, 0));
    }
}
