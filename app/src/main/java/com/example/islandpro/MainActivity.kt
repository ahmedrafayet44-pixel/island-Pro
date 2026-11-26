package com.example.islandpro

import android.app.Activity
import android.app.AlertDialog
import android.app.AppOpsManager
import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

class MainActivity : Activity() {

    private val swatchColors = intArrayOf(
        0xFF000000.toInt(),
        0xFF1C1C1E.toInt(),
        0xFF0B1B3A.toInt(),
        0xFF3A0B1B.toInt(),
        0xFF0B3A22.toInt(),
        0xFF25104A.toInt()
    )
    private val swatchViews = ArrayList<View>()

    private lateinit var ui: OneUi
    private lateinit var prefs: SharedPreferences

    private lateinit var heroDot: View
    private lateinit var heroTitle: TextView
    private lateinit var heroSub: TextView
    private lateinit var overlayRow: RowRef
    private lateinit var listenerRow: RowRef
    private lateinit var usageRow: RowRef
    private lateinit var shizukuRow: RowRef
    private lateinit var edgeRow: RowRef

    private fun dp(v: Int) = ui.dp(v)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        ui = OneUi(this)

        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---------- Status ----------
        val hero = ui.group()
        val heroTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(4))
        }
        heroDot = View(this)
        heroTitle = ui.tv("", 20f, ui.text, true)
        heroSub = ui.tv("", 13f, ui.sub)
        val heroText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(heroTitle)
            addView(heroSub, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) })
        }
        heroTop.addView(heroDot, LinearLayout.LayoutParams(dp(14), dp(14)).apply {
            marginEnd = dp(14)
        })
        heroTop.addView(heroText)
        hero.addView(heroTop)
        hero.addView(ui.buttonRow(
            ui.pill("Start", true) { startIsland() },
            ui.pill("Stop", false) {
                stopService(Intent(this, IslandService::class.java))
                refreshLater()
            }
        ))
        c.addView(hero)

        // ---------- Permissions ----------
        c.addView(ui.section("PERMISSIONS"))
        val perms = ui.group()
        overlayRow = ui.navRow("Display over other apps", "") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        listenerRow = ui.navRow("Notification access", "") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
        usageRow = ui.navRow("Usage access", "") {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        shizukuRow = ui.navRow("Shizuku", "") {
            val i = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (i != null) {
                startActivity(i)
            } else {
                Toast.makeText(this, "Shizuku app is not installed", Toast.LENGTH_SHORT).show()
            }
        }
        ui.add(perms, overlayRow.root)
        ui.add(perms, listenerRow.root)
        ui.add(perms, usageRow.root)
        ui.add(perms, shizukuRow.root)
        ui.add(perms, ui.navRow("Grant Shizuku permission", "Tap after starting Shizuku") {
            ShizukuHelper.requestPermission(100)
            refreshLater()
        }.root)
        ui.add(perms, ui.navRow(
            "App info",
            "If a switch is blocked: ⋮ menu, then Allow restricted settings"
        ) {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:$packageName")
                )
            )
        }.root)
        ui.add(perms, ui.navRow("Island's own notification settings", null) {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
            )
        }.root)
        c.addView(perms)

        // ---------- Notifications ----------
        c.addView(ui.section("NOTIFICATIONS"))
        val notif = ui.group()
        ui.add(notif, ui.navRow("Apps shown in Island", "Choose which apps can appear") {
            chooseApps("blocked_apps", "Apps shown in Island", true)
        }.root)
        ui.add(notif, ui.switchRow(
            "Clear when I open the app",
            "Opening an app removes its notifications from Island. Needs Usage access.",
            prefs.getBoolean("clear_on_open", true)
        ) { v ->
            prefs.edit().putBoolean("clear_on_open", v).apply()
            restartIfRunning()
        })
        ui.add(notif, ui.switchRow(
            "Remove from the shade too",
            "Keep notifications only in Island. Reply may not work in some apps.",
            prefs.getBoolean("hide_sys", false)
        ) { v -> prefs.edit().putBoolean("hide_sys", v).apply() })
        c.addView(notif)

        // ---------- Edge lighting ----------
        c.addView(ui.section("EDGE LIGHTING"))
        val edge = ui.group()
        edgeRow = ui.navRow("Edge Lighting", "") {
            startActivity(Intent(this, EdgeLightingActivity::class.java))
        }
        ui.add(edge, edgeRow.root)
        c.addView(edge)

        // ---------- Behavior ----------
        c.addView(ui.section("BEHAVIOR"))
        val beh = ui.group()
        ui.add(beh, ui.switchRow(
            "Start on boot", "Start Island when the phone turns on",
            prefs.getBoolean("autostart", true)
        ) { v -> prefs.edit().putBoolean("autostart", v).apply() })
        ui.add(beh, ui.switchRow(
            "Haptic feedback", null, prefs.getBoolean("haptic", true)
        ) { v ->
            prefs.edit().putBoolean("haptic", v).apply()
            restartIfRunning()
        })
        ui.add(beh, ui.switchRow(
            "Hide in landscape", "For video and games",
            prefs.getBoolean("hide_land", true)
        ) { v ->
            prefs.edit().putBoolean("hide_land", v).apply()
            restartIfRunning()
        })
        ui.add(beh, ui.switchRow(
            "Dynamic colours", "Match album art and app icons",
            prefs.getBoolean("match_colors", true)
        ) { v ->
            prefs.edit().putBoolean("match_colors", v).apply()
            restartIfRunning()
        })
        ui.add(beh, ui.switchRow(
            "Brightness bar", null, prefs.getBoolean("show_bright", false)
        ) { v ->
            prefs.edit().putBoolean("show_bright", v).apply()
            restartIfRunning()
        })
        ui.add(beh, ui.switchRow(
            "Hide phone's pop-up banners",
            "Needs Shizuku. Incoming call banners may also be hidden.",
            prefs.getBoolean("hide_popups", true)
        ) { checked ->
            prefs.edit().putBoolean("hide_popups", checked).apply()
            if (IslandService.running) {
                val ctx = applicationContext
                Thread { ShizukuHelper.setHeadsUp(ctx, !checked) }.start()
            }
        })
        ui.add(beh, ui.navRow("Hide Island in chosen apps", "Games, banking, video. Needs Usage access.") {
            chooseApps("hide_apps", "Hide Island in these apps", false)
        }.root)
        c.addView(beh)

        // ---------- Appearance ----------
        c.addView(ui.section("APPEARANCE"))
        val look = ui.group()
        val sw = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(18), dp(20), dp(8))
        }
        sw.addView(ui.tv("Island colour", 16f, ui.text), LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        val dots = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (i in swatchColors.indices) {
            val v = View(this)
            v.setOnClickListener {
                prefs.edit().putInt("pill_color", swatchColors[i]).apply()
                styleSwatches()
                restartIfRunning()
            }
            swatchViews.add(v)
            dots.addView(v, LinearLayout.LayoutParams(dp(30), dp(30)).apply { marginStart = dp(8) })
        }
        sw.addView(dots)
        look.addView(sw)
        styleSwatches()
        look.addView(slider("Opacity", "pill_alpha", 40, 100, 100) { "$it%" })
        c.addView(look)

        // ---------- Size and position ----------
        c.addView(ui.section("SIZE AND POSITION"))
        val size = ui.group()
        ui.add(size, slider("Width", "w", 60, 220, 120))
        ui.add(size, slider("Height", "h", 20, 70, 34))
        ui.add(size, slider("Distance from top", "top", 0, 80, 8))
        ui.add(size, slider("Horizontal position", "x", -200, 200, 0) {
            if (it == 0) "Centre" else if (it < 0) "Left ${-it}" else "Right $it"
        })
        c.addView(size)

        // ---------- Backup ----------
        c.addView(ui.section("BACKUP"))
        val back = ui.group()
        back.addView(ui.tv(
            "Copy your settings, then paste them here after reinstalling.",
            13f, ui.sub
        ).apply { setPadding(dp(20), dp(18), dp(20), 0) })
        back.addView(ui.buttonRow(
            ui.pill("Copy settings", true) { exportSettings() },
            ui.pill("Paste settings", false) { importSettings() }
        ))
        c.addView(back)

        c.addView(ui.pill("Reset to defaults", false, true) { confirmReset() },
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52)
            ).apply { topMargin = dp(22) })

        val ver = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        c.addView(ui.tv("Island  •  Version $ver", 12f, ui.sub).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, 0)
        }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        setContentView(ui.screen("Island", "Dynamic Island for your Samsung phone", null, c))

        val req = ArrayList<String>()
        if (Build.VERSION.SDK_INT >= 33) req.add("android.permission.POST_NOTIFICATIONS")
        if (Build.VERSION.SDK_INT >= 31) req.add("android.permission.BLUETOOTH_CONNECT")
        req.add("android.permission.ANSWER_PHONE_CALLS")
        // Needed for the live music bars (reads the playing audio, not the microphone)
        req.add("android.permission.RECORD_AUDIO")
        val need = req.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // restart so the live music bars start working right after the permission is granted
        restartIfRunning()
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

    // ---------- Small builders ----------

    private fun slider(
        title: String,
        key: String,
        min: Int,
        max: Int,
        def: Int,
        fmt: (Int) -> String = { it.toString() }
    ): View {
        return ui.sliderRow(
            title, min, max, prefs.getInt(key, def), fmt,
            onLive = {},
            onCommit = {
                prefs.edit().putInt(key, it).apply()
                restartIfRunning()
            }
        )
    }

    private fun styleSwatches() {
        val cur = prefs.getInt("pill_color", swatchColors[0])
        for (i in swatchViews.indices) {
            val sel = swatchColors[i] == cur
            swatchViews[i].background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(swatchColors[i])
                setStroke(dp(if (sel) 3 else 1), if (sel) ui.accent else ui.sub)
            }
        }
    }

    private fun confirmReset() {
        AlertDialog.Builder(
            this,
            if (ui.dark) android.R.style.Theme_DeviceDefault_Dialog_Alert
            else android.R.style.Theme_DeviceDefault_Light_Dialog_Alert
        )
            .setTitle("Reset to defaults?")
            .setMessage("All Island settings will go back to their default values.")
            .setPositiveButton("Reset") { _, _ ->
                prefs.edit().clear().apply()
                restartIfRunning()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---------- App picker (One UI style bottom sheet with icons and search) ----------

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
        val checked = BooleanArray(list.size) {
            val inSet = stored.contains(list[it].first)
            if (invert) !inSet else inSet
        }
        val icons = ConcurrentHashMap<String, Drawable>()
        val shown = ArrayList<Int>()
        for (i in list.indices) shown.add(i)

        val adapter = object : BaseAdapter() {
            override fun getCount() = shown.size
            override fun getItem(position: Int): Any = shown[position]
            override fun getItemId(position: Int): Long = shown[position].toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val idx = shown[position]
                val row = (convertView as? LinearLayout) ?: LinearLayout(this@MainActivity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(20), dp(8), dp(16), dp(8))
                    val iv = ImageView(this@MainActivity).apply { tag = "icon" }
                    addView(iv, LinearLayout.LayoutParams(dp(40), dp(40)).apply { marginEnd = dp(14) })
                    addView(ui.tv("", 16f, ui.text).apply { tag = "name" },
                        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    addView(CheckBox(this@MainActivity).apply {
                        tag = "check"
                        isClickable = false
                        isFocusable = false
                        buttonTintList = ColorStateList.valueOf(ui.accent)
                    })
                }
                row.findViewWithTag<ImageView>("icon").setImageDrawable(icons[list[idx].first])
                row.findViewWithTag<TextView>("name").text = list[idx].second
                row.findViewWithTag<CheckBox>("check").isChecked = checked[idx]
                return row
            }
        }

        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)

        val lv = ListView(this).apply {
            this.adapter = adapter
            divider = null
            selector = ColorDrawable(Color.TRANSPARENT)
            setOnItemClickListener { _, _, pos, _ ->
                val idx = shown[pos]
                checked[idx] = !checked[idx]
                adapter.notifyDataSetChanged()
            }
        }

        val search = EditText(this).apply {
            hint = "Search apps"
            setHintTextColor(ui.sub)
            setTextColor(ui.text)
            textSize = 15f
            isSingleLine = true
            setPadding(dp(18), 0, dp(18), 0)
            background = GradientDrawable().apply {
                setColor(if (ui.dark) 0xFF2A2A2D.toInt() else 0xFFF0F0F4.toInt())
                cornerRadius = dp(22).toFloat()
            }
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) {
                    val q = s?.toString()?.trim()?.lowercase() ?: ""
                    shown.clear()
                    for (i in list.indices) {
                        if (q.isEmpty() || list[i].second.lowercase().contains(q)) shown.add(i)
                    }
                    adapter.notifyDataSetChanged()
                }

                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            })
        }

        val buttons = ui.buttonRow(
            ui.pill("Cancel", false) { dlg.dismiss() },
            ui.pill("Save", true) {
                val nb = HashSet<String>()
                for (i in list.indices) {
                    val inSet = if (invert) !checked[i] else checked[i]
                    if (inSet) nb.add(list[i].first)
                }
                prefs.edit().putStringSet(key, nb).apply()
                restartIfRunning()
                dlg.dismiss()
            }
        )

        val sheet = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(ui.card)
                cornerRadius = dp(28).toFloat()
            }
            addView(ui.tv(title, 20f, ui.text, true).apply {
                setPadding(dp(24), dp(24), dp(24), dp(14))
            })
            addView(search, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(46)
            ).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
                bottomMargin = dp(8)
            })
            addView(lv, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            ))
            addView(buttons)
        }

        val holder = FrameLayout(this).apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(sheet, FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            ))
        }
        dlg.setContentView(holder)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (resources.displayMetrics.heightPixels * 0.82f).toInt()
            )
            setGravity(Gravity.BOTTOM)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dlg.show()

        // Load the real app icons in the background
        Thread {
            for ((pkg, _) in list) {
                try {
                    icons[pkg] = pm.getApplicationIcon(pkg)
                } catch (e: Exception) {
                }
            }
            runOnUiThread { adapter.notifyDataSetChanged() }
        }.start()
    }

    // ---------- Backup ----------

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

    // ---------- Status ----------

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
        val running = IslandService.running
        heroDot.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (running) ui.ok else ui.sub)
        }
        heroTitle.text = if (running) "Island is running" else "Island is stopped"
        val overlay = Settings.canDrawOverlays(this)
        val listener = listenerEnabled()
        heroSub.text = when {
            !overlay -> "Allow “Display over other apps” to start"
            !listener -> "Grant notification access to show notifications"
            running -> "Everything is set up"
            else -> "Tap Start to show Island"
        }

        ui.setStatus(overlayRow, overlay, if (overlay) "Allowed" else "Not allowed, tap to allow")
        ui.setStatus(listenerRow, listener, if (listener) "Allowed" else "Not allowed, tap to allow")
        val usage = hasUsageAccess()
        ui.setStatus(
            usageRow, usage,
            if (usage) "Allowed" else "Not allowed. Needed for clear-on-open and hide-in-apps"
        )
        val shizuku = when {
            !ShizukuHelper.isRunning() -> "Not running" to false
            !ShizukuHelper.hasPermission() -> "Running, permission needed" to false
            else -> "Ready" to true
        }
        ui.setStatus(shizukuRow, shizuku.second, shizuku.first)

        val edgeOn = prefs.getBoolean("edge_on", true)
        val styleName = listOf("Aurora", "Comet", "Pulse").getOrElse(prefs.getInt("edge_style", 0)) { "Aurora" }
        edgeRow.summary?.text = if (edgeOn) "On  •  $styleName" else "Off"
    }

    private fun refreshLater() {
        heroTitle.postDelayed({ refresh() }, 800)
        heroTitle.postDelayed({ refresh() }, 2500)
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
}
