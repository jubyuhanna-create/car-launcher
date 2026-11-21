package com.el7ajb.carlauncher

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.provider.Settings
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/** Shared helpers for the home screen and the settings/apps panels. */
open class BaseActivity : ComponentActivity() {

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) onLocationGranted()
    }

    fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    open fun onLocationGranted() {}

    fun hasAudio() = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private var audioCb: ((Boolean) -> Unit)? = null
    private val audioLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> audioCb?.invoke(ok); audioCb = null }

    fun requestAudio(cb: (Boolean) -> Unit) {
        if (hasAudio()) { cb(true); return }
        audioCb = cb
        try { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) } catch (_: Throwable) { cb(false) }
    }

    fun requestLocation() {
        if (hasLocation()) { onLocationGranted(); return }
        permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun notifAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, NotifService::class.java).flattenToString()
        return flat.split(":").any { it == me }
    }

    fun guardEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
        val me = ComponentName(this, HomeGuard::class.java)
        return flat.split(":").any { it.equals(me.flattenToString(), true) || it.equals(me.flattenToShortString(), true) }
    }

    fun openAccessibility() {
        if (!tryStart(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))) openAndroidSettings()
    }

    fun openNotifAccess() {
        tryStart(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    fun openHomeSettings() {
        if (!tryStart(Intent(Settings.ACTION_HOME_SETTINGS))) tryStart(Intent(Settings.ACTION_SETTINGS))
    }

    fun openAndroidSettings() { tryStart(Intent(Settings.ACTION_SETTINGS)) }

    fun tryStart(i: Intent): Boolean = try {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(i); true
    } catch (_: Throwable) { false }

    /** Launches the first installed package from the list. */
    fun launchAny(vararg pkgs: String, toastKey: String? = null, lang: Lang = Lang.AR): Boolean {
        for (p in pkgs) {
            if (p.isBlank()) continue
            val i = packageManager.getLaunchIntentForPackage(p) ?: continue
            if (tryStart(i)) return true
        }
        if (toastKey != null) Toast.makeText(this, S.get(toastKey, lang), Toast.LENGTH_SHORT).show()
        return false
    }

    fun openDialer() {
        if (!tryStart(Intent(Intent.ACTION_DIAL))) launchAny("com.android.dialer", "com.google.android.dialer")
    }

    fun mediaKey(code: Int) {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        } catch (_: Throwable) {}
    }

    val sdk: Int get() = Build.VERSION.SDK_INT

    /** 2-4 GB / 4-core head units: use the smooth mode by default. */
    fun isWeakDevice(): Boolean = try {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val mi = android.app.ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        am.isLowRamDevice || mi.totalMem < 3_500_000_000L || Runtime.getRuntime().availableProcessors() <= 4
    } catch (_: Throwable) { true }

    fun canOverlay() = Launch.canOverlay(this)

    fun requestOverlay() {
        if (Build.VERSION.SDK_INT >= 23)
            if (!tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:$packageName"))))
                tryStart(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION))
    }

    fun isDefaultHome(): Boolean = try {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName == packageName
    } catch (_: Throwable) { false }

    private val roleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    fun makeDefaultHome() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                val rm = getSystemService(android.app.role.RoleManager::class.java)
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_HOME) && !rm.isRoleHeld(android.app.role.RoleManager.ROLE_HOME)) {
                    roleLauncher.launch(rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_HOME)); return
                }
            } catch (_: Throwable) {}
        }
        openHomeSettings()
    }
}
