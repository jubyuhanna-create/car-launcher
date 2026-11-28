package com.el7ajb.carlauncher

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat

/** The launcher itself: a lightweight native View home screen. */
class MainActivity : BaseActivity() {

    private lateinit var home: HomeUi
    private var lm: LocationManager? = null
    // All four methods must be implemented: on Android 10 and older they are abstract, and a
    // lambda here crashes the app the first time the GPS is switched on/off.
    private val locListener = object : LocationListener {
        override fun onLocationChanged(loc: Location) {
            Hub.gpsFix.value = true
            Clock.onLocation(this@MainActivity, loc.latitude, loc.longitude)
            Trip.onLocation(this@MainActivity, loc)
            Hub.speedKmh.value = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) { Hub.gpsFix.value = false }
    }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { Trip.onWake(this@MainActivity); Hub.bootTick.value++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        Trip.load(this)
        home = HomeUi(this)
        setContentView(home.root)
        // the logo splash is only for the cold start: once the home is drawn, a plain black window is cheaper
        home.root.post { window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(android.graphics.Color.BLACK)) }
        if (!getSharedPreferences("cl", 0).getBoolean("setup", false)) openPanel("picker")
        CrashLog.take(this)?.let { showCrash(it) }
        if (hasLocation()) startGps()
    }

    override fun onResume() {
        super.onResume()
        Hub.mainResumed = true
        home.refresh()
        home.onResume()
        Thread { VendorApps.resolveAll(this, force = true) }.start()
        if (hasLocation()) startGps()
    }

    override fun onPause() {
        Hub.mainResumed = false
        home.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        try { unregisterReceiver(screenOn) } catch (_: Throwable) {}
        try { lm?.removeUpdates(locListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    @Suppress("MissingSuperCall")
    override fun onBackPressed() { home.onBack() /* otherwise: home screen, nothing to go back to */ }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        home.onBack()   // home key while in the showroom: back to the normal home
    }

    override fun onLocationGranted() { startGps() }

    private fun showCrash(txt: String) {
        try {
            val tv = android.widget.TextView(this).apply {
                text = txt; textSize = 11f; setTextIsSelectable(true); setPadding(32, 24, 32, 24)
                setTextColor(0xFFECEEF0.toInt()); typeface = android.graphics.Typeface.MONOSPACE
            }
            android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle("Drive: last error / آخر خطأ")
                .setView(android.widget.ScrollView(this).apply { addView(tv) })
                .setPositiveButton("OK", null)
                .show()
        } catch (_: Throwable) {}
    }

    fun openPanel(screen: String) {
        try {
            startActivity(Intent(this, PanelActivity::class.java).putExtra("screen", screen))
            @Suppress("DEPRECATION")
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        } catch (_: Throwable) {}
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
}
