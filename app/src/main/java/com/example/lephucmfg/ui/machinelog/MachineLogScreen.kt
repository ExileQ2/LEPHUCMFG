package com.example.lephucmfg.ui.machinelog

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MachineLogScreen(
    viewModel: MachineLogViewModel,
    onBack: () -> Unit,
    onScan: (ScanTarget) -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showRouting by remember { mutableStateOf(false) }
    var showSerials by remember { mutableStateOf(false) }
    var showHistory by remember { mutableStateOf(false) }
    var showExitConfirmation by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    LaunchedEffect(state.routingDialogRequestId) {
        if (state.routingDialogRequestId > 0) showRouting = true
    }

    LaunchedEffect(state.isJigJob) {
        if (state.isJigJob) {
            showRouting = false
            showSerials = false
        }
    }

    LaunchedEffect(state.hasActiveProcess) {
        if (!state.hasActiveProcess) showExitConfirmation = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Khai báo máy") },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Về") } },
                actions = { TextButton(onClick = { showHistory = true }) { Text("Lịch sử") } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Spacer(Modifier.height(2.dp))
                LabeledScanField(
                    label = "Mã nhân viên",
                    value = state.staffNo,
                    onValueChange = viewModel::setStaffNo,
                    onDone = viewModel::lookupStaff,
                    onScan = { onScan(ScanTarget.STAFF) },
                    keyboardType = KeyboardType.Number
                )
                val info = state.staffInfo
                if (info != null) {
                    HintText(listOf(info.fullName, info.workJob, info.workPlace).filter(String::isNotBlank).joinToString(" • "))
                }
            }

            item {
                LabeledScanField(
                    label = "Mã máy",
                    value = state.machine,
                    onValueChange = viewModel::setMachine,
                    onDone = viewModel::lookupMachine,
                    onScan = { onScan(ScanTarget.MACHINE) },
                    keyboardType = KeyboardType.Number
                )
                state.machineInfo?.let { HintText(listOf(it.model, it.status).filter(String::isNotBlank).joinToString(" • ")) }
            }

            if (state.loading) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Đang lấy dữ liệu…", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            if (state.banner.isNotBlank()) {
                item { StatusBanner(state.banner, state.lockWorkFields) }
            }

            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.weight(1f)) {
                        LabeledScanField(
                            label = "Job No",
                            value = state.job,
                            onValueChange = viewModel::setJob,
                            onDone = viewModel::resolveJob,
                            onScan = { onScan(ScanTarget.JOB) },
                            enabled = !state.lockJob,
                            lookupOnFocusLoss = false
                        )
                    }
                    if (!state.isJigJob) {
                        Spacer(Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = {
                                if (state.job.isBlank()) showRouting = true
                                else viewModel.requestRouting(state.job)
                            },
                            enabled = !state.lockJob
                        ) { Text("Routing") }
                    }
                }
            }

            if (state.isJigJob && state.jigDescription.isNotBlank()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE0F2FE)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = state.jigDescription,
                            color = Color(0xFF0369A1),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            if (!state.isJigJob && state.productionOrders.isNotEmpty()) {
                item {
                    Text("Lệnh sản xuất", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.productionOrders) { order ->
                            FilterChip(
                                selected = order == state.productionOrder,
                                onClick = { viewModel.selectProductionOrder(order) },
                                enabled = !state.lockJob,
                                label = { Text(order) }
                            )
                        }
                    }
                }
            } else if (!state.isJigJob) {
                item {
                    LabeledScanField(
                        label = "Lệnh sản xuất",
                        value = state.productionOrder,
                        onValueChange = viewModel::setProductionOrder,
                        onDone = viewModel::loadSelectedProductionOrderSerials,
                        onScan = { onScan(ScanTarget.PRODUCTION_ORDER) },
                        enabled = !state.lockJob
                    )
                }
            }

            if (!state.isJigJob) item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = state.serialSummary,
                        onValueChange = {},
                        readOnly = true,
                        enabled = !state.lockWorkFields,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Serial") },
                        placeholder = { Text("Chưa chọn serial") }
                    )
                    OutlinedButton(
                        onClick = { showSerials = true },
                        enabled = !state.lockWorkFields && state.productionOrder.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            if (state.serials.isEmpty()) "CHỌN / NHẬP SERIAL"
                            else "CHỌN SERIAL (${state.serials.size})"
                        )
                    }
                }
            }

            item {
                OutlinedTextField(
                    value = state.note,
                    onValueChange = viewModel::setNote,
                    enabled = !state.lockWorkFields,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Ghi chú") },
                    minLines = 2,
                    maxLines = 4
                )
            }

            item {
                Text("Số lượng", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuantityField("Đạt", state.qtyGood, viewModel::setQtyGood, Modifier.weight(1f), !state.lockWorkFields)
                    QuantityField("Hỏng", state.qtyReject, viewModel::setQtyReject, Modifier.weight(1f), !state.lockWorkFields)
                    QuantityField("Làm lại", state.qtyRework, viewModel::setQtyRework, Modifier.weight(1f), !state.lockWorkFields)
                }
            }

            if (!state.isJigJob) item {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Toggle("Setup", state.setup, viewModel::setSetup, !state.lockWorkFields)
                    Toggle("Rework", state.rework, viewModel::setRework, !state.lockWorkFields)
                }
            }

            item {
                Button(
                    onClick = {
                        if (state.hasActiveProcess) showExitConfirmation = true
                        else viewModel.submit()
                    },
                    enabled = !state.submitBlocked,
                    modifier = Modifier.fillMaxWidth().height(54.dp)
                ) {
                    if (state.submitting) CircularProgressIndicator(modifier = Modifier.width(22.dp), strokeWidth = 2.dp)
                    else Text(if (state.hasActiveProcess) "KẾT THÚC CÔNG VIỆC" else "BẮT ĐẦU CÔNG VIỆC")
                }
                state.submitHint.takeIf(String::isNotBlank)?.let { HintText(it) }
                Spacer(Modifier.height(18.dp))
            }
        }
    }

    if (showRouting && !state.isJigJob) {
        RoutingDialog(
            state = state,
            onDismiss = { showRouting = false },
            onSearch = viewModel::loadRouting,
            onSelect = { viewModel.selectRoutingStep(it); showRouting = false }
        )
    }
    if (showSerials && !state.isJigJob) {
        SerialDialog(
            state = state,
            onDismiss = { showSerials = false },
            onToggle = viewModel::toggleSerial,
            onSelectAll = viewModel::selectAllSerials,
            onManual = viewModel::setManualSerials,
            onScan = { onScan(ScanTarget.SERIAL) }
        )
    }
    if (showHistory) {
        HistoryDialog(state.history) { showHistory = false }
    }
    if (showExitConfirmation && state.hasActiveProcess) {
        ExitWorkConfirmationDialog(
            state = state,
            onDismiss = { showExitConfirmation = false },
            onConfirm = {
                showExitConfirmation = false
                viewModel.submit()
            }
        )
    }
    if (state.submitSuccess) {
        val returnHome = {
            viewModel.consumeSubmitSuccess()
            onBack()
        }
        AlertDialog(
            onDismissRequest = returnHome,
            title = { Text("Đã lưu thành công") },
            confirmButton = {
                Button(onClick = returnHome) { Text("VỀ TRANG CHỦ") }
            }
        )
    }
}

@Composable
private fun ExitWorkConfirmationDialog(
    state: MachineLogUiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    var confirmed by remember(state.process?.processNo) { mutableStateOf(false) }
    val staffName = state.staffInfo?.fullName?.trim().orEmpty()
        .ifBlank { "Nhân viên ${state.staffNo}" }
    val workDescription = if (state.isJigJob) {
        "Đồ gá: ${state.jigDescription.ifBlank { state.job }}"
    } else {
        "Công đoạn: ${state.job}"
    }
    val serial = state.process?.serial2?.trim().orEmpty().ifBlank { state.serialSummary }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thoát máy ${state.machine}?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(staffName, fontWeight = FontWeight.SemiBold)
                Text(workDescription, fontWeight = FontWeight.SemiBold)
                if (!state.isJigJob && serial.isNotBlank()) Text("Serial: $serial")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { confirmed = !confirmed }
                ) {
                    Checkbox(checked = confirmed, onCheckedChange = { confirmed = it })
                    Text("Xác nhận thoát máy")
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = confirmed && !state.submitting
            ) { Text("THOÁT MÁY") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.submitting) { Text("HỦY") }
        }
    )
}

@Composable
private fun LabeledScanField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    onScan: () -> Unit,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    lookupOnFocusLoss: Boolean = true
) {
    var wasFocused by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (lookupOnFocusLoss && wasFocused && !focusState.isFocused) onDone()
                wasFocused = focusState.isFocused
            },
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        trailingIcon = { TextButton(onClick = onScan, enabled = enabled) { Text("Quét") } }
    )
}

@Composable
private fun QuantityField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier,
    enabled: Boolean
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun Toggle(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, enabled: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label)
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChecked, enabled = enabled)
    }
}

@Composable
private fun HintText(text: String) {
    if (text.isNotBlank()) Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF52606D))
}

private val MachineLogUiState.submitHint: String
    get() = when {
        loading || submitting || !submitBlocked -> ""
        staffNo.isBlank() -> "Nhập mã nhân viên"
        machine.length < 3 -> "Nhập đủ 3 số mã máy"
        lockWorkFields -> ""
        job.isBlank() -> "Quét Job No"
        !isJigJob && productionOrder.isBlank() -> "Chọn lệnh sản xuất"
        else -> ""
    }

@Composable
private fun StatusBanner(text: String, blocked: Boolean) {
    val color = if (blocked) Color(0xFFFFE1E1) else Color(0xFFDDF5E5)
    Card(colors = CardDefaults.cardColors(containerColor = color), modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.padding(12.dp), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RoutingDialog(
    state: MachineLogUiState,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (com.example.lephucmfg.data.machinelog.RoutingStepDto) -> Unit
) {
    var query by remember(state.job) {
        mutableStateOf(MachineLogLogic.routingLookupJob(state.job))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn công đoạn Routing") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(query, { query = it.uppercase() }, label = { Text("Job gốc") }, singleLine = true)
                Button(
                    onClick = { onSearch(query) },
                    enabled = query.isNotBlank() && !state.loading,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (state.loading) "Đang tra cứu…" else "Tra cứu") }
                if (state.loading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.width(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Đang lấy công đoạn…", style = MaterialTheme.typography.bodySmall)
                    }
                }
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(state.routing) { step ->
                        TextButton(onClick = { onSelect(step) }, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text("${step.stepNo.toString().padStart(3, '0')} • ${step.operation}", fontWeight = FontWeight.Bold)
                                HintText(step.description)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}

@Composable
private fun SerialDialog(
    state: MachineLogUiState,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onManual: (String) -> Unit,
    onScan: () -> Unit
) {
    val allSelected = state.serials.isNotEmpty() && state.serials.all(state.selectedSerials::contains)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Chọn serial") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "LSX ${state.productionOrder} • ${state.selectedSerials.size} đã chọn",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF1D4ED8)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { onSelectAll(!allSelected) },
                        enabled = state.serials.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (allSelected) "Bỏ chọn tất cả" else "Chọn tất cả (${state.serials.size})")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = onScan) { Text("Quét") }
                }
                if (state.serials.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Chưa có serial")
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        gridItems(state.serials, key = { it }) { serial ->
                            val selected = serial in state.selectedSerials
                            val used = serial in state.usedSerials
                            val background = when {
                                selected -> Color(0xFF2563EB)
                                used -> Color(0xFFE5E7EB)
                                else -> Color(0xFFF8FAFC)
                            }
                            val border = when {
                                selected -> Color(0xFF1D4ED8)
                                used -> Color(0xFF94A3B8)
                                else -> Color(0xFFCBD5E1)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .background(background, RoundedCornerShape(8.dp))
                                    .border(1.dp, border, RoundedCornerShape(8.dp))
                                    .clickable { onToggle(serial) },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        serial,
                                        color = if (selected) Color.White else Color(0xFF334155),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (used) {
                                        Text(
                                            "đã dùng",
                                            color = if (selected) Color(0xFFDBEAFE) else Color(0xFF64748B),
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(
                    state.manualSerials,
                    onManual,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Nhập tay: K1-K100, K105") },
                    minLines = 1,
                    maxLines = 2
                )
                HintText("Kết quả: ${state.serialSummary.ifBlank { "chưa chọn serial nào" }}")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Xác nhận") } },
        dismissButton = { TextButton(onClick = { onSelectAll(false) }) { Text("Bỏ chọn") } }
    )
}

@Composable
private fun HistoryDialog(history: List<com.example.lephucmfg.data.machinelog.MachineLogHistoryItem>, onDismiss: () -> Unit) {
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("5 lần khai báo gần nhất") },
        text = {
            LazyColumn(modifier = Modifier.height(300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isEmpty()) item { Text("Chưa có lịch sử trên máy này.") }
                items(history) { item ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(10.dp)) {
                            Text("${item.action} • Máy ${item.machine}", fontWeight = FontWeight.Bold)
                            Text("${item.job} • ${item.productionOrder}")
                            HintText("${item.serial} • ${formatter.format(Date(item.createdAt))}")
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Đóng") } }
    )
}
