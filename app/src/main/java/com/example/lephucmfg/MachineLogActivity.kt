package com.example.lephucmfg

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.network.RetrofitClient
import com.example.lephucmfg.utils.LoadingStates
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
import retrofit2.http.POST
import retrofit2.http.Body

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
        @GET("/api/Laylsx/{JobControlNo}")
        suspend fun getProOrd(@Path("JobControlNo") jobControlNo: String): ResponseBody
    }
    // --- Data class for production order DTO ---
    data class ProOrdDto(
        @SerializedName("jobControlNo") val jobControlNo: String?
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
    // --- API interface for fetching process number (ProcessNo) ---
    interface ProcessNoApi {
        @GET("/api/GetProcessNoChuaKetThuc/{StaffNo}/{McName}")
        suspend fun getProcessNo(@Path("StaffNo") staffNo: String, @Path("McName") mcName: String): ProcessNoDto
    }
    data class ProcessNoDto(
        @SerializedName("processNo") val processNo: String?,
        @SerializedName("note") val note: String?,
        @SerializedName("serial2") val serial2: String?,
        @SerializedName("proOrdNo2") val proOrdNo2: String?
    )
    // --- API interface for posting machine log ---
    interface PostNhatKyGiaCongApi {
        @POST("/api/postNhatKyGiaCong")
        suspend fun postLog(@Body body: NhatKyGiaCongDto): retrofit2.Response<Void>
    }
    data class NhatKyGiaCongDto(
        val processNo: String?,
        val jobControlNo: String?,
        val staffNo: String?,
        val mcName: String?,
        val note: String?,
        val proOrdNo: String?,
        val serial: String?,
        val setup: Boolean,
        val rework: Boolean,
        val qtyGood: Int,
        val qtyReject: Int,
        val qtyRework: Int
    )

    // Shared function to process machine and staff data
    private suspend fun processMachineAndStaff() {
        val edtStaffNo = findViewById<EditText>(R.id.edtStaffNo)
        val edtMcName = findViewById<EditText>(R.id.edtMcName)
        val txtMachineInfo = findViewById<TextView>(R.id.txtMachineInfo)
        val txtProcessNo = findViewById<TextView>(R.id.txtProcessNo)
        val txtMachineRunning = findViewById<TextView>(R.id.txtMachineRunning)
        val layoutSmallEdits = findViewById<LinearLayout>(R.id.layoutSmallEdits)
        val edtJobNo = findViewById<EditText>(R.id.edtJobNo)
        val edtProOrdNo = findViewById<EditText>(R.id.edtProOrdNo)
        val edtSerial = findViewById<EditText>(R.id.edtSerial)
        val edtGhiChu = findViewById<EditText>(R.id.edtGhiChu)
        val layoutProOrdNoResults = findViewById<android.widget.GridLayout>(R.id.layoutProOrdNoResults)
        val machineApi = RetrofitClient.retrofitPublic.create(MachineApi::class.java)

        val staffNo = edtStaffNo.text.toString().trim()
        val mcName = edtMcName.text.toString().trim()

        // If machine code is empty, clear everything and return
        if (mcName.isEmpty()) {
            txtMachineInfo.text = ""
            txtProcessNo.text = ""
            txtMachineRunning.visibility = View.GONE
            layoutSmallEdits.visibility = View.GONE
            edtJobNo.isEnabled = true
            edtProOrdNo.isEnabled = true
            edtSerial.isEnabled = true
            edtGhiChu.isEnabled = true
            updateSubmitButtonState()
            return
        }

        // Show loading indicator
        txtMachineInfo.text = LoadingStates.LOADING
        txtMachineInfo.setTextColor(LoadingStates.getLoadingColor(resources))

        var machineModel: String? = null
        var machineStatus: String? = null

        // Fetch machine info
        try {
            val info = machineApi.getMachine(mcName)
            if (info != null) {
                txtMachineInfo.text = listOfNotNull(info.model, info.status).joinToString(", ")
                txtMachineInfo.setTextColor(resources.getColor(android.R.color.holo_blue_dark))
                machineModel = info.model
                machineStatus = info.status
            } else {
                txtMachineInfo.text = "Không lấy được dữ liệu"
                txtMachineInfo.setTextColor(resources.getColor(android.R.color.holo_red_dark))
            }
        } catch (e: Exception) {
            txtMachineInfo.text = "Không lấy được dữ liệu"
            txtMachineInfo.setTextColor(resources.getColor(android.R.color.holo_red_dark))
        }

        // Fetch ProcessNo if both staff and machine are available
        var processNoValue: String? = null
        var processNoDto: ProcessNoDto? = null

        if (staffNo.isNotEmpty()) {
            try {
                val processNoApi = RetrofitClient.retrofitPublic.create(ProcessNoApi::class.java)
                processNoDto = processNoApi.getProcessNo(staffNo, mcName)
                processNoValue = processNoDto.processNo?.trim()
                txtProcessNo.text = processNoValue ?: ""

                // If we have a processNo, show "Máy đang chạy" and auto-fill fields
                if (!processNoValue.isNullOrBlank()) {
                    txtMachineRunning.visibility = View.VISIBLE
                    txtMachineRunning.text = "Máy đang chạy"
                    txtMachineRunning.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    txtMachineRunning.setTypeface(null, android.graphics.Typeface.NORMAL)
                    layoutSmallEdits.visibility = View.VISIBLE

                    // Auto-fill fields with data from processNoDto
                    if (!machineModel.isNullOrBlank()) {
                        edtJobNo.setText(machineModel)
                        edtJobNo.isEnabled = false
                    }

                    if (!processNoDto.proOrdNo2.isNullOrBlank()) {
                        edtProOrdNo.setText(processNoDto.proOrdNo2)
                        edtProOrdNo.requestFocus()
                        edtProOrdNo.clearFocus()
                        edtProOrdNo.isEnabled = false
                    }

                    if (!processNoDto.serial2.isNullOrBlank()) {
                        edtSerial.setText(processNoDto.serial2)
                        edtSerial.isEnabled = true
                    }

                    if (!processNoDto.note.isNullOrBlank()) {
                        edtGhiChu.setText(processNoDto.note)
                        edtGhiChu.isEnabled = true
                    }

                    // Hide keyboard and clear any remaining focus
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.hideSoftInputFromWindow(edtGhiChu.windowToken, 0)
                    layoutProOrdNoResults.removeAllViews()

                    updateSubmitButtonState()
                    return
                }
            } catch (e: Exception) {
                txtProcessNo.text = ""
            }
        } else {
            txtProcessNo.text = ""
        }

        // Show machine status based on machine status only
        when {
            machineStatus?.contains("Status: Ready", ignoreCase = true) == true -> {
                txtMachineRunning.visibility = View.VISIBLE
                txtMachineRunning.text = "Đang chờ việc"
                txtMachineRunning.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                txtMachineRunning.setTypeface(null, android.graphics.Typeface.NORMAL)
                layoutSmallEdits.visibility = View.GONE
            }
            machineStatus?.contains("Status: Processing", ignoreCase = true) == true -> {
                txtMachineRunning.visibility = View.VISIBLE
                txtMachineRunning.text = "Đang gia công"
                txtMachineRunning.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                txtMachineRunning.setTypeface(null, android.graphics.Typeface.BOLD)
                layoutSmallEdits.visibility = View.GONE
            }
            machineStatus?.contains("Status: Maintenance", ignoreCase = true) == true -> {
                txtMachineRunning.visibility = View.VISIBLE
                txtMachineRunning.text = "Đang bảo trì"
                txtMachineRunning.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                txtMachineRunning.setTypeface(null, android.graphics.Typeface.NORMAL)
                layoutSmallEdits.visibility = View.GONE
            }
            machineStatus?.contains("Status: BeingSetup", ignoreCase = true) == true -> {
                txtMachineRunning.visibility = View.VISIBLE
                txtMachineRunning.text = "Đang setup"
                txtMachineRunning.setTextColor(resources.getColor(android.R.color.holo_orange_dark))
                txtMachineRunning.setTypeface(null, android.graphics.Typeface.NORMAL)
                layoutSmallEdits.visibility = View.GONE
            }
            machineStatus?.contains("Status: Damage", ignoreCase = true) == true -> {
                txtMachineRunning.visibility = View.VISIBLE
                txtMachineRunning.text = "Báo hư chờ sửa"
                txtMachineRunning.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                txtMachineRunning.setTypeface(null, android.graphics.Typeface.NORMAL)
                layoutSmallEdits.visibility = View.GONE
            }
            else -> {
                txtMachineRunning.visibility = View.GONE
                layoutSmallEdits.visibility = View.GONE
            }
        }

        // Re-enable editing when machine is not running for this user
        edtJobNo.isEnabled = true
        edtProOrdNo.isEnabled = true
        edtSerial.isEnabled = true
        edtGhiChu.isEnabled = true

        updateSubmitButtonState()
    }

    // Function to check machine status and control submit button
    private fun updateSubmitButtonState() {
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)
        val txtMachineInfo = findViewById<TextView>(R.id.txtMachineInfo)
        val txtMachineRunning = findViewById<TextView>(R.id.txtMachineRunning)

        val machineInfoText = txtMachineInfo.text.toString()
        val machineRunningText = txtMachineRunning.text.toString()
        val isMachineRunningVisible = txtMachineRunning.visibility == View.VISIBLE

        // Check conditions that should block submit
        val hasProcessingStatus = machineInfoText.contains("Status: Processing", ignoreCase = true)
        val isShowingProcessingForOther = isMachineRunningVisible && machineRunningText == "Đang gia công"
        val hasMaintenanceStatus = machineInfoText.contains("Status: Maintenance", ignoreCase = true)
        val isShowingMaintenance = isMachineRunningVisible && machineRunningText == "Đang bảo trì"
        val hasBeingSetupStatus = machineInfoText.contains("Status: BeingSetup", ignoreCase = true)
        val isShowingBeingSetup = isMachineRunningVisible && machineRunningText == "Đang setup"
        val hasDamageStatus = machineInfoText.contains("Status: Damage", ignoreCase = true)
        val isShowingDamage = isMachineRunningVisible && machineRunningText == "Báo hư chờ sửa"

        val shouldBlockSubmit = (hasProcessingStatus && isShowingProcessingForOther) ||
                               (hasMaintenanceStatus && isShowingMaintenance) ||
                               (hasBeingSetupStatus && isShowingBeingSetup) ||
                               (hasDamageStatus && isShowingDamage)

        if (shouldBlockSubmit) {
            btnSubmit.isEnabled = false
            btnSubmit.alpha = 0.5f // Gray out the button
        } else {
            btnSubmit.isEnabled = true
            btnSubmit.alpha = 1.0f // Restore normal appearance
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_machine_log)

        // --- UI references for "Thợ" (Staff) block ---
        val edtStaffNo = findViewById<EditText>(R.id.edtStaffNo)
        val txtStaffInfo = findViewById<TextView>(R.id.txtStaffInfo)
        val api = RetrofitClient.retrofitPublic.create(StaffApi::class.java)

        // --- UI references for "Mã máy" (Machine) block ---
        val edtMcName = findViewById<EditText>(R.id.edtMcName)
        val txtMachineInfo = findViewById<TextView>(R.id.txtMachineInfo)
        val txtProcessNo = findViewById<TextView>(R.id.txtProcessNo)
        val txtMachineRunning = findViewById<TextView>(R.id.txtMachineRunning)

        // --- UI reference for serial info below LSX (ProOrdNo) ---
        val edtProOrdNo = findViewById<EditText>(R.id.edtProOrdNo)
        val txtSerialInfo = findViewById<TextView>(R.id.txtSerialInfo)
        val txtJobInfo = findViewById<TextView>(R.id.txtJobInfo)
        val txtSerialStatus = findViewById<TextView>(R.id.txtSerialStatus)
        val txtNoteStatus = findViewById<TextView>(R.id.txtNoteStatus)
        val machineApi = RetrofitClient.retrofitPublic.create(MachineApi::class.java)
        val serialApi = RetrofitClient.retrofitPublic.create(SerialApi::class.java)
        val proOrdApi = RetrofitClient.retrofitPublic.create(ProOrdApi::class.java)
        var processNoValue: String? = null

        // --- Declare layoutSmallEdits reference ---
        val layoutSmallEdits = findViewById<LinearLayout>(R.id.layoutSmallEdits)

        // --- UI references for LSX (ProOrdNo) and result grid block ---
        val edtJobNo = findViewById<EditText>(R.id.edtJobNo)
        val layoutProOrdNoResults = findViewById<android.widget.GridLayout>(R.id.layoutProOrdNoResults)

        val edtSerial = findViewById<EditText>(R.id.edtSerial)
        val btnScan = findViewById<Button>(R.id.btnScan)

        // --- UI references for small quantity EditTexts ---
        val edtDat = findViewById<EditText>(R.id.edtDat)
        val edtPhe = findViewById<EditText>(R.id.edtPhe)
        val edtXuLy = findViewById<EditText>(R.id.edtXuLy)

        // --- UI references for note field and status ---
        val edtGhiChu = findViewById<EditText>(R.id.edtGhiChu)
        val txtSendStatus = findViewById<TextView>(R.id.txtSendStatus)
        val btnSubmit = findViewById<Button>(R.id.btnSubmit)

        // --- Setup IME action listeners for all EditTexts ---
        edtStaffNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtMcName.requestFocus()
                true
            } else false
        }

        edtMcName.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtJobNo.requestFocus()
                true
            } else false
        }

        edtJobNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtProOrdNo.requestFocus()
                true
            } else false
        }

        edtProOrdNo.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtSerial.requestFocus()
                true
            } else false
        }

        edtSerial.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                if (layoutSmallEdits.visibility == View.VISIBLE) {
                    edtDat.requestFocus()
                } else {
                    edtGhiChu.requestFocus()
                }
                true
            } else false
        }

        edtDat.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtPhe.requestFocus()
                true
            } else false
        }

        edtPhe.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtXuLy.requestFocus()
                true
            } else false
        }

        edtXuLy.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                edtGhiChu.requestFocus()
                true
            } else false
        }

        edtGhiChu.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                edtGhiChu.clearFocus()
                val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(edtGhiChu.windowToken, 0)
                true
            } else false
        }

        // --- Block for handling Staff ("Thợ") input and validation ---
        edtStaffNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val staffNoStr = edtStaffNo.text.toString().trim()
                if (staffNoStr.isNotEmpty()) {
                    try {
                        val staffNo = staffNoStr.toInt()
                        // Show loading indicator using LoadingStates
                        txtStaffInfo.text = LoadingStates.LOADING
                        txtStaffInfo.setTextColor(LoadingStates.getLoadingColor(resources))

                        // Fetch staff info asynchronously
                        lifecycleScope.launch {
                            try {
                                val info = api.getStaff(staffNo)
                                if (info != null) {
                                    // Display staff info if found - dark red color for valid and bold
                                    txtStaffInfo.text = listOfNotNull(info.fullName, info.workJob, info.workPlace).joinToString(", ")
                                    txtStaffInfo.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                                    //txtStaffInfo.setTypeface(null, android.graphics.Typeface.BOLD)  // Uncomment to make bold
                                } else {
                                    // Show error if staff not found - API responded but returned null (invalid)
                                    txtStaffInfo.text = "Không lấy được dữ liệu"
                                    txtStaffInfo.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                                }
                            } catch (e: Exception) {
                                // Show server connection error if API call fails
                                txtStaffInfo.text = "Không lấy được dữ liệu"
                                txtStaffInfo.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                            }

                            // Call shared function to process machine and staff combination
                            processMachineAndStaff()
                        }
                    } catch (e: NumberFormatException) {
                        // Show error if input is not a number
                        txtStaffInfo.text = "Không lấy được dữ liệu"
                        txtStaffInfo.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    }
                } else {
                    // Clear staff info if input is empty
                    txtStaffInfo.text = ""
                    // Call shared function to clear machine processing
                    lifecycleScope.launch {
                        processMachineAndStaff()
                    }
                }
            }
        }

        // --- Block for handling Machine Code ("Mã máy") input and validation ---
        edtMcName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // Call shared function to process machine and staff combination
                lifecycleScope.launch {
                    processMachineAndStaff()
                }
            }
        }

        // --- Block for handling LSX (ProOrdNo) input and displaying results in a grid ---
        // Goal 2: Move the auto-click logic here when processNo is blank
        edtJobNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val jobNo = edtJobNo.text.toString().trim()
                if (jobNo.isNotEmpty()) {
                    // Show loading indicator
                    txtJobInfo.visibility = View.VISIBLE
                    txtJobInfo.text = LoadingStates.LOADING
                    txtJobInfo.setTextColor(LoadingStates.getLoadingColor(resources))

                    // Fetch production order list asynchronously
                    lifecycleScope.launch {
                        try {
                            val responseBody = proOrdApi.getProOrd(jobNo)
                            val json = responseBody.string()
                            val gson = Gson()
                            val jsonElement = JsonParser.parseString(json)
                            val proOrdList = when {
                                jsonElement.isJsonArray -> jsonElement.asJsonArray.mapNotNull {
                                    gson.fromJson(it, ProOrdDto::class.java).jobControlNo
                                }
                                jsonElement.isJsonObject -> listOfNotNull(gson.fromJson(jsonElement, ProOrdDto::class.java).jobControlNo)
                                else -> emptyList()
                            }

                            // Hide loading indicator
                            txtJobInfo.visibility = View.GONE

                            // Clear previous results in the grid
                            layoutProOrdNoResults.removeAllViews()

                            // Check if machine is running first - if so, don't show any buttons
                            val currentProcessNo = txtProcessNo.text.toString().trim()
                            val isMachineRunning = !currentProcessNo.isBlank()

                            if (isMachineRunning) {
                                // Machine is running - don't show any buttons, just clear and return
                                return@launch
                            }

                            if (proOrdList.isNotEmpty()) {
                                // Check if machine is NOT running (processNo is blank)
                                val shouldAutoClick = currentProcessNo.isBlank()

                                // Auto-click if only one result and machine is not running
                                if (proOrdList.size == 1 && shouldAutoClick) {
                                    val singleJobControlNo = proOrdList.first()
                                    edtProOrdNo.setText(singleJobControlNo)
                                    edtProOrdNo.requestFocus()
                                    edtProOrdNo.clearFocus()
                                } else {
                                    // Multiple results or machine is running, show clickable boxes
                                    proOrdList.forEach { jobControlNo ->
                                        val tv = TextView(this@MachineLogActivity)
                                        tv.text = jobControlNo
                                        tv.setPadding(12, 8, 12, 8)
                                        tv.setBackgroundResource(R.drawable.clickable_button_background)
                                        tv.setTextColor(resources.getColor(android.R.color.white))
                                        tv.textSize = 14f
                                        tv.gravity = android.view.Gravity.CENTER

                                        val params = android.widget.GridLayout.LayoutParams()
                                        params.setMargins(4, 4, 4, 4)
                                        params.width = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                                        params.height = android.widget.GridLayout.LayoutParams.WRAP_CONTENT
                                        tv.layoutParams = params

                                        tv.setOnClickListener {
                                            edtProOrdNo.setText(jobControlNo)
                                            edtProOrdNo.requestFocus()
                                            edtProOrdNo.clearFocus()
                                            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
                                            imm?.hideSoftInputFromWindow(edtProOrdNo.windowToken, 0)
                                        }
                                        layoutProOrdNoResults.addView(tv)
                                    }
                                }
                            } else {
                                // Show error if no results found
                                val tv = TextView(this@MachineLogActivity)
                                tv.text = "Không lấy được dữ liệu"
                                tv.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                                layoutProOrdNoResults.addView(tv)
                            }
                        } catch (e: Exception) {
                            // Hide loading and show error if API call fails
                            txtJobInfo.visibility = View.GONE
                            layoutProOrdNoResults.removeAllViews()
                            val tv = TextView(this@MachineLogActivity)
                            tv.text = "Không lấy được dữ liệu"
                            tv.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                            layoutProOrdNoResults.addView(tv)
                        }
                    }
                } else {
                    // Clear grid and hide loading if input is empty
                    layoutProOrdNoResults.removeAllViews()
                    txtJobInfo.visibility = View.GONE
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
                                txtSerialInfo.text = "Không lấy được dữ liệu"
                            }
                        } catch (e: Exception) {
                            txtSerialInfo.text = "Không lấy được dữ liệu"
                        }
                    }
                } else {
                    txtSerialInfo.text = ""
                }
            }
        }

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

        // --- UI references for checkboxes (Rework, Setup) ---
        val chkRework = findViewById<CheckBox>(R.id.chkRework)
        val chkSetup = findViewById<CheckBox>(R.id.chkSetup)
        // --- Checkbox value mapping: 1 if checked, 0 if not ---
        var reworkValue = 0
        var setupValue = 0
        chkRework.setOnCheckedChangeListener { _, isChecked ->
            reworkValue = if (isChecked) 1 else 0
        }
        chkSetup.setOnCheckedChangeListener { _, isChecked ->
            setupValue = if (isChecked) 1 else 0
        }

        val postApi = RetrofitClient.retrofitPublic.create(PostNhatKyGiaCongApi::class.java)
        btnSubmit.setOnClickListener {
            // Prevent submit if any error message is shown
            val dataError = "Không lấy được dữ liệu"
            val serverError = "Không lấy được dữ liệu"
            val errorFields = listOf(txtStaffInfo, txtMachineInfo, txtSerialInfo)
            if (errorFields.any {
                val text = it.text.toString()
                text.contains(dataError, ignoreCase = true) || text.contains(serverError, ignoreCase = true)
            }) {
                Toast.makeText(this, "Vui lòng kiểm tra lại thông tin", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val processNoForDto = txtProcessNo.text.toString().let { if (it.isBlank()) " " else it }

            // Show immediate feedback and disable button
            Toast.makeText(this, "Dữ liệu đã được gửi", Toast.LENGTH_SHORT).show()
            btnSubmit.isEnabled = false
            txtSendStatus.visibility = View.VISIBLE
            txtSendStatus.text = "Đang xử lý..."
            txtSendStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark))

            val dto = NhatKyGiaCongDto(
                processNo = processNoForDto,
                jobControlNo = edtJobNo.text.toString().trim(),
                staffNo = edtStaffNo.text.toString().trim(),
                mcName = edtMcName.text.toString().trim(),
                note = edtGhiChu.text.toString().trim(),
                proOrdNo = edtProOrdNo.text.toString().trim(),
                serial = edtSerial.text.toString().trim(),
                setup = chkSetup.isChecked,
                rework = chkRework.isChecked,
                qtyGood = edtDat.text.toString().toIntOrNull() ?: 0,
                qtyReject = edtPhe.text.toString().toIntOrNull() ?: 0,
                qtyRework = edtXuLy.text.toString().toIntOrNull() ?: 0
            )
            lifecycleScope.launch {
                try {
                    android.util.Log.d("POST_DTO", Gson().toJson(dto)) // Log the payload
                    val response = postApi.postLog(dto)
                    if (response.isSuccessful) {
                        Toast.makeText(this@MachineLogActivity, "Cập nhật thành công", Toast.LENGTH_SHORT).show()
                        txtSendStatus.text = "Cập nhật thành công"
                        txtSendStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark))
                    } else {
                        val errorBody = response.errorBody()?.string()
                        val errorMessage = "Cập nhật thất bại ${response.code()} ${errorBody ?: ""}"
                        Toast.makeText(this@MachineLogActivity, errorMessage, Toast.LENGTH_LONG).show()
                        txtSendStatus.text = errorMessage
                        txtSendStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                        android.util.Log.e("POST_ERROR", "Code: ${response.code()} Body: $errorBody")
                    }
                } catch (e: Exception) {
                    val errorMessage = "Cập nhật thất bại: ${e.message}"
                    Toast.makeText(this@MachineLogActivity, errorMessage, Toast.LENGTH_SHORT).show()
                    txtSendStatus.text = errorMessage
                    txtSendStatus.setTextColor(resources.getColor(android.R.color.holo_red_dark))
                    android.util.Log.e("POST_EXCEPTION", e.toString())
                } finally {
                    // Re-enable button after operation completes
                    btnSubmit.isEnabled = true
                }
            }
        }
    }
}
