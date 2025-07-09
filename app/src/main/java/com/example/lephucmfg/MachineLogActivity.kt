package com.example.lephucmfg // Ensure this matches your package name

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.semantics.text
import androidx.lifecycle.lifecycleScope
import com.example.lephucmfg.databinding.ActivityMachineLogBinding // If this is red, sync Gradle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class MachineLogActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMachineLogBinding
    private val apiService by lazy { ApiService.create() }

    companion object {
        const val EXTRA_QR_CONTENT = "EXTRA_QR_CONTENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMachineLogBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val qrContent = intent.getStringExtra(EXTRA_QR_CONTENT)
        if (!qrContent.isNullOrEmpty()) {
            binding.editTextMachineCode.setText(qrContent)
        }

        binding.buttonSubmit.setOnClickListener {
            submitData()
        }
    }

    private fun getCurrentDateTimeGmtPlus7(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.getDefault())
        sdf.timeZone = TimeZone.getTimeZone("GMT+07:00")
        return sdf.format(Date())
    }

    private fun submitData() {
        val jobDetail = binding.editTextJobDetail.text.toString().trim().ifEmpty { null }
        val perID = binding.editTextPerID.text.toString().toIntOrNull()
        val name = binding.editTextName.text.toString().trim().ifEmpty { null }
        val mno = binding.editTextMno.text.toString().toIntOrNull()
        val partNo = binding.editTextPartNo.text.toString().trim().ifEmpty { null }
        val jobPhase = binding.editTextJobPhase.text.toString().toIntOrNull()
        val setM = binding.checkBoxSetM.isChecked
        val pass = binding.editTextPass.text.toString().toIntOrNull()
        val fail = binding.editTextFail.text.toString().toIntOrNull()
        val reworkVal = binding.editTextRework.text.toString().toIntOrNull() // Renamed to avoid conflict
        val checkQuant = binding.editTextCheckQuant.text.toString().trim().let { if (it.isEmpty()) null else it.toIntOrNull() }
        val seriesNo = binding.editTextSeriesNo.text.toString().trim().ifEmpty { null }
        val startTime = getCurrentDateTimeGmtPlus7()
        val endTime = getCurrentDateTimeGmtPlus7()
        val timeCountM = binding.editTextTimeCountM.text.toString().toIntOrNull()
        val cycleTimeM = binding.editTextCycleTimeM.text.toString().toIntOrNull()
        val efficiency = binding.editTextEfficiency.text.toString().toDoubleOrNull()
        val workNo = binding.editTextWorkNo.text.toString().trim().ifEmpty { null }
        val productOrder = binding.editTextProductOrder.text.toString().trim().ifEmpty { null }
        val note = binding.editTextNote.text.toString().trim().ifEmpty { null }
        val company = binding.editTextCompany.text.toString().trim().ifEmpty { null }
        val groupName = binding.editTextGroupName.text.toString().trim().ifEmpty { null }
        val phaseName = binding.editTextPhaseName.text.toString().trim().ifEmpty { null }
        val reworkBit = binding.checkBoxReworkBit.isChecked
        val machineCode = binding.editTextMachineCode.text.toString().trim().ifEmpty { null }

        if (perID == null || name == null || name.isEmpty() || machineCode == null || machineCode.isEmpty()) {
            Toast.makeText(this, "Please fill required fields: PerID, Name, Machine Code", Toast.LENGTH_LONG).show()
            return
        }

        val scanData = ScanData(
            jobDetail = jobDetail, perID = perID, name = name, mno = mno, partNo = partNo,
            jobPhase = jobPhase, setM = setM, pass = pass, fail = fail, rework = reworkVal, // Use reworkVal
            checkQuant = checkQuant, seriesNo = seriesNo, startTime = startTime, endTime = endTime,
            timeCountM = timeCountM, cycleTimeM = cycleTimeM, efficiency = efficiency,
            workNo = workNo, productOrder = productOrder, note = note, company = company,
            groupName = groupName, phaseName = phaseName, reworkBit = reworkBit, machineCode = machineCode
        )

        lifecycleScope.launch {
            binding.buttonSubmit.isEnabled = false
            try {
                val response = apiService.submitScanData(scanData)
                if (response.isSuccessful) {
                    Toast.makeText(this@MachineLogActivity, "Data submitted successfully!", Toast.LENGTH_LONG).show()
                    finish()
                } else {
                    val errorBody = response.errorBody()?.string() ?: "Unknown server error"
                    Toast.makeText(this@MachineLogActivity, "Error: ${response.code()} - $errorBody", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MachineLogActivity, "Network Error: ${e.message}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } finally {
                binding.buttonSubmit.isEnabled = true
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}