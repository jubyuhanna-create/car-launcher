package com.el7ajb.carlauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

/** Brings Drive to the front when the head unit boots or wakes (ACC on). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent?) {
        Launch.front(ctx, delayMs = 2500)
    }
}

object Launch {
    fun autoOpenEnabled(ctx: Context) = ctx.getSharedPreferences("cl", 0).getBoolean("autoOpen", true)

    fun canOverlay(ctx: Context) = Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(ctx)

    /**
     * Brings Drive's home screen to the front. Without force it only does so when nothing of
     * Drive is already showing, so it never pulls the user out of the settings panel.
     */
    fun front(ctx: Context, delayMs: Long = 0, force: Boolean = false) {
        if (!autoOpenEnabled(ctx)) return
        val app = ctx.applicationContext
        val run = Runnable {
            if (!force && (Hub.mainResumed || Hub.panelOpen)) return@Runnable
            if (force && Hub.mainResumed) return@Runnable
            try {
                app.startActivity(
                    Intent(app, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            } catch (_: Throwable) {}
        }
        val h = android.os.Handler(android.os.Looper.getMainLooper())
        if (delayMs > 0) {
            h.postDelayed(run, delayMs)
            if (!force) h.postDelayed(run, delayMs + 4000) // vendor launchers often pop up late, so try once more
        } else run.run()
    }
}
