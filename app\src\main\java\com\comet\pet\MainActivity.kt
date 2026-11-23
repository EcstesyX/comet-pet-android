package com.comet.pet

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.slider.Slider

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var layoutPermission: LinearLayout
    private lateinit var btnPermission: MaterialButton
    private lateinit var switchAutostart: MaterialSwitch
    private lateinit var switchClipboard: MaterialSwitch
    private lateinit var sliderSize: Slider
    private lateinit var btnTogglePet: MaterialButton
    private lateinit var ivLogo: ImageView

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updateUIState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    }

    override fun onResume() {
        super.onResume()
        updateUIState()
    }

    private fun setupListeners() {
        btnPermission.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                overlayPermissionLauncher.launch(intent)
            }
        }

        switchAutostart.isChecked = prefs.getBoolean(CometOverlayService.PREF_AUTOSTART, true)
        switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(CometOverlayService.PREF_AUTOSTART, isChecked).apply()
        }

        switchClipboard.isChecked = prefs.getBoolean(CometOverlayService.PREF_CLIPBOARD_REACTION, true)
        switchClipboard.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean(CometOverlayService.PREF_CLIPBOARD_REACTION, isChecked).apply()
        }

        val currentSize = prefs.getInt(CometOverlayService.PREF_PET_SIZE_DP, 96).toFloat()
        sliderSize.value = currentSize.coerceIn(sliderSize.valueFrom, sliderSize.valueTo)
        sliderSize.addOnChangeListener { _, value, _ ->
            prefs.edit().putInt(CometOverlayService.PREF_PET_SIZE_DP, value.toInt()).apply()
            if (isServiceRunning()) {
                val intent = Intent(this, CometOverlayService::class.java).apply {
                    action = CometOverlayService.ACTION_UPDATE_SETTINGS
                }
                startService(intent)
            }
        }

        btnTogglePet.setOnClickListener {
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
                btnTogglePet.text = getString(R.string.btn_start_pet)
                btnTogglePet.setBackgroundColor(getColor(R.color.primary))
            } else {
                val intent = Intent(this, CometOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent)
                } else {
                    startService(intent)
                }
                btnTogglePet.text = getString(R.string.btn_stop_pet)
                btnTogglePet.setBackgroundColor(getColor(R.color.surface_variant))
            }
        }
    }

    private fun updateUIState() {
        val hasPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }

        layoutPermission.visibility = if (hasPermission) View.GONE else View.VISIBLE

        if (isServiceRunning()) {
            btnTogglePet.text = getString(R.string.btn_stop_pet)
            btnTogglePet.setBackgroundColor(getColor(R.color.surface_variant))
        } else {
            btnTogglePet.text = getString(R.string.btn_start_pet)
            btnTogglePet.setBackgroundColor(getColor(R.color.primary))
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(): Boolean {
        val manager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (CometOverlayService::class.java.name == service.service.className) {
                return true
            }
        }
        return false
    }

    private fun startLogoAnimation() {
        val handler = Handler(Looper.getMainLooper())
        var isBlink = false
        val runnable = object : Runnable {
            override fun run() {
                isBlink = !isBlink
                ivLogo.setImageResource(if (isBlink) R.drawable.idle_blink else R.drawable.idle_1)
                handler.postDelayed(this, if (isBlink) 200 else 3000)
            }
        }
        handler.postDelayed(runnable, 2000)
    }
}
