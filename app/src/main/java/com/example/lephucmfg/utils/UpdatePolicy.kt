package com.example.lephucmfg.utils

enum class UpdateDecision { NONE, OPTIONAL, REQUIRED }

object UpdatePolicy {
    fun evaluate(
        installedVersionCode: Int,
        latestVersionCode: Int,
        minimumSupportedVersionCode: Int
    ): UpdateDecision = when {
        installedVersionCode >= latestVersionCode -> UpdateDecision.NONE
        installedVersionCode < minimumSupportedVersionCode -> UpdateDecision.REQUIRED
        else -> UpdateDecision.OPTIONAL
    }

    fun shouldCheckAutomatically(
        lastSuccessfulCheckMs: Long,
        nowMs: Long,
        intervalMs: Long
    ): Boolean = lastSuccessfulCheckMs <= 0L ||
        nowMs < lastSuccessfulCheckMs ||
        nowMs - lastSuccessfulCheckMs >= intervalMs
}
