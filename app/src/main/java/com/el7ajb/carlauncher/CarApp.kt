package com.el7ajb.carlauncher

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Saves the reason of any crash so it can be shown (and photographed) on the next start. */
class CarApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Hub.processStart = android.os.SystemClock.uptimeMillis()
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            CrashLog.record(this, e, fatal = true, thread = t.name)
            prev?.uncaughtException(t, e)
        }
    }
}

object CrashLog {
    private fun file(ctx: Context) = File(ctx.filesDir, "crash.txt")

    fun record(ctx: Context, e: Throwable, fatal: Boolean, thread: String = "") {
        try {
            val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
            val head = "Drive ${BuildConfigShim.version(ctx)} | Android ${Build.VERSION.RELEASE} (${Build.VERSION.SDK_INT}) | ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())} ${if (fatal) "CRASH" else "caught"} $thread\n"
            val body = sw.toString().lines().take(40).joinToString("\n")
            if (fatal || !file(ctx).exists()) file(ctx).writeText(head + body)
        } catch (_: Throwable) {}
    }

    fun take(ctx: Context): String? = try {
        val f = file(ctx)
        if (f.exists()) f.readText().also { f.delete() } else null
    } catch (_: Throwable) { null }
}

object BuildConfigShim {
    fun version(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Throwable) { "" }
}
