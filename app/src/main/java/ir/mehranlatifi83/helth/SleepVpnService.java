package ir.mehranlatifi83.helth;

import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import java.io.IOException;

public class SleepVpnService extends VpnService {

    private static ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (vpnInterface == null) {
            Builder builder = new Builder();
            builder.setSession("SleepModeVPN")
                    .addAddress("10.0.0.2", 32) // IP جعلی
                    .addRoute("0.0.0.0", 0);    // مسدود کردن همه ترافیک

            try {
                vpnInterface = builder.establish();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    public static void disconnect() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                vpnInterface = null;
            }
        }
    }
}
