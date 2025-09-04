package com.example.lephucmfg.data

data class AndroidVersionDto(
    val currentVersion: String,
    val latestVersion: String,
    val downloadUrl: String,
    val forceUpdate: Boolean
)
