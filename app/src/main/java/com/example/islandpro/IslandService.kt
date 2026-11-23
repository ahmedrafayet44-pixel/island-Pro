package com.example.islandpro

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.content.res.Configuration
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val MP = -1
private const val WC = -2

private fun avgColor(bmp: Bitmap?): Int? {
    if (bmp == null) return null
    return try {
        val p = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
        Color.rgb(
            (Color.red(p) * 0.45f).toInt(),
            (Color.green(p) * 0.45f).toInt(),
            (Color.blue(p) * 0.45f).toInt()
        )
    } catch (e: Exception) {
        null
    }
}

internal class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val icon: Bitmap?,
    val title: String,
    val lines: List<String>,
    val time: Long,
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
    val live: Boolean,
    val progress: Int,
    val progressMax: Int,
    val tint: Int?
) {
    var viewed = false
}

// kind: 0 incoming call, 1 alarm, 2 ongoing call
internal class AlertItem(
    val key: String,
    val kind: Int,
    val name: String,
    val sub: String,
    val icon: Bitmap?,
    val leftLabel: String,
    val leftColor: String,
    val left: PendingIntent?,
    val rightLabel: String,
    val rightColor: String,
    val right: PendingIntent?,
    val open: PendingIntent?,
    val startMs: Long
)

// kind: 0 music, 1 notification, 2 live progress, 3 ongoing call
internal class Page(val kind: Int, val item: NotifItem?)

class IslandService : Service() {

    companion object {
        @Volatile
        var running = false

        @Volatile
        var mediaReady = false

        @Volatile
        var instance: IslandService? = null
        private const val CHANNEL_ID = "island_min"
        private const val COLLAPSE_DELAY_MS = 1300L
        private const val ANIM_MS = 350L
        private const val LONG_COLLAPSE_MS = 3500L
        private const val EVENT_NONE = 0
        private const val EVENT_PLUG = 1
        private const val EVENT_UNPLUG = 2
        private const val EVENT_PAGE = 3
        private const val EVENT_ALERT = 4
        private const val EVENT_BAR = 5
        private const val EVENT_WARN = 6
        private const val EVENT_TOAST = 7
        private const val EVENT_QUICK = 8
    }

    private var wm: WindowManager? = null
    private var box: FrameLayout? = null
    private var bg: GradientDrawable? = null
    private var bgArt: ImageView? = null
    private var bgScrim: View? = null
    private var label: TextView? = null
    private var chargeRow: LinearLayout? = null
    private var chargeView: ChargeView? = null
    private var chargeTitle: TextView? = null
    private var chargeLevel: TextView? = null
    private var chargeSub: TextView? = null
    private var musicCard: LinearLayout? = null
    private var cardArt: ImageView? = null
    private var cardTitle: TextView? = null
    private var cardArtist: TextView? = null
    private var cardEq: EqView? = null
    private var playBtn: MediaButton? = null
    private var compactArt: ImageView? = null
    private var compactEq: EqView? = null
    private var compactTimer: TextView? = null
    private var notifRow: LinearLayout? = null
    private var notifIcon: ImageView? = null
    private var notifApp: TextView? = null
    private var notifTitle: TextView? = null
    private var notifText: TextView? = null
    private var notifBar: BarView? = null
    private var notifBtnRow: LinearLayout? = null
    private var notifInputRow: LinearLayout? = null
    private var notifInput: EditText? = null
    private var replyBtn: TextView? = null
    private var clearBtn: TextView? = null
    private var alertRow: LinearLayout? = null
    private var alertIcon: ImageView? = null
    private var alertSub: TextView? = null
    private var alertName: TextView? = null
    private var alertLeft: TextView? = null
    private var alertRight: TextView? = null
    private var toastRow: LinearLayout? = null
    private var toastIcon: TextView? = null
    private var toastTitle: TextView? = null
    private var toastSub: TextView? = null
    private var quickRow: LinearLayout? = null
    private var qTorch: TextView? = null
    private var qRinger: TextView? = null
    private var barRow: LinearLayout? = null
    private var barIcon: TextView? = null
    private var barText: TextView? = null
    private var barView: BarView? = null
    private var strip: LinearLayout? = null
    private var badgeBox: FrameLayout? = null
    private var badgeL: LinearLayout? = null
    private var badgeR: LinearLayout? = null
    private var dotCam: View? = null
    private var dotMic: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null
    private var heightAnim: ValueAnimator? = null

    private var smallW = 0
    private var smallH = 0
    private var bigW = 0
    private var bigH = 0
    private var frac = 0f
    private var targetFrac = 0f
    private var cornerPx = 0f

    private var charging = false
    private var event = EVENT_NONE
    private var chargeShown = false
    private var warnStage = 0
    private var warnText = ""
    private var landHidden = false
    private var appHidden = false
    private var hidden = false
    private var hapticOn = true
    private var barsOn = false
    private var hideLand = true
    private var matchColors = true
    private var basePillColor = Color.BLACK
    private var colorTarget = 0
    private var hideApps: Set<String> = emptySet()
    private var lastFg: String? = null
    private var badgeSig = ""

    private var curPage = 0
    private var replyMode = false
    private var downX = 0f
    private var downY = 0f
    private var downOnStrip = false
    private var lpFired = false

    private val notifs = ArrayList<NotifItem>()
    private val lives = ArrayList<NotifItem>()
    private var alert: AlertItem? = null
    private var ongoing: AlertItem? = null
    private val seen = HashMap<String, Int>()
    private val selfCancelled = HashSet<String>()

    private var torchOn = false
    private var micActive = false
    private val camUnavail = HashSet<String>()

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var musicTitle = ""
    private var musicArtist = ""
    private var musicArt: Bitmap? = null
    private var artTint: Int? = null
    private var musicPlaying = false

    private val handler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { animateTo(0f) }
    private val longPress = Runnable {
        lpFired = true
        haptic(true)
        showQuick()
    }

    private val ticker = object : Runnable {
        override fun run() {
            val o = ongoing ?: return
            val t = fmtElapsed(System.currentTimeMillis() - o.startMs)
            compactTimer?.text = t
            if (alertRow?.visibility == View.VISIBLE && event != EVENT_ALERT) {
                alertSub?.text = "Call • $t"
            }
            handler.postDelayed(this, 1000)
        }
    }

    private val fgPoll = object : Runnable {
        override fun run() {
            if (hideApps.isEmpty()) return
            try {
                updateForeground()
            } catch (e: Exception) {
            }
            handler.postDelayed(this, 1500)
        }
    }

    private val pillOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> pickController(list) }

    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            updateMusic()
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            updateMusic()
        }
    }

    private val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            try {
                val v = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                showBar("☀", "Brightness", v / 255f)
            } catch (e: Exception) {
            }
        }
    }

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { onClip() }

    private val camCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            camUnavail.remove(cameraId)
            updatePrivacy()
        }

        override fun onCameraUnavailable(cameraId: String) {
            camUnavail.add(cameraId)
            updatePrivacy()
        }
    }

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            torchOn = enabled
            updateQuickIcons()
            updatePrivacy()
        }
    }

    private val recCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            micActive = !configs.isNullOrEmpty()
            updatePrivacy()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    charging = true
                    warnStage = 0
                    startEvent(EVENT_PLUG)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    charging = false
                    startEvent(EVENT_UNPLUG)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (event == EVENT_PLUG || event == EVENT_UNPLUG) {
                        updateChargeTexts()
                    } else if (event == EVENT_NONE && label?.visibility == View.VISIBLE) {
                        showInfo()
                    }
                    checkLowBattery()
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> onRinger()
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (intent != null) onBluetooth(intent, true)
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (intent != null) onBluetooth(intent, false)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val sticky = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        charging = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        try {
            contentResolver.registerContentObserver(
                Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
                false,
                brightnessObserver
            )
        } catch (e: Exception) {
        }
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.addPrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
        try {
            val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.registerAvailabilityCallback(camCallback, handler)
            cam.registerTorchCallback(torchCallback, handler)
        } catch (e: Exception) {
        }
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.registerAudioRecordingCallback(recCallback, handler)
        } catch (e: Exception) {
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startAsForeground()
        running = true
        buildPill()
        setupMedia()
        applyPopupSetting()
        return START_STICKY
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyHidden()
    }

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Island (running)", NotificationManager.IMPORTANCE_MIN)
        )
        val n = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Island is running")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, n)
        }
    }

    private fun applyPopupSetting() {
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        val ctx = applicationContext
        if (prefs.getBoolean("hide_popups", true)) {
            Thread { ShizukuHelper.setHeadsUp(ctx, false) }.start()
        } else if (prefs.getBoolean("headsup_off", false)) {
            Thread { ShizukuHelper.setHeadsUp(ctx, true) }.start()
        }
    }

    // ---------- Helpers ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

    private fun vbox(g: Int = Gravity.NO_GRAVITY): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = g
        }
    }

    private fun hbox(g: Int = Gravity.CENTER_VERTICAL): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = g
        }
    }

    private fun collapseDelay(): Long = COLLAPSE_DELAY_MS + ANIM_MS

    private fun batteryLevel(): Int {
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    }

    private fun fmtElapsed(ms: Long): String {
        val s = maxOf(0L, ms / 1000)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        } else {
            String.format(Locale.US, "%02d:%02d", m, sec)
        }
    }

    private fun haptic(strong: Boolean = false) {
        if (!hapticOn) return
        try {
            val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (Build.VERSION.SDK_INT >= 29) {
                v.vibrate(
                    VibrationEffect.createPredefined(
                        if (strong) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_TICK
                    )
                )
            } else {
                v.vibrate(
                    VibrationEffect.createOneShot(
                        if (strong) 20L else 10L,
                        VibrationEffect.DEFAULT_AMPLITUDE
                    )
                )
            }
        } catch (e: Exception) {
        }
    }

    private fun drawableToBitmap(d: Drawable, size: Int): Bitmap {
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, size, size)
        d.draw(c)
        return b
    }

    private fun appIcon(pkg: String): Bitmap? {
        return try {
            drawableToBitmap(packageManager.getApplicationIcon(pkg), dp(48))
        } catch (e: Exception) {
            null
        }
    }

    private fun sendPi(pi: PendingIntent) {
        try {
            pi.send()
        } catch (e: Exception) {
        }
    }

    private fun launchApp(pkg: String?) {
        if (pkg == null) return
        try {
            val i = packageManager.getLaunchIntentForPackage(pkg) ?: return
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        } catch (e: Exception) {
        }
    }

    private fun busy(): Boolean {
        return targetFrac == 1f &&
            (event == EVENT_PAGE || event == EVENT_ALERT || event == EVENT_PLUG ||
                event == EVENT_UNPLUG || event == EVENT_QUICK)
    }

    // ---------- Notifications ----------

    private fun findReplyAction(n: Notification): Notification.Action? {
        val acts = n.actions ?: return null
        for (a in acts) {
            val ris = a.remoteInputs ?: continue
            for (ri in ris) {
                if (ri.allowFreeFormInput) return a
            }
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun extractLines(ex: Bundle): List<String> {
        val out = ArrayList<String>()
        val msgs = ex.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (msgs != null) {
            for (m in msgs) {
                val b = m as? Bundle ?: continue
                val t = b.getCharSequence("text")?.toString() ?: continue
                val sender = b.getCharSequence("sender")?.toString() ?: ""
                out.add(if (sender.isEmpty()) t else "$sender: $t")
            }
        }
        if (out.isEmpty()) {
            val ls = ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (ls != null) {
                for (l in ls) out.add(l.toString())
            }
        }
        if (out.isEmpty()) {
            val t = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
            if (t.isNotEmpty()) out.add(t)
        }
        return if (out.size > 3) out.subList(out.size - 3, out.size).toList() else out
    }

    fun onNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        val cat = n.category

        if (cat == Notification.CATEGORY_CALL) {
            handleCall(sbn, n, ex)
            return
        }
        val acts0 = n.actions
        if (cat == Notification.CATEGORY_ALARM) {
            if (acts0 != null && acts0.isNotEmpty()) handleAlarm(sbn, n, ex)
            return
        }

        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet("blocked_apps", null)
        if (blocked != null && blocked.contains(sbn.packageName)) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.fullScreenIntent != null) return
        if (cat == Notification.CATEGORY_TRANSPORT ||
            cat == Notification.CATEGORY_SERVICE ||
            cat == Notification.CATEGORY_NAVIGATION
        ) return
        val template = ex.getString(Notification.EXTRA_TEMPLATE) ?: ""
        if (template.contains("MediaStyle")) return

        val pmax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val live = pmax > 0
        val ongoingFlag = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        if (ongoingFlag && !live) return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val lines = extractLines(ex)
        if (title.isEmpty() && lines.isEmpty()) return
        val progress = ex.getInt(Notification.EXTRA_PROGRESS, 0)

        if (!live && prefs.getBoolean("hide_sys", false)) {
            removeFromSystem(sbn.key)
        }

        val sig = title.hashCode() * 31 + lines.joinToString("|").hashCode() + progress * 7
        if (seen[sbn.key] == sig) return
        if (seen.size > 100) seen.clear()
        seen[sbn.key] = sig

        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        val icon = appIcon(sbn.packageName)

        val item = NotifItem(
            sbn.key, sbn.packageName, appName, icon,
            title, lines, sbn.postTime, n.contentIntent,
            if (live) null else findReplyAction(n),
            live, progress, pmax, avgColor(icon)
        )

        if (live) {
            val idx = lives.indexOfFirst { it.key == item.key }
            if (idx >= 0) {
                lives[idx] = item
            } else {
                lives.add(0, item)
                while (lives.size > 5) lives.removeAt(lives.size - 1)
            }
            if (box != null) {
                renderPage()
                renderContent()
            }
            return
        }

        notifs.removeAll { it.key == item.key }
        notifs.add(0, item)
        while (notifs.size > 15) notifs.removeAt(notifs.size - 1)

        if (box == null) return
        if (replyMode || busy()) {
            renderPage()
            renderContent()
            return
        }
        showPages(if (hasMusic()) 1 else 0)
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        if (alert?.key == sbn.key) endAlert()
        if (ongoing?.key == sbn.key) {
            ongoing = null
            compactTimer?.visibility = View.GONE
            if (box != null) {
                renderPage()
                renderContent()
            }
        }
        val liveRemoved = lives.removeAll { it.key == sbn.key }
        if (selfCancelled.remove(sbn.key)) {
            if (liveRemoved && box != null) {
                renderPage()
                renderContent()
            }
            return
        }
        val removed = notifs.removeAll { it.key == sbn.key }
        if ((removed || liveRemoved) && box != null) {
            renderPage()
            renderContent()
        }
    }

    private fun removeFromSystem(key: String) {
        val l = MusicListener.instance ?: return
        if (selfCancelled.size > 200) selfCancelled.clear()
        selfCancelled.add(key)
        try {
            l.cancelNotification(key)
        } catch (e: Exception) {
            selfCancelled.remove(key)
        }
    }

    private fun openItem(item: NotifItem) {
        var ok = false
        val pi = item.contentIntent
        if (pi != null) {
            try {
                pi.send()
                ok = true
            } catch (e: Exception) {
            }
        }
        if (!ok) launchApp(item.pkg)
        notifs.remove(item)
        lives.remove(item)
    }

    private fun openMusicApp() {
        val c = controller ?: return
        val pi = c.sessionActivity
        if (pi != null) {
            try {
                pi.send()
                return
            } catch (e: Exception) {
            }
        }
        launchApp(c.packageName)
    }

    private fun dismissCurrent() {
        val pg = pageAt(curPage) ?: return
        val item = pg.item ?: return
        if (pg.kind == 1) {
            removeFromSystem(item.key)
            notifs.remove(item)
        } else if (pg.kind == 2) {
            lives.remove(item)
        }
        haptic()
        afterListChange()
    }

    private fun clearAll() {
        for (n in notifs) removeFromSystem(n.key)
        notifs.clear()
        haptic(true)
        animateTo(0f)
        renderPage()
    }

    private fun afterListChange() {
        renderPage()
        if (pageCount() == 0) {
            animateTo(0f)
            return
        }
        animateHeightTo(pageHeight())
        renderContent()
        scheduleCollapse(collapseDelay())
    }

    // ---------- Calls and alarms ----------

    @Suppress("DEPRECATION")
    private fun handleCall(sbn: StatusBarNotification, n: Notification, ex: Bundle) {
        val ct = ex.getInt("android.callType", 0)
        val ongoingFlag = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val incoming = ct == 1 || (ct == 0 && n.fullScreenIntent != null)
        val inCall = ct == 2 || (ct == 0 && ongoingFlag && n.fullScreenIntent == null)
        if (!incoming && !inCall) return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val name = title.ifEmpty {
            ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Unknown caller"
        }
        val open = n.contentIntent ?: n.fullScreenIntent
        val icon = appIcon(sbn.packageName)
        val acts = n.actions

        if (incoming) {
            var ans: PendingIntent? = ex.getParcelable("android.answerIntent")
            var dec: PendingIntent? = ex.getParcelable("android.declineIntent")
            if ((ans == null || dec == null) && acts != null) {
                for (a in acts) {
                    val t = a.title?.toString()?.lowercase(Locale.ROOT) ?: ""
                    if (ans == null &&
                        (t.contains("answer") || t.contains("accept") || t.contains("receive"))
                    ) {
                        ans = a.actionIntent
                    } else if (dec == null &&
                        (t.contains("decline") || t.contains("reject") ||
                            t.contains("dismiss") || t.contains("hang"))
                    ) {
                        dec = a.actionIntent
                    }
                }
            }
            alert = AlertItem(
                sbn.key, 0, name, "Incoming call", icon,
                if (dec != null) "Decline" else "", "#FF453A", dec,
                if (ans != null) "Accept" else "", "#30D158", ans,
                open, System.currentTimeMillis()
            )
            if (box != null) showAlert()
            return
        }

        var hang: PendingIntent? = ex.getParcelable("android.hangUpIntent")
        var mute: PendingIntent? = null
        if (acts != null) {
            for (a in acts) {
                val t = a.title?.toString()?.lowercase(Locale.ROOT) ?: ""
                if (mute == null && t.contains("mute")) {
                    mute = a.actionIntent
                } else if (hang == null &&
                    (t.contains("end") || t.contains("hang") || t.contains("disconnect"))
                ) {
                    hang = a.actionIntent
                }
            }
        }
        val start = if (n.`when` > 0) n.`when` else sbn.postTime
        ongoing = AlertItem(
            sbn.key, 2, name, "Call", icon,
            if (mute != null) "Mute" else "", "#33FFFFFF", mute,
            if (hang != null) "End call" else "", "#FF453A", hang,
            open, start
        )
        if (alert?.key == sbn.key) {
            alert = null
            if (event == EVENT_ALERT) animateTo(0f)
        }
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        if (box != null) {
            renderPage()
            renderContent()
        }
    }

    private fun handleAlarm(sbn: StatusBarNotification, n: Notification, ex: Bundle) {
        val acts = n.actions ?: return
        if (alert?.key == sbn.key) return
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val a0 = acts[0]
        val a1 = if (acts.size > 1) acts[1] else null
        alert = AlertItem(
            sbn.key, 1, title.ifEmpty { "Alarm" }, "Alarm", appIcon(sbn.packageName),
            a0.title?.toString() ?: "", "#33FFFFFF", a0.actionIntent,
            a1?.title?.toString() ?: "", "#6A5CFF", a1?.actionIntent,
            n.contentIntent, System.currentTimeMillis()
        )
        if (box != null) showAlert()
    }

    private fun setBtn(tv: TextView?, text: String, color: String) {
        tv?.text = text
        tv?.visibility = if (text.isEmpty()) View.GONE else View.VISIBLE
        (tv?.background as? GradientDrawable)?.setColor(Color.parseColor(color))
    }

    private fun bindAlert(a: AlertItem) {
        alertName?.text = a.name
        alertIcon?.setImageBitmap(a.icon)
        alertSub?.text = if (a.kind == 2) {
            "Call • " + fmtElapsed(System.currentTimeMillis() - a.startMs)
        } else {
            a.sub
        }
        setBtn(alertLeft, a.leftLabel, a.leftColor)
        setBtn(alertRight, a.rightLabel, a.rightColor)
    }

    private fun showAlert() {
        val a = alert ?: return
        bindAlert(a)
        handler.removeCallbacks(collapseRunnable)
        if (replyMode) exitReply()
        event = EVENT_ALERT
        setBigHeight(dp(124))
        haptic(true)
        animateTo(1f)
        renderContent()
    }

    private fun endAlert() {
        if (alert == null) return
        alert = null
        if (event == EVENT_ALERT) animateTo(0f)
    }

    private fun alertTap(left: Boolean) {
        val a: AlertItem? = if (event == EVENT_ALERT) {
            alert
        } else if (event == EVENT_PAGE && pageAt(curPage)?.kind == 3) {
            ongoing
        } else {
            null
        }
        if (a == null) return
        val pi = if (left) a.left else a.right
        if (pi != null) sendPi(pi)
        if (event == EVENT_ALERT) alert = null
        scheduleCollapse(1500)
    }

    // ---------- Reply ----------

    private fun setFocusable(on: Boolean) {
        val wlp = params ?: return
        if (on) {
            wlp.flags = wlp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            wlp.flags = wlp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val v = box ?: return
        if (v.isAttachedToWindow) wm?.updateViewLayout(v, wlp)
    }

    private fun startReply() {
        val item = pageAt(curPage)?.item ?: return
        if (item.replyAction == null) return
        replyMode = true
        handler.removeCallbacks(collapseRunnable)
        notifBtnRow?.visibility = View.GONE
        notifInputRow?.visibility = View.VISIBLE
        notifInput?.setText("")
        setFocusable(true)
        notifInput?.requestFocus()
        handler.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            val inp = notifInput
            if (inp != null) imm.showSoftInput(inp, 0)
        }, 250)
    }

    private fun exitReply() {
        if (!replyMode) return
        replyMode = false
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val tok = notifInput?.windowToken
        if (tok != null) imm.hideSoftInputFromWindow(tok, 0)
        notifInput?.clearFocus()
        notifInputRow?.visibility = View.GONE
        setFocusable(false)
        val item = pageAt(curPage)?.item
        notifBtnRow?.visibility =
            if (item != null && !item.live) View.VISIBLE else View.GONE
    }

    private fun sendReply(item: NotifItem, text: String) {
        val a = item.replyAction ?: return
        val ris = a.remoteInputs ?: return
        val intent = Intent()
        val results = Bundle()
        for (ri in ris) results.putCharSequence(ri.resultKey, text)
        RemoteInput.addResultsToIntent(ris, intent, results)
        try {
            a.actionIntent.send(this, 0, intent)
        } catch (e: Exception) {
        }
    }

    private fun submitReply() {
        val item = pageAt(curPage)?.item ?: return
        val txt = notifInput?.text?.toString()?.trim() ?: ""
        if (txt.isEmpty()) return
        sendReply(item, txt)
        notifs.remove(item)
        haptic(true)
        animateTo(0f)
        renderPage()
    }

    // ---------- Media ----------

    private fun setupMedia() {
        try {
            val m = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            try {
                msm?.removeOnActiveSessionsChangedListener(sessionsListener)
            } catch (e: Exception) {
            }
            val cn = ComponentName(this, MusicListener::class.java)
            m.addOnActiveSessionsChangedListener(sessionsListener, cn, handler)
            msm = m
            mediaReady = true
            pickController(m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            mediaReady = false
        }
    }

    private fun pickController(list: List<MediaController>?) {
        val best = list?.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: list?.firstOrNull()
        if (best?.sessionToken != controller?.sessionToken) {
            controller?.unregisterCallback(mediaCallback)
            controller = best
            controller?.registerCallback(mediaCallback, handler)
        }
        updateMusic()
    }

    private fun hasMusic(): Boolean = controller != null && musicTitle.isNotEmpty()

    private fun updateMusic() {
        val c = controller
        val md = c?.metadata
        musicTitle = md?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
        musicArtist = md?.getString(MediaMetadata.METADATA_KEY_ARTIST)
            ?: md?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            ?: ""
        val art = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        if (art !== musicArt) {
            musicArt = art
            artTint = avgColor(art)
        }
        val st = c?.playbackState?.state
        musicPlaying = st == PlaybackState.STATE_PLAYING ||
            st == PlaybackState.STATE_BUFFERING ||
            st == PlaybackState.STATE_SKIPPING_TO_NEXT ||
            st == PlaybackState.STATE_SKIPPING_TO_PREVIOUS
        refreshMusicViews()
    }

    private fun refreshMusicViews() {
        cardTitle?.text = musicTitle.ifEmpty { "Unknown title" }
        cardArtist?.text = musicArtist
        cardArt?.setImageBitmap(musicArt)
        bgArt?.setImageBitmap(musicArt)
        compactArt?.setImageBitmap(musicArt)
        playBtn?.icon = if (musicPlaying) MediaButton.PAUSE else MediaButton.PLAY
        cardEq?.setActive(musicPlaying)
        compactEq?.setActive(musicPlaying)
        renderContent()
    }

    // ---------- Small events ----------

    private fun showToast(icon: String, title: String, sub: String) {
        if (box == null || hidden || replyMode || busy()) return
        toastIcon?.text = icon
        toastTitle?.text = title
        toastSub?.text = sub
        toastSub?.visibility = if (sub.isEmpty()) View.GONE else View.VISIBLE
        event = EVENT_TOAST
        setBigHeight(dp(72))
        handler.removeCallbacks(collapseRunnable)
        haptic()
        if (targetFrac != 1f) animateTo(1f)
        renderContent()
        scheduleCollapse(2000L + ANIM_MS)
    }

    private fun onClip() {
        var preview = ""
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            val sensitive =
                clip?.description?.extras?.getBoolean("android.content.extra.IS_SENSITIVE", false)
                    ?: false
            if (!sensitive) {
                preview = clip?.getItemAt(0)?.coerceToText(this)?.toString()?.take(40) ?: ""
            }
        } catch (e: Exception) {
        }
        showToast("📋", "Copied", preview.replace("\n", " "))
    }

    private fun onRinger() {
        updateQuickIcons()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> showToast("🔕", "Silent", "")
            AudioManager.RINGER_MODE_VIBRATE -> showToast("📳", "Vibrate", "")
            else -> showToast("🔔", "Sound on", "")
        }
    }

    @Suppress("DEPRECATION")
    private fun onBluetooth(i: Intent, connected: Boolean) {
        val d = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
        var name = "Bluetooth device"
        var battery = -1
        val ok = Build.VERSION.SDK_INT < 31 ||
            checkSelfPermission("android.permission.BLUETOOTH_CONNECT") ==
            PackageManager.PERMISSION_GRANTED
        if (ok) {
            try {
                name = d.name ?: name
            } catch (e: SecurityException) {
            }
            try {
                battery = d.javaClass.getMethod("getBatteryLevel").invoke(d) as Int
            } catch (e: Throwable) {
            }
        }
        val sub = if (connected) {
            if (battery in 0..100) "Connected • $battery%" else "Connected"
        } else {
            "Disconnected"
        }
        showToast("🎧", name, sub)
    }

    private fun showBar(icon: String, name: String, value: Float) {
        if (!barsOn || box == null || hidden || replyMode || busy()) return
        barIcon?.text = icon
        barText?.text = "$name  ${(value * 100).toInt()}%"
        barView?.value = value
        if (event != EVENT_BAR) {
            event = EVENT_BAR
            setBigHeight(dp(72))
        }
        handler.removeCallbacks(collapseRunnable)
        if (targetFrac != 1f) animateTo(1f)
        renderContent()
        scheduleCollapse(collapseDelay())
    }

    private fun checkLowBattery() {
        if (charging) {
            warnStage = 0
            return
        }
        val level = batteryLevel()
        if (level < 0) return
        val stage = if (level <= 10) 2 else if (level <= 20) 1 else 0
        if (stage > warnStage) {
            warnStage = stage
            showWarn(level)
        } else if (stage < warnStage) {
            warnStage = stage
        }
    }

    private fun showWarn(level: Int) {
        if (box == null || hidden || replyMode || busy()) return
        warnText = "⚠ Battery low  $level%"
        event = EVENT_WARN
        setBigHeight(dp(80))
        handler.removeCallbacks(collapseRunnable)
        haptic(true)
        animateTo(1f)
        scheduleCollapse(LONG_COLLAPSE_MS)
    }

    // ---------- Quick buttons, torch, ringer ----------

    private fun torchId(): String? {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in cm.cameraIdList) {
            val c = cm.getCameraCharacteristics(id)
            val flash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            val back = c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            if (flash && back) return id
        }
        return null
    }

    private fun toggleTorch() {
        try {
            val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = torchId() ?: return
            cm.setTorchMode(id, !torchOn)
        } catch (e: Exception) {
        }
    }

    private fun toggleRinger() {
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_NORMAL) {
                AudioManager.RINGER_MODE_VIBRATE
            } else {
                AudioManager.RINGER_MODE_NORMAL
            }
        } catch (e: SecurityException) {
        }
        updateQuickIcons()
    }

    private fun updateQuickIcons() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        qRinger?.text = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> "🔕"
            AudioManager.RINGER_MODE_VIBRATE -> "📳"
            else -> "🔔"
        }
        (qTorch?.background as? GradientDrawable)
            ?.setColor(Color.parseColor(if (torchOn) "#6A5CFF" else "#33FFFFFF"))
    }

    private fun showQuick() {
        if (box == null || hidden || replyMode) return
        updateQuickIcons()
        event = EVENT_QUICK
        setBigHeight(dp(84))
        handler.removeCallbacks(collapseRunnable)
        animateTo(1f)
        renderContent()
        scheduleCollapse(LONG_COLLAPSE_MS)
    }

    private fun openShade() {
        haptic(true)
        if (!ShizukuHelper.hasPermission()) {
            showToast("ⓘ", "Shizuku needed", "Grant it in the Island app")
            return
        }
        animateTo(0f)
        Thread { ShizukuHelper.expandShade() }.start()
    }

    // ---------- Privacy dots, charging info, foreground app ----------

    private fun updatePrivacy() {
        val cam = camUnavail.isNotEmpty() && !torchOn
        dotCam?.visibility = if (cam) View.VISIBLE else View.GONE
        dotMic?.visibility = if (micActive) View.VISIBLE else View.GONE
        updateBadges()
    }

    private fun chargeInfo(): String {
        return try {
            val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val mv = bat?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
            var ua = abs(bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)).toDouble()
            if (ua in 1.0..20000.0) ua *= 1000.0
            val w = ua / 1_000_000.0 * (mv / 1000.0)
            val parts = ArrayList<String>()
            if (w > 0.5) {
                val speed = if (w >= 18) "Super fast" else if (w >= 10) "Fast" else "Slow"
                parts.add(speed)
                parts.add(String.format(Locale.US, "%.1f W", w))
            }
            val rem = if (Build.VERSION.SDK_INT >= 28) bm.computeChargeTimeRemaining() else -1L
            if (rem > 0) parts.add("${rem / 60000} min left")
            parts.joinToString(" • ")
        } catch (e: Exception) {
            ""
        }
    }

    private fun updateForeground() {
        val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val ev = usm.queryEvents(now - (if (lastFg == null) 3600000L else 10000L), now)
        val e = UsageEvents.Event()
        var found: String? = null
        while (ev.hasNextEvent()) {
            ev.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) found = e.packageName
        }
        if (found != null) lastFg = found
        val fg = lastFg
        val h = fg != null && hideApps.contains(fg)
        if (h != appHidden) {
            appHidden = h
            applyHidden()
        }
    }

    // ---------- Pages ----------

    private fun pages(): List<Page> {
        val l = ArrayList<Page>()
        if (ongoing != null) l.add(Page(3, null))
        if (hasMusic()) l.add(Page(0, null))
        for (n in notifs) l.add(Page(1, n))
        for (v in lives) l.add(Page(2, v))
        return l
    }

    private fun pageCount(): Int = pages().size

    private fun pageAt(p: Int): Page? = pages().getOrNull(p)

    private fun firstMusicOrNotif(): Int {
        val ps = pages()
        for (i in ps.indices) {
            if (ps[i].kind == 1 && ps[i].item?.viewed == false) return i
        }
        return 0
    }

    private fun markViewedCurrent() {
        val pg = pageAt(curPage)
        if (pg != null && pg.kind == 1) pg.item?.viewed = true
    }

    private fun pageHeight(): Int {
        val extra = if (notifs.isNotEmpty()) dp(18) else 0
        val pg = pageAt(curPage) ?: return dp(80)
        val base = when (pg.kind) {
            0 -> dp(124)
            2 -> dp(100)
            3 -> dp(124)
            else -> dp(93) + dp(17) * minOf(3, maxOf(1, pg.item?.lines?.size ?: 1))
        }
        return base + extra
    }

    private fun bindItem(item: NotifItem) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.time))
        notifApp?.text =
            if (item.live) "${item.appName}  •  In progress" else "${item.appName}  •  $time"
        notifTitle?.text = item.title
        notifText?.text = item.lines.joinToString("\n")
        notifText?.maxLines = if (item.live) 1 else 3
        notifIcon?.setImageBitmap(item.icon)
        notifBar?.visibility = if (item.live) View.VISIBLE else View.GONE
        if (item.live) {
            notifBar?.value =
                if (item.progressMax > 0) item.progress / item.progressMax.toFloat() else 0f
        }
        notifBtnRow?.visibility = if (!item.live && !replyMode) View.VISIBLE else View.GONE
        replyBtn?.visibility = if (item.replyAction != null) View.VISIBLE else View.GONE
        clearBtn?.visibility = if (notifs.size >= 2) View.VISIBLE else View.GONE
    }

    private fun renderPage() {
        curPage = curPage.coerceIn(0, maxOf(0, pageCount() - 1))
        val pg = pageAt(curPage)
        val item = pg?.item
        if (item != null) bindItem(item)
        val o = ongoing
        if (pg != null && pg.kind == 3 && o != null) bindAlert(o)
        val extra = if (notifs.isNotEmpty()) dp(18) else 0
        musicCard?.setPadding(dp(18), dp(8), dp(18), dp(8) + extra)
        notifRow?.setPadding(dp(16), dp(8), dp(16), dp(8) + extra)
        alertRow?.setPadding(dp(18), dp(10), dp(18), dp(12) + extra)
        buildStrip()
        updateBadges()
    }

    private fun smallIcon(bmp: Bitmap?, ratio: Float): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundOutline(ratio)
            if (bmp != null) {
                setImageBitmap(bmp)
            } else {
                setBackgroundColor(Color.parseColor("#6A5CFF"))
            }
        }
    }

    private fun buildStrip() {
        val s = strip ?: return
        s.removeAllViews()
        val byApp = LinkedHashMap<String, NotifItem>()
        for (n in notifs) {
            if (!byApp.containsKey(n.pkg)) byApp[n.pkg] = n
        }
        if (byApp.isEmpty()) {
            s.visibility = View.GONE
            return
        }
        val curPkg = pageAt(curPage)?.item?.pkg
        var count = 0
        for ((pkg, item) in byApp) {
            if (count >= 7) break
            val cur = pkg == curPkg
            val iv = smallIcon(item.icon, 0.28f)
            iv.alpha = if (cur) 1f else 0.5f
            val size = if (cur) dp(16) else dp(13)
            s.addView(iv, lp(size, size).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
            count++
        }
    }

    private fun updateBadges() {
        val bb = badgeBox ?: return
        val unseen = LinkedHashMap<String, NotifItem>()
        for (n in notifs) {
            if (!n.viewed && !unseen.containsKey(n.pkg)) unseen[n.pkg] = n
        }
        val sig = unseen.keys.joinToString(",")
        if (sig != badgeSig) {
            badgeSig = sig
            badgeL?.removeAllViews()
            badgeR?.removeAllViews()
            var i = 0
            for ((_, item) in unseen) {
                if (i >= 8) break
                val iv = smallIcon(item.icon, 0.3f)
                val target = if (i % 2 == 0) badgeL else badgeR
                target?.addView(iv, lp(dp(14), dp(14)).apply {
                    marginStart = dp(2)
                    marginEnd = dp(2)
                })
                i++
            }
        }
        val dots = dotCam?.visibility == View.VISIBLE || dotMic?.visibility == View.VISIBLE
        val show = (unseen.isNotEmpty() || dots) && frac < 0.3f && !hidden
        bb.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun changePage(dir: Int) {
        if (pageCount() < 2) return
        val np = curPage + dir
        if (np < 0 || np >= pageCount()) return
        if (replyMode) exitReply()
        curPage = np
        markViewedCurrent()
        haptic()
        renderPage()
        animateHeightTo(pageHeight())
        renderContent()
        val k = pageAt(curPage)?.kind
        val v: View? = when (k) {
            0 -> musicCard
            3 -> alertRow
            else -> notifRow
        }
        v?.translationX = (dir * dp(48)).toFloat()
        v?.alpha = 0f
        v?.animate()?.translationX(0f)?.alpha(1f)?.setDuration(200)?.start()
    }

    // ---------- Pill ----------

    private fun roundOutline(ratio: Float): ViewOutlineProvider {
        return object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, view.width * ratio)
            }
        }
    }

    private fun artView(ratio: Float): ImageView {
        return ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#6A5CFF"), Color.parseColor("#1FD1F9"))
            )
            clipToOutline = true
            outlineProvider = roundOutline(ratio)
        }
    }

    private fun mediaBtn(kind: Int, onClick: () -> Unit): MediaButton {
        return MediaButton(this, kind).apply {
            setOnClickListener {
                haptic()
                onClick()
                scheduleCollapse(collapseDelay())
            }
        }
    }

    private fun smallText(size: Float, color: String, bold: Boolean): TextView {
        return TextView(this).apply {
            textSize = size
            setTextColor(Color.parseColor(color))
            if (bold) setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
    }

    private fun pillButton(text: String, fill: String = "#33FFFFFF", onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor(fill))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener {
                haptic()
                onClick()
            }
        }
    }

    private fun circleBtn(emoji: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = emoji
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            setOnClickListener {
                haptic()
                onClick()
                scheduleCollapse(LONG_COLLAPSE_MS)
            }
        }
    }

    private fun dot(color: String): View {
        return View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(color))
            }
            visibility = View.GONE
        }
    }

    private fun buildPill() {
        removePill()
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        smallW = dp(prefs.getInt("w", 120))
        smallH = dp(prefs.getInt("h", 34))
        val top = dp(prefs.getInt("top", 8))
        val offsetX = dp(prefs.getInt("x", 0))
        hapticOn = prefs.getBoolean("haptic", true)
        barsOn = prefs.getBoolean("show_bright", false)
        hideLand = prefs.getBoolean("hide_land", true)
        matchColors = prefs.getBoolean("match_colors", true)
        hideApps = prefs.getStringSet("hide_apps", null)?.toSet() ?: emptySet()
        val baseColor = prefs.getInt("pill_color", Color.BLACK)
        val alphaPct = prefs.getInt("pill_alpha", 100)
        basePillColor = Color.argb(
            alphaPct * 255 / 100,
            Color.red(baseColor), Color.green(baseColor), Color.blue(baseColor)
        )
        colorTarget = basePillColor
        bigW = minOf(dp(320), resources.displayMetrics.widthPixels - dp(32))
        bigH = dp(80)
        frac = 0f
        targetFrac = 0f
        cornerPx = smallH / 2f
        event = EVENT_NONE
        chargeShown = false
        replyMode = false
        hidden = false
        landHidden = false
        appHidden = false
        badgeSig = ""

        // Album art background + scrim
        val bArt = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }
        bgArt = bArt
        val bScrim = View(this).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.parseColor("#66000000"), Color.parseColor("#B3000000"))
            )
            visibility = View.GONE
        }
        bgScrim = bScrim

        // Info / warning text
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        label = tv

        // Charging row
        val cv = ChargeView(this)
        chargeView = cv
        val cTitle = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0AE"))
        }
        val cLevel = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
        }
        val cSub = smallText(11f, "#9AA0AE", false)
        chargeTitle = cTitle
        chargeLevel = cLevel
        chargeSub = cSub
        val cTextCol = vbox().apply {
            addView(cTitle)
            addView(cLevel)
            addView(cSub)
        }
        val cRow = hbox(Gravity.CENTER).apply {
            visibility = View.GONE
            addView(cv, lp(dp(54), dp(54)))
            addView(cTextCol, lp(WC, WC).apply { marginStart = dp(14) })
        }
        chargeRow = cRow

        // Notification card
        val nIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundOutline(0.26f)
        }
        notifIcon = nIcon
        val nApp = smallText(11f, "#9AA0AE", false)
        val nTitle = smallText(15f, "#FFFFFF", true)
        val nText = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#D0D4DC"))
            maxLines = 3
            ellipsize = TextUtils.TruncateAt.END
        }
        notifApp = nApp
        notifTitle = nTitle
        notifText = nText
        val nCol = vbox().apply {
            addView(nApp)
            addView(nTitle)
            addView(nText)
        }
        val nTop = hbox().apply {
            addView(nIcon, lp(dp(42), dp(42)))
            addView(nCol, lp(0, WC, 1f).apply { marginStart = dp(12) })
        }
        val nBar = BarView(this).apply { visibility = View.GONE }
        notifBar = nBar
        val rBtn = pillButton("Reply") { startReply() }
        val dBtn = pillButton("Dismiss") { dismissCurrent() }
        val cBtn = pillButton("Clear all") { clearAll() }
        replyBtn = rBtn
        clearBtn = cBtn
        val spacer = View(this)
        val btnRow = hbox().apply {
            visibility = View.GONE
            addView(rBtn, lp(WC, dp(34)))
            addView(spacer, lp(0, 1, 1f))
            addView(dBtn, lp(WC, dp(34)).apply { marginEnd = dp(6) })
            addView(cBtn, lp(WC, dp(34)))
        }
        notifBtnRow = btnRow
        val input = EditText(this).apply {
            hint = "Reply…"
            setHintTextColor(Color.parseColor("#80FFFFFF"))
            setTextColor(Color.WHITE)
            textSize = 14f
            setSingleLine(true)
            gravity = Gravity.CENTER_VERTICAL
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND
            setPadding(dp(14), 0, dp(14), 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#26FFFFFF"))
                cornerRadius = dp(19).toFloat()
            }
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) {
                    submitReply()
                    true
                } else {
                    false
                }
            }
            setOnKeyListener { _, keyCode, e ->
                if (keyCode == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) {
                    animateTo(0f)
                    true
                } else {
                    false
                }
            }
        }
        notifInput = input
        val sendBtn = pillButton("Send") { submitReply() }
        val inputRow = hbox().apply {
            visibility = View.GONE
            addView(input, lp(0, dp(38), 1f))
            addView(sendBtn, lp(WC, dp(38)).apply { marginStart = dp(8) })
        }
        notifInputRow = inputRow
        val nRow = vbox(Gravity.CENTER_VERTICAL).apply {
            setPadding(dp(16), dp(8), dp(16), dp(8))
            visibility = View.GONE
            addView(nTop, lp(MP, WC))
            addView(nBar, lp(MP, dp(6)).apply { topMargin = dp(8) })
            addView(btnRow, lp(MP, WC).apply { topMargin = dp(8) })
            addView(inputRow, lp(MP, WC).apply { topMargin = dp(8) })
        }
        notifRow = nRow

        // Music card
        val mArt = artView(0.22f)
        cardArt = mArt
        val mTitle = smallText(15f, "#FFFFFF", true)
        val mArtist = smallText(12f, "#D0D4DC", false)
        cardTitle = mTitle
        cardArtist = mArtist
        val mText = vbox().apply {
            addView(mTitle)
            addView(mArtist)
        }
        val mEq = EqView(this)
        cardEq = mEq
        val topRow = hbox().apply {
            addView(mArt, lp(dp(52), dp(52)))
            addView(mText, lp(0, WC, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            })
            addView(mEq, lp(dp(34), dp(28)))
        }
        val prevBtn = mediaBtn(MediaButton.PREV) {
            controller?.transportControls?.skipToPrevious()
        }
        val pBtn = mediaBtn(MediaButton.PLAY) {
            val tc = controller?.transportControls
            if (musicPlaying) tc?.pause() else tc?.play()
        }
        val nextBtn = mediaBtn(MediaButton.NEXT) {
            controller?.transportControls?.skipToNext()
        }
        playBtn = pBtn
        val controls = hbox(Gravity.CENTER).apply {
            addView(prevBtn, lp(dp(44), dp(44)))
            addView(pBtn, lp(dp(52), dp(52)).apply {
                marginStart = dp(16)
                marginEnd = dp(16)
            })
            addView(nextBtn, lp(dp(44), dp(44)))
        }
        val mCard = vbox(Gravity.CENTER).apply {
            setPadding(dp(18), dp(8), dp(18), dp(8))
            visibility = View.GONE
            addView(topRow, lp(MP, WC))
            addView(controls, lp(MP, WC).apply { topMargin = dp(4) })
        }
        musicCard = mCard

        // Alert card (incoming call, alarm, ongoing call)
        val aIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundOutline(0.5f)
        }
        alertIcon = aIcon
        val aSub = smallText(12f, "#9AA0AE", false)
        val aName = smallText(18f, "#FFFFFF", true)
        alertSub = aSub
        alertName = aName
        val aCol = vbox().apply {
            addView(aSub)
            addView(aName)
        }
        val aTop = hbox().apply {
            addView(aIcon, lp(dp(44), dp(44)))
            addView(aCol, lp(0, WC, 1f).apply { marginStart = dp(12) })
        }
        val aLeft = pillButton("", "#33FFFFFF") { alertTap(true) }
        val aRight = pillButton("", "#6A5CFF") { alertTap(false) }
        alertLeft = aLeft
        alertRight = aRight
        val aBtns = hbox().apply {
            addView(aLeft, lp(0, dp(42), 1f).apply { marginEnd = dp(6) })
            addView(aRight, lp(0, dp(42), 1f).apply { marginStart = dp(6) })
        }
        val aCard = vbox(Gravity.CENTER).apply {
            setPadding(dp(18), dp(10), dp(18), dp(12))
            visibility = View.GONE
            addView(aTop, lp(MP, WC))
            addView(aBtns, lp(MP, WC).apply { topMargin = dp(12) })
        }
        alertRow = aCard

        // Toast card (Bluetooth, ringer, clipboard)
        val tIcon = TextView(this).apply {
            textSize = 24f
            setTextColor(Color.WHITE)
        }
        val tTitle = smallText(15f, "#FFFFFF", true)
        val tSub = smallText(12f, "#D0D4DC", false)
        toastIcon = tIcon
        toastTitle = tTitle
        toastSub = tSub
        val tCol = vbox().apply {
            addView(tTitle)
            addView(tSub)
        }
        val tRow = hbox().apply {
            setPadding(dp(22), dp(8), dp(22), dp(8))
            visibility = View.GONE
            addView(tIcon, lp(WC, WC).apply { marginEnd = dp(14) })
            addView(tCol, lp(0, WC, 1f))
        }
        toastRow = tRow

        // Quick buttons (long press)
        val qT = circleBtn("🔦") { toggleTorch() }
        val qR = circleBtn("🔔") { toggleRinger() }
        val qS = circleBtn("⚙") {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
        qTorch = qT
        qRinger = qR
        val qRow = hbox(Gravity.CENTER).apply {
            visibility = View.GONE
            addView(qT, lp(dp(52), dp(52)).apply { marginEnd = dp(14) })
            addView(qR, lp(dp(52), dp(52)).apply { marginEnd = dp(14) })
            addView(qS, lp(dp(52), dp(52)))
        }
        quickRow = qRow

        // Brightness bar
        val bIcon = TextView(this).apply {
            textSize = 20f
            setTextColor(Color.WHITE)
        }
        barIcon = bIcon
        val bTxt = smallText(14f, "#FFFFFF", true)
        barText = bTxt
        val bView = BarView(this)
        barView = bView
        val bCol = vbox().apply {
            addView(bTxt, lp(MP, WC))
            addView(bView, lp(MP, dp(8)).apply { topMargin = dp(8) })
        }
        val bRow = hbox().apply {
            setPadding(dp(20), dp(8), dp(20), dp(8))
            visibility = View.GONE
            addView(bIcon, lp(WC, WC).apply { marginEnd = dp(14) })
            addView(bCol, lp(0, WC, 1f))
        }
        barRow = bRow

        // Compact items inside the small pill
        val artSize = maxOf(dp(12), smallH - dp(12))
        val eqH = maxOf(dp(8), (smallH * 0.45f).toInt())
        val cArt = artView(0.5f).apply { visibility = View.GONE }
        compactArt = cArt
        val cEq = EqView(this).apply { visibility = View.GONE }
        compactEq = cEq
        val cTimer = TextView(this).apply {
            textSize = 11f
            setTextColor(Color.parseColor("#30D158"))
            setTypeface(typeface, Typeface.BOLD)
            visibility = View.GONE
        }
        compactTimer = cTimer

        // Bottom strip (inside expanded pill)
        val stripView = hbox(Gravity.CENTER).apply { visibility = View.GONE }
        strip = stripView

        val shape = GradientDrawable().apply {
            setColor(basePillColor)
            cornerRadius = cornerPx
        }
        bg = shape

        val b = FrameLayout(this).apply {
            background = shape
            clipToOutline = true
            outlineProvider = pillOutline
            addView(bArt, FrameLayout.LayoutParams(MP, MP))
            addView(bScrim, FrameLayout.LayoutParams(MP, MP))
            addView(tv, FrameLayout.LayoutParams(MP, MP))
            addView(cRow, FrameLayout.LayoutParams(MP, MP))
            addView(nRow, FrameLayout.LayoutParams(MP, MP))
            addView(mCard, FrameLayout.LayoutParams(MP, MP))
            addView(aCard, FrameLayout.LayoutParams(MP, MP))
            addView(tRow, FrameLayout.LayoutParams(MP, MP))
            addView(qRow, FrameLayout.LayoutParams(MP, MP))
            addView(bRow, FrameLayout.LayoutParams(MP, MP))
            addView(
                cArt,
                FrameLayout.LayoutParams(
                    artSize, artSize, Gravity.START or Gravity.CENTER_VERTICAL
                ).apply { marginStart = dp(8) }
            )
            addView(
                cEq,
                FrameLayout.LayoutParams(
                    dp(24), eqH, Gravity.END or Gravity.CENTER_VERTICAL
                ).apply { marginEnd = dp(12) }
            )
            addView(
                cTimer,
                FrameLayout.LayoutParams(
                    WC, WC, Gravity.END or Gravity.CENTER_VERTICAL
                ).apply { marginEnd = dp(12) }
            )
            addView(
                stripView,
                FrameLayout.LayoutParams(
                    WC, dp(18), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply { bottomMargin = dp(4) }
            )
            setOnTouchListener { _, ev -> onPillTouch(ev) }
        }
        box = b

        val wlp = WindowManager.LayoutParams(
            smallW,
            smallH,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            x = offsetX
            y = top
        }
        params = wlp

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm?.addView(b, wlp)

        // Side badges under the pill: unseen notification icons, privacy dots in the middle
        val bl = hbox()
        val br = hbox()
        badgeL = bl
        badgeR = br
        val dc = dot("#30D158")
        val dm = dot("#FF9F0A")
        dotCam = dc
        dotMic = dm
        val centerDots = hbox(Gravity.CENTER).apply {
            addView(dc, lp(dp(6), dp(6)).apply { marginEnd = dp(3) })
            addView(dm, lp(dp(6), dp(6)))
        }
        val bb = FrameLayout(this).apply {
            visibility = View.GONE
            addView(bl, FrameLayout.LayoutParams(WC, MP, Gravity.START or Gravity.CENTER_VERTICAL))
            addView(br, FrameLayout.LayoutParams(WC, MP, Gravity.END or Gravity.CENTER_VERTICAL))
            addView(centerDots, FrameLayout.LayoutParams(WC, MP, Gravity.CENTER))
        }
        badgeBox = bb
        val bp = WindowManager.LayoutParams(
            smallW,
            dp(18),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = offsetX
            y = top + smallH + dp(2)
        }
        wm?.addView(bb, bp)

        renderPage()
        refreshMusicViews()
        updatePrivacy()
        applyHidden()

        handler.removeCallbacks(fgPoll)
        if (hideApps.isNotEmpty()) handler.post(fgPoll)
        if (ongoing != null) {
            handler.removeCallbacks(ticker)
            handler.post(ticker)
        }
    }

    private fun applyHidden() {
        landHidden = hideLand &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        hidden = landHidden || appHidden
        box?.alpha = if (hidden) 0f else 1f
        val wlp = params
        val v = box
        if (wlp != null && v != null) {
            if (hidden) {
                wlp.flags = wlp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            } else {
                wlp.flags = wlp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
            }
            if (v.isAttachedToWindow) wm?.updateViewLayout(v, wlp)
        }
        updateBadges()
    }

    // ---------- Touch ----------

    private fun isOnStrip(x: Float, y: Float): Boolean {
        val s = strip ?: return false
        if (s.visibility != View.VISIBLE) return false
        val pad = dp(4)
        return x >= s.left - pad && x <= s.right + pad && y >= s.top - pad && y <= s.bottom + pad
    }

    private fun onPillTouch(ev: MotionEvent): Boolean {
        when (ev.action) {
            MotionEvent.ACTION_OUTSIDE -> {
                if (targetFrac == 1f && !replyMode && event != EVENT_ALERT) {
                    handler.removeCallbacks(collapseRunnable)
                    animateTo(0f)
                }
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                lpFired = false
                downOnStrip = isOnStrip(ev.x, ev.y)
                if (targetFrac == 1f && !replyMode) handler.removeCallbacks(collapseRunnable)
                if (frac < 0.5f && event == EVENT_NONE && !hidden) {
                    handler.postDelayed(longPress, 450)
                }
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (abs(ev.x - downX) > dp(12) || abs(ev.y - downY) > dp(12)) {
                    handler.removeCallbacks(longPress)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (lpFired) {
                    lpFired = false
                    return true
                }
                val dx = ev.x - downX
                val dy = ev.y - downY
                val horiz = abs(dx) > dp(36) && abs(dx) > abs(dy) * 1.3f
                val up = dy < -dp(40) && abs(dy) > abs(dx) * 1.3f
                val down = dy > dp(40) && abs(dy) > abs(dx) * 1.3f
                if (frac > 0.5f && event == EVENT_PAGE && horiz) {
                    changePage(if (dx < 0) 1 else -1)
                } else if (frac > 0.5f && event == EVENT_PAGE && up) {
                    dismissCurrent()
                } else if (down && event != EVENT_ALERT && !replyMode) {
                    openShade()
                } else if (abs(dx) < dp(12) && abs(dy) < dp(12)) {
                    if (!downOnStrip) onPillClick()
                }
                if (targetFrac == 1f && !replyMode && event != EVENT_ALERT) {
                    scheduleCollapse(collapseDelay())
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                if (targetFrac == 1f && !replyMode && event != EVENT_ALERT) {
                    scheduleCollapse(collapseDelay())
                }
                return true
            }
        }
        return true
    }

    private fun onPillClick() {
        if (frac < 0.5f) {
            expand()
            return
        }
        if (event == EVENT_ALERT) {
            val o = alert?.open
            if (o != null) sendPi(o)
            return
        }
        if (replyMode) {
            animateTo(0f)
            return
        }
        if (event == EVENT_PAGE) {
            val pg = pageAt(curPage)
            if (pg != null && pg.kind == 0) {
                openMusicApp()
            } else if (pg != null && pg.kind == 3) {
                val o = ongoing?.open
                if (o != null) sendPi(o)
            } else if (pg?.item != null) {
                openItem(pg.item)
            }
        }
        animateTo(0f)
    }

    // ---------- Events ----------

    private fun setBigHeight(h: Int) {
        if (frac > 0.99f) animateHeightTo(h) else bigH = h
    }

    private fun expand() {
        handler.removeCallbacks(collapseRunnable)
        if (event == EVENT_NONE && pageCount() > 0) {
            event = EVENT_PAGE
            curPage = firstMusicOrNotif()
            markViewedCurrent()
            renderPage()
        }
        bigH = if (event == EVENT_PAGE) pageHeight() else dp(80)
        haptic(true)
        animateTo(1f)
        scheduleCollapse(collapseDelay())
    }

    private fun showPages(page: Int) {
        event = EVENT_PAGE
        curPage = page
        chargeShown = false
        renderPage()
        setBigHeight(pageHeight())
        handler.removeCallbacks(collapseRunnable)
        haptic(true)
        animateTo(1f)
        scheduleCollapse(collapseDelay())
    }

    private fun startEvent(e: Int) {
        if (box == null || replyMode) return
        if (targetFrac == 1f && event == EVENT_ALERT) return
        event = e
        chargeShown = false
        updateChargeTexts()
        setBigHeight(dp(80))
        handler.removeCallbacks(collapseRunnable)
        haptic(true)
        animateTo(1f)
        scheduleCollapse(LONG_COLLAPSE_MS)
    }

    private fun updateChargeTexts() {
        val unplug = event == EVENT_UNPLUG
        chargeTitle?.text = if (unplug) "Unplugged" else "Charging"
        chargeTitle?.setTextColor(Color.parseColor(if (unplug) "#FF9F43" else "#3DDC84"))
        chargeLevel?.text = "${batteryLevel()}%"
        chargeSub?.text = if (unplug) "" else chargeInfo()
    }

    private fun scheduleCollapse(ms: Long) {
        handler.removeCallbacks(collapseRunnable)
        handler.postDelayed(collapseRunnable, ms)
    }

    private fun animateHeightTo(h: Int) {
        heightAnim?.cancel()
        if (bigH == h) return
        heightAnim = ValueAnimator.ofInt(bigH, h).apply {
            duration = 220
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                bigH = it.animatedValue as Int
                applyLayout()
            }
            start()
        }
    }

    private fun animateTo(target: Float) {
        if (box == null || params == null) return
        if (target == 0f && replyMode) exitReply()
        targetFrac = target
        animator?.cancel()
        val ip: TimeInterpolator = if (target == 0f) {
            AccelerateDecelerateInterpolator()
        } else {
            OvershootInterpolator(1.05f)
        }
        animator = ValueAnimator.ofFloat(frac, target).apply {
            duration = if (target == 0f) 300L else ANIM_MS
            interpolator = ip
            addUpdateListener { a ->
                frac = a.animatedValue as Float
                applyLayout()
                renderContent()
            }
            start()
        }
    }

    private fun applyLayout() {
        val wlp = params ?: return
        wlp.width = (smallW + (bigW - smallW) * frac).toInt()
        wlp.height = (smallH + (bigH - smallH) * frac).toInt()
        cornerPx = minOf(wlp.height / 2f, dp(34).toFloat())
        bg?.cornerRadius = cornerPx
        val v = box ?: return
        v.invalidateOutline()
        if (v.isAttachedToWindow) wm?.updateViewLayout(v, wlp)
    }

    private fun showOnly(which: View?) {
        val all = listOf<View?>(
            label, chargeRow, musicCard, notifRow, alertRow, toastRow, quickRow, barRow
        )
        for (v in all) {
            v?.visibility = if (v != null && v === which) View.VISIBLE else View.GONE
        }
    }

    private fun applyTint() {
        var c = basePillColor
        if (matchColors) {
            var t: Int? = null
            if (event == EVENT_PAGE) {
                val pg = pageAt(curPage)
                if (pg != null) t = if (pg.kind == 0) artTint else pg.item?.tint
            } else if (event == EVENT_NONE && musicPlaying) {
                t = artTint
            }
            if (t != null) {
                c = Color.argb(Color.alpha(basePillColor), Color.red(t), Color.green(t), Color.blue(t))
            }
        }
        if (c != colorTarget) {
            colorTarget = c
            bg?.setColor(c)
        }
    }

    private fun renderContent() {
        if (targetFrac == 0f && frac < 0.3f && event != EVENT_NONE) {
            event = EVENT_NONE
            chargeView?.stop()
            exitReply()
        }
        if (event == EVENT_PAGE) {
            curPage = curPage.coerceIn(0, maxOf(0, pageCount() - 1))
            if (pageCount() == 0 && targetFrac == 1f) {
                handler.post { animateTo(0f) }
            }
        }
        applyTint()

        val calling = ongoing != null
        val showCompact = frac < 0.3f && event == EVENT_NONE && !hidden
        compactArt?.visibility =
            if (showCompact && musicPlaying) View.VISIBLE else View.GONE
        compactEq?.visibility =
            if (showCompact && musicPlaying && !calling) View.VISIBLE else View.GONE
        compactTimer?.visibility =
            if (showCompact && calling) View.VISIBLE else View.GONE

        val kind = if (event == EVENT_PAGE) (pageAt(curPage)?.kind ?: -1) else -1
        val artBg = kind == 0 && musicArt != null
        bgArt?.visibility = if (artBg) View.VISIBLE else View.GONE
        bgScrim?.visibility = if (artBg) View.VISIBLE else View.GONE
        updateBadges()

        if (frac <= 0.6f) {
            showOnly(null)
            strip?.visibility = View.GONE
            return
        }

        strip?.visibility =
            if (event == EVENT_PAGE && notifs.isNotEmpty()) View.VISIBLE else View.GONE

        when (event) {
            EVENT_PLUG, EVENT_UNPLUG -> {
                showOnly(chargeRow)
                if (!chargeShown) {
                    chargeShown = true
                    chargeView?.play(batteryLevel(), event == EVENT_UNPLUG)
                }
            }
            EVENT_PAGE -> {
                showOnly(
                    when (kind) {
                        0 -> musicCard
                        1, 2 -> notifRow
                        3 -> alertRow
                        else -> null
                    }
                )
            }
            EVENT_ALERT -> showOnly(alertRow)
            EVENT_TOAST -> showOnly(toastRow)
            EVENT_QUICK -> showOnly(quickRow)
            EVENT_BAR -> showOnly(barRow)
            EVENT_WARN -> {
                showOnly(label)
                label?.text = warnText
                label?.setTextColor(Color.parseColor("#FF6B6B"))
            }
            else -> {
                val first = label?.visibility != View.VISIBLE
                showOnly(label)
                if (first) showInfo()
            }
        }
    }

    private fun showInfo() {
        val level = batteryLevel()
        label?.setTextColor(Color.WHITE)
        label?.text = if (charging) {
            "⚡ Charging   $level%"
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "🔋 $level%   •   $time"
        }
        label?.visibility = View.VISIBLE
    }

    private fun removePill() {
        handler.removeCallbacks(collapseRunnable)
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(fgPoll)
        animator?.cancel()
        heightAnim?.cancel()
        chargeView?.stop()
        cardEq?.setActive(false)
        compactEq?.setActive(false)
        box?.let {
            if (it.isAttachedToWindow) wm?.removeView(it)
        }
        badgeBox?.let {
            if (it.isAttachedToWindow) wm?.removeView(it)
        }
        box = null
        bg = null
        bgArt = null
        bgScrim = null
        label = null
        chargeRow = null
        chargeView = null
        chargeTitle = null
        chargeLevel = null
        chargeSub = null
        musicCard = null
        cardArt = null
        cardTitle = null
        cardArtist = null
        cardEq = null
        playBtn = null
        compactArt = null
        compactEq = null
        compactTimer = null
        notifRow = null
        notifIcon = null
        notifApp = null
        notifTitle = null
        notifText = null
        notifBar = null
        notifBtnRow = null
        notifInputRow = null
        notifInput = null
        replyBtn = null
        clearBtn = null
        alertRow = null
        alertIcon = null
        alertSub = null
        alertName = null
        alertLeft = null
        alertRight = null
        toastRow = null
        toastIcon = null
        toastTitle = null
        toastSub = null
        quickRow = null
        qTorch = null
        qRinger = null
        barRow = null
        barIcon = null
        barText = null
        barView = null
        strip = null
        badgeBox = null
        badgeL = null
        badgeR = null
        dotCam = null
        dotMic = null
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(ticker)
        removePill()
        controller?.unregisterCallback(mediaCallback)
        controller = null
        try {
            msm?.removeOnActiveSessionsChangedListener(sessionsListener)
        } catch (e: Exception) {
        }
        mediaReady = false
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
        }
        try {
            contentResolver.unregisterContentObserver(brightnessObserver)
        } catch (e: Exception) {
        }
        try {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.removePrimaryClipChangedListener(clipListener)
        } catch (e: Exception) {
        }
        try {
            val cam = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cam.unregisterAvailabilityCallback(camCallback)
            cam.unregisterTorchCallback(torchCallback)
        } catch (e: Exception) {
        }
        try {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.unregisterAudioRecordingCallback(recCallback)
        } catch (e: Exception) {
        }
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        if (prefs.getBoolean("headsup_off", false)) {
            val ctx = applicationContext
            Thread { ShizukuHelper.setHeadsUp(ctx, true) }.start()
        }
        running = false
        super.onDestroy()
    }
}
