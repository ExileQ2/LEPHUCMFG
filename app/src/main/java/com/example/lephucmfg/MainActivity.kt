package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

        // Clean up any leftover APK files from previous installations
        updateManager.cleanupAfterInstall()

        // Set current app version in bottom left corner
        val txtVersion = findViewById<TextView>(R.id.txtVersion)
        txtVersion.text = getDisplayedVersion()

        startAutomaticUpdateChecks()

        // Load changelog content from assets
        val txtChangeLog = findViewById<TextView>(R.id.txtChangeLog)
        loadChangelogFromAssets(txtChangeLog)

        findViewById<Button>(R.id.btnAbTesting).setOnClickListener {
            startActivity(Intent(this, ABTestingActivity::class.java))
        }
        findViewById<Button>(R.id.btnMachineLog).setOnClickListener {
            startActivity(Intent(this, MachineLogActivity::class.java))
        }
        findViewById<Button>(R.id.btnCheckUpdate).setOnClickListener {
            updateManager.checkForUpdates(manual = true)
        }
    }

    override fun onResume() {
        super.onResume()
        if (::updateManager.isInitialized) updateManager.resumePendingUpdate()
    }

    private fun startAutomaticUpdateChecks() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                updateManager.checkForUpdates()
                while (isActive) {
                    delay(UpdateManager.AUTO_CHECK_INTERVAL_MS)
                    updateManager.checkForUpdates()
                }
            }
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    private fun getDisplayedVersion(): String {
        val base = getAppVersion()
        return "v$base"
    }

    private fun loadChangelogFromAssets(textView: TextView) {
        try {
            val inputStream = assets.open("changelog.txt")
            val text = inputStream.bufferedReader().use { it.readText() }
            val spannable = android.text.SpannableString(text)

            // Color "CHANGELOG:" in dark red
            val changelogHeader = "CHANGELOG:"
            val idxHeader = text.indexOf(changelogHeader)
            if (idxHeader != -1) {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#B71C1C")),
                    idxHeader,
                    idxHeader + changelogHeader.length,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Color version lines in blue
            val regexVersion = Regex("LEPHUCMFG v[\\d.]+")
            regexVersion.findAll(text).forEach {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#1976D2")),
                    it.range.first,
                    it.range.last + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            // Color section headers (lines starting with '>') in green
            val regexSection = Regex(">[^\n]+")
            regexSection.findAll(text).forEach {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#388E3C")),
                    it.range.first,
                    it.range.last + 1,
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }

            textView.text = spannable
        } catch (e: IOException) {
            textView.text = "Error loading changelog: ${e.message}"
        }
    }
}
