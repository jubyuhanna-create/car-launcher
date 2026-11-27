package com.el7ajb.carlauncher

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.util.zip.ZipInputStream

/**
 * A customer build carries his car inside the APK: assets/bundle/<color>.carpack, one pack per color
 * of the same model. On first start the driver picks the color and only that pack is unpacked, so the
 * screen works with no internet and no code. Normal builds have no bundle and nothing here runs.
 */
object Bundle {

    data class Item(val asset: String, val id: String, val brand: String, val model: String, val year: String, val color: CarColor)

    @Volatile private var cache: List<Item>? = null

    fun list(ctx: Context): List<Item> {
        cache?.let { return it }
        val out = ArrayList<Item>()
        try {
            ctx.assets.list("bundle")?.filter { it.endsWith(".carpack") }?.sorted()?.forEach { name ->
                try {
                    ZipInputStream(ctx.assets.open("bundle/$name")).use { z ->
                        while (true) {
                            val e = z.nextEntry ?: break
                            if (e.name == "meta.json") {
                                val o = JSONObject(z.readBytes().toString(Charsets.UTF_8))
                                val c = o.getJSONObject("color")
                                out.add(Item(name, o.getString("id"), o.getString("brand"), o.getString("model"), o.optString("year"),
                                    CarColor(c.getString("k"), c.optString("n"), android.graphics.Color.parseColor(c.getString("hex")))))
                                break
                            }
                        }
                    }
                } catch (_: Throwable) {}
            }
        } catch (_: Throwable) {}
        // a fixed, familiar color order
        val order = listOf("white", "black", "graphite", "silver", "red", "blue")
        val sorted = out.sortedBy { order.indexOf(it.color.k).let { i -> if (i < 0) 99 else i } }
        cache = sorted
        return sorted
    }

    fun has(ctx: Context) = list(ctx).isNotEmpty()

    /** The lights-on home picture of one color, small, for the color chooser. */
    fun preview(ctx: Context, item: Item): Bitmap? = try {
        ZipInputStream(ctx.assets.open("bundle/" + item.asset)).use { z ->
            var bmp: Bitmap? = null
            while (true) {
                val e = z.nextEntry ?: break
                if (e.name == item.color.k + "_on.webp") {
                    val bytes = z.readBytes()
                    bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = 2 })
                    break
                }
            }
            bmp
        }
    } catch (_: Throwable) { null }

    /** Unpacks one color and removes any other color of the bundled car, so the screen keeps just one. */
    fun install(ctx: Context, item: Item): Car? {
        val car = try { ctx.assets.open("bundle/" + item.asset).use { CarRepo.importStream(ctx, it, null) } } catch (_: Throwable) { null }
            ?: return null
        list(ctx).filter { it.id != car.id }.forEach { CarRepo.delete(ctx, it.id) }
        return car
    }
}
