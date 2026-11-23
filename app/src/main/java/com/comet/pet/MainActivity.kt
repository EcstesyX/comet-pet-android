package com.comet.pet

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private val tag = "CometPetMain"
    private lateinit var prefs: SharedPreferences
    private var layoutPermission: LinearLayout? = null
    private var btnPermission: AppCompatButton? = null
    private var switchAutostart: SwitchCompat? = null
    private var switchClipboard: SwitchCompat? = null
    private var sliderSize: SeekBar? = null
    private var tvSizeLabel: TextView? = null
    private var btnTogglePet: AppCompatButton? = null
    private var ivLogo: ImageView? = null

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUIState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)

            prefs = getSharedPreferences(CometOverlayService.PREFS_NAME, Context.MODE_PRIVATE)

            layoutPermission = findViewById(R.id.layoutPermission)
            btnPermission = findViewById(R.id.btnPermission)
            switchAutostart = findViewById(R.id.switchAutostart)
            switchClipboard = findViewById(R.id.switchClipboard)
            sliderSize = findViewById(R.id.sliderSize)
            tvSizeLabel = findViewById(R.id.tvSizeLabel)
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

        sliderSize?.let { seekBar ->
            val currentSize = prefs.getInt(CometOverlayService.PREF_PET_SIZE_DP, 96).coerceIn(60, 140)
            seekBar.progress = currentSize
            tvSizeLabel?.text = "Размер лисёнка: ${currentSize} dp"

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                    val finalVal = progress.coerceIn(60, 140)
                    tvSizeLabel?.text = "Размер лисёнка: ${finalVal} dp"
                    prefs.edit().putInt(CometOverlayService.PREF_PET_SIZE_DP, finalVal).apply()
                    if (isServiceRunning()) {
                        val intent = Intent(this@MainActivity, CometOverlayService::class.java).apply {
                            action = CometOverlayService.ACTION_UPDATE_SETTINGS
                        }
                        startService(intent)
                    }
                }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
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
                false
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
                btn.setBackgroundColor(color)
            } else {
                btn.text = getString(R.string.btn_start_pet)
                val color = ContextCompat.getColor(this, R.color.primary)
                btn.setBackgroundColor(color)
            }
        }
    }

    private fun isServiceRunning(): Boolean {
        return CometServiceState.isRunning
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
