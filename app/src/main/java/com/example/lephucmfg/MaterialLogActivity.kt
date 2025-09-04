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
import com.example.lephucmfg.utils.StaffPreferences
import com.google.gson.annotations.SerializedName
import com.google.zxing.integration.android.IntentIntegrator
import kotlinx.coroutines.launch
import retrofit2.http.GET
import retrofit2.http.Path

class MaterialLogActivity : AppCompatActivity() {

    // Add StaffPreferences instance
    private lateinit var staffPreferences: StaffPreferences

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
        suspend fun postMaterialUsing(@retrofit2.http.Body data: PostMUsingDto): retrofit2.Response<PostMUsingResponse>
    }

    // --- API interface for posting material input ---
    interface PostMInputApi {
        @retrofit2.http.POST("/api/PostMInput")
        suspend fun postMaterialInput(@retrofit2.http.Body data: PostMInputDto): retrofit2.Response<PostMInputResponse>
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
        @SerializedName("matQty") val matQty: Int,  // Changed from Float to Int
        @SerializedName("partQty") val partQty: Int,
        @SerializedName("jobNo") val jobNo: String,
        @SerializedName("notes") val notes: String
    )

    // --- Data class for POST response ---
    data class PostMUsingResponse(
        @SerializedName("message") val message: String?
    )

    // --- Data class for POST request (Material Input) ---
    data class PostMInputDto(
        @SerializedName("staffNo") val staffNo: Int,
        @SerializedName("heatNo") val heatNo: String,
        @SerializedName("materialType") val materialType: String,
        @SerializedName("newInpSize1") val newInpSize1: String,
        @SerializedName("newInpSize2") val newInpSize2: String,
        @SerializedName("warehouseArea") val warehouseArea: String,
        @SerializedName("matQty") val matQty: Int
    )

    // --- Data class for POST response (Material Input) ---
    data class PostMInputResponse(
        @SerializedName("message") val message: String?
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_material_log)

        // Initialize StaffPreferences
        staffPreferences = StaffPreferences(this)

        val edtStaffNo = findViewById<EditText>(R.id.edtStaffNo)
        val txtStaffInfo = findViewById<TextView>(R.id.txtStaffInfo)
        val edtHeatNo = findViewById<EditText>(R.id.edtHeatNo)
        val txtHeatInfo = findViewById<TextView>(R.id.txtHeatInfo)
        val btnScan = findViewById<Button>(R.id.btnScan)
        val layoutHeatInfoButtons = findViewById<LinearLayout>(R.id.layoutHeatInfoButtons)
        val txtSelectedMaterial = findViewById<TextView>(R.id.txtSelectedMaterial)

        // Load saved staff number and info on startup
        val savedStaffNumber = staffPreferences.getStaffNumber()
        val savedStaffInfo = staffPreferences.getStaffInfo()

        if (savedStaffNumber.isNotEmpty()) {
            edtStaffNo.setText(savedStaffNumber)
            if (savedStaffInfo.isNotEmpty()) {
                txtStaffInfo.text = savedStaffInfo
                txtStaffInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            }
            // Auto-call API with saved staff number
            autoCallStaffApi(savedStaffNumber.toIntOrNull() ?: 0)
        }

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

        // Existing UI elements for Xuất phôi mode
        val edtJobNo = findViewById<EditText>(R.id.edtJobNo)
        val txtSize1Label = findViewById<TextView>(R.id.txtSize1Label)
        val edtSize1 = findViewById<EditText>(R.id.edtSize1)
        val txtOldSize1 = findViewById<TextView>(R.id.txtOldSize1)
        val txtSize2Label = findViewById<TextView>(R.id.txtSize2Label)
        val edtSize2 = findViewById<EditText>(R.id.edtSize2)
        val txtOldSize2 = findViewById<TextView>(R.id.txtOldSize2)
        val edtMatQty = findViewById<EditText>(R.id.edtMaterialQty)  // matQty for API consistency
        val txtMatQtyLabel = findViewById<TextView>(R.id.txtOldMaterialQty)
        val txtPartQtyLabel = findViewById<TextView>(R.id.txtPartQtyLabel)
        val edtPartQty = findViewById<EditText>(R.id.edtPartQty)
        val edtNotes = findViewById<EditText>(R.id.edtNotes)
        val edtWarehouseArea = findViewById<EditText>(R.id.edtWarehouseArea)
        val edtMatQtyInput = findViewById<EditText>(R.id.edtMatQty)  // For Nhập phôi mode
        val btnXuatHang = findViewById<Button>(R.id.btnXuatHang)
        val btnNhapHang = findViewById<Button>(R.id.btnNhapHang)
        val btnXuatHangTop = findViewById<Button>(R.id.btnXuatHangTop)

        // Track which mode is selected
        var isXuatHangModeSelected = false
        var isNhapHangModeSelected = false
        var selectedMaterialType = "" // "TẤM", "CÂY", or "ỐNG" - UI helper only
        var selectedMatIID = "" // Store the selected Material Input ID for POST function
        var actualMaterialName = "" // Store the actual material name from API for POST function

        val staffApi = RetrofitClient.retrofitPublic.create(StaffApi::class.java)
        val heatNoApi = RetrofitClient.retrofitPublic.create(HeatNoApi::class.java)
        val inputMaterialApi = RetrofitClient.retrofitPublic.create(InputMaterialApi::class.java)
        val postMUsingApi = RetrofitClient.retrofitPublic.create(PostMUsingApi::class.java)
        val postMInputApi = RetrofitClient.retrofitPublic.create(PostMInputApi::class.java)

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

        // Function to clear all form fields (EXCEPT staff input - preserve during mode switching)
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

            // Clear Xuất phôi mode fields (matQty and partQty)
            edtMatQty.setText("")
            edtMatQty.visibility = android.view.View.GONE
            txtMatQtyLabel.text = ""
            txtMatQtyLabel.visibility = android.view.View.GONE
            txtPartQtyLabel.text = ""
            txtPartQtyLabel.visibility = android.view.View.GONE
            edtPartQty.setText("")
            edtPartQty.visibility = android.view.View.GONE

            edtNotes.setText("")
            edtNotes.visibility = android.view.View.GONE

            // Clear Nhập phôi mode fields
            edtWarehouseArea.setText("")
            edtWarehouseArea.visibility = android.view.View.GONE
            edtMatQtyInput.setText("")
            edtMatQtyInput.visibility = android.view.View.GONE

            // Hide the update button
            btnXuatHang.visibility = android.view.View.GONE

            // Clear any dynamic buttons
            layoutHeatInfoButtons.removeAllViews()

            // Clear heat number field
            edtHeatNo.setText("")

            // PRESERVED: Staff input (edtStaffNo and txtStaffInfo) are NOT cleared
            // This allows the staff number to survive mode switching
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

                // Save staff number to SharedPreferences when user finishes editing
                if (staffNoStr.isNotEmpty()) {
                    staffPreferences.saveStaffNumber(staffNoStr)

                    try {
                        val staffNo = staffNoStr.toInt()
                        txtStaffInfo.text = getString(R.string.loading)
                        txtStaffInfo.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        lifecycleScope.launch {
                            try {
                                val info = staffApi.getStaff(staffNo)
                                if (info != null) {
                                    val staffInfoText = listOfNotNull(info.fullName, info.workJob, info.workPlace).joinToString(", ")
                                    txtStaffInfo.text = staffInfoText
                                    txtStaffInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))

                                    // Save staff info to SharedPreferences
                                    staffPreferences.saveStaffInfo(staffInfoText)
                                } else {
                                    txtStaffInfo.text = getString(R.string.data_not_found)
                                    txtStaffInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))
                                    staffPreferences.saveStaffInfo("")
                                }
                            } catch (_: Exception) {
                                txtStaffInfo.text = getString(R.string.data_not_found)
                                txtStaffInfo.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))
                                staffPreferences.saveStaffInfo("")
                            }
                        }
                    } catch (_: NumberFormatException) {
                        txtStaffInfo.text = getString(R.string.data_not_found)
                        staffPreferences.saveStaffInfo("")
                    }
                } else {
                    txtStaffInfo.text = ""
                    // Clear saved staff data if field is emptied
                    staffPreferences.clearStaffPreferences()
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
                                                // Parse existQty from API response for consistency with "SL vật liệu"
                                                txtMatQtyLabel.text = "Số lượng dùng:"
                                                txtMatQtyLabel.visibility = android.view.View.VISIBLE
                                                txtMatQtyLabel.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                                                edtMatQty.visibility = android.view.View.VISIBLE
                                                edtMatQty.setText(existQty)

                                                // Show part quantity with label above editable field
                                                // Parse qty from API response for consistency with "SL con hàng"
                                                txtPartQtyLabel.text = "Số lượng con hàng:"
                                                txtPartQtyLabel.visibility = android.view.View.VISIBLE
                                                txtPartQtyLabel.setTextColor(ContextCompat.getColor(context, android.R.color.black))
                                                edtPartQty.visibility = android.view.View.VISIBLE
                                                edtPartQty.setText(qty)

                                                // Show notes field
                                                edtNotes.visibility = android.view.View.VISIBLE


                                                // Hide product quantity and warehouse area fields
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

                                    // Store the actual material name for POST API
                                    actualMaterialName = material

                                    // Show material name field with API data
                                    txtSelectedMaterial.text = "Tên vật liệu: $material, P.O: $matPONo"
                                    txtSelectedMaterial.visibility = android.view.View.VISIBLE
                                    txtSelectedMaterial.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))

                                    txtHeatInfo.text = ""
                                } else {
                                    // No data found, show default
                                    actualMaterialName = ""
                                    txtSelectedMaterial.text = "Tên vật liệu: chưa có"
                                    txtSelectedMaterial.visibility = android.view.View.VISIBLE
                                    txtSelectedMaterial.setTextColor(ContextCompat.getColor(this@MaterialLogActivity, android.R.color.holo_red_dark))

                                    txtHeatInfo.text = ""
                                }
                            } catch (_: Exception) {
                                // API call failed, show default
                                actualMaterialName = ""
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
                        edtMatQty.visibility = android.view.View.GONE
                        txtMatQtyLabel.visibility = android.view.View.GONE
                        txtPartQtyLabel.visibility = android.view.View.GONE
                        edtPartQty.visibility = android.view.View.GONE
                        edtNotes.visibility = android.view.View.GONE
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

            // Show warehouse area, material quantity and update button
            edtWarehouseArea.visibility = android.view.View.VISIBLE
            edtMatQtyInput.visibility = android.view.View.VISIBLE
            btnXuatHang.visibility = android.view.View.VISIBLE
        }

        btnCay.setOnClickListener {
            resetMaterialTypeButtons()
            selectedMaterialType = "CÂY"
            btnCay.setBackgroundResource(R.drawable.button_activated_background)

            // Show CÂY specific fields
            txtSizeALabel.text = "Nhập đường kính"
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
            edtMatQtyInput.visibility = android.view.View.VISIBLE
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

            // Show warehouse area, material quantity and update button
            edtWarehouseArea.visibility = android.view.View.VISIBLE
            edtMatQtyInput.visibility = android.view.View.VISIBLE
            btnXuatHang.visibility = android.view.View.VISIBLE
        }

        // "Cập nhật" button click listener for submitting material usage data
        btnXuatHang.setOnClickListener {
            // Validate required fields for Xuất phôi mode
            if (isXuatHangModeSelected) {
                val staffNoStr = edtStaffNo.text.toString().trim()
                val jobNo = edtJobNo.text.toString().trim()
                val materialQtyStr = edtMatQty.text.toString().trim()
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
                    android.widget.Toast.makeText(this, "Vui lòng nhập số lượng con hàng", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                try {
                    val staffNo = staffNoStr.toInt()
                    val materialQty = materialQtyStr.toInt()  // Changed from toFloat() to toInt()
                    val partQty = partQtyStr.toInt()

                    // Create POST data object
                    val postData = PostMUsingDto(
                        staffNo = staffNo,
                        matIID = selectedMatIID,
                        matQty = materialQty,  // Now using Int instead of Float
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
                                if (responseBody != null) {
                                    // Success - show response message
                                    val successMessage = "Thành công!\n${responseBody.message}"

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
                                // Handle API error response - show more detailed error info
                                val httpCode = response.code()
                                val errorMessage = try {
                                    response.errorBody()?.string() ?: "Lỗi HTTP $httpCode"
                                } catch (e: Exception) {
                                    "Lỗi HTTP $httpCode - không thể đọc chi tiết lỗi"
                                }

                                android.util.Log.e("MaterialLogActivity", "HTTP Error $httpCode: $errorMessage")
                                android.widget.Toast.makeText(this@MaterialLogActivity, "Lỗi HTTP $httpCode: $errorMessage", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            // Handle network or other errors
                            android.widget.Toast.makeText(this@MaterialLogActivity, "Lỗi kết nối: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            android.util.Log.e("MaterialLogActivity", "POST request failed", e)
                        } finally {
                            // Reset button state
                            btnXuatHang.text = "Cập nhật"
                            btnXuatHang.isEnabled = true
                        }
                    }

                } catch (e: NumberFormatException) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập số hợp lệ", android.widget.Toast.LENGTH_SHORT).show()
                }
            } else if (isNhapHangModeSelected) {
                // Validate and submit for Nhập phôi mode
                val staffNoStr = edtStaffNo.text.toString().trim()
                val heatNoStr = edtHeatNo.text.toString().trim()
                val warehouseAreaStr = edtWarehouseArea.text.toString().trim()
                val matQtyStr = edtMatQtyInput.text.toString().trim()  // Fixed: Use edtMatQtyInput instead of edtMatQty

                // Validation
                if (staffNoStr.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập số thợ", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (heatNoStr.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập Heat Number", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (selectedMaterialType.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng chọn loại vật liệu", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (warehouseAreaStr.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập khu vực kho", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                if (matQtyStr.isEmpty()) {
                    android.widget.Toast.makeText(this, "Vui lòng nhập số lượng vật liệu", android.widget.Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // Function to convert sizeA, sizeB, sizeC to newInpSize1 and newInpSize2
                fun convertSizesToInpSizes(): Pair<String, String> {
                    val sizeA = edtSizeA.text.toString().trim()
                    val sizeB = edtSizeB.text.toString().trim()
                    val sizeC = edtSizeC.text.toString().trim()

                    return when (selectedMaterialType) {
                        "TẤM" -> {
                            // For TẤM: newInpSize1 = chiều rộng, newInpSize2 = độ dày*chiều dài
                            val newInpSize1 = sizeA // chiều rộng
                            val newInpSize2 = "${sizeB}*${sizeC}" // độ dày*chiều dài
                            Pair(newInpSize1, newInpSize2)
                        }
                        "CÂY" -> {
                            // For CÂY: newInpSize1 = đường kính, newInpSize2 = chiều dài
                            val newInpSize1 = sizeA // đường kính (D)
                            val newInpSize2 = sizeB // chiều dài
                            Pair(newInpSize1, newInpSize2)
                        }
                        "ỐNG" -> {
                            // For ỐNG: newInpSize1 = đường kính ngoài, newInpSize2 = đường kính trong*chiều dài
                            val newInpSize1 = sizeA // đường kính ngoài (OD)
                            val newInpSize2 = "${sizeB}*${sizeC}" // đường kính trong*chiều dài
                            Pair(newInpSize1, newInpSize2)
                        }
                        else -> Pair("", "")
                    }
                }

                try {
                    val staffNo = staffNoStr.toInt()
                    val matQty = matQtyStr.toInt()  // Changed from toFloat() to toInt()
                    val (newInpSize1, newInpSize2) = convertSizesToInpSizes()

                    // Create POST data object for material input using the correct API structure
                    val postData = PostMInputDto(
                        staffNo = staffNo,
                        heatNo = heatNoStr,
                        materialType = actualMaterialName, // Use actual material name from GetInputMaterial API
                        newInpSize1 = newInpSize1,
                        newInpSize2 = newInpSize2,
                        warehouseArea = warehouseAreaStr,
                        matQty = matQty
                    )

                    // Show loading state
                    btnXuatHang.text = "Đang xử lý..."
                    btnXuatHang.isEnabled = false

                    // Submit data to API
                    lifecycleScope.launch {
                        try {
                            val response = postMInputApi.postMaterialInput(postData)

                            if (response.isSuccessful) {
                                val responseBody = response.body()
                                if (responseBody != null) {
                                    // Success - show response message
                                    val successMessage = "Nhập phôi thành công!\n${responseBody.message}"
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
                                // Handle API error response - show more detailed error info
                                val httpCode = response.code()
                                val errorMessage = try {
                                    response.errorBody()?.string() ?: "Lỗi HTTP $httpCode"
                                } catch (e: Exception) {
                                    "Lỗi HTTP $httpCode - không thể đọc chi tiết lỗi"
                                }

                                android.util.Log.e("MaterialLogActivity", "HTTP Error $httpCode: $errorMessage")
                                android.widget.Toast.makeText(this@MaterialLogActivity, "Lỗi HTTP $httpCode: $errorMessage", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            // Handle network or other errors
                            android.widget.Toast.makeText(this@MaterialLogActivity, "Lỗi kết nối: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            android.util.Log.e("MaterialLogActivity", "POST request failed", e)
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

    // Auto-call staff API with saved staff number
    private fun autoCallStaffApi(staffNo: Int) {
        val txtStaffInfo = findViewById<TextView>(R.id.txtStaffInfo)
        val edtStaffNo = findViewById<EditText>(R.id.edtStaffNo)
        val staffApi = RetrofitClient.retrofitPublic.create(StaffApi::class.java)

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
    }
}
