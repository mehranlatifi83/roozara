package ir.mehranlatifi83.roozara.util;

/**
 * Every notification id the app posts, in one place.
 *
 * They were spread across five classes, twice as duplicate private constants and once as
 * a bare literal. Two of them have to agree across class boundaries — the bedtime
 * notification is posted by the schedule receiver and cancelled by both the controller
 * and the lock screen — and a mismatch there leaves an ongoing "time to sleep" entry in
 * the shade that nothing can remove.
 */
public final class Notifications {

    private Notifications() {}

    /** Foreground notification of the blocking VPN tunnel. */
    public static final int VPN_RUNNING = 1;

    /** Bedtime notification. Posted by the schedule, cancelled by whatever ends the night. */
    public static final int SLEEP = 2;

    /** Foreground notification of the wake alarm. */
    public static final int WAKE_ALARM = 4;

    /** "Bedtime is in fifteen minutes." */
    public static final int SLEEP_REMINDER = 5;

    /** VPN consent is missing, so the internet cannot be blocked tonight. */
    public static final int VPN_PERMISSION_MISSING = 6;

    /** The tunnel could not be established even though consent was granted. */
    public static final int VPN_START_FAILED = 7;

    /** Water reminders: one id per slot, offset so it can never collide with the above. */
    private static final int WATER_BASE = 100;

    public static int water(int slot) {
        return WATER_BASE + slot;
    }
}
