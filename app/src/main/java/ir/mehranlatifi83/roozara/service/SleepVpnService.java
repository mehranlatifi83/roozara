package ir.mehranlatifi83.roozara.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import ir.mehranlatifi83.roozara.R;
import ir.mehranlatifi83.roozara.ui.MainActivity;

import java.io.IOException;

public class SleepVpnService extends VpnService {

    private static final String TAG        = "SleepVpnService";
    private static final String CHANNEL_ID = "sleep_vpn_channel";
    private static final int    NOTIF_ID   = 1;

    // One active VPN interface per app process; other bedtime components may close it.
    private static ParcelFileDescriptor vpnInterface;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        if (!establishVpnTunnel()) {
            Toast.makeText(this, R.string.vpn_start_failed, Toast.LENGTH_LONG).show();
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        disconnect();
    }

    /** Closes the VPN tunnel. Safe to call from any thread or context. */
    public static synchronized void disconnect() {
        if (vpnInterface != null) {
            try {
                vpnInterface.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing VPN interface", e);
            } finally {
                vpnInterface = null;
            }
        }
    }

    private boolean establishVpnTunnel() {
        if (vpnInterface != null) return true;
        try {
            vpnInterface = new Builder()
                    .setSession(getString(R.string.app_name))
                    .addAddress("10.0.0.2", 32)
                    .addRoute("0.0.0.0", 0)
                    // Modern mobile networks commonly prefer IPv6. Without this route,
                    // IPv6 traffic can bypass an IPv4-only blocking tunnel.
                    .addAddress("fd00::2", 128)
                    .addRoute("::", 0)
                    .establish();
            return vpnInterface != null;
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish VPN tunnel", e);
            return false;
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW);
        channel.setShowBadge(false);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification() {
        PendingIntent openApp = PendingIntent.getActivity(
                this, 0,
                new Intent(this, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notif_title))
                .setContentText(getString(R.string.notif_text))
                .setSmallIcon(R.drawable.ic_moon)
                .setContentIntent(openApp)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }
}
