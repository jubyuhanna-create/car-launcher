package com.el7ajb.carlauncher

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.KeyEvent
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class MainActivity : ComponentActivity() {

    private var lm: LocationManager? = null
    private val locListener = LocationListener { loc: Location ->
        Hub.gpsFix.value = true
        Hub.speedKmh.value = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
    }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { Hub.bootTick.value++ }
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startGps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideBars()
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        setContent { App(this) }
        if (hasLocation()) startGps()
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        if (hasLocation()) startGps()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOn) } catch (_: Throwable) {}
        try { lm?.removeUpdates(locListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.hide(WindowInsetsCompat.Type.systemBars())
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    fun hasLocation() = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    fun requestLocation() {
        if (hasLocation()) { startGps(); return }
        permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    @SuppressLint("MissingPermission")
    private fun startGps() {
        if (!hasLocation()) return
        try {
            if (lm == null) lm = getSystemService(LOCATION_SERVICE) as LocationManager
            lm?.removeUpdates(locListener)
            if (lm?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true)
                lm?.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locListener)
        } catch (_: Throwable) {}
    }

    fun notifAccessGranted(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        val me = ComponentName(this, NotifService::class.java).flattenToString()
        return flat.split(":").any { it == me }
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
}
