package com.el7ajb.carlauncher

import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.session.MediaController
import kotlinx.coroutines.flow.MutableStateFlow

/** Shared live state between the notification service and the UI. */
object Hub {
    data class Media(
        val title: String = "",
        val artist: String = "",
        val art: Bitmap? = null,
        val playing: Boolean = false,
        val duration: Long = 0,
        val position: Long = 0,
        val posTime: Long = 0,
        val speed: Float = 1f,
        val pkg: String = "",
    )

    data class Nav(val title: String, val text: String, val icon: Bitmap?, val pkg: String)

    data class Notif(
        val id: Long,
        val pkg: String,
        val app: String,
        val title: String,
        val text: String,
        val icon: Bitmap?,
        val intent: PendingIntent?,
    )

    val media = MutableStateFlow<Media?>(null)
    val nav = MutableStateFlow<Nav?>(null)
    val notif = MutableStateFlow<Notif?>(null)
    val listenerConnected = MutableStateFlow(false)
    val speedKmh = MutableStateFlow<Int?>(null)
    val gpsFix = MutableStateFlow(false)
    val bootTick = MutableStateFlow(0)

    @Volatile
    var controller: MediaController? = null
}

fun Drawable.toBitmapSafe(size: Int): Bitmap? = try {
    if (this is BitmapDrawable && bitmap != null) {
        val b = bitmap
        if (b.width <= size && b.height <= size) b
        else Bitmap.createScaledBitmap(b, size, (size * b.height / b.width.coerceAtLeast(1)).coerceAtLeast(1), true)
    } else {
        val w = if (intrinsicWidth > 0) intrinsicWidth.coerceAtMost(size) else size
        val h = if (intrinsicHeight > 0) intrinsicHeight.coerceAtMost(size) else size
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        setBounds(0, 0, w, h)
        draw(c)
        bmp
    }
} catch (e: Throwable) {
    null
}

fun Bitmap.scaledMax(max: Int): Bitmap {
    if (width <= max && height <= max) return this
    val r = minOf(max.toFloat() / width, max.toFloat() / height)
    return Bitmap.createScaledBitmap(this, (width * r).toInt().coerceAtLeast(1), (height * r).toInt().coerceAtLeast(1), true)
}
