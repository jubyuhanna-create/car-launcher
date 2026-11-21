package com.el7ajb.carlauncher

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotifService : NotificationListenerService() {

    private val main = Handler(Looper.getMainLooper())
    private var msm: MediaSessionManager? = null
    private var current: MediaController? = null
    private var counter = 0L

    private val cb = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) { maybeSwitch(); push() }
        override fun onMetadataChanged(metadata: MediaMetadata?) { push() }
        override fun onSessionDestroyed() { refreshSessions() }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list) }

    override fun onListenerConnected() {
        Hub.listenerConnected.value = true
        msm = getSystemService(MEDIA_SESSION_SERVICE) as? MediaSessionManager
        try {
            msm?.addOnActiveSessionsChangedListener(sessionsListener, ComponentName(this, NotifService::class.java), main)
        } catch (_: Throwable) {}
        refreshSessions()
        try { activeNotifications?.forEach { handleNav(it) } } catch (_: Throwable) {}
    }

    override fun onListenerDisconnected() {
        Hub.listenerConnected.value = false
        try { msm?.removeOnActiveSessionsChangedListener(sessionsListener) } catch (_: Throwable) {}
        try { current?.unregisterCallback(cb) } catch (_: Throwable) {}
        current = null
        Hub.controller = null
    }

    private fun refreshSessions() {
        val list = try { msm?.getActiveSessions(ComponentName(this, NotifService::class.java)) } catch (_: Throwable) { null }
        pick(list)
    }

    /** If another session starts playing while ours is paused, switch to it. */
    private fun maybeSwitch() {
        val cur = current
        if (cur?.playbackState?.state == PlaybackState.STATE_PLAYING) return
        refreshSessions()
    }

    private fun pick(list: List<MediaController>?) {
        val l = list ?: emptyList()
        val best = l.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: l.firstOrNull { it.sessionToken == current?.sessionToken }
            ?: l.firstOrNull()
        if (best?.sessionToken != current?.sessionToken) {
            try { current?.unregisterCallback(cb) } catch (_: Throwable) {}
            current = best
            try { best?.registerCallback(cb, main) } catch (_: Throwable) {}
        }
        Hub.controller = current
        push()
    }

    private fun push() {
        val c = current
        if (c == null) { Hub.media.value = null; return }
        val md = c.metadata
        val st = c.playbackState
        val art: Bitmap? = try {
            (md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
                ?: md?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON))?.scaledMax(320)
        } catch (_: Throwable) { null }
        Hub.media.value = Hub.Media(
            title = md?.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE) ?: "",
            artist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
                ?: md?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: "",
            art = art,
            playing = st?.state == PlaybackState.STATE_PLAYING,
            duration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            position = st?.position ?: 0L,
            posTime = st?.lastPositionUpdateTime ?: 0L,
            speed = st?.playbackSpeed ?: 1f,
            pkg = c.packageName ?: "",
        )
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        try {
            if (handleNav(sbn)) return
            handlePopup(sbn)
        } catch (_: Throwable) {}
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName in NAV_PKGS && Hub.nav.value?.pkg == sbn.packageName) {
            val still = try {
                activeNotifications?.any { it.packageName == sbn.packageName && it.isOngoing }
            } catch (_: Throwable) { false }
            if (still != true) Hub.nav.value = null
        }
    }

    private fun largeIcon(n: Notification): Bitmap? {
        try {
            val ic = n.getLargeIcon()
            if (ic != null) return ic.loadDrawable(this)?.toBitmapSafe(192)
        } catch (_: Throwable) {}
        return try {
            @Suppress("DEPRECATION")
            (n.extras?.get(Notification.EXTRA_LARGE_ICON) as? Bitmap)?.scaledMax(192)
        } catch (_: Throwable) { null }
    }

    private fun handleNav(sbn: StatusBarNotification): Boolean {
        if (sbn.packageName !in NAV_PKGS) return false
        if (!sbn.isOngoing) return true
        val ex = sbn.notification.extras ?: return true
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_SUB_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return true
        Hub.nav.value = Hub.Nav(title, text, largeIcon(sbn.notification), sbn.packageName)
        return true
    }

    private fun handlePopup(sbn: StatusBarNotification) {
        if (sbn.isOngoing) return
        val pkg = sbn.packageName
        if (pkg == packageName || pkg in IGNORE) return
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        if (n.category == Notification.CATEGORY_TRANSPORT || n.category == Notification.CATEGORY_PROGRESS || n.category == Notification.CATEGORY_SERVICE) return
        val ex = n.extras ?: return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_TEXT) ?: ex.getCharSequence(Notification.EXTRA_BIG_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val pm = packageManager
        val app = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (_: PackageManager.NameNotFoundException) { pkg }
        val icon = largeIcon(n) ?: try { pm.getApplicationIcon(pkg).toBitmapSafe(128) } catch (_: Throwable) { null }
        counter++
        Hub.notif.value = Hub.Notif(counter, pkg, app, title, text, icon, n.contentIntent)
    }

    companion object {
        val NAV_PKGS = setOf("com.waze", "com.google.android.apps.maps", "com.google.android.apps.mapslite")
        val IGNORE = setOf("android", "com.android.systemui", "com.android.vending", "com.google.android.gms", "com.android.providers.downloads")
    }
}
