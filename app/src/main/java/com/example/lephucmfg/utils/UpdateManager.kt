package com.example.lephucmfg.utils

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.R
import com.example.lephucmfg.data.AndroidVersionDto
import com.example.lephucmfg.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

class UpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "UpdateManager"
        private const val APK_FILE_NAME = "app_update.apk"
    }

    private val updateService = RetrofitClient.updateService

    fun checkForUpdates() {
        // Use lifecycleScope if context is an Activity, otherwise use a different approach
        if (context is androidx.lifecycle.LifecycleOwner) {
            context.lifecycleScope.launch {
                try {
                    val response = updateService.checkVersion()
                    if (response.isSuccessful) {
                        response.body()?.let { versionInfo ->
                            handleVersionResponse(versionInfo)
                        }
                    } else {
                        Log.e(TAG, "Failed to check version: ${response.code()}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error checking for updates", e)
                }
            }
        }
    }

    private fun handleVersionResponse(versionInfo: AndroidVersionDto) {
        val currentVersion = getCurrentAppVersion()
        val latestVersion = versionInfo.latestVersion

        Log.d(TAG, "Current version: $currentVersion, Latest version: $latestVersion")

        if (isUpdateAvailable(currentVersion, latestVersion)) {
            showUpdateDialog(versionInfo)
        } else {
            Log.d(TAG, "App is up to date")
        }
    }

    private fun getCurrentAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "1.0.0"
        } catch (e: PackageManager.NameNotFoundException) {
            "1.0.0"
        }
    }

    private fun isUpdateAvailable(currentVersion: String, latestVersion: String): Boolean {
        // Simple version comparison - you can make this more sophisticated if needed
        return currentVersion != latestVersion
    }

    private fun showUpdateDialog(versionInfo: AndroidVersionDto) {
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Cập nhật ứng dụng")
        builder.setMessage("Phiên bản mới (${versionInfo.latestVersion}) đã có sẵn.")

        builder.setPositiveButton("Cập nhật") { _, _ ->
            if (checkInstallPermission()) {
                startDownload(versionInfo)
            } else {
                requestInstallPermission()
            }
        }

        // Remove the "Later" button and make dialog non-cancelable
        builder.setCancelable(false)

        builder.show()
    }

    private fun checkInstallPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    private fun requestInstallPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
            Toast.makeText(context, "Vui lòng bật 'Cài đặt ứng dụng không rõ nguồn gốc' và thử lại", Toast.LENGTH_LONG).show()
        }
    }

    private fun startDownload(versionInfo: AndroidVersionDto) {
        // Create custom progress dialog
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_download_progress, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progressBar)
        val txtProgress = dialogView.findViewById<TextView>(R.id.txtProgress)
        val txtDownloadInfo = dialogView.findViewById<TextView>(R.id.txtDownloadInfo)

        val progressDialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        progressDialog.show()

        if (context is androidx.lifecycle.LifecycleOwner) {
            context.lifecycleScope.launch {
                try {
                    txtDownloadInfo.text = "Đang kết nối đến máy chủ..."

                    val success = downloadApkWithProgress(progressBar, txtProgress, txtDownloadInfo)
                    progressDialog.dismiss()

                    if (success) {
                        txtDownloadInfo.text = "Tải xuống hoàn tất! Đang cài đặt..."
                        installApk()
                    } else {
                        Toast.makeText(context, "Tải xuống thất bại. Vui lòng thử lại.", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    progressDialog.dismiss()
                    Log.e(TAG, "Download error", e)
                    Toast.makeText(context, "Lỗi tải xuống: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun downloadApkWithProgress(
        progressBar: ProgressBar,
        txtProgress: TextView,
        txtDownloadInfo: TextView
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val response = updateService.downloadApk()
                if (response.isSuccessful) {
                    response.body()?.let { body ->
                        withContext(Dispatchers.Main) {
                            txtDownloadInfo.text = "Đang tải..."
                        }
                        saveApkFileWithProgress(body, progressBar, txtProgress, txtDownloadInfo)
                        true
                    } ?: false
                } else {
                    Log.e(TAG, "Download failed: ${response.code()}")
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download exception", e)
                false
            }
        }
    }

    private suspend fun saveApkFileWithProgress(
        body: ResponseBody,
        progressBar: ProgressBar,
        txtProgress: TextView,
        txtDownloadInfo: TextView
    ): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val apkFile = File(context.getExternalFilesDir(null), APK_FILE_NAME)
                val inputStream: InputStream = body.byteStream()
                val outputStream = FileOutputStream(apkFile)

                val totalBytes = body.contentLength()
                var downloadedBytes = 0L
                val buffer = ByteArray(4096)
                var bytesRead: Int

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloadedBytes += bytesRead

                    // Update progress on main thread
                    val progress = if (totalBytes > 0) {
                        ((downloadedBytes * 100) / totalBytes).toInt()
                    } else {
                        0
                    }

                    withContext(Dispatchers.Main) {
                        progressBar.progress = progress
                        txtProgress.text = "$progress%"

                        val downloadedMB = downloadedBytes / (1024 * 1024)
                        val totalMB = totalBytes / (1024 * 1024)
                        txtDownloadInfo.text = if (totalBytes > 0) {
                            "Đã tải $downloadedMB  / $totalMB "
                        } else {
                            "Đã tải $downloadedMB "
                        }
                    }
                }

                outputStream.close()
                inputStream.close()

                withContext(Dispatchers.Main) {
                    progressBar.progress = 100
                    txtProgress.text = "100%"
                    txtDownloadInfo.text = "Tải xuống hoàn tất!"
                }

                Log.d(TAG, "APK saved to: ${apkFile.absolutePath}")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Error saving APK", e)
                false
            }
        }
    }

    private fun installApk() {
        try {
            val apkFile = File(context.getExternalFilesDir(null), APK_FILE_NAME)
            if (apkFile.exists()) {
                val apkUri = FileProvider.getUriForFile(
                    context,
                    "com.example.lephucmfg.fileprovider",
                    apkFile
                )

                val installIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(apkUri, "application/vnd.android.package-archive")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

                // Clean up old APK files before starting installation
                cleanupOldApkFiles()

                context.startActivity(installIntent)
            } else {
                Toast.makeText(context, "Không tìm thấy tệp APK", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error installing APK", e)
            Toast.makeText(context, "Lỗi cài đặt: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun cleanupOldApkFiles() {
        try {
            val filesDir = context.getExternalFilesDir(null)
            Log.d(TAG, "=== CLEANUP OLD APK FILES ===")
            Log.d(TAG, "Files directory: ${filesDir?.absolutePath}")

            val allFiles = filesDir?.listFiles()
            Log.d(TAG, "Total files in directory: ${allFiles?.size ?: 0}")

            allFiles?.forEach { file ->
                Log.d(TAG, "Checking file: ${file.name} (size: ${file.length()} bytes)")
                if (file.name.endsWith(".apk") && file.name != APK_FILE_NAME) {
                    Log.d(TAG, "Found old APK to delete: ${file.name}")
                    val deleted = file.delete()
                    Log.d(TAG, "Deletion result for ${file.name}: $deleted")
                    if (deleted) {
                        Log.i(TAG, "✅ Successfully deleted old APK: ${file.name}")
                    } else {
                        Log.w(TAG, "❌ Failed to delete old APK: ${file.name}")
                    }
                } else if (file.name.endsWith(".apk")) {
                    Log.d(TAG, "Keeping current APK: ${file.name}")
                }
            }
            Log.d(TAG, "=== END CLEANUP OLD APK FILES ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up old APK files", e)
        }
    }

    // Enhanced cleanup method with detailed logging
    fun cleanupAfterInstall() {
        try {
            Log.d(TAG, "=== CLEANUP AFTER INSTALL ===")
            val filesDir = context.getExternalFilesDir(null)
            Log.d(TAG, "Files directory: ${filesDir?.absolutePath}")

            val apkFile = File(context.getExternalFilesDir(null), APK_FILE_NAME)
            Log.d(TAG, "Looking for APK file: ${apkFile.absolutePath}")
            Log.d(TAG, "APK file exists: ${apkFile.exists()}")

            if (apkFile.exists()) {
                Log.d(TAG, "APK file size before deletion: ${apkFile.length()} bytes")
                val deleted = apkFile.delete()
                Log.d(TAG, "Deletion result: $deleted")

                if (deleted) {
                    Log.i(TAG, "✅ Successfully cleaned up APK after install: ${APK_FILE_NAME}")
                } else {
                    Log.w(TAG, "❌ Failed to clean up APK after install: ${APK_FILE_NAME}")
                }

                // Verify deletion
                Log.d(TAG, "Verification - APK file still exists: ${apkFile.exists()}")
            } else {
                Log.d(TAG, "No APK file found to clean up")
            }

            // List remaining files for verification
            val remainingFiles = filesDir?.listFiles()?.filter { it.name.endsWith(".apk") }
            Log.d(TAG, "Remaining APK files after cleanup: ${remainingFiles?.size ?: 0}")
            remainingFiles?.forEach { file ->
                Log.d(TAG, "Remaining APK: ${file.name} (${file.length()} bytes)")
            }

            Log.d(TAG, "=== END CLEANUP AFTER INSTALL ===")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up APK after install", e)
        }
    }
}
