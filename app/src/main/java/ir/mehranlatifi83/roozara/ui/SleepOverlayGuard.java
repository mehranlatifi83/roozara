package ir.mehranlatifi83.roozara.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.provider.Settings;
import android.view.ContextThemeWrapper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import ir.mehranlatifi83.roozara.R;
import ir.mehranlatifi83.roozara.util.ActivityLog;

/**
 * A full-screen overlay that covers whatever is behind the sleep lock screen.
 *
 * This replaces lock task mode ("screen pinning"). Pinning worked, but it demanded a
 * system setting the user had to find and switch on themselves, it took over the whole
 * device in a way that alarmed people, and leaving it was a documented two-button
 * gesture anyway — so it asked a lot and delivered a soft guarantee.
 *
 * The overlay approach uses the permission the app already asks for and already
 * explains: "display over other apps". While the lock activity is in the foreground it
 * is not needed. The moment the activity is pushed aside — Home, Recents, another app
 * coming forward — this window is raised, and because it is a window rather than an
 * activity, Home does not dismiss it. Whatever the user switched to is still running
 * underneath, but it is covered and cannot be touched, and the lock activity is brought
 * back within a few hundred milliseconds regardless.
 *
 * Honest limits, the same ones the lock screen has always had:
 *   - Without overlay access this does nothing at all, and the app falls back to
 *     relaunching the activity, which is what it did before.
 *   - It cannot cover the status bar. Since API 26 the system deliberately places
 *     TYPE_APPLICATION_OVERLAY below the status and navigation bars, and no ordinary
 *     app can get above them.
 * It raises the cost of wandering off. It is not a cage, and the app should not
 * pretend otherwise.
 */
public final class SleepOverlayGuard {

    private static final String TAG = "SleepOverlayGuard";

    /**
     * Held statically on purpose: the cover has to outlive the activity that raised it.
     *
     * That is exactly what it is for — it goes up as the lock activity is pushed aside
     * and comes down when it is back. It is inflated from the application context below
     * so that holding it here keeps no activity alive.
     */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static View overlayView;

    private SleepOverlayGuard() {}

    public static boolean isAvailable(Context ctx) {
        return Settings.canDrawOverlays(ctx);
    }

    public static boolean isShowing() {
        return overlayView != null;
    }

    /** Raise the cover. Safe to call repeatedly and from any activity. */
    public static void show(Context ctx) {
        if (overlayView != null || !isAvailable(ctx)) return;

        WindowManager wm = (WindowManager) ctx.getApplicationContext()
                .getSystemService(Context.WINDOW_SERVICE);
        if (wm == null) return;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                // Not focusable: the overlay must never steal key events from the lock
                // activity underneath it, and a screen reader has to keep working. It
                // still consumes touches, which is what stops the app behind it being
                // used.
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.OPAQUE);
        params.gravity = Gravity.TOP | Gravity.START;

        try {
            // Inflated from the application context, wrapped in the app theme so the
            // cover still uses the right colours. Inflating from the calling activity
            // left this static field holding that activity for the rest of the night.
            Context themed = new ContextThemeWrapper(
                    ctx.getApplicationContext(), R.style.Theme_Roozara);
            View view = LayoutInflater.from(themed).inflate(R.layout.overlay_sleep_guard, null);
            Context appCtx = ctx.getApplicationContext();
            // Tapping anywhere goes straight back to the lock screen, so someone who
            // ends up here is never stuck looking at a blank cover.
            view.setOnClickListener(v -> SleepLockActivity.launch(appCtx));
            wm.addView(view, params);
            overlayView = view;
        } catch (Exception e) {
            // Overlay access can be revoked while the app runs, and some ROMs refuse the
            // window even with it granted. Bedtime must carry on either way — but this is
            // exactly why "the lock screen did not cover anything" happens, so it belongs
            // in the file the user can share rather than only in logcat.
            Log.w(TAG, "Could not show the sleep overlay", e);
            ActivityLog.log(ctx, "sleep overlay could NOT be shown",
                    "error=" + e.getClass().getSimpleName());
            overlayView = null;
        }
    }

    /** Take the cover down. Safe to call when it was never shown. */
    public static void hide(Context ctx) {
        if (overlayView == null) return;
        WindowManager wm = (WindowManager) ctx.getApplicationContext()
                .getSystemService(Context.WINDOW_SERVICE);
        try {
            if (wm != null) wm.removeView(overlayView);
        } catch (Exception e) {
            Log.w(TAG, "Could not remove the sleep overlay", e);
        } finally {
            overlayView = null;
        }
    }
}
