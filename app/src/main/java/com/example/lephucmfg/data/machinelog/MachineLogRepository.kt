package com.example.lephucmfg.data.machinelog

import android.content.Context
import com.example.lephucmfg.network.MachineLogApiService
import com.example.lephucmfg.network.RetrofitClient
import com.example.lephucmfg.ui.machinelog.MachineLogLogic
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class MachineLogRepository(
    context: Context,
    private val api: MachineLogApiService = RetrofitClient.machineLogService,
    private val gson: Gson = Gson()
) {
    private val preferences = context.getSharedPreferences("machine_log_native", Context.MODE_PRIVATE)

    suspend fun getStaff(staffNo: String) = api.getStaff(staffNo.toInt())
    suspend fun getMachine(machine: String) = api.getMachine(machine)
    suspend fun getActiveProcess(staffNo: String, machine: String) =
        api.getActiveProcess(staffNo, machine)

    suspend fun getProductionOrders(job: String) = api.getProductionOrders(job)
        .map { it.jobControlNo.trim() }
        .filter(String::isNotBlank)
        .distinct()

    suspend fun getSerials(productionOrder: String) = api.getSerials(productionOrder)
        .map { it.serial.trim() }
        .filter(String::isNotBlank)
        .let(MachineLogLogic::normalizeSerialList)

    suspend fun getUsedSerials(job: String) = api.getUsedSerials(job).sr
        .split(',', ';', '\n', '\r')
        .map(String::trim)
        .filter(String::isNotBlank)

    suspend fun getRouting(job: String) = api.getRouting(job).sortedBy { it.stepNo }

    suspend fun getJigWork(job: String) = api.getJigWork(job)

    suspend fun submit(request: MachineLogRequest): MachineLogSubmitResponse {
        val response = api.submit(request)
        if (!response.isSuccessful) error("Máy chủ từ chối (${response.code()})")
        return response.body() ?: error("Máy chủ không trả kết quả")
    }

    fun savedStaffNo(): String = preferences.getString(KEY_STAFF_NO, "").orEmpty()

    fun saveStaffNo(value: String) {
        preferences.edit().putString(KEY_STAFF_NO, value).apply()
    }

    fun history(): List<MachineLogHistoryItem> {
        val raw = preferences.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<MachineLogHistoryItem>>() {}.type
        return runCatching { gson.fromJson<List<MachineLogHistoryItem>>(raw, type) }
            .getOrDefault(emptyList())
    }

    fun addHistory(item: MachineLogHistoryItem) {
        val updated = (listOf(item) + history()).take(5)
        preferences.edit().putString(KEY_HISTORY, gson.toJson(updated)).apply()
    }

    private companion object {
        const val KEY_STAFF_NO = "staff_no"
        const val KEY_HISTORY = "history"
    }
}
