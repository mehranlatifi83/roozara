package ir.mehranlatifi83.roozara.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.net.VpnService;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import ir.mehranlatifi83.roozara.R;
import ir.mehranlatifi83.roozara.manager.SleepModeController;
import ir.mehranlatifi83.roozara.receiver.ScreenStateReceiver;
import ir.mehranlatifi83.roozara.util.ActivityLog;
import ir.mehranlatifi83.roozara.util.Notifications;
import ir.mehranlatifi83.roozara.ui.MainActivity;

import java.io.FileInputStream;
import java.io.IOException;

/**
 * Blocks internet access during the sleep window by routing every route into a local
 * tunnel whose packets are read and discarded.
 *
 * The tunnel is treated as something that will be taken away rather than something that
 * stays up. Another VPN app can claim the single VPN slot at any moment, an OEM power
 * manager can kill the service, and the user can revoke consent — in every one of those
 * cases the phone is silently back online for the rest of the night. So the service
 * watches itself: a watchdog re-checks the tunnel on a timer for as long as sleep mode
 * is active, and rebuilds it whenever it has gone.
 */
public class SleepVpnService extends VpnService {

    private static final String TAG        = "SleepVpnService";
    private static final String CHANNEL_ID = "sleep_vpn_channel";
    private static final int    NOTIF_ID   = Notifications.VPN_RUNNING;

    /**
     * How often the tunnel is re-checked while sleep mode is running.
     *
     * Short enough that a tunnel lost to another VPN app is back within a minute, long
     * enough to be invisible on the battery next to a foreground service that is already
     * running for the whole night.
     */
    private static final long WATCHDOG_INTERVAL_MS = 60_000L;

    /** Guards {@link #vpnInterface}. Everything that opens or closes the tunnel holds it. */
    private static final Object TUNNEL_LOCK = new Object();

    // One active VPN interface per app process; other bedtime components may close it.
    private static ParcelFileDescriptor vpnInterface;

    private volatile boolean draining;
    private Thread drainThread;

    private final Handler watchdogHandler = new Handler(Looper.getMainLooper());
    private boolean watchdogRunning;

    // Which address families the tunnel actually captured, so the log can say whether
    // traffic could still be leaving over the other one.
    private boolean hasIpv4;
    private boolean hasIpv6;

    // ─── Service lifecycle ───────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        // Registered here as well as in the guard, so the phone still re-locks on
        // screen-on for anyone who has not switched the accessibility guard on.
        ScreenStateReceiver.register(this);

        // START_STICKY restarts this service with a null intent after the system kills
        // it. The old descriptor is dead by then, so the tunnel is rebuilt — but a
        // tunnel that is genuinely still up is left exactly as it is. Tearing down a
        // working tunnel to rebuild it dropped every connection for a moment and, if the
        // rebuild then failed, left the phone online for the rest of the night.
        if (!establishVpnTunnel()) {
            // A Toast is unreliable here: this runs from a background-started service,
            // and a user who is asleep or blind would not see it anyway. A notification
            // persists until it is read.
            ActivityLog.log(this, "internet block failed", "reason=tunnel_not_established");
            notifyBlockingFailed();
            // Deliberately still sticky while the night is running: the watchdog gets
            // another chance a minute from now, which is how a tunnel that failed only
            // because another VPN was momentarily in the slot comes back on its own.
            if (SleepModeController.isSleepActive(this)) {
                startWatchdog();
                return START_STICKY;
            }
            stopSelf();
            return START_NOT_STICKY;
        }
        ActivityLog.log(this, "internet blocked", "tunnel=up");
        startDraining();
        startWatchdog();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopWatchdog();
        stopDraining();
        disconnect();
    }

    @Override
    public void onRevoke() {
        Log.w(TAG, "VPN permission revoked by the system or another app");
        // The single most common reason the internet is not actually blocked: another
        // VPN app took the slot, or the user revoked consent. Android allows exactly one
        // active VPN, so ours is simply gone and the phone is fully online again.
        ActivityLog.log(this, "internet block LOST",
                "reason=vpn_revoked_by_system_or_another_vpn_app");
        stopDraining();
        disconnect();

        if (SleepModeController.isSleepActive(this)) {
            // Losing the slot mid-night is exactly the case the watchdog exists for.
            // Giving up here is what left people quietly online until morning. If the
            // other VPN is dismissed, or consent is still ours, the next tick takes the
            // slot back; if it is not, each tick records why.
            ActivityLog.log(this, "internet block will keep retrying", "reason=sleep_still_active");
            startWatchdog();
        } else {
            stopSelf();
        }
        super.onRevoke();
    }

    // ─── Watchdog ────────────────────────────────────────────────────────────

    private final Runnable watchdogTick = new Runnable() {
        @Override
        public void run() {
            if (!SleepModeController.isSleepActive(SleepVpnService.this)) {
                // The night ended without this service being told. Nothing left to guard.
                watchdogRunning = false;
                stopSelf();
                return;
            }
            if (!isTunnelUp()) {
                ActivityLog.log(SleepVpnService.this,
                        "internet block was down - watchdog rebuilding the tunnel");
                if (establishVpnTunnel()) {
                    startDraining();
                    ActivityLog.log(SleepVpnService.this, "internet block restored", "tunnel=up");
                }
            }
            watchdogHandler.postDelayed(this, WATCHDOG_INTERVAL_MS);
        }
    };

    private void startWatchdog() {
        if (watchdogRunning) return;
        watchdogRunning = true;
        watchdogHandler.postDelayed(watchdogTick, WATCHDOG_INTERVAL_MS);
    }

    private void stopWatchdog() {
        watchdogRunning = false;
        watchdogHandler.removeCallbacks(watchdogTick);
    }

    // ─── Tunnel ──────────────────────────────────────────────────────────────

    /** True while a tunnel is actually open, not merely while the service is running. */
    public static boolean isTunnelUp() {
        synchronized (TUNNEL_LOCK) {
            return vpnInterface != null && vpnInterface.getFileDescriptor() != null
                    && vpnInterface.getFileDescriptor().valid();
        }
    }

    /**
     * Starts the tunnel if it is not already up. Called whenever the lock screen comes
     * back to the foreground, so a tunnel lost to a process kill or to another VPN app
     * is rebuilt instead of silently leaving the user online for the rest of the night.
     */
    public static void ensureRunning(android.content.Context ctx) {
        if (VpnService.prepare(ctx) != null) {
            ActivityLog.log(ctx, "internet block cannot restart", "reason=vpn_consent_missing");
            return;
        }
        if (isTunnelUp()) return;
        ActivityLog.log(ctx, "internet block was down - restarting the tunnel");
        try {
            ctx.startForegroundService(new Intent(ctx, SleepVpnService.class));
        } catch (Exception e) {
            Log.w(TAG, "Could not restart the blocking tunnel", e);
            ActivityLog.log(ctx, "internet block restart failed", "error=" + describe(e));
        }
    }

    /** Closes the VPN tunnel. Safe to call from any thread or context. */
    public static void disconnect() {
        synchronized (TUNNEL_LOCK) {
            if (vpnInterface == null) return;
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
        synchronized (TUNNEL_LOCK) {
            // A tunnel that is genuinely still up is left alone. Rebuilding it here is
            // what made an ordinary service restart — a reminder waking the process, the
            // system re-delivering START_STICKY — briefly drop the block.
            if (isTunnelUp()) {
                ActivityLog.log(this, "internet block already up", "tunnel=kept");
                return true;
            }
            // Anything left over from a dead tunnel has to go before a new one is opened.
            disconnect();

            Builder builder = new Builder().setSession(getString(R.string.app_name));

            // IPv4 and IPv6 are configured independently. Adding an IPv6 address throws on
            // devices and networks without IPv6 support, and when that exception escaped it
            // took the whole tunnel down with it — leaving the user fully online all night
            // while the app reported success. Each family is now best-effort, and the tunnel
            // is established as long as at least one of them applied.
            boolean anyFamily = false;
            hasIpv4 = false;
            hasIpv6 = false;

            try {
                builder.addAddress("10.0.0.2", 32).addRoute("0.0.0.0", 0);
                anyFamily = true;
                hasIpv4 = true;
            } catch (Exception e) {
                Log.w(TAG, "IPv4 route unavailable", e);
                ActivityLog.log(this, "IPv4 could not be routed", "error=" + describe(e));
            }

            try {
                // Modern mobile networks commonly prefer IPv6. Without this route, IPv6
                // traffic can bypass an IPv4-only blocking tunnel.
                builder.addAddress("fd00::2", 128).addRoute("::", 0);
                anyFamily = true;
                hasIpv6 = true;
            } catch (Exception e) {
                Log.w(TAG, "IPv6 could not be routed", e);
                // Worth recording on its own: most mobile networks prefer IPv6, so an
                // IPv4-only tunnel can leave the phone effectively online.
                ActivityLog.log(this, "IPv6 could not be routed", "error=" + describe(e));
            }

            if (!anyFamily) {
                Log.e(TAG, "Neither IPv4 nor IPv6 could be routed; cannot block traffic");
                ActivityLog.log(this, "internet block failed",
                        "reason=no_ip_family_could_be_routed");
                return false;
            }

            try {
                // Our own process is excluded so the app stays responsive and its own
                // scheduling work is never affected by the block it installed.
                builder.addDisallowedApplication(getPackageName());
            } catch (Exception e) {
                Log.w(TAG, "Could not exclude own package from the tunnel", e);
                ActivityLog.log(this, "could not exclude Roozara from the tunnel",
                        "error=" + describe(e));
            }

            try {
                vpnInterface = builder.establish();
            } catch (Exception e) {
                Log.e(TAG, "Failed to establish VPN tunnel", e);
                ActivityLog.log(this, "internet block failed",
                        "reason=establish_threw error=" + describe(e));
                return false;
            }

            if (vpnInterface == null) {
                // establish() returns null when consent was never granted or was withdrawn.
                Log.e(TAG, "establish() returned null — VPN consent is missing");
                ActivityLog.log(this, "internet block failed",
                        "reason=establish_returned_null_vpn_consent_missing");
                return false;
            }
            ActivityLog.log(this, "tunnel established",
                    "ipv4=" + ActivityLog.yesNo(hasIpv4) + " ipv6=" + ActivityLog.yesNo(hasIpv6));
            return true;
        }
    }

    /** Exception summary short enough for one log line but specific enough to act on. */
    private static String describe(Throwable e) {
        String message = e.getMessage();
        String name = e.getClass().getSimpleName();
        return message == null || message.isEmpty() ? name : name + ": " + message;
    }

    // ─── Draining ────────────────────────────────────────────────────────────

    /**
     * Reads and discards everything the system writes into the tunnel.
     *
     * Without this the descriptor is never drained, so blocking depends on the kernel
     * queue filling up. On some devices that queue drains or is large enough that
     * traffic keeps flowing for a while, which is why the block could appear partial.
     * Actively discarding packets makes the block immediate and deterministic.
     */
    private void startDraining() {
        // A previous thread may still be winding down on a descriptor that is now closed.
        // Restarting the drain on a rebuilt tunnel has to replace it, not run beside it.
        stopDraining();

        final ParcelFileDescriptor fd;
        synchronized (TUNNEL_LOCK) {
            fd = vpnInterface;
        }
        if (fd == null) return;

        draining = true;
        drainThread = new Thread(() -> {
            byte[] packet = new byte[32767];
            try (FileInputStream in = new FileInputStream(fd.getFileDescriptor())) {
                while (draining) {
                    int read = in.read(packet);   // Discarding what is read is the block.
                    if (read < 0) break;          // Tunnel closed.
                }
            } catch (IOException e) {
                // Expected when the tunnel is torn down at wake time.
                if (draining) {
                    Log.w(TAG, "Tunnel read ended", e);
                    ActivityLog.log(this, "tunnel stopped draining unexpectedly",
                            "error=" + describe(e));
                }
            }
        }, "SleepVpnDrain");
        drainThread.setDaemon(true);
        drainThread.start();
    }

    private void stopDraining() {
        draining = false;
        if (drainThread != null) {
            drainThread.interrupt();
            drainThread = null;
        }
    }

    // ─── Notifications ───────────────────────────────────────────────────────

    private void notifyBlockingFailed() {
        getSystemService(NotificationManager.class).notify(Notifications.VPN_START_FAILED,
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setContentTitle(getString(R.string.vpn_permission_missing_title))
                        .setContentText(getString(R.string.vpn_start_failed))
                        .setSmallIcon(R.drawable.ic_moon)
                        .setPriority(NotificationCompat.PRIORITY_HIGH)
                        .setAutoCancel(true)
                        .build());
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
