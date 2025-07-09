package com.example.lephucmfg

import com.google.gson.annotations.SerializedName

data class ScanData(
    @SerializedName("Jobdetail") val jobDetail: String?,
    @SerializedName("PerID") val perID: Int?,
    @SerializedName("Name") val name: String?,
    @SerializedName("Mno") val mno: Int?,
    @SerializedName("Partno") val partNo: String?,
    @SerializedName("JobPhase") val jobPhase: Int?,
    @SerializedName("SetM") val setM: Boolean,
    @SerializedName("Pass") val pass: Int?,
    @SerializedName("Fail") val fail: Int?,
    @SerializedName("Rework") val rework: Int?,
    @SerializedName("CheckQuant") val checkQuant: Int?,
    @SerializedName("Seriesno") val seriesNo: String?,
    @SerializedName("StartTime") val startTime: String?,
    @SerializedName("EndTime") val endTime: String?,
    @SerializedName("TimeCountM") val timeCountM: Int?,
    @SerializedName("CycleTimeM") val cycleTimeM: Int?,
    @SerializedName("Efficiency") val efficiency: Double?,
    @SerializedName("Workno") val workNo: String?,
    @SerializedName("ProductOrder") val productOrder: String?,
    @SerializedName("Note") val note: String?,
    @SerializedName("Company") val company: String?,
    @SerializedName("Groupname") val groupName: String?,
    @SerializedName("PhaseName") val phaseName: String?,
    @SerializedName("ReworkBit") val reworkBit: Boolean,
    @SerializedName("MachineCode") val machineCode: String?
)
