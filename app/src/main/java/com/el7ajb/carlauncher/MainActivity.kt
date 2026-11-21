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
    private val locListener = LocationListener { loc: Location ->
        Hub.gpsFix.value = true
        Hub.speedKmh.value = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0
    }

    private val screenOn = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { Hub.bootTick.value++ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, true)
        ContextCompat.registerReceiver(this, screenOn, IntentFilter(Intent.ACTION_SCREEN_ON), ContextCompat.RECEIVER_NOT_EXPORTED)
        home = HomeUi(this)
        setContentView(home.root)
        if (!getSharedPreferences("cl", 0).getBoolean("setup", false)) openPanel("picker")
        if (hasLocation()) startGps()
    }

    override fun onResume() {
        super.onResume()
        home.refresh()
        home.onResume()
        Thread { VendorApps.resolveAll(this, force = true) }.start()
        if (hasLocation()) startGps()
    }

    override fun onPause() {
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
    override fun onBackPressed() { /* home screen: nothing to go back to */ }

    override fun onLocationGranted() { startGps() }

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
