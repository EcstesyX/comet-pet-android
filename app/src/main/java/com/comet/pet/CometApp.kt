package com.comet.pet

import android.app.Application
import android.content.Intent
import android.util.Log

class CometApp : Application() {

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("CometApp", "FATAL CRASH on thread ${thread.name}: ${throwable.message}", throwable)
            try {
                val intent = Intent(this, CrashActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    putExtra("error", Log.getStackTraceString(throwable))
                }
                startActivity(intent)
            } catch (e: Exception) {
                Log.e("CometApp", "Failed to launch CrashActivity", e)
            }
        }
    }
}
