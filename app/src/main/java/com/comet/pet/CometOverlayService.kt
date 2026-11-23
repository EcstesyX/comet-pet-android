package com.comet.pet

import android.app.*
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class CometOverlayService : Service(), PetView.Listener {

    companion object {
        const val CHANNEL_ID = "comet_pet_overlay_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_FEED = "com.comet.pet.ACTION_FEED"
        const val ACTION_SLEEP = "com.comet.pet.ACTION_SLEEP"
        const val ACTION_STOP = "com.comet.pet.ACTION_STOP"
        const val ACTION_UPDATE_SETTINGS = "com.comet.pet.ACTION_UPDATE_SETTINGS"

        const val PREFS_NAME = "comet_pet_prefs"
        const val PREF_PET_SIZE_DP = "pref_pet_size_dp"
        const val PREF_CLIPBOARD_REACTION = "pref_clipboard_reaction"
        const val PREF_AUTOSTART = "pref_autostart"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: SharedPreferences
    private var petView: PetView? = null
    private var spriteBank: SpriteBank? = null
    private var clipboardManager: ClipboardManager? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        if (!prefs.getBoolean(PREF_CLIPBOARD_REACTION, true)) return@OnPrimaryClipChangedListener
        val clip = clipboardManager?.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this)?.toString()
            if (!text.isNullOrBlank()) {
                val reaction = ClipboardBrain.analyze(text)
                petView?.reactClipboard(reaction)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        clipboardManager = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        setupOverlay()

        clipboardManager?.addPrimaryClipChangedListener(clipListener)
    }

    private fun setupOverlay() {
        val sizeDp = prefs.getInt(PREF_PET_SIZE_DP, 96)
        val sizePx = (sizeDp * resources.displayMetrics.density).toInt()

        val layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 200
            y = 1200
        }

        spriteBank = SpriteBank(this, sizePx)
        petView = PetView(this, windowManager, layoutParams, spriteBank!!).apply {
            this.petSizePx = sizePx
            this.listener = this@CometOverlayService
            start()
        }

        try {
            windowManager.addView(petView, layoutParams)
        } catch (_: Exception) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_FEED -> petView?.feed()
            ACTION_SLEEP -> petView?.toggleSleep()
            ACTION_STOP -> stopSelf()
            ACTION_UPDATE_SETTINGS -> {
                val sizeDp = prefs.getInt(PREF_PET_SIZE_DP, 96)
                val sizePx = (sizeDp * resources.displayMetrics.density).toInt()
                petView?.petSizePx = sizePx
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Лисичка Комета",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Активный экранный питомец"
                setShowBadge(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val feedIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, CometOverlayService::class.java).apply { action = ACTION_FEED },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sleepIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, CometOverlayService::class.java).apply { action = ACTION_SLEEP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, CometOverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Комета гуляет по экрану")
            .setContentText("Нажмите для открытия настроек питомца")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(R.drawable.ic_notification, "Покормить", feedIntent)
            .addAction(R.drawable.ic_notification, "Сон / Подъём", sleepIntent)
            .addAction(R.drawable.ic_notification, "Закрыть", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDismissRequested() {
        stopSelf()
    }

    override fun onFeedRequested() {
        petView?.feed()
    }

    override fun onSleepToggleRequested() {
        petView?.toggleSleep()
    }

    override fun onDestroy() {
        super.onDestroy()
        clipboardManager?.removePrimaryClipChangedListener(clipListener)
        petView?.stop()
        if (petView != null && petView?.isAttachedToWindow == true) {
            try {
                windowManager.removeView(petView)
            } catch (_: Exception) {}
        }
        petView = null
    }
}
