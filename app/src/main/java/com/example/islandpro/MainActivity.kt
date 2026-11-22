package com.example.islandpro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

class MainActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(40), dp(20), dp(20))
        }
        root.addView(TextView(this).apply {
            text = "Island"
            textSize = 28f
        })
        root.addView(makeButton("শুরু করুন") { startIsland() })
        root.addView(makeButton("বন্ধ করুন") {
            stopService(Intent(this, IslandService::class.java))
        })

        addSlider(root, prefs, "চওড়া (dp)", "w", 60, 200, 120)
        addSlider(root, prefs, "উচ্চতা (dp)", "h", 20, 60, 34)
        addSlider(root, prefs, "উপর থেকে দূরত্ব (dp)", "top", 0, 60, 8)

        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
    }

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
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
        }
    }

    private fun addSlider(
        root: LinearLayout,
        prefs: SharedPreferences,
        title: String,
        key: String,
        min: Int,
        max: Int,
        def: Int
    ) {
        val cur = prefs.getInt(key, def)
        val label = TextView(this).apply {
            text = "$title: $cur"
            setPadding(0, dp(20), 0, 0)
        }
        val bar = SeekBar(this).apply {
            this.max = max - min
            progress = cur - min
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    label.text = "$title: ${p + min}"
                }

                override fun onStartTrackingTouch(s: SeekBar?) {}

                override fun onStopTrackingTouch(s: SeekBar?) {
                    prefs.edit().putInt(key, (s?.progress ?: 0) + min).apply()
                    if (IslandService.running) {
                        startForegroundService(Intent(this@MainActivity, IslandService::class.java))
                    }
                }
            })
        }
        root.addView(label)
        root.addView(bar)
    }
}
