package ir.mehranlatifi83.roozara.util;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * A plain-text record of everything Roozara does.
 *
 * The app spends most of its life with no screen showing, doing things the user is
 * asleep for. When something goes wrong afterwards there is nothing to look at, and
 * "the alarm didn't ring" is impossible to diagnose from memory. This writes one line
 * per action: what happened, when, and what the result was.
 *
 * Deliberately simple. It is meant to be read aloud by TalkBack and shared with someone
 * who can help, not parsed by a machine.
 *
 * Nothing here ever throws. A logging failure must never break the feature it was only
 * meant to observe.
 *
 * Writing happens on a single background thread. Every caller is on a main thread that
 * matters — a broadcast receiver the system is timing, a foreground service, and above
 * all the accessibility guard, which logs on every bounce and whose main thread the
 * whole device waits on. Opening a file there made the phone stutter exactly while it
 * was meant to be sitting quietly. One thread rather than a pool, because the order of
 * the lines is the point of the file.
 */
public final class ActivityLog {

    private static final String TAG        = "RoozaraLog";
    private static final String PREFS      = "helth_prefs";
    private static final String KEY_ENABLED = "logging_enabled";
    private static final String FILE_NAME  = "activity.log";

    /** Rotated past this size so it cannot grow without bound over months of use. */
    private static final long MAX_BYTES = 1024 * 1024;

    private static final Object LOCK = new Object();

    /** Serial and daemon: lines keep their order and never hold the process open. */
    private static final ExecutorService WRITER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "RoozaraLogWriter");
        thread.setDaemon(true);
        return thread;
    });

    /** Guarded by LOCK. Reused rather than rebuilt for every line. */
    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);

    private ActivityLog() {}

    // ─── On/off ──────────────────────────────────────────────────────────────

    public static boolean isEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_ENABLED, true);
    }

    public static void setEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_ENABLED, enabled).apply();
        // Written whatever the new state is, so a gap in the file is never unexplained.
        write(ctx, enabled ? "logging enabled by the user" : "logging disabled by the user");
    }

    // ─── Recording ───────────────────────────────────────────────────────────

    /** Record one action, e.g. log(ctx, "sleep mode started"). */
    public static void log(Context ctx, String event) {
        if (ctx == null || !isEnabled(ctx)) return;
        write(ctx, event);
    }

    /** Record one action with a detail, e.g. log(ctx, "internet blocked", "succeeded=yes"). */
    public static void log(Context ctx, String event, String detail) {
        if (ctx == null || !isEnabled(ctx)) return;
        write(ctx, event + " " + detail);
    }

    public static String yesNo(boolean value) {
        return value ? "yes" : "no";
    }

    // ─── File ────────────────────────────────────────────────────────────────

    public static File file(Context ctx) {
        File dir = new File(ctx.getFilesDir(), "logs");
        if (!dir.exists() && !dir.mkdirs()) {
            Log.w(TAG, "Could not create the log directory");
        }
        return new File(dir, FILE_NAME);
    }

    public static long sizeBytes(Context ctx) {
        awaitPendingWrites();
        File f = file(ctx);
        return f.exists() ? f.length() : 0;
    }

    /** Empty the log. Recording carries on afterwards if it is switched on. */
    public static boolean clear(Context ctx) {
        // Anything still queued belongs to the log being thrown away, so it is drained
        // first; otherwise those lines reappeared in the freshly emptied file.
        awaitPendingWrites();
        synchronized (LOCK) {
            try (FileWriter writer = new FileWriter(file(ctx), false)) {
                writer.write("");
            } catch (Exception e) {
                Log.w(TAG, "Could not clear the log", e);
                return false;
            }
        }
        // Only when recording is on. Clearing with logging switched off used to leave a
        // line behind in a file the user had just asked to be empty.
        if (isEnabled(ctx)) write(ctx, "log cleared by the user");
        return true;
    }

    /**
     * Block briefly until queued lines have reached the file.
     *
     * Callers that read the file — sharing it, showing its size — would otherwise see a
     * version missing whatever was written moments earlier. The timeout means a stuck
     * write can never hang the UI; at worst the file is one line short.
     */
    public static void awaitPendingWrites() {
        try {
            WRITER.submit(() -> { }).get(500, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // Interrupted, timed out, or the executor is gone. Nothing worth reporting.
        }
    }

    /**
     * Build a share intent for the log file.
     *
     * Returns null when there is nothing to share, so the caller can say so rather than
     * opening an empty share sheet.
     */
    public static Intent shareIntent(Context ctx) {
        awaitPendingWrites();
        File f = file(ctx);
        if (!f.exists() || f.length() == 0) return null;
        try {
            Uri uri = FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".fileprovider", f);
            return new Intent(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_SUBJECT, "Roozara activity log")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception e) {
            Log.w(TAG, "Could not build the share intent", e);
            return null;
        }
    }

    // ─── Internals ───────────────────────────────────────────────────────────

    private static void write(Context ctx, String line) {
        // Stamped on the calling thread. Taking the time inside the worker would date
        // every line by when the queue reached it rather than when the thing happened,
        // which is the one question this file exists to answer.
        final Date when = new Date();
        // The application context, so a queued line can never keep an activity or a
        // service alive for as long as it waits.
        final Context appCtx = ctx.getApplicationContext();
        try {
            WRITER.execute(() -> writeNow(appCtx, when, line));
        } catch (Exception e) {
            // The executor refuses work only once it is shutting down with the process.
            Log.w(TAG, "Could not queue a log line", e);
        }
    }

    private static void writeNow(Context ctx, Date when, String line) {
        synchronized (LOCK) {
            try {
                File f = file(ctx);
                rotateIfNeeded(f);
                String stamp = STAMP.format(when);
                try (FileWriter writer = new FileWriter(f, true)) {
                    writer.append(stamp).append("  ").append(line).append('\n');
                }
            } catch (Exception e) {
                // Losing a line is always preferable to breaking the action.
                Log.w(TAG, "Could not write to the log", e);
            }
        }
    }

    private static void rotateIfNeeded(File f) {
        if (!f.exists() || f.length() < MAX_BYTES) return;
        File previous = new File(f.getParentFile(), FILE_NAME + ".previous");
        if ((!previous.exists() || previous.delete()) && f.renameTo(previous)) return;

        // Rotation failed. Truncating loses the history, but the alternative was giving
        // up silently and letting the file grow without bound for the rest of the
        // install — the one thing the size limit exists to prevent.
        Log.w(TAG, "Could not rotate the log; truncating instead");
        try (FileWriter writer = new FileWriter(f, false)) {
            writer.write("");
        } catch (Exception e) {
            Log.w(TAG, "Could not truncate the log either", e);
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
