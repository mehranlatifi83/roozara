package ir.mehranlatifi83.helth;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.NotificationCompat;

public class WakeAlarmService extends Service {

    private static final String TAG         = "WakeAlarmService";
    private static final String CHANNEL_ID  = "wake_alarm_channel";
    private static final int    NOTIF_ID    = 4;

    static final String ACTION_DISMISS = "ir.mehranlatifi83.helth.ACTION_DISMISS_WAKE";
    static final String PREF_SOUND_URI = "alarm_sound_uri";

    private MediaPlayer player;

    // ─── Service lifecycle ───────────────────────────────────────────────────

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (ACTION_DISMISS.equals(intent != null ? intent.getAction() : null)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureChannel();
        startForeground(NOTIF_ID, buildNotification());
        playAlarm();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopAlarm();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.cancel(NOTIF_ID);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // ─── Static helpers ───────────────────────────────────────────────────────

    /** Starts the alarm service. Safe to call from Activity or BroadcastReceiver. */
    public static void start(Context ctx) {
        ctx.startForegroundService(new Intent(ctx, WakeAlarmService.class));
    }

    /** Stops the alarm service and silences the sound. */
    public static void stop(Context ctx) {
        ctx.stopService(new Intent(ctx, WakeAlarmService.class));
    }

    // ─── Alarm sound ─────────────────────────────────────────────────────────

    private void playAlarm() {
        Uri uri = resolveAlarmUri();
        try {
            player = new MediaPlayer();
            player.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            player.setDataSource(this, uri);
            player.setLooping(true);
            player.prepareAsync();
            player.setOnPreparedListener(MediaPlayer::start);
            player.setOnErrorListener((mp, what, extra) -> {
                Log.e(TAG, "MediaPlayer error: " + what + "/" + extra);
                return false;
            });
        } catch (Exception e) {
            Log.e(TAG, "Failed to start alarm player", e);
        }
    }

    private void stopAlarm() {
        if (player != null) {
            try {
                if (player.isPlaying()) player.stop();
                player.release();
            } catch (Exception ignored) {}
            player = null;
        }
    }

    private Uri resolveAlarmUri() {
        String saved = getSharedPreferences("helth_prefs", MODE_PRIVATE)
                .getString(PREF_SOUND_URI, null);
        if (saved != null) return Uri.parse(saved);
        Uri def = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        return def != null ? def : RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
    }

    // ─── Notification ─────────────────────────────────────────────────────────

    private Notification buildNotification() {
        // "Dismiss" action stops the service and silences the alarm
        Intent dismissIntent = new Intent(this, WakeAlarmService.class)
                .setAction(ACTION_DISMISS);
        PendingIntent dismissPi = PendingIntent.getService(
                this, 0, dismissIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.wake_alarm_notif_title))
                .setContentText(getString(R.string.wake_alarm_notif_text))
                .setSmallIcon(R.drawable.ic_sun)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .addAction(0, getString(R.string.confirm_awake), dismissPi)
                .setContentIntent(dismissPi)
                .build();
    }

    private void ensureChannel() {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wake_alarm_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        ch.setSound(null, null); // Sound is played via MediaPlayer, not through the channel
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }
}
