package com.example.lephucmfg

import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.network.RetrofitClient
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.launch
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
    }
}
