package com.el7ajb.carlauncher

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Many head units show their own home screen on boot / wake no matter which launcher is the
 * default. This service only listens to "which screen is now in front"; the moment the original
 * home screen appears, Drive is brought back in its place.
 */
class HomeGuard : AccessibilityService() {

    private var homeActivities: Set<String> = emptySet()
    private var homePackages: Set<String> = emptySet()
    private var last = 0L

    override fun onServiceConnected() {
        Hub.guardConnected = true
        refreshHomes()
        Launch.front(this, delayMs = 400, force = false)
    }

    private fun refreshHomes() {
        try {
            val pm = packageManager
            @Suppress("DEPRECATION")
            val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_ALL)
            val others = list.filter { it.activityInfo.packageName != packageName && it.activityInfo.packageName != "com.android.settings" }
            homePackages = others.map { it.activityInfo.packageName }.toSet()
            homeActivities = others.map { it.activityInfo.packageName + "/" + it.activityInfo.name }.toSet()
        } catch (_: Throwable) {}
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        try {
            if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
            if (!Launch.autoOpenEnabled(this)) return
            val pkg = e.packageName?.toString() ?: return
            if (pkg !in homePackages) return
            val cls = e.className?.toString().orEmpty()
            // Only the original HOME screen itself, not other screens that live in the same package.
            val full = if (cls.startsWith(".")) pkg + cls else cls
            val isHome = homeActivities.contains("$pkg/$full") ||
                homeActivities.any { it.startsWith("$pkg/") && it.substringAfterLast('.') == full.substringAfterLast('.') }
            if (!isHome) return
            val now = SystemClock.elapsedRealtime()
            if (now - last < 1200) return
            last = now
            Launch.front(this, delayMs = 0, force = true)
        } catch (t: Throwable) {
            CrashLog.record(this, t, fatal = false, thread = "guard")
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        Hub.guardConnected = false
        super.onDestroy()
    }
}
