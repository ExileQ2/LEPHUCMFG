package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity
import com.example.lephucmfg.databinding.ActivityMainBinding
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var lastScannedQrContent: String? = null

    private val barcodeLauncher: ActivityResultLauncher<ScanOptions> =
        registerForActivityResult(ScanContract()) { result ->
            result?.contents?.let {
                binding.txtResult.text = "Scanned: $it"
                lastScannedQrContent = it
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ✅ ViewBinding setup
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ✅ QR Scan Button
        binding.btnScanQR.setOnClickListener {
            val options = ScanOptions()
            options.setPrompt("Scan a QR Code")
            options.setBeepEnabled(true)
            options.setOrientationLocked(false)
            barcodeLauncher.launch(options)
        }

        // ✅ "Nhật ký máy" Button → go to MachineLogActivity
        binding.btnOpenMachineLog.setOnClickListener {
            val intent = Intent(this, MachineLogActivity::class.java)
            lastScannedQrContent?.let { qrContent ->
                intent.putExtra(MachineLogActivity.EXTRA_QR_CONTENT, qrContent)
            }
            startActivity(intent)
        }
    }
}
