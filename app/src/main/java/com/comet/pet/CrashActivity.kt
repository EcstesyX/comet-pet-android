package com.comet.pet

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CrashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash)

        val errorDetails = intent.getStringExtra("error") ?: "Неизвестная ошибка"

        val tvDetails = findViewById<TextView>(R.id.tvCrashDetails)
        val btnCopy = findViewById<Button>(R.id.btnCopyError)

        tvDetails.text = errorDetails

        btnCopy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Crash Error", errorDetails))
            Toast.makeText(this, "Текст ошибки скопирован!", Toast.LENGTH_SHORT).show()
        }
    }
}
