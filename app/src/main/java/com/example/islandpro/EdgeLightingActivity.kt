package com.example.islandpro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class EdgeLightingActivity : Activity() {

    private val bgColor = Color.parseColor("#0E0F13")
    private val cardColor = Color.parseColor("#181A20")
    private val accent = Color.parseColor("#6A5CFF")
    private val textMain = Color.WHITE
    private val textDim = Color.parseColor("#9AA0AE")

    private val defC1 = 0xFF3987FF.toInt()
    private val defC2 = 0xFF55D9FF.toInt()
    private val palette = intArrayOf(
        0xFF3987FF.toInt(),
        0xFF55D9FF.toInt(),
        0xFF8B5CF6.toInt(),
        0xFFFF4DA6.toInt(),
        0xFFFF9F0A.toInt(),
        0xFF30D158.toInt(),
        0xFFFFD60A.toInt(),
        0xFFFFFFFF.toInt()
    )

    private lateinit var prefs: SharedPreferences
    private val rows = HashMap<String, ArrayList<View>>()
    private var previewBar: View? = null

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        window.statusBarColor = bgColor
        window.navigationBarColor = bgColor

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(36), dp(18), dp(28))
            setBackgroundColor(bgColor)
        }

        root.addView(label("Edge Lighting", 30f, textMain, true))
        root.addView(
            label(
                "A soft glow around Island when something arrives. " +
                    "The screen border never lights up.",
                13f, textDim, false
            ).apply { setPadding(0, dp(4), 0, dp(18)) }
        )

        // General
        val general = card()
        general.addView(label("General", 18f, textMain, true))
        general.addView(makeSwitch("Edge Lighting", "edge_on", true))
        general.addView(makeSwitch("Light up for notifications", "edge_notif", true))
        general.addView(makeSwitch("Light up for calls and alarms", "edge_call", true))
        root.addView(general)

        // Colours
        val colours = card()
        colours.addView(label("Colours", 18f, textMain, true))
        colours.addView(label("Start colour", 13f, textDim, false).apply {
            setPadding(0, dp(10), 0, 0)
        })
        colours.addView(swatchRow("edge_c1", defC1))
        colours.addView(label("End colour", 13f, textDim, false).apply {
            setPadding(0, dp(6), 0, 0)
        })
        colours.addView(swatchRow("edge_c2", defC2))
        val bar = View(this)
        previewBar = bar
        colours.addView(bar, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(14)
        ).apply { topMargin = dp(12) })
        updateBar()
        colours.addView(
            actionButton("Reset to Electric Blue + Soft Cyan", false) {
                prefs.edit().remove("edge_c1").remove("edge_c2").apply()
                styleRow("edge_c1", defC1)
                styleRow("edge_c2", defC2)
                updateBar()
                restartIfRunning()
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)
            ).apply { topMargin = dp(12) }
        )
        root.addView(colours)

        // Glow
        val glow = card()
        glow.addView(label("Glow", 18f, textMain, true))
        addSlider(glow, "Duration", "edge_ms10", 10, 60, 25) {
            "${it / 10}.${it % 10} s"
        }
        addSlider(glow, "Line thickness", "edge_thick", 1, 8, 3) { "$it dp" }
        addSlider(glow, "Glow size", "edge_glow", 4, 28, 12) { "$it dp" }
        root.addView(glow)

        root.addView(
            actionButton("Preview", true) { preview() },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(54)
            )
        )

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(root)
        })
    }

    private fun preview() {
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

    private fun updateBar() {
        val c1 = prefs.getInt("edge_c1", defC1)
        val c2 = prefs.getInt("edge_c2", defC2)
        previewBar?.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(c1, c2)
        ).apply { cornerRadius = dp(8).toFloat() }
    }

    private fun swatchRow(key: String, def: Int): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(8), 0, dp(4))
        }
        val views = ArrayList<View>()
        for (c in palette) {
            val v = View(this)
            v.setOnClickListener {
                prefs.edit().putInt(key, c).apply()
                styleRow(key, def)
                updateBar()
                restartIfRunning()
            }
            views.add(v)
            row.addView(v, LinearLayout.LayoutParams(dp(30), dp(30)).apply {
                rightMargin = dp(8)
            })
        }
        rows[key] = views
        styleRow(key, def)
        return row
    }

    private fun styleRow(key: String, def: Int) {
        val cur = prefs.getInt(key, def)
        val views = rows[key] ?: return
        for (i in views.indices) {
            val sel = palette[i] == cur
            views[i].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(palette[i])
                setStroke(
                    dp(if (sel) 3 else 1),
                    if (sel) Color.WHITE else Color.parseColor("#555B66")
                )
            }
        }
    }

    private fun label(t: String, size: Float, color: Int, bold: Boolean): TextView {
        return TextView(this).apply {
            text = t
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = GradientDrawable().apply {
                setColor(cardColor)
                cornerRadius = dp(24).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
    }

    private fun actionButton(text: String, filled: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setAllCaps(false)
            textSize = 15f
            setTextColor(if (filled) Color.WHITE else textMain)
            background = GradientDrawable().apply {
                setColor(if (filled) accent else Color.parseColor("#262932"))
                cornerRadius = dp(18).toFloat()
            }
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun makeSwitch(text: String, key: String, def: Boolean): Switch {
        return Switch(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(textMain)
            isChecked = prefs.getBoolean(key, def)
            setPadding(0, dp(10), 0, dp(4))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                restartIfRunning()
            }
        }
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        key: String,
        min: Int,
        max: Int,
        def: Int,
        fmt: (Int) -> String
    ) {
        val cur = prefs.getInt(key, def)
        val valueLabel = label("", 14f, textDim, false).apply {
            setPadding(0, dp(12), 0, dp(2))
        }
        valueLabel.text = "$title: ${fmt(cur)}"
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = cur - min
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    valueLabel.text = "$title: ${fmt(p + min)}"
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}

                override fun onStopTrackingTouch(s: SeekBar?) {
                    prefs.edit().putInt(key, (s?.progress ?: 0) + min).apply()
                    restartIfRunning()
                }
            })
        }
        parent.addView(valueLabel)
        parent.addView(bar)
    }
}
