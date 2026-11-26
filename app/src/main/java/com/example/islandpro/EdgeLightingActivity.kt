package com.example.islandpro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class EdgeLightingActivity : Activity() {

    private val defC1 = 0xFF3987FF.toInt()
    private val defC2 = 0xFF55D9FF.toInt()

    private val palette = intArrayOf(
        0xFF3987FF.toInt(),
        0xFF55D9FF.toInt(),
        0xFF8B5CF6.toInt(),
        0xFFFF4DA6.toInt(),
        0xFFFF453A.toInt(),
        0xFFFF9F0A.toInt(),
        0xFFFFD60A.toInt(),
        0xFF30D158.toInt(),
        0xFF64D2FF.toInt(),
        0xFFFFFFFF.toInt()
    )

    private class Preset(val name: String, val c1: Int, val c2: Int)

    private val presets = listOf(
        Preset("Ocean", 0xFF3987FF.toInt(), 0xFF55D9FF.toInt()),
        Preset("Sunset", 0xFFFF9F0A.toInt(), 0xFFFF4DA6.toInt()),
        Preset("Aurora", 0xFF30D158.toInt(), 0xFF64D2FF.toInt()),
        Preset("Neon", 0xFF8B5CF6.toInt(), 0xFFFF4DA6.toInt()),
        Preset("Lava", 0xFFFF453A.toInt(), 0xFFFFD60A.toInt()),
        Preset("Pearl", 0xFFFFFFFF.toInt(), 0xFF9AA0AE.toInt())
    )

    private lateinit var ui: OneUi
    private lateinit var prefs: SharedPreferences
    private val handler = Handler(Looper.getMainLooper())

    private val rows = HashMap<String, ArrayList<View>>()
    private val presetViews = ArrayList<View>()

    private var stage: FrameLayout? = null
    private var glow: GlowView? = null
    private var pill: View? = null
    private var resumed = false

    private fun dp(v: Int) = ui.dp(v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        ui = OneUi(this)

        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---------- Live preview ----------
        c.addView(buildPreview(), LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(180)
        ))

        // ---------- General ----------
        c.addView(ui.section("GENERAL"))
        val general = ui.group()
        ui.add(general, ui.switchRow(
            "Edge Lighting", "A soft glow around Island when something arrives",
            prefs.getBoolean("edge_on", true)
        ) { v ->
            prefs.edit().putBoolean("edge_on", v).apply()
            restartIfRunning()
        })
        ui.add(general, ui.switchRow(
            "Notifications", null, prefs.getBoolean("edge_notif", true)
        ) { v ->
            prefs.edit().putBoolean("edge_notif", v).apply()
            restartIfRunning()
        })
        ui.add(general, ui.switchRow(
            "Calls and alarms", null, prefs.getBoolean("edge_call", true)
        ) { v ->
            prefs.edit().putBoolean("edge_call", v).apply()
            restartIfRunning()
        })
        c.addView(general)

        // ---------- Style ----------
        c.addView(ui.section("EFFECT STYLE"))
        val styleG = ui.group()
        styleG.addView(ui.segmented(
            listOf("Aurora", "Comet", "Pulse"),
            prefs.getInt("edge_style", 0).coerceIn(0, 2)
        ) { i ->
            prefs.edit().putInt("edge_style", i).apply()
            applyPreview()
            restartIfRunning()
        })
        c.addView(styleG)
        c.addView(ui.hint(
            "Aurora: a flowing shimmer.  Comet: two glowing comets circle Island.  " +
                "Pulse: a calm breathing ring."
        ))

        // ---------- Colours ----------
        c.addView(ui.section("COLOURS"))
        val colours = ui.group()
        colours.addView(presetRow())
        ui.add(colours, label("Start colour"))
        colours.addView(swatchRow("edge_c1", defC1))
        ui.add(colours, label("End colour"))
        colours.addView(swatchRow("edge_c2", defC2))
        colours.addView(ui.buttonRow(
            ui.pill("Reset colours", false) {
                prefs.edit().remove("edge_c1").remove("edge_c2").apply()
                styleAll()
                applyPreview()
                restartIfRunning()
            }
        ))
        c.addView(colours)

        // ---------- Glow ----------
        c.addView(ui.section("GLOW"))
        val g = ui.group()
        ui.add(g, slider("Duration", "edge_ms10", 10, 60, 25) { "${it / 10}.${it % 10} s" })
        ui.add(g, slider("Line thickness", "edge_thick", 1, 8, 3) { "$it dp" })
        ui.add(g, slider("Glow size", "edge_glow", 4, 28, 12) { "$it dp" })
        c.addView(g)

        c.addView(ui.pill("Preview on Island", true) { previewOnIsland() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(22) })

        setContentView(ui.screen("Edge Lighting", "Make every alert glow", { finish() }, c))
        styleAll()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        applyPreview()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacksAndMessages(null)
        glow?.stopNow()
        super.onPause()
    }

    // ---------- Preview card ----------

    private fun buildPreview(): View {
        val st = FrameLayout(this)
        stage = st
        val gv = GlowView(this)
        glow = gv
        gv.visibility = View.INVISIBLE
        gv.onEnd = {
            if (resumed) {
                handler.postDelayed({
                    if (resumed) startPreview()
                }, 500)
            }
        }
        st.addView(gv, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val p = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.BLACK)
                cornerRadius = dp(18).toFloat()
            }
        }
        pill = p
        st.addView(p, FrameLayout.LayoutParams(dp(132), dp(36), Gravity.CENTER))

        return FrameLayout(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF2A3350.toInt(), 0xFF0E1018.toInt())
            ).apply { cornerRadius = dp(26).toFloat() }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, o: android.graphics.Outline) {
                    o.setRoundRect(0, 0, view.width, view.height, dp(26).toFloat())
                }
            }
            addView(st, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            ))
        }
    }

    private fun applyPreview() {
        val st = stage ?: return
        val gv = glow ?: return
        val c1 = prefs.getInt("edge_c1", defC1)
        val c2 = prefs.getInt("edge_c2", defC2)
        val thick = prefs.getInt("edge_thick", 3)
        val size = prefs.getInt("edge_glow", 12)
        val style = prefs.getInt("edge_style", 0)
        val pad = dp(size) + dp(thick) + dp(3)
        val lp = st.layoutParams
        lp.width = dp(132) + 2 * pad
        lp.height = dp(36) + 2 * pad
        st.layoutParams = lp
        gv.radius = dp(18).toFloat()
        gv.configure(c1, c2, dp(thick).toFloat(), dp(size).toFloat(), pad.toFloat(), style)
        if (resumed) {
            handler.removeCallbacksAndMessages(null)
            startPreview()
        }
    }

    private fun startPreview() {
        val gv = glow ?: return
        gv.start(prefs.getInt("edge_ms10", 25) * 100L)
    }

    private fun previewOnIsland() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Start Island from the home screen first", Toast.LENGTH_SHORT)
                .show()
            return
        }
        if (!prefs.getBoolean("edge_on", true)) {
            Toast.makeText(this, "Turn Edge Lighting on first", Toast.LENGTH_SHORT).show()
            return
        }
        val i = Intent(this, IslandService::class.java)
        i.action = IslandService.ACTION_PREVIEW
        startForegroundService(i)
    }

    private fun restartIfRunning() {
        if (IslandService.running) {
            startForegroundService(Intent(this, IslandService::class.java))
        }
    }

    // ---------- Builders ----------

    private fun label(t: String): TextView {
        return ui.tv(t, 14f, ui.sub, true).apply {
            setPadding(dp(20), dp(14), dp(20), 0)
        }
    }

    private fun slider(
        title: String,
        key: String,
        min: Int,
        max: Int,
        def: Int,
        fmt: (Int) -> String
    ): View {
        return ui.sliderRow(
            title, min, max, prefs.getInt(key, def), fmt,
            onLive = {
                prefs.edit().putInt(key, it).apply()
                applyPreview()
            },
            onCommit = {
                prefs.edit().putInt(key, it).apply()
                applyPreview()
                restartIfRunning()
            }
        )
    }

    private fun presetRow(): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(16), dp(16), dp(8))
        }
        for (p in presets) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), 0, dp(6), 0)
            }
            val dotView = View(this)
            presetViews.add(dotView)
            cell.addView(dotView, LinearLayout.LayoutParams(dp(46), dp(46)))
            cell.addView(ui.tv(p.name, 11.5f, ui.sub).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) })
            cell.setOnClickListener {
                prefs.edit().putInt("edge_c1", p.c1).putInt("edge_c2", p.c2).apply()
                styleAll()
                applyPreview()
                restartIfRunning()
            }
            row.addView(cell)
        }
        return ui.hscroll(row)
    }

    private fun swatchRow(key: String, def: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(16), dp(10), dp(16), dp(12))
        }
        val views = ArrayList<View>()
        for (col in palette) {
            val v = View(this)
            v.setOnClickListener {
                prefs.edit().putInt(key, col).apply()
                styleAll()
                applyPreview()
                restartIfRunning()
            }
            views.add(v)
            row.addView(v, LinearLayout.LayoutParams(dp(36), dp(36)).apply {
                marginStart = dp(4)
                marginEnd = dp(4)
            })
        }
        rows[key] = views
        return ui.hscroll(row)
    }

    private fun styleAll() {
        styleRow("edge_c1", defC1)
        styleRow("edge_c2", defC2)
        val c1 = prefs.getInt("edge_c1", defC1)
        val c2 = prefs.getInt("edge_c2", defC2)
        for (i in presetViews.indices) {
            val p = presets[i]
            val sel = p.c1 == c1 && p.c2 == c2
            presetViews[i].background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR, intArrayOf(p.c1, p.c2)
            ).apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(if (sel) 3 else 1), if (sel) ui.accent else ui.line)
            }
        }
    }

    private fun styleRow(key: String, def: Int) {
        val cur = prefs.getInt(key, def)
        val views = rows[key] ?: return
        for (i in views.indices) {
            val sel = palette[i] == cur
            views[i].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette[i])
                setStroke(dp(if (sel) 3 else 1), if (sel) ui.accent else ui.line)
            }
        }
    }
}
