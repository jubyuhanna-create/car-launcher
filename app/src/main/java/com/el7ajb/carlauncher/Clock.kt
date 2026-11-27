package com.el7ajb.carlauncher

import android.content.Context
import java.util.TimeZone

/**
 * Chinese head units often ship set to China time (or UTC), so at 21:00 in Nazareth the screen says 03:00.
 * Drive shows Israel time when the unit is clearly in the wrong zone: GPS says it is in Israel/Palestine,
 * or (before any GPS fix) the zone is one of the usual factory defaults. Other apps still follow the system
 * clock, so Settings also offers a one-tap fix.
 */
object Clock {
    private val LOCAL = setOf("Asia/Jerusalem", "Asia/Tel_Aviv", "Israel", "Asia/Hebron", "Asia/Gaza")
    private val FACTORY = setOf("Asia/Shanghai", "Asia/Chongqing", "Asia/Harbin", "Asia/Urumqi", "PRC", "Asia/Hong_Kong",
        "Asia/Taipei", "Asia/Singapore", "Asia/Kuala_Lumpur", "GMT", "UTC", "Etc/UTC", "Etc/GMT", "America/Los_Angeles", "America/New_York")
    private val ISRAEL: TimeZone = TimeZone.getTimeZone("Asia/Jerusalem")

    fun systemIsLocal() = TimeZone.getDefault().id in LOCAL

    /** true = GPS put us in Israel/Palestine, false = somewhere else, null = no fix yet */
    private fun inRegion(ctx: Context): Boolean? {
        val p = ctx.getSharedPreferences("cl", 0)
        return if (p.contains("inIL")) p.getBoolean("inIL", false) else null
    }

    fun onLocation(ctx: Context, lat: Double, lon: Double) {
        val inside = lat in 29.3..33.5 && lon in 34.1..35.95
        val p = ctx.getSharedPreferences("cl", 0)
        if (!p.contains("inIL") || p.getBoolean("inIL", false) != inside) p.edit().putBoolean("inIL", inside).apply()
    }

    /** The zone Drive shows its clock and greeting in. */
    fun zone(ctx: Context): TimeZone {
        val sys = TimeZone.getDefault()
        if (sys.id in LOCAL) return sys
        return when (inRegion(ctx)) {
            true -> ISRAEL
            false -> sys
            null -> if (sys.id in FACTORY) ISRAEL else sys
        }
    }

    /** The system zone is wrong for where the car is: worth fixing in Android settings. */
    fun systemWrong(ctx: Context) = zone(ctx).id != TimeZone.getDefault().id
}
