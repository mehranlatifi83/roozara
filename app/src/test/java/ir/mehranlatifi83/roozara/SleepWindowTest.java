package ir.mehranlatifi83.roozara;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import ir.mehranlatifi83.roozara.manager.ScheduleManager;

/**
 * The sleep window decides whether the phone should be locked right now, and almost
 * every bedtime wraps past midnight — so the wrapping case is the one that matters.
 */
public class SleepWindowTest {

    private static int at(int hour, int min) {
        return hour * 60 + min;
    }

    private static final int BEDTIME = at(23, 0);
    private static final int WAKE    = at(7, 0);

    // ─── Windows that wrap past midnight (the ordinary case) ─────────────────

    @Test
    public void bedtimeItselfIsInsideTheWindow() {
        assertTrue(ScheduleManager.isInsideWindow(at(23, 0), BEDTIME, WAKE));
    }

    @Test
    public void lateEveningIsInsideTheWindow() {
        assertTrue(ScheduleManager.isInsideWindow(at(23, 59), BEDTIME, WAKE));
    }

    @Test
    public void afterMidnightIsInsideTheWindow() {
        assertTrue(ScheduleManager.isInsideWindow(at(0, 0), BEDTIME, WAKE));
        assertTrue(ScheduleManager.isInsideWindow(at(3, 30), BEDTIME, WAKE));
        assertTrue(ScheduleManager.isInsideWindow(at(6, 59), BEDTIME, WAKE));
    }

    /**
     * The window is half-open. At the exact wake minute the night is over, so the lock
     * screen must not be put back by whatever checks the window a moment after the wake
     * alarm has already ended it.
     */
    @Test
    public void wakeMinuteIsOutsideTheWindow() {
        assertFalse(ScheduleManager.isInsideWindow(at(7, 0), BEDTIME, WAKE));
    }

    @Test
    public void daytimeIsOutsideTheWindow() {
        assertFalse(ScheduleManager.isInsideWindow(at(7, 1), BEDTIME, WAKE));
        assertFalse(ScheduleManager.isInsideWindow(at(13, 0), BEDTIME, WAKE));
        assertFalse(ScheduleManager.isInsideWindow(at(22, 59), BEDTIME, WAKE));
    }

    // ─── Windows that do not wrap (a daytime nap) ────────────────────────────

    @Test
    public void nonWrappingWindowIncludesItsStartAndExcludesItsEnd() {
        int start = at(13, 0);
        int end   = at(15, 0);
        assertTrue(ScheduleManager.isInsideWindow(at(13, 0), start, end));
        assertTrue(ScheduleManager.isInsideWindow(at(14, 30), start, end));
        assertFalse(ScheduleManager.isInsideWindow(at(15, 0), start, end));
        assertFalse(ScheduleManager.isInsideWindow(at(12, 59), start, end));
        assertFalse(ScheduleManager.isInsideWindow(at(23, 0), start, end));
    }

    // ─── Degenerate input ────────────────────────────────────────────────────

    /**
     * Bedtime and wake time set to the same minute is a window of zero length, not a
     * window of twenty-four hours. Treating it as the latter would lock the phone
     * permanently with no wake alarm able to end it.
     */
    @Test
    public void equalBedtimeAndWakeTimeIsNeverInside() {
        int same = at(23, 0);
        assertFalse(ScheduleManager.isInsideWindow(same, same, same));
        assertFalse(ScheduleManager.isInsideWindow(at(3, 0), same, same));
        assertFalse(ScheduleManager.isInsideWindow(at(12, 0), same, same));
    }

    /** A one-minute window still behaves like every other window. */
    @Test
    public void oneMinuteWindowContainsOnlyItsStart() {
        int start = at(23, 0);
        int end   = at(23, 1);
        assertTrue(ScheduleManager.isInsideWindow(start, start, end));
        assertFalse(ScheduleManager.isInsideWindow(end, start, end));
    }

    /** A window that starts at midnight does not wrap, despite looking like it might. */
    @Test
    public void windowStartingAtMidnight() {
        int start = at(0, 0);
        int end   = at(6, 0);
        assertTrue(ScheduleManager.isInsideWindow(at(0, 0), start, end));
        assertTrue(ScheduleManager.isInsideWindow(at(5, 59), start, end));
        assertFalse(ScheduleManager.isInsideWindow(at(6, 0), start, end));
        assertFalse(ScheduleManager.isInsideWindow(at(23, 59), start, end));
    }

    /** A window ending at midnight wraps by the arithmetic, and must still be right. */
    @Test
    public void windowEndingAtMidnight() {
        int start = at(22, 0);
        int end   = at(0, 0);
        assertTrue(ScheduleManager.isInsideWindow(at(22, 0), start, end));
        assertTrue(ScheduleManager.isInsideWindow(at(23, 59), start, end));
        assertFalse(ScheduleManager.isInsideWindow(at(0, 0), start, end));
        assertFalse(ScheduleManager.isInsideWindow(at(12, 0), start, end));
    }
}
