package com.el7ajb.carlauncher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import org.json.JSONObject
import java.io.File

/**
 * Updates Drive from the public drive-cars repo: app.json says which version is newest and where its
 * APK is. The APK is signed with the same key, so Android installs it over the old one and keeps
 * every setting. No token, no Play Store, no browser.
 */
object Updater {

    data class Info(val versionCode: Int, val versionName: String, val apk: String, val size: Long)

    fun versionCode(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (_: Throwable) { 0 }

    fun versionName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
    } catch (_: Throwable) { "" }

    /** Blocking. Returns null when offline. */
    fun check(): Info? {
        val c = Catalog.open(Catalog.BASE + "app.json?t=" + System.currentTimeMillis() / 60_000)
        try {
            if (c.responseCode != 200) return null
            val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
            return Info(o.getInt("versionCode"), o.optString("versionName"), o.getString("apk"), o.optLong("size", 0L))
        } finally { c.disconnect() }
    }

    fun canInstall(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun askInstallPermission(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Throwable) {
            try { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Throwable) {}
        }
    }

    /** Blocking. Downloads the APK and hands it to Android's installer. */
    fun downloadAndInstall(ctx: Context, info: Info, progress: (Float) -> Unit): Boolean {
        val f = File(ctx.cacheDir, "drive-update.apk")
        try {
            val c = Catalog.open(Catalog.BASE + info.apk)
            try {
                if (c.responseCode != 200) return false
                val total = if (c.contentLength > 0) c.contentLength.toLong() else info.size
                c.inputStream.use { ins ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L; var last = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); read += n
                            if (total > 0 && read - last > 128 * 1024) { last = read; progress((read.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
            } finally { c.disconnect() }
            if (info.size > 0 && f.length() != info.size) return false
            progress(1f)

            val pi = ctx.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(ctx.packageName)
            val id = pi.createSession(params)
            pi.openSession(id).use { s ->
                f.inputStream().use { ins ->
                    s.openWrite("drive.apk", 0, f.length()).use { out -> ins.copyTo(out, 64 * 1024); s.fsync(out) }
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
                val pending = PendingIntent.getBroadcast(ctx, 7, Intent(ctx, InstallReceiver::class.java), flags)
                s.commit(pending.intentSender)
            }
            return true
        } catch (_: Throwable) {
            return false
        } finally {
            f.delete()
        }
    }
}

/** Android asks the driver to confirm the update; this shows that screen. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, i: Intent) {
        when (i.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = i.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                try { ctx.startActivity(confirm) } catch (_: Throwable) {}
            }
        }
    }
}
