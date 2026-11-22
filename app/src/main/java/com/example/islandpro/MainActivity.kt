package com.example.islandpro

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

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
        root.addView(makeButton("শুরু করুন") { sendToService(null, 0) })
        root.addView(makeButton("বন্ধ করুন") {
            stopService(Intent(this, IslandService::class.java))
        })

        root.addView(sectionTitle("টাইমার"))
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (m in listOf(1, 5, 10)) {
            quick.addView(
                makeButton("$m মি.") { sendToService(IslandService.ACTION_TIMER, m * 60) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        root.addView(quick)

        val custom = EditText(this).apply {
            hint = "মিনিট লিখুন"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        root.addView(custom)
        root.addView(makeButton("কাস্টম টাইমার শুরু") {
            val m = custom.text.toString().toIntOrNull()
            if (m == null || m <= 0) {
                Toast.makeText(this, "সঠিক মিনিট লিখুন", Toast.LENGTH_SHORT).show()
            } else {
                sendToService(IslandService.ACTION_TIMER, m * 60)
            }
        })

        root.addView(sectionTitle("স্টপওয়াচ"))
        root.addView(makeButton("স্টপওয়াচ শুরু") {
            sendToService(IslandService.ACTION_STOPWATCH, 0)
        })
        root.addView(makeButton("টাইমার/স্টপওয়াচ বন্ধ") {
            sendToService(IslandService.ACTION_CANCEL, 0)
        })

        root.addView(sectionTitle("পিলের সেটিংস"))
        addSlider(root, prefs, "চওড়া (dp)", "w", 60, 200, 120)
        addSlider(root, prefs, "উচ্চতা (dp)", "h", 20, 60, 34)
        addSlider(root, prefs, "উপর থেকে দূরত্ব (dp)", "top", 0, 60, 8)

        setContentView(ScrollView(this).apply { addView(root) })

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 1)
        }
    }

    private fun sectionTitle(t: String): TextView {
        return TextView(this).apply {
            text = t
            textSize = 20f
            setPadding(0, dp(24), 0, dp(4))
        }
    }

    private fun makeButton(text: String, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            setOnClickListener { onClick() }
        }
    }

    private fun sendToService(action: String?, sec: Int) {
        if (!Settings.canDrawOverlays(this)) {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (action == IslandService.ACTION_CANCEL && !IslandService.running) return
        val i = Intent(this, IslandService::class.java)
        if (action != null) {
            i.action = action
            i.putExtra("sec", sec)
        }
        startForegroundService(i)
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
