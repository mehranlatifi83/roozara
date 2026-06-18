package ir.mehranlatifi83.salemzi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.media.AudioManager;

import ir.mehranlatifi83.salemzi.manager.ScheduleManager;
import ir.mehranlatifi83.salemzi.manager.WaterReminderManager;
import ir.mehranlatifi83.salemzi.service.SleepVpnService;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        // If the phone rebooted mid-sleep, the sleep_active flag is stale.
        // Services are dead after reboot, so clean up silently: restore ringer
        // and clear the flag so the app starts in a consistent state.
        boolean wasSleeping = ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                .getBoolean("sleep_active", false);
        if (wasSleeping) {
            ((AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE))
                    .setRingerMode(AudioManager.RINGER_MODE_NORMAL);
            ctx.getSharedPreferences("helth_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("sleep_active", false)
                    .putBoolean("wake_alarm_active", false)
                    .remove(ir.mehranlatifi83.salemzi.ui.SleepLockActivity.KEY_SLEEP_START)
                    .apply();
        }

        if (ScheduleManager.isScheduleEnabled(ctx)) {
            ScheduleManager.scheduleSleepAlarm(ctx);
            ScheduleManager.scheduleWakeAlarm(ctx);
            ScheduleManager.scheduleSleepReminderAlarm(ctx);
        }

        if (WaterReminderManager.isEnabled(ctx)) {
            WaterReminderManager.scheduleAll(ctx);
        }
    }
}
