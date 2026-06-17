package ir.mehranlatifi83.helth;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && ScheduleManager.isScheduleEnabled(ctx)) {
            ScheduleManager.scheduleSleepAlarm(ctx);
            ScheduleManager.scheduleWakeAlarm(ctx);
            ScheduleManager.scheduleSleepReminderAlarm(ctx);
        }
    }
}
