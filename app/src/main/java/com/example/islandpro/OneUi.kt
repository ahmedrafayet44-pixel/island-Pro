package com.example.islandpro

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** A small chevron "<" or ">" drawn with a path, so no icon files are needed. */
internal class ChevronView(c: Context, private val left: Boolean, col: Int) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = col
        strokeWidth = 2.2f * c.resources.displayMetrics.density
    }
    private val path = Path()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w, h) * 0.26f
        val cx = w / 2f
        val cy = h / 2f
        path.reset()
        if (left) {
            path.moveTo(cx + s * 0.5f, cy - s)
            path.lineTo(cx - s * 0.5f, cy)
            path.lineTo(cx + s * 0.5f, cy + s)
        } else {
            path.moveTo(cx - s * 0.5f, cy - s)
            path.lineTo(cx + s * 0.5f, cy)
            path.lineTo(cx - s * 0.5f, cy + s)
        }
        canvas.drawPath(path, p)
    }
}

internal class RowRef(val root: LinearLayout, val title: TextView, val summary: TextView?)

/**
 * Builds a One UI looking screen (big collapsing title, rounded grouped cards,
 * pill buttons, accent switches). Works in light and dark mode.
 */
internal class OneUi(private val a: Activity) {

    val dark = (a.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

    val bg = if (dark) 0xFF000000.toInt() else 0xFFF6F6F6.toInt()
    val card = if (dark) 0xFF1C1C1E.toInt() else 0xFFFFFFFF.toInt()
    val text = if (dark) 0xFFF2F2F7.toInt() else 0xFF111111.toInt()
    val sub = if (dark) 0xFF9A9AA2.toInt() else 0xFF6E6E73.toInt()
    val line = if (dark) 0xFF2E2E31.toInt() else 0xFFEBEBEF.toInt()
    val accent = 0xFF3E91FF.toInt()
    val tonal = if (dark) 0xFF262B36.toInt() else 0xFFE6EFFE.toInt()
    val tonalText = if (dark) 0xFF8DBBFF.toInt() else 0xFF1F6FE0.toInt()
    val ok = 0xFF2FB863.toInt()
    val warn = 0xFFF59E0B.toInt()
    val bad = 0xFFFF453A.toInt()
    private val ripple = if (dark) 0x22FFFFFF else 0x14000000

    fun dp(v: Int): Int = (v * a.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(v: Float): Float = v * a.resources.displayMetrics.density

    fun applyWindow() {
        a.window.statusBarColor = bg
        a.window.navigationBarColor = bg
        if (!dark) {
            @Suppress("DEPRECATION")
            var f = a.window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            if (Build.VERSION.SDK_INT >= 26) f = f or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            @Suppress("DEPRECATION")
            a.window.decorView.systemUiVisibility = f
        }
    }

    // ---------- Basic pieces ----------

    fun tv(t: String, size: Float, color: Int, medium: Boolean = false): TextView {
        return TextView(a).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            includeFontPadding = false
        }
    }

    private fun roundClip(v: View, r: Float) {
        v.clipToOutline = true
        v.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, o: Outline) {
                o.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
    }

    fun rippleBg(content: android.graphics.drawable.Drawable? = null): RippleDrawable {
        return RippleDrawable(
            ColorStateList.valueOf(ripple),
            content,
            ColorDrawable(Color.WHITE)
        )
    }

    // ---------- Screen with collapsing One UI header ----------

    fun screen(
        title: String,
        subtitle: String,
        onBack: (() -> Unit)?,
        content: LinearLayout
    ): View {
        applyWindow()
        val headerH = dp(200)
        val barH = dp(56)

        val big = tv(title, 36f, text, true)
        val subT = tv(subtitle, 14f, sub)
        val header = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.BOTTOM
            setPadding(dp(26), 0, dp(26), dp(22))
            addView(big)
            addView(subT, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) })
        }

        content.setPadding(dp(16), 0, dp(16), dp(40))
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(header, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, headerH))
            addView(content, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }

        val sv = ScrollView(a).apply {
            isVerticalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isFillViewport = true
            addView(col)
        }

        // Compact top bar that fades in when the big title scrolls away
        val barBg = ColorDrawable(bg).apply { alpha = 0 }
        val smallTitle = tv(title, 18f, text, true).apply { alpha = 0f }
        val hair = View(a).apply {
            setBackgroundColor(line)
            alpha = 0f
        }
        val bar = FrameLayout(a).apply {
            background = barBg
            addView(smallTitle, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER_VERTICAL or Gravity.START
            ).apply { marginStart = if (onBack != null) dp(60) else dp(26) })
            if (onBack != null) {
                val back = FrameLayout(a).apply {
                    background = rippleBg().also { }
                    roundClip(this, dpf(24f))
                    setOnClickListener { onBack() }
                    addView(ChevronView(a, true, text), FrameLayout.LayoutParams(dp(48), dp(48)))
                }
                addView(back, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER_VERTICAL)
                    .apply { marginStart = dp(8) })
            }
            addView(hair, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1, Gravity.BOTTOM
            ))
        }

        val range = (headerH - barH - dp(24)).toFloat()
        sv.setOnScrollChangeListener { _, _, y, _, _ ->
            val p = (y / range).coerceIn(0f, 1f)
            big.alpha = 1f - p
            subT.alpha = 1f - p
            smallTitle.alpha = p
            hair.alpha = p
            barBg.alpha = (255 * p).toInt()
        }

        return FrameLayout(a).apply {
            setBackgroundColor(bg)
            addView(sv, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
            addView(bar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, barH))
        }
    }

    // ---------- Groups and rows ----------

    fun section(t: String): TextView {
        return tv(t, 13f, sub, true).apply {
            setPadding(dp(14), dp(22), dp(14), dp(8))
        }
    }

    fun hint(t: String): TextView {
        return tv(t, 12.5f, sub).apply {
            setLineSpacing(0f, 1.15f)
            setPadding(dp(14), dp(8), dp(14), 0)
        }
    }

    fun group(): LinearLayout {
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(card)
                cornerRadius = dpf(26f)
            }
            roundClip(this, dpf(26f))
        }
    }

    fun add(g: LinearLayout, row: View) {
        if (g.childCount > 0) {
            g.addView(View(a).apply { setBackgroundColor(line) }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1
            ).apply {
                marginStart = dp(20)
                marginEnd = dp(20)
            })
        }
        g.addView(row, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
    }

    private fun rowBase(): LinearLayout {
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(62)
            setPadding(dp(20), dp(12), dp(16), dp(12))
        }
    }

    private fun textBlock(title: String, summary: String?): Triple<LinearLayout, TextView, TextView?> {
        val t = tv(title, 16f, text)
        val s = if (summary != null) tv(summary, 13f, sub).apply {
            setLineSpacing(0f, 1.1f)
        } else null
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            addView(t)
            if (s != null) addView(s, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(3) })
        }
        return Triple(box, t, s)
    }

    fun navRow(title: String, summary: String?, onClick: () -> Unit): RowRef {
        val r = rowBase()
        val (box, t, s) = textBlock(title, summary)
        r.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(ChevronView(a, false, sub), LinearLayout.LayoutParams(dp(20), dp(24)))
        r.background = rippleBg()
        r.isClickable = true
        r.setOnClickListener { onClick() }
        return RowRef(r, t, s)
    }

    fun setStatus(row: RowRef, good: Boolean, label: String) {
        row.summary?.text = label
        row.summary?.setTextColor(if (good) ok else warn)
    }

    fun styleSwitch(s: Switch) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        s.thumbTintList = ColorStateList(states, intArrayOf(Color.WHITE, if (dark) 0xFFCFCFD4.toInt() else Color.WHITE))
        s.trackTintList = ColorStateList(
            states,
            intArrayOf(accent, if (dark) 0xFF48484D.toInt() else 0xFFCDCDD3.toInt())
        )
    }

    fun switchRow(
        title: String,
        summary: String?,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): LinearLayout {
        val r = rowBase()
        val (box, _, _) = textBlock(title, summary)
        val sw = Switch(a).apply {
            isChecked = checked
            styleSwitch(this)
            setOnCheckedChangeListener { _, c -> onChange(c) }
        }
        r.addView(box, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(12)
        })
        r.addView(sw)
        r.background = rippleBg()
        r.isClickable = true
        r.setOnClickListener { sw.toggle() }
        return r
    }

    fun sliderRow(
        title: String,
        min: Int,
        max: Int,
        cur: Int,
        fmt: (Int) -> String,
        onLive: (Int) -> Unit,
        onCommit: (Int) -> Unit
    ): LinearLayout {
        val head = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        val value = tv(fmt(cur), 14f, tonalText, true)
        head.addView(tv(title, 16f, text), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(value)
        val bar = SeekBar(a).apply {
            this.max = max - min
            progress = cur - min
            progressTintList = ColorStateList.valueOf(accent)
            progressBackgroundTintList = ColorStateList.valueOf(line)
            thumbTintList = ColorStateList.valueOf(accent)
            setPadding(dp(8), dp(4), dp(8), dp(4))
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    value.text = fmt(p + min)
                    if (fromUser) onLive(p + min)
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}

                override fun onStopTrackingTouch(s: SeekBar?) {
                    onCommit((s?.progress ?: 0) + min)
                }
            })
        }
        return LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(10))
            addView(head)
            addView(bar, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                marginStart = -dp(8)
                marginEnd = -dp(8)
            })
        }
    }

    // ---------- Buttons ----------

    fun pill(label: String, filled: Boolean, danger: Boolean = false, onClick: () -> Unit): TextView {
        val fill = when {
            filled -> accent
            danger -> if (dark) 0xFF3A1F1F.toInt() else 0xFFFFE8E6.toInt()
            else -> tonal
        }
        val fg = when {
            filled -> Color.WHITE
            danger -> bad
            else -> tonalText
        }
        return tv(label, 15f, fg, true).apply {
            gravity = Gravity.CENTER
            minHeight = dp(48)
            setPadding(dp(14), 0, dp(14), 0)
            background = rippleBg(GradientDrawable().apply {
                setColor(fill)
                cornerRadius = dpf(24f)
            })
            roundClip(this, dpf(24f))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    fun buttonRow(vararg buttons: TextView): LinearLayout {
        return LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(12), dp(16), dp(16))
            for ((i, b) in buttons.withIndex()) {
                addView(b, LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    if (i > 0) marginStart = dp(10)
                })
            }
        }
    }

    // ---------- Segmented control ----------

    fun segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit): LinearLayout {
        val wrap = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            background = GradientDrawable().apply {
                setColor(if (dark) 0xFF2A2A2D.toInt() else 0xFFF0F0F4.toInt())
                cornerRadius = dpf(22f)
            }
        }
        val cells = ArrayList<TextView>()
        fun paint(sel: Int) {
            for ((i, c) in cells.withIndex()) {
                val on = i == sel
                c.setTextColor(if (on) Color.WHITE else sub)
                c.background = if (on) GradientDrawable().apply {
                    setColor(accent)
                    cornerRadius = dpf(18f)
                } else null
            }
        }
        for ((i, o) in options.withIndex()) {
            val c = tv(o, 14f, sub, true).apply {
                gravity = Gravity.CENTER
                setOnClickListener {
                    paint(i)
                    onSelect(i)
                }
            }
            cells.add(c)
            wrap.addView(c, LinearLayout.LayoutParams(0, dp(38), 1f))
        }
        paint(selected)
        return LinearLayout(a).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            addView(wrap, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
    }

    // ---------- Colour dots ----------

    fun dot(size: Int, fill: GradientDrawable, selected: Boolean, ring: Int): View {
        return View(a).apply {
            background = fill.apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(if (selected) 3 else 1), if (selected) ring else line)
            }
        }
    }

    fun hscroll(child: LinearLayout): HorizontalScrollView {
        return HorizontalScrollView(a).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(child)
        }
    }
}
