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

    // --- API interface for fetching input material info ---
    interface InputMaterialApi {
        @GET("/api/GetInputMaterial/{HeatNo}")
        suspend fun getInputMaterial(@Path("HeatNo") heatNo: String): List<InputMaterialInfo>?
    }

    // --- API interface for posting material usage ---
    interface PostMUsingApi {
        @retrofit2.http.POST("/api/PostMUsing")
        suspend fun postMaterialUsing(@retrofit2.http.Body data: PostMUsingDto): retrofit2.Response<List<PostMUsingResponse>>
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
        @SerializedName("notes") val notes: String?,
        @SerializedName("matIID") val matIID: String?
    )

    // --- Data class for input material info ---
    data class InputMaterialInfo(
        @SerializedName("material") val material: String?,
        @SerializedName("matPONo") val matPONo: String?
    )

    // --- Data class for POST request ---
    data class PostMUsingDto(
        @SerializedName("staffNo") val staffNo: Int,
        @SerializedName("matIID") val matIID: String,
        @SerializedName("materialQty") val materialQty: Float,
        @SerializedName("partQty") val partQty: Int,
        @SerializedName("jobNo") val jobNo: String,
        @SerializedName("notes") val notes: String
    )

    // --- Data class for POST response ---
    data class PostMUsingResponse(
        @SerializedName("MatUID") val matUID: String?,
        @SerializedName("MatIID") val matIID: String?,
        @SerializedName("StaffReceiver") val staffReceiver: String?,
        @SerializedName("MatQty") val matQty: Float?,
        @SerializedName("PartQty") val partQty: Int?,
        @SerializedName("PartRemain") val partRemain: Int?,
        @SerializedName("MatUNote") val matUNote: String?,
        @SerializedName("JobNo") val jobNo: String?,
        @SerializedName("MatUForms") val matUForms: String?,
        @SerializedName("Notes") val notes: String?,
        @SerializedName("RemainNote") val remainNote: String?
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
        val txtSelectedMaterial = findViewById<TextView>(R.id.txtSelectedMaterial)

        // New material type selection UI elements
        val layoutMaterialTypeButtons = findViewById<LinearLayout>(R.id.layoutMaterialTypeButtons)
        val btnTam = findViewById<Button>(R.id.btnTam)
        val btnCay = findViewById<Button>(R.id.btnCay)
        val btnOng = findViewById<Button>(R.id.btnOng)

        // New size input fields
        val txtSizeALabel = findViewById<TextView>(R.id.txtSizeALabel)
        val edtSizeA = findViewById<EditText>(R.id.edtSizeA)
        val txtSizeBLabel = findViewById<TextView>(R.id.txtSizeBLabel)
        val edtSizeB = findViewById<EditText>(R.id.edtSizeB)
        val txtSizeCLabel = findViewById<TextView>(R.id.txtSizeCLabel)
        val edtSizeC = findViewById<EditText>(R.id.edtSizeC)

        // Existing UI elements
        val edtJobNo = findViewById<EditText>(R.id.edtJobNo)
        val txtSize1Label = findViewById<TextView>(R.id.txtSize1Label)
        val edtSize1 = findViewById<EditText>(R.id.edtSize1)
        val txtOldSize1 = findViewById<TextView>(R.id.txtOldSize1)
        val txtSize2Label = findViewById<TextView>(R.id.txtSize2Label)
        val edtSize2 = findViewById<EditText>(R.id.edtSize2)
        val txtOldSize2 = findViewById<TextView>(R.id.txtOldSize2)
        val edtMaterialQty = findViewById<EditText>(R.id.edtMaterialQty)
        val txtOldMaterialQty = findViewById<TextView>(R.id.txtOldMaterialQty)
        val edtNotes = findViewById<EditText>(R.id.edtNotes)
        val edtPartQty = findViewById<EditText>(R.id.edtPartQty)
        val edtProductQty = findViewById<EditText>(R.id.edtProductQty)
        val edtWarehouseArea = findViewById<EditText>(R.id.edtWarehouseArea)
        val btnXuatHang = findViewById<Button>(R.id.btnXuatHang)
        val btnNhapHang = findViewById<Button>(R.id.btnNhapHang)
        val btnXuatHangTop = findViewById<Button>(R.id.btnXuatHangTop)

        // Track which mode is selected
        var isXuatHangModeSelected = false
        var isNhapHangModeSelected = false
        var selectedMaterialType = "" // "TẤM", "CÂY", or "ỐNG"
        var selectedMatIID = "" // Store the selected Material Input ID for POST function

        val staffApi = RetrofitClient.retrofitPublic.create(StaffApi::class.java)
        val heatNoApi = RetrofitClient.retrofitPublic.create(HeatNoApi::class.java)
        val inputMaterialApi = RetrofitClient.retrofitPublic.create(InputMaterialApi::class.java)
        val postMUsingApi = RetrofitClient.retrofitPublic.create(PostMUsingApi::class.java)

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

        // Function to reset button states
        fun resetButtonStates() {
            btnNhapHang.setBackgroundResource(android.R.drawable.btn_default)
            btnXuatHangTop.setBackgroundResource(android.R.drawable.btn_default)
            isNhapHangModeSelected = false
            isXuatHangModeSelected = false
        }

        // Function to reset material type button states
        fun resetMaterialTypeButtons() {
            btnTam.setBackgroundResource(android.R.drawable.btn_default)
            btnCay.setBackgroundResource(android.R.drawable.btn_default)
            btnOng.setBackgroundResource(android.R.drawable.btn_default)
            selectedMaterialType = ""
        }

        // Function to clear all form fields
        fun clearAllFields() {
            // Clear text fields
            txtHeatInfo.text = ""
            txtSelectedMaterial.text = ""
            txtSelectedMaterial.visibility = android.view.View.GONE

            // Hide material type buttons
            layoutMaterialTypeButtons.visibility = android.view.View.GONE
            resetMaterialTypeButtons()

            // Clear and hide input fields
            edtJobNo.setText("")
            edtJobNo.visibility = android.view.View.GONE

            // Clear and hide old size fields and labels
            txtSize1Label.visibility = android.view.View.GONE
            edtSize1.setText("")
            edtSize1.visibility = android.view.View.GONE
            txtOldSize1.text = ""
            txtOldSize1.visibility = android.view.View.GONE

            txtSize2Label.visibility = android.view.View.GONE
            edtSize2.setText("")
            edtSize2.visibility = android.view.View.GONE
            txtOldSize2.text = ""
            txtOldSize2.visibility = android.view.View.GONE

            // Clear new size fields and labels
            txtSizeALabel.visibility = android.view.View.GONE
            edtSizeA.setText("")
            edtSizeA.visibility = android.view.View.GONE
            txtSizeBLabel.visibility = android.view.View.GONE
            edtSizeB.setText("")
            edtSizeB.visibility = android.view.View.GONE
            txtSizeCLabel.visibility = android.view.View.GONE
            edtSizeC.setText("")
            edtSizeC.visibility = android.view.View.GONE

            edtMaterialQty.setText("")
            edtMaterialQty.visibility = android.view.View.GONE
            txtOldMaterialQty.text = ""
            txtOldMaterialQty.visibility = android.view.View.GONE

            edtNotes.setText("")
            edtNotes.visibility = android.view.View.GONE

            edtPartQty.setText("")
            edtPartQty.visibility = android.view.View.GONE

            edtProductQty.setText("")
            edtProductQty.visibility = android.view.View.GONE

            edtWarehouseArea.setText("")
            edtWarehouseArea.visibility = android.view.View.GONE

            // Hide the update button
            btnXuatHang.visibility = android.view.View.GONE

            // Clear any dynamic buttons
            layoutHeatInfoButtons.removeAllViews()
        }

        // Nhập phôi button click listener
        btnNhapHang.setOnClickListener {
            resetButtonStates()
            clearAllFields()
            isNhapHangModeSelected = true
            btnNhapHang.setBackgroundResource(R.drawable.button_activated_background)
        }

        // Xuất phôi button click listener
        btnXuatHangTop.setOnClickListener {
            resetButtonStates()
            clearAllFields()
            isXuatHangModeSelected = true
            btnXuatHangTop.setBackgroundResource(R.drawable.button_activated_background)
        }

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
                    // Check if any mode is selected
                    if (!isXuatHangModeSelected && !isNhapHangModeSelected) {
                        txtHeatInfo.text = "Hãy chọn Nhập phôi hoặc Xuất phôi"
                        txtHeatInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        return@setOnFocusChangeListener
                    }

                    // Only proceed with API call if Xuất phôi is selected
                    if (isXuatHangModeSelected) {
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
                                        val matIID = info.matIID ?: ""

                                        // Include MatIID in the button text
                                        val buttonText = "ID: $matIID - $material, SL vật liệu $existQty, $inpSize1 * $inpSize2, SL con hàng $qty, $notes"

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
                                                // Store the MatIID internally for POST function
                                                selectedMatIID = info.matIID ?: ""

                                                // Show selected material with MatIID in the TextView
                                                txtSelectedMaterial.text = "ID: $matIID - Tên vật liệu: $material"
                                                txtSelectedMaterial.visibility = android.view.View.VISIBLE
                                                txtSelectedMaterial.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))

                                                // Show size fields as display-only TextViews (no edit fields)
                                                txtOldSize1.text = inpSize1
                                                txtOldSize1.visibility = android.view.View.VISIBLE
                                                txtOldSize1.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                                                edtSize1.visibility = android.view.View.GONE

                                                txtOldSize2.text = inpSize2
                                                txtOldSize2.visibility = android.view.View.VISIBLE
                                                txtOldSize2.setTextColor(ContextCompat.getColor(context, android.R.color.holo_red_dark))
                                                edtSize2.visibility = android.view.View.GONE

                                                // Show job details field (Chi tiết công việc)
                                                edtJobNo.hint = "Chi tiết công việc"
                                                edtJobNo.visibility = android.view.View.VISIBLE

                                                // Show material quantity with label above editable field
                                                txtOldMaterialQty.text = "Số lượng dùng:"
                                                txtOldMaterialQty.visibility = android.view.View.VISIBLE
                                                txtOldMaterialQty.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                                                edtMaterialQty.visibility = android.view.View.VISIBLE
                                                edtMaterialQty.setText("1")

                                                // Show notes field
                                                edtNotes.visibility = android.view.View.VISIBLE

                                                // Show part quantity field
                                                edtPartQty.visibility = android.view.View.VISIBLE

                                                // Hide product quantity and warehouse area fields
                                                edtProductQty.visibility = android.view.View.GONE
                                                edtWarehouseArea.visibility = android.view.View.GONE

                                                // Show the "Cập nhật" button
                                                btnXuatHang.visibility = android.view.View.VISIBLE
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
                    } else if (isNhapHangModeSelected) {
                        // For Nhập phôi mode - call GetInputMaterial API and show form for new material input
                        txtHeatInfo.text = getString(R.string.loading)
                        txtHeatInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))

                        lifecycleScope.launch {
                            try {
                                val inputMaterialList = inputMaterialApi.getInputMaterial(heatNoStr)
                                if (inputMaterialList != null && inputMaterialList.isNotEmpty()) {
                                    val firstMaterial = inputMaterialList[0]
                                    val material = firstMaterial.material ?: ""
                                    val matPONo = firstMaterial.matPONo ?: ""

                                    // Show material name field with API data
                                    txtSelectedMaterial.text = "Tên vật liệu: $material, P.O: $matPONo"
                                    txtSelectedMaterial.visibility = android.view.View.VISIBLE
                                    txtSelectedMaterial.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))

                                    txtHeatInfo.text = ""
                                } else {
                                    // No data found, show default
                                    txtSelectedMaterial.text = "Tên vật liệu: chưa có"
                                    txtSelectedMaterial.visibility = android.view.View.VISIBLE
                                    txtSelectedMaterial.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))

                                    txtHeatInfo.text = ""
                                }
                            } catch (_: Exception) {
                                // API call failed, show default
                                txtSelectedMaterial.text = "Tên vật liệu: chưa có"
                                txtSelectedMaterial.visibility = android.view.View.VISIBLE
                                txtSelectedMaterial.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))

                                txtHeatInfo.text = ""
                            }
                        }

                        // Show material type selection buttons for Nhập phôi mode
                        layoutMaterialTypeButtons.visibility = android.view.View.VISIBLE

                        // Hide old size fields initially
                        txtSize1Label.visibility = android.view.View.GONE
                        edtSize1.visibility = android.view.View.GONE
                        txtOldSize1.visibility = android.view.View.GONE
                        txtSize2Label.visibility = android.view.View.GONE
                        edtSize2.visibility = android.view.View.GONE
                        txtOldSize2.visibility = android.view.View.GONE

                        // Hide job and material quantity fields for Nhập phôi mode
                        edtJobNo.visibility = android.view.View.GONE
                        edtMaterialQty.visibility = android.view.View.GONE
                        txtOldMaterialQty.visibility = android.view.View.GONE
                        edtProductQty.visibility = android.view.View.GONE

                        // Don't show warehouse area and update button until material type is selected
                        edtWarehouseArea.visibility = android.view.View.GONE
                        btnXuatHang.visibility = android.view.View.GONE
                    }
                } else {
                    txtHeatInfo.text = ""
                }
            }
        }

        // Material type button click listeners
        btnTam.setOnClickListener {
            resetMaterialTypeButtons()
            selectedMaterialType = "TẤM"
            btnTam.setBackgroundResource(R.drawable.button_activated_background)

            // Show TẤM specific fields
            txtSizeALabel.text = "Nhập chiều rộng"
            txtSizeALabel.visibility = android.view.View.VISIBLE
            edtSizeA.hint = ""
            edtSizeA.setText("")
            edtSizeA.visibility = android.view.View.VISIBLE

            txtSizeBLabel.text = "Nhập độ dày"
            txtSizeBLabel.visibility = android.view.View.VISIBLE
            edtSizeB.hint = ""
            edtSizeB.setText("")
            edtSizeB.visibility = android.view.View.VISIBLE

            txtSizeCLabel.text = "Nhập chiều dài"
            txtSizeCLabel.visibility = android.view.View.VISIBLE
            edtSizeC.hint = ""
            edtSizeC.setText("")
            edtSizeC.visibility = android.view.View.VISIBLE

            // Show warehouse area and update button
            edtWarehouseArea.visibility = android.view.View.VISIBLE
            btnXuatHang.visibility = android.view.View.VISIBLE
        }

        btnCay.setOnClickListener {
            resetMaterialTypeButtons()
            selectedMaterialType = "CÂY"
            btnCay.setBackgroundResource(R.drawable.button_activated_background)

            // Show CÂY specific fields
            txtSizeALabel.text = "Nhập diện tích"
            txtSizeALabel.visibility = android.view.View.VISIBLE
            edtSizeA.hint = ""
            edtSizeA.setText("D")
            edtSizeA.visibility = android.view.View.VISIBLE

            txtSizeBLabel.text = "Nhập chiều dài"
            txtSizeBLabel.visibility = android.view.View.VISIBLE
            edtSizeB.hint = ""
            edtSizeB.setText("")
            edtSizeB.visibility = android.view.View.VISIBLE

            // Hide Size C for CÂY
            txtSizeCLabel.visibility = android.view.View.GONE
            edtSizeC.visibility = android.view.View.GONE

            // Show warehouse area and update button
            edtWarehouseArea.visibility = android.view.View.VISIBLE
            btnXuatHang.visibility = android.view.View.VISIBLE
        }

        btnOng.setOnClickListener {
            resetMaterialTypeButtons()
            selectedMaterialType = "ỐNG"
            btnOng.setBackgroundResource(R.drawable.button_activated_background)

            // Show ỐNG specific fields
            txtSizeALabel.text = "Nhập đường kính ngoài"
            txtSizeALabel.visibility = android.view.View.VISIBLE
            edtSizeA.hint = ""
            edtSizeA.setText("OD")
            edtSizeA.visibility = android.view.View.VISIBLE

            txtSizeBLabel.text = "Nhập đường kính trong"
            txtSizeBLabel.visibility = android.view.View.VISIBLE
            edtSizeB.hint = ""
            edtSizeB.setText("ID")
            edtSizeB.visibility = android.view.View.VISIBLE

            txtSizeCLabel.text = "Nhập chiều dài"
            txtSizeCLabel.visibility = android.view.View.VISIBLE
            edtSizeC.hint = ""
            edtSizeC.setText("")
            edtSizeC.visibility = android.view.View.VISIBLE

            // Show warehouse area and update button
            edtWarehouseArea.visibility = android.view.View.VISIBLE
            btnXuatHang.visibility = android.view.View.VISIBLE
        }

        // "Cập nhật" button click listener for submitting material usage data
        btnXuatHang.setOnClickListener {
            // Validate required fields for Xuất phôi mode
            if (isXuatHangModeSelected) {
                val staffNoStr = edtStaffNo.text.toString().trim()
                val jobNo = edtJobNo.text.toString().trim()
                val materialQtyStr = edtMaterialQty.text.toString().trim()
                val partQtyStr = edtPartQty.text.toString().trim()
                val notes = edtNotes.text.toString().trim()

                // Validation
                if (staffNoStr.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập số thợ", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (selectedMatIID.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng chọn vật liệu", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (jobNo.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập chi tiết công việc", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (materialQtyStr.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập số lượng vật liệu", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (partQtyStr.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập số lượng thành phẩm", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                try {
                    val staffNo = staffNoStr.toInt()
                    val materialQty = materialQtyStr.toFloat()
                    val partQty = partQtyStr.toInt()

                    // Create POST data object
                    val postData = PostMUsingDto(
                        staffNo = staffNo,
                        matIID = selectedMatIID,
                        materialQty = materialQty,
                        partQty = partQty,
                        jobNo = jobNo,
                        notes = notes
                    )

                    // Show loading state
                    btnXuatHang.text = "Đang xử lý..."
                    btnXuatHang.isEnabled = false

                    // Submit data to API
                    lifecycleScope.launch {
                        try {
                            val response = postMUsingApi.postMaterialUsing(postData)

                            if (response.isSuccessful) {
                                val responseBody = response.body()
                                if (responseBody != null && responseBody.isNotEmpty()) {
                                    // Success - show response data
                                    val result = responseBody[0]
                                    val successMessage = "Thành công!\nMã UID: ${result.matUID}\nSố lượng còn lại: ${result.partRemain}"

                                    android.widget.Toast.makeText(this@MaterialLogActivity, successMessage, android.widget.Toast.LENGTH_LONG).show()

                                    // Clear form after successful submission
                                    clearAllFields()
                                    resetButtonStates()
                                    edtStaffNo.setText("")
                                    edtHeatNo.setText("")
                                    txtStaffInfo.text = ""
                                    selectedMatIID = ""
                                } else {
                                    android.widget.Toast.makeText(this@MaterialLogActivity, "Không có dữ liệu trả về", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                // Handle API error response
                                val errorMessage = response.errorBody()?.string() ?: "Lỗi không xác định"
                                android.widget.Toast.makeText(this@MaterialLogActivity, "Lỗi: $errorMessage", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            // Handle network or other errors
                            android.widget.Toast.makeText(this@MaterialLogActivity, "Lỗi kết nối: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        } finally {
                            // Reset button state
                            btnXuatHang.text = "Cập nhật"
                            btnXuatHang.isEnabled = true
                        }
                    }

                } catch (e: NumberFormatException) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập số hợp lệ", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
