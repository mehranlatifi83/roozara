package ir.mehranlatifi83.roozara.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.core.content.ContextCompat;

import ir.mehranlatifi83.roozara.manager.ScheduleManager;
import ir.mehranlatifi83.roozara.manager.SleepModeController;
import ir.mehranlatifi83.roozara.ui.SleepLockActivity;
import ir.mehranlatifi83.roozara.util.ActivityLog;

/**
 * Puts the lock screen back the instant the display comes on during the sleep window.
 *
 * Without this, waking the phone at 3am showed the system keyguard, and the lock screen
 * only returned once the accessibility guard noticed some other app had come to the
 * front — which, if the user simply unlocked and sat on their home screen, could be a
 * visible second or more. The same path covers the first screen-on after a reboot: the
 * boot receiver restarts the night, but nothing was watching for the display coming
 * back, so the phone was usable until something else happened to trigger a bounce.
 *
 * SCREEN_ON and SCREEN_OFF cannot be declared in the manifest — the system only delivers
 * them to receivers registered at runtime — so this is registered by the components that
 * are alive for the whole night: the sleep guard and the blocking VPN service.
 */
public class ScreenStateReceiver extends BroadcastReceiver {

    private static ScreenStateReceiver registered;

    /** Registers once per process. Safe to call from every component that needs it. */
    public static synchronized void register(Context ctx) {
        if (registered != null) return;
        ScreenStateReceiver receiver = new ScreenStateReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        // Application context: this outlives whichever service happened to register it.
        // NOT_EXPORTED because nothing outside the app has any business triggering this;
        // both actions are protected system broadcasts, which still arrive either way.
        //
        // Through ContextCompat rather than Context: the flag only exists from API 33,
        // and this app runs from 30. Passing it directly meant handing a value the
        // platform did not know to registerReceiver on Android 11 and 12.
        ContextCompat.registerReceiver(ctx.getApplicationContext(), receiver, filter,
                ContextCompat.RECEIVER_NOT_EXPORTED);
        registered = receiver;
    }

    public static synchronized void unregister(Context ctx) {
        if (registered == null) return;
        try {
            ctx.getApplicationContext().unregisterReceiver(registered);
        } catch (IllegalArgumentException ignored) {
            // Already gone with the process. Nothing to undo.
        }
        registered = null;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_SCREEN_ON.equals(action) && !Intent.ACTION_USER_PRESENT.equals(action)) {
            return;
        }

        if (SleepModeController.isSleepActive(ctx)) {
            // Worth a line of its own: during a night the screen should rarely come on,
            // so these entries are how someone reconstructs a restless night afterwards.
            ActivityLog.log(ctx, "screen came on during sleep - restoring the lock screen",
                    "trigger=" + action);
            SleepLockActivity.launch(ctx);
            return;
        }

        // The screen coming on is also the earliest reliable moment to notice that a
        // night should have started but did not — an alarm dropped by an OEM power
        // manager, or a reboot that finished after bedtime. A night the user already
        // earned their way out of stays finished.
        if (ScheduleManager.isScheduleEnabled(ctx)
                && ScheduleManager.isInsideSleepWindow(ctx)
                && !SleepModeController.wasCycleLeftEarly(ctx)) {
            SleepScheduleReceiver.activateSleepMode(ctx, "screen_on_inside_window");
        }
    }
}
