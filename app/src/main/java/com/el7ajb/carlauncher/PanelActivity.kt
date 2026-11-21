package com.el7ajb.carlauncher

import android.os.Bundle
import androidx.activity.compose.setContent

/** Settings, car picker and the app drawer. Opened from the home screen. */
class PanelActivity : BaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val start = intent?.getStringExtra("screen") ?: "settings"
        setContent { App(this, start) { exit() } }
    }

    private fun exit() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
