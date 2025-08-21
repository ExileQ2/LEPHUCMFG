package com.example.lephucmfg

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

//note// Custom scan activity with fixed viewfinder overlay and gallery support
//note// Replaces default ZXing scanner while maintaining result compatibility
class CustomScanActivity : AppCompatActivity() {

    private lateinit var viewFinder: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var btnGallery: Button
    private lateinit var cameraExecutor: ExecutorService

    //note// Gallery launcher for selecting QR images from device storage
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { processImageFromGallery(it) }
    }

    //note// Camera permission launcher to request camera access
    private val cameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_custom_scan)

        viewFinder = findViewById(R.id.viewFinder)
        overlayView = findViewById(R.id.overlayView)
        btnGallery = findViewById(R.id.btnGallery)

        //note// Gallery button click opens image picker for QR scanning from photos
        btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        cameraExecutor = Executors.newSingleThreadExecutor()

        //note// Check camera permission and start camera or request permission
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    //note// Check if camera permission is granted
    private fun allPermissionsGranted() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    //note// Initialize camera with preview and image analysis for QR detection
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            //note// Image analysis use case for real-time QR code detection
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, QRCodeAnalyzer { qrText ->
                        //note// Return scanned QR text and finish activity
                        returnScanResult(qrText)
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer)
            } catch(exc: Exception) {
                Log.e("CustomScanActivity", "Camera binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    //note// Process QR codes from gallery images using ML Kit
    private fun processImageFromGallery(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            val image = InputImage.fromBitmap(bitmap, 0)

            val scanner = BarcodeScanning.getClient()
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    if (barcodes.isNotEmpty()) {
                        val qrText = barcodes.first().rawValue
                        if (!qrText.isNullOrEmpty()) {
                            returnScanResult(qrText)
                        } else {
                            Toast.makeText(this, "No QR code found in image", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(this, "No QR code found in image", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to scan image", Toast.LENGTH_SHORT).show()
                }
        } catch (e: Exception) {
            Toast.makeText(this, "Error processing image", Toast.LENGTH_SHORT).show()
        }
    }

    //note// Return scan result to calling activity using ZXing-compatible result format
    private fun returnScanResult(qrText: String) {
        val resultIntent = Intent().apply {
            putExtra("SCAN_RESULT", qrText)
            putExtra("SCAN_RESULT_ORIGINAL", qrText)
            putExtra("SCAN_RESULT_RAW", qrText)
        }
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    //note// QR code analyzer that only processes codes within the overlay rectangle
    private inner class QRCodeAnalyzer(private val onQRCodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {

        //note// Add experimental annotation for imageProxy.image access
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

                val scanner = BarcodeScanning.getClient()
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        //note// Filter barcodes to only accept those intersecting the overlay rectangle
                        for (barcode in barcodes) {
                            if (barcode.rawValue != null && isWithinOverlay(barcode, imageProxy)) {
                                onQRCodeDetected(barcode.rawValue!!)
                                break
                            }
                        }
                    }
                    .addOnFailureListener {
                        Log.e("QRCodeAnalyzer", "Barcode scanning failed", it)
                    }
                    .addOnCompleteListener {
                        imageProxy.close()
                    }
            } else {
                imageProxy.close()
            }
        }

        //note// Check if detected barcode intersects with the fixed overlay rectangle
        private fun isWithinOverlay(barcode: Barcode, imageProxy: ImageProxy): Boolean {
            val boundingBox = barcode.boundingBox ?: return false

            //note// Get overlay rectangle dimensions (centered rounded rect)
            val overlayRect = overlayView.getOverlayRect()

            //note// Convert camera coordinates to overlay coordinates
            val scaleX = overlayView.width.toFloat() / imageProxy.width.toFloat()
            val scaleY = overlayView.height.toFloat() / imageProxy.height.toFloat()

            val scaledLeft = boundingBox.left * scaleX
            val scaledTop = boundingBox.top * scaleY
            val scaledRight = boundingBox.right * scaleX
            val scaledBottom = boundingBox.bottom * scaleY

            //note// Check if barcode rectangle intersects with overlay rectangle
            return !(scaledRight < overlayRect.left ||
                    scaledLeft > overlayRect.right ||
                    scaledBottom < overlayRect.top ||
                    scaledTop > overlayRect.bottom)
        }
    }
}

//note// Custom overlay view that draws a fixed rounded rectangle viewfinder
class OverlayView @JvmOverloads constructor(
    context: android.content.Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 4f
    }

    private val backgroundPaint = Paint().apply {
        color = Color.BLACK
        alpha = 128 // Semi-transparent overlay
    }

    private val clearPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }

    private lateinit var overlayRect: RectF

    //note// Calculate overlay rectangle dimensions (centered, 70% of view size)
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val rectSize = minOf(w, h) * 0.7f
        val left = (w - rectSize) / 2f
        val top = (h - rectSize) / 2f

        overlayRect = RectF(left, top, left + rectSize, top + rectSize)
    }

    //note// Draw semi-transparent overlay with clear rounded rectangle in center
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (::overlayRect.isInitialized) {
            //note// Draw dark background overlay
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

            //note// Clear the center rectangle to show camera preview
            canvas.drawRoundRect(overlayRect, 20f, 20f, clearPaint)

            //note// Draw white border around the viewfinder
            canvas.drawRoundRect(overlayRect, 20f, 20f, paint)
        }
    }

    //note// Return overlay rectangle for barcode intersection checking
    fun getOverlayRect(): RectF {
        return if (::overlayRect.isInitialized) overlayRect else RectF()
    }
}
