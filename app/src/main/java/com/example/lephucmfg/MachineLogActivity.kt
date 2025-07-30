package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.network.RetrofitClient
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
import okhttp3.ResponseBody
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

class MachineLogActivity : AppCompatActivity() {
    // --- API interface for fetching staff info ("Thợ") ---
    interface StaffApi {
        @GET("/api/GetStaff/{staffNo}")
        suspend fun getStaff(@Path("staffNo") staffNo: Int): StaffInfo?
    }
    // --- Data class for staff info ---
    data class StaffInfo(
        @SerializedName("fullName") val fullName: String?,
        @SerializedName("workJob") val workJob: String?,
        @SerializedName("workPlace") val workPlace: String?
    )
    // --- API interface for fetching machine info ("Mã máy") ---
    interface MachineApi {
        @GET("/api/GetMachine/{mcName}")
        suspend fun getMachine(@Path("mcName") mcName: String): MachineInfo?
    }
    // --- Data class for machine info ---
    data class MachineInfo(
        @SerializedName("model") val model: String?,
        @SerializedName("status") val status: String?
    )
    // --- API interface for fetching production order (LSX/ProOrdNo) ---
    interface ProOrdApi {
        @GET("/api/Laylsx/{A}")
        suspend fun getProOrd(@Path("A") jobNo: String): ResponseBody
    }
    // --- Data class for production order DTO ---
    data class ProOrdDto(
        @SerializedName("proOrdNo") val proOrdNo: String?
    )
    // --- API interface for fetching serial info (GetSerial) ---
    interface SerialApi {
        @GET("/api/GetSerial/{proOrdNo}")
        suspend fun getSerial(@Path("proOrdNo") proOrdNo: String): List<SerialDto>
    }
    // --- Data class for serial info ---
    data class SerialDto(
        @SerializedName("serial") val serial: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_machine_log)

        // --- UI references for "Thợ" (Staff) block ---
        val edtStaffNo = findViewById<EditText>(R.id.edtStaffNo)
        val txtStaffInfo = findViewById<TextView>(R.id.txtStaffInfo)
        val api = RetrofitClient.retrofitPublic.create(StaffApi::class.java)

        // --- Block for handling Staff ("Thợ") input and validation ---
        edtStaffNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val staffNoStr = edtStaffNo.text.toString().trim()
                if (staffNoStr.isNotEmpty()) {
                    try {
                        val staffNo = staffNoStr.toInt()
                        // Fetch staff info asynchronously
                        lifecycleScope.launch {
                            try {
                                val info = api.getStaff(staffNo)
                                if (info != null) {
                                    // Display staff info if found
                                    txtStaffInfo.text = listOfNotNull(info.fullName, info.workJob, info.workPlace).joinToString(", ")
                                } else {
                                    // Show error if staff not found
                                    txtStaffInfo.setText(R.string.invalid_staff)
                                }
                            } catch (e: Exception) {
                                // Show error if API call fails
                                txtStaffInfo.setText(R.string.invalid_staff)
                            }
                        }
                    } catch (e: NumberFormatException) {
                        // Show error if input is not a number
                        txtStaffInfo.setText(R.string.invalid_staff)
                    }
                } else {
                    // Clear staff info if input is empty
                    txtStaffInfo.text = ""
                }
            }
        }
        // --- UI references for "Mã máy" (Machine) block ---
        val edtMcName = findViewById<EditText>(R.id.edtMcName)
        val txtMachineInfo = findViewById<TextView>(R.id.txtMachineInfo)
        // --- UI reference for serial info below LSX (ProOrdNo) ---
        val edtProOrdNo = findViewById<EditText>(R.id.edtProOrdNo)
        val txtSerialInfo = findViewById<TextView>(R.id.txtSerialInfo) // Add this TextView in your layout XML below edtProOrdNo
        val machineApi = RetrofitClient.retrofitPublic.create(MachineApi::class.java)
        val serialApi = RetrofitClient.retrofitPublic.create(SerialApi::class.java)
        val proOrdApi = RetrofitClient.retrofitPublic.create(ProOrdApi::class.java)

        // --- Block for handling Machine Code ("Mã máy") input and validation ---
        edtMcName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val mcName = edtMcName.text.toString().trim()
                if (mcName.isNotEmpty()) {
                    // Launch coroutine to fetch machine info asynchronously
                    lifecycleScope.launch {
                        try {
                            val info = machineApi.getMachine(mcName)
                            if (info != null) {
                                // Display machine model and status if found
                                txtMachineInfo.text = listOfNotNull(info.model, info.status).joinToString(", ")
                            } else {
                                // Show error if machine not found
                                txtMachineInfo.setText(R.string.invalid_staff)
                            }
                        } catch (e: Exception) {
                            // Show error if API call fails
                            txtMachineInfo.setText(R.string.invalid_staff)
                        }
                    }
                } else {
                    // Clear machine info if input is empty
                    txtMachineInfo.text = ""
                }
            }
        }
        // --- UI references for LSX (ProOrdNo) and result grid block ---
        val edtJobNo = findViewById<EditText>(R.id.edtJobNo)
        val layoutProOrdNoResults = findViewById<android.widget.GridLayout>(R.id.layoutProOrdNoResults)

        // --- Block for handling LSX (ProOrdNo) input and displaying results in a grid ---
        edtJobNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val jobNo = edtJobNo.text.toString().trim()
                if (jobNo.isNotEmpty()) {
                    // Fetch production order list asynchronously
                    lifecycleScope.launch {
                        try {
                            val responseBody = proOrdApi.getProOrd(jobNo)
                            val json = responseBody.string()
                            val gson = Gson()
                            val jsonElement = JsonParser.parseString(json)
                            val proOrdList = when {
                                jsonElement.isJsonArray -> jsonElement.asJsonArray.mapNotNull {
                                    gson.fromJson(it, ProOrdDto::class.java).proOrdNo
                                }
                                jsonElement.isJsonObject -> listOfNotNull(gson.fromJson(jsonElement, ProOrdDto::class.java).proOrdNo)
                                else -> emptyList()
                            }
                            // Clear previous results in the grid
                            layoutProOrdNoResults.removeAllViews()
                            if (proOrdList.isNotEmpty()) {
                                // For each result, create a clickable TextView and add to grid
                                proOrdList.forEach { proOrdNo ->
                                    val tv = TextView(this@MachineLogActivity)
                                    tv.text = proOrdNo
                                    tv.setPadding(24, 16, 24, 16)
                                    tv.setBackgroundResource(android.R.drawable.dialog_holo_light_frame)
                                    tv.setTextColor(resources.getColor(android.R.color.black))
                                    val params = android.widget.GridLayout.LayoutParams()
                                    params.setMargins(8, 8, 8, 8)
                                    params.width = 0
                                    params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                                    params.columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                                    tv.layoutParams = params
                                    // On click, copy result to LSX (ProOrdNo) input
                                    tv.setOnClickListener {
                                        edtProOrdNo.setText(proOrdNo)
                                    }
                                    layoutProOrdNoResults.addView(tv)
                                }
                            } else {
                                // Show error if no results found
                                val tv = TextView(this@MachineLogActivity)
                                tv.text = getString(R.string.invalid_staff)
                                tv.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                                layoutProOrdNoResults.addView(tv)
                            }
                        } catch (e: Exception) {
                            // Show error if API call fails
                            layoutProOrdNoResults.removeAllViews()
                            val tv = TextView(this@MachineLogActivity)
                            tv.text = getString(R.string.invalid_staff)
                            tv.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                            layoutProOrdNoResults.addView(tv)
                        }
                    }
                } else {
                    // Clear grid if input is empty
                    layoutProOrdNoResults.removeAllViews()
                }
            }
        }
        // --- Block for handling LSX (ProOrdNo) serial info fetching ---
        edtProOrdNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val proOrdNo = edtProOrdNo.text.toString().trim()
                if (proOrdNo.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val serialList = serialApi.getSerial(proOrdNo)
                            if (serialList.isNotEmpty()) {
                                txtSerialInfo.text = serialList.joinToString(", ") { it.serial ?: "" }
                            } else {
                                txtSerialInfo.text = getString(R.string.invalid_staff)
                            }
                        } catch (e: Exception) {
                            txtSerialInfo.text = getString(R.string.invalid_staff)
                        }
                    }
                } else {
                    txtSerialInfo.text = ""
                }
            }
        }
        val edtSerial = findViewById<EditText>(R.id.edtSerial)
        val btnScan = findViewById<Button>(R.id.btnScan)

        // Map QR keys to EditText IDs (future-proof, add new keys here)
        val editFields = mapOf(
            "staffNo" to edtStaffNo,
            "mcName" to edtMcName,
            "jobNo" to edtJobNo,
            "proOrdNo" to edtProOrdNo,
            "serial" to edtSerial,
            // fallback: also allow direct EditText id mapping
            "edtStaffNo" to edtStaffNo,
            "edtMcName" to edtMcName,
            "edtJobNo" to edtJobNo,
            "edtProOrdNo" to edtProOrdNo,
            "edtSerial" to edtSerial
        )

        // Register ActivityResultLauncher for QR scan (must be defined before ScanHelper)
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

        // Setup ScanHelper (button click launches scan)
        scanHelper = ScanHelper(this, scanLauncher, editFields, btnScan)
    }
}
