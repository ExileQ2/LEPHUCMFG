package com.example.lephucmfg.utils

import android.content.res.Resources

object LoadingStates {
    const val LOADING = "Đang xử lý..."

    fun getLoadingColor(resources: Resources): Int {
        return resources.getColor(android.R.color.holo_orange_dark)
    }
}
