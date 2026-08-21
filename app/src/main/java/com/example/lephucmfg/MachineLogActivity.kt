package com.example.lephucmfg

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.lephucmfg.ui.machinelog.MachineLogScreen
import com.example.lephucmfg.ui.machinelog.MachineLogViewModel
import com.example.lephucmfg.ui.machinelog.ScanTarget
import com.example.lephucmfg.ui.theme.LEPHUCMFGTheme
import com.example.lephucmfg.utils.startAutomaticUpdateChecks
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MachineLogActivity : ComponentActivity() {
    private val viewModel: MachineLogViewModel by viewModels()
    private var scanTarget = ScanTarget.STAFF

    private val scanner = registerForActivityResult(ScanContract()) { result ->
        result.contents?.let { viewModel.applyScan(it, scanTarget) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAutomaticUpdateChecks()
        setContent {
            LEPHUCMFGTheme {
                MachineLogScreen(
                    viewModel = viewModel,
                    onBack = ::finish,
                    onScan = { target ->
                        scanTarget = target
                        scanner.launch(
                            ScanOptions()
                                .setCaptureActivity(CustomScanActivity::class.java)
                                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                                .setPrompt("Đưa mã QR vào trong khung")
                                .setBeepEnabled(true)
                                .setOrientationLocked(true)
                        )
                    }
                )
            }
        }
    }
}
