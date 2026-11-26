package com.example.islandpro

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.ActivityOptions
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
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.telecom.TelecomManager
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
import android.view.animation.LinearInterpolator
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
import kotlin.math.exp
import kotlin.math.sin

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
    val mono: Bitmap?,
    val title: String,
    val lines: List<String>,
    val time: Long,
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?,
    val live: Boolean,
    val progress: Int,
    val progressMax: Int,
    val tint: Int?,
    val chrono: Boolean,
    val chronoWhen: Long,
    val chronoDown: Boolean,
    val actions: List<Notification.Action> = emptyList()
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
    val startMs: Long,
    val pkg: String = ""
)

// kind: 0 music, 1 notification, 2 live / ongoing, 3 ongoing call
internal class Page(val kind: Int, val item: NotifItem?)

class IslandService : Service() {

    companion object {
        @Volatile
        var running = false

        @Volatile
        var mediaReady = false

        @Volatile
        var instance: IslandService? = null

        const val ACTION_PREVIEW = "com.example.islandpro.PREVIEW_EDGE"
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
        private const val EVENT_COMPACT = 9
        private const val COMPACT_MS = 2400L
    }

    private var wm: WindowManager? = null
    private var box: FrameLayout? = null
    private var bg: GradientDrawable? = null
    private var bgArt: ImageView? = null
    private var bgScrim: View? = null
    private var infoRow: LinearLayout? = null
    private var infoIcon: GlyphView? = null
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
    private var compactCall: PhoneIconView? = null
    private var seekView: SeekView? = null
    private var seekRow: LinearLayout? = null
    private var seekElapsed: TextView? = null
    private var seekTotal: TextView? = null
    private var callBtnsBox: LinearLayout? = null
    private var callL: CallButton? = null
    private var callR: CallButton? = null
    private var alertBtnRow: LinearLayout? = null
    private var rippleView: RippleView? = null
    private var rippleParams: WindowManager.LayoutParams? = null
    private var ripplePad = 0
    private var rippleActive = false
    private var jellyW = 1f
    private var jellyH = 1f
    private var jellyAnim: ValueAnimator? = null
    private var audioViz: AudioViz? = null
    private var musicDuration = 0L
    private var seeking = false
    private var seekHoldUntil = 0L
    private var musicTickOn = false
    private var artAccent = Color.WHITE
    private var notifRow: LinearLayout? = null
    private var notifIcon: ImageView? = null
    private var notifApp: TextView? = null
    private var notifTitle: TextView? = null
    private var notifText: TextView? = null
    private var notifCount: TextView? = null
    private var lightCard = false
    private var notifBar: BarView? = null
    private var notifBtnRow: LinearLayout? = null
    private var notifInputRow: LinearLayout? = null
    private var notifInput: EditText? = null
    private var replyBtn: TextView? = null
    private var clearBtn: TextView? = null
    private var stackRow: LinearLayout? = null
    private val peekIcons = ArrayList<ImageView>()
    private val peekTexts = ArrayList<TextView>()
    private var peekTargets: List<NotifItem> = emptyList()
    private var alertRow: LinearLayout? = null
    private var alertIcon: ImageView? = null
    private var alertSub: TextView? = null
    private var alertName: TextView? = null
    private var alertLeft: TextView? = null
    private var alertRight: TextView? = null
    private var toastRow: LinearLayout? = null
    private var toastIcon: GlyphView? = null
    private var toastTitle: TextView? = null
    private var toastSub: TextView? = null
    private var quickRow: LinearLayout? = null
    private var qTorch: GlyphView? = null
    private var qRinger: GlyphView? = null
    private var qTorchTile: FrameLayout? = null
    private var compactRow: LinearLayout? = null
    private var compIcon: ImageView? = null
    private var compText: TextView? = null
    private var actRow: LinearLayout? = null
    private var actBtn1: TextView? = null
    private var actBtn2: TextView? = null
    private var compactKey: String? = null
    private var compactIdx = 0
    // true = notification first shows as a small pill (icon + title), tap to expand
    private val compactNotif = true
    private var qRingerTile: FrameLayout? = null
    private var barRow: LinearLayout? = null
    private var barIcon: GlyphView? = null
    private var barText: TextView? = null
    private var barView: BarView? = null
    private var strip: LinearLayout? = null
    private var badgeBox: FrameLayout? = null
    private var winL: FrameLayout? = null
    private var winR: FrameLayout? = null
    private var dotCam: View? = null
    private var dotMic: View? = null
    private var chargeBadge: LinearLayout? = null
    private var chargeBadgeText: TextView? = null
    private var glowView: GlowView? = null
    private var glowParams: WindowManager.LayoutParams? = null
    private var glowPad = 0
    private var glowActive = false
    private var params: WindowManager.LayoutParams? = null
    private var animator: ValueAnimator? = null
    private var heightAnim: ValueAnimator? = null
    private var springAnim: ValueAnimator? = null
    private var pressAnim: ValueAnimator? = null

    private var edgeOn = true
    private var edgeNotif = true
    private var edgeCall = true
    private var edgeC1 = 0xFF3987FF.toInt()
    private var edgeC2 = 0xFF55D9FF.toInt()
    private var edgeMs = 2500L
    private var edgeThick = 3
    private var edgeGlow = 12
    private var edgeStyle = 0

    private var smallW = 0
    private var smallH = 0
    private var bigW = 0
    private var bigH = 0
    private var baseX = 0
    private var baseTop = 0
    private var yShift = 0
    private var dragX = 0f
    private var dragY = 0f
    private var pressScale = 1f
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
private val soundPlayed = HashSet<String>()
private val selfCancelled = HashSet<String>()

    private var torchOn = false
    private var micActive = false
    private val camUnavail = HashSet<String>()

    private var msm: MediaSessionManager? = null
    private val ctls = ArrayList<MediaController>()
    private val ctlCallbacks = ArrayList<MediaController.Callback>()
    private var lastPlayingPkg: String? = null
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
            val o = ongoing
            val c = lives.firstOrNull { it.chrono }
            if (o == null && c == null) return
            val tv = compactTimer
            if (o != null) {
                tv?.text = fmtElapsed(System.currentTimeMillis() - o.startMs)
                tv?.setTextColor(Color.WHITE)
            } else if (c != null) {
                tv?.text = chronoText(c)
                tv?.setTextColor(Color.parseColor("#FF9F0A"))
            }
            if (o != null && alertRow?.visibility == View.VISIBLE && event != EVENT_ALERT) {
                alertSub?.text = "Call • " + fmtElapsed(System.currentTimeMillis() - o.startMs)
            }
            val cur = pageAt(curPage)?.item
            if (event == EVENT_PAGE && cur != null && cur.chrono) {
                notifText?.text = chronoText(cur)
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

    private val musicTick = object : Runnable {
        override fun run() {
            val visible = box != null && frac > 0.6f && event == EVENT_PAGE &&
                pageAt(curPage)?.kind == 0
            if (!visible) {
                musicTickOn = false
                return
            }
            updateSeek()
            handler.postDelayed(this, 250)
        }
    }

    private val pillOutline = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
        }
    }

    private val sessionsListener =
        MediaSessionManager.OnActiveSessionsChangedListener { list -> watchControllers(list) }

    private val brightnessObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            try {
                val v = Settings.System.getInt(contentResolver, Settings.System.SCREEN_BRIGHTNESS)
                showBar(GlyphView.SUN, "Brightness", v / 255f)
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
                    updateChargeBadge()
                    startEvent(EVENT_PLUG)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    charging = false
                    updateChargeBadge()
                    startEvent(EVENT_UNPLUG)
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    if (event == EVENT_PLUG || event == EVENT_UNPLUG) {
                        updateChargeTexts()
                    } else if (event == EVENT_NONE && infoRow?.visibility == View.VISIBLE) {
                        showInfo()
                    }
                    updateChargeBadge()
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
        val preview = intent?.action == ACTION_PREVIEW
        if (preview && box != null) {
            edgeLight(edgeMs)
            return START_STICKY
        }
        buildPill()
        setupMedia()
        applyPopupSetting()
        if (preview) handler.postDelayed({ edgeLight(edgeMs) }, 700)
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

    private fun popupsHidden(): Boolean {
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        return prefs.getBoolean("hide_popups", true) && ShizukuHelper.hasPermission()
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

    private fun chronoText(i: NotifItem): String {
        val now = System.currentTimeMillis()
        val ms = if (i.chronoDown) i.chronoWhen - now else now - i.chronoWhen
        return fmtElapsed(ms)
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
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

    // Sends a PendingIntent with permission to open an activity from the background
    private fun fire(pi: PendingIntent): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= 34) {
                val o = ActivityOptions.makeBasic()
                o.setPendingIntentBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                )
                pi.send(this, 0, null, null, null, null, o.toBundle())
            } else {
                pi.send()
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun sendPi(pi: PendingIntent) {
        fire(pi)
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

    // ---------- Edge Lighting ----------

    private fun edgeLight(duration: Long) {
        val gv = glowView ?: return
        if (box == null || hidden) return
        glowActive = true
        gv.radius = cornerPx
        updateGlowLayout()
        gv.start(duration)
    }

    private fun updateGlowLayout() {
        if (!glowActive) return
        val gv = glowView ?: return
        val gp = glowParams ?: return
        val wlp = params ?: return
        gp.width = wlp.width + 2 * glowPad
        gp.height = wlp.height + 2 * glowPad
        gp.x = wlp.x
        gp.y = wlp.y - glowPad
        gv.radius = cornerPx
        if (gv.isAttachedToWindow) wm?.updateViewLayout(gv, gp)
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
private val dialerPkgs = setOf(
        "com.google.android.dialer", "com.samsung.android.incallui",
        "com.android.dialer", "com.android.server.telecom",
        "com.samsung.android.dialer", "com.android.incallui", "com.android.phone"
    )

    private fun looksLikeCall(n: Notification): Boolean {
        if (n.fullScreenIntent == null) return false
        val acts = n.actions ?: return false
        var ans = false
        var dec = false
        for (a in acts) {
            val t = a.title?.toString()?.lowercase(Locale.ROOT) ?: continue
            if (t.contains("answer") || t.contains("accept") || t.contains("receive") ||
                t.contains("pick")
            ) ans = true
            if (t.contains("decline") || t.contains("reject") || t.contains("hang") ||
                t.contains("dismiss")
            ) dec = true
        }
        return ans && dec
    }

    private val soundApps = setOf(
        "com.imo.android.imoim", "com.imo.android.imoimbeta",
        "com.whatsapp", "com.whatsapp.w4b",
        "com.facebook.katana", "com.facebook.lite",
        "com.facebook.orca", "com.facebook.mlite",
        "com.google.android.apps.messaging", "com.android.mms",
        "com.samsung.android.messaging", "com.miui.mms",
        "com.google.android.gm", "com.google.android.apps.gmail",
        "com.android.email", "com.microsoft.office.outlook",
        "org.telegram.messenger", "com.viber.voip"
    )

    private fun shouldPlaySound(
        sbn: StatusBarNotification,
        n: Notification,
        cat: String?
    ): Boolean {
        val okApp = soundApps.contains(sbn.packageName) ||
            cat == Notification.CATEGORY_MESSAGE ||
            cat == Notification.CATEGORY_EMAIL ||
            cat == Notification.CATEGORY_SOCIAL
        if (!okApp) return false
        if (MusicListener.importanceOf(sbn) < NotificationManager.IMPORTANCE_DEFAULT) return false
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return false
        if (System.currentTimeMillis() - sbn.postTime > 5000) return false
        return true
    }
    fun onNotification(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        val ex = n.extras ?: return
        val cat = n.category
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)

        val tmpl0 = ex.getString(Notification.EXTRA_TEMPLATE) ?: ""
        if (cat == Notification.CATEGORY_CALL || tmpl0.contains("CallStyle") || looksLikeCall(n)) {
            if (handleCall(sbn, n, ex)) return
        }
        if (cat == Notification.CATEGORY_ALARM && !chrono) {
            val a = n.actions
            if (a != null && a.isNotEmpty()) handleAlarm(sbn, n, ex)
            return
        }

        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        val blocked = prefs.getStringSet("blocked_apps", null)
        if (blocked != null && blocked.contains(sbn.packageName)) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val template = ex.getString(Notification.EXTRA_TEMPLATE) ?: ""
        if (template.contains("MediaStyle")) return
        if (cat == Notification.CATEGORY_TRANSPORT) return

        val pmax = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val ongoingFlag = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val live = pmax > 0 || ongoingFlag || chrono
        if (!live && n.fullScreenIntent != null) return

        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
        var title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val lines = extractLines(ex)
        if (title.isEmpty() && lines.isEmpty()) {
            if (live) title = appName else return
        }
        val progress = ex.getInt(Notification.EXTRA_PROGRESS, 0)

        if (!live && prefs.getBoolean("hide_sys", false)) {
            removeFromSystem(sbn.key)
        }

        val sig = title.hashCode() * 31 + lines.joinToString("|").hashCode() + progress * 7
if (seen[sbn.key] == sig) return
if (seen.size > 100) seen.clear()
seen[sbn.key] = sig

if (!live && shouldPlaySound(sbn, n, cat)) {
            NotificationSound.play(this)
}

        val icon = appIcon(sbn.packageName)
        val mono = try {
            val d = n.smallIcon?.loadDrawable(this)
            if (d != null) drawableToBitmap(d, dp(32)) else null
        } catch (e: Exception) {
            null
        }

        val item = NotifItem(
            sbn.key, sbn.packageName, appName, icon, mono,
            title, lines, sbn.postTime, n.contentIntent,
            if (live) null else findReplyAction(n),
            live, progress, pmax, avgColor(icon),
            chrono, n.`when`, ex.getBoolean("android.chronometerCountDown", false),
            if (live) emptyList() else (n.actions
                ?.filter { it.actionIntent != null && it.remoteInputs.isNullOrEmpty() && !it.title.isNullOrBlank() }
                ?.take(2) ?: emptyList())
        )

        if (live) {
            val idx = lives.indexOfFirst { it.key == item.key }
            if (idx >= 0) {
                lives[idx] = item
            } else {
                lives.add(0, item)
                while (lives.size > 8) lives.removeAt(lives.size - 1)
            }
            if (chrono) startTicker()
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
        if (edgeNotif) edgeLight(edgeMs)
        if (replyMode || busy()) {
            renderPage()
            renderContent()
            return
        }
        dropEffect()
        if (compactNotif) {
            showCompactNotif()
        } else {
            showPages(if (hasMusic()) 1 else 0)
        }
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
    seen.remove(sbn.key)
    soundPlayed.remove(sbn.key)
        if (alert?.key == sbn.key) endAlert()
        if (ongoing?.key == sbn.key) {
            ongoing = null
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
        if (pi != null) ok = fire(pi)
        if (!ok) launchApp(item.pkg)
        notifs.remove(item)
        lives.remove(item)
    }

    private fun openBadge(item: NotifItem) {
        openItem(item)
        renderPage()
        updateBadges()
    }

    private fun openMusicApp() {
        val c = controller ?: return
        val pi = c.sessionActivity
        if (pi != null && fire(pi)) return
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

    private fun telecomAnswer(accept: Boolean): Boolean {
        if (checkSelfPermission("android.permission.ANSWER_PHONE_CALLS") !=
            PackageManager.PERMISSION_GRANTED
        ) return false
        return try {
            val tm = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            if (accept) {
                tm.acceptRingingCall()
                true
            } else if (Build.VERSION.SDK_INT >= 28) {
                tm.endCall()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun handleCall(sbn: StatusBarNotification, n: Notification, ex: Bundle): Boolean {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val mode = am.mode
        val ct = ex.getInt("android.callType", 0)
        val chrono = ex.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false)
        val ongoingFlag = (n.flags and Notification.FLAG_ONGOING_EVENT) != 0
        val inCallMode = mode == AudioManager.MODE_IN_CALL ||
            mode == AudioManager.MODE_IN_COMMUNICATION
        val incoming = ct == 1 || (ct == 0 && (
            n.fullScreenIntent != null ||
                mode == AudioManager.MODE_RINGTONE ||
                (ongoingFlag && !chrono && !inCallMode)
            ))
        val inCall = !incoming && (ct == 2 || (ct == 0 && (chrono || inCallMode)))
        if (!incoming && !inCall) return false

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val name = title.ifEmpty {
            ex.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "Unknown caller"
        }
        val open = n.contentIntent ?: n.fullScreenIntent
        val icon = appIcon(sbn.packageName)
        val acts = n.actions

        val appLabel = try {
            packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(sbn.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            sbn.packageName
        }
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
                sbn.key, 0, name,
                if (dialerPkgs.contains(sbn.packageName)) "Incoming call"
                else "$appLabel • Incoming call",
                icon,
                "Decline", "#FF453A", dec,
                "Accept", "#30D158", ans,
                open, System.currentTimeMillis(), sbn.packageName
            )
            callL?.muted = false
            if (box != null) showAlert()
            return true
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
            "End call", "#FF453A", hang,
            open, start, sbn.packageName
        )
        if (alert?.key == sbn.key) {
            alert = null
            if (event == EVENT_ALERT) animateTo(0f)
        }
        startTicker()
        if (box != null) {
            renderPage()
            renderContent()
        }
        return true
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
        val isCall = a.kind == 0 || a.kind == 2
        callBtnsBox?.visibility = if (isCall) View.VISIBLE else View.GONE
        alertBtnRow?.visibility = if (isCall) View.GONE else View.VISIBLE
        if (isCall) {
            if (a.kind == 0) {
                callL?.kind = CallButton.DECLINE
                callL?.visibility = View.VISIBLE
                callR?.kind = CallButton.ACCEPT
            } else {
                callL?.kind = CallButton.MUTE
                callL?.visibility = if (a.leftLabel.isEmpty()) View.GONE else View.VISIBLE
                callR?.kind = CallButton.DECLINE
            }
        } else {
            setBtn(alertLeft, a.leftLabel, a.leftColor)
            setBtn(alertRight, a.rightLabel, a.rightColor)
        }
    }

    private fun showAlert() {
        val a = alert ?: return
        bindAlert(a)
        handler.removeCallbacks(collapseRunnable)
        if (replyMode) exitReply()
        // When the phone's own banner is not hidden it sits on top of the pill,
        // so the card is moved just below the banner to stay tappable
        yShift = if (popupsHidden()) 0 else dp(136)
        event = EVENT_ALERT
        setBigHeight(if (a.kind == 1) dp(124) else dp(84))
        haptic(true)
        animateTo(1f)
        renderContent()
        if (edgeCall) edgeLight(6000L)
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
        if (a.kind == 0) {
            // left = Decline, right = Accept
            val accept = !left
            val sys = a.pkg.isEmpty() || dialerPkgs.contains(a.pkg)
            val done = if (sys) telecomAnswer(accept) else false
            val pi = if (left) a.left else a.right
            if (pi != null && (accept || !done)) sendPi(pi)
            alert = null
        } else if (a.kind == 2) {
            if (left) {
                val pi = a.left
                if (pi != null) sendPi(pi)
                callL?.let { it.muted = !it.muted }
            } else {
                val sys2 = a.pkg.isEmpty() || dialerPkgs.contains(a.pkg)
                if (!sys2 || !telecomAnswer(false)) {
                    val pi = a.right
                    if (pi != null) sendPi(pi)
                }
            }
        } else {
            val pi = if (left) a.left else a.right
            if (pi != null) sendPi(pi)
            if (event == EVENT_ALERT) alert = null
        }
        haptic(true)
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

    // ---------- Media (watches every music app) ----------

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
            watchControllers(m.getActiveSessions(cn))
        } catch (e: SecurityException) {
            mediaReady = false
        }
    }

    private fun clearControllers() {
        for (i in ctls.indices) {
            try {
                ctls[i].unregisterCallback(ctlCallbacks[i])
            } catch (e: Exception) {
            }
        }
        ctls.clear()
        ctlCallbacks.clear()
    }

    private fun watchControllers(list: List<MediaController>?) {
        clearControllers()
        if (list != null) {
            for (c in list) {
                val cb = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        if (state?.state == PlaybackState.STATE_PLAYING) {
                            lastPlayingPkg = c.packageName
                        }
                        reselect()
                    }

                    override fun onMetadataChanged(metadata: MediaMetadata?) {
                        reselect()
                    }
                }
                c.registerCallback(cb, handler)
                ctls.add(c)
                ctlCallbacks.add(cb)
                if (lastPlayingPkg == null &&
                    c.playbackState?.state == PlaybackState.STATE_PLAYING
                ) {
                    lastPlayingPkg = c.packageName
                }
            }
        }
        reselect()
    }

    private fun reselect() {
        val playing = ctls.filter { it.playbackState?.state == PlaybackState.STATE_PLAYING }
        val cur = controller
        val pick: MediaController? = when {
            playing.isNotEmpty() ->
                playing.firstOrNull { it.packageName == lastPlayingPkg } ?: playing[0]
            cur != null && ctls.any { it.sessionToken == cur.sessionToken } ->
                ctls.first { it.sessionToken == cur.sessionToken }
            else -> ctls.firstOrNull()
        }
        controller = pick
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
        musicDuration = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
        if (art !== musicArt) {
            musicArt = art
            artTint = avgColor(art)
            artAccent = accentColor(art)
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
        cardEq?.accent = artAccent
        compactEq?.accent = artAccent
        cardEq?.setActive(musicPlaying)
        compactEq?.setActive(musicPlaying)
        syncViz()
        updateSeek()
        renderContent()
    }

    private fun accentColor(bmp: Bitmap?): Int {
        if (bmp == null) return Color.WHITE
        return try {
            val p = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
            blendColor(Color.rgb(Color.red(p), Color.green(p), Color.blue(p)), Color.WHITE, 0.5f)
        } catch (e: Exception) {
            Color.WHITE
        }
    }

    // Live audio bars: needs the microphone-free "record audio" permission for the visualizer
    private fun syncViz() {
        val want = musicPlaying && box != null && !hidden
        if (want) {
            if (audioViz == null) {
                audioViz = AudioViz(this) { lv ->
                    cardEq?.feed(lv)
                    compactEq?.feed(lv)
                }
            }
            audioViz?.start()
        } else {
            audioViz?.stop()
        }
    }

    private fun musicPositionMs(): Long {
        val st = controller?.playbackState ?: return 0L
        var p = st.position
        if (p < 0) p = 0
        if (st.state == PlaybackState.STATE_PLAYING) {
            val dt = SystemClock.elapsedRealtime() - st.lastPositionUpdateTime
            p += (dt * st.playbackSpeed).toLong()
        }
        if (musicDuration > 0) p = p.coerceIn(0L, musicDuration)
        return maxOf(0L, p)
    }

    private fun updateSeek() {
        val sv = seekView ?: return
        val has = musicDuration > 0
        seekRow?.visibility = if (has) View.VISIBLE else View.INVISIBLE
        if (!has || seeking) return
        if (SystemClock.uptimeMillis() < seekHoldUntil) return
        val p = musicPositionMs()
        sv.progress = p / musicDuration.toFloat()
        seekElapsed?.text = fmtElapsed(p)
        seekTotal?.text = fmtElapsed(musicDuration)
    }

    private fun startMusicTick() {
        if (musicTickOn) return
        musicTickOn = true
        handler.post(musicTick)
    }

    private fun seekTo(ms: Long) {
        controller?.transportControls?.seekTo(ms)
        seekHoldUntil = SystemClock.uptimeMillis() + 900L
    }

    // Jump forward / back by delta ms (long press on next / previous)
    private fun seekBy(delta: Long) {
        if (controller == null) return
        var np = musicPositionMs() + delta
        if (np < 0) np = 0
        if (musicDuration > 0 && np > musicDuration) np = musicDuration
        seekTo(np)
        if (musicDuration > 0) {
            seekView?.progress = np / musicDuration.toFloat()
            seekElapsed?.text = fmtElapsed(np)
        }
    }

    // ---------- Small events ----------

    private fun showToast(g: Int, title: String, sub: String) {
        if (box == null || hidden || replyMode || busy()) return
        toastIcon?.glyph = g
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
        showToast(GlyphView.CLIP, "Copied", preview.replace("\n", " "))
    }

    private fun onRinger() {
        updateQuickIcons()
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> showToast(GlyphView.MUTE, "Silent", "")
            AudioManager.RINGER_MODE_VIBRATE -> showToast(GlyphView.VIBRATE, "Vibrate", "")
            else -> showToast(GlyphView.BELL, "Sound on", "")
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
        showToast(GlyphView.HEADSET, name, sub)
    }

    private fun showBar(g: Int, name: String, value: Float) {
        if (!barsOn || box == null || hidden || replyMode || busy()) return
        barIcon?.glyph = g
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
        warnText = "Battery low  $level%"
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
            val back =
                c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
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
        qRinger?.glyph = when (am.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> GlyphView.MUTE
            AudioManager.RINGER_MODE_VIBRATE -> GlyphView.VIBRATE
            else -> GlyphView.BELL
        }
        qTorch?.filled = torchOn
        styleTile(qTorchTile, qTorch, torchOn)
        styleTile(qRingerTile, qRinger, am.ringerMode == AudioManager.RINGER_MODE_NORMAL)
    }

    // One UI 9.5 style quick tile: dark glass circle, white circle when active
    private fun styleTile(tile: FrameLayout?, g: GlyphView?, on: Boolean) {
        (tile?.background as? GradientDrawable)
            ?.setColor(Color.parseColor(if (on) "#F2F2F7" else "#40FFFFFF"))
        g?.tint = Color.parseColor(if (on) "#1C1C1E" else "#FFFFFF")
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
            showToast(GlyphView.INFO, "Shizuku needed", "Grant it in the Island app")
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

    private fun updateChargeBadge() {
        chargeBadgeText?.text = "Charging  ${batteryLevel()}%"
        chargeBadge?.visibility = if (charging) View.VISIBLE else View.GONE
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

    // Older notifications that peek out behind the current card (the stack)
    private fun behindItems(): List<NotifItem> {
        val pg = pageAt(curPage)
        val cur = pg?.item
        if (pg == null || pg.kind != 1 || cur == null) return emptyList()
        val idx = notifs.indexOf(cur)
        if (idx < 0) return emptyList()
        val out = ArrayList<NotifItem>()
        var i = idx + 1
        while (i < notifs.size && out.size < 2) {
            out.add(notifs[i])
            i++
        }
        return out
    }

    private fun pageHeight(): Int {
        val extra = if (notifs.isNotEmpty()) dp(18) else 0
        val pg = pageAt(curPage) ?: return dp(80)
        val base = when (pg.kind) {
            0 -> dp(164)
            2 -> dp(100)
            3 -> dp(84)
            else -> dp(93) + dp(17) * minOf(3, maxOf(1, pg.item?.lines?.size ?: 1)) +
                dp(25) * behindItems().size +
                (if (pg.item?.actions?.isNotEmpty() == true) dp(42) else 0)
        }
        return base + extra
    }

    private fun blendColor(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        fun mix(x: Int, y: Int): Int = (x + (y - x) * f).toInt()
        return Color.argb(
            mix(Color.alpha(a), Color.alpha(b)),
            mix(Color.red(a), Color.red(b)),
            mix(Color.green(a), Color.green(b)),
            mix(Color.blue(a), Color.blue(b))
        )
    }

    // One UI 9.5 style: white card with dark text for notifications
    private fun applyCardTheme(light: Boolean) {
        val main = Color.parseColor(if (light) "#111111" else "#FFFFFF")
        val sub = Color.parseColor(if (light) "#3A3D45" else "#D0D4DC")
        val dim = Color.parseColor(if (light) "#6B6F7A" else "#9AA0AE")
        notifTitle?.setTextColor(main)
        notifText?.setTextColor(sub)
        notifApp?.setTextColor(dim)
        notifCount?.setTextColor(dim)
        val row = notifBtnRow
        if (row != null) {
            for (i in 0 until row.childCount) {
                val c = row.getChildAt(i)
                if (c is TextView) {
                    c.setTextColor(main)
                    (c.background as? GradientDrawable)
                        ?.setColor(Color.parseColor(if (light) "#14000000" else "#33FFFFFF"))
                }
            }
        }
        for (t in peekTexts) t.setTextColor(sub)
        val sr = stackRow
        if (sr != null) {
            for (i in 0 until sr.childCount) {
                val hex = if (light) (if (i == 0) "#14000000" else "#0D000000")
                          else (if (i == 0) "#22FFFFFF" else "#14FFFFFF")
                (sr.getChildAt(i).background as? GradientDrawable)?.setColor(Color.parseColor(hex))
            }
        }
        notifInput?.setTextColor(main)
        notifInput?.setHintTextColor(Color.parseColor(if (light) "#80000000" else "#80FFFFFF"))
        (notifInput?.background as? GradientDrawable)
            ?.setColor(Color.parseColor(if (light) "#14000000" else "#26FFFFFF"))
    }

    private fun bindItem(item: NotifItem) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.time))
        val total = pageCount()
        notifCount?.text = "${curPage + 1}/$total"
        notifCount?.visibility = if (total > 1) View.VISIBLE else View.GONE
        notifApp?.text =
            if (item.live) "${item.appName}  •  Ongoing" else "${item.appName}  •  $time"
        notifApp?.visibility = if (item.live) View.VISIBLE else View.GONE
        val acts = item.actions
        actBtn1?.text = acts.getOrNull(0)?.title ?: ""
        actBtn2?.text = acts.getOrNull(1)?.title ?: ""
        actBtn1?.visibility = if (acts.size > 0) View.VISIBLE else View.GONE
        actBtn2?.visibility = if (acts.size > 1) View.VISIBLE else View.GONE
        actRow?.visibility = if (acts.isNotEmpty()) View.VISIBLE else View.GONE
        notifTitle?.text = item.title
        notifText?.text =
            if (item.chrono) chronoText(item) else item.lines.joinToString("\n")
        notifText?.maxLines = if (item.live) 1 else 3
        notifIcon?.setImageBitmap(item.icon)
        notifBar?.visibility = if (item.live && item.progressMax > 0) View.VISIBLE else View.GONE
        if (item.live && item.progressMax > 0) {
            notifBar?.value = item.progress / item.progressMax.toFloat()
        }
        notifBtnRow?.visibility = if (!item.live && !replyMode) View.VISIBLE else View.GONE
        replyBtn?.visibility = if (item.replyAction != null) View.VISIBLE else View.GONE
        clearBtn?.visibility = if (notifs.size >= 2) View.VISIBLE else View.GONE
    }

    private fun renderPage() {
        curPage = curPage.coerceIn(0, maxOf(0, pageCount() - 1))
        val pg = pageAt(curPage)
        val item = pg?.item
        lightCard = false
        if (item != null) bindItem(item)
        applyCardTheme(lightCard)
        val o = ongoing
        if (pg != null && pg.kind == 3 && o != null) bindAlert(o)

        // Stack: older notifications peeking out behind the current card
        val behind = behindItems()
        peekTargets = behind
        val sr = stackRow
        for (i in 0 until 2) {
            val row = sr?.getChildAt(i)
            if (row == null) continue
            if (i < behind.size && i < peekIcons.size) {
                val b = behind[i]
                val ic = peekIcons[i]
                val m = b.mono
                if (b.icon != null) {
                    ic.setImageBitmap(b.icon)
                    ic.clearColorFilter()
                } else if (m != null) {
                    ic.setImageBitmap(m)
                    ic.setColorFilter(Color.WHITE)
                }
                val head = b.title.ifEmpty { b.lines.firstOrNull() ?: "" }
                peekTexts[i].text = "${b.appName} • $head"
                row.visibility = View.VISIBLE
            } else {
                row.visibility = View.GONE
            }
        }

        val extra = if (notifs.isNotEmpty()) dp(18) else 0
        musicCard?.setPadding(dp(18), dp(10), dp(18), dp(8) + extra)
        notifRow?.setPadding(dp(16), dp(8), dp(16), dp(8) + extra + dp(25) * behind.size)
        alertRow?.setPadding(dp(18), dp(10), dp(18), dp(12) + extra)
        buildStrip()
        updateBadges()
    }

    private fun bringToFront(slot: Int) {
        val target = peekTargets.getOrNull(slot) ?: return
        val idx = pages().indexOfFirst { it.item === target }
        if (idx < 0) return
        if (replyMode) exitReply()
        curPage = idx
        markViewedCurrent()
        haptic()
        renderPage()
        animateHeightTo(pageHeight())
        renderContent()
        scheduleCollapse(collapseDelay())
        val v = notifRow
        if (v != null) {
            v.translationX = dp(48).toFloat()
            v.alpha = 0f
            v.animate().translationX(0f).alpha(1f).setDuration(200).start()
        }
    }

    // White One UI style status icon (falls back to the coloured app icon)
    private fun statusIcon(item: NotifItem): ImageView {
        return ImageView(this).apply {
            val ic = item.icon
            val m = item.mono
            if (ic != null) {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = roundOutline(0.3f)
                setImageBitmap(ic)
            } else if (m != null) {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageBitmap(m)
                setColorFilter(Color.WHITE)
            } else {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = roundOutline(0.3f)
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
            val iv = statusIcon(item)
            iv.alpha = if (cur) 1f else 0.5f
            val size = if (cur) dp(16) else dp(13)
            s.addView(iv, lp(size, size).apply {
                marginStart = dp(3)
                marginEnd = dp(3)
            })
            count++
        }
    }

    // isAttachedToWindow turns true only after the first draw, so a window added a
    // moment ago used to be skipped here and stayed on screen forever.
    private fun safeRemove(v: View?) {
        if (v == null) return
        try {
            wm?.removeViewImmediate(v)
        } catch (e: Exception) {
        }
    }

    private fun removeSideWindows() {
        winL?.let {
            safeRemove(it)
        }
        winR?.let {
            safeRemove(it)
        }
        winL = null
        winR = null
    }

    private fun sideWindow(items: List<NotifItem>, left: Boolean): FrameLayout? {
        if (items.isEmpty()) return null
        val row = hbox(Gravity.CENTER)
        for (item in items) {
            val iv = statusIcon(item)
            iv.setOnClickListener {
                haptic()
                openBadge(item)
            }
            row.addView(iv, lp(dp(16), dp(16)).apply {
                marginStart = dp(2)
                marginEnd = dp(2)
            })
        }
        val holder = FrameLayout(this).apply {
            addView(row, FrameLayout.LayoutParams(WC, MP, Gravity.CENTER))
        }
        val w = items.size * dp(20)
        val p = WindowManager.LayoutParams(
            w,
            dp(26),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = baseX + if (left) -(smallW / 2) + w / 2 else (smallW / 2) - w / 2
            y = baseTop + smallH
        }
        wm?.addView(holder, p)
        return holder
    }

    private fun updateBadges() {
        if (box == null) return
        val unseen = LinkedHashMap<String, NotifItem>()
        for (n in notifs) {
            if (!n.viewed && !unseen.containsKey(n.pkg)) unseen[n.pkg] = n
        }
        val showSides = unseen.isNotEmpty() && frac < 0.3f && !hidden
        val sig = if (showSides) unseen.keys.joinToString(",") else ""
        if (sig != badgeSig) {
            badgeSig = sig
            removeSideWindows()
            if (showSides) {
                val leftItems = ArrayList<NotifItem>()
                val rightItems = ArrayList<NotifItem>()
                var i = 0
                for ((_, item) in unseen) {
                    if (i >= 8) break
                    if (i % 2 == 0) leftItems.add(item) else rightItems.add(item)
                    i++
                }
                winL = sideWindow(leftItems, true)
                winR = sideWindow(rightItems, false)
            }
        }
        val bb = badgeBox
        if (bb != null) {
            val dots = dotCam?.visibility == View.VISIBLE || dotMic?.visibility == View.VISIBLE
            bb.visibility = if ((dots || charging) && frac < 0.3f && !hidden) {
                View.VISIBLE
            } else {
                View.GONE
            }
        }
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

    private fun mediaBtn(kind: Int, onLong: (() -> Unit)? = null, onClick: () -> Unit): MediaButton {
        return MediaButton(this, kind).apply {
            setOnClickListener {
                haptic()
                onClick()
                scheduleCollapse(collapseDelay())
            }
            if (onLong != null) {
                setOnLongClickListener {
                    haptic(true)
                    onLong()
                    scheduleCollapse(collapseDelay())
                    true
                }
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

    private fun quickTile(g: GlyphView, onClick: () -> Unit): FrameLayout {
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#40FFFFFF"))
            }
            addView(g, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
            setOnClickListener {
                haptic()
                onClick()
                scheduleCollapse(LONG_COLLAPSE_MS)
            }
        }
    }

    private fun glyphBtn(g: Int, onClick: () -> Unit): GlyphView {
        return GlyphView(this, g).apply {
            shadow = true
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

    private fun makePeek(bgHex: String): LinearLayout {
        val ic = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val tx = smallText(11f, "#D0D4DC", false)
        peekIcons.add(ic)
        peekTexts.add(tx)
        return hbox().apply {
            background = GradientDrawable().apply {
                setColor(Color.parseColor(bgHex))
                cornerRadius = dp(12).toFloat()
            }
            setPadding(dp(10), 0, dp(10), 0)
            visibility = View.GONE
            addView(ic, lp(dp(14), dp(14)).apply { marginEnd = dp(6) })
            addView(tx, lp(0, WC, 1f))
        }
    }

    private fun buildPill() {
        removePill()
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        smallW = dp(prefs.getInt("w", 120))
        smallH = dp(prefs.getInt("h", 34))
        val top = dp(prefs.getInt("top", 8))
        val offsetX = dp(prefs.getInt("x", 0))
        baseX = offsetX
        baseTop = top
        yShift = 0
        dragX = 0f
        dragY = 0f
        pressScale = 1f
        hapticOn = prefs.getBoolean("haptic", true)
        barsOn = prefs.getBoolean("show_bright", false)
        hideLand = prefs.getBoolean("hide_land", true)
        matchColors = prefs.getBoolean("match_colors", true)
        hideApps = prefs.getStringSet("hide_apps", null)?.toSet() ?: emptySet()
        edgeOn = prefs.getBoolean("edge_on", true)
        edgeNotif = prefs.getBoolean("edge_notif", true)
        edgeCall = prefs.getBoolean("edge_call", true)
        edgeC1 = prefs.getInt("edge_c1", 0xFF3987FF.toInt())
        edgeC2 = prefs.getInt("edge_c2", 0xFF55D9FF.toInt())
        edgeMs = prefs.getInt("edge_ms10", 25) * 100L
        edgeThick = prefs.getInt("edge_thick", 3)
        edgeGlow = prefs.getInt("edge_glow", 12)
        edgeStyle = prefs.getInt("edge_style", 0)
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

        // Info / warning row
        val iIcon = GlyphView(this, GlyphView.BATTERY)
        infoIcon = iIcon
        val tv = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
        }
        label = tv
        val iRow = hbox(Gravity.CENTER).apply {
            visibility = View.GONE
            addView(iIcon, lp(dp(22), dp(22)).apply { marginEnd = dp(8) })
            addView(tv, lp(WC, WC))
        }
        infoRow = iRow

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
        val nTitle = smallText(17f, "#FFFFFF", true)
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
        val nCnt = TextView(this).apply {
            textSize = 13f
            setTextColor(Color.parseColor("#9AA0AE"))
            visibility = View.GONE
        }
        notifCount = nCnt
        val nTop = hbox().apply {
            addView(nIcon, lp(dp(42), dp(42)))
            addView(nCol, lp(0, WC, 1f).apply { marginStart = dp(12) })
            addView(nCnt, lp(WC, WC).apply { marginStart = dp(8) })
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
        val aBtn1 = pillButton("") { fireAction(0) }
        val aBtn2 = pillButton("") { fireAction(1) }
        for (b in listOf(aBtn1, aBtn2)) {
            b.textSize = 15f
            b.background = null
        }
        actBtn1 = aBtn1
        actBtn2 = aBtn2
        val aRow = hbox(Gravity.CENTER).apply {
            visibility = View.GONE
            addView(aBtn1, lp(WC, dp(34)))
            addView(aBtn2, lp(WC, dp(34)).apply { marginStart = dp(8) })
        }
        actRow = aRow
        val cpIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundOutline(0.3f)
        }
        val cpText = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        compIcon = cpIcon
        compText = cpText
        val cpRow = hbox(Gravity.CENTER_VERTICAL).apply {
            visibility = View.GONE
            setPadding(dp(8), 0, dp(14), 0)
            addView(cpIcon, lp(dp(24), dp(24)))
            addView(cpText, lp(0, WC, 1f).apply { marginStart = dp(8) })
        }
        compactRow = cpRow
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
            addView(aRow, lp(MP, WC).apply { topMargin = dp(4) })
            addView(nBar, lp(MP, dp(6)).apply { topMargin = dp(8) })
            addView(btnRow, lp(MP, WC).apply { topMargin = dp(8) })
            addView(inputRow, lp(MP, WC).apply { topMargin = dp(8) })
        }
        notifRow = nRow

        // Stack: two layers peeking out behind the notification card
        peekIcons.clear()
        peekTexts.clear()
        val p1 = makePeek("#22FFFFFF")
        val p2 = makePeek("#14FFFFFF")
        p1.setOnClickListener { bringToFront(0) }
        p2.setOnClickListener { bringToFront(1) }
        val stackBox = vbox().apply {
            visibility = View.GONE
            addView(p1, lp(MP, dp(22)).apply {
                marginStart = dp(18)
                marginEnd = dp(18)
            })
            addView(p2, lp(MP, dp(22)).apply {
                topMargin = dp(3)
                marginStart = dp(30)
                marginEnd = dp(30)
            })
        }
        stackRow = stackBox

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
        val mEq = EqView(this, 7)
        cardEq = mEq
        val topRow = hbox().apply {
            addView(mArt, lp(dp(56), dp(56)))
            addView(mText, lp(0, WC, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(12)
            })
            addView(mEq, lp(dp(40), dp(30)))
        }

        // Seek bar with time labels (drag to go forward or back)
        val sElapsed = smallText(11f, "#B8BDC8", false).apply {
            text = "0:00"
            gravity = Gravity.CENTER_VERTICAL
        }
        val sTotal = smallText(11f, "#B8BDC8", false).apply {
            text = "0:00"
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
        }
        seekElapsed = sElapsed
        seekTotal = sTotal
        val sBar = SeekView(this).apply {
            onTouchState = { down ->
                seeking = down
                if (down) {
                    this@IslandService.handler.removeCallbacks(collapseRunnable)
                    haptic()
                } else {
                    scheduleCollapse(collapseDelay())
                }
            }
            onScrub = { f ->
                sElapsed.text = fmtElapsed((f * musicDuration).toLong())
            }
            onCommit = { f ->
                seekTo((f * musicDuration).toLong())
                haptic()
            }
        }
        seekView = sBar
        val sRow = hbox().apply {
            visibility = View.INVISIBLE
            addView(sElapsed, lp(dp(40), dp(22)))
            addView(sBar, lp(0, dp(22), 1f))
            addView(sTotal, lp(dp(40), dp(22)))
        }
        seekRow = sRow

        val prevBtn = mediaBtn(MediaButton.PREV, { seekBy(-10000L) }) {
            controller?.transportControls?.skipToPrevious()
        }
        val pBtn = mediaBtn(MediaButton.PLAY) {
            val tc = controller?.transportControls
            if (musicPlaying) tc?.pause() else tc?.play()
        }
        val nextBtn = mediaBtn(MediaButton.NEXT, { seekBy(10000L) }) {
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
            setPadding(dp(18), dp(10), dp(18), dp(8))
            visibility = View.GONE
            addView(topRow, lp(MP, WC))
            addView(sRow, lp(MP, WC).apply { topMargin = dp(6) })
            addView(controls, lp(MP, WC).apply { topMargin = dp(2) })
        }
        musicCard = mCard

        // Alert card (incoming call, alarm, ongoing call)
        val aIcon = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            clipToOutline = true
            outlineProvider = roundOutline(0.5f)
        }
        alertIcon = aIcon
        val aSub = smallText(12f, "#B8BDC8", false)
        val aName = smallText(18f, "#FFFFFF", true)
        alertSub = aSub
        alertName = aName
        val aCol = vbox(Gravity.CENTER_VERTICAL).apply {
            addView(aName)
            addView(aSub)
        }
        // One UI style round call buttons (red decline / green accept, or mute / end)
        val cl = CallButton(this, CallButton.DECLINE).apply {
            setOnClickListener {
                haptic(true)
                alertTap(true)
            }
        }
        val cr = CallButton(this, CallButton.ACCEPT).apply {
            setOnClickListener {
                haptic(true)
                alertTap(false)
            }
        }
        callL = cl
        callR = cr
        val cBox = hbox(Gravity.CENTER).apply {
            visibility = View.GONE
            addView(cl, lp(dp(46), dp(46)).apply { marginEnd = dp(10) })
            addView(cr, lp(dp(46), dp(46)))
        }
        callBtnsBox = cBox
        val aTop = hbox().apply {
            addView(aIcon, lp(dp(46), dp(46)))
            addView(aCol, lp(0, WC, 1f).apply {
                marginStart = dp(12)
                marginEnd = dp(8)
            })
            addView(cBox, lp(WC, WC))
        }
        val aLeft = pillButton("", "#33FFFFFF") { alertTap(true) }
        val aRight = pillButton("", "#6A5CFF") { alertTap(false) }
        alertLeft = aLeft
        alertRight = aRight
        val aBtns = hbox().apply {
            visibility = View.GONE
            addView(aLeft, lp(0, dp(42), 1f).apply { marginEnd = dp(6) })
            addView(aRight, lp(0, dp(42), 1f).apply { marginStart = dp(6) })
        }
        alertBtnRow = aBtns
        val aCard = vbox(Gravity.CENTER).apply {
            setPadding(dp(18), dp(10), dp(18), dp(12))
            visibility = View.GONE
            addView(aTop, lp(MP, WC))
            addView(aBtns, lp(MP, WC).apply { topMargin = dp(12) })
        }
        alertRow = aCard

        // Toast card (Bluetooth, ringer, clipboard)
        val tIcon = GlyphView(this, GlyphView.INFO)
        toastIcon = tIcon
        val tTitle = smallText(15f, "#FFFFFF", true)
        val tSub = smallText(12f, "#D0D4DC", false)
        toastTitle = tTitle
        toastSub = tSub
        val tCol = vbox().apply {
            addView(tTitle)
            addView(tSub)
        }
        val tRow = hbox().apply {
            setPadding(dp(22), dp(8), dp(22), dp(8))
            visibility = View.GONE
            addView(tIcon, lp(dp(28), dp(28)).apply { marginEnd = dp(14) })
            addView(tCol, lp(0, WC, 1f))
        }
        toastRow = tRow

        // Quick buttons (long press): no background, just icons
        val qT = GlyphView(this, GlyphView.TORCH)
        val qR = GlyphView(this, GlyphView.BELL)
        val qG = GlyphView(this, GlyphView.GEAR)
        qTorch = qT
        qRinger = qR
        val qTTile = quickTile(qT) { toggleTorch() }
        val qRTile = quickTile(qR) { toggleRinger() }
        val qSTile = quickTile(qG) {
            val i = Intent(this, MainActivity::class.java)
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(i)
        }
        qTorchTile = qTTile
        qRingerTile = qRTile
        val qRow = hbox(Gravity.CENTER).apply {
            visibility = View.GONE
            addView(qTTile, lp(dp(52), dp(52)).apply { marginEnd = dp(16) })
            addView(qRTile, lp(dp(52), dp(52)).apply { marginEnd = dp(16) })
            addView(qSTile, lp(dp(52), dp(52)))
        }
        quickRow = qRow

        // Brightness bar
        val bIcon = GlyphView(this, GlyphView.SUN)
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
            addView(bIcon, lp(dp(26), dp(26)).apply { marginEnd = dp(14) })
            addView(bCol, lp(0, WC, 1f))
        }
        barRow = bRow

        // Compact items inside the small pill
        val artSize = maxOf(dp(12), smallH - dp(12))
        val eqH = maxOf(dp(8), (smallH * 0.45f).toInt())
        val cArt = artView(0.5f).apply { visibility = View.GONE }
        compactArt = cArt
        val cEq = EqView(this, 4).apply { visibility = View.GONE }
        compactEq = cEq
        val callSize = maxOf(dp(12), smallH - dp(14))
        val cCall = PhoneIconView(this).apply {
            tint = Color.WHITE
            visibility = View.GONE
        }
        compactCall = cCall
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
            addView(iRow, FrameLayout.LayoutParams(MP, MP))
            addView(cRow, FrameLayout.LayoutParams(MP, MP))
            addView(nRow, FrameLayout.LayoutParams(MP, MP))
            addView(mCard, FrameLayout.LayoutParams(MP, MP))
            addView(aCard, FrameLayout.LayoutParams(MP, MP))
            addView(tRow, FrameLayout.LayoutParams(MP, MP))
            addView(qRow, FrameLayout.LayoutParams(MP, MP))
            addView(cpRow, FrameLayout.LayoutParams(MP, MP))
            addView(bRow, FrameLayout.LayoutParams(MP, MP))
            addView(
                stackBox,
                FrameLayout.LayoutParams(MP, WC, Gravity.BOTTOM).apply { bottomMargin = dp(22) }
            )
            addView(
                cArt,
                FrameLayout.LayoutParams(
                    artSize, artSize, Gravity.START or Gravity.CENTER_VERTICAL
                ).apply { marginStart = dp(8) }
            )
            addView(
                cCall,
                FrameLayout.LayoutParams(
                    callSize, callSize, Gravity.START or Gravity.CENTER_VERTICAL
                ).apply { marginStart = dp(12) }
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

        // Edge Lighting window: added first so it sits behind the pill
        if (edgeOn) {
            val gv = GlowView(this)
            glowView = gv
            glowPad = dp(edgeGlow) + dp(edgeThick) + dp(3)
            gv.configure(
                edgeC1, edgeC2,
                dp(edgeThick).toFloat(), dp(edgeGlow).toFloat(), glowPad.toFloat(),
                edgeStyle
            )
            gv.radius = cornerPx
            gv.visibility = View.INVISIBLE
            gv.onEnd = { glowActive = false }
            val gp = WindowManager.LayoutParams(
                smallW + 2 * glowPad,
                smallH + 2 * glowPad,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = offsetX
                y = top - glowPad
            }
            glowParams = gp
            wm?.addView(gv, gp)
        }

        // Water drop ripple window: also behind the pill
        ripplePad = dp(46)
        val rv = RippleView(this)
        rippleView = rv
        rv.pad = ripplePad.toFloat()
        rv.radius = cornerPx
        rv.visibility = View.INVISIBLE
        rv.onEnd = { rippleActive = false }
        val rp = WindowManager.LayoutParams(
            smallW + 2 * ripplePad,
            smallH + 2 * ripplePad,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = offsetX
            y = top - ripplePad
        }
        rippleParams = rp
        wm?.addView(rv, rp)

        wm?.addView(b, wlp)

        // Middle strip under the pill: privacy dots and the charging word
        val dc = dot("#30D158")
        val dm = dot("#FF9F0A")
        dotCam = dc
        dotMic = dm
        val cbIcon = GlyphView(this, GlyphView.BOLT).apply {
            tint = Color.parseColor("#30D158")
            filled = true
        }
        val cbText = TextView(this).apply {
            textSize = 10f
            setTextColor(Color.parseColor("#30D158"))
            setTypeface(typeface, Typeface.BOLD)
        }
        chargeBadgeText = cbText
        val cbBox = hbox(Gravity.CENTER).apply {
            visibility = View.GONE
            addView(cbIcon, lp(dp(11), dp(11)))
            addView(cbText, lp(WC, WC).apply { marginStart = dp(3) })
        }
        chargeBadge = cbBox
        val centerBox = hbox(Gravity.CENTER).apply {
            addView(dc, lp(dp(6), dp(6)).apply { marginEnd = dp(3) })
            addView(dm, lp(dp(6), dp(6)).apply { marginEnd = dp(3) })
            addView(cbBox, lp(WC, WC))
        }
        val bb = FrameLayout(this).apply {
            visibility = View.GONE
            addView(centerBox, FrameLayout.LayoutParams(WC, MP, Gravity.CENTER))
        }
        badgeBox = bb
        val bp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
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
        updateChargeBadge()
        applyHidden()

        handler.removeCallbacks(fgPoll)
        if (hideApps.isNotEmpty()) handler.post(fgPoll)
        if (ongoing != null || lives.any { it.chrono }) startTicker()
    }

    private fun applyHidden() {
        landHidden = hideLand &&
            resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        hidden = landHidden || appHidden
        box?.alpha = if (hidden) 0f else 1f
        syncViz()
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

    // ---------- Touch (iPhone style: grows, follows the finger, springs back) ----------

    private fun rubber(v: Float, lim: Float): Float {
        val s = v / lim
        return lim * s / (1f + abs(s))
    }

    private fun animatePress(target: Float) {
        pressAnim?.cancel()
        pressAnim = ValueAnimator.ofFloat(pressScale, target).apply {
            duration = 160
            addUpdateListener {
                pressScale = it.animatedValue as Float
                applyLayout()
            }
            start()
        }
    }

    private fun springBack() {
        springAnim?.cancel()
        animatePress(1f)
        val sx = dragX
        val sy = dragY
        if (sx == 0f && sy == 0f) return
        springAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            interpolator = OvershootInterpolator(2.6f)
            addUpdateListener { a ->
                val t = a.animatedValue as Float
                dragX = sx * (1f - t)
                dragY = sy * (1f - t)
                applyLayout()
            }
            start()
        }
    }

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
                downX = ev.rawX
                downY = ev.rawY
                lpFired = false
                downOnStrip = isOnStrip(ev.x, ev.y)
                if (targetFrac == 1f && !replyMode) handler.removeCallbacks(collapseRunnable)
                if (frac < 0.5f && event == EVENT_NONE && !hidden) {
                    handler.postDelayed(longPress, 450)
                }
                springAnim?.cancel()
                if (!replyMode) animatePress(1.04f)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
                if (abs(dx) > dp(12) || abs(dy) > dp(12)) {
                    handler.removeCallbacks(longPress)
                }
                if (!replyMode) {
                    dragX = rubber(dx, dp(40).toFloat())
                    dragY = rubber(dy, dp(32).toFloat())
                    applyLayout()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                springBack()
                if (lpFired) {
                    lpFired = false
                    return true
                }
                val dx = ev.rawX - downX
                val dy = ev.rawY - downY
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
                springBack()
                if (targetFrac == 1f && !replyMode && event != EVENT_ALERT) {
                    scheduleCollapse(collapseDelay())
                }
                return true
            }
        }
        return true
    }

    private fun onPillClick() {
        if (event == EVENT_COMPACT) {
            expandFromCompact()
            return
        }
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

    private val compactNext = Runnable { advanceCompact() }

    private fun compactList(): List<NotifItem> = notifs.filter { !it.viewed }.take(4)

    private fun bindCompact(item: NotifItem) {
        compactKey = item.key
        compIcon?.setImageBitmap(item.icon)
        compText?.text =
            if (item.title.isNotBlank()) item.title else (item.lines.firstOrNull() ?: item.appName)
        val r = compactRow ?: return
        r.animate().cancel()
        r.alpha = 0f
        r.translationY = dp(4).toFloat()
        r.animate().alpha(1f).translationY(0f).setDuration(220).start()
    }

    private fun showCompactNotif() {
        val list = compactList()
        if (list.isEmpty()) {
            showPages(if (hasMusic()) 1 else 0)
            return
        }
        handler.removeCallbacks(collapseRunnable)
        handler.removeCallbacks(compactNext)
        compactIdx = 0
        bindCompact(list[0])
        event = EVENT_COMPACT
        chargeShown = false
        val dH = maxOf(smallH, dp(38))
        bigH = 2 * dH - smallH
        haptic(true)
        animateTo(0.5f)
        handler.postDelayed(compactNext, COMPACT_MS)
    }

    // Auto-cycle through the unread notifications, then collapse
    private fun advanceCompact() {
        if (event != EVENT_COMPACT) return
        val list = compactList()
        compactIdx++
        if (compactIdx < list.size) {
            bindCompact(list[compactIdx])
            handler.postDelayed(compactNext, COMPACT_MS)
        } else {
            animateTo(0f)
        }
    }

    private fun expandFromCompact() {
        handler.removeCallbacks(compactNext)
        handler.removeCallbacks(collapseRunnable)
        val key = compactKey
        val idx = pages().indexOfFirst { it.item?.key == key }
        event = EVENT_PAGE
        curPage = if (idx >= 0) idx else firstMusicOrNotif()
        chargeShown = false
        markViewedCurrent()
        renderPage()
        animateHeightTo(pageHeight())
        haptic(true)
        animateTo(1f)
        scheduleCollapse(collapseDelay())
    }

    private fun fireAction(i: Int) {
        val a = pageAt(curPage)?.item?.actions?.getOrNull(i) ?: return
        try {
            a.actionIntent?.send()
        } catch (e: Exception) {
        }
        animateTo(0f)
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
        if (target == 0f) handler.removeCallbacks(compactNext)
        animator?.cancel()
        val ip: TimeInterpolator = when {
            target == 0f -> AccelerateDecelerateInterpolator()
            target < 1f -> OvershootInterpolator(1.9f)
            else -> OvershootInterpolator(1.3f)
        }
        animator = ValueAnimator.ofFloat(frac, target).apply {
            duration = when {
                target == 0f -> 320L
                target < 1f -> 380L
                else -> ANIM_MS + 80L
            }
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
        wlp.width = ((smallW + (bigW - smallW) * frac) * pressScale * jellyW).toInt()
        wlp.height = ((smallH + (bigH - smallH) * frac) * pressScale * jellyH).toInt()
        wlp.x = baseX + dragX.toInt()
        wlp.y = baseTop + yShift + dragY.toInt()
        cornerPx = minOf(wlp.height / 2f, dp(34).toFloat())
        bg?.cornerRadius = cornerPx
        val v = box ?: return
        v.invalidateOutline()
        if (v.isAttachedToWindow) wm?.updateViewLayout(v, wlp)
        updateGlowLayout()
        updateRippleLayout()
    }

    private fun updateRippleLayout() {
        if (!rippleActive) return
        val rv = rippleView ?: return
        val rp = rippleParams ?: return
        val wlp = params ?: return
        rp.width = wlp.width + 2 * ripplePad
        rp.height = wlp.height + 2 * ripplePad
        rp.x = wlp.x
        rp.y = wlp.y - ripplePad
        rv.radius = cornerPx
        if (rv.isAttachedToWindow) wm?.updateViewLayout(rv, rp)
    }

    // Water drop: the pill stretches like a falling drop, wobbles like jelly, then settles.
    // Rings spread out of it like ripples on water.
    private fun dropEffect() {
        if (box == null || hidden) return
        val rv = rippleView
        if (rv != null) {
            rippleActive = true
            updateRippleLayout()
            rv.play(edgeC2)
        }
        jellyAnim?.cancel()
        jellyAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 950L
            interpolator = LinearInterpolator()
            addUpdateListener {
                val t = it.animatedValue as Float
                val decay = exp(-4.0f * t)
                val w = sin(t * 17f)
                jellyH = 1f + 0.60f * decay * w
                jellyW = 1f - 0.14f * decay * w
                applyLayout()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(a: android.animation.Animator) {
                    jellyW = 1f
                    jellyH = 1f
                    applyLayout()
                }
            })
            start()
        }
    }

    private fun showOnly(which: View?) {
        val all = listOf<View?>(
            infoRow, chargeRow, musicCard, notifRow, alertRow, toastRow, quickRow, barRow, compactRow
        )
        for (v in all) {
            v?.visibility = if (v != null && v === which) View.VISIBLE else View.GONE
        }
    }

    private fun applyTint() {
        var c = basePillColor
        if (event == EVENT_QUICK) {
            c = Color.argb(238, 32, 32, 36)
        } else if (event == EVENT_NONE && ongoing != null) {
            c = Color.argb(Color.alpha(basePillColor), 30, 160, 78)
        } else if (matchColors) {
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
        if (event == EVENT_PAGE) {
            val pg2 = pageAt(curPage)
            val t2 = if (pg2 != null && pg2.kind == 1) pg2.item?.tint else null
            if (t2 != null) {
                val tc = Color.argb(Color.alpha(basePillColor), Color.red(t2), Color.green(t2), Color.blue(t2))
                c = blendColor(basePillColor, tc, frac)
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
            yShift = 0
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

        val timerOn = ongoing != null || lives.any { it.chrono }
        val showCompact = frac < 0.3f && event == EVENT_NONE && !hidden
        val callOn = ongoing != null
        compactCall?.visibility =
            if (showCompact && callOn) View.VISIBLE else View.GONE
        compactArt?.visibility =
            if (showCompact && musicPlaying && !callOn) View.VISIBLE else View.GONE
        compactEq?.visibility =
            if (showCompact && musicPlaying && !timerOn) View.VISIBLE else View.GONE
        compactTimer?.visibility =
            if (showCompact && timerOn) View.VISIBLE else View.GONE

        val kind = if (event == EVENT_PAGE) (pageAt(curPage)?.kind ?: -1) else -1
        val artBg = kind == 0 && musicArt != null
        bgArt?.visibility = if (artBg) View.VISIBLE else View.GONE
        bgScrim?.visibility = if (artBg) View.VISIBLE else View.GONE
        updateBadges()

        if (event == EVENT_COMPACT) {
            showOnly(compactRow)
            strip?.visibility = View.GONE
            stackRow?.visibility = View.GONE
            return
        }

        if (frac <= 0.6f) {
            showOnly(null)
            strip?.visibility = View.GONE
            stackRow?.visibility = View.GONE
            return
        }

        strip?.visibility =
            if (event == EVENT_PAGE && notifs.isNotEmpty()) View.VISIBLE else View.GONE
        stackRow?.visibility =
            if (event == EVENT_PAGE && kind == 1 && peekTargets.isNotEmpty()) {
                View.VISIBLE
            } else {
                View.GONE
            }

        when (event) {
            EVENT_PLUG, EVENT_UNPLUG -> {
                showOnly(chargeRow)
                if (!chargeShown) {
                    chargeShown = true
                    chargeView?.play(batteryLevel(), event == EVENT_UNPLUG)
                }
            }
            EVENT_PAGE -> {
                if (kind == 0) startMusicTick()
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
                showOnly(infoRow)
                infoIcon?.glyph = GlyphView.WARN
                infoIcon?.filled = false
                infoIcon?.tint = Color.parseColor("#FF6B6B")
                label?.text = warnText
                label?.setTextColor(Color.parseColor("#FF6B6B"))
            }
            else -> {
                val first = infoRow?.visibility != View.VISIBLE
                showOnly(infoRow)
                if (first) showInfo()
            }
        }
    }

    private fun showInfo() {
        val level = batteryLevel()
        infoIcon?.glyph = if (charging) GlyphView.BOLT else GlyphView.BATTERY
        infoIcon?.filled = charging
        infoIcon?.tint = if (charging) Color.parseColor("#30D158") else Color.WHITE
        label?.setTextColor(Color.WHITE)
        label?.text = if (charging) {
            "Charging   $level%"
        } else {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            "$level%   •   $time"
        }
        infoRow?.visibility = View.VISIBLE
    }


    private fun removePill() {
        handler.removeCallbacks(collapseRunnable)
        handler.removeCallbacks(longPress)
        handler.removeCallbacks(fgPoll)
        animator?.cancel()
        heightAnim?.cancel()
        springAnim?.cancel()
        pressAnim?.cancel()
        chargeView?.stop()
        cardEq?.setActive(false)
        compactEq?.setActive(false)
        audioViz?.stop()
        handler.removeCallbacks(musicTick)
        musicTickOn = false
        jellyAnim?.cancel()
        jellyW = 1f
        jellyH = 1f
        rippleView?.let {
            it.stopNow()
            safeRemove(it)
        }
        rippleView = null
        rippleParams = null
        rippleActive = false
        removeSideWindows()
        glowView?.let {
            it.stopNow()
            safeRemove(it)
        }
        glowView = null
        glowParams = null
        glowActive = false
        box?.let {
            safeRemove(it)
        }
        badgeBox?.let {
            safeRemove(it)
        }
        box = null
        bg = null
        bgArt = null
        bgScrim = null
        infoRow = null
        infoIcon = null
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
        compactCall = null
        seekView = null
        seekRow = null
        seekElapsed = null
        seekTotal = null
        callBtnsBox = null
        callL = null
        callR = null
        alertBtnRow = null
        notifRow = null
        notifCount = null
        compactRow = null
        compIcon = null
        compText = null
        actRow = null
        actBtn1 = null
        actBtn2 = null
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
        stackRow = null
        peekIcons.clear()
        peekTexts.clear()
        peekTargets = emptyList()
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
        qTorchTile = null
        qRingerTile = null
        barRow = null
        barIcon = null
        barText = null
        barView = null
        strip = null
        badgeBox = null
        dotCam = null
        dotMic = null
        chargeBadge = null
        chargeBadgeText = null
    }

    override fun onDestroy() {
        instance = null
        handler.removeCallbacks(ticker)
        removePill()
        audioViz?.stop()
        audioViz = null
        clearControllers()
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
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        if (prefs.getBoolean("headsup_off", false)) {
            val ctx = applicationContext
            Thread { ShizukuHelper.setHeadsUp(ctx, true) }.start()
        }
        running = false
        super.onDestroy()
    }
                 }
