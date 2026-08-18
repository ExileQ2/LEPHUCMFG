package com.example.lephucmfg.data

data class AndroidVersionDto(
    val currentVersion: String = "",
    val latestVersion: String = "",
    val downloadUrl: String = "",
    val forceUpdate: Boolean = false
)

data class AndroidReleaseDto(
    val versionCode: Int,
    val versionName: String,
    val downloadUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val minSupportedVersionCode: Int = 1,
    val releaseNotes: String = "",
    val publishedAtUtc: String = ""
)
