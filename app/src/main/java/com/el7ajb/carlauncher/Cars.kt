package com.el7ajb.carlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.File

data class CarColor(val k: String, val n: String, val hex: Int)

data class Car(
    val id: String,
    val brand: String,
    val model: String,
    val year: String,
    val colors: List<CarColor>,
    val builtin: Boolean,
)

object CarRepo {

    private fun parseMeta(o: JSONObject, builtin: Boolean): Car? = try {
        val arr = o.getJSONArray("colors")
        val cols = (0 until arr.length()).map {
            val c = arr.getJSONObject(it)
            CarColor(c.getString("k"), c.optString("n"), android.graphics.Color.parseColor(c.getString("hex")))
        }
        Car(o.getString("id"), o.getString("brand"), o.getString("model"), o.optString("year"), cols, builtin)
    } catch (_: Throwable) { null }

    private fun userDir(ctx: Context) = File(ctx.filesDir, "cars").apply { mkdirs() }

    fun load(ctx: Context): List<Car> {
        val out = LinkedHashMap<String, Car>()
        val order = listOf("superb", "octavia", "corolla", "golfgti", "polo", "ibiza", "leon")
        val ids = (ctx.assets.list("cars") ?: emptyArray()).sortedBy { order.indexOf(it).let { i -> if (i < 0) 99 else i } }
        for (id in ids) {
            try {
                val txt = ctx.assets.open("cars/$id/meta.json").bufferedReader().use { it.readText() }
                parseMeta(JSONObject(txt), true)?.let { out[it.id] = it }
            } catch (_: Throwable) {}
        }
        userDir(ctx).listFiles()?.sortedBy { it.lastModified() }?.forEach { d ->
            val f = File(d, "meta.json")
            if (f.exists()) try { parseMeta(JSONObject(f.readText()), false)?.let { out[it.id] = it } } catch (_: Throwable) {}
        }
        return out.values.toList()
    }

    fun bitmap(ctx: Context, car: Car, key: String): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        if (car.builtin) ctx.assets.open("cars/${car.id}/$key.webp").use { BitmapFactory.decodeStream(it, null, opts) }
        else {
            val f = File(File(userDir(ctx), car.id), "$key.webp")
            if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, opts) else null
        }
    } catch (_: Throwable) { null }

    /** Imports a .carpack file (same format as the web prototype). Returns the car or null if invalid. */
    fun import(ctx: Context, uri: Uri): Car? {
        return try {
            val txt = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return null
            val o = JSONObject(txt)
            if (o.optString("format") != "carpack-1") return null
            val id = o.getString("id").replace(Regex("[^a-zA-Z0-9_-]"), "_")
            o.put("id", id)
            val car = parseMeta(o, false) ?: return null
            val imgs = o.getJSONObject("imgs")
            if (car.colors.none { imgs.has(it.k + "_off") }) return null
            val dir = File(userDir(ctx), id).apply { deleteRecursively(); mkdirs() }
            val keys = imgs.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                if (!k.matches(Regex("[a-zA-Z0-9_]+"))) continue
                val data = imgs.getString(k).substringAfter(",")
                File(dir, "$k.webp").writeBytes(Base64.decode(data, Base64.DEFAULT))
            }
            o.remove("imgs")
            File(dir, "meta.json").writeText(o.toString())
            car
        } catch (_: Throwable) { null }
    }

    fun delete(ctx: Context, id: String) {
        File(userDir(ctx), id).deleteRecursively()
    }
}
