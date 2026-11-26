package com.el7ajb.carlauncher

import android.content.Context
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Car downloads from the public drive-cars repo on GitHub. Read-only: the app never holds a token.
 *  - c/<CODE>.carpack : one customer's car, downloaded by its short code
 *  - catalog.json     : optional list of public cars
 */
object Catalog {
    const val BASE = "https://raw.githubusercontent.com/jubyuhanna-create/drive-cars/main/"

    data class Entry(
        val id: String, val brand: String, val model: String, val year: String,
        val file: String, val size: Long, val rev: Int,
    )

    sealed class Result {
        data class Ok(val car: Car) : Result()
        object NotFound : Result()
        object BadPack : Result()
        object Network : Result()
    }

    fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000; readTimeout = 25_000; useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
            instanceFollowRedirects = true
        }

    fun cleanCode(s: String): String = s.uppercase().filter { it in 'A'..'Z' || it in '0'..'9' }.take(10)

    /** Blocking. Downloads the car for this code and installs it. */
    fun byCode(ctx: Context, code: String, progress: (Float) -> Unit): Result {
        val c = cleanCode(code)
        if (c.length < 4) return Result.NotFound
        return download(ctx, BASE + "c/" + c + ".carpack?t=" + System.currentTimeMillis() / 60_000, 0L, null, progress)
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

    /** Blocking: downloads and installs one catalog car. Returns the installed car or null. */
    fun install(ctx: Context, e: Entry, progress: (Float) -> Unit): Car? =
        (download(ctx, BASE + "cars/" + e.file + "?r=" + e.rev, e.size, e.rev, progress) as? Result.Ok)?.car

    private fun download(ctx: Context, url: String, sizeHint: Long, rev: Int?, progress: (Float) -> Unit): Result {
        val tmp = File(ctx.cacheDir, "pack.tmp")
        try {
            val c = open(url)
            try {
                val code = c.responseCode
                if (code == 404) return Result.NotFound
                if (code != 200) return Result.Network
                val total = if (c.contentLength > 0) c.contentLength.toLong() else sizeHint
                c.inputStream.use { ins ->
                    tmp.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var read = 0L
                        var last = 0L
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n); read += n
                            if (total > 0 && read - last > 64 * 1024) { last = read; progress((read.toFloat() / total).coerceIn(0f, 1f)) }
                        }
                    }
                }
            } finally { c.disconnect() }
            progress(1f)
            val car = tmp.inputStream().use { CarRepo.importStream(ctx, it, rev) }
            return if (car != null) Result.Ok(car) else Result.BadPack
        } catch (_: Throwable) {
            return Result.Network
        } finally {
            tmp.delete()
        }
    }
}
