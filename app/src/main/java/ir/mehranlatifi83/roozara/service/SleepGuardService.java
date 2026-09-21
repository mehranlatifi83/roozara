package ir.mehranlatifi83.roozara.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;

import java.util.Locale;

import ir.mehranlatifi83.roozara.manager.SleepModeController;
import ir.mehranlatifi83.roozara.receiver.ScreenStateReceiver;
import ir.mehranlatifi83.roozara.ui.SleepLockActivity;
import ir.mehranlatifi83.roozara.ui.SleepOverlayGuard;
import ir.mehranlatifi83.roozara.util.ActivityLog;

/**
 * Keeps the phone unusable while sleep mode is running.
 *
 * The overlay covers the screen and the activity relaunches itself, but neither can
 * close the last gap: no ordinary app may cover the status bar, so the notification
 * shade can still be pulled down, and from there Settings and "force stop" are two taps
 * away. An accessibility service is the only mechanism Android gives a normal app that
 * can see what came to the foreground and react to it.
 *
 * What it does, and nothing more:
 *   - While sleep mode is active, anything that is not Roozara brings the lock screen
 *     straight back.
 *   - The notification shade is collapsed as soon as it opens.
 *   - Settings is treated the same as any other app, which is what closes the
 *     force-stop route.
 *
 * What it deliberately does not do: it never reads screen content, never touches
 * TalkBack, and never consumes a gesture or a key. Several accessibility services run
 * side by side on Android, so a blind user keeps full control of their screen reader
 * while this is active. It is switched off entirely outside the sleep window.
 */
public class SleepGuardService extends AccessibilityService {

    private static final String SYSTEM_UI = "com.android.systemui";

    /**
     * Never bounced away from, whatever the hour.
     *
     * A phone that cannot be answered is not a sleep aid, it is a hazard. Anything to
     * do with a call in progress — the dialer, the in-call screen, the emergency
     * dialer — is left alone entirely. Sleep mode is a commitment device about habits,
     * and it has no business standing between someone and a phone call at 3am.
     */
    private static final String[] CALL_PACKAGES = {
            "dialer", "incallui", "telecom", "telephony", "emergency",
    };

    /**
     * Bounces are rate-limited, but only just.
     *
     * The limit exists so that a burst of window events from one transition does not
     * turn into a relaunch loop — not to give the user a grace period. At 400ms a
     * deliberate swipe to another app stayed on screen long enough to tap something, so
     * it is now short enough to feel immediate and still long enough to coalesce a
     * single transition's events.
     */
    private static final long MIN_BOUNCE_INTERVAL_MS = 120;

    /**
     * System panels are rate-limited separately.
     *
     * They shared a budget with app bounces, so pulling the shade down used it up and
     * an app opened immediately afterwards was let through untouched.
     */
    private static final long MIN_PANEL_INTERVAL_MS = 120;

    private static boolean running = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private long lastBounce = 0;
    private long lastPanelClose = 0;

    // ─── Availability ────────────────────────────────────────────────────────

    /** True when the user has switched the service on in Accessibility settings. */
    public static boolean isEnabled(Context ctx) {
        String enabled = Settings.Secure.getString(
                ctx.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (TextUtils.isEmpty(enabled)) return false;

        String target = new ComponentName(ctx, SleepGuardService.class).flattenToString();
        String shortTarget = ctx.getPackageName() + "/" + SleepGuardService.class.getSimpleName();
        for (String part : enabled.split(":")) {
            if (part.equalsIgnoreCase(target) || part.equalsIgnoreCase(shortTarget)) return true;
        }
        return false;
    }

    /** True when the service is not merely enabled but actually connected and running. */
    public static boolean isRunning() {
        return running;
    }

    public static android.content.Intent settingsIntent() {
        return new android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        running = true;

        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        // WINDOWS_CHANGED as well as WINDOW_STATE_CHANGED: some launchers and some
        // system panels come forward without a state change, and those were the routes
        // that stayed open.
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                | AccessibilityEvent.TYPE_WINDOWS_CHANGED;
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        // No content retrieval: this only needs to know which package is in front, and
        // asking for less is the right default for a permission this powerful.
        info.flags = AccessibilityServiceInfo.DEFAULT;
        info.notificationTimeout = 100;
        setServiceInfo(info);

        // Registered, never unregistered from here. The VPN service and the boot
        // receiver rely on the same watcher, and tearing it down when this service is
        // switched off took the instant re-lock away from them too. Its lifetime belongs
        // to the schedule, which is what ScheduleManager now owns.
        ScreenStateReceiver.register(this);

        ActivityLog.log(this, "sleep guard connected");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        running = false;
        ActivityLog.log(this, "sleep guard disconnected");
        return super.onUnbind(intent);
    }

    /** True for the dialer, the in-call screen and the emergency dialer. */
    private static boolean isCallRelated(String packageName) {
        String lower = packageName.toLowerCase(Locale.ROOT);
        for (String marker : CALL_PACKAGES) {
            if (lower.contains(marker)) return true;
        }
        return false;
    }

    /**
     * True while a call is ringing or connected.
     *
     * Checked as well as the package name because dialers are vendor-specific and the
     * name check cannot possibly cover every ROM. The audio mode is the same on all of
     * them, so this catches the ones the list misses.
     */
    private boolean isCallInProgress() {
        android.media.AudioManager audio =
                (android.media.AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audio == null) return false;
        int mode = audio.getMode();
        return mode == android.media.AudioManager.MODE_IN_CALL
                || mode == android.media.AudioManager.MODE_IN_COMMUNICATION
                || mode == android.media.AudioManager.MODE_RINGTONE;
    }

    @Override
    public void onInterrupt() {
        // Required by the platform. Nothing to interrupt: this service produces no
        // feedback of its own.
    }

    // ─── The guard ───────────────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        int type = event.getEventType();
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && type != AccessibilityEvent.TYPE_WINDOWS_CHANGED) {
            return;
        }
        // Only ever active during the sleep window. Outside it this service watches
        // window changes and does nothing at all with them.
        if (!SleepModeController.isSleepActive(this)) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String packageName = pkg.toString();

        if (isCallRelated(packageName) || isCallInProgress()) {
            // Deliberately not even logged as a bounce that was skipped: this is not a
            // near miss, it is the guard correctly staying out of the way.
            return;
        }

        if (packageName.equals(getPackageName())) {
            // Our own package is not automatically fine. The lock screen is; anything
            // else of ours — the main screen reached from a notification, the water
            // reminder popup — is just another window covering it, and leaving those
            // alone was a way out of the lock screen that happened to be in-app.
            //
            // Only WINDOW_STATE_CHANGED is trusted to make that distinction. A
            // WINDOWS_CHANGED event carries no activity class name, and the overlay this
            // service raises itself produces one — so acting on it meant bouncing
            // endlessly against our own cover.
            if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
            if (isLockScreen(event)) return;
            bounce("own screen opened during sleep - returning to the lock screen",
                    String.valueOf(event.getClassName()));
            return;
        }

        if (packageName.equals(SYSTEM_UI)) {
            long now = System.currentTimeMillis();
            if (now - lastPanelClose < MIN_PANEL_INTERVAL_MS) return;
            lastPanelClose = now;
            // The notification shade, the power menu, or the volume panel. Back closes
            // all three without disturbing anything else. The lock screen is brought
            // back as well: on several ROMs closing the shade reveals whatever was
            // behind it rather than returning to the activity that was in front.
            boolean closed = performGlobalAction(GLOBAL_ACTION_BACK);
            // The action is refused while the device is locked or another service holds
            // the gesture. Recording it as a success made the log claim the shade had
            // been closed on exactly the nights it had not.
            ActivityLog.log(this, closed
                            ? "system panel closed during sleep"
                            : "system panel could NOT be closed during sleep",
                    "action=global_back");
            handler.post(() -> SleepLockActivity.launch(this));
            return;
        }

        bounce("app opened during sleep - returning to the lock screen", packageName);
    }

    /** True when the window that just came forward is the lock screen itself. */
    private boolean isLockScreen(AccessibilityEvent event) {
        CharSequence cls = event.getClassName();
        return cls != null && SleepLockActivity.class.getName().contentEquals(cls);
    }

    /**
     * Cover the screen now, bring the lock screen back a moment later.
     *
     * The overlay goes up synchronously because it is a window and needs no activity
     * transition, so there is nothing usable on screen even for the instant before the
     * activity returns. The relaunch itself is posted: the window that just appeared is
     * still settling, and starting an activity in the middle of that is unreliable.
     */
    private void bounce(String reason, String detail) {
        long now = System.currentTimeMillis();
        if (now - lastBounce < MIN_BOUNCE_INTERVAL_MS) return;
        lastBounce = now;

        SleepOverlayGuard.show(this);
        ActivityLog.log(this, reason, "target=" + detail);
        handler.post(() -> SleepLockActivity.launch(this));
    }
}
