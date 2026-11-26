package com.el7ajb.carlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

data class CarColor(val k: String, val n: String, val hex: Int)

/** 360° turntable frames shipped inside a v2 car pack. Frame files: spin/<row>_<index>.webp */
data class Spin(
    val n: Int,                 // frames per full turn
    val rows: List<Float>,      // camera elevation of each row, low to high
    val w: Int, val h: Int,
    val startRow: Int, val startIdx: Int,   // the frame that matches the home picture
)

data class Car(
    val id: String,
    val brand: String,
    val model: String,
    val year: String,
    val colors: List<CarColor>,
    val builtin: Boolean,
    val rev: Int = 0,
    val spin: Spin? = null,
)

/**
 * Cars live only in filesDir/cars/<id>/. The app ships empty: every customer gets just his own car,
 * downloaded by code (or copied from a USB stick), so the screen stays light.
 */
object CarRepo {

    private val ID_OK = Regex("[^a-zA-Z0-9_-]")

    private fun parseSpin(o: JSONObject?): Spin? = try {
        if (o == null) null else {
            val rows = o.getJSONArray("rows").let { a -> (0 until a.length()).map { a.getDouble(it).toFloat() } }
            val n = o.getInt("n")
            val st = o.optJSONArray("start")
            if (n < 8 || n > 360 || rows.isEmpty() || rows.size > 5) null
            else Spin(n, rows, o.optInt("w", 1024), o.optInt("h", 576),
                (st?.optInt(0, 0) ?: 0).coerceIn(0, rows.size - 1), (st?.optInt(1, 0) ?: 0).coerceIn(0, n - 1))
        }
    } catch (_: Throwable) { null }

    private fun parseMeta(o: JSONObject, builtin: Boolean): Car? = try {
        val cols = if (o.has("colors")) {
            val arr = o.getJSONArray("colors")
            (0 until arr.length()).map {
                val c = arr.getJSONObject(it)
                CarColor(c.getString("k"), c.optString("n"), android.graphics.Color.parseColor(c.getString("hex")))
            }
        } else {
            val c = o.getJSONObject("color")
            listOf(CarColor(c.getString("k"), c.optString("n"), android.graphics.Color.parseColor(c.getString("hex"))))
        }
        if (cols.isEmpty()) null
        else Car(o.getString("id"), o.getString("brand"), o.getString("model"), o.optString("year"), cols, builtin,
            o.optInt("rev", 0), parseSpin(o.optJSONObject("spin")))
    } catch (_: Throwable) { null }

    private fun userDir(ctx: Context) = File(ctx.filesDir, "cars").apply { mkdirs() }
    fun dir(ctx: Context, car: Car) = File(userDir(ctx), car.id)

    fun load(ctx: Context): List<Car> {
        val out = LinkedHashMap<String, Car>()
        userDir(ctx).listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.lastModified() }?.forEach { d ->
            val f = File(d, "meta.json")
            if (f.exists()) try { parseMeta(JSONObject(f.readText()), false)?.let { out[it.id] = it } } catch (_: Throwable) {}
        }
        return out.values.toList()
    }

    fun bitmap(ctx: Context, car: Car, key: String): Bitmap? = try {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val f = File(dir(ctx, car), "$key.webp")
        if (f.exists()) BitmapFactory.decodeFile(f.absolutePath, opts) else null
    } catch (_: Throwable) { null }

    fun spinFile(ctx: Context, car: Car, row: Int, idx: Int) = File(dir(ctx, car), "spin/${row}_${idx}.webp")

    /** Imports a car pack from a USB stick / file manager. v2 packs are ZIP files, v1 packs are JSON. */
    fun import(ctx: Context, uri: Uri): Car? = try {
        ctx.contentResolver.openInputStream(uri)?.use { importStream(ctx, it, null) }
    } catch (_: Throwable) { null }

    fun importStream(ctx: Context, raw: InputStream, rev: Int?): Car? {
        val ins = BufferedInputStream(raw, 64 * 1024)
        ins.mark(4)
        val head = ByteArray(2)
        val n = ins.read(head)
        ins.reset()
        return if (n == 2 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()) importZip(ctx, ins, rev)
        else importText(ctx, ins.bufferedReader().use { it.readText() }, rev)
    }

    private val ZIP_NAME = Regex("^(meta\\.json|[a-zA-Z0-9_]+\\.webp|spin/[0-9]{1,2}_[0-9]{1,3}\\.webp)$")

    /** carpack-2: meta.json + <color>_off.webp + <color>_on.webp + spin/<row>_<i>.webp */
    private fun importZip(ctx: Context, ins: InputStream, rev: Int?): Car? {
        val tmp = File(userDir(ctx), ".incoming").apply { deleteRecursively(); mkdirs() }
        try {
            var total = 0L
            var count = 0
            ZipInputStream(ins).use { z ->
                val buf = ByteArray(32 * 1024)
                while (true) {
                    val e = z.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.trimStart('/')
                    if (!ZIP_NAME.matches(name)) continue
                    if (++count > 2000) return null
                    val f = File(tmp, name)
                    f.parentFile?.mkdirs()
                    f.outputStream().use { o ->
                        while (true) {
                            val r = z.read(buf)
                            if (r < 0) break
                            total += r
                            if (total > 120L * 1024 * 1024) return null
                            o.write(buf, 0, r)
                        }
                    }
                }
            }
            val metaF = File(tmp, "meta.json")
            if (!metaF.exists()) return null
            val o = JSONObject(metaF.readText())
            if (o.optString("format") != "carpack-2") return null
            if (rev != null) o.put("rev", rev)
            val id = o.getString("id").replace(ID_OK, "_")
            o.put("id", id)
            val car = parseMeta(o, false) ?: return null
            if (car.colors.none { File(tmp, it.k + "_off.webp").exists() }) return null
            val sp = car.spin
            if (sp != null && !File(tmp, "spin/${sp.startRow}_${sp.startIdx}.webp").exists()) o.remove("spin")
            metaF.writeText(o.toString())
            val dest = File(userDir(ctx), id)
            dest.deleteRecursively()
            if (!tmp.renameTo(dest)) { tmp.copyRecursively(dest, true); tmp.deleteRecursively() }
            dest.setLastModified(System.currentTimeMillis())
            return parseMeta(o, false)
        } catch (_: Throwable) {
            return null
        } finally {
            tmp.deleteRecursively()
        }
    }

    /** carpack-1 (JSON with base64 pictures), kept so old packs still work. */
    fun importText(ctx: Context, txt: String, rev: Int? = null): Car? {
        return try {
            val o = JSONObject(txt)
            if (rev != null) o.put("rev", rev)
            if (o.optString("format") != "carpack-1") return null
            val id = o.getString("id").replace(ID_OK, "_")
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
        File(userDir(ctx), id.replace(ID_OK, "_")).deleteRecursively()
    }
}
