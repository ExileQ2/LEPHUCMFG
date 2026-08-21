package com.example.lephucmfg.utils

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.R
import com.example.lephucmfg.data.AndroidReleaseDto
import com.example.lephucmfg.network.RetrofitClient
import com.google.gson.Gson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

class UpdateManager(private val activity: Activity) {
    private val service = RetrofitClient.updateService
    private val preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val checking = AtomicBoolean(false)
    private val updating = AtomicBoolean(false)
    private var updateDialog: AlertDialog? = null

    suspend fun checkForUpdates(manual: Boolean = false) {
        val owner = activity as? LifecycleOwner ?: return
        if (!owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (updating.get()) return
        if (!manual && !UpdatePolicy.shouldCheckAutomatically(
                lastSuccessfulCheckMs = preferences.getLong(KEY_LAST_CHECK, 0),
                nowMs = System.currentTimeMillis(),
                intervalMs = AUTO_CHECK_INTERVAL_MS
            )
        ) return
        if (!checking.compareAndSet(false, true)) return

        try {
            val installed = installedVersionCode()
            val response = service.latestRelease(installed)
            if (!response.isSuccessful) error("Máy chủ trả về ${response.code()}")
            val release = response.body() ?: error("Thiếu thông tin bản phát hành")
            preferences.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            when (UpdatePolicy.evaluate(installed, release.versionCode, release.minSupportedVersionCode)) {
                UpdateDecision.NONE -> if (manual) toast("Ứng dụng đang ở bản mới nhất")
                UpdateDecision.OPTIONAL -> showUpdateDialog(release, required = false)
                UpdateDecision.REQUIRED -> showUpdateDialog(release, required = true)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Update check failed", error)
            if (manual) toast("Không kiểm tra được cập nhật: ${error.message}")
        } finally {
            checking.set(false)
        }
    }

    fun resumePendingUpdate() {
        val raw = preferences.getString(KEY_PENDING_RELEASE, null) ?: return
        val release = runCatching { gson.fromJson(raw, AndroidReleaseDto::class.java) }.getOrNull()
        if (release == null) {
            clearPending()
            return
        }
        if (canInstallPackages()) {
            clearPending()
            downloadAndInstall(release)
        }
    }

    fun cleanupAfterInstall() {
        updateDirectory().listFiles()?.forEach { file ->
            if (file.extension.equals("apk", true) || file.name.endsWith(".part")) file.delete()
        }
    }

    private fun showUpdateDialog(release: AndroidReleaseDto, required: Boolean) {
        if (updateDialog?.isShowing == true) return
        val notes = release.releaseNotes.trim().ifBlank { "Cải thiện độ ổn định và tính năng." }
        val builder = AlertDialog.Builder(activity)
            .setTitle(if (required) "Cần cập nhật" else "Có bản cập nhật")
            .setMessage("Phiên bản ${release.versionName}\n\n$notes")
            .setPositiveButton("Tải và cài") { _, _ -> ensurePermissionThenDownload(release) }
            .setCancelable(!required)
        if (!required) builder.setNegativeButton("Để sau", null)
        val dialog = builder.create()
        dialog.setOnDismissListener {
            if (updateDialog === dialog) updateDialog = null
        }
        updateDialog = dialog
        dialog.show()
    }

    private fun ensurePermissionThenDownload(release: AndroidReleaseDto) {
        if (canInstallPackages()) {
            downloadAndInstall(release)
            return
        }
        preferences.edit().putString(KEY_PENDING_RELEASE, gson.toJson(release)).apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}"))
            )
            toast("Bật 'Cho phép từ nguồn này'; app sẽ tự tiếp tục")
        }
    }

    private fun downloadAndInstall(release: AndroidReleaseDto) {
        val owner = activity as? LifecycleOwner ?: return
        if (!updating.compareAndSet(false, true)) return
        val view = LayoutInflater.from(activity).inflate(R.layout.dialog_download_progress, null)
        val progress = view.findViewById<ProgressBar>(R.id.progressBar)
        val percent = view.findViewById<TextView>(R.id.txtProgress)
        val detail = view.findViewById<TextView>(R.id.txtDownloadInfo)
        val dialog = AlertDialog.Builder(activity).setView(view).setCancelable(false).create()
        dialog.show()

        owner.lifecycleScope.launch {
            try {
                val apk = downloadVerifiedRelease(release) { downloaded, total ->
                    val value = if (total > 0) ((downloaded * 100) / total).toInt() else 0
                    progress.progress = value
                    percent.text = "$value%"
                    detail.text = "${downloaded / MB} MB / ${if (total > 0) total / MB else "?"} MB"
                }
                install(apk)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.e(TAG, "Update failed", error)
                toast("Cập nhật thất bại: ${error.message}")
            } finally {
                dialog.dismiss()
                updating.set(false)
            }
        }
    }

    private suspend fun downloadVerifiedRelease(
        release: AndroidReleaseDto,
        onProgress: suspend (Long, Long) -> Unit
    ): File = withContext(Dispatchers.IO) {
        val directory = updateDirectory()
        directory.listFiles()?.filter { it.name.endsWith(".part") }?.forEach(File::delete)
        val partial = File(directory, "LPMFG-${release.versionCode}.apk.part")
        val target = File(directory, "LPMFG-${release.versionCode}.apk")
        target.delete()

        val response = service.downloadRelease(release.downloadUrl)
        if (!response.isSuccessful) error("Tải APK lỗi ${response.code()}")
        val body = response.body() ?: error("APK rỗng")
        val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
        body.byteStream().use { input ->
            FileOutputStream(partial).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var downloaded = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                    downloaded += count
                    if (downloaded % (512 * 1024) < count) withContext(Dispatchers.Main) {
                        onProgress(downloaded, total)
                    }
                }
                output.fd.sync()
            }
        }
        if (release.sizeBytes > 0 && partial.length() != release.sizeBytes) {
            partial.delete()
            error("Kích thước APK không khớp")
        }
        val actualHash = sha256(partial)
        if (!actualHash.equals(release.sha256, ignoreCase = true)) {
            partial.delete()
            error("APK không đúng mã kiểm tra")
        }
        if (!partial.renameTo(target)) error("Không hoàn tất được tệp tải")
        verifyPackage(target, release.versionCode)
        withContext(Dispatchers.Main) { onProgress(target.length(), target.length()) }
        target
    }

    private fun verifyPackage(apk: File, expectedVersionCode: Int) {
        val archive = packageInfo(apk.absolutePath) ?: error("APK không hợp lệ")
        if (archive.packageName != activity.packageName) error("APK không đúng ứng dụng")
        if (versionCode(archive) != expectedVersionCode.toLong()) error("APK sai phiên bản")
        val installed = packageInfo(null) ?: error("Không đọc được chữ ký hiện tại")
        if (signatureDigest(installed) != signatureDigest(archive)) error("Chữ ký APK không khớp")
    }

    private fun install(apk: File) {
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        activity.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(archivePath: String?): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else PackageManager.GET_SIGNATURES
        return if (archivePath == null) {
            activity.packageManager.getPackageInfo(activity.packageName, flags)
        } else {
            activity.packageManager.getPackageArchiveInfo(archivePath, flags)
        }
    }

    @Suppress("DEPRECATION")
    private fun signatureDigest(info: PackageInfo): String {
        val bytes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = info.signingInfo ?: error("APK thiếu chữ ký")
            val signatures = if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners
            } else signingInfo.signingCertificateHistory
            signatures.firstOrNull()?.toByteArray()
        } else info.signatures?.firstOrNull()?.toByteArray()
        return bytes?.let(::sha256) ?: error("APK thiếu chữ ký")
    }

    private fun installedVersionCode(): Int = packageInfo(null)?.let(::versionCode)?.toInt() ?: 0

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()

    private fun sha256(file: File): String = file.inputStream().use { input ->
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            digest.update(buffer, 0, count)
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it) }

    private fun updateDirectory() = File(activity.cacheDir, "updates").apply { mkdirs() }
    private fun canInstallPackages() = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || activity.packageManager.canRequestPackageInstalls()
    private fun clearPending() = preferences.edit().remove(KEY_PENDING_RELEASE).apply()
    private fun toast(text: String) = Toast.makeText(activity, text, Toast.LENGTH_LONG).show()

    companion object {
        const val AUTO_CHECK_INTERVAL_MS = 5 * 60 * 1000L
        const val AUTO_CHECK_POLL_MS = 60 * 1000L

        private const val TAG = "UpdateManager"
        private const val PREFS = "android_update_v2"
        private const val KEY_LAST_CHECK = "last_successful_check"
        private const val KEY_PENDING_RELEASE = "pending_release"
        private const val MB = 1024 * 1024
    }
}
