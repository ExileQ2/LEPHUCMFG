package com.example.lephucmfg.utils

import androidx.activity.ComponentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

fun ComponentActivity.startAutomaticUpdateChecks(
    updateManager: UpdateManager = UpdateManager(this),
    cleanupAfterInstall: Boolean = false
) {
    if (cleanupAfterInstall) updateManager.cleanupAfterInstall()
    lifecycleScope.launch {
        repeatOnLifecycle(Lifecycle.State.RESUMED) {
            updateManager.resumePendingUpdate()
            while (isActive) {
                updateManager.checkForUpdates()
                delay(UpdateManager.AUTO_CHECK_POLL_MS)
            }
        }
    }
}
