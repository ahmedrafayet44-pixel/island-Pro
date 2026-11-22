package com.example.islandpro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private val bgColor = Color.parseColor("#0E0F13")
    private val cardColor = Color.parseColor("#181A20")
    private val accent = Color.parseColor("#6A5CFF")
    private val green = Color.parseColor("#3DDC84")
    private val textMain = Color.WHITE
    private val textDim = Color.parseColor("#9AA0AE")

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var permText: TextView

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

        root.addView(label("Island", 32f, textMain, true))
        root.addView(label("Dynamic Island for your Samsung phone", 14f, textDim, false).apply {
            setPadding(0, dp(2), 0, dp(18))
        })

        // Status card
        val statusCard = card()
        statusText = label("", 18f, textMain, true)
        permText = label("", 14f, textDim, false).apply { setPadding(0, dp(4), 0, 0) }
        statusCard.addView(statusText)
        statusCard.addView(permText)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(14), 0, 0)
        }
        row.addView(actionButton("Start", true) { startIsland() }, buttonParams())
        row.addView(actionButton("Stop", false) {
            stopService(Intent(this, IslandService::class.java))
            refreshLater()
        }, buttonParams())
        statusCard.addView(row)
        root.addView(statusCard)

        // Size card
        val sizeCard = card()
        sizeCard.addView(label("Size", 18f, textMain, true))
        addSlider(sizeCard, "Width", "w", 60, 220, 120)
        addSlider(sizeCard, "Height", "h", 20, 70, 34)
        root.addView(sizeCard)

        // Position card
        val posCard = card()
        posCard.addView(label("Position", 18f, textMain, true))
        addSlider(posCard, "Distance from top", "top", 0, 80, 8)
        addSlider(posCard, "Left  <  0  >  Right", "x", -200, 200, 0)
        root.addView(posCard)

        // Reset
        root.addView(actionButton("Reset to defaults", false) {
            prefs.edit().clear().apply()
            restartIfRunning()
            recreate()
        }, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
        ))

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        root.addView(label("Version $ver", 12f, textDim, false).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
        })

        setContentView(ScrollView(this).apply {
            setBackgroundColor(bgColor)
            addView(root)
        })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (IslandService.running) {
            statusText.text = "● Running"
            statusText.setTextColor(green)
        } else {
            statusText.text = "○ Stopped"
            statusText.setTextColor(textDim)
        }
        permText.text = if (Settings.canDrawOverlays(this)) {
            "Overlay permission: Granted"
        } else {
            "Overlay permission: Not granted (tap Start to grant)"
        }
    }

    private fun refreshLater() {
        statusText.postDelayed({ refresh() }, 500)
    }

    private fun startIsland() {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        } else {
            startForegroundService(Intent(this, IslandService::class.java))
            refreshLater()
        }
    }

    private fun restartIfRunning() {
        if (IslandService.running) {
            startForegroundService(Intent(this, IslandService::class.java))
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

    private fun buttonParams(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(0, dp(52), 1f).apply {
            leftMargin = dp(4)
            rightMargin = dp(4)
        }
    }

    private fun actionButton(text: String, filled: Boolean, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setAllCaps(false)
            textSize = 16f
            setTextColor(if (filled) Color.WHITE else textMain)
            background = GradientDrawable().apply {
                setColor(if (filled) accent else Color.parseColor("#262932"))
                cornerRadius = dp(18).toFloat()
            }
            stateListAnimator = null
            setOnClickListener { onClick() }
        }
    }

    private fun addSlider(
        parent: LinearLayout,
        title: String,
        key: String,
        min: Int,
        max: Int,
        def: Int
    ) {
        val cur = prefs.getInt(key, def)
        val valueLabel = label("", 14f, textDim, false).apply {
            setPadding(0, dp(12), 0, dp(2))
        }
        valueLabel.text = "$title: $cur"

        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = cur - min
            progressTintList = ColorStateList.valueOf(accent)
            thumbTintList = ColorStateList.valueOf(accent)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    valueLabel.text = "$title: ${p + min}"
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
