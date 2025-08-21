package com.example.lephucmfg

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import com.google.zxing.integration.android.IntentIntegrator

/**
 * Helper for QR scanning and field population.
 * Pass the caller Activity and all EditTexts to constructor.
 */
class ScanHelper(
    private val activity: Activity,
    private val scanLauncher: ActivityResultLauncher<Intent>,
    private val editFields: Map<String, EditText>,
    private val scanButton: View
) {
    // Order of fields for focus movement
    private val fieldOrder = listOf("edtStaffNo", "edtMcName", "edtJobNo", "edtProOrdNo", "edtSerial", "edtHeatNo")

    init {
        scanButton.setOnClickListener {
            // If no EditText has focus, focus the first field
            if (editFields.values.none { it.hasFocus() }) {
                fieldOrder.mapNotNull { editFields[it] }.firstOrNull()?.requestFocus()
            }
            //note// Launch custom scanner instead of default ZXing scanner
            //note// This provides fixed viewfinder overlay and gallery selection
            val intent = Intent(activity, CustomScanActivity::class.java)
            scanLauncher.launch(intent)
        }
    }

    fun handleScanResult(qrText: String) {
        val keyValuePairs = qrText.split('&').mapNotNull {
            val idx = it.indexOf('=')
            if (idx > 0) {
                val key = it.substring(0, idx).trim()
                val value = it.substring(idx + 1).trim()
                if (value.isNotEmpty()) key to value else null
            } else null
        }.toMap()

        if (keyValuePairs.isNotEmpty()) {
            // Map keys to EditText IDs, last occurrence wins
            for ((key, value) in keyValuePairs) {
                editFields[key]?.setText(value)
            }
            // Move focus to next empty field
            moveFocusToNextEmpty()
            Toast.makeText(activity, "đã nhập", Toast.LENGTH_SHORT).show()
        } else {
            // No key-value, treat as value for focused field
            val focused = editFields.values.firstOrNull { it.hasFocus() }
            if (focused != null) {
                focused.setText(qrText)
                moveFocusToNextEmpty()
                Toast.makeText(activity, "đã nhập", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun moveFocusToNextEmpty() {
        for (id in fieldOrder) {
            val field = editFields[id]
            if (field != null && field.text.isNullOrEmpty()) {
                field.requestFocus()
                hideKeyboard(field)
                return
            }
        }
        // All fields filled: clear focus and hide keyboard
        activity.currentFocus?.clearFocus()
        hideKeyboard(activity.currentFocus)
    }

    private fun hideKeyboard(view: View?) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view?.windowToken, 0)
    }
}
