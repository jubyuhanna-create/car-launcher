package com.el7ajb.carlauncher

import android.content.Context
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online car library. New cars are published to a public GitHub repo; every installed screen
 * lists them and downloads only the car the driver picks. No app update needed.
 */
object Catalog {
    private const val BASE = "https://raw.githubusercontent.com/jubyuhanna-create/drive-cars/main/"

    data class Entry(
        val id: String, val brand: String, val model: String, val year: String,
        val file: String, val size: Long, val rev: Int,
    )

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000; useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            instanceFollowRedirects = true
        }

    /** Blocking: call from a background thread. Throws on network errors. */
    fun fetch(): List<Entry> {
        val c = open(BASE + "catalog.json?t=" + System.currentTimeMillis() / 60_000)
        try {
            if (c.responseCode != 200) throw IllegalStateException("HTTP ${c.responseCode}")
            val txt = c.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONObject(txt).getJSONArray("cars")
            return (0 until arr.length()).mapNotNull { i ->
                try {
                    val o = arr.getJSONObject(i)
                    Entry(o.getString("id"), o.getString("brand"), o.getString("model"), o.optString("year"),
                        o.getString("file"), o.optLong("size", 0L), o.optInt("rev", 1))
                } catch (_: Throwable) { null }
            }
        } finally { c.disconnect() }
    }

    /** Blocking: downloads and installs one car. Returns the installed car or null. */
    fun install(ctx: Context, e: Entry, progress: (Float) -> Unit): Car? {
        val c = open(BASE + "cars/" + e.file + "?r=" + e.rev)
        try {
            if (c.responseCode != 200) return null
            val total = if (c.contentLength > 0) c.contentLength.toLong() else e.size
            val out = ByteArrayOutputStream(if (total > 0) total.toInt() else 1 shl 20)
            c.inputStream.use { ins ->
                val buf = ByteArray(32 * 1024)
                var read = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n); read += n
                    if (total > 0) progress((read.toFloat() / total).coerceIn(0f, 1f))
                }
            }
            return CarRepo.importText(ctx, out.toString("UTF-8"), e.rev)
        } finally { c.disconnect() }
    }
}
