package com.example.lephucmfg.utils

import android.content.Context
import android.content.SharedPreferences

class StaffPreferences(context: Context) {

    companion object {
        private const val PREFS_NAME = "staff_preferences"
        private const val KEY_STAFF_NUMBER = "staff_number"
        private const val KEY_STAFF_INFO = "staff_info"
    }

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Save staff number to SharedPreferences
     */
    fun saveStaffNumber(staffNumber: String) {
        sharedPreferences.edit()
            .putString(KEY_STAFF_NUMBER, staffNumber)
            .apply()
    }

    /**
     * Get saved staff number from SharedPreferences
     */
    fun getStaffNumber(): String {
        return sharedPreferences.getString(KEY_STAFF_NUMBER, "") ?: ""
    }

    /**
     * Save staff info (name, job, workplace) to SharedPreferences
     */
    fun saveStaffInfo(staffInfo: String) {
        sharedPreferences.edit()
            .putString(KEY_STAFF_INFO, staffInfo)
            .apply()
    }

    /**
     * Get saved staff info from SharedPreferences
     */
    fun getStaffInfo(): String {
        return sharedPreferences.getString(KEY_STAFF_INFO, "") ?: ""
    }

    /**
     * Clear all staff preferences
     */
    fun clearStaffPreferences() {
        sharedPreferences.edit()
            .remove(KEY_STAFF_NUMBER)
            .remove(KEY_STAFF_INFO)
            .apply()
    }

    /**
     * Check if staff number is saved
     */
    fun hasStaffNumber(): Boolean {
        return getStaffNumber().isNotEmpty()
    }
}
