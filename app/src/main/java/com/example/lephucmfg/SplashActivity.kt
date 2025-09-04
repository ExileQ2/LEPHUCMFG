package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // The splash background with your image will be shown automatically
        // due to the SplashTheme applied to this activity

        // Wait for 1 second, then go to MainActivity (frontpage)
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(Intent(this, MainActivity::class.java))
            finish() // Close splash activity so user can't go back to it
        }, 750) // 1000ms = 1 second
    }
}
