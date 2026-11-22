package com.example.islandpro

import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
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
import android.service.notification.NotificationListenerService
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
import kotlin.math.sin

internal class NotifItem(
    val key: String,
    val pkg: String,
    val appName: String,
    val icon: Bitmap?,
    val title: String,
    val text: String,
    val time: Long,
    val contentIntent: PendingIntent?,
    val replyAction: Notification.Action?
)

class IslandService : Service() {

    companion object {
        @Volatile
        var running = false

        @Volatile
        var mediaReady = false

        @Volatile
        var instance: IslandService? = null
        private const val CHANNEL_ID = "island_channel"

        // Time the panel stays open before it shrinks (milliseconds)
        private const val COLLAPSE_DELAY_MS = 1300L
        private const val ANIM_MS = 350L
        private const val CHARGE_COLLAPSE_MS = 3500L
        private const val EVENT_NONE = 0
        private const val EVENT_PLUG = 1
        private const val EVENT_UNPLUG = 2
        private const val EVENT_PAGE = 3
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
    private var musicCard: LinearLayout? = null
    private var cardArt: ImageView? = null
    private var cardTitle: TextView? = null
    private var cardArtist: TextView? = null
    private var cardEq: EqView? = null
    private var playBtn: MediaButton? = null
    private var compactArt: ImageView? = null
    private var compactEq: EqView? = null
    private var notifRow: LinearLayout? = null
    private var notifIcon: ImageView? = null
    private var notifApp: TextView? = null
    private var notifTitle: TextView? = null
    private var notifText: TextView? = null
    private var notifBtnRow: LinearLayout? = null
    private var notifInputRow: LinearLayout? = null
    private var notifInput: EditText? = null
    private var strip: LinearLayout? = null
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

    private var curPage = 0
    private var replyMode = false
    private var downX = 0f
    private var downY = 0f
    private var downOnStrip = false

    private val notifs = ArrayList<NotifItem>()
    private val seen = HashMap<String, Int>()
    private val selfCancelled = HashSet<String>()

    private var msm: MediaSessionManager? = null
    private var controller: MediaController? = null
    private var musicTitle = ""
    private var musicArtist = ""
    private var musicArt: Bitmap? = null
    private var musicPlaying = false

    private val handler = Handler(Looper.getMainLooper())
    private val collapseRunnable = Runnable { animateTo(0f) }

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

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_POWER_CONNECTED -> {
                    charging = true
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
        }
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
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

    private fun startAsForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Island", NotificationManager.IMPORTANCE_LOW)
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

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun collapseDelay(): Long = COLLAPSE_DELAY_MS + ANIM_MS

    private fun batteryLevel(): Int {
        val bat = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        return bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
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

    fun onNotification(sbn: StatusBarNotification, listener: NotificationListenerService) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if (n.fullScreenIntent != null) return
        val cat = n.category
        if (cat == Notification.CATEGORY_TRANSPORT ||
            cat == Notification.CATEGORY_PROGRESS ||
            cat == Notification.CATEGORY_SERVICE ||
            cat == Notification.CATEGORY_CALL ||
            cat == Notification.CATEGORY_ALARM ||
            cat == Notification.CATEGORY_NAVIGATION
        ) return

        val ex = n.extras ?: return
        val template = ex.getString(Notification.EXTRA_TEMPLATE) ?: ""
        if (template.contains("MediaStyle")) return

        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT)
            ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString() ?: ""
        if (title.isEmpty() && text.isEmpty()) return

        // Optional fallback: remove it from the phone's shade
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        if (prefs.getBoolean("hide_sys", false)) {
            if (selfCancelled.size > 200) selfCancelled.clear()
            selfCancelled.add(sbn.key)
            try {
                listener.cancelNotification(sbn.key)
            } catch (e: Exception) {
                selfCancelled.remove(sbn.key)
            }
        }

        val sig = title.hashCode() * 31 + text.hashCode()
        if (seen[sbn.key] == sig) return
        if (seen.size > 100) seen.clear()
        seen[sbn.key] = sig

        val pm = packageManager
        val appName = try {
            pm.getApplicationLabel(pm.getApplicationInfo(sbn.packageName, 0)).toString()
        } catch (e: Exception) {
            sbn.packageName
        }

        val item = NotifItem(
            sbn.key,
            sbn.packageName,
            appName,
            appIcon(sbn.packageName),
            title,
            text,
            sbn.postTime,
            n.contentIntent,
            findReplyAction(n)
        )
        notifs.removeAll { it.key == item.key }
        notifs.add(0, item)
        while (notifs.size > 15) notifs.removeAt(notifs.size - 1)

        if (box == null) return
        if (replyMode) {
            renderPage()
            renderContent()
            return
        }
        if ((event == EVENT_PLUG || event == EVENT_UNPLUG) && targetFrac == 1f) {
            renderPage()
            return
        }
        showPages(if (hasMusic()) 1 else 0)
    }

    fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
        if (selfCancelled.remove(sbn.key)) return
        val removed = notifs.removeAll { it.key == sbn.key }
        if (removed && box != null) {
            renderPage()
            renderContent()
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

    // ---------- Reply ----------

    private fun setFocusable(on: Boolean) {
        val lp = params ?: return
        lp.flags = if (on) {
            lp.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        } else {
            lp.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        val v = box ?: return
        if (v.isAttachedToWindow) wm?.updateViewLayout(v, lp)
    }

    private fun startReply() {
        val item = pageNotif(curPage) ?: return
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
        val item = pageNotif(curPage)
        notifBtnRow?.visibility =
            if (item?.replyAction != null) View.VISIBLE else View.GONE
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
        val item = pageNotif(curPage) ?: return
        val txt = notifInput?.text?.toString()?.trim() ?: ""
        if (txt.isEmpty()) return
        sendReply(item, txt)
        notifs.remove(item)
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
        musicArt = md?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
            ?: md?.getBitmap(MediaMetadata.METADATA_KEY_ART)
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

    // ---------- Pages ----------

    private fun pageCount(): Int = (if (hasMusic()) 1 else 0) + notifs.size

    private fun pageIsMusic(p: Int): Boolean = hasMusic() && p == 0

    private fun pageNotif(p: Int): NotifItem? {
        val i = if (hasMusic()) p - 1 else p
        return notifs.getOrNull(i)
    }

    private fun pageHeight(): Int {
        val extra = if (notifs.isNotEmpty()) dp(18) else 0
        if (pageIsMusic(curPage)) return dp(124) + extra
        val item = pageNotif(curPage)
        return (if (item?.replyAction != null) dp(112) else dp(84)) + extra
    }

    private fun bindNotif(item: NotifItem) {
        val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(item.time))
        notifApp?.text = "${item.appName}  •  $time"
        notifTitle?.text = item.title
        notifText?.text = item.text
        notifIcon?.setImageBitmap(item.icon)
        val canReply = item.replyAction != null && !replyMode
        notifBtnRow?.visibility = if (canReply) View.VISIBLE else View.GONE
    }

    private fun renderPage() {
        val count = pageCount()
        curPage = curPage.coerceIn(0, maxOf(0, count - 1))
        val item = pageNotif(curPage)
        if (item != null) bindNotif(item)
        val extra = if (notifs.isNotEmpty()) dp(18) else 0
        musicCard?.setPadding(dp(18), dp(8), dp(18), dp(8) + extra)
        notifRow?.setPadding(dp(16), dp(8), dp(16), dp(8) + extra)
        buildStrip()
    }

    private fun buildStrip() {
        val s = strip ?: return
        s.removeAllViews()
        val pkgs = LinkedHashSet<String>()
        for (it in notifs) pkgs.add(it.pkg)
        if (pkgs.isEmpty()) {
            s.visibility = View.GONE
            return
        }
        val curPkg = pageNotif(curPage)?.pkg
        var n = 0
        for (p in pkgs) {
            if (n >= 7) break
            val item = notifs.first { it.pkg == p }
            val cur = p == curPkg
            val iv = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = roundOutline(0.28f)
                alpha = if (cur) 1f else 0.5f
            }
            val bmp = item.icon
            if (bmp != null) {
                iv.setImageBitmap(bmp)
            } else {
                iv.setBackgroundColor(Color.parseColor("#6A5CFF"))
            }
            val size = if (cur) dp(16) else dp(13)
            s.addView(
                iv,
                LinearLayout.LayoutParams(size, size).apply {
                    marginStart = dp(3)
                    marginEnd = dp(3)
                }
            )
            n++
        }
    }

    private fun changePage(dir: Int) {
        if (pageCount() < 2) return
        val np = curPage + dir
        if (np < 0 || np >= pageCount()) return
        if (replyMode) exitReply()
        curPage = np
        renderPage()
        animateHeightTo(pageHeight())
        renderContent()
        val v: View? = if (pageIsMusic(curPage)) musicCard else notifRow
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

    private fun pillButton(text: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(18), 0, dp(18), 0)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = dp(18).toFloat()
            }
            setOnClickListener { onClick() }
        }
    }

    private fun buildPill() {
        removePill()
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        smallW = dp(prefs.getInt("w", 120))
        smallH = dp(prefs.getInt("h", 34))
        val top = dp(prefs.getInt("top", 8))
        val offsetX = dp(prefs.getInt("x", 0))
        bigW = minOf(dp(320), resources.displayMetrics.widthPixels - dp(32))
        bigH = dp(80)
        frac = 0f
        targetFrac = 0f
        cornerPx = smallH / 2f
        event = EVENT_NONE
        chargeShown = false
        replyMode = false

        // Album art as card background (with a dark scrim on top)
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

        // Info text
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
        chargeTitle = cTitle
        chargeLevel = cLevel
        val cTextCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(cTitle)
            addView(cLevel)
        }
        val cRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            addView(cv, LinearLayout.LayoutParams(dp(54), dp(54)))
            addView(
                cTextCol,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(14) }
            )
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
        val nText = smallText(13f, "#D0D4DC", false)
        notifApp = nApp
        notifTitle = nTitle
        notifText = nText
        val nCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(nApp)
            addView(nTitle)
            addView(nText)
        }
        val nTop = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(nIcon, LinearLayout.LayoutParams(dp(42), dp(42)))
            addView(
                nCol,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            )
        }
        val replyBtn = pillButton("Reply") { startReply() }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
            visibility = View.GONE
            addView(replyBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(34)))
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
        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            addView(input, LinearLayout.LayoutParams(0, dp(38), 1f))
            addView(
                sendBtn,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(38)).apply {
                    marginStart = dp(8)
                }
            )
        }
        notifInputRow = inputRow
        val nRow = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
            visibility = View.GONE
            addView(
                nTop,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                btnRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            )
            addView(
                inputRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(8) }
            )
        }
        notifRow = nRow

        // Music card
        val mArt = artView(0.22f)
        cardArt = mArt
        val mTitle = smallText(15f, "#FFFFFF", true)
        val mArtist = smallText(12f, "#D0D4DC", false)
        cardTitle = mTitle
        cardArtist = mArtist
        val mText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(mTitle)
            addView(mArtist)
        }
        val mEq = EqView(this)
        cardEq = mEq
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(mArt, LinearLayout.LayoutParams(dp(52), dp(52)))
            addView(
                mText,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(12)
                }
            )
            addView(mEq, LinearLayout.LayoutParams(dp(34), dp(28)))
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
        val controls = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(prevBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
            addView(
                pBtn,
                LinearLayout.LayoutParams(dp(52), dp(52)).apply {
                    marginStart = dp(16)
                    marginEnd = dp(16)
                }
            )
            addView(nextBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        }
        val mCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(18), dp(8), dp(18), dp(8))
            visibility = View.GONE
            addView(
                topRow,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(
                controls,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(4) }
            )
        }
        musicCard = mCard

        // Compact music (shown inside the small pill)
        val artSize = maxOf(dp(12), smallH - dp(12))
        val eqH = maxOf(dp(8), (smallH * 0.45f).toInt())
        val cArt = artView(0.5f).apply { visibility = View.GONE }
        compactArt = cArt
        val cEq = EqView(this).apply { visibility = View.GONE }
        compactEq = cEq

        // Bottom strip of small app icons (display only)
        val stripView = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        strip = stripView

        val shape = GradientDrawable().apply {
            setColor(Color.BLACK)
            cornerRadius = cornerPx
        }
        bg = shape

        val match = FrameLayout.LayoutParams.MATCH_PARENT
        val b = FrameLayout(this).apply {
            background = shape
            clipToOutline = true
            outlineProvider = pillOutline
            addView(bArt, FrameLayout.LayoutParams(match, match))
            addView(bScrim, FrameLayout.LayoutParams(match, match))
            addView(tv, FrameLayout.LayoutParams(match, match))
            addView(cRow, FrameLayout.LayoutParams(match, match))
            addView(nRow, FrameLayout.LayoutParams(match, match))
            addView(mCard, FrameLayout.LayoutParams(match, match))
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
                stripView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    dp(18),
                    Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                ).apply { bottomMargin = dp(4) }
            )
            setOnTouchListener { _, ev -> onPillTouch(ev) }
        }
        box = b

        val lp = WindowManager.LayoutParams(
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
        params = lp

        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        wm?.addView(b, lp)

        renderPage()
        refreshMusicViews()
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
                if (targetFrac == 1f && !replyMode) {
                    handler.removeCallbacks(collapseRunnable)
                    animateTo(0f)
                }
                return false
            }
            MotionEvent.ACTION_DOWN -> {
                downX = ev.x
                downY = ev.y
                downOnStrip = isOnStrip(ev.x, ev.y)
                if (targetFrac == 1f && !replyMode) handler.removeCallbacks(collapseRunnable)
                return true
            }
            MotionEvent.ACTION_UP -> {
                val dx = ev.x - downX
                val dy = ev.y - downY
                val swipe = frac > 0.5f && event == EVENT_PAGE &&
                    abs(dx) > dp(36) && abs(dx) > abs(dy) * 1.3f
                if (swipe) {
                    changePage(if (dx < 0) 1 else -1)
                } else if (abs(dx) < dp(12) && abs(dy) < dp(12)) {
                    if (!downOnStrip) onPillClick()
                }
                if (targetFrac == 1f && !replyMode) scheduleCollapse(collapseDelay())
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (targetFrac == 1f && !replyMode) scheduleCollapse(collapseDelay())
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
        if (replyMode) {
            animateTo(0f)
            return
        }
        if (event == EVENT_PAGE) {
            if (pageIsMusic(curPage)) {
                openMusicApp()
            } else {
                val item = pageNotif(curPage)
                if (item != null) openItem(item)
            }
        }
        animateTo(0f)
    }

    private fun expand() {
        handler.removeCallbacks(collapseRunnable)
        if (event == EVENT_NONE && pageCount() > 0) {
            event = EVENT_PAGE
            curPage = 0
            renderPage()
        }
        bigH = if (event == EVENT_PAGE) pageHeight() else dp(80)
        animateTo(1f)
        scheduleCollapse(collapseDelay())
    }

    private fun showPages(page: Int) {
        event = EVENT_PAGE
        curPage = page
        chargeShown = false
        renderPage()
        val h = pageHeight()
        if (frac > 0.99f) animateHeightTo(h) else bigH = h
        handler.removeCallbacks(collapseRunnable)
        animateTo(1f)
        scheduleCollapse(collapseDelay())
    }

    private fun startEvent(e: Int) {
        if (box == null) return
        if (replyMode) return
        event = e
        chargeShown = false
        updateChargeTexts()
        if (frac > 0.99f) animateHeightTo(dp(80)) else bigH = dp(80)
        handler.removeCallbacks(collapseRunnable)
        animateTo(1f)
        scheduleCollapse(CHARGE_COLLAPSE_MS)
    }

    private fun updateChargeTexts() {
        val unplug = event == EVENT_UNPLUG
        chargeTitle?.text = if (unplug) "Unplugged" else "Charging"
        chargeTitle?.setTextColor(Color.parseColor(if (unplug) "#FF9F43" else "#3DDC84"))
        chargeLevel?.text = "${batteryLevel()}%"
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
        val lp = params ?: return
        lp.width = (smallW + (bigW - smallW) * frac).toInt()
        lp.height = (smallH + (bigH - smallH) * frac).toInt()
        cornerPx = minOf(lp.height / 2f, dp(34).toFloat())
        bg?.cornerRadius = cornerPx
        val v = box ?: return
        v.invalidateOutline()
        if (v.isAttachedToWindow) wm?.updateViewLayout(v, lp)
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

        val showCompact = frac < 0.3f && event == EVENT_NONE && musicPlaying
        compactArt?.visibility = if (showCompact) View.VISIBLE else View.GONE
        compactEq?.visibility = if (showCompact) View.VISIBLE else View.GONE

        val musicPage = event == EVENT_PAGE && pageIsMusic(curPage)
        val artBg = musicPage && musicArt != null
        bgArt?.visibility = if (artBg) View.VISIBLE else View.GONE
        bgScrim?.visibility = if (artBg) View.VISIBLE else View.GONE

        if (frac <= 0.6f) {
            label?.visibility = View.GONE
            chargeRow?.visibility = View.GONE
            musicCard?.visibility = View.GONE
            notifRow?.visibility = View.GONE
            strip?.visibility = View.GONE
            return
        }

        when (event) {
            EVENT_PLUG, EVENT_UNPLUG -> {
                label?.visibility = View.GONE
                musicCard?.visibility = View.GONE
                notifRow?.visibility = View.GONE
                strip?.visibility = View.GONE
                chargeRow?.visibility = View.VISIBLE
                if (!chargeShown) {
                    chargeShown = true
                    chargeView?.play(batteryLevel(), event == EVENT_UNPLUG)
                }
            }
            EVENT_PAGE -> {
                label?.visibility = View.GONE
                chargeRow?.visibility = View.GONE
                musicCard?.visibility = if (musicPage) View.VISIBLE else View.GONE
                notifRow?.visibility =
                    if (!musicPage && pageNotif(curPage) != null) View.VISIBLE else View.GONE
                strip?.visibility = if (notifs.isNotEmpty()) View.VISIBLE else View.GONE
            }
            else -> {
                chargeRow?.visibility = View.GONE
                musicCard?.visibility = View.GONE
                notifRow?.visibility = View.GONE
                strip?.visibility = View.GONE
                if (label?.visibility != View.VISIBLE) showInfo()
            }
        }
    }

    private fun showInfo() {
        val level = batteryLevel()
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
        animator?.cancel()
        heightAnim?.cancel()
        chargeView?.stop()
        cardEq?.setActive(false)
        compactEq?.setActive(false)
        box?.let {
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
        musicCard = null
        cardArt = null
        cardTitle = null
        cardArtist = null
        cardEq = null
        playBtn = null
        compactArt = null
        compactEq = null
        notifRow = null
        notifIcon = null
        notifApp = null
        notifTitle = null
        notifText = null
        notifBtnRow = null
        notifInputRow = null
        notifInput = null
        strip = null
    }

    override fun onDestroy() {
        instance = null
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
        val prefs = getSharedPreferences("island", Context.MODE_PRIVATE)
        if (prefs.getBoolean("headsup_off", false)) {
            val ctx = applicationContext
            Thread { ShizukuHelper.setHeadsUp(ctx, true) }.start()
        }
        running = false
        super.onDestroy()
    }
}

internal class MediaButton(context: Context, initial: Int) : View(context) {

    companion object {
        const val PREV = 0
        const val PLAY = 1
        const val PAUSE = 2
        const val NEXT = 3
    }

    var icon: Int = initial
        set(value) {
            field = value
            invalidate()
        }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val target = if (pressed) 0.88f else 1f
        animate().scaleX(target).scaleY(target).setDuration(90).start()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val d = minOf(w, h)
        val big = icon == PLAY || icon == PAUSE

        paint.style = Paint.Style.FILL
        paint.color = if (big) Color.WHITE else Color.parseColor("#33FFFFFF")
        canvas.drawCircle(cx, cy, d / 2f, paint)

        val s = d * (if (big) 0.40f else 0.34f)
        paint.color = if (big) Color.BLACK else Color.WHITE
        paint.style = Paint.Style.FILL_AND_STROKE
        paint.strokeJoin = Paint.Join.ROUND
        paint.strokeWidth = d * 0.04f

        when (icon) {
            PLAY -> {
                path.reset()
                path.moveTo(cx - 0.38f * s, cy - 0.5f * s)
                path.lineTo(cx + 0.52f * s, cy)
                path.lineTo(cx - 0.38f * s, cy + 0.5f * s)
                path.close()
                canvas.drawPath(path, paint)
            }
            PAUSE -> {
                val bw = 0.26f * s
                val gap = 0.24f * s
                rect.set(cx - gap / 2f - bw, cy - 0.5f * s, cx - gap / 2f, cy + 0.5f * s)
                canvas.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, paint)
                rect.set(cx + gap / 2f, cy - 0.5f * s, cx + gap / 2f + bw, cy + 0.5f * s)
                canvas.drawRoundRect(rect, bw * 0.3f, bw * 0.3f, paint)
            }
            NEXT -> {
                path.reset()
                path.moveTo(cx - 0.5f * s, cy - 0.45f * s)
                path.lineTo(cx + 0.2f * s, cy)
                path.lineTo(cx - 0.5f * s, cy + 0.45f * s)
                path.close()
                canvas.drawPath(path, paint)
                rect.set(cx + 0.32f * s, cy - 0.45f * s, cx + 0.5f * s, cy + 0.45f * s)
                canvas.drawRoundRect(rect, 0.05f * s, 0.05f * s, paint)
            }
            else -> {
                path.reset()
                path.moveTo(cx + 0.5f * s, cy - 0.45f * s)
                path.lineTo(cx - 0.2f * s, cy)
                path.lineTo(cx + 0.5f * s, cy + 0.45f * s)
                path.close()
                canvas.drawPath(path, paint)
                rect.set(cx - 0.5f * s, cy - 0.45f * s, cx - 0.32f * s, cy + 0.45f * s)
                canvas.drawRoundRect(rect, 0.05f * s, 0.05f * s, paint)
            }
        }
    }
}

internal class EqView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val colors = intArrayOf(
        Color.parseColor("#3DDCFF"),
        Color.parseColor("#6A5CFF"),
        Color.parseColor("#FF5CA8"),
        Color.parseColor("#3DDC84")
    )
    private val speeds = intArrayOf(2, 3, 1, 3)
    private val offsets = floatArrayOf(0f, 1.3f, 2.6f, 4.1f)
    private val rect = RectF()
    private var phase = 0f
    private var active = false
    private var anim: ValueAnimator? = null

    fun setActive(a: Boolean) {
        if (a == active) return
        active = a
        if (a) {
            anim = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1400L
                repeatCount = ValueAnimator.INFINITE
                interpolator = LinearInterpolator()
                addUpdateListener {
                    phase = it.animatedValue as Float
                    invalidate()
                }
                start()
            }
        } else {
            anim?.cancel()
            anim = null
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        anim = null
        active = false
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        val n = colors.size
        val gap = width * 0.08f
        val bw = (width - gap * (n - 1)) / n
        val h = height.toFloat()
        for (i in 0 until n) {
            val f = if (active) {
                0.25f + 0.75f * abs(sin(2.0 * Math.PI * (phase * speeds[i]) + offsets[i])).toFloat()
            } else {
                0.18f
            }
            val bh = h * f
            val left = i * (bw + gap)
            rect.set(left, (h - bh) / 2f, left + bw, (h + bh) / 2f)
            paint.color = colors[i]
            canvas.drawRoundRect(rect, bw / 2f, bw / 2f, paint)
        }
    }
}

internal class ChargeView(context: Context) : View(context) {

    private val green = Color.parseColor("#3DDC84")
    private val orange = Color.parseColor("#FF9F43")

    private val ringBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#33FFFFFF")
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val slash = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = orange
    }
    private val boltPath = Path()
    private val oval = RectF()

    private var level = 0
    private var unplug = false
    private var t = 0f
    private var pulse = 0f
    private var introAnim: ValueAnimator? = null
    private var pulseAnim: ValueAnimator? = null

    fun play(batteryLevel: Int, isUnplug: Boolean) {
        stop()
        level = batteryLevel.coerceIn(0, 100)
        unplug = isUnplug
        t = 0f
        pulse = 0f
        introAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = if (isUnplug) 900L else 1100L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                t = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        pulseAnim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                pulse = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stop() {
        introAnim?.cancel()
        pulseAnim?.cancel()
        introAnim = null
        pulseAnim = null
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val k = f.coerceIn(0f, 1f)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * k).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * k).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * k).toInt()
        )
    }

    private fun drawBolt(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        dx: Float,
        scale: Float,
        alpha: Int
    ) {
        val l = cx - size / 2f + dx
        val tp = cy - size / 2f
        boltPath.reset()
        boltPath.moveTo(l + 0.55f * size, tp)
        boltPath.lineTo(l + 0.20f * size, tp + 0.55f * size)
        boltPath.lineTo(l + 0.45f * size, tp + 0.55f * size)
        boltPath.lineTo(l + 0.40f * size, tp + size)
        boltPath.lineTo(l + 0.80f * size, tp + 0.40f * size)
        boltPath.lineTo(l + 0.55f * size, tp + 0.40f * size)
        boltPath.close()
        boltPaint.alpha = alpha.coerceIn(0, 255)
        canvas.save()
        canvas.scale(scale, scale, cx, cy)
        canvas.drawPath(boltPath, boltPaint)
        canvas.restore()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val cx = w / 2f
        val cy = height / 2f
        val stroke = w * 0.09f
        val r = w / 2f - stroke * 1.2f
        ringBg.strokeWidth = stroke
        ring.strokeWidth = stroke
        oval.set(cx - r, cy - r, cx + r, cy + r)
        canvas.drawArc(oval, 0f, 360f, false, ringBg)

        if (!unplug) {
            ring.color = green
            canvas.drawArc(oval, -90f, 360f * level / 100f * t, false, ring)
            fill.color = green
            fill.alpha = (70 * (1f - pulse)).toInt()
            canvas.drawCircle(cx, cy, r * (0.45f + 0.5f * pulse), fill)
            drawBolt(canvas, cx, cy, r * 1.15f, 0f, 0.6f + 0.4f * t, 255)
        } else {
            ring.color = mix(green, orange, t)
            canvas.drawArc(oval, -90f, 360f * level / 100f, false, ring)
            fill.color = orange
            fill.alpha = (60 * (1f - pulse)).toInt()
            canvas.drawCircle(cx, cy, r * (0.4f + 0.6f * pulse), fill)
            val shake = sin(t * 25f) * (1f - t) * w * 0.06f
            drawBolt(canvas, cx, cy, r * 1.15f, shake, 1f, 255 - (120 * t).toInt())
            val p = (t * 1.5f - 0.3f).coerceIn(0f, 1f)
            val d = r * 0.65f
            slash.strokeWidth = stroke * 0.9f
            canvas.drawLine(cx - d, cy - d, cx - d + 2f * d * p, cy - d + 2f * d * p, slash)
        }
    }
}
