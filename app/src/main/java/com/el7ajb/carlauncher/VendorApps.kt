package com.el7ajb.carlauncher

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Finds the head unit's own apps (Bluetooth phone, BT music, radio, car settings...) by label
 * or package name, because every manufacturer uses different package names.
 */
object VendorApps {

    data class Spec(
        val id: String,
        val labels: List<String>,
        val pkgWords: List<String> = emptyList(),
        val exclude: List<String> = emptyList(),
        val packages: List<String> = emptyList(),
    )

    val SPECS = listOf(
        Spec("phone", listOf("bluetooth", "bt phone", "phone", "bluetooth phone", "hands free", "بلوتوث", "בלוטות'"),
            listOf("bluetooth", "btphone", "bt.phone", "bthfp", "dialer"), listOf("music", "audio", "a2dp", "settings")),
        Spec("btmusic", listOf("bt music", "bluetooth music", "btmusic", "a2dp"), listOf("btmusic", "bt.music", "a2dp", "btaudio")),
        Spec("radio", listOf("radio", "fm", "fm radio", "راديو", "רדיו"), listOf("radio", ".fm"), listOf("internet", "tunein")),
        Spec("music", listOf("music", "media", "local music", "player"), listOf("music", "mediaplayer"),
            listOf("bt", "bluetooth", "spotify", "youtube", "soundcloud", "a2dp")),
        Spec("video", listOf("video", "videos", "movie"), listOf("video"), listOf("youtube", "netflix")),
        Spec("nav", emptyList(), packages = listOf("com.waze", "com.google.android.apps.maps")),
        Spec("youtube", emptyList(), packages = listOf("com.google.android.youtube", "com.google.android.apps.youtube.music")),
        Spec("zlink", listOf("zlink", "zlink5", "carplay", "android auto", "autolink", "carbit"), listOf("zlink", "carplay", "autolink", "carbit", "auto.link")),
        Spec("camera", listOf("av in", "avin", "camera", "reverse", "dvr", "cvbs"), listOf("avin", "av_in", "camera", "dvr", "cvbs")),
        Spec("carset", listOf("car settings", "car setting", "carsettings", "vehicle settings", "canbus"), listOf("carsetting", "car.setting", "canbus", "vehiclesetting")),
        Spec("dsp", listOf("dsp", "eq", "equalizer", "sound", "audio settings"), listOf("dsp", "equalizer", ".eq")),
        Spec("fan", listOf("fan", "ac", "air", "climate", "aircon"), listOf("aircon", "climate", ".fan", "hvac")),
        Spec("files", listOf("file manager", "files", "my files"), listOf("filemanager", "documentsui", "fileexplorer")),
    )

    @Volatile private var cache: Map<String, ComponentName>? = null

    fun resolveAll(ctx: Context, force: Boolean = false): Map<String, ComponentName> {
        if (!force) cache?.let { return it }
        val pm = ctx.packageManager
        val list = try {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        } catch (_: Throwable) { emptyList() }
        val apps = list.filter { it.activityInfo.packageName != ctx.packageName }.map {
            Triple(it.loadLabel(pm).toString().trim().lowercase(), it.activityInfo.packageName.lowercase(),
                ComponentName(it.activityInfo.packageName, it.activityInfo.name))
        }
        val out = HashMap<String, ComponentName>()
        for (s in SPECS) {
            val ok = apps.filter { (label, pkg, _) -> s.exclude.none { label.contains(it) || pkg.contains(it) } }
            val hit =
                s.packages.firstNotNullOfOrNull { p -> apps.firstOrNull { it.second == p } }
                    ?: s.labels.firstNotNullOfOrNull { l -> ok.firstOrNull { it.first == l } }
                    ?: s.labels.filter { it.length >= 4 }.firstNotNullOfOrNull { l -> ok.firstOrNull { it.first.contains(l) } }
                    ?: s.pkgWords.firstNotNullOfOrNull { w -> ok.firstOrNull { it.second.contains(w) } }
            if (hit != null) out[s.id] = hit.third
        }
        cache = out
        return out
    }

    fun open(act: BaseActivity, id: String): Boolean {
        val cn = resolveAll(act)[id] ?: return false
        return act.tryStart(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(cn))
    }
}
