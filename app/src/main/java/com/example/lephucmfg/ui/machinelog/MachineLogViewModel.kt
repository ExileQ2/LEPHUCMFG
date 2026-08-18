package com.example.lephucmfg.ui.machinelog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lephucmfg.data.machinelog.MachineInfoDto
import com.example.lephucmfg.data.machinelog.MachineLogHistoryItem
import com.example.lephucmfg.data.machinelog.MachineLogRepository
import com.example.lephucmfg.data.machinelog.MachineLogRequest
import com.example.lephucmfg.data.machinelog.ProcessInfoDto
import com.example.lephucmfg.data.machinelog.RoutingStepDto
import com.example.lephucmfg.data.machinelog.StaffInfoDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ScanTarget { STAFF, MACHINE, JOB, PRODUCTION_ORDER, SERIAL }

data class MachineLogUiState(
    val staffNo: String = "",
    val staffInfo: StaffInfoDto? = null,
    val machine: String = "",
    val machineInfo: MachineInfoDto? = null,
    val process: ProcessInfoDto? = null,
    val job: String = "",
    val jigDescription: String = "",
    val productionOrders: List<String> = emptyList(),
    val productionOrder: String = "",
    val serials: List<String> = emptyList(),
    val usedSerials: Set<String> = emptySet(),
    val selectedSerials: Set<String> = emptySet(),
    val manualSerials: String = "",
    val note: String = "",
    val qtyGood: String = "",
    val qtyReject: String = "",
    val qtyRework: String = "",
    val setup: Boolean = false,
    val rework: Boolean = false,
    val routing: List<RoutingStepDto> = emptyList(),
    val routingQuery: String = "",
    val routingDialogRequestId: Long = 0,
    val history: List<MachineLogHistoryItem> = emptyList(),
    val loading: Boolean = false,
    val submitting: Boolean = false,
    val message: String? = null
) {
    val hasActiveProcess: Boolean get() = !process?.processNo.isNullOrBlank()
    val isJigJob: Boolean get() = MachineLogLogic.isJigJob(job)
    val banner: String get() = MachineLogLogic.banner(machine, machineInfo?.status, hasActiveProcess)
    val submitBlocked: Boolean get() = MachineLogLogic.isSubmitBlocked(
        machine, machineInfo?.status, hasActiveProcess, staffNo, job, productionOrder
    ) || submitting
    val lockWorkFields: Boolean get() =
        MachineLogLogic.shouldLockWorkFields(machine, machineInfo?.status, hasActiveProcess)
    val lockJob: Boolean get() = hasActiveProcess || lockWorkFields
    val serialSummary: String get() = MachineLogLogic.summarizeSerialSelection(
        selectedSerials + MachineLogLogic.normalizeSerialList(listOf(manualSerials))
    )
}

class MachineLogViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MachineLogRepository(application)
    private val _state = MutableStateFlow(
        MachineLogUiState(
            staffNo = repository.savedStaffNo(),
            history = repository.history()
        )
    )
    val state: StateFlow<MachineLogUiState> = _state.asStateFlow()

    private var lookupJob: Job? = null

    fun setStaffNo(value: String) = _state.update { it.copy(staffNo = value.filter(Char::isDigit)) }
    fun setMachine(value: String) = _state.update { it.copy(machine = value.trim().uppercase()) }
    fun setJob(value: String) = _state.update {
        val normalized = value.trim().uppercase()
        it.copy(
            job = normalized,
            jigDescription = if (normalized == it.job) it.jigDescription else ""
        )
    }
    fun setProductionOrder(value: String) = _state.update { it.copy(productionOrder = value.trim().uppercase()) }
    fun setNote(value: String) = _state.update { it.copy(note = value) }
    fun setManualSerials(value: String) = _state.update { it.copy(manualSerials = value) }
    fun setQtyGood(value: String) = _state.update { it.copy(qtyGood = value.filter(Char::isDigit)) }
    fun setQtyReject(value: String) = _state.update { it.copy(qtyReject = value.filter(Char::isDigit)) }
    fun setQtyRework(value: String) = _state.update { it.copy(qtyRework = value.filter(Char::isDigit)) }
    fun setSetup(value: Boolean) = _state.update { it.copy(setup = value) }
    fun setRework(value: Boolean) = _state.update { it.copy(rework = value) }
    fun consumeMessage() = _state.update { it.copy(message = null) }

    fun lookupStaff() {
        val staff = state.value.staffNo.trim()
        if (staff.isBlank()) {
            _state.update { it.copy(staffInfo = null, process = null) }
            return
        }
        launchLookup {
            val info = repository.getStaff(staff)
            repository.saveStaffNo(staff)
            _state.update { it.copy(staffInfo = info) }
            refreshProcessIfPossible()
        }
    }

    fun lookupMachine() {
        val machine = state.value.machine.trim()
        if (machine.isBlank()) {
            _state.update { it.copy(machineInfo = null, process = null) }
            return
        }
        launchLookup {
            val info = repository.getMachine(machine)
            _state.update { it.copy(machineInfo = info) }
            refreshProcessIfPossible()
        }
    }

    private suspend fun refreshProcessIfPossible() {
        val current = state.value
        if (current.staffNo.isBlank() || current.machine.isBlank()) {
            _state.update { it.copy(process = null) }
            return
        }
        val process = repository.getActiveProcess(current.staffNo, current.machine)
        if (process.processNo.isBlank()) {
            _state.update { it.copy(process = null) }
            return
        }

        val machineModel = state.value.machineInfo?.model.orEmpty()
        val activeJob = process.jobControlNo.ifBlank { machineModel }.ifBlank { state.value.job }
        val activeIsJig = MachineLogLogic.isJigJob(activeJob)
        val activeJigDescription = if (activeIsJig) {
            runCatching { repository.getJigWork(activeJob).description }.getOrDefault("")
        } else {
            ""
        }
        _state.update {
            it.copy(
                process = process,
                job = activeJob,
                jigDescription = activeJigDescription,
                productionOrders = if (activeIsJig) emptyList() else it.productionOrders,
                productionOrder = if (activeIsJig) "" else process.proOrdNo2.ifBlank { it.productionOrder },
                serials = if (activeIsJig) emptyList() else it.serials,
                selectedSerials = if (activeIsJig) emptySet()
                    else MachineLogLogic.normalizeSerialList(listOf(process.serial2)).toSet(),
                manualSerials = "",
                note = process.note
            )
        }
        if (!activeIsJig && process.proOrdNo2.isNotBlank()) loadSerials(process.proOrdNo2)
    }

    fun resolveJob(jobNo: String = state.value.job) {
        val clean = MachineLogLogic.routingLookupJob(jobNo)
        if (clean.isBlank()) return
        setJob(clean)
        if (MachineLogLogic.isJigJob(clean)) {
            loadJigWork(clean)
        } else {
            requestRouting(clean)
        }
    }

    private fun loadJigWork(jobNo: String) {
        val clean = MachineLogLogic.routingLookupJob(jobNo)
        if (!MachineLogLogic.isJigJob(clean)) return
        launchLookup {
            val jig = repository.getJigWork(clean)
            _state.update {
                it.copy(
                    job = jig.jobNo.ifBlank { clean },
                    jigDescription = jig.description.ifBlank { jig.jobNo.ifBlank { clean } },
                    productionOrders = emptyList(),
                    productionOrder = "",
                    serials = emptyList(),
                    usedSerials = emptySet(),
                    selectedSerials = emptySet(),
                    manualSerials = "",
                    note = jig.suggestedNote,
                    qtyGood = "",
                    qtyReject = "",
                    qtyRework = "",
                    setup = false,
                    rework = false,
                    routing = emptyList(),
                    routingQuery = ""
                )
            }
        }
    }

    fun loadProductionOrders() {
        val job = state.value.job.trim()
        if (job.isBlank()) return
        if (MachineLogLogic.isJigJob(job)) {
            loadJigWork(job)
            return
        }
        launchLookup {
            val orders = repository.getProductionOrders(job)
            _state.update {
                it.copy(
                    productionOrders = orders,
                    productionOrder = if (orders.size == 1) orders.first() else "",
                    serials = emptyList(),
                    selectedSerials = emptySet(),
                    manualSerials = ""
                )
            }
            if (orders.size == 1) loadSerials(orders.first())
        }
    }

    fun selectProductionOrder(value: String) {
        _state.update {
            it.copy(
                productionOrder = value,
                selectedSerials = emptySet(),
                manualSerials = "",
                serials = emptyList()
            )
        }
        loadSerials(value)
    }

    fun loadSelectedProductionOrderSerials() = loadSerials(state.value.productionOrder)

    private fun loadSerials(productionOrder: String) {
        if (productionOrder.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val serials = repository.getSerials(productionOrder)
                val used = state.value.job.takeIf(String::isNotBlank)
                    ?.let { repository.getUsedSerials(it) }
                    .orEmpty()
                val expandedUsed = MachineLogLogic.normalizeSerialList(used).toSet()
                _state.update { it.copy(serials = serials, usedSerials = expandedUsed) }
            }.onFailure(::showError)
        }
    }

    fun toggleSerial(serial: String) = _state.update {
        val updated = it.selectedSerials.toMutableSet().apply {
            if (!add(serial)) remove(serial)
        }
        it.copy(selectedSerials = updated)
    }

    fun selectAllSerials(select: Boolean) = _state.update {
        it.copy(selectedSerials = if (select) it.serials.toSet() else emptySet())
    }

    fun requestRouting(jobNo: String = state.value.job) {
        val clean = MachineLogLogic.routingLookupJob(jobNo)
        if (clean.isBlank()) return
        if (MachineLogLogic.isJigJob(clean)) {
            loadJigWork(clean)
            return
        }
        _state.update {
            it.copy(
                job = clean,
                routing = emptyList(),
                routingQuery = clean,
                routingDialogRequestId = it.routingDialogRequestId + 1
            )
        }
        loadRouting(clean)
    }

    fun loadRouting(query: String) {
        val clean = MachineLogLogic.routingLookupJob(query)
        if (clean.isBlank()) return
        _state.update { it.copy(routingQuery = clean) }
        launchLookup {
            _state.update { it.copy(routing = repository.getRouting(clean)) }
        }
    }

    fun selectRoutingStep(step: RoutingStepDto) {
        val current = state.value
        val composed = MachineLogLogic.composeRoutingJob(
            jobNo = current.routingQuery,
            availableStepNos = current.routing.map { it.stepNo },
            selectedStepNo = step.stepNo
        )
        setJob(composed)
        _state.update { it.copy(routing = emptyList()) }
        loadProductionOrders()
    }

    fun applyScan(raw: String, target: ScanTarget) {
        val parsed = MachineLogLogic.parseQrPayload(raw)
        if (parsed.isNotEmpty()) {
            parsed["staffNo"]?.let(::setStaffNo)
            parsed["mcName"]?.let(::setMachine)
            parsed["jobNo"]?.let(::setJob)
            parsed["proOrdNo"]?.let { _state.update { state -> state.copy(productionOrder = it) } }
            parsed["serial"]?.let(::setManualSerials)
            if (target == ScanTarget.JOB && !parsed["jobNo"].isNullOrBlank()) {
                resolveJob(parsed.getValue("jobNo"))
                return
            }
            launchLookup {
                val current = state.value
                if (current.staffNo.isNotBlank()) {
                    val staff = repository.getStaff(current.staffNo)
                    repository.saveStaffNo(current.staffNo)
                    _state.update { it.copy(staffInfo = staff) }
                }
                if (current.machine.isNotBlank()) {
                    _state.update { it.copy(machineInfo = repository.getMachine(current.machine)) }
                    refreshProcessIfPossible()
                }
                val afterProcess = state.value
                if (!afterProcess.hasActiveProcess && afterProcess.job.isNotBlank()) {
                    if (MachineLogLogic.isJigJob(afterProcess.job)) {
                        val jig = repository.getJigWork(afterProcess.job)
                        _state.update {
                            it.copy(
                                job = jig.jobNo.ifBlank { afterProcess.job },
                                jigDescription = jig.description.ifBlank { jig.jobNo.ifBlank { afterProcess.job } },
                                productionOrders = emptyList(),
                                productionOrder = "",
                                serials = emptyList(),
                                selectedSerials = emptySet(),
                                manualSerials = "",
                                note = jig.suggestedNote
                            )
                        }
                        return@launchLookup
                    }
                    val orders = repository.getProductionOrders(afterProcess.job)
                    val selected = parsed["proOrdNo"] ?: orders.singleOrNull().orEmpty()
                    _state.update { it.copy(productionOrders = orders, productionOrder = selected) }
                    if (selected.isNotBlank()) loadSerials(selected)
                }
            }
            return
        }

        when (target) {
            ScanTarget.STAFF -> { setStaffNo(raw); lookupStaff() }
            ScanTarget.MACHINE -> { setMachine(raw); lookupMachine() }
            ScanTarget.JOB -> resolveJob(raw)
            ScanTarget.PRODUCTION_ORDER -> selectProductionOrder(raw.trim())
            ScanTarget.SERIAL -> setManualSerials(raw.trim())
        }
    }

    fun submit() {
        val current = state.value
        if (current.submitBlocked) return
        viewModelScope.launch {
            _state.update { it.copy(submitting = true, message = null) }
            runCatching {
                val jigWork = current.isJigJob
                val request = MachineLogRequest(
                    processNo = current.process?.processNo.orEmpty().trim(),
                    jobControlNo = current.job.trim(),
                    staffNo = current.staffNo.trim(),
                    mcName = current.machine.trim(),
                    note = current.note.trim(),
                    proOrdNo = if (jigWork) "" else current.productionOrder.trim(),
                    serial = if (jigWork) "" else current.serialSummary,
                    setup = if (jigWork) false else current.setup,
                    rework = if (jigWork) false else current.rework,
                    qtyGood = current.qtyGood.toIntOrNull() ?: 0,
                    qtyReject = current.qtyReject.toIntOrNull() ?: 0,
                    qtyRework = current.qtyRework.toIntOrNull() ?: 0
                )
                repository.submit(request)
                val history = MachineLogHistoryItem(
                    createdAt = System.currentTimeMillis(),
                    machine = request.mcName,
                    job = request.jobControlNo,
                    productionOrder = request.proOrdNo,
                    serial = request.serial,
                    action = if (current.hasActiveProcess) "Kết thúc" else "Bắt đầu"
                )
                repository.addHistory(history)
                _state.value = MachineLogUiState(
                    staffNo = current.staffNo,
                    staffInfo = current.staffInfo,
                    history = repository.history(),
                    message = "Đã lưu nhật ký máy"
                )
            }.onFailure(::showError)
            _state.update { it.copy(submitting = false) }
        }
    }

    private fun launchLookup(block: suspend () -> Unit) {
        lookupJob?.cancel()
        lookupJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, message = null) }
            runCatching { block() }.onFailure(::showError)
            _state.update { it.copy(loading = false) }
        }
    }

    private fun showError(error: Throwable) {
        _state.update { it.copy(message = error.message?.take(180) ?: "Không lấy được dữ liệu") }
    }
}
