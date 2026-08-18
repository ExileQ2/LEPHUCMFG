package com.example.lephucmfg

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * Deliberately basic scanner backed by ZXing Embedded.
 * Camera lifecycle, permission handling, preview and decoding stay inside the
 * proven library flow used by the legacy app.
 */
class CustomScanActivity : CaptureActivity()
