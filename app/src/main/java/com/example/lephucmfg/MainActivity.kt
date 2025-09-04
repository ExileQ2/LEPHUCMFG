package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.IOException

import com.example.lephucmfg.ABTestingActivity
import com.example.lephucmfg.utils.UpdateManager

class MainActivity : AppCompatActivity() {

    private lateinit var updateManager: UpdateManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.frontpage)

        // Initialize update manager
        updateManager = UpdateManager(this)

        // Check for updates when app starts
        updateManager.checkForUpdates()

        // Load changelog content from assets
        val txtChangeLog = findViewById<TextView>(R.id.txtChangeLog)
        loadChangelogFromAssets(txtChangeLog)

        findViewById<Button>(R.id.btnAbTesting).setOnClickListener {
            startActivity(Intent(this, ABTestingActivity::class.java))
        }
        findViewById<Button>(R.id.btnMachineLog).setOnClickListener {
            startActivity(Intent(this, MachineLogActivity::class.java))
        }
        findViewById<Button>(R.id.btnMaterialLog).setOnClickListener {
            startActivity(Intent(this, MaterialLogActivity::class.java))
        }
    }

    private fun loadChangelogFromAssets(textView: TextView) {
        try {
            val inputStream = assets.open("changelog.txt")
            val text = inputStream.bufferedReader().use { it.readText() }
            textView.text = text
        } catch (e: IOException) {
            textView.text = "Error loading changelog: ${e.message}"
        }
    }
}
