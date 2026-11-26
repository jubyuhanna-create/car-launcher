package com.el7ajb.carlauncher

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.TextUtils
import android.util.TypedValue
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * The home screen is built with plain Android Views on purpose: the View system is pre-compiled
 * inside Android itself, so it starts instantly and stays smooth on 2 GB head units. Every
 * continuous effect (breathing glow, light sweep) only changes alpha/translation of a view, which
 * the GPU composites without redrawing anything.
 */

private const val FG = 0xFFECEEF0.toInt()
private const val FG2 = 0xFFC9CCD0.toInt()
private const val MUTED = 0xFF7A7F86.toInt()

private val THIN: Typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
private val LIGHT: Typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
private val REG: Typeface = Typeface.create("sans-serif", Typeface.NORMAL)

private fun withAlpha(c: Int, a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
private fun mixWhite(c: Int, f: Float): Int = Color.rgb(
    (Color.red(c) + (255 - Color.red(c)) * f).toInt(), (Color.green(c) + (255 - Color.green(c)) * f).toInt(), (Color.blue(c) + (255 - Color.blue(c)) * f).toInt()
)

private fun glass(radius: Float, strokePx: Int = 1): GradientDrawable =
    GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x16FFFFFF, 0x06FFFFFF)).apply {
        cornerRadius = radius
        setStroke(strokePx, 0x24FFFFFF)
    }

/** Press feedback: shrink + spring back + light haptic. */
@SuppressLint("ClickableViewAccessibility")
private fun View.pressable(scale: Float = 0.92f, onClick: () -> Unit) {
    isClickable = true
    setOnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> v.animate().scaleX(scale).scaleY(scale).setDuration(90).setInterpolator(DecelerateInterpolator()).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(260).setInterpolator(OvershootInterpolator(2.2f)).start()
        }
        false
    }
    setOnClickListener {
        try { performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Throwable) {}
        onClick()
    }
}

/* ------------------------------------------------------------------ background */

private class BackgroundView(ctx: Context) : View(ctx) {
    private var cache: Bitmap? = null
    private var old: Bitmap? = null
    private var fade = 1f
    var accent: Int = 0xFF6F8DB8.toInt()
        set(v) { if (field != v) { field = v; rebuild(true) } }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { rebuild(false) }

    private fun rebuild(animate: Boolean) {
        val w = width; val h = height
        if (w <= 0 || h <= 0) return
        val bmp = try { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) } catch (_: Throwable) { return }
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(w / 2f, h * 0.5f, w * 0.7f, intArrayOf(0xFF111316.toInt(), 0xFF08090B.toInt(), 0xFF030405.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = RadialGradient(w / 2f, h * 0.45f, w * 0.42f, intArrayOf(withAlpha(accent, 0.22f), withAlpha(accent, 0.06f), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        p.shader = LinearGradient(0f, 0f, 0f, h * 0.6f, intArrayOf(withAlpha(accent, 0.11f), 0), floatArrayOf(0f, 0.75f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h * 0.6f, p)
        // perspective floor grid
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1f
        p.color = withAlpha(Color.WHITE, 0.04f)
        val fy = h * 0.655f; val vpy = h * 0.36f
        for (i in -14..14) {
            val bx = w / 2f + i * w * 0.09f
            val t = (fy - vpy) / (h - vpy)
            c.drawLine(w / 2f + (bx - w / 2f) * t, fy, bx, h.toFloat(), p)
        }
        var yy = fy; var step = h * 0.012f
        while (yy < h) { c.drawLine(0f, yy, w.toFloat(), yy, p); yy += step; step *= 1.45f }
        // floor line
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w.toFloat(), 0f,
            intArrayOf(0, withAlpha(accent, 0.4f), withAlpha(Color.WHITE, 0.22f), withAlpha(accent, 0.4f), 0), null, Shader.TileMode.CLAMP)
        c.drawRect(0f, fy - 1f, w.toFloat(), fy + 1f, p)

        old = if (animate) cache else null
        cache = bmp
        if (animate && old != null) {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 700
                addUpdateListener { fade = it.animatedValue as Float; invalidate() }
                start()
            }
        } else { fade = 1f; invalidate() }
    }

    private val bp = Paint(Paint.FILTER_BITMAP_FLAG)
    override fun onDraw(canvas: Canvas) {
        val o = old
        if (o != null && fade < 1f) { bp.alpha = 255; canvas.drawBitmap(o, 0f, 0f, bp) }
        cache?.let { bp.alpha = (fade * 255).toInt(); canvas.drawBitmap(it, 0f, 0f, bp) }
    }
}

/* ------------------------------------------------------------------ speed arc */

private class SpeedArcView(ctx: Context) : View(ctx) {
    var accent = 0xFF6F8DB8.toInt(); set(v) { field = v; invalidate() }
    var unit = "KM/H"
    private var shown = 0f
    private var target = 0
    private var anim: ValueAnimator? = null
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setSpeed(v: Int) {
        if (v == target) return
        target = v
        anim?.cancel()
        anim = ValueAnimator.ofFloat(shown, v.toFloat()).apply {
            duration = 600; interpolator = DecelerateInterpolator()
            addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val s = min(width, height).toFloat()
        val cx = width / 2f; val cy = height / 2f
        val stroke = s * 0.032f
        val rOut = s / 2f
        // ticks
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        for (i in 0..20) {
            val a = Math.toRadians(135.0 + 270.0 * i / 20.0)
            val major = i % 5 == 0
            val r1 = rOut - (if (major) stroke * 1.6f else stroke)
            p.color = withAlpha(Color.WHITE, if (major) 0.35f else 0.14f); p.strokeWidth = stroke * 0.35f
            c.drawLine(cx + (rOut * cos(a)).toFloat(), cy + (rOut * sin(a)).toFloat(), cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(), p)
        }
        val inset = stroke * 2.2f
        val rect = RectF(cx - s / 2 + inset, cy - s / 2 + inset, cx + s / 2 - inset, cy + s / 2 - inset)
        p.strokeWidth = stroke; p.color = withAlpha(Color.WHITE, 0.08f)
        c.drawArc(rect, 135f, 270f, false, p)
        val v = shown
        val frac = (v / 200f).coerceIn(0f, 1f)
        val col = when { v >= 130 -> 0xFFFF4D4D.toInt(); v >= 100 -> 0xFFFFB84D.toInt(); else -> accent }
        if (frac > 0.002f) {
            p.color = withAlpha(col, 0.25f); p.strokeWidth = stroke * 3.2f
            c.drawArc(rect, 135f, 270f * frac, false, p)
            p.color = col; p.strokeWidth = stroke
            c.drawArc(rect, 135f, 270f * frac, false, p)
        }
        tp.typeface = THIN; tp.color = FG; tp.textSize = s * 0.34f
        c.drawText(v.toInt().toString(), cx, cy + s * 0.1f, tp)
        tp.typeface = REG; tp.color = MUTED; tp.textSize = s * 0.065f; tp.letterSpacing = 0.25f
        c.drawText(unit, cx, cy + s * 0.24f, tp)
        tp.letterSpacing = 0f
    }
}

/* ------------------------------------------------------------------ light sweep on the car */

private class ShineView(ctx: Context) : View(ctx) {
    var mask: Bitmap? = null
    var progress = -1f; set(v) { field = v; invalidate() }
    private val mp = Paint(Paint.FILTER_BITMAP_FLAG)
    private val sp = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) }
    private val dst = Rect()
    override fun onDraw(c: Canvas) {
        val m = mask ?: return
        val p = progress
        if (p !in 0f..1f) return
        val w = width.toFloat(); val h = height.toFloat()
        val save = c.saveLayer(0f, 0f, w, h, null)
        dst.set(0, 0, width, height)
        c.drawBitmap(m, null, dst, mp)
        val x = -0.3f * w + p * 1.6f * w
        sp.shader = LinearGradient(x - w * 0.1f, 0f, x + w * 0.1f, h * 0.6f,
            intArrayOf(0, 0x4DFFFFFF, 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, sp)
        c.restoreToCount(save)
    }
}

/* ------------------------------------------------------------------ simple two page pager */

private class PagerFrame(ctx: Context) : FrameLayout(ctx) {
    var page = 0; private set
    var onPage: ((Int) -> Unit)? = null
    private val slop = ViewConfiguration.get(ctx).scaledTouchSlop
    private var downX = 0f; private var downY = 0f; private var dragging = false; private var startTx = 0f
    private val pages = ArrayList<View>()

    fun addPage(v: View) { pages.add(v); addView(v, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)) }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        if (!dragging) apply(-page * width.toFloat())
    }

    private fun apply(offset: Float) { pages.forEachIndexed { i, v -> v.translationX = offset + i * width } }

    fun go(p: Int, animate: Boolean = true) {
        page = p.coerceIn(0, pages.size - 1)
        val target = -page * width.toFloat()
        if (!animate) { apply(target); onPage?.invoke(page); return }
        val from = pages.firstOrNull()?.translationX ?: 0f
        ValueAnimator.ofFloat(from, target).apply {
            duration = 280; interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener { apply(it.animatedValue as Float) }
            start()
        }
        onPage?.invoke(page)
    }

    override fun onInterceptTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; dragging = false; startTx = pages.firstOrNull()?.translationX ?: 0f }
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX; val dy = e.y - downY
                if (abs(dx) > slop * 1.5f && abs(dx) > abs(dy) * 1.3f) { dragging = true; parent?.requestDisallowInterceptTouchEvent(true); return true }
            }
        }
        return false
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> return true
            MotionEvent.ACTION_MOVE -> {
                val dx = e.x - downX
                val off = (startTx + dx).coerceIn(-(pages.size - 1) * width.toFloat() - width * 0.1f, width * 0.1f)
                apply(off)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val dx = e.x - downX
                dragging = false
                val np = if (abs(dx) > width * 0.18f) (if (dx < 0) page + 1 else page - 1) else page
                go(np)
            }
        }
        return true
    }
}

/* ------------------------------------------------------------------ the home screen */

class HomeUi(private val act: MainActivity) {

    val root = FrameLayout(act)
    private val prefs = act.getSharedPreferences("cl", 0)
    private val main = Handler(Looper.getMainLooper())

    // state
    private var lang = Lang.AR
    private var lite = false
    private var accent = 0xFF6F8DB8.toInt()
    private var carGlow = accent
    private var musicGlow: Int? = null
    private var theme = "car"
    private var car: Car? = null
    private var colorK = "graphite"
    private var builtKey = ""
    private var W = 0
    private var H = 0
    private var cars: List<Car> = emptyList()

    // views
    private lateinit var bg: BackgroundView
    private lateinit var glow: ImageView
    private lateinit var bloom: ImageView
    private lateinit var tripVal: TextView
    private lateinit var tripSub: TextView
    private lateinit var carOff: ImageView
    private lateinit var carOn: ImageView
    private lateinit var refl: ImageView
    private lateinit var shine: ShineView
    private lateinit var drl: View
    private lateinit var greet: TextView
    private lateinit var greetName: TextView
    private lateinit var nameplate: LinearLayout
    private lateinit var plateDot: View
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var gpsDot: View
    private lateinit var liveDot: View
    private lateinit var speed: SpeedArcView
    private lateinit var hud: FrameLayout
    private lateinit var carLayer: FrameLayout
    private lateinit var pager: PagerFrame
    private lateinit var dots: LinearLayout
    private lateinit var dockActiveDot: View
    private lateinit var dockActiveBg: View
    // media card
    private lateinit var mArtBg: ImageView
    private lateinit var mArt: ImageView
    private lateinit var mTitle: TextView
    private lateinit var mArtist: TextView
    private lateinit var mProg: View
    private lateinit var mProgTrack: FrameLayout
    private lateinit var mPlay: ImageView
    // nav card
    private lateinit var nIcon: ImageView
    private lateinit var nTitle: TextView
    private lateinit var nSub: TextView
    // popup
    private lateinit var popup: LinearLayout
    private lateinit var pIcon: ImageView
    private lateinit var pApp: TextView
    private lateinit var pTitle: TextView
    private lateinit var pText: TextView
    private var popupIntent: android.app.PendingIntent? = null
    // tiles
    private val accentTints = ArrayList<ImageView>()
    private val accentCircles = ArrayList<View>()
    private lateinit var heroMusicTitle: TextView
    private lateinit var heroMusicSub: TextView
    private lateinit var heroMusicArt: ImageView

    private var breathAnim: ObjectAnimator? = null
    private var carAspect = 2f
    private var carBmp: Bitmap? = null
    private var carBody: RectF? = null          // solid car body inside the home picture, 0..1
    private val carRect = RectF()               // where the home picture sits on screen
    private var showroom: ShowroomView? = null

    init {
        root.setBackgroundColor(0xFF030405.toInt())
        root.addOnLayoutChangeListener { _, l, t, r, b, ol, ot, or_, ob ->
            if (r - l != or_ - ol || b - t != ob - ot) main.post { safe { build(force = true) } }
        }
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, act.resources.displayMetrics)
    private fun hf(f: Float) = (H * f)
    private fun t(key: String) = S.get(key, lang)
    private val rtl get() = lang.rtl

    /** Absolute placement with optional mirroring for right-to-left languages. */
    private fun FrameLayout.place(v: View, x: Float, y: Float, w: Int, h: Int, mirror: Boolean = true) {
        val lp = FrameLayout.LayoutParams(w, h)
        lp.gravity = Gravity.TOP or Gravity.LEFT
        lp.leftMargin = (if (mirror && rtl) W - x - w else x).toInt()
        lp.topMargin = y.toInt()
        addView(v, lp)
    }

    private fun text(size: Float, color: Int = FG, tf: Typeface = REG, lines: Int = 1): TextView = TextView(act).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size)
        setTextColor(color); typeface = tf
        maxLines = lines; ellipsize = TextUtils.TruncateAt.END
        includeFontPadding = false
        textDirection = View.TEXT_DIRECTION_FIRST_STRONG
    }

    private fun icon(res: Int, tint: Int = FG2): ImageView = ImageView(act).apply {
        setImageResource(res); imageTintList = ColorStateList.valueOf(tint); scaleType = ImageView.ScaleType.FIT_CENTER
    }

    private fun circle(color: Int): GradientDrawable = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) }

    /* --------------------------------------------------------------- state from prefs */

    fun refresh() = safe { refreshInner() }

    private fun refreshInner() {
        lang = Lang.values().firstOrNull { it.code == prefs.getString("lang", null) } ?: run {
            when (Locale.getDefault().language) { "iw", "he" -> Lang.HE; "en" -> Lang.EN; else -> Lang.AR }
        }
        val perf = prefs.getString("perf", "auto") ?: "auto"
        lite = perf == "lite"
        theme = prefs.getString("theme", "car") ?: "car"
        cars = CarRepo.load(act)
        val id = prefs.getString("car", "superb") ?: "superb"
        car = cars.firstOrNull { it.id == id } ?: cars.firstOrNull()
        colorK = prefs.getString("color", "graphite") ?: "graphite"
        val c = car
        val col = c?.colors?.firstOrNull { it.k == colorK } ?: c?.colors?.firstOrNull()
        carGlow = col?.let { glowFrom(it.hex).toArgbInt() } ?: 0xFF6F8DB8.toInt()
        build(force = false)
        updateAccent()
        tickClock()
    }

    private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
        Color.argb((alpha * 255).toInt(), (red * 255).toInt(), (green * 255).toInt(), (blue * 255).toInt())

    private fun targetAccent(): Int = when (theme) {
        "car" -> carGlow
        "music" -> musicGlow ?: carGlow
        else -> THEMES.firstOrNull { it.id == theme }?.color?.toArgbInt() ?: carGlow
    }

    private fun updateAccent() {
        val a = targetAccent()
        if (!::bg.isInitialized) { accent = a; return }
        accent = a
        bg.accent = a
        glow.imageTintList = ColorStateList.valueOf(a)
        if (::bloom.isInitialized) bloom.imageTintList = ColorStateList.valueOf(a)
        (plateDot.background as? GradientDrawable)?.setColor(a)
        speed.accent = a
        (mProg.background as? GradientDrawable)?.setColor(a)
        (dockActiveDot.background as? GradientDrawable)?.setColor(a)
        dockActiveBg.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; gradientType = GradientDrawable.RADIAL_GRADIENT
            gradientRadius = hf(0.05f); colors = intArrayOf(withAlpha(a, 0.32f), 0)
        }
        refreshDots()
        accentTints.forEach { it.imageTintList = ColorStateList.valueOf(mixWhite(a, 0.6f)) }
        accentCircles.forEach { v ->
            v.background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL; gradientType = GradientDrawable.RADIAL_GRADIENT
                gradientRadius = (v.layoutParams?.width ?: 100) / 2f
                colors = intArrayOf(withAlpha(a, 0.45f), withAlpha(a, 0.07f))
            }
        }
        refreshPageDots()
    }

    /* --------------------------------------------------------------- build */

    private fun build(force: Boolean) {
        val w = root.width; val h = root.height
        if (w <= 0 || h <= 0) return
        val key = "$w:$h:${lang.code}:${car?.id}:$colorK:$lite"
        if (!force && key == builtKey) return
        builtKey = key
        W = w; H = h
        closeShowroomNow()
        breathAnim?.cancel()
        shineStop()
        root.removeAllViews()
        accentTints.clear(); accentCircles.clear()
        root.layoutDirection = if (rtl) View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR

        bg = BackgroundView(act)
        root.addView(bg, FrameLayout.LayoutParams(-1, -1))

        pager = PagerFrame(act)
        root.addView(pager, FrameLayout.LayoutParams(-1, -1))
        val heroPage = FrameLayout(act)
        val tilesPage = FrameLayout(act)
        pager.addPage(heroPage)
        pager.addPage(tilesPage)
        pager.onPage = { refreshPageDots() }

        buildHero(heroPage)
        if (car == null) buildEmptyCar(heroPage)
        buildTiles(tilesPage)

        hud = FrameLayout(act)
        root.addView(hud, FrameLayout.LayoutParams(-1, -1))
        buildTopBar(hud)
        buildDots(hud)
        buildDock(hud)
        buildPopup(root)

        accent = targetAccent()
        bg.accent = accent
        updateAccent()
        loadCar()
        bindLive()
        boot()
    }

    private fun buildHero(page: FrameLayout) {
        // breathing under-glow (alpha animated by the GPU)
        glow = ImageView(act).apply {
            setImageBitmap(radialBitmap(256, 64))
            scaleType = ImageView.ScaleType.FIT_XY
            imageTintList = ColorStateList.valueOf(accent)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        page.place(glow, W * 0.18f, hf(0.655f) - hf(0.07f), (W * 0.64f).toInt(), hf(0.14f).toInt(), mirror = false)

        // big soft light behind the car that pulses with the music's bass
        bloom = ImageView(act).apply {
            setImageBitmap(radialBitmap(256, 160)); scaleType = ImageView.ScaleType.FIT_XY
            imageTintList = ColorStateList.valueOf(accent); alpha = 0f
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
        page.place(bloom, W * 0.15f, hf(0.12f), (W * 0.70f).toInt(), hf(0.62f).toInt(), mirror = false)

        carLayer = FrameLayout(act)
        page.addView(carLayer, FrameLayout.LayoutParams(-1, -1))
        refl = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_XY; alpha = 0.16f }
        carOff = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_XY }
        carOn = ImageView(act).apply { scaleType = ImageView.ScaleType.FIT_XY; alpha = 0f }
        shine = ShineView(act)
        carLayer.addView(refl); carLayer.addView(carOff); carLayer.addView(carOn); carLayer.addView(shine)
        carOff.setOnClickListener { if (car?.spin != null) openShowroom() else boot() }

        drl = View(act).apply {
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(0, mixWhite(accent, 0.7f), Color.WHITE, mixWhite(accent, 0.7f), 0))
            alpha = 0f; scaleX = 0f
        }
        page.place(drl, W * 0.19f, hf(0.50f), (W * 0.62f).toInt(), dp(2f).toInt(), mirror = false)

        greet = text(hf(0.055f), FG, THIN).apply { gravity = Gravity.CENTER; alpha = 0f }
        page.place(greet, 0f, hf(0.33f), W, hf(0.075f).toInt(), mirror = false)
        greetName = text(hf(0.03f), FG, LIGHT).apply { gravity = Gravity.CENTER; alpha = 0f; letterSpacing = 0.08f }
        page.place(greetName, 0f, hf(0.415f), W, hf(0.045f).toInt(), mirror = false)

        nameplate = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val brand = text(hf(0.026f), FG).apply { letterSpacing = 0.35f; text = car?.brand?.uppercase() ?: "" }
        plateDot = View(act).apply { background = circle(accent) }
        val model = text(hf(0.026f), MUTED).apply { letterSpacing = 0.35f; text = car?.model?.uppercase() ?: "" }
        nameplate.addView(brand)
        nameplate.addView(plateDot, LinearLayout.LayoutParams(dp(4f).toInt(), dp(4f).toInt()).apply { marginStart = dp(14f).toInt(); marginEnd = dp(14f).toInt() })
        nameplate.addView(model)
        page.place(nameplate, 0f, hf(0.135f), W, hf(0.05f).toInt(), mirror = false)

        // speed
        speed = SpeedArcView(act).apply { unit = t("kmh"); accent = this@HomeUi.accent }
        val sz = hf(0.36f).toInt()
        page.place(speed, W * 0.03f, hf(0.22f), sz, sz)

        // trip computer under the speedometer
        val trip = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        tripVal = text(hf(0.03f), FG, LIGHT).apply { gravity = Gravity.CENTER }
        tripSub = text(hf(0.019f), MUTED).apply { gravity = Gravity.CENTER; letterSpacing = 0.06f }
        trip.addView(tripVal); trip.addView(tripSub)
        page.place(trip, W * 0.03f, hf(0.22f) + sz + hf(0.005f), sz, hf(0.08f).toInt())
        bindTrip(Trip.state.value)

        // cards
        val cw = (W * 0.20f).toInt()
        val mediaCard = buildMediaCard(cw)
        page.place(mediaCard, W - W * 0.04f - cw, hf(0.24f), cw, hf(0.33f).toInt())
        val navCard = buildNavCard(cw)
        page.place(navCard, W - W * 0.04f - cw, hf(0.24f) + hf(0.33f) + hf(0.025f), cw, hf(0.17f).toInt())
    }

    private fun radialBitmap(w: Int, h: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.save(); c.scale(1f, h.toFloat() / w)
        p.shader = RadialGradient(w / 2f, w / 2f, w / 2f, intArrayOf(0xA6FFFFFF.toInt(), 0x24FFFFFF, 0), floatArrayOf(0f, 0.45f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(w / 2f, w / 2f, w / 2f, p)
        c.restore()
        return b
    }

    private fun cardShell(): Pair<FrameLayout, LinearLayout> {
        val frame = FrameLayout(act).apply { background = glass(dp(20f)); clipToOutline = true }
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f).toInt(), dp(14f).toInt(), dp(16f).toInt(), dp(14f).toInt()) }
        return frame to col
    }

    private fun label(s: String) = text(hf(0.017f), MUTED).apply { text = s; letterSpacing = 0.12f }

    private fun buildMediaCard(cw: Int): View {
        val (frame, col) = cardShell()
        mArtBg = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.38f; visibility = View.GONE }
        frame.addView(mArtBg, FrameLayout.LayoutParams(-1, -1))
        val shade = View(act).apply { background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x99040506.toInt(), 0xE6040506.toInt())); visibility = View.GONE }
        frame.addView(shade, FrameLayout.LayoutParams(-1, -1))
        mArtBg.tag = shade
        frame.addView(col, FrameLayout.LayoutParams(-1, -1))

        col.addView(label(t("nowPlaying")))
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        mArt = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = GradientDrawable().apply { cornerRadius = dp(10f) }; visibility = View.GONE }
        val artSz = hf(0.075f).toInt()
        row.addView(mArt, LinearLayout.LayoutParams(artSz, artSz).apply { marginEnd = dp(12f).toInt() })
        val tcol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        mTitle = text(hf(0.026f), FG).apply { text = t("nothingPlaying") }
        mArtist = text(hf(0.02f), MUTED).apply { text = t("tapMusic") }
        tcol.addView(mTitle); tcol.addView(mArtist)
        row.addView(tcol, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f).toInt() })

        mProgTrack = FrameLayout(act).apply { background = GradientDrawable().apply { setColor(0x14FFFFFF); cornerRadius = 4f } }
        mProg = View(act).apply { background = GradientDrawable().apply { setColor(accent); cornerRadius = 4f } }
        mProgTrack.addView(mProg, FrameLayout.LayoutParams(0, -1))
        col.addView(mProgTrack, LinearLayout.LayoutParams(-1, dp(2f).toInt()).apply { topMargin = dp(14f).toInt() })

        val ctr = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        val isz = hf(0.07f).toInt()
        val prev = icon(R.drawable.ic_prev).apply { setPadding(isz / 5, isz / 5, isz / 5, isz / 5); pressable(0.8f) { transport("prev") } }
        mPlay = icon(R.drawable.ic_play, 0xFF040506.toInt()).apply {
            background = circle(FG); setPadding(isz / 4, isz / 4, isz / 4, isz / 4); pressable(0.85f) { transport("play") }
        }
        val next = icon(R.drawable.ic_next).apply { setPadding(isz / 5, isz / 5, isz / 5, isz / 5); pressable(0.8f) { transport("next") } }
        ctr.addView(prev, LinearLayout.LayoutParams(isz, isz))
        ctr.addView(Space(act), LinearLayout.LayoutParams(0, 1, 1f))
        ctr.addView(mPlay, LinearLayout.LayoutParams(isz, isz))
        ctr.addView(Space(act), LinearLayout.LayoutParams(0, 1, 1f))
        ctr.addView(next, LinearLayout.LayoutParams(isz, isz))
        col.addView(ctr, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f).toInt() })

        frame.pressable(0.97f) { openMusic() }
        return frame
    }

    private fun buildNavCard(cw: Int): View {
        val (frame, col) = cardShell()
        frame.addView(col, FrameLayout.LayoutParams(-1, -1))
        col.addView(label(t("navigation")))
        val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        nIcon = ImageView(act).apply { visibility = View.GONE }
        row.addView(nIcon, LinearLayout.LayoutParams(hf(0.065f).toInt(), hf(0.065f).toInt()).apply { marginEnd = dp(12f).toInt() })
        val tcol = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        nTitle = text(hf(0.027f), FG, REG, 2).apply { text = t("whereTo") }
        nSub = text(hf(0.02f), MUTED).apply { text = "Waze" }
        tcol.addView(nTitle); tcol.addView(nSub)
        row.addView(tcol, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(row, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10f).toInt() })
        frame.pressable(0.97f) {
            val n = Hub.nav.value
            if (n != null) act.launchAny(n.pkg) else act.launchAny("com.waze", "com.google.android.apps.maps", toastKey = "notInstalled", lang = lang)
        }
        return frame
    }

    private fun buildTopBar(p: FrameLayout) {
        clock = text(hf(0.075f), FG, THIN)
        date = text(hf(0.022f), 0xFF8A8F96.toInt())
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.START }
        col.addView(clock); col.addView(date)
        p.place(col, W * 0.04f, hf(0.035f), (W * 0.3f).toInt(), hf(0.13f).toInt())
        val st = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        fun statusRow(label: String): View {
            val r = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL or Gravity.END }
            r.addView(text(hf(0.018f), MUTED).apply { text = label; letterSpacing = 0.2f })
            val d = View(act).apply { background = circle(0xFF3A3E44.toInt()) }
            r.addView(d, LinearLayout.LayoutParams(dp(6f).toInt(), dp(6f).toInt()).apply { marginStart = dp(8f).toInt() })
            r.tag = d
            return r
        }
        val g = statusRow("GPS"); val l = statusRow("LIVE")
        gpsDot = g.tag as View; liveDot = l.tag as View
        st.addView(g); st.addView(l, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(6f).toInt() })
        p.place(st, W - W * 0.04f - W * 0.2f, hf(0.045f), (W * 0.2f).toInt(), hf(0.1f).toInt())
        tickClock()
    }

    private fun refreshDots() {
        if (!::gpsDot.isInitialized) return
        (gpsDot.background as GradientDrawable).setColor(if (Hub.gpsFix.value) accent else 0xFF3A3E44.toInt())
        (liveDot.background as GradientDrawable).setColor(if (Hub.listenerConnected.value) accent else 0xFF3A3E44.toInt())
    }

    private fun buildDots(p: FrameLayout) {
        dots = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER; layoutDirection = View.LAYOUT_DIRECTION_LTR }
        repeat(2) {
            dots.addView(View(act).apply { background = GradientDrawable().apply { cornerRadius = 10f; setColor(0x40FFFFFF) } },
                LinearLayout.LayoutParams(dp(5f).toInt(), dp(5f).toInt()).apply { marginStart = dp(4f).toInt(); marginEnd = dp(4f).toInt() })
        }
        p.place(dots, 0f, H - hf(0.165f), W, dp(8f).toInt(), mirror = false)
        refreshPageDots()
    }

    private fun refreshPageDots() {
        if (!::dots.isInitialized) return
        for (i in 0 until dots.childCount) {
            val v = dots.getChildAt(i)
            val on = ::pager.isInitialized && pager.page == i
            (v.background as GradientDrawable).setColor(if (on) accent else 0x40FFFFFF)
            v.layoutParams = (v.layoutParams as LinearLayout.LayoutParams).apply { width = dp(if (on) 18f else 5f).toInt() }
        }
    }

    private fun buildDock(p: FrameLayout) {
        val s = hf(0.09f).toInt()
        val dock = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = glass(s.toFloat())
            setPadding(dp(12f).toInt(), dp(6f).toInt(), dp(12f).toInt(), dp(6f).toInt())
        }
        fun btn(res: Int, active: Boolean = false, onClick: () -> Unit) {
            val f = FrameLayout(act)
            if (active) {
                dockActiveBg = View(act)
                f.addView(dockActiveBg, FrameLayout.LayoutParams(-1, -1))
            }
            val ic = icon(res, if (active) FG else FG2)
            val pad = (s * 0.29f).toInt()
            ic.setPadding(pad, pad, pad, pad)
            f.addView(ic, FrameLayout.LayoutParams(-1, -1))
            if (active) {
                dockActiveDot = View(act).apply { background = circle(accent) }
                f.addView(dockActiveDot, FrameLayout.LayoutParams(dp(5f).toInt(), dp(5f).toInt(), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = (s * 0.07f).toInt() })
            }
            f.pressable(0.86f, onClick)
            dock.addView(f, LinearLayout.LayoutParams(s, s).apply { marginStart = dp(4f).toInt(); marginEnd = dp(4f).toInt() })
        }
        btn(R.drawable.ic_nav) { act.launchAny("com.waze", "com.google.android.apps.maps", toastKey = "notInstalled", lang = lang) }
        btn(R.drawable.ic_bt) { if (!VendorApps.open(act, "phone")) act.openDialer() }
        btn(R.drawable.ic_music) { openMusic() }
        btn(R.drawable.ic_car, active = true) { if (pager.page != 0) pager.go(0) else boot() }
        btn(R.drawable.ic_radio) { if (!VendorApps.open(act, "radio")) toastMissing() }
        btn(R.drawable.ic_apps) { act.openPanel("apps") }
        btn(R.drawable.ic_tune) { act.openPanel("settings") }
        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = hf(0.035f).toInt() }
        p.addView(dock, lp)
    }

    private fun toastMissing() = Toast.makeText(act, t("notInstalled"), Toast.LENGTH_SHORT).show()

    private fun buildPopup(p: FrameLayout) {
        popup = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { cornerRadius = dp(22f); setColor(0xF0141619.toInt()); setStroke(1, 0x1FFFFFFF) }
            setPadding(dp(18f).toInt(), dp(12f).toInt(), dp(14f).toInt(), dp(12f).toInt())
            alpha = 0f; visibility = View.GONE
        }
        pIcon = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true; background = circle(0xFF222222.toInt()) }
        popup.addView(pIcon, LinearLayout.LayoutParams(dp(44f).toInt(), dp(44f).toInt()).apply { marginEnd = dp(14f).toInt() })
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        pApp = text(dp(12f), MUTED); pTitle = text(dp(17f), FG); pText = text(dp(15f), FG2, REG, 2)
        col.addView(pApp); col.addView(pTitle); col.addView(pText)
        popup.addView(col, LinearLayout.LayoutParams(0, -2, 1f))
        val close = icon(R.drawable.ic_close, MUTED).apply { pressable(0.8f) { hidePopup() } }
        popup.addView(close, LinearLayout.LayoutParams(dp(30f).toInt(), dp(30f).toInt()).apply { marginStart = dp(10f).toInt() })
        popup.pressable(0.97f) { try { popupIntent?.send() } catch (_: Throwable) {}; hidePopup() }
        val w = min((W * 0.5f).toInt(), dp(560f).toInt())
        p.addView(popup, FrameLayout.LayoutParams(w, -2, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(16f).toInt() })
    }

    private val hidePopupRun = Runnable { hidePopup() }
    private fun showPopup(n: Hub.Notif) {
        popupIntent = n.intent
        pApp.text = n.app; pTitle.text = n.title; pText.text = n.text
        pText.visibility = if (n.text.isBlank()) View.GONE else View.VISIBLE
        if (n.icon != null) pIcon.setImageBitmap(n.icon) else pIcon.setImageDrawable(null)
        popup.visibility = View.VISIBLE
        popup.translationY = -dp(30f)
        popup.animate().alpha(1f).translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator()).start()
        main.removeCallbacks(hidePopupRun)
        main.postDelayed(hidePopupRun, 6500)
    }
    private fun hidePopup() {
        popup.animate().alpha(0f).translationY(-dp(30f)).setDuration(240).withEndAction { popup.visibility = View.GONE }.start()
    }

    /* --------------------------------------------------------------- tiles page */

    private data class TileDef(val id: String, val key: String, val icon: Int)
    private val TILE_DEFS = listOf(
        TileDef("radio", "t_radio", R.drawable.ic_radio), TileDef("music", "t_music", R.drawable.ic_music),
        TileDef("nav", "t_nav", R.drawable.ic_nav), TileDef("zlink", "t_zlink", R.drawable.ic_zlink),
        TileDef("video", "t_video", R.drawable.ic_video), TileDef("youtube", "t_youtube", R.drawable.ic_youtube),
        TileDef("camera", "t_camera", R.drawable.ic_camera), TileDef("carset", "t_carset", R.drawable.ic_car),
        TileDef("dsp", "t_dsp", R.drawable.ic_sound), TileDef("fan", "t_fan", R.drawable.ic_fan),
        TileDef("files", "t_files", R.drawable.ic_folder),
    )

    private fun buildTiles(page: FrameLayout) {
        val found = VendorApps.resolveAll(act)
        val padX = W * 0.05f
        val top = hf(0.17f)
        val bottom = hf(0.19f)
        val areaH = H - top - bottom
        val col = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        page.place(col, padX, top, (W - padX * 2).toInt(), areaH.toInt(), mirror = false)

        // hero row
        val hero = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        col.addView(hero, LinearLayout.LayoutParams(-1, 0, 1.15f))
        val gap = (W * 0.015f).toInt()
        fun heroTile(icon: Int, title: String, sub: String, withArt: Boolean, onClick: () -> Unit): Triple<FrameLayout, TextView, TextView> {
            val f = FrameLayout(act).apply { background = glass(dp(24f)); clipToOutline = true }
            if (withArt) {
                heroMusicArt = ImageView(act).apply { scaleType = ImageView.ScaleType.CENTER_CROP; alpha = 0.45f; visibility = View.GONE }
                f.addView(heroMusicArt, FrameLayout.LayoutParams(-1, -1))
            }
            val r = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(22f).toInt(), 0, dp(22f).toInt(), 0) }
            val cs = hf(0.11f).toInt()
            val c = FrameLayout(act)
            accentCircles.add(c)
            val ic = icon(icon, Color.WHITE).apply { val pd = (cs * 0.26f).toInt(); setPadding(pd, pd, pd, pd) }
            c.addView(ic, FrameLayout.LayoutParams(-1, -1))
            r.addView(c, LinearLayout.LayoutParams(cs, cs).apply { marginEnd = dp(18f).toInt() })
            val tc = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
            val tt = text(hf(0.034f), FG).apply { text = title }
            val ts = text(hf(0.021f), MUTED).apply { text = sub }
            tc.addView(tt); tc.addView(ts)
            r.addView(tc, LinearLayout.LayoutParams(0, -2, 1f))
            f.addView(r, FrameLayout.LayoutParams(-1, -1))
            f.pressable(0.96f, onClick)
            return Triple(f, tt, ts)
        }
        val (phone, _, _) = heroTile(R.drawable.ic_bt, t("t_phone"), t("bluetoothSub"), false) { if (!VendorApps.open(act, "phone")) act.openDialer() }
        val (music, mt, ms) = heroTile(R.drawable.ic_btmusic, t("t_btmusic"), t("tapMusic"), true) { openMusic() }
        heroMusicTitle = mt; heroMusicSub = ms
        hero.addView(phone, LinearLayout.LayoutParams(0, -1, 1f).apply { marginEnd = gap / 2 })
        hero.addView(music, LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = gap / 2 })

        val list = TILE_DEFS.filter { found.containsKey(it.id) } + TileDef("__apps", "t_apps", R.drawable.ic_apps)
        list.chunked(6).forEach { rowDefs ->
            val row = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            col.addView(row, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = (H * 0.02f).toInt() })
            rowDefs.forEachIndexed { i, d ->
                val tile = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = glass(dp(20f))
                }
                val cs = hf(0.068f).toInt()
                val c = FrameLayout(act); accentCircles.add(c)
                val ic = icon(d.icon, mixWhite(accent, 0.6f)).apply { val pd = (cs * 0.22f).toInt(); setPadding(pd, pd, pd, pd) }
                accentTints.add(ic)
                c.addView(ic, FrameLayout.LayoutParams(-1, -1))
                tile.addView(c, LinearLayout.LayoutParams(cs, cs))
                tile.addView(text(hf(0.02f), FG).apply { text = t(d.key); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = hf(0.012f).toInt() })
                tile.pressable(0.92f) {
                    if (d.id == "__apps") act.openPanel("apps")
                    else if (!VendorApps.open(act, d.id)) toastMissing()
                }
                row.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i > 0) marginStart = (W * 0.012f).toInt() })
            }
            repeat(6 - rowDefs.size) { row.addView(Space(act), LinearLayout.LayoutParams(0, -1, 1f).apply { marginStart = (W * 0.012f).toInt() }) }
        }
    }

    /* --------------------------------------------------------------- car images */

    private fun loadCar() {
        val c = car ?: return
        val k = if (c.colors.any { it.k == colorK }) colorK else c.colors.first().k
        val maxW = if (lite) 640 else 760
        act.lifecycleScope.launch {
            val r = withContext(Dispatchers.IO) {
                val off = shrinkBmp(CarRepo.bitmap(act, c, k + "_off"), maxW)
                val on = shrinkBmp(CarRepo.bitmap(act, c, k + "_on"), maxW) ?: off
                val refl = off?.let { reflectionOf(it) }
                val mask = if (!lite) off?.extractAlpha() else null
                carBody = off?.let { ShowroomView.bodyBox(it) }
                arrayOf(off, on, refl, mask)
            }
            val off = r[0] ?: return@launch
            if (off.isRecycled) return@launch
            carBmp = off
            carOff.setImageBitmap(off); carOn.setImageBitmap(r[1]); refl.setImageBitmap(r[2])
            shine.mask = r[3]
            carAspect = off.width.toFloat() / off.height
            layoutCar()
        }
    }

    private fun layoutCar() {
        val bx = W * 0.21f; val by = hf(0.20f); val bw = W * 0.60f; val bh = hf(0.46f)
        var ch = bh; var cw = ch * carAspect
        if (cw > bw) { cw = bw; ch = cw / carAspect }
        val x = bx + (bw - cw) / 2f
        val y = by + bh - ch
        fun set(v: View, yy: Float) {
            v.layoutParams = FrameLayout.LayoutParams(cw.toInt(), ch.toInt()).apply { gravity = Gravity.TOP or Gravity.LEFT; leftMargin = x.toInt(); topMargin = yy.toInt() }
        }
        set(carOff, y); set(carOn, y); set(shine, y); set(refl, y + ch * 0.93f)
        carRect.set(x, y, x + cw, y + ch)
    }

    private fun shrinkBmp(b: Bitmap?, maxW: Int): Bitmap? {
        b ?: return null
        if (b.width <= maxW) return b
        return Bitmap.createScaledBitmap(b, maxW, (b.height * maxW.toFloat() / b.width).toInt().coerceAtLeast(1), true)
    }

    private fun reflectionOf(src: Bitmap): Bitmap? = try {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val m = Matrix().apply { setScale(1f, -1f); postTranslate(0f, src.height.toFloat()) }
        c.drawBitmap(src, m, null)
        val p = Paint().apply {
            shader = LinearGradient(0f, 0f, 0f, src.height * 0.38f, Color.BLACK, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        c.drawRect(0f, 0f, src.width.toFloat(), src.height.toFloat(), p)
        out
    } catch (_: Throwable) { null }

    /* --------------------------------------------------------------- showroom */

    private fun openShowroom() = safe {
        val c = car ?: return@safe
        val sp = c.spin ?: return@safe
        if (showroom != null || pager.page != 0 || W == 0) return@safe
        if ((Hub.speedKmh.value ?: 0) > 10) { boot(); return@safe }   // no showroom while driving
        val nb = carBody
        val body = if (nb != null && carRect.width() > 0f) RectF(
            carRect.left + nb.left * carRect.width(), carRect.top + nb.top * carRect.height(),
            carRect.left + nb.right * carRect.width(), carRect.top + nb.bottom * carRect.height()
        ) else null
        val seen = prefs.getInt("srHint", 0)
        val hint = if (seen < 3) t("showroomHint") else null
        if (seen < 3) prefs.edit().putInt("srHint", seen + 1).apply()
        val v = ShowroomView(act, sp, { r, i -> CarRepo.spinFile(act, c, r, i) }, accent,
            c.brand.uppercase() + "   ·   " + c.model.uppercase(), hint, body)
        v.onClosing = { safe { showHome() } }
        v.onClosed = { safe { if (showroom === v) showroom = null; root.removeView(v); v.release(); resumeHomeFx() } }
        showroom = v
        val at = if (::popup.isInitialized) root.indexOfChild(popup).let { if (it < 0) root.childCount else it } else root.childCount
        root.addView(v, at, FrameLayout.LayoutParams(-1, -1))
        try { root.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) } catch (_: Throwable) {}
        v.prepare {
            safe {
                if (showroom !== v) return@safe
                pauseHomeFx()
                pager.animate().alpha(0f).setStartDelay(0).setDuration(260).withEndAction { if (showroom != null) pager.visibility = View.INVISIBLE }.start()
                hud.animate().alpha(0f).setStartDelay(0).setDuration(220).withEndAction { if (showroom != null) hud.visibility = View.INVISIBLE }.start()
                v.open()
            }
        }
    }

    private fun showHome() {
        pager.visibility = View.VISIBLE; hud.visibility = View.VISIBLE
        pager.animate().alpha(1f).setStartDelay(160).setDuration(380).start()
        hud.animate().alpha(1f).setStartDelay(260).setDuration(380).start()
    }

    private fun pauseHomeFx() { breathAnim?.pause(); shineStop(); stopBeat() }
    private fun resumeHomeFx() { if (!resumed) return; breathAnim?.resume(); if (!lite) shineLoop(); updateBeat() }

    private fun closeShowroomNow() {
        val v = showroom ?: return
        showroom = null
        try { v.release(); root.removeView(v) } catch (_: Throwable) {}
        if (::pager.isInitialized) { pager.animate().cancel(); pager.visibility = View.VISIBLE; pager.alpha = 1f }
        if (::hud.isInitialized) { hud.animate().cancel(); hud.visibility = View.VISIBLE; hud.alpha = 1f }
    }

    /** Back button / home key: leave the showroom first. */
    fun onBack(): Boolean {
        val v = showroom ?: return false
        v.close()
        return true
    }

    private fun buildEmptyCar(page: FrameLayout) {
        val box = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        box.addView(icon(R.drawable.ic_car, 0x38FFFFFF), LinearLayout.LayoutParams(hf(0.14f).toInt(), hf(0.14f).toInt()))
        box.addView(text(hf(0.04f), FG, THIN).apply { text = t("noCarYet"); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = hf(0.01f).toInt() })
        box.addView(text(hf(0.022f), MUTED, REG, 2).apply { text = t("noCarYetSub"); gravity = Gravity.CENTER }, LinearLayout.LayoutParams(-2, -2).apply { topMargin = hf(0.008f).toInt() })
        val btn = text(hf(0.024f), FG).apply {
            text = t("carCode"); gravity = Gravity.CENTER; background = glass(hf(0.06f))
            setPadding(dp(26f).toInt(), dp(12f).toInt(), dp(26f).toInt(), dp(12f).toInt())
            pressable(0.94f) { act.openPanel("picker") }
        }
        box.addView(btn, LinearLayout.LayoutParams(-2, -2).apply { topMargin = hf(0.03f).toInt() })
        page.place(box, W * 0.25f, hf(0.2f), (W * 0.5f).toInt(), hf(0.46f).toInt(), mirror = false)
    }

    /* --------------------------------------------------------------- boot animation */

    fun boot() {
        if (!::carOff.isInitialized) return
        closeShowroomNow()
        val fast = lite
        carLayer.animate().cancel(); hud.animate().cancel()
        carLayer.alpha = 0f; carOn.alpha = 0f; hud.alpha = 0f; nameplate.alpha = 0f
        greet.text = greeting(); greetName.text = prefs.getString("name", "") ?: ""
        greet.alpha = 0f; greetName.alpha = 0f
        val gIn = if (fast) 350L else 700L
        greet.animate().alpha(1f).setStartDelay(0).setDuration(gIn).withEndAction {
            greet.animate().alpha(0f).setStartDelay(if (fast) 600 else 900).setDuration(if (fast) 300 else 600).start()
        }.start()
        if (greetName.text.isNotBlank()) greetName.animate().alpha(1f).setStartDelay(0).setDuration(gIn).withEndAction {
            greetName.animate().alpha(0f).setStartDelay(if (fast) 600 else 900).setDuration(if (fast) 300 else 600).start()
        }.start()
        drl.alpha = 0f; drl.scaleX = 0f
        drl.animate().alpha(1f).scaleX(1f).setStartDelay(if (fast) 100 else 250).setDuration(if (fast) 700 else 1100).setInterpolator(DecelerateInterpolator())
            .withEndAction { drl.animate().alpha(0f).setStartDelay(0).setDuration(400).start() }.start()
        carLayer.animate().alpha(1f).setStartDelay(if (fast) 700 else 1400).setDuration(if (fast) 500 else 1200).start()
        carOn.animate().alpha(1f).setStartDelay(if (fast) 1100 else 2400).setDuration(if (fast) 200 else 700).start()
        hud.animate().alpha(1f).setStartDelay(if (fast) 900 else 2300).setDuration(if (fast) 400 else 900).start()
        nameplate.animate().alpha(1f).setStartDelay(if (fast) 900 else 2300).setDuration(if (fast) 400 else 1000).start()

        breathAnim?.cancel()
        glow.alpha = 0.8f
        if (!fast) {
            breathAnim = ObjectAnimator.ofFloat(glow, View.ALPHA, 0.5f, 1f).apply {
                duration = 4200; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE; startDelay = 2000
                start()
            }
            shineLoop()
        }
    }

    private var shineAnim: ValueAnimator? = null
    private val shineRun = Runnable { shineOnce() }
    private fun shineLoop() { main.removeCallbacks(shineRun); main.postDelayed(shineRun, 4500) }
    private fun shineStop() { main.removeCallbacks(shineRun); shineAnim?.cancel() }
    private fun shineOnce() {
        if (!::shine.isInitialized || shine.mask == null || lite) return
        shineAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1700; interpolator = LinearInterpolator()
            addUpdateListener { shine.progress = it.animatedValue as Float }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) { shine.progress = -1f }
            })
            start()
        }
        main.postDelayed(shineRun, 11000)
    }

    private fun greeting(): String {
        val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return t(when (h) { in 5..11 -> "gMorning"; in 12..16 -> "gNoon"; in 17..21 -> "gEvening"; else -> "gNight" })
    }

    /* --------------------------------------------------------------- live data */

    private val clockRun = object : Runnable {
        override fun run() { tickClock(); main.postDelayed(this, 60_000L - System.currentTimeMillis() % 60_000L + 50) }
    }

    private fun tickClock() {
        if (!::clock.isInitialized) return
        val now = Date()
        clock.text = SimpleDateFormat("HH:mm", Locale.US).format(now)
        val pat = when (lang) { Lang.AR -> "EEEE، d MMMM"; Lang.HE -> "EEEE, d בMMMM"; Lang.EN -> "EEEE, d MMMM" }
        date.text = SimpleDateFormat(pat, lang.locale).format(now)
    }

    private var lastArt: Bitmap? = null
    private val progRun = object : Runnable {
        override fun run() { updateProgress(); if (Hub.media.value?.playing == true) main.postDelayed(this, 1000) }
    }

    private fun updateProgress() {
        val m = Hub.media.value
        val frac = if (m != null && m.duration > 0) {
            val pos = if (m.playing && m.posTime > 0) m.position + ((SystemClock.elapsedRealtime() - m.posTime) * m.speed).toLong() else m.position
            (pos.toFloat() / m.duration).coerceIn(0f, 1f)
        } else 0f
        val w = (mProgTrack.width * frac).toInt()
        val lp = mProg.layoutParams
        if (lp.width != w) { lp.width = w; mProg.layoutParams = lp }
    }

    private fun bindMedia(m: Hub.Media?) {
        val has = m != null && (m.title.isNotBlank() || m.artist.isNotBlank())
        mTitle.text = if (has) m!!.title.ifBlank { m.artist } else t("nothingPlaying")
        mArtist.text = if (has) m!!.artist else t("tapMusic")
        mPlay.setImageResource(if (m?.playing == true) R.drawable.ic_pause else R.drawable.ic_play)
        val art = m?.art
        if (art !== lastArt) {
            lastArt = art
            val shade = mArtBg.tag as View
            if (art != null) {
                mArt.setImageBitmap(art); mArt.visibility = View.VISIBLE
                mArtBg.setImageBitmap(art); mArtBg.visibility = View.VISIBLE; shade.visibility = View.VISIBLE
                heroMusicArt.setImageBitmap(art); heroMusicArt.visibility = View.VISIBLE
            } else {
                mArt.visibility = View.GONE; mArtBg.visibility = View.GONE; shade.visibility = View.GONE
                heroMusicArt.visibility = View.GONE
            }
            if (theme == "music") {
                act.lifecycleScope.launch {
                    val c = withContext(Dispatchers.Default) { try { paletteGlow(art) } catch (_: Throwable) { null } }
                    musicGlow = c?.toArgbInt()
                    safe { updateAccent() }
                }
            }
        }
        heroMusicTitle.text = if (has) mTitle.text else t("t_btmusic")
        heroMusicSub.text = if (has) mArtist.text else t("tapMusic")
        updateBeat()
        main.removeCallbacks(progRun)
        updateProgress()
        if (m?.playing == true) main.postDelayed(progRun, 1000)
    }

    private fun bindNav(n: Hub.Nav?) {
        if (n != null) {
            nTitle.text = n.title; nSub.text = n.text
            if (n.icon != null) { nIcon.setImageBitmap(n.icon); nIcon.visibility = View.VISIBLE } else nIcon.visibility = View.GONE
        } else {
            nTitle.text = t("whereTo"); nSub.text = "Waze"; nIcon.visibility = View.GONE
        }
    }

    private var bound = false
    private fun bindLive() {
        main.removeCallbacks(clockRun)
        main.post(clockRun)
        bindMedia(Hub.media.value); bindNav(Hub.nav.value); refreshDots()
        if (bound) return
        bound = true
        act.lifecycleScope.launch {
            act.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { Hub.media.collect { safe { if (::mTitle.isInitialized) bindMedia(it) } } }
                launch { Hub.nav.collect { safe { if (::nTitle.isInitialized) bindNav(it) } } }
                launch { Hub.speedKmh.collect { safe { if (::speed.isInitialized) speed.setSpeed(it ?: 0); if ((it ?: 0) > 10) showroom?.close() } } }
                launch { Hub.gpsFix.collect { safe { refreshDots() } } }
                launch { Hub.listenerConnected.collect { safe { refreshDots() } } }
                var lastId = Hub.notif.value?.id ?: -1L
                launch { Hub.notif.collect { n -> safe { if (n != null && n.id != lastId && ::popup.isInitialized) { lastId = n.id; showPopup(n) } } } }
                var lastBoot = Hub.bootTick.value
                launch { Trip.state.collect { safe { if (::tripVal.isInitialized) bindTrip(it) } } }
                launch { Hub.bootTick.collect { b -> safe { if (b != lastBoot) { lastBoot = b; if (::pager.isInitialized && pager.page != 0) pager.go(0, false); boot() } } } }
            }
        }
    }

    private inline fun safe(block: () -> Unit) {
        try { block() } catch (e: Throwable) { CrashLog.record(act, e, fatal = false, thread = "home") }
    }

    /* --------------------------------------------------------------- actions */

    private fun transport(what: String) {
        val c = Hub.controller
        when (what) {
            "prev" -> c?.transportControls?.skipToPrevious() ?: act.mediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            "next" -> c?.transportControls?.skipToNext() ?: act.mediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
            else -> if (c != null) {
                if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play()
            } else act.mediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        }
    }

    private fun openMusic() {
        val pkg = Hub.media.value?.pkg.orEmpty()
        if (pkg.isNotBlank() && act.launchAny(pkg)) return
        if (VendorApps.open(act, "btmusic") || VendorApps.open(act, "music")) return
        act.launchAny("com.spotify.music", "com.soundcloud.android", "com.google.android.apps.youtube.music", toastKey = "notInstalled", lang = lang)
    }

    private var resumed = false
    fun onPause() { resumed = false; closeShowroomNow(); stopBeat(); breathAnim?.pause(); shineStop() }
    fun onResume() { resumed = true; breathAnim?.resume(); if (!lite && ::shine.isInitialized) shineLoop(); updateBeat() }

    /* --------------------------------------------------------------- trip */

    private fun bindTrip(s: Trip.State) {
        val km = if (s.km < 10) String.format(Locale.US, "%.1f", s.km) else String.format(Locale.US, "%.0f", s.km)
        val mins = s.sec / 60
        val time = if (mins >= 60) "${mins / 60}${t("hr")} ${mins % 60}${t("min")}" else "$mins ${t("min")}"
        tripVal.text = "$km ${t("km")}  ·  $time"
        tripSub.text = "${t("trip")}  ·  ${t("top")} ${s.maxKmh}"
    }

    /* --------------------------------------------------------------- music-reactive light */

    private var viz: android.media.audiofx.Visualizer? = null
    private var level = 0f
    private var peak = 8f
    private var lastEnergy = 0L

    private fun beatWanted(): Boolean =
        resumed && !lite && showroom == null && prefs.getBoolean("beat", false) && act.hasAudio() &&
            Hub.media.value?.playing == true && ::glow.isInitialized

    fun updateBeat() = safe { if (beatWanted()) startBeat() else stopBeat() }

    private fun startBeat() {
        if (viz != null) return
        try {
            val v = android.media.audiofx.Visualizer(0)
            v.enabled = false
            val range = android.media.audiofx.Visualizer.getCaptureSizeRange()
            v.captureSize = range[0].coerceAtLeast(128).coerceAtMost(range[1])
            val rate = min(android.media.audiofx.Visualizer.getMaxCaptureRate(), 20000)
            v.setDataCaptureListener(object : android.media.audiofx.Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(vz: android.media.audiofx.Visualizer?, wave: ByteArray?, rate: Int) {}
                override fun onFftDataCapture(vz: android.media.audiofx.Visualizer?, fft: ByteArray?, rate: Int) { safe { onFft(fft) } }
            }, rate, false, true)
            v.enabled = true
            viz = v
            lastEnergy = SystemClock.elapsedRealtime()
            breathAnim?.pause()
        } catch (e: Throwable) {
            CrashLog.record(act, e, fatal = false, thread = "beat")
            viz = null
        }
    }

    private fun stopBeat() {
        val v = viz ?: return
        viz = null
        try { v.enabled = false } catch (_: Throwable) {}
        try { v.release() } catch (_: Throwable) {}
        level = 0f
        if (::glow.isInitialized) { glow.scaleX = 1f; glow.scaleY = 1f; glow.alpha = 0.8f }
        if (::bloom.isInitialized) bloom.animate().alpha(0f).setDuration(400).start()
        if (!lite) breathAnim?.resume()
    }

    private fun onFft(fft: ByteArray?) {
        if (fft == null || fft.size < 16) return
        // bass energy: the first few frequency bins
        var bass = 0f
        val bins = min(6, fft.size / 2 - 1)
        for (k in 1..bins) {
            val re = fft[2 * k].toFloat(); val im = fft[2 * k + 1].toFloat()
            bass += kotlin.math.sqrt(re * re + im * im)
        }
        val now = SystemClock.elapsedRealtime()
        if (bass > 3f) lastEnergy = now
        if (now - lastEnergy > 4000) {
            // the unit plays this source outside Android (e.g. some BT/radio paths): keep breathing instead
            if (level > 0f) { level = 0f; bloom.animate().alpha(0f).setDuration(300).start(); breathAnim?.resume() }
            return
        }
        breathAnim?.pause()
        peak = maxOf(peak * 0.996f, bass, 8f)
        val target = (bass / peak).coerceIn(0f, 1f)
        level = if (target > level) level + (target - level) * 0.65f else level * 0.82f
        val l = level
        glow.animate().alpha(0.45f + 0.55f * l).scaleX(1f + 0.10f * l).scaleY(1f + 0.22f * l).setStartDelay(0).setDuration(70).start()
        bloom.animate().alpha(0.55f * l * l).setStartDelay(0).setDuration(70).start()
    }
}
