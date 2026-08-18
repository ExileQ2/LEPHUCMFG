package com.example.lephucmfg.data.machinelog

data class StaffInfoDto(
    val fullName: String = "",
    val workJob: String = "",
    val workPlace: String = ""
)

data class MachineInfoDto(
    val model: String = "",
    val status: String = ""
)

data class ProcessInfoDto(
    val processNo: String = "",
    val jobControlNo: String = "",
    val note: String = "",
    val serial2: String = "",
    val proOrdNo2: String = ""
)

data class JigWorkInfoDto(
    val jobNo: String = "",
    val found: Boolean = false,
    val description: String = "",
    val suggestedNote: String = ""
)

data class ProductionOrderDto(val jobControlNo: String = "")
data class SerialDto(val serial: String = "")
data class SerialListDto(val sr: String = "")

data class RoutingStepDto(
    val stepNo: Int = 0,
    val operation: String = "",
    val description: String = ""
)

data class MachineLogRequest(
    val processNo: String,
    val jobControlNo: String,
    val staffNo: String,
    val mcName: String,
    val note: String,
    val proOrdNo: String,
    val serial: String,
    val setup: Boolean,
    val rework: Boolean,
    val qtyGood: Int,
    val qtyReject: Int,
    val qtyRework: Int
)

data class MachineLogHistoryItem(
    val createdAt: Long,
    val machine: String,
    val job: String,
    val productionOrder: String,
    val serial: String,
    val action: String
)
