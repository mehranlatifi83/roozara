package ir.mehranlatifi83.salemzi.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import ir.mehranlatifi83.salemzi.manager.ScheduleManager;
import ir.mehranlatifi83.salemzi.manager.WaterReminderManager;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

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
