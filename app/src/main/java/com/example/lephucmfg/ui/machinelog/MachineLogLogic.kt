package com.example.lephucmfg.ui.machinelog

import java.net.URLDecoder
import java.net.SocketTimeoutException
import java.nio.charset.StandardCharsets

object MachineLogLogic {
    private const val MAX_SERIAL_EXPANSION = 1_400
    private val exemptMachineCodes = (1..13).map { it.toString().padStart(3, '0') }.toSet()
    private val rangePattern = Regex("^(.*?)(\\d+)-(.*?)(\\d+)$")
    private val serialPattern = Regex("^(.*?)(\\d+)$")

    fun parseQrPayload(raw: String): Map<String, String> {
        val trimmed = raw.trim()
        if (!trimmed.contains('=')) return emptyMap()

        return trimmed
            .substringAfter('?', trimmed)
            .split('&')
            .mapNotNull { part ->
                val separator = part.indexOf('=')
                if (separator <= 0) return@mapNotNull null
                val key = decode(part.substring(0, separator)).trim()
                val value = decode(part.substring(separator + 1)).trim()
                canonicalQrKey(key)?.let { it to value }
            }
            .filter { it.second.isNotBlank() }
            .toMap()
    }

    fun expandSerialToken(token: String): List<String> {
        val clean = token.trim()
        val match = rangePattern.matchEntire(clean) ?: return listOf(clean)
        val (startPrefix, startDigits, endPrefix, endDigits) = match.destructured
        val effectiveEndPrefix = endPrefix.ifBlank { startPrefix }
        if (startPrefix != effectiveEndPrefix) return listOf(clean)

        val start = startDigits.toIntOrNull() ?: return listOf(clean)
        val end = endDigits.toIntOrNull() ?: return listOf(clean)
        val count = end - start + 1
        if (count !in 1..MAX_SERIAL_EXPANSION) return listOf(clean)
        val width = maxOf(startDigits.length, endDigits.length)
        return (start..end).map { "$startPrefix${it.toString().padStart(width, '0')}" }
    }

    fun normalizeSerialList(serials: Iterable<String>): List<String> = serials
        .flatMap { raw -> raw.split(',', ';', '\n').flatMap(::expandSerialToken) }
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinct()

    fun summarizeSerialSelection(serials: Iterable<String>): String {
        val normalized = normalizeSerialList(serials)
        if (normalized.isEmpty()) return ""

        data class ParsedSerial(
            val value: String,
            val prefix: String?,
            val number: Int?,
            val originalIndex: Int
        )
        data class SummaryPiece(val value: String, val order: Int)

        val parsed = normalized.mapIndexed { index, value ->
            val match = serialPattern.matchEntire(value)
            ParsedSerial(
                value = value,
                prefix = match?.groupValues?.get(1),
                number = match?.groupValues?.get(2)?.toIntOrNull(),
                originalIndex = index
            )
        }
        val numeric = parsed
            .filter { it.number != null }
            .sortedWith(
                compareBy<ParsedSerial>({ it.prefix.orEmpty() }, { it.number }, { it.originalIndex })
            )
        val pieces = mutableListOf<SummaryPiece>()

        var cursor = 0
        while (cursor < numeric.size) {
            val start = numeric[cursor]
            var end = start
            var nextIndex = cursor + 1
            var minOrder = start.originalIndex
            while (
                nextIndex < numeric.size &&
                numeric[nextIndex].prefix == start.prefix &&
                numeric[nextIndex].number == end.number!! + 1
            ) {
                end = numeric[nextIndex]
                minOrder = minOf(minOrder, end.originalIndex)
                nextIndex++
            }
            pieces += SummaryPiece(
                value = if (nextIndex - cursor >= 2) "${start.value}-${end.value}" else start.value,
                order = minOrder
            )
            cursor = nextIndex
        }

        parsed.filter { it.number == null }.forEach {
            pieces += SummaryPiece(it.value, it.originalIndex)
        }
        return pieces
            .sortedWith(compareBy<SummaryPiece>({ it.order }, { it.value }))
            .joinToString(", ") { it.value }
    }

    fun routingLookupJob(jobNo: String): String = jobNo.trim().uppercase()

    fun normalizeMachineCode(value: String): String =
        value.filter(Char::isDigit).take(3)

    fun isJigJob(jobNo: String): Boolean {
        val clean = jobNo.trim().uppercase()
        return clean.length > 1 && clean.first() == 'G' && clean.drop(1).all(Char::isDigit)
    }

    fun usesRouting(jobNo: String): Boolean = !isJigJob(jobNo)

    fun isTimeout(error: Throwable): Boolean {
        var current: Throwable? = error
        while (current != null) {
            if (current is SocketTimeoutException) return true
            current = current.cause
        }
        return false
    }

    fun submissionWasAppliedAfterTimeout(
        wasEnding: Boolean,
        attemptedProcessNo: String,
        attemptedJob: String,
        activeProcessNo: String,
        activeJob: String
    ): Boolean = if (wasEnding) {
        activeProcessNo.isBlank() ||
            !activeProcessNo.trim().equals(attemptedProcessNo.trim(), ignoreCase = true)
    } else {
        activeProcessNo.isNotBlank() &&
            activeJob.trim().equals(attemptedJob.trim(), ignoreCase = true)
    }

    fun composeRoutingJob(
        jobNo: String,
        availableStepNos: Iterable<Int>,
        selectedStepNo: Int
    ): String {
        val job = routingLookupJob(jobNo)
        val selectedStep = formatRoutingStep(selectedStepNo)
        val currentSuffix = job.takeLast(3)
        val alreadyHasStep = job.length > 3 && availableStepNos.any {
            formatRoutingStep(it) == currentSuffix
        }
        val baseJob = if (alreadyHasStep) job.dropLast(3) else job
        return baseJob + selectedStep
    }

    fun banner(machineCode: String, machineStatus: String?, hasActiveProcess: Boolean): String = when {
        hasActiveProcess -> "Máy đang chạy"
        machineStatus.containsStatus("Ready") -> "Đang chờ việc"
        machineStatus.containsStatus("Processing") && isExemptMachine(machineCode) ->
            "Đang gia công. Có thể nhập đè trên máy này"
        machineStatus.containsStatus("Processing") -> "Đang gia công bởi người khác"
        machineStatus.containsStatus("Maintenance") -> "Đang bảo trì"
        machineStatus.containsStatus("BeingSetup") -> "Đang setup"
        machineStatus.containsStatus("Damage") -> "Máy đang hỏng"
        machineCode.isNotBlank() -> "Không xác định trạng thái máy"
        else -> ""
    }

    fun isSubmitBlocked(
        machineCode: String,
        machineStatus: String?,
        hasActiveProcess: Boolean,
        staffNo: String,
        jobNo: String,
        productionOrder: String
    ): Boolean {
        if (machineCode.isBlank()) return true
        if (staffNo.isBlank() || jobNo.isBlank()) return true
        if (!isJigJob(jobNo) && productionOrder.isBlank()) return true
        if (isExemptMachine(machineCode)) return false
        if (hasActiveProcess) return false
        if (!canStartNewWork(machineStatus)) return true
        return false
    }

    fun canStartNewWork(machineStatus: String?): Boolean =
        machineStatus.containsStatus("Ready")

    fun shouldLockWorkFields(
        machineCode: String,
        machineStatus: String?,
        hasActiveProcess: Boolean
    ): Boolean = !isExemptMachine(machineCode) &&
        !hasActiveProcess &&
        !canStartNewWork(machineStatus)

    fun isExemptMachine(machineCode: String): Boolean =
        exemptMachineCodes.contains(machineCode.trim())

    private fun String?.containsStatus(status: String) =
        this?.contains("Status: $status", ignoreCase = true) == true ||
            this?.trim()?.equals(status, ignoreCase = true) == true

    private fun decode(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8.name())

    private fun formatRoutingStep(stepNo: Int): String = stepNo.toString().padStart(3, '0')

    private fun canonicalQrKey(key: String): String? = when (key.lowercase()) {
        "staffno", "staff", "nhanvien" -> "staffNo"
        "mcname", "machine", "may" -> "mcName"
        "jobno", "jobcontrolno", "job" -> "jobNo"
        "proordno", "productionorder", "lsx" -> "proOrdNo"
        "serial" -> "serial"
        else -> null
    }
}
