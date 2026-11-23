package com.example.islandpro

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.content.ClipData
import android.content.ClipboardManager
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
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {

    private val bgColor = Color.parseColor("#0E0F13")
    private val cardColor = Color.parseColor("#181A20")
    private val accent = Color.parseColor("#6A5CFF")
    private val green = Color.parseColor("#3DDC84")
    private val textMain = Color.WHITE
    private val textDim = Color.parseColor("#9AA0AE")

    private val swatchColors = intArrayOf(
        0xFF000000.toInt(),
        0xFF1C1C1E.toInt(),
        0xFF0B1B3A.toInt(),
        0xFF3A0B1B.toInt(),
        0xFF0B3A22.toInt(),
        0xFF25104A.toInt()
    )
    private val swatchViews = ArrayList<View>()

    private lateinit var prefs: SharedPreferences
    private lateinit var statusText: TextView
    private lateinit var permText: TextView
    private lateinit var musicStatus: TextView
    private lateinit var shizukuStatus: TextView
    private lateinit var usageStatus: TextView

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

        // Status
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

        // Notification access
        val accessCard = card()
        accessCard.addView(label("Notification access", 18f, textMain, true))
        musicStatus = label("", 14f, textDim, false).apply { setPadding(0, dp(4), 0, 0) }
        accessCard.addView(musicStatus)
        accessCard.addView(
            label(
                "Needed to show music, notifications, calls and alarms. If the switch is " +
                    "blocked, open App info, tap the three-dot menu and choose Allow " +
                    "restricted settings.",
                12f, textDim, false
            ).apply { setPadding(0, dp(6), 0, dp(10)) }
        )
        val accessRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        accessRow.addView(actionButton("Grant access", true) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }, buttonParams())
        accessRow.addView(actionButton("App info", false) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }, buttonParams())
        accessCard.addView(accessRow)
        accessCard.addView(
            actionButton("Island's own notification settings", false) {
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(10) }
        )
        root.addView(accessCard)

        // Behavior
        val behCard = card()
        behCard.addView(label("Behavior", 18f, textMain, true))
        behCard.addView(makeSwitch("Start automatically when the phone turns on", "autostart", true))
        behCard.addView(makeSwitch("Haptic feedback", "haptic", true) { restartIfRunning() })
        behCard.addView(
            makeSwitch("Hide Island in landscape (video and games)", "hide_land", true) {
                restartIfRunning()
            }
        )
        behCard.addView(
            makeSwitch("Match colours to album art and app icons", "match_colors", true) {
                restartIfRunning()
            }
        )
        behCard.addView(
            makeSwitch("Show brightness bar", "show_bright", false) { restartIfRunning() }
        )
        behCard.addView(
            actionButton("Choose apps for notifications", false) {
                chooseApps("blocked_apps", "Apps shown in Island", true)
            },
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(14) }
        )
        root.addView(behCard)

        // Hide in apps
        val hideCard = card()
        hideCard.addView(label("Hide Island in chosen apps", 18f, textMain, true))
        usageStatus = label("", 14f, textDim, false).apply { setPadding(0, dp(4), 0, 0) }
        hideCard.addView(usageStatus)
        hideCard.addView(
            label(
                "Pick apps (games, banking, video) where Island should stay hidden. " +
                    "This needs Usage access.",
                12f, textDim, false
            ).apply { setPadding(0, dp(6), 0, dp(10)) }
        )
        val hideRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hideRow.addView(actionButton("Usage access", true) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }, buttonParams())
        hideRow.addView(actionButton("Choose apps", false) {
            chooseApps("hide_apps", "Hide Island in these apps", false)
        }, buttonParams())
        hideCard.addView(hideRow)
        root.addView(hideCard)

        // Shizuku
        val shizukuCard = card()
        shizukuCard.addView(label("Shizuku", 18f, textMain, true))
        shizukuStatus = label("", 14f, textDim, false).apply { setPadding(0, dp(4), 0, 0) }
        shizukuCard.addView(shizukuStatus)
        shizukuCard.addView(
            label(
                "Install and start Shizuku first. It lets Island hide the phone's own " +
                    "pop-up banners and open the notification shade when you swipe down on " +
                    "the pill. Incoming call banners may also be hidden, so turn the " +
                    "switch off if you need them.",
                12f, textDim, false
            ).apply { setPadding(0, dp(6), 0, dp(10)) }
        )
        val shizukuRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        shizukuRow.addView(actionButton("Grant", true) {
            ShizukuHelper.requestPermission(100)
            refreshLater()
        }, buttonParams())
        shizukuRow.addView(actionButton("Open Shizuku", false) {
            val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (i != null) {
                startActivity(i)
            } else {
                Toast.makeText(this, "Shizuku app is not installed", Toast.LENGTH_SHORT).show()
            }
        }, buttonParams())
        shizukuCard.addView(shizukuRow)
        shizukuCard.addView(
            makeSwitch("Hide phone's pop-up banners", "hide_popups", true) { checked ->
                if (IslandService.running) {
                    val ctx = applicationContext
                    Thread { ShizukuHelper.setHeadsUp(ctx, !checked) }.start()
                }
            }
        )
        root.addView(shizukuCard)

        // Shade fallback
        val shadeCard = card()
        shadeCard.addView(label("Notification shade", 18f, textMain, true))
        shadeCard.addView(
            makeSwitch("Also remove shown notifications from the shade", "hide_sys", false)
        )
        shadeCard.addView(
            label(
                "Turn this on to keep notifications only in Island. Reply may not work in " +
                    "some apps once a notification is removed from the shade.",
                12f, textDim, false
            ).apply { setPadding(0, dp(6), 0, 0) }
        )
        root.addView(shadeCard)

        // Appearance
        val lookCard = card()
        lookCard.addView(label("Appearance", 18f, textMain, true))
        val sw = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(12), 0, dp(4))
        }
        for (i in swatchColors.indices) {
            val v = View(this)
            v.setOnClickListener {
                prefs.edit().putInt("pill_color", swatchColors[i]).apply()
                styleSwatches()
                restartIfRunning()
            }
            swatchViews.add(v)
            sw.addView(v, LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(10) })
        }
        lookCard.addView(sw)
        styleSwatches()
        addSlider(lookCard, "Opacity (%)", "pill_alpha", 40, 100, 100)
        root.addView(lookCard)

        // Size
        val sizeCard = card()
        sizeCard.addView(label("Size", 18f, textMain, true))
        addSlider(sizeCard, "Width", "w", 60, 220, 120)
        addSlider(sizeCard, "Height", "h", 20, 70, 34)
        root.addView(sizeCard)

        // Position
        val posCard = card()
        posCard.addView(label("Position", 18f, textMain, true))
        addSlider(posCard, "Distance from top", "top", 0, 80, 8)
        addSlider(posCard, "Left  <  0  >  Right", "x", -200, 200, 0)
        root.addView(posCard)

        // Backup
        val backCard = card()
        backCard.addView(label("Backup", 18f, textMain, true))
        backCard.addView(
            label(
                "Copy your settings, then paste them here after reinstalling.",
                12f, textDim, false
            ).apply { setPadding(0, dp(4), 0, dp(10)) }
        )
        val backRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        backRow.addView(actionButton("Copy settings", true) { exportSettings() }, buttonParams())
        backRow.addView(actionButton("Paste settings", false) { importSettings() }, buttonParams())
        backCard.addView(backRow)
        root.addView(backCard)

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

        val req = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) req.add("android.permission.POST_NOTIFICATIONS")
        if (Build.VERSION.SDK_INT >= 31) req.add("android.permission.BLUETOOTH_CONNECT")
        if (req.isNotEmpty()) requestPermissions(req.toTypedArray(), 1)
    }

    override fun onResume() {
        super.onResume()
        refresh()
        if (IslandService.running && !IslandService.mediaReady && listenerEnabled()) {
            startForegroundService(Intent(this, IslandService::class.java))
        }
        if (!IslandService.running && prefs.getBoolean("headsup_off", false)) {
            val ctx = applicationContext
            Thread { ShizukuHelper.setHeadsUp(ctx, true) }.start()
        }
    }

    private fun styleSwatches() {
        val cur = prefs.getInt("pill_color", swatchColors[0])
        for (i in swatchViews.indices) {
            val sel = swatchColors[i] == cur
            swatchViews[i].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(swatchColors[i])
                setStroke(dp(if (sel) 3 else 1), if (sel) accent else Color.parseColor("#555B66"))
            }
        }
    }

    // invert = true: the saved set is a block list (ticked = shown)
    // invert = false: the saved set is the ticked apps
    private fun chooseApps(key: String, title: String, invert: Boolean) {
        val pm = packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(main, 0)
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != packageName }
            .distinctBy { it.first }
            .sortedBy { it.second.lowercase() }
        val stored = HashSet<String>(prefs.getStringSet(key, null) ?: emptySet())
        val names = list.map { it.second }.toTypedArray()
        val checked = BooleanArray(list.size) {
            val inSet = stored.contains(list[it].first)
            if (invert) !inSet else inSet
        }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(title)
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton("Save") { _, _ ->
                val nb = HashSet<String>()
                for (i in list.indices) {
                    val inSet = if (invert) !checked[i] else checked[i]
                    if (inSet) nb.add(list[i].first)
                }
                prefs.edit().putStringSet(key, nb).apply()
                restartIfRunning()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun exportSettings() {
        val o = JSONObject()
        for ((k, v) in prefs.all) {
            if (k == "headsup_off" || v == null) continue
            if (v is Set<*>) {
                o.put(k, JSONArray(v.map { it.toString() }))
            } else {
                o.put(k, v)
            }
        }
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Island settings", o.toString()))
        Toast.makeText(this, "Settings copied", Toast.LENGTH_SHORT).show()
    }

    private fun importSettings() {
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString() ?: ""
            val o = JSONObject(text)
            val ed = prefs.edit()
            for (k in o.keys()) {
                when (val v = o.get(k)) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is String -> ed.putString(k, v)
                    is JSONArray -> {
                        val s = HashSet<String>()
                        for (i in 0 until v.length()) s.add(v.getString(i))
                        ed.putStringSet(k, s)
                    }
                }
            }
            ed.apply()
            restartIfRunning()
            Toast.makeText(this, "Settings applied", Toast.LENGTH_SHORT).show()
            recreate()
        } catch (e: Exception) {
            Toast.makeText(this, "No valid settings found in clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    private fun listenerEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
            ?: return false
        return flat.contains(packageName)
    }

    private fun hasUsageAccess(): Boolean {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
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
        musicStatus.text = if (listenerEnabled()) {
            "Notification access: Granted"
        } else {
            "Notification access: Not granted"
        }
        usageStatus.text = if (hasUsageAccess()) {
            "Usage access: Granted"
        } else {
            "Usage access: Not granted"
        }
        shizukuStatus.text = when {
            !ShizukuHelper.isRunning() -> "Shizuku: Not running"
            !ShizukuHelper.hasPermission() -> "Shizuku: Running, permission needed"
            else -> "Shizuku: Ready"
        }
    }

    private fun refreshLater() {
        statusText.postDelayed({ refresh() }, 800)
        statusText.postDelayed({ refresh() }, 2500)
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

    private fun makeSwitch(
        text: String,
        key: String,
        def: Boolean,
        onChange: (Boolean) -> Unit = {}
    ): Switch {
        return Switch(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(textMain)
            isChecked = prefs.getBoolean(key, def)
            setPadding(0, dp(10), 0, dp(4))
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean(key, checked).apply()
                onChange(checked)
            }
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
