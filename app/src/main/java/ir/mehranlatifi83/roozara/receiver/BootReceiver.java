package ir.mehranlatifi83.roozara.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ir.mehranlatifi83.roozara.manager.ScheduleManager;
import ir.mehranlatifi83.roozara.manager.SleepModeController;
import ir.mehranlatifi83.roozara.manager.WaterReminderManager;
import ir.mehranlatifi83.roozara.service.WakeAlarmService;
import ir.mehranlatifi83.roozara.util.ActivityLog;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action)
                && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) {
            return;
        }
        // Logged after the filter and after checking there is a schedule at all, so the
        // file says what actually happened rather than claiming a restore for every
        // unrelated broadcast, or for a schedule the user has switched off.
        boolean enabled = ScheduleManager.isScheduleEnabled(ctx);
        ActivityLog.log(ctx, enabled
                        ? "restoring the schedule"
                        : "nothing to restore - the schedule is switched off",
                "trigger=" + action);

        // If the phone rebooted mid-sleep, the sleep_active flag is stale.
        // Services are dead after reboot, so clean up silently: restore ringer
        // and clear the flag so the app starts in a consistent state.
        boolean wasSleeping = Intent.ACTION_BOOT_COMPLETED.equals(action)
                && SleepModeController.isSleepActive(ctx);
        if (wasSleeping) {
            ActivityLog.log(ctx, "phone rebooted during sleep - cleaning up");
            // Goes through the controller so the ringer returns to whatever it was
            // before bedtime. Forcing NORMAL here meant a reboot mid-sleep switched the
            // ringer on for anyone who keeps their phone on vibrate.
            SleepModeController.releaseSystemState(ctx, "reboot_during_sleep");
            ctx.getSharedPreferences(SleepModeController.PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(WakeAlarmService.KEY_WAKE_ALARM_ACTIVE, false)
                    .apply();
        }

        ScheduleManager.rescheduleIfEnabled(ctx);

        // A reboot/update/time change can occur inside the sleep window. Waiting until
        // tomorrow would leave the phone unprotected for the rest of tonight.
        if (enabled
                && ScheduleManager.isInsideSleepWindow(ctx)
                // A night the user already earned their way out of stays finished. Without
                // this, an early exit followed by a reboot — or by the phone simply being
                // restarted later that evening — dropped them straight back into the lock
                // screen they had just passed a challenge to leave.
                && !SleepModeController.wasCycleLeftEarly(ctx)
                && !SleepModeController.isSleepActive(ctx)) {
            // Called directly rather than broadcast to ourselves. The broadcast was an
            // extra hop through the system that could be delayed or dropped during the
            // boot storm, which is how a phone restarted at night came up usable.
            SleepScheduleReceiver.activateSleepMode(ctx, "reboot_inside_window");
        }

        // From here on, the display coming back on re-locks the phone at once. Nothing
        // was watching for that after a reboot, so the phone stayed usable until some
        // other app happened to come forward. Only while there is a schedule to enforce:
        // switching it off unregisters this, and a reboot must not quietly bring it back.
        if (enabled) ScreenStateReceiver.register(ctx);

        if (WaterReminderManager.isEnabled(ctx)) {
            WaterReminderManager.scheduleAll(ctx);
        }
    }
}
