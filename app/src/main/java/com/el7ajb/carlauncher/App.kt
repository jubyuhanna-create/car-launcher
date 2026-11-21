package com.el7ajb.carlauncher

import android.content.Intent
import android.graphics.Bitmap
import android.media.session.PlaybackState
import android.view.KeyEvent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Fg = Color(0xFFECEEF0)
private val Fg2 = Color(0xFFC9CCD0)
private val Muted = Color(0xFF7A7F86)
private val Dim = Color(0xFF4D5157)
private val CardBg = Color(0x09FFFFFF)
private val CardLine = Color(0x10FFFFFF)
private val Mint = Color(0xFF9FE1CB)

private enum class Screen { Home, Picker, Apps, Settings }

/** Premium press feedback: shrinks a little, springs back, and gives a light haptic tick. */
@Composable
private fun Modifier.press(scaleTo: Float = 0.93f, onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val sc by androidx.compose.animation.core.animateFloatAsState(
        if (pressed) scaleTo else 1f,
        androidx.compose.animation.core.spring(dampingRatio = 0.55f, stiffness = 700f), label = "press"
    )
    val view = androidx.compose.ui.platform.LocalView.current
    return this.graphicsLayer { scaleX = sc; scaleY = sc }
        .clickable(interactionSource = src, indication = null) {
            try { view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Throwable) {}
            onClick()
        }
}

private val GlassFill = Brush.verticalGradient(listOf(Color(0x14FFFFFF), Color(0x05FFFFFF)))
private val GlassEdge = Brush.verticalGradient(listOf(Color(0x33FFFFFF), Color(0x0AFFFFFF)))

private fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape): Modifier =
    this.clip(shape).background(GlassFill, shape).border(1.dp, GlassEdge, shape)

@Composable
private fun Modifier.tap(onClick: () -> Unit): Modifier {
    val src = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = src, indication = null, onClick = onClick)
}

@Composable
private fun Dp.sp(): TextUnit = with(LocalDensity.current) { this@sp.toSp() }

private fun defaultLang(): Lang = when (Locale.getDefault().language) {
    "iw", "he" -> Lang.HE
    "en" -> Lang.EN
    else -> Lang.AR
}

@Composable
fun App(act: BaseActivity, start: String, onExit: () -> Unit) {
    val prefs = remember { act.getSharedPreferences("cl", 0) }
    var lang by remember { mutableStateOf(Lang.values().firstOrNull { it.code == prefs.getString("lang", null) } ?: defaultLang()) }
    var cars by remember { mutableStateOf(CarRepo.load(act)) }
    var carId by remember { mutableStateOf(prefs.getString("car", "superb") ?: "superb") }
    var colorK by remember { mutableStateOf(prefs.getString("color", "graphite") ?: "graphite") }
    var screen by remember { mutableStateOf(when (start) { "picker" -> Screen.Picker; "apps" -> Screen.Apps; else -> Screen.Settings }) }
    var theme by remember { mutableStateOf(prefs.getString("theme", "car") ?: "car") }
    var onboarding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(prefs.getString("name", "") ?: "") }
    var perf by remember { mutableStateOf(prefs.getString("perf", "auto") ?: "auto") }
    val lite = when (perf) { "lite" -> true; "full" -> false; else -> act.isWeakDevice() }
    val car = cars.firstOrNull { it.id == carId } ?: cars.firstOrNull()
    val carCol = car?.colors?.firstOrNull { it.k == colorK } ?: car?.colors?.firstOrNull()
    val media by Hub.media.collectAsState()
    val art = media?.art
    val musicGlow by produceState<Color?>(null, art) { value = withContext(Dispatchers.Default) { paletteGlow(art) } }
    val carGlow = carCol?.let { glowFrom(it.hex) } ?: Color(0xFF6F8DB8)
    val target = when (theme) {
        "car" -> carGlow
        "music" -> musicGlow ?: carGlow
        else -> THEMES.firstOrNull { it.id == theme }?.color ?: carGlow
    }
    val accent by animateColorAsState(target, tween(1400), label = "accent")

    fun saveCar(id: String, k: String) {
        carId = id; colorK = k
        prefs.edit().putString("car", id).putString("color", k).apply()
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalContentColor provides Fg,
            LocalAccent provides accent,
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF040506))) {
                if (car == null) {
                    Text("No cars", Modifier.align(Alignment.Center))
                } else when (screen) {
                    Screen.Home -> { LaunchedEffect(Unit) { onExit() } }
                    Screen.Picker -> PickerScreen(act, lang, cars, car, colorK,
                        canGoBack = prefs.getBoolean("setup", false),
                        onSelect = { id, k -> saveCar(id, k) },
                        onCarsChanged = { cars = CarRepo.load(act) },
                        onStart = {
                            val first = !prefs.getBoolean("setup", false)
                            prefs.edit().putBoolean("setup", true).apply()
                            Hub.bootTick.value++
                            if (first) { onboarding = true; screen = Screen.Settings } else onExit()
                        },
                        onBack = { if (start == "picker") onExit() else screen = Screen.Settings })
                    Screen.Apps -> AppsScreen(act, lang, onBack = { screen = Screen.Home })
                    Screen.Settings -> SettingsScreen(act, lang, car, carGlow, theme, name, perf, onboarding,
                        onPerf = { perf = it; prefs.edit().putString("perf", it).apply() },
                        onTheme = { theme = it; prefs.edit().putString("theme", it).apply() },
                        onName = { name = it; prefs.edit().putString("name", it).apply() },
                        onLang = { lang = it; prefs.edit().putString("lang", it.code).apply() },
                        onPicker = { screen = Screen.Picker },
                        onBack = { screen = Screen.Home })
                }
                NotificationPopup(lang)
            }
        }
    }
}

/* ---------------------------------- car image ---------------------------------- */

private class CarImgs(val off: ImageBitmap?, val on: ImageBitmap?, val refl: ImageBitmap?)

private fun shrink(b: Bitmap?, maxW: Int): Bitmap? {
    b ?: return null
    if (b.width <= maxW) return b
    val h = (b.height * maxW.toFloat() / b.width).toInt().coerceAtLeast(1)
    return Bitmap.createScaledBitmap(b, maxW, h, true)
}

/** Mirror + fade the car once, so the reflection costs nothing while drawing. */
private fun bakeReflection(src: Bitmap): Bitmap? = try {
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val c = android.graphics.Canvas(out)
    val m = android.graphics.Matrix().apply { setScale(1f, -1f); postTranslate(0f, src.height.toFloat()) }
    c.drawBitmap(src, m, null)
    val p = android.graphics.Paint().apply {
        shader = android.graphics.LinearGradient(0f, 0f, 0f, src.height * 0.38f,
            android.graphics.Color.BLACK, android.graphics.Color.TRANSPARENT, android.graphics.Shader.TileMode.CLAMP)
        xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
    }
    c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), p)
    out
} catch (_: Throwable) { null }

@Composable
private fun rememberCarImages(act: BaseActivity, car: Car, k: String, maxW: Int = 760): CarImgs {
    val st = produceState(CarImgs(null, null, null), car.id, k) {
        value = withContext(Dispatchers.IO) {
            val key = if (car.colors.any { it.k == k }) k else car.colors.first().k
            val off = shrink(CarRepo.bitmap(act, car, key + "_off"), maxW)
            val on = shrink(CarRepo.bitmap(act, car, key + "_on"), maxW) ?: off
            val refl = off?.let { bakeReflection(it) }
            CarImgs(off?.asImageBitmap(), on?.asImageBitmap(), refl?.asImageBitmap())
        }
    }
    return st.value
}

@Composable
private fun CarView(imgs: CarImgs, lamp: Float, modifier: Modifier = Modifier, shine: (() -> Float)? = null) {
    val off = imgs.off
    val on = imgs.on
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
            if (off != null) {
                val refl = imgs.refl
                if (refl != null) Image(
                    refl, null,
                    Modifier.fillMaxSize().graphicsLayer { translationY = size.height * 0.93f },
                    alignment = Alignment.BottomCenter, contentScale = ContentScale.Fit, alpha = 0.16f
                )
                val carMod = if (shine == null) Modifier.fillMaxSize() else Modifier.fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        val p = shine()
                        if (p in 0f..1f) {
                            val x = -0.3f * size.width + p * 1.6f * size.width
                            drawRect(
                                Brush.linearGradient(
                                    0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.28f), 1f to Color.Transparent,
                                    start = Offset(x - size.width * 0.10f, 0f), end = Offset(x + size.width * 0.10f, size.height * 0.6f)
                                ),
                                blendMode = BlendMode.SrcAtop
                            )
                        }
                    }
                Box(carMod) {
                    Image(off, null, Modifier.fillMaxSize(), alignment = Alignment.BottomCenter, contentScale = ContentScale.Fit)
                    if (on != null && lamp > 0.01f)
                        Image(on, null, Modifier.fillMaxSize(), alignment = Alignment.BottomCenter, contentScale = ContentScale.Fit, alpha = lamp)
                }
            }
        }
    }
}

/* ---------------------------------- home ---------------------------------- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(act: BaseActivity, lang: Lang, car: Car, colorK: String, name: String, lite: Boolean, onApps: () -> Unit, onSettings: () -> Unit) {
    val imgs = rememberCarImages(act, car, colorK, if (lite) 640 else 760)
    val off = imgs.off
    val accent = LocalAccent.current
    val drl = remember { Animatable(0f) }
    val greetA = remember { Animatable(0f) }
    val carA = remember { Animatable(0f) }
    val lampA = remember { Animatable(0f) }
    val hudA = remember { Animatable(0f) }
    val glowA = remember { Animatable(0f) }
    val tick by Hub.bootTick.collectAsState()
    var localBoot by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val pager = rememberPagerState(pageCount = { 2 })

    LaunchedEffect(tick, localBoot, off != null) {
        if (off == null) return@LaunchedEffect
        drl.snapTo(0f); greetA.snapTo(0f); carA.snapTo(0f); lampA.snapTo(0f)
        if (hudA.value < 1f) hudA.snapTo(0f)
        if (lite) {
            glowA.snapTo(1f)
            scope.launch { greetA.animateTo(1f, tween(350)); delay(650); greetA.animateTo(0f, tween(300)) }
            scope.launch { drl.animateTo(1f, tween(900, easing = LinearEasing)) }
            scope.launch { delay(700); carA.animateTo(1f, tween(500)) }
            scope.launch { delay(1100); lampA.snapTo(1f) }
            scope.launch { delay(900); hudA.animateTo(1f, tween(400)) }
        } else {
            glowA.snapTo(0f)
            scope.launch { delay(150); greetA.animateTo(1f, tween(700)); delay(900); greetA.animateTo(0f, tween(600)) }
            scope.launch { delay(250); drl.animateTo(1f, tween(1500, easing = FastOutSlowInEasing)) }
            scope.launch { delay(1500); carA.animateTo(1f, tween(1400)) }
            scope.launch { delay(1700); glowA.animateTo(1f, tween(1600)) }
            scope.launch { delay(2500); lampA.animateTo(1f, tween(800)) }
            scope.launch { delay(2500); hudA.animateTo(1f, tween(1100)) }
        }
    }
    LaunchedEffect(tick) { if (pager.currentPage != 0) pager.scrollToPage(0) }

    val breath: State<Float>
    val shineT: State<Float>?
    if (lite) {
        breath = remember { mutableFloatStateOf(0.8f) }
        shineT = null
    } else {
        val inf = rememberInfiniteTransition(label = "ambient")
        breath = inf.animateFloat(0.55f, 1f, infiniteRepeatable(tween(4200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breath")
        shineT = inf.animateFloat(0f, 1f, infiniteRepeatable(tween(11000, easing = LinearEasing)), label = "shine")
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().drawWithCache {
            val w = size.width; val h = size.height
            val g = glowA.value
            val acc = accent
            val base = Brush.radialGradient(listOf(Color(0xFF111316), Color(0xFF08090B), Color(0xFF030405)), Offset(w / 2f, h * 0.5f), w * 0.7f)
            val glow = Brush.radialGradient(listOf(acc.copy(alpha = 0.20f * g), acc.copy(alpha = 0.06f * g), Color.Transparent), Offset(w / 2f, h * 0.45f), w * 0.42f)
            val spot = Brush.verticalGradient(0f to acc.copy(alpha = 0.10f * g), 0.45f to Color.Transparent, endY = h * 0.6f)
            val fy = h * 0.655f
            val vpy = h * 0.36f
            val grid = androidx.compose.ui.graphics.Path().apply {
                for (i in -14..14) {
                    val bx = w / 2f + i * w * 0.09f
                    val t = (fy - vpy) / (h - vpy)
                    moveTo(w / 2f + (bx - w / 2f) * t, fy); lineTo(bx, h)
                }
                var yy = fy; var step = h * 0.012f
                while (yy < h) { moveTo(0f, yy); lineTo(w, yy); yy += step; step *= 1.45f }
            }
            val floor = Brush.horizontalGradient(listOf(Color.Transparent, acc.copy(alpha = 0.35f * g), Color.White.copy(alpha = 0.18f), acc.copy(alpha = 0.35f * g), Color.Transparent))
            val gridStroke = androidx.compose.ui.graphics.drawscope.Stroke(1f)
            val gridCol = Color.White.copy(alpha = 0.035f * g)
            onDrawBehind {
                drawRect(base)
                if (g > 0f) { drawRect(glow); drawRect(spot) }
                drawPath(grid, gridCol, style = gridStroke)
                drawLine(floor, Offset(0f, fy), Offset(w, fy), 1.5f)
                val b = breath.value * g
                if (b > 0f) drawOval(
                    Brush.radialGradient(listOf(acc.copy(alpha = 0.55f * b), acc.copy(alpha = 0.12f * b), Color.Transparent), Offset(w / 2f, fy), w * 0.30f),
                    topLeft = Offset(w * 0.18f, fy - h * 0.07f), size = androidx.compose.ui.geometry.Size(w * 0.64f, h * 0.14f)
                )
            }
        }
    ) {
        val W = maxWidth
        val H = maxHeight

        HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            if (page == 0) {
                Box(Modifier.fillMaxSize()) {
                    val p = drl.value
                    if (p > 0f && p < 1f) {
                        val a = when { p < 0.2f -> p / 0.2f; p < 0.75f -> 1f; else -> (1f - p) / 0.25f }
                        Box(
                            Modifier.align(Alignment.TopCenter).offset(y = H * 0.50f)
                                .width(W * 0.62f * (p / 0.75f).coerceAtMost(1f)).height(2.dp).alpha(a)
                                .background(Brush.horizontalGradient(listOf(Color.Transparent, accent.mixWhite(0.7f), Color.White, accent.mixWhite(0.7f), Color.Transparent)))
                        )
                    }
                    if (greetA.value > 0.01f) {
                        Column(Modifier.align(Alignment.TopCenter).offset(y = H * 0.36f).alpha(greetA.value), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(greeting(lang), fontSize = (H * 0.055f).sp(), fontWeight = FontWeight.ExtraLight, color = Fg)
                            if (name.isNotBlank()) Text(name, fontSize = (H * 0.03f).sp(), color = accent.mixWhite(0.4f), letterSpacing = 2.sp)
                        }
                    }
                    Row(Modifier.align(Alignment.TopCenter).offset(y = H * 0.14f).alpha(hudA.value)) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(car.brand.uppercase(), fontSize = (H * 0.026f).sp(), color = Fg, letterSpacing = 6.sp)
                                Box(Modifier.padding(horizontal = 14.dp).size(4.dp).clip(CircleShape).background(accent))
                                Text(car.model.uppercase(), fontSize = (H * 0.026f).sp(), color = Muted, letterSpacing = 6.sp)
                            }
                        }
                    }
                    CarView(
                        imgs, lampA.value,
                        Modifier.offset(y = H * 0.20f).padding(start = W * 0.21f, end = W * 0.19f)
                            .fillMaxWidth().height(H * 0.46f).alpha(carA.value)
                            .tap { localBoot++ },
                        shine = shineT?.let { st -> { val t = st.value * 11000f; if (t < 1700f && carA.value > 0.99f) t / 1700f else -1f } }
                    )
                    Box(Modifier.fillMaxSize().alpha(hudA.value)) {
                        SpeedArcBlock(lang, Modifier.align(Alignment.TopStart).padding(start = W * 0.03f, top = H * 0.22f), H)
                        Column(Modifier.align(Alignment.TopEnd).padding(end = W * 0.04f, top = H * 0.25f).width(W * 0.20f)) {
                            MediaCard(act, lang, H)
                            Spacer(Modifier.height(H * 0.025f))
                            NavCard(act, lang, H)
                        }
                    }
                }
            } else {
                QuickTiles(act, lang, W, H, onApps)
            }
        }

        // global HUD: clock, page dots, dock
        Box(Modifier.fillMaxSize().alpha(hudA.value)) {
            TopBar(lang, W, H)
            Row(Modifier.align(Alignment.BottomCenter).padding(bottom = H * 0.155f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(2) { i ->
                    Box(Modifier.height(5.dp).width(if (pager.currentPage == i) 18.dp else 5.dp).clip(CircleShape)
                        .background(if (pager.currentPage == i) accent else Color(0x40FFFFFF)))
                }
            }
            Dock(act, lang, H,
                onHome = { if (pager.currentPage != 0) scope.launch { pager.animateScrollToPage(0) } else localBoot++ },
                onApps = onApps, onSettings = onSettings,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = H * 0.035f))
        }
    }
}

private data class Tile(val id: String, val key: String, val icon: ImageVector)

private val TILES = listOf(
    Tile("phone", "t_phone", Icons.Rounded.Bluetooth),
    Tile("btmusic", "t_btmusic", Icons.Rounded.BluetoothAudio),
    Tile("radio", "t_radio", Icons.Rounded.Radio),
    Tile("music", "t_music", Icons.Rounded.MusicNote),
    Tile("nav", "t_nav", Icons.Rounded.Navigation),
    Tile("zlink", "t_zlink", Icons.Rounded.PhonelinkRing),
    Tile("video", "t_video", Icons.Rounded.Movie),
    Tile("youtube", "t_youtube", Icons.Rounded.SmartDisplay),
    Tile("camera", "t_camera", Icons.Rounded.Videocam),
    Tile("carset", "t_carset", Icons.Rounded.DirectionsCar),
    Tile("dsp", "t_dsp", Icons.Rounded.GraphicEq),
    Tile("fan", "t_fan", Icons.Rounded.AcUnit),
    Tile("files", "t_files", Icons.Rounded.Folder),
)

@Composable
private fun QuickTiles(act: BaseActivity, lang: Lang, W: Dp, H: Dp, onApps: () -> Unit) {
    val accent = LocalAccent.current
    val found by produceState<Map<String, android.content.ComponentName>>(emptyMap()) {
        value = withContext(Dispatchers.IO) { VendorApps.resolveAll(act) }
    }
    val media by Hub.media.collectAsState()
    fun open(id: String) {
        if (id == "__apps") { onApps(); return }
        if (!VendorApps.open(act, id)) Toast.makeText(act, S.get("notInstalled", lang), Toast.LENGTH_SHORT).show()
    }
    val small = TILES.filter { it.id != "phone" && it.id != "btmusic" && found.containsKey(it.id) } +
        Tile("__apps", "t_apps", Icons.Rounded.Apps)

    Column(Modifier.fillMaxSize().padding(start = W * 0.05f, end = W * 0.05f, top = H * 0.17f, bottom = H * 0.19f)) {
        // hero row
        Row(Modifier.fillMaxWidth().weight(1.15f), horizontalArrangement = Arrangement.spacedBy(W * 0.015f)) {
            HeroTile(Modifier.weight(1f), Icons.Rounded.Bluetooth, S.get("t_phone", lang), S.get("bluetoothSub", lang), null, H) { open("phone") }
            val m = media
            val playing = m != null && m.title.isNotBlank()
            HeroTile(
                Modifier.weight(1f), if (playing) Icons.Rounded.GraphicEq else Icons.Rounded.BluetoothAudio,
                if (playing) m!!.title else S.get("t_btmusic", lang),
                if (playing) m!!.artist else S.get("tapMusic", lang),
                m?.art, H
            ) {
                val pkg = m?.pkg.orEmpty()
                if (!(pkg.isNotBlank() && act.launchAny(pkg))) { if (!VendorApps.open(act, "btmusic")) open("music") }
            }
        }
        Spacer(Modifier.height(H * 0.022f))
        // grid
        val rows = small.chunked(6)
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f).padding(bottom = H * 0.018f), horizontalArrangement = Arrangement.spacedBy(W * 0.012f)) {
                row.forEach { t ->
                    Column(
                        Modifier.weight(1f).fillMaxHeight().press(0.92f) { open(t.id) }.glass(RoundedCornerShape(20.dp)).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            Modifier.size(H * 0.068f).clip(CircleShape)
                                .background(Brush.radialGradient(listOf(accent.copy(alpha = 0.40f), accent.copy(alpha = 0.06f)))),
                            contentAlignment = Alignment.Center
                        ) { Icon(t.icon, null, tint = accent.mixWhite(0.6f), modifier = Modifier.size(H * 0.038f)) }
                        Spacer(Modifier.height(H * 0.012f))
                        Text(S.get(t.key, lang), fontSize = (H * 0.02f).sp(), color = Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                repeat(6 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun HeroTile(modifier: Modifier, icon: ImageVector, title: String, sub: String, art: android.graphics.Bitmap?, H: Dp, onClick: () -> Unit) {
    val accent = LocalAccent.current
    val shape = RoundedCornerShape(24.dp)
    val img = remember(art) { art?.asImageBitmap() }
    Box(modifier.fillMaxHeight().press(0.96f, onClick).glass(shape)) {
        if (img != null) {
            Image(img, null, Modifier.matchParentSize().alpha(0.45f), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(Color(0xF0040506), Color(0x66040506)))))
        } else {
            Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(accent.copy(alpha = 0.22f), Color.Transparent), radius = 600f)))
        }
        Row(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(H * 0.11f).clip(CircleShape).background(Brush.radialGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.10f)))),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(H * 0.055f)) }
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = (H * 0.034f).sp(), color = Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, fontSize = (H * 0.021f).sp(), color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun greeting(lang: Lang): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return S.get(when (h) { in 5..11 -> "gMorning"; in 12..16 -> "gNoon"; in 17..21 -> "gEvening"; else -> "gNight" }, lang)
}

@Composable
private fun SpeedArcBlock(lang: Lang, modifier: Modifier, H: Dp) {
    val speed by Hub.speedKmh.collectAsState()
    SpeedArc(speed ?: 0, S.get("kmh", lang), LocalAccent.current, H * 0.36f, modifier)
}

private fun String.spaced() = this.uppercase()

@Composable
private fun TopBar(lang: Lang, W: Dp, H: Dp) {
    val now by produceState(Date()) {
        while (true) {
            value = Date()
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50)
        }
    }
    val gps by Hub.gpsFix.collectAsState()
    val bt by Hub.listenerConnected.collectAsState()
    val datePattern = when (lang) { Lang.AR -> "EEEE، d MMMM"; Lang.HE -> "EEEE, d בMMMM"; Lang.EN -> "EEEE, d MMMM" }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = W * 0.04f, vertical = H * 0.04f),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top
    ) {
        Column {
            Text(SimpleDateFormat("HH:mm", Locale.US).format(now), fontSize = (H * 0.075f).sp(), fontWeight = FontWeight.ExtraLight, color = Fg)
            Text(SimpleDateFormat(datePattern, lang.locale).format(now), fontSize = (H * 0.022f).sp(), color = Color(0xFF8A8F96))
        }
        Column(horizontalAlignment = Alignment.End) {
            StatusDot("GPS", gps, H)
            Spacer(Modifier.height(6.dp))
            StatusDot("LIVE", bt, H)
        }
    }
}

@Composable
private fun StatusDot(label: String, on: Boolean, H: Dp) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = (H * 0.018f).sp(), color = Muted, letterSpacing = 3.sp)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) LocalAccent.current else Color(0xFF3A3E44)))
        }
    }
}

@Composable
private fun SpeedBlock(lang: Lang, modifier: Modifier, H: Dp) {
    val speed by Hub.speedKmh.collectAsState()
    val v = speed ?: 0
    Column(modifier) {
        Text(v.toString(), fontSize = (H * 0.15f).sp(), fontWeight = FontWeight.ExtraLight, color = Fg, lineHeight = (H * 0.15f).sp())
        Text(S.get("kmh", lang), fontSize = (H * 0.02f).sp(), color = Muted, letterSpacing = 3.sp)
        Spacer(Modifier.height(H * 0.025f))
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF1A1D21))) {
            Box(Modifier.fillMaxWidth((v / 180f).coerceIn(0f, 1f)).fillMaxHeight().background(Fg))
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, onClick: () -> Unit, bg: (@Composable BoxScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Box(modifier.fillMaxWidth().press(0.97f, onClick).glass(shape)) {
        bg?.invoke(this)
        Column(Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun MediaCard(act: BaseActivity, lang: Lang, H: Dp) {
    val m by Hub.media.collectAsState()
    val media = m
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(media?.playing) {
        while (media?.playing == true) { now = android.os.SystemClock.elapsedRealtime(); delay(1000) }
    }
    val hasMedia = media != null && (media.title.isNotBlank() || media.artist.isNotBlank())
    val artBg = media?.art
    val artImg = remember(artBg) { artBg?.asImageBitmap() }
    Card(bg = {
        if (artImg != null) {
            Image(artImg, null, Modifier.matchParentSize().alpha(0.38f), contentScale = ContentScale.Crop)
            Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color(0x99040506), Color(0xE6040506)))))
        }
    }, onClick = {
        if (media?.pkg?.isNotBlank() == true) act.launchAny(media.pkg, toastKey = "notInstalled", lang = lang)
        else act.launchAny("com.spotify.music", "com.soundcloud.android", "com.google.android.apps.youtube.music", "com.google.android.youtube", toastKey = "notInstalled", lang = lang)
    }) {
        Text(S.get("nowPlaying", lang), fontSize = (H * 0.017f).sp(), color = Muted, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            val art = media?.art
            if (art != null) {
                val img = remember(art) { art.asImageBitmap() }
                Image(img, null, Modifier.size(H * 0.075f).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    if (hasMedia) media!!.title.ifBlank { media.artist } else S.get("nothingPlaying", lang),
                    fontSize = (H * 0.026f).sp(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Fg
                )
                Text(
                    if (hasMedia) media!!.artist else S.get("tapMusic", lang),
                    fontSize = (H * 0.02f).sp(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        val frac = if (media != null && media.duration > 0) {
            val pos = if (media.playing && media.posTime > 0) media.position + ((now - media.posTime) * media.speed).toLong() else media.position
            (pos.toFloat() / media.duration).coerceIn(0f, 1f)
        } else 0f
        Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x14FFFFFF))) {
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(LocalAccent.current))
        }
        Spacer(Modifier.height(12.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.SkipPrevious, H * 0.045f) {
                    Hub.controller?.transportControls?.skipToPrevious() ?: act.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
                Box(
                    Modifier.size(H * 0.068f).press(0.88f) {
                        val c = Hub.controller
                        if (c != null) {
                            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
                        } else act.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    }.clip(CircleShape).background(Fg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(if (media?.playing == true) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = Color(0xFF040506), modifier = Modifier.size(H * 0.04f))
                }
                IconBtn(Icons.Rounded.SkipNext, H * 0.045f) {
                    Hub.controller?.transportControls?.skipToNext() ?: act.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
                }
            }
        }
    }
}

@Composable
private fun IconBtn(icon: ImageVector, size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size * 1.5f).clip(CircleShape).press(0.85f, onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Fg2, modifier = Modifier.size(size))
    }
}

@Composable
private fun NavCard(act: BaseActivity, lang: Lang, H: Dp) {
    val n by Hub.nav.collectAsState()
    val nav = n
    Card(onClick = {
        if (nav != null) act.launchAny(nav.pkg)
        else act.launchAny("com.waze", "com.google.android.apps.maps", toastKey = "notInstalled", lang = lang)
    }) {
        Text(S.get("navigation", lang), fontSize = (H * 0.017f).sp(), color = Muted, letterSpacing = 2.sp)
        Spacer(Modifier.height(10.dp))
        if (nav != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val ic = nav.icon
                if (ic != null) {
                    val img = remember(ic) { ic.asImageBitmap() }
                    Image(img, null, Modifier.size(H * 0.07f))
                    Spacer(Modifier.width(12.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(nav.title, fontSize = (H * 0.03f).sp(), maxLines = 2, overflow = TextOverflow.Ellipsis, color = Fg)
                    if (nav.text.isNotBlank())
                        Text(nav.text, fontSize = (H * 0.02f).sp(), maxLines = 1, overflow = TextOverflow.Ellipsis, color = Muted)
                }
            }
        } else {
            Text(S.get("whereTo", lang), fontSize = (H * 0.026f).sp(), color = Fg)
            Text("Waze", fontSize = (H * 0.02f).sp(), color = Muted)
        }
    }
}

@Composable
private fun Dock(act: BaseActivity, lang: Lang, H: Dp, onHome: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val m by Hub.media.collectAsState()
    Row(
        modifier.glass(RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val s = H * 0.09f
        fun nope() = Toast.makeText(act, S.get("notInstalled", lang), Toast.LENGTH_SHORT).show()
        DockBtn(Icons.Rounded.Navigation, s) { act.launchAny("com.waze", "com.google.android.apps.maps", toastKey = "notInstalled", lang = lang) }
        DockBtn(Icons.Rounded.Bluetooth, s) { if (!VendorApps.open(act, "phone")) act.openDialer() }
        DockBtn(Icons.Rounded.MusicNote, s) {
            val pkg = m?.pkg.orEmpty()
            if (pkg.isNotBlank() && act.launchAny(pkg)) return@DockBtn
            if (VendorApps.open(act, "btmusic") || VendorApps.open(act, "music")) return@DockBtn
            act.launchAny("com.spotify.music", "com.soundcloud.android", toastKey = "notInstalled", lang = lang)
        }
        DockBtn(Icons.Rounded.DirectionsCar, s, active = true, onClick = onHome)
        DockBtn(Icons.Rounded.Radio, s) { if (!VendorApps.open(act, "radio")) nope() }
        DockBtn(Icons.Rounded.Apps, s, onClick = onApps)
        DockBtn(Icons.Rounded.Tune, s, onClick = onSettings)
    }
}

@Composable
private fun DockBtn(icon: ImageVector, size: Dp, active: Boolean = false, onClick: () -> Unit) {
    val accent = LocalAccent.current
    Box(
        Modifier.padding(horizontal = 5.dp).size(size).press(0.86f, onClick).clip(CircleShape)
            .then(if (active) Modifier.background(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent))) else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, null, tint = if (active) Fg else Fg2, modifier = Modifier.size(size * 0.42f))
        if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.07f).size(5.dp).clip(CircleShape).background(accent))
    }
}

/* ---------------------------------- notification popup ---------------------------------- */

@Composable
private fun BoxScope.NotificationPopup(lang: Lang) {
    val n by Hub.notif.collectAsState()
    var shown by remember { mutableStateOf<Hub.Notif?>(null) }
    var last by remember { mutableStateOf<Hub.Notif?>(null) }
    LaunchedEffect(n?.id) {
        val cur = n ?: return@LaunchedEffect
        shown = cur; last = cur
        delay(6500)
        if (shown?.id == cur.id) shown = null
    }
    AnimatedVisibility(
        visible = shown != null,
        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { -it / 2 },
        exit = fadeOut(tween(300)) + slideOutVertically(tween(300)) { -it / 2 },
        modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp)
    ) {
        val x = shown ?: last
        if (x != null) {
            Row(
                Modifier.widthIn(max = 560.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xF0141619))
                    .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(22.dp))
                    .tap { try { x.intent?.send() } catch (_: Throwable) {}; shown = null }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val ic = x.icon
                if (ic != null) {
                    val img = remember(ic) { ic.asImageBitmap() }
                    Image(img, null, Modifier.size(44.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(14.dp))
                }
                Column(Modifier.weight(1f, fill = false)) {
                    Text(x.app, fontSize = 12.sp, color = Muted)
                    Text(x.title, fontSize = 17.sp, color = Fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (x.text.isNotBlank()) Text(x.text, fontSize = 15.sp, color = Fg2, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Rounded.Close, null, tint = Muted, modifier = Modifier.size(22.dp).tap { shown = null })
            }
        }
    }
}

/* ---------------------------------- car picker ---------------------------------- */

@Composable
private fun PickerScreen(
    act: BaseActivity, lang: Lang, cars: List<Car>, car: Car, colorK: String, canGoBack: Boolean,
    onSelect: (String, String) -> Unit, onCarsChanged: () -> Unit, onStart: () -> Unit, onBack: () -> Unit,
) {
    BackHandler(enabled = canGoBack) { onBack() }
    val scope = rememberCoroutineScope()
    val col = car.colors.firstOrNull { it.k == colorK } ?: car.colors.first()
    val imgs = rememberCarImages(act, car, col.k)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val added = withContext(Dispatchers.IO) { CarRepo.import(act, uri) }
            if (added == null) Toast.makeText(act, S.get("invalidPack", lang), Toast.LENGTH_SHORT).show()
            else {
                onCarsChanged(); onSelect(added.id, added.colors.first().k)
                Toast.makeText(act, S.get("carAdded", lang) + ": " + added.brand + " " + added.model, Toast.LENGTH_SHORT).show()
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().drawBehind {
        drawRect(Brush.radialGradient(listOf(Color(0xFF15181C), Color(0xFF0A0B0D), Color(0xFF040506)), Offset(size.width / 2, size.height * 0.52f), size.width * 0.62f))
    }) {
        val W = maxWidth; val H = maxHeight
        Column(Modifier.fillMaxSize().padding(horizontal = W * 0.04f, vertical = H * 0.05f)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                if (canGoBack) {
                    Icon(Icons.Rounded.ArrowBack, null, tint = Fg2, modifier = Modifier.padding(end = 14.dp).size(30.dp).graphicsLayer { if (lang.rtl) scaleX = -1f }.tap(onBack))
                }
                Column(Modifier.weight(1f)) {
                    Text(S.get("chooseCar", lang), fontSize = (H * 0.05f).sp(), fontWeight = FontWeight.Light)
                    Text(S.get("chooseSub", lang), fontSize = (H * 0.022f).sp(), color = Muted)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50))
                        .clickable { picker.launch(arrayOf("*/*")) }.padding(horizontal = 22.dp, vertical = 10.dp)
                ) { Text(S.get("addCar", lang), fontSize = (H * 0.022f).sp()) }
            }
            Spacer(Modifier.height(H * 0.035f))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(cars, key = { it.id }) { c ->
                    val sel = c.id == car.id
                    Box(
                        Modifier.width(W * 0.17f).clip(RoundedCornerShape(16.dp))
                            .background(if (sel) Color(0x0DFFFFFF) else Color(0x05FFFFFF))
                            .border(1.dp, if (sel) Color(0x8CFFFFFF) else Color(0xFF191C20), RoundedCornerShape(16.dp))
                            .clickable { onSelect(c.id, if (c.colors.any { it.k == colorK }) colorK else c.colors.first().k) }
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                    ) {
                        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                            Column {
                                Text(c.brand, fontSize = (H * 0.018f).sp(), color = Muted, letterSpacing = 2.sp, maxLines = 1)
                                Text(c.model, fontSize = (H * 0.026f).sp(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(c.year, fontSize = (H * 0.017f).sp(), color = Muted)
                            }
                        }
                        if (!c.builtin) {
                            Icon(Icons.Rounded.Close, null, tint = Muted, modifier = Modifier.align(Alignment.TopEnd).size(22.dp).clickable {
                                CarRepo.delete(act, c.id); onCarsChanged()
                                if (sel) onSelect("superb", "graphite")
                                Toast.makeText(act, S.get("carRemoved", lang), Toast.LENGTH_SHORT).show()
                            })
                        }
                    }
                }
            }
            CarView(imgs, 1f, Modifier.weight(1f).fillMaxWidth().padding(horizontal = W * 0.14f, vertical = H * 0.03f))
            Spacer(Modifier.height(H * 0.12f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                car.colors.forEach { cc ->
                    val sel = cc.k == col.k
                    Box(
                        Modifier.padding(end = 14.dp).size(H * 0.075f)
                            .border(1.dp, if (sel) Color(0xB3FFFFFF) else Color.Transparent, CircleShape)
                            .padding(5.dp).clip(CircleShape).background(Color(cc.hex))
                            .border(1.dp, Color(0x2EFFFFFF), CircleShape)
                            .clickable { onSelect(car.id, cc.k) }
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(S.get(col.k, lang).let { if (it == col.k) col.n else it }, fontSize = (H * 0.024f).sp(), color = Fg2, modifier = Modifier.weight(1f))
                Box(
                    Modifier.clip(RoundedCornerShape(50)).background(Fg).clickable(onClick = onStart)
                        .padding(horizontal = W * 0.045f, vertical = H * 0.022f)
                ) { Text(S.get("start", lang), color = Color(0xFF040506), fontSize = (H * 0.026f).sp()) }
            }
        }
    }
}

/* ---------------------------------- apps ---------------------------------- */

private data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap?)

@Composable
private fun AppsScreen(act: BaseActivity, lang: Lang, onBack: () -> Unit) {
    BackHandler { onBack() }
    val apps by produceState<List<AppItem>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = act.packageManager
            val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(i, 0)
                .filter { it.activityInfo.packageName != act.packageName }
                .distinctBy { it.activityInfo.packageName }
                .map { ri ->
                    val bmp: Bitmap? = try { ri.loadIcon(pm).toBitmapSafe(144) } catch (_: Throwable) { null }
                    AppItem(ri.loadLabel(pm).toString(), ri.activityInfo.packageName, bmp?.asImageBitmap())
                }
                .sortedBy { it.label.lowercase() }
        }
    }
    Column(Modifier.fillMaxSize().background(Color(0xFF07080A)).padding(24.dp)) {
        Header(S.get("apps", lang), lang, onBack)
        Spacer(Modifier.height(16.dp))
        val list = apps
        if (list == null) Text(S.get("loading", lang), color = Muted)
        else LazyVerticalGrid(GridCells.Adaptive(118.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            items(list, key = { it.pkg }) { a ->
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { act.launchAny(a.pkg) }.padding(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (a.icon != null) Image(a.icon, null, Modifier.size(56.dp))
                    else Box(Modifier.size(56.dp).clip(CircleShape).background(CardBg))
                    Spacer(Modifier.height(8.dp))
                    Text(a.label, fontSize = 14.sp, color = Fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, lang: Lang, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.ArrowBack, null, tint = Fg2, modifier = Modifier.size(28.dp).graphicsLayer { if (lang.rtl) scaleX = -1f })
        }
        Spacer(Modifier.width(10.dp))
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Light)
    }
}

/* ---------------------------------- settings ---------------------------------- */

@Composable
private fun SettingsScreen(
    act: BaseActivity, lang: Lang, car: Car, carGlow: Color, theme: String, name: String, perf: String, onboarding: Boolean,
    onPerf: (String) -> Unit, onTheme: (String) -> Unit, onName: (String) -> Unit, onLang: (Lang) -> Unit, onPicker: () -> Unit, onBack: () -> Unit,
) {
    BackHandler { onBack() }
    var resume by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resume++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    val connected by Hub.listenerConnected.collectAsState()
    val notifOk = remember(resume, connected) { connected || act.notifAccessGranted() }
    val locOk = remember(resume) { act.hasLocation() }

    Column(Modifier.fillMaxSize().background(Color(0xFF07080A)).padding(24.dp)) {
        Header(S.get("settings", lang), lang, onBack)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (onboarding) item {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(LocalAccent.current.copy(alpha = 0.18f))
                        .border(1.dp, LocalAccent.current.copy(alpha = 0.5f), RoundedCornerShape(18.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(S.get("finishSetup", lang), fontSize = 17.sp, color = Fg, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Pill(S.get("done", lang), onBack)
                }
            }
            item {
                val isDef = remember(resume) { act.isDefaultHome() }
                SettingRow(Icons.Rounded.Home, S.get("defaultLauncher", lang), S.get("defaultLauncherSub", lang)) {
                    if (isDef) OkTag(S.get("granted", lang)) else Pill(S.get("grant", lang)) { act.makeDefaultHome() }
                }
            }
            item {
                val ok = remember(resume) { act.guardEnabled() }
                SettingRow(Icons.Rounded.Verified, S.get("guard", lang), if (ok) null else S.get("guardSub", lang)) {
                    if (ok) OkTag(S.get("granted", lang)) else Pill(S.get("grant", lang)) { act.openAccessibility() }
                }
            }
            item {
                SettingRow(Icons.Rounded.Language, S.get("language", lang), null) {
                    Row {
                        Lang.values().forEach { l ->
                            val sel = l == lang
                            Box(
                                Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
                                    .background(if (sel) Fg else Color.Transparent)
                                    .border(1.dp, if (sel) Fg else Color(0x40FFFFFF), RoundedCornerShape(50))
                                    .clickable { onLang(l) }.padding(horizontal = 18.dp, vertical = 9.dp)
                            ) { Text(l.label, color = if (sel) Color(0xFF040506) else Fg, fontSize = 15.sp) }
                        }
                    }
                }
            }
            item {
                SettingRow(Icons.Rounded.Bluetooth, S.get("bluetooth", lang), S.get("bluetoothSub", lang)) {
                    Pill(S.get("open", lang)) { if (!VendorApps.open(act, "phone")) act.tryStart(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)) }
                }
            }
            item {
                SettingRow(Icons.Rounded.CarRepair, S.get("t_carset", lang), null) {
                    Pill(S.get("open", lang)) { if (!VendorApps.open(act, "carset")) act.openAndroidSettings() }
                }
            }
            item {
                SettingRow(Icons.Rounded.DirectionsCar, S.get("carAndColor", lang), car.brand + " " + car.model) {
                    Pill(S.get("change", lang), onPicker)
                }
            }
            item {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).border(1.dp, CardLine, RoundedCornerShape(18.dp))
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Palette, null, tint = Fg2, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(S.get("background", lang), fontSize = 18.sp, color = Fg)
                            Text(S.get("backgroundSub", lang), fontSize = 14.sp, color = Muted)
                        }
                        Text(S.get(THEMES.firstOrNull { it.id == theme }?.key ?: "themeCar", lang), fontSize = 15.sp, color = LocalAccent.current)
                    }
                    Spacer(Modifier.height(14.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(THEMES, key = { it.id }) { t ->
                            val sel = t.id == theme
                            val c = when (t.id) { "car" -> carGlow; "music" -> Color(0xFF9070F5); else -> t.color }
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(78.dp).clickable { onTheme(t.id) }) {
                                Box(
                                    Modifier.size(54.dp).border(2.dp, if (sel) Fg else Color.Transparent, CircleShape).padding(5.dp).clip(CircleShape)
                                        .background(Brush.radialGradient(listOf(c, c.copy(alpha = 0.35f), Color(0xFF0A0B0D)))),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (t.id == "car") Icon(Icons.Rounded.DirectionsCar, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                    if (t.id == "music") Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(S.get(t.key, lang), fontSize = 12.sp, color = if (sel) Fg else Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            item {
                SettingRow(Icons.Rounded.Person, S.get("yourName", lang), S.get("yourNameSub", lang)) {
                    var txt by remember { mutableStateOf(name) }
                    BasicTextField(
                        value = txt, onValueChange = { v -> txt = v.take(24); onName(txt) }, singleLine = true,
                        textStyle = TextStyle(color = Fg, fontSize = 17.sp), cursorBrush = SolidColor(LocalAccent.current),
                        modifier = Modifier.width(220.dp).clip(RoundedCornerShape(12.dp)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
            item {
                SettingRow(Icons.Rounded.NotificationsActive, S.get("notifAccess", lang), S.get("notifAccessSub", lang)) {
                    if (notifOk) OkTag(S.get("granted", lang)) else Pill(S.get("grant", lang)) { act.openNotifAccess() }
                }
            }
            item {
                SettingRow(Icons.Rounded.Speed, S.get("location", lang), S.get("locationSub", lang)) {
                    if (locOk) OkTag(S.get("granted", lang)) else Pill(S.get("grant", lang)) { act.requestLocation(); resume++ }
                }
            }
            item {
                val prefs = remember { act.getSharedPreferences("cl", 0) }
                var autoOn by remember { mutableStateOf(prefs.getBoolean("autoOpen", true)) }
                val overlayOk = remember(resume) { act.canOverlay() }
                SettingRow(Icons.Rounded.PowerSettingsNew, S.get("autoOpen", lang), if (overlayOk) null else S.get("autoOpenSub", lang)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (autoOn && !overlayOk) { Pill(S.get("grant", lang)) { act.requestOverlay() }; Spacer(Modifier.width(8.dp)) }
                        Pill(S.get(if (autoOn) "on" else "off", lang)) { autoOn = !autoOn; prefs.edit().putBoolean("autoOpen", autoOn).apply() }
                    }
                }
            }
            item {
                SettingRow(Icons.Rounded.Speed, S.get("perf", lang), S.get("perfSub", lang)) {
                    Row {
                        listOf("auto" to "perfAuto", "lite" to "perfLite", "full" to "perfFull").forEach { (id, key) ->
                            val sel = id == perf
                            Box(
                                Modifier.padding(start = 8.dp).clip(RoundedCornerShape(50))
                                    .background(if (sel) Fg else Color.Transparent)
                                    .border(1.dp, if (sel) Fg else Color(0x40FFFFFF), RoundedCornerShape(50))
                                    .clickable { onPerf(id) }.padding(horizontal = 16.dp, vertical = 9.dp)
                            ) { Text(S.get(key, lang), color = if (sel) Color(0xFF040506) else Fg, fontSize = 15.sp) }
                        }
                    }
                }
            }
            item {
                SettingRow(Icons.Rounded.Settings, S.get("androidSettings", lang), null) {
                    Pill(S.get("open", lang)) { act.openAndroidSettings() }
                }
            }
            item {
                Text(S.get("version", lang) + " 1.0", color = Dim, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp, start = 4.dp))
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String?, action: @Composable () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg).border(1.dp, CardLine, RoundedCornerShape(18.dp))
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = Fg2, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 18.sp, color = Fg)
            if (sub != null) Text(sub, fontSize = 14.sp, color = Muted)
        }
        Spacer(Modifier.width(12.dp))
        action()
    }
}

@Composable
private fun Pill(text: String, onClick: () -> Unit) {
    Box(
        Modifier.press(0.92f, onClick).glass(RoundedCornerShape(50)).padding(horizontal = 20.dp, vertical = 9.dp)
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
private fun OkTag(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Check, null, tint = Mint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = Mint, fontSize = 15.sp)
    }
}
