package com.el7ajb.carlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Choreographer
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import java.io.File
import java.util.concurrent.atomic.AtomicReferenceArray
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Showroom mode: the car alone in a studio, turned by the driver's finger.
 *
 * The car is a ring of pre-rendered 360° frames (no 3D engine on the head unit, so it stays smooth on
 * 2 GB units). While the finger moves we show half-resolution frames that a background thread keeps
 * decoded around the current angle; when the car stops we swap in the full-resolution frame.
 *
 * Gestures: drag sideways = turn, drag up/down = camera higher/lower, pinch = zoom, double tap = back.
 */
@SuppressLint("ViewConstructor")
class ShowroomView(
    ctx: Context,
    private val spin: Spin,
    private val frameFile: (row: Int, idx: Int) -> File,
    private val accent: Int,
    private val label: String,
    private val hint: String?,
    private val homeBody: RectF?,      // where the car body sits on the home screen, in this view's coordinates
    private val keep: Int = 36,        // frames kept decoded on each side of the current angle (less on weak units)
) : View(ctx) {

    var onClosing: (() -> Unit)? = null
    var onClosed: (() -> Unit)? = null

    private val n = spin.n
    private val rows = spin.rows.size
    private var row = spin.startRow
    private var angle = spin.startIdx.toFloat()              // continuous, in frames

    // background decoding
    private val worker = HandlerThread("showroom", android.os.Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val bg = Handler(worker.looper)
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var gen = 0
    @Volatile private var curIdx = spin.startIdx
    @Volatile private var cacheRow = row
    @Volatile private var released = false
    @Volatile private var cache = AtomicReferenceArray<Bitmap?>(n)
    private var full: Bitmap? = null
    private var fullKey = -1
    private var bodyInFrame: RectF? = null                   // car body inside a frame, 0..1

    // animation state
    private var enter = 0f
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var velocity = 0f                                // frames per second
    private var closing = false
    private var opened = false
    private var hintAlpha = 0f

    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val bgPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val txt = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; color = 0xFFECEEF0.toInt() }
    private var studio: Bitmap? = null

    private fun key(r: Int, i: Int) = r * 1000 + i
    private fun mod(i: Int) = ((i % n) + n) % n
    private fun idxOf(a: Float) = mod(a.roundToInt())

    /* ------------------------------------------------------------------ loading */

    /** Decodes the first frame off the UI thread, then calls [ready] on the UI thread. */
    fun prepare(ready: () -> Unit) {
        val r = row; val i = curIdx
        bg.post {
            val b = decode(r, i, 1)
            val body = b?.let { bodyBox(it) }
            main.post {
                if (released) return@post
                if (b == null) { onClosed?.invoke(); return@post }
                full = b; fullKey = key(r, i); bodyInFrame = body
                ready()
                prefetch()
            }
        }
    }

    private fun decode(r: Int, i: Int, sample: Int): Bitmap? = try {
        val f = frameFile(r, i)
        if (!f.exists()) null else BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply {
            inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888
        })
    } catch (_: Throwable) { null }

    /** Keeps half-size frames decoded, nearest to the current angle first. */
    private fun prefetch() {
        val g = ++gen
        val r = row
        bg.post(object : Runnable {
            override fun run() {
                if (g != gen || released) return
                val c = cache
                val center = curIdx
                // weak units: forget frames far from the current angle so memory stays small
                if (keep < n / 2) for (i in 0 until n) {
                    var d = abs(i - center); if (d > n / 2) d = n - d
                    if (d > keep + 2 && c.get(i) != null) c.set(i, null)
                }
                var pick = -1
                for (d in 0..min(keep, n / 2)) {
                    val a = mod(center + d); val b = mod(center - d)
                    if (c.get(a) == null) { pick = a; break }
                    if (c.get(b) == null) { pick = b; break }
                }
                if (pick < 0) { if (keep < n / 2) bg.postDelayed(this, 60); return }
                val bmp = decode(r, pick, 2)
                if (g != gen || released) return
                c.set(pick, bmp ?: EMPTY)
                if (abs(pick - curIdx) <= 2 || abs(pick - curIdx) >= n - 2) postInvalidateOnAnimation()
                bg.post(this)
            }
        })
    }

    private var fullReq = -1
    private fun requestFull() {
        val r = row; val i = idxOf(angle); val k = key(r, i)
        if (fullKey == k || fullReq == k) return
        fullReq = k
        bg.post {
            if (released) return@post
            val b = decode(r, i, 1)
            main.post {
                if (released || b == null) return@post
                if (row == r && idxOf(angle) == i) { full = b; fullKey = k; invalidate() }
                if (fullReq == k) fullReq = -1
            }
        }
    }

    private val settle = Runnable { if (velocity == 0f && !dragging) requestFull() }
    private fun scheduleFull(delay: Long = 90) { main.removeCallbacks(settle); main.postDelayed(settle, delay) }

    private fun setRow(r: Int) {
        val nr = r.coerceIn(0, rows - 1)
        if (nr == row) return
        row = nr
        cacheRow = nr
        cache = AtomicReferenceArray(n)
        fullReq = -1
        requestFull()
        prefetch()
        try { performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) } catch (_: Throwable) {}
    }

    private fun setAngle(a: Float) {
        angle = a
        val i = idxOf(a)
        if (i != curIdx) { curIdx = i; invalidate() }
    }

    fun release() {
        if (released) return
        released = true
        gen++
        main.removeCallbacksAndMessages(null)
        Choreographer.getInstance().removeFrameCallback(flingFrame)
        try { worker.quitSafely() } catch (_: Throwable) {}
        // hand the frames' memory back right away instead of waiting for the garbage collector
        val old = cache
        val list = ArrayList<Bitmap>()
        for (i in 0 until old.length()) old.get(i)?.let { if (it !== EMPTY) list.add(it) }
        full?.let { list.add(it) }
        cache = AtomicReferenceArray(1)
        full = null
        Thread {
            // let the worker finish its last decode and the screen drop the last frame, then recycle
            try { worker.join(1000); Thread.sleep(700) } catch (_: Throwable) {}
            list.forEach { try { if (!it.isRecycled) it.recycle() } catch (_: Throwable) {} }
        }.start()
    }

    /* ------------------------------------------------------------------ open / close */

    fun open() {
        if (opened) return
        opened = true
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 650; interpolator = DecelerateInterpolator(1.8f)
            addUpdateListener { enter = it.animatedValue as Float; invalidate() }
            start()
        }
        // the car turns a little as it rolls into the studio
        val from = angle
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100; startDelay = 120; interpolator = DecelerateInterpolator(2f)
            addUpdateListener { setAngle(from + n * 0.125f * (it.animatedValue as Float)) }
            addListener(object : AnimatorListenerAdapter() { override fun onAnimationEnd(a: Animator) { scheduleFull(0) } })
            start()
        }
        if (hint != null) ValueAnimator.ofFloat(0f, 1f, 1f, 0f).apply {
            duration = 4200; startDelay = 700
            addUpdateListener { hintAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    /** Animated way back to the home screen. */
    fun close() {
        if (closing || !opened) return
        closing = true
        velocity = 0f
        Choreographer.getInstance().removeFrameCallback(flingFrame)
        onClosing?.invoke()
        if (row != spin.startRow) setRow(spin.startRow)
        val target = spin.startIdx.toFloat()
        // shortest way back to the home angle
        var delta = (target - angle) % n
        if (delta > n / 2f) delta -= n
        if (delta < -n / 2f) delta += n
        val a0 = angle; val z0 = zoom; val px0 = panX; val py0 = panY
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 560; interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                val t = it.animatedValue as Float           // 1 -> 0
                val k = 1f - t
                enter = t
                zoom = z0 + (1f - z0) * k; panX = px0 * t; panY = py0 * t
                setAngle(a0 + delta * k)
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: Animator) { onClosed?.invoke() }
            })
            start()
        }
    }

    val isClosing get() = closing

    /* ------------------------------------------------------------------ drawing */

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) { buildStudio(w, h) }

    private fun buildStudio(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val b = try { Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) } catch (_: Throwable) { return }
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, h.toFloat(), intArrayOf(0xFF0E0F12.toInt(), 0xFF08090B.toInt(), 0xFF050506.toInt()), floatArrayOf(0f, 0.62f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // soft ceiling light
        p.shader = RadialGradient(w / 2f, -h * 0.15f, h * 1.05f, intArrayOf(0x24FFFFFF, mix(accent, 0x12), 0), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        // lit floor under the car
        c.save(); c.scale(1f, 0.22f, w / 2f, h * 0.70f)
        p.shader = RadialGradient(w / 2f, h * 0.70f, w * 0.46f, intArrayOf(0x1CFFFFFF, mix(accent, 0x10), 0), floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        c.drawCircle(w / 2f, h * 0.70f, w * 0.46f, p)
        c.restore()
        // vignette
        p.shader = RadialGradient(w / 2f, h * 0.55f, max(w, h) * 0.75f, intArrayOf(0, 0, 0xB0000000.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
        studio = b
    }

    private fun mix(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    private val dst = RectF()
    private val fitR = RectF()
    private val homeR = RectF()

    private fun pick(): Bitmap? {
        val i = idxOf(angle)
        if (fullKey == key(row, i)) full?.let { return it }
        val c = cache
        if (cacheRow == row) {
            c.get(i)?.let { if (it !== EMPTY) return it }
            for (d in 1..4) {
                c.get(mod(i + d))?.let { if (it !== EMPTY) return it }
                c.get(mod(i - d))?.let { if (it !== EMPTY) return it }
            }
        }
        return full
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val e = enter
        studio?.let { bgPaint.alpha = (e * 255).toInt().coerceIn(0, 255); canvas.drawBitmap(it, 0f, 0f, bgPaint) }
        val bmp = pick() ?: return

        // where the frame sits in the studio
        val fw = spin.w.toFloat(); val fh = spin.h.toFloat()
        val s = min(w * 0.98f / fw, h * 0.94f / fh)
        val cx = w / 2f; val cy = h * 0.53f
        fitR.set(cx - fw * s / 2f, cy - fh * s / 2f, cx + fw * s / 2f, cy + fh * s / 2f)

        // where the same frame must sit so the car covers the home picture exactly
        val body = bodyInFrame; val hb = homeBody
        if (body != null && hb != null && body.width() > 0.01f) {
            val dw = hb.width() / body.width()
            val dh = dw * fh / fw
            val l = hb.centerX() - body.centerX() * dw
            val t = hb.centerY() - body.centerY() * dh
            homeR.set(l, t, l + dw, t + dh)
        } else homeR.set(fitR)

        dst.set(
            homeR.left + (fitR.left - homeR.left) * e, homeR.top + (fitR.top - homeR.top) * e,
            homeR.right + (fitR.right - homeR.right) * e, homeR.bottom + (fitR.bottom - homeR.bottom) * e,
        )
        if (zoom != 1f || panX != 0f || panY != 0f) {
            val zx = cx + panX; val zy = cy + panY
            dst.set(zx + (dst.left - cx) * zoom, zy + (dst.top - cy) * zoom, zx + (dst.right - cx) * zoom, zy + (dst.bottom - cy) * zoom)
        }
        canvas.drawBitmap(bmp, null, dst, bmpPaint)

        // nameplate + first-time hint
        val ta = (e * e * 0.55f)
        if (ta > 0.01f) {
            txt.textSize = h * 0.026f; txt.letterSpacing = 0.4f; txt.alpha = (ta * 255).toInt()
            canvas.drawText(label, w / 2f, h * 0.93f, txt)
        }
        if (hint != null && hintAlpha > 0.01f) {
            txt.textSize = h * 0.024f; txt.letterSpacing = 0.02f; txt.alpha = (hintAlpha * e * 0.8f * 255).toInt()
            canvas.drawText(hint, w / 2f, h * 0.09f, txt)
        }
    }

    /* ------------------------------------------------------------------ touch */

    private var dragging = false
    private var axis = 0            // 0 undecided, 1 turn, 2 tilt
    private var tiltAcc = 0f
    private val slop = android.view.ViewConfiguration.get(ctx).scaledTouchSlop

    private val scaler = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var lx = 0f; private var ly = 0f
        override fun onScaleBegin(d: ScaleGestureDetector): Boolean { lx = d.focusX; ly = d.focusY; axis = 3; return true }
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val nz = (zoom * d.scaleFactor).coerceIn(1f, 2.4f)
            val cx = width / 2f; val cy = height * 0.53f
            // keep the point under the fingers still while zooming, and let two fingers pan
            val f = nz / zoom
            panX = (d.focusX - cx) - (d.focusX - cx - panX) * f + (d.focusX - lx)
            panY = (d.focusY - cy) - (d.focusY - cy - panY) * f + (d.focusY - ly)
            lx = d.focusX; ly = d.focusY
            zoom = nz
            if (zoom <= 1.001f) { panX = 0f; panY = 0f }
            clampPan(); invalidate()
            return true
        }
        override fun onScaleEnd(d: ScaleGestureDetector) { if (zoom < 1.05f) animateZoomReset(); scheduleFull(0) }
    })

    private fun clampPan() {
        val mx = width * (zoom - 1f) * 0.5f; val my = height * (zoom - 1f) * 0.5f
        panX = panX.coerceIn(-mx, mx); panY = panY.coerceIn(-my, my)
    }

    private fun animateZoomReset() {
        val z0 = zoom; val px = panX; val py = panY
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 220
            addUpdateListener { val t = it.animatedValue as Float; zoom = 1f + (z0 - 1f) * t; panX = px * t; panY = py * t; invalidate() }
            start()
        }
    }

    private val gestures = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean { stopFling(); return true }
        override fun onDoubleTap(e: MotionEvent): Boolean { close(); return true }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (closing || scaler.isInProgress || e2.pointerCount > 1 || axis == 3) return true
            val sx = e2.x - (e1?.x ?: e2.x); val sy = e2.y - (e1?.y ?: e2.y)
            if (axis == 0) {
                if (abs(sx) < slop && abs(sy) < slop) return true
                axis = if (abs(sx) >= abs(sy) * 0.8f) 1 else 2
            }
            dragging = true
            if (axis == 1) setAngle(angle + dx * framesPerPx())
            else {
                tiltAcc -= dy
                val step = height * 0.11f
                while (tiltAcc > step) { tiltAcc -= step; setRow(row + 1) }
                while (tiltAcc < -step) { tiltAcc += step; setRow(row - 1) }
            }
            return true
        }
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (closing || axis != 1) return false
            startFling(-vx * framesPerPx())
            return true
        }
    })

    private fun framesPerPx() = n / (width.coerceAtLeast(1) * 1.25f) / zoom.coerceAtLeast(1f).let { 1f + (it - 1f) * 0.5f }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!opened) return true
        if (e.actionMasked == MotionEvent.ACTION_DOWN) { axis = 0; tiltAcc = 0f; dragging = false }
        scaler.onTouchEvent(e)
        gestures.onTouchEvent(e)
        if (e.actionMasked == MotionEvent.ACTION_UP || e.actionMasked == MotionEvent.ACTION_CANCEL) {
            dragging = false
            if (velocity == 0f) scheduleFull()
        }
        return true
    }

    /* ------------------------------------------------------------------ inertia */

    private var lastFrameNs = 0L
    private val flingFrame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (released || velocity == 0f) return
            val dt = if (lastFrameNs == 0L) 0.016f else ((now - lastFrameNs) / 1e9f).coerceIn(0.001f, 0.05f)
            lastFrameNs = now
            setAngle(angle + velocity * dt)
            velocity *= exp(-2.6f * dt)
            if (abs(velocity) < 1.2f) { velocity = 0f; angle = floor(angle + 0.5f); scheduleFull(0); return }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun startFling(v: Float) {
        velocity = v.coerceIn(-n * 1.6f, n * 1.6f)
        if (abs(velocity) < 2f) { velocity = 0f; scheduleFull(); return }
        lastFrameNs = 0L
        Choreographer.getInstance().removeFrameCallback(flingFrame)
        Choreographer.getInstance().postFrameCallback(flingFrame)
    }

    private fun stopFling() {
        if (velocity != 0f) { velocity = 0f; Choreographer.getInstance().removeFrameCallback(flingFrame) }
    }

    companion object {
        private val EMPTY: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ALPHA_8)

        /** Bounding box of the solid car body (ignores the soft floor shadow), as fractions of the picture. */
        fun bodyBox(b: Bitmap): RectF? = try {
            val w = b.width; val h = b.height
            val step = max(1, w / 320)
            val row = IntArray(w)
            var x0 = w; var y0 = h; var x1 = -1; var y1 = -1
            var y = 0
            while (y < h) {
                b.getPixels(row, 0, w, 0, y, w, 1)
                var x = 0
                while (x < w) {
                    if ((row[x] ushr 24) > 200) {
                        if (x < x0) x0 = x; if (x > x1) x1 = x
                        if (y < y0) y0 = y; if (y > y1) y1 = y
                    }
                    x += step
                }
                y += step
            }
            if (x1 < 0) null else RectF(x0 / w.toFloat(), y0 / h.toFloat(), (x1 + 1) / w.toFloat(), (y1 + 1) / h.toFloat())
        } catch (_: Throwable) { null }
    }
}
