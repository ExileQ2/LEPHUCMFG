package com.example.lephucmfg

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
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
    interface StaffApi {
        @GET("/api/GetStaff/{staffNo}")
        suspend fun getStaff(@Path("staffNo") staffNo: Int): StaffInfo?
    }
    data class StaffInfo(
        @SerializedName("fullName") val fullName: String?,
        @SerializedName("workJob") val workJob: String?,
        @SerializedName("workPlace") val workPlace: String?
    )
    interface MachineApi {
        @GET("/api/GetMachine/{mcName}")
        suspend fun getMachine(@Path("mcName") mcName: String): MachineInfo?
    }
    data class MachineInfo(
        @SerializedName("model") val model: String?,
        @SerializedName("status") val status: String?
    )
    interface ProOrdApi {
        @GET("/api/Laylsx/{A}")
        suspend fun getProOrd(@Path("A") jobNo: String): ResponseBody
    }
    data class ProOrdDto(
        @SerializedName("proOrdNo") val proOrdNo: String?
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_machine_log)

        val edtStaffNo = findViewById<EditText>(R.id.edtStaffNo)
        val txtStaffInfo = findViewById<TextView>(R.id.txtStaffInfo)
        val api = RetrofitClient.retrofitPublic.create(StaffApi::class.java)

        edtStaffNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val staffNoStr = edtStaffNo.text.toString().trim()
                if (staffNoStr.isNotEmpty()) {
                    try {
                        val staffNo = staffNoStr.toInt()
                        lifecycleScope.launch {
                            try {
                                val info = api.getStaff(staffNo)
                                if (info != null) {
                                    txtStaffInfo.text = listOfNotNull(info.fullName, info.workJob, info.workPlace).joinToString(", ")
                                } else {
                                    txtStaffInfo.setText(R.string.invalid_staff)
                                }
                            } catch (e: Exception) {
                                txtStaffInfo.setText(R.string.invalid_staff)
                            }
                        }
                    } catch (e: NumberFormatException) {
                        txtStaffInfo.setText(R.string.invalid_staff)
                    }
                } else {
                    txtStaffInfo.text = ""
                }
            }
        }
        val edtMcName = findViewById<EditText>(R.id.edtMcName)
        val txtMachineInfo = findViewById<TextView>(R.id.txtMachineInfo)
        val machineApi = RetrofitClient.retrofitPublic.create(MachineApi::class.java)

        edtMcName.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val mcName = edtMcName.text.toString().trim()
                if (mcName.isNotEmpty()) {
                    lifecycleScope.launch {
                        try {
                            val info = machineApi.getMachine(mcName)
                            if (info != null) {
                                txtMachineInfo.text = listOfNotNull(info.model, info.status).joinToString(", ")
                            } else {
                                txtMachineInfo.setText(R.string.invalid_staff)
                            }
                        } catch (e: Exception) {
                            txtMachineInfo.setText(R.string.invalid_staff)
                        }
                    }
                } else {
                    txtMachineInfo.text = ""
                }
            }
        }
        val edtJobNo = findViewById<EditText>(R.id.edtJobNo)
        val dropProOrdNo = findViewById<android.widget.AutoCompleteTextView>(R.id.dropProOrdNo)
        val edtProOrdNo = findViewById<EditText>(R.id.edtProOrdNo)
        val proOrdApi = RetrofitClient.retrofitPublic.create(ProOrdApi::class.java)
        val proOrdAdapter = android.widget.ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        dropProOrdNo.setAdapter(proOrdAdapter)
        dropProOrdNo.setOnItemClickListener { _, _, position, _ ->
            val selected = proOrdAdapter.getItem(position)
            if (selected != null) edtProOrdNo.setText(selected)
        }
        edtJobNo.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val jobNo = edtJobNo.text.toString().trim()
                if (jobNo.isNotEmpty()) {
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
                            proOrdAdapter.clear()
                            dropProOrdNo.setText("", false)
                            if (proOrdList.isNotEmpty()) {
                                // Show all results as a single string in the dropProOrdNo box
                                dropProOrdNo.isEnabled = false
                                dropProOrdNo.setText(proOrdList.joinToString(", "), false)
                                // Optionally: let user tap the box to pick one
                                dropProOrdNo.setOnClickListener {
                                    val builder = android.app.AlertDialog.Builder(this@MachineLogActivity)
                                    builder.setTitle("Chọn LSX (ProOrdNo)")
                                    builder.setItems(proOrdList.toTypedArray()) { _, which ->
                                        edtProOrdNo.setText(proOrdList[which])
                                    }
                                    builder.show()
                                }
                            } else {
                                dropProOrdNo.isEnabled = false
                                dropProOrdNo.setText(getString(R.string.invalid_staff), false)
                                dropProOrdNo.setOnClickListener(null)
                            }
                        } catch (e: Exception) {
                            proOrdAdapter.clear()
                            dropProOrdNo.isEnabled = false
                            dropProOrdNo.setText(getString(R.string.invalid_staff), false)
                            dropProOrdNo.setOnClickListener(null)
                        }
                    }
                } else {
                    proOrdAdapter.clear()
                    dropProOrdNo.isEnabled = false
                    dropProOrdNo.setText("", false)
                    dropProOrdNo.setOnClickListener(null)
                }
            }
        }
    }
}
