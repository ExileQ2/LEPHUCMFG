package com.example.lephucmfg

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.network.RetrofitClient
import com.example.lephucmfg.utils.LoadingStates
import com.google.gson.annotations.SerializedName
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import retrofit2.http.GET
import retrofit2.http.Path

class MaterialLogActivity : AppCompatActivity() {
    // --- API interface for fetching staff info ("Thợ") ---
    interface StaffApi {
        @GET("/api/GetStaff/{staffNo}")
        suspend fun getStaff(@Path("staffNo") staffNo: Int): StaffInfo?
    }

    // --- API interface for fetching heat number info ---
    interface HeatNoApi {
        @GET("/api/GetInfoHeatNo/{HeatNo}")
        suspend fun getHeatNoInfo(@Path("HeatNo") heatNo: String): List<HeatNoInfo>?
    }

    // --- Data class for staff info ---
    data class StaffInfo(
        @SerializedName("fullName") val fullName: String?,
        @SerializedName("workJob") val workJob: String?,
        @SerializedName("workPlace") val workPlace: String?
    )

    // --- Data class for heat number info ---
    data class HeatNoInfo(
        @SerializedName("material") val material: String?,
        @SerializedName("existQty") val existQty: Int?,
        @SerializedName("inpSize1") val inpSize1: String?,
        @SerializedName("inpSize2") val inpSize2: String?,
        @SerializedName("qty") val qty: Int?,
        @SerializedName("notes") val notes: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_material_log)

        val edtStaffNo = findViewById<EditText>(R.id.edtStaffNo)
        val txtStaffInfo = findViewById<TextView>(R.id.txtStaffInfo)
        val edtHeatNo = findViewById<EditText>(R.id.edtHeatNo)
        val txtHeatInfo = findViewById<TextView>(R.id.txtHeatInfo)
        val btnScan = findViewById<Button>(R.id.btnScan)
        val layoutHeatInfoButtons = findViewById<LinearLayout>(R.id.layoutHeatInfoButtons)

        val staffApi = RetrofitClient.retrofitPublic.create(StaffApi::class.java)
        val heatNoApi = RetrofitClient.retrofitPublic.create(HeatNoApi::class.java)

        // --- QR Scan integration ---
        val editFields = mapOf(
            "staffNo" to edtStaffNo,
            "heatno" to edtHeatNo,
            // fallback: also allow direct EditText id mapping
            "edtStaffNo" to edtStaffNo,
            "edtHeatNo" to edtHeatNo
        )
        var scanHelper: ScanHelper? = null
        val scanLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val intent = result.data
                val qrText = intent?.getStringExtra("SCAN_RESULT")
                    ?: intent?.getStringExtra("SCAN_RESULT_ORIGINAL")
                    ?: intent?.getStringExtra("SCAN_RESULT_RAW")
                if (!qrText.isNullOrEmpty()) {
                    scanHelper?.handleScanResult(qrText)
                }
            }
        }
        scanHelper = ScanHelper(this, scanLauncher, editFields, btnScan)

        // Staff number focus change listener
        edtStaffNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val staffNoStr = edtStaffNo.text.toString().trim()
                if (staffNoStr.isNotEmpty()) {
                    try {
                        val staffNo = staffNoStr.toInt()
                        txtStaffInfo.text = getString(R.string.loading)
                        txtStaffInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        lifecycleScope.launch {
                            try {
                                val info = staffApi.getStaff(staffNo)
                                if (info != null) {
                                    txtStaffInfo.text = listOfNotNull(info.fullName, info.workJob, info.workPlace).joinToString(", ")
                                    txtStaffInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))
                                } else {
                                    txtStaffInfo.text = getString(R.string.data_not_found)
                                    txtStaffInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))
                                }
                            } catch (_: Exception) {
                                txtStaffInfo.text = getString(R.string.data_not_found)
                                txtStaffInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))
                            }
                        }
                    } catch (_: NumberFormatException) {
                        txtStaffInfo.text = getString(R.string.data_not_found)
                    }
                } else {
                    txtStaffInfo.text = ""
                }
            }
        }

        // HeatNo focus change listener
        edtHeatNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val heatNoStr = edtHeatNo.text.toString().trim()
                // Clear previous buttons
                layoutHeatInfoButtons.removeAllViews()

                if (heatNoStr.isNotEmpty()) {
                    txtHeatInfo.text = getString(R.string.loading)
                    txtHeatInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                    lifecycleScope.launch {
                        try {
                            val heatInfoList = heatNoApi.getHeatNoInfo(heatNoStr)
                            if (heatInfoList != null && heatInfoList.isNotEmpty()) {
                                txtHeatInfo.text = ""

                                // Create clickable buttons for each heat info item
                                heatInfoList.forEach { info ->
                                    val material = info.material ?: ""
                                    val existQty = info.existQty?.toString() ?: ""
                                    val inpSize1 = info.inpSize1 ?: ""
                                    val inpSize2 = info.inpSize2 ?: ""
                                    val qty = info.qty?.toString() ?: ""
                                    val notes = info.notes ?: ""

                                    val buttonText = "$material, SL vật liệu $existQty, $inpSize1 * $inpSize2, SL con hàng $qty, $notes"

                                    val button = Button(this@MaterialLogActivity).apply {
                                        text = buttonText
                                        setBackgroundResource(R.drawable.clickable_button_background)
                                        setTextColor(ContextCompat.getColor(context, android.R.color.white))
                                        layoutParams = LinearLayout.LayoutParams(
                                            LinearLayout.LayoutParams.MATCH_PARENT,
                                            LinearLayout.LayoutParams.WRAP_CONTENT
                                        ).apply {
                                            setMargins(0, 0, 0, 8)
                                        }

                                        setOnClickListener {
                                            // Button click handler - no action for now
                                        }
                                    }

                                    layoutHeatInfoButtons.addView(button)
                                }
                            } else {
                                txtHeatInfo.text = getString(R.string.data_not_found)
                                txtHeatInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))
                            }
                        } catch (_: Exception) {
                            txtHeatInfo.text = getString(R.string.data_not_found)
                            txtHeatInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))
                        }
                    }
                } else {
                    txtHeatInfo.text = ""
                }
            }
        }
    }
}