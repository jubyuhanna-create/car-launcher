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
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
fun App(act: MainActivity) {
    val prefs = remember { act.getSharedPreferences("cl", 0) }
    var lang by remember { mutableStateOf(Lang.values().firstOrNull { it.code == prefs.getString("lang", null) } ?: defaultLang()) }
    var cars by remember { mutableStateOf(CarRepo.load(act)) }
    var carId by remember { mutableStateOf(prefs.getString("car", "superb") ?: "superb") }
    var colorK by remember { mutableStateOf(prefs.getString("color", "graphite") ?: "graphite") }
    var screen by remember { mutableStateOf(if (prefs.getBoolean("setup", false)) Screen.Home else Screen.Picker) }
    val car = cars.firstOrNull { it.id == carId } ?: cars.firstOrNull()

    fun saveCar(id: String, k: String) {
        carId = id; colorK = k
        prefs.edit().putString("car", id).putString("color", k).apply()
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        CompositionLocalProvider(
            LocalLayoutDirection provides if (lang.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
            LocalContentColor provides Fg,
        ) {
            Box(Modifier.fillMaxSize().background(Color(0xFF040506))) {
                if (car == null) {
                    Text("No cars", Modifier.align(Alignment.Center))
                } else when (screen) {
                    Screen.Home -> {
                        BackHandler { /* home screen: back does nothing */ }
                        HomeScreen(act, lang, car, colorK,
                        onApps = { screen = Screen.Apps },
                        onSettings = { screen = Screen.Settings })
                    }
                    Screen.Picker -> PickerScreen(act, lang, cars, car, colorK,
                        canGoBack = prefs.getBoolean("setup", false),
                        onSelect = { id, k -> saveCar(id, k) },
                        onCarsChanged = { cars = CarRepo.load(act) },
                        onStart = {
                            prefs.edit().putBoolean("setup", true).apply()
                            Hub.bootTick.value++
                            screen = Screen.Home
                        },
                        onBack = { screen = Screen.Settings })
                    Screen.Apps -> AppsScreen(act, lang, onBack = { screen = Screen.Home })
                    Screen.Settings -> SettingsScreen(act, lang, car,
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

@Composable
private fun rememberCarImages(act: MainActivity, car: Car, k: String): Pair<ImageBitmap?, ImageBitmap?> {
    val st = produceState<Pair<ImageBitmap?, ImageBitmap?>>(null to null, car.id, k) {
        value = withContext(Dispatchers.IO) {
            val key = if (car.colors.any { it.k == k }) k else car.colors.first().k
            val off = CarRepo.bitmap(act, car, key + "_off")
            val on = CarRepo.bitmap(act, car, key + "_on") ?: off
            off?.asImageBitmap() to on?.asImageBitmap()
        }
    }
    return st.value
}

@Composable
private fun CarView(off: ImageBitmap?, on: ImageBitmap?, lamp: Float, modifier: Modifier = Modifier) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(modifier) {
            if (off != null) {
                Image(
                    off, null,
                    Modifier.fillMaxSize()
                        .graphicsLayer {
                            scaleY = -1f
                            translationY = size.height * 0.93f
                            alpha = 0.14f
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(Brush.verticalGradient(0.62f to Color.Transparent, 1f to Color.Black), blendMode = BlendMode.DstIn)
                        },
                    alignment = Alignment.BottomCenter, contentScale = ContentScale.Fit
                )
                Image(off, null, Modifier.fillMaxSize(), alignment = Alignment.BottomCenter, contentScale = ContentScale.Fit)
                if (on != null && lamp > 0.01f)
                    Image(on, null, Modifier.fillMaxSize().alpha(lamp), alignment = Alignment.BottomCenter, contentScale = ContentScale.Fit)
            }
        }
    }
}

/* ---------------------------------- home ---------------------------------- */

@Composable
private fun HomeScreen(act: MainActivity, lang: Lang, car: Car, colorK: String, onApps: () -> Unit, onSettings: () -> Unit) {
    val (off, on) = rememberCarImages(act, car, colorK)
    val drl = remember { Animatable(0f) }
    val carA = remember { Animatable(0f) }
    val lampA = remember { Animatable(0f) }
    val hudA = remember { Animatable(0f) }
    val tick by Hub.bootTick.collectAsState()
    var localBoot by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(tick, localBoot, off != null) {
        if (off == null) return@LaunchedEffect
        drl.snapTo(0f); carA.snapTo(0f); lampA.snapTo(0f)
        if (hudA.value < 1f) hudA.snapTo(0f)
        scope.launch { drl.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)) }
        scope.launch { delay(600); carA.animateTo(1f, tween(1500)) }
        scope.launch { delay(1500); lampA.animateTo(1f, tween(900)) }
        scope.launch { delay(1500); hudA.animateTo(1f, tween(1100)) }
    }

    BoxWithConstraints(
        Modifier.fillMaxSize().drawBehind {
            drawRect(
                Brush.radialGradient(
                    listOf(Color(0xFF16191D), Color(0xFF0A0B0D), Color(0xFF040506)),
                    center = Offset(size.width / 2f, size.height * 0.52f),
                    radius = size.width * 0.62f
                )
            )
        }
    ) {
        val W = maxWidth
        val H = maxHeight

        // floor line
        Box(
            Modifier.offset(y = H * 0.655f).fillMaxWidth().height(1.dp)
                .background(Brush.horizontalGradient(listOf(Color.Transparent, Color(0x1AFFFFFF), Color(0x1AFFFFFF), Color.Transparent)))
        )

        // light line of the boot animation
        val p = drl.value
        if (p > 0f && p < 1f) {
            val a = when { p < 0.2f -> p / 0.2f; p < 0.7f -> 1f; else -> (1f - p) / 0.3f }
            Box(
                Modifier.align(Alignment.TopCenter).offset(y = H * 0.47f)
                    .width(W * 0.6f * (p / 0.7f).coerceAtMost(1f)).height(2.dp)
                    .alpha(a).background(Color.White)
            )
        }

        // nameplate
        Row(Modifier.align(Alignment.TopCenter).offset(y = H * 0.14f).alpha(hudA.value)) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row {
                    Text(car.brand.spaced(), fontSize = (H * 0.026f).sp(), color = Fg, letterSpacing = 6.sp)
                    Spacer(Modifier.width(18.dp))
                    Text(car.model.spaced(), fontSize = (H * 0.026f).sp(), color = Muted, letterSpacing = 6.sp)
                }
            }
        }

        // car
        CarView(
            off, on, lampA.value,
            Modifier.offset(y = H * 0.20f).padding(start = W * 0.21f, end = W * 0.19f)
                .fillMaxWidth().height(H * 0.46f).alpha(carA.value)
                .tap { localBoot++ }
        )

        // HUD
        Box(Modifier.fillMaxSize().alpha(hudA.value)) {
            TopBar(lang, W, H)
            SpeedBlock(lang, Modifier.align(Alignment.TopStart).padding(start = W * 0.045f, top = H * 0.30f).width(W * 0.16f), H)
            Column(Modifier.align(Alignment.TopEnd).padding(end = W * 0.04f, top = H * 0.25f).width(W * 0.20f)) {
                MediaCard(act, lang, H)
                Spacer(Modifier.height(H * 0.025f))
                NavCard(act, lang, H)
            }
            Dock(act, lang, H, onHome = { localBoot++ }, onApps = onApps, onSettings = onSettings,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = H * 0.04f))
        }
    }
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
            Box(Modifier.size(6.dp).clip(CircleShape).background(if (on) Mint else Color(0xFF3A3E44)))
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
private fun Card(modifier: Modifier = Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardBg)
            .border(1.dp, CardLine, RoundedCornerShape(18.dp)).tap(onClick).padding(16.dp),
        content = content
    )
}

@Composable
private fun MediaCard(act: MainActivity, lang: Lang, H: Dp) {
    val m by Hub.media.collectAsState()
    val media = m
    var now by remember { mutableLongStateOf(android.os.SystemClock.elapsedRealtime()) }
    LaunchedEffect(media?.playing) {
        while (media?.playing == true) { now = android.os.SystemClock.elapsedRealtime(); delay(1000) }
    }
    val hasMedia = media != null && (media.title.isNotBlank() || media.artist.isNotBlank())
    Card(onClick = {
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
            Box(Modifier.fillMaxWidth(frac).fillMaxHeight().background(Fg))
        }
        Spacer(Modifier.height(12.dp))
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconBtn(Icons.Rounded.SkipPrevious, H * 0.045f) {
                    Hub.controller?.transportControls?.skipToPrevious() ?: act.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
                }
                Box(
                    Modifier.size(H * 0.068f).clip(CircleShape).background(Fg).tap {
                        val c = Hub.controller
                        if (c != null) {
                            if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
                        } else act.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                    },
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
    Box(Modifier.size(size * 1.5f).clip(CircleShape).tap(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Fg2, modifier = Modifier.size(size))
    }
}

@Composable
private fun NavCard(act: MainActivity, lang: Lang, H: Dp) {
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
private fun Dock(act: MainActivity, lang: Lang, H: Dp, onHome: () -> Unit, onApps: () -> Unit, onSettings: () -> Unit, modifier: Modifier) {
    val m by Hub.media.collectAsState()
    Row(
        modifier.clip(RoundedCornerShape(50)).background(CardBg).border(1.dp, CardLine, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val s = H * 0.085f
        DockBtn(Icons.Rounded.Navigation, s) { act.launchAny("com.waze", "com.google.android.apps.maps", toastKey = "notInstalled", lang = lang) }
        DockBtn(Icons.Rounded.MusicNote, s) {
            val pkg = m?.pkg.orEmpty()
            act.launchAny(pkg, "com.spotify.music", "com.soundcloud.android", "com.google.android.apps.youtube.music", toastKey = "notInstalled", lang = lang)
        }
        DockBtn(Icons.Rounded.DirectionsCar, s, active = true, onClick = onHome)
        DockBtn(Icons.Rounded.Apps, s, onClick = onApps)
        DockBtn(Icons.Rounded.Phone, s) { act.openDialer() }
        DockBtn(Icons.Rounded.Tune, s, onClick = onSettings)
    }
}

@Composable
private fun DockBtn(icon: ImageVector, size: Dp, active: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.padding(horizontal = 6.dp).size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (active) Fg else Fg2, modifier = Modifier.size(size * 0.42f))
        if (active) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.08f).size(5.dp).clip(CircleShape).background(Fg))
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
    act: MainActivity, lang: Lang, cars: List<Car>, car: Car, colorK: String, canGoBack: Boolean,
    onSelect: (String, String) -> Unit, onCarsChanged: () -> Unit, onStart: () -> Unit, onBack: () -> Unit,
) {
    BackHandler(enabled = canGoBack) { onBack() }
    val scope = rememberCoroutineScope()
    val col = car.colors.firstOrNull { it.k == colorK } ?: car.colors.first()
    val (off, on) = rememberCarImages(act, car, col.k)
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
            CarView(off, on, 1f, Modifier.weight(1f).fillMaxWidth().padding(horizontal = W * 0.14f, vertical = H * 0.03f))
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
private fun AppsScreen(act: MainActivity, lang: Lang, onBack: () -> Unit) {
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
private fun SettingsScreen(act: MainActivity, lang: Lang, car: Car, onLang: (Lang) -> Unit, onPicker: () -> Unit, onBack: () -> Unit) {
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
                SettingRow(Icons.Rounded.DirectionsCar, S.get("carAndColor", lang), car.brand + " " + car.model) {
                    Pill(S.get("change", lang), onPicker)
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
                SettingRow(Icons.Rounded.Home, S.get("defaultLauncher", lang), S.get("defaultLauncherSub", lang)) {
                    Pill(S.get("open", lang)) { act.openHomeSettings() }
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
        Modifier.clip(RoundedCornerShape(50)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50))
            .clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 9.dp)
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
