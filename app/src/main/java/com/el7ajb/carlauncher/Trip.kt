package com.el7ajb.carlauncher

import android.content.Context
import android.location.Location
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow

/** Trip computer from GPS alone (no OBD): distance, driving time and top speed of this drive. */
object Trip {
    data class State(val km: Double = 0.0, val sec: Long = 0, val maxKmh: Int = 0)

    val state = MutableStateFlow(State())
    private var last: Location? = null
    private var lastT = 0L
    private var lastSave = 0L
    private var loaded = false

    fun load(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = ctx.getSharedPreferences("cl", 0)
        val idle = System.currentTimeMillis() - p.getLong("trip_seen", 0L)
        // Car switched off for more than 20 minutes = a new drive.
        if (idle > 20 * 60_000L) reset(ctx)
        else state.value = State(p.getFloat("trip_km", 0f).toDouble(), p.getLong("trip_sec", 0L), p.getInt("trip_max", 0))
    }

    fun onWake(ctx: Context) {
        val p = ctx.getSharedPreferences("cl", 0)
        if (System.currentTimeMillis() - p.getLong("trip_seen", 0L) > 20 * 60_000L) reset(ctx)
        last = null
    }

    fun reset(ctx: Context) {
        state.value = State()
        last = null
        save(ctx, force = true)
    }

    fun onLocation(ctx: Context, loc: Location) {
        try {
            val now = SystemClock.elapsedRealtime()
            val kmh = if (loc.hasSpeed()) loc.speed * 3.6f else 0f
            val prev = last
            var s = state.value
            if (prev != null && (!loc.hasAccuracy() || loc.accuracy < 60f) && kmh > 3f) {
                val d = prev.distanceTo(loc)
                val dt = ((now - lastT) / 1000L).coerceIn(0L, 10L)
                if (d < 400f) s = s.copy(km = s.km + d / 1000.0, sec = s.sec + dt)
            }
            if (kmh.toInt() > s.maxKmh && kmh < 260f) s = s.copy(maxKmh = kmh.toInt())
            state.value = s
            last = loc; lastT = now
            save(ctx, force = false)
        } catch (_: Throwable) {}
    }

    private fun save(ctx: Context, force: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastSave < 20_000L) return
        lastSave = now
        val s = state.value
        ctx.getSharedPreferences("cl", 0).edit()
            .putFloat("trip_km", s.km.toFloat()).putLong("trip_sec", s.sec).putInt("trip_max", s.maxKmh)
            .putLong("trip_seen", System.currentTimeMillis()).apply()
    }
}
