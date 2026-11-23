package com.comet.pet

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private val tag = "CometPetMain"
    private lateinit var prefs: SharedPreferences
    private var layoutPermission: LinearLayout? = null
    private var btnPermission: MaterialButton? = null
    private var switchAutostart: MaterialSwitch? = null
    private var switchClipboard: MaterialSwitch? = null
    private var sliderSize: Slider? = null
    private var btnTogglePet: MaterialButton? = null
    private var ivLogo: ImageView? = null

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUIState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Global uncaught exception logger
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e(tag, "FATAL CRASH in MainActivity: ${throwable.message}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        try {
            setContentView(R.layout.activity_main)

            prefs = getSharedPreferences(CometOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

            layoutPermission = findViewById(R.id.layoutPermission)
            btnPermission = findViewById(R.id.btnPermission)
            switchAutostart = findViewById(R.id.switchAutostart)
            switchClipboard = findViewById(R.id.switchClipboard)
            sliderSize = findViewById(R.id.sliderSize)
            btnTogglePet = findViewById(R.id.btnTogglePet)
            ivLogo = findViewById(R.id.ivLogo)

            setupListeners()
            startLogoAnimation()
        } catch (t: Throwable) {
            Log.e(tag, "Error during onCreate: ${t.message}", t)
            showCrashDialog(t)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            updateUIState()
        } catch (t: Throwable) {
            Log.e(tag, "Error in onResume: ${t.message}", t)
        }
    }

    private fun setupListeners() {
        btnPermission?.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error opening overlay settings", e)
                try {
                    val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
                    startActivity(fallbackIntent)
                } catch (_: Exception) {}
            }
        }

        switchAutostart?.isChecked = prefs.getBoolean(CometOverlayService.PREF_AUTOSTART, true)
        switchAutostart?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(CometOverlayService.PREF_AUTOSTART, isChecked).apply()
        }

        switchClipboard?.isChecked = prefs.getBoolean(CometOverlayService.PREF_CLIPBOARD_REACTION, true)
        switchClipboard?.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(CometOverlayService.PREF_CLIPBOARD_REACTION, isChecked).apply()
        }

        sliderSize?.let { slider ->
            try {
                slider.valueFrom = 60.0f
                slider.valueTo = 140.0f
                slider.stepSize = 10.0f
                val currentSize = prefs.getInt(CometOverlayService.PREF_PET_SIZE_DP, 96).toFloat()
                slider.value = currentSize.coerceIn(slider.valueFrom, slider.valueTo)

                slider.addOnChangeListener { _, value, _ ->
                    prefs.edit().putInt(CometOverlayService.PREF_PET_SIZE_DP, value.toInt()).apply()
                    if (isServiceRunning()) {
                        val intent = Intent(this, CometOverlayService::class.java).apply {
                            action = CometOverlayService.ACTION_UPDATE_SETTINGS
                        }
                        startService(intent)
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error setting up slider", e)
            }
        }

        btnTogglePet?.setOnClickListener {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Сначала разрешите отображение поверх других окон", Toast.LENGTH_SHORT).show()
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    overlayPermissionLauncher.launch(intent)
                    return@setOnClickListener
                }

                if (isServiceRunning()) {
                    val intent = Intent(this, CometOverlayService::class.java)
                    stopService(intent)
                    updateButtonState(false)
                } else {
                    val intent = Intent(this, CometOverlayService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(intent)
                    } else {
                        startService(intent)
                    }
                    updateButtonState(true)
                }
            } catch (e: Exception) {
                Log.e(tag, "Error toggling pet service", e)
                Toast.makeText(this, "Ошибка запуска: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateUIState() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                Settings.canDrawOverlays(this)
            } catch (e: Exception) {
                true
            }
        } else {
            true
        }

        layoutPermission?.visibility = if (hasPermission) View.GONE else View.VISIBLE
        updateButtonState(isServiceRunning())
    }

    private fun updateButtonState(running: Boolean) {
        btnTogglePet?.let { btn ->
            if (running) {
                btn.text = getString(R.string.btn_stop_pet)
                val color = ContextCompat.getColor(this, R.color.surface_variant)
                btn.backgroundTintList = ColorStateList.valueOf(color)
            } else {
                btn.text = getString(R.string.btn_start_pet)
                val color = ContextCompat.getColor(this, R.color.primary)
                btn.backgroundTintList = ColorStateList.valueOf(color)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        return try {
            val manager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
            val services = manager.getRunningServices(Int.MAX_VALUE) ?: return false
            services.any { it.service.className == CometOverlayService::class.java.name }
        } catch (e: Exception) {
            Log.e(tag, "Error checking running services", e)
            false
        }
    }

    private fun startLogoAnimation() {
        val logo = ivLogo ?: return
        val handler = Handler(Looper.getMainLooper())
        var isBlink = false
        val runnable = object : Runnable {
            override fun run() {
                isBlink = !isBlink
                try {
                    logo.setImageResource(if (isBlink) R.drawable.idle_blink else R.drawable.idle_1)
                } catch (_: Exception) {}
                handler.postDelayed(this, if (isBlink) 200 else 3200)
            }
        }
        handler.postDelayed(runnable, 2000)
    }

    private fun showCrashDialog(t: Throwable) {
        try {
            AlertDialog.Builder(this)
                .setTitle("Ошибка инициализации")
                .setMessage("Не удалось запустить: ${t.javaClass.simpleName}: ${t.message}")
                .setPositiveButton("OK") { d, _ -> d.dismiss() }
                .show()
        } catch (_: Exception) {}
    }
}
