package com.el7ajb.carlauncher

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.key

/** Settings, car picker and the app drawer. Opened from the home screen. */
class PanelActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        show(intent)
    }

    private var opened = 0

    /** Opened again while already open (e.g. apps after settings): switch to the asked screen. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        show(intent)
    }

    private fun show(i: Intent?) {
        val start = i?.getStringExtra("screen") ?: "settings"
        val k = ++opened
        setContent { key(k) { App(this, start) { exit() } } }
    }

    override fun onResume() { super.onResume(); Hub.panelOpen = true }
    override fun onPause() { Hub.panelOpen = false; super.onPause() }

    private fun exit() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
