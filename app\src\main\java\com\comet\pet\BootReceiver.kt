package com.comet.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(CometOverlayService.PREFS_NAME, Context.MODE_PRIVATE)
            val autostart = prefs.getBoolean(CometOverlayService.PREF_AUTOSTART, true)

            // Can only draw overlay if permission is granted
            if (autostart && Settings.canDrawOverlays(context)) {
                val serviceIntent = Intent(context, CometOverlayService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
