package com.example.lephucmfg.ui.machinelog

import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MachineLogLogicTest {

    @Test
    fun timeoutDetection_followsWrappedCauses() {
        assertTrue(MachineLogLogic.isTimeout(IllegalStateException(SocketTimeoutException("slow"))))
        assertFalse(MachineLogLogic.isTimeout(IllegalStateException("bad request")))
    }

    @Test
    fun timeoutRecheck_confirmsMatchingStartOrCompletedExit() {
        assertTrue(
            MachineLogLogic.submissionWasAppliedAfterTimeout(
                wasEnding = false,
                attemptedProcessNo = "",
                attemptedJob = "G2600387",
                activeProcessNo = "P123",
                activeJob = "g2600387"
            )
        )
        assertFalse(
            MachineLogLogic.submissionWasAppliedAfterTimeout(
                wasEnding = false,
                attemptedProcessNo = "",
                attemptedJob = "G2600387",
                activeProcessNo = "P124",
                activeJob = "G2600999"
            )
        )
        assertTrue(
            MachineLogLogic.submissionWasAppliedAfterTimeout(
                wasEnding = true,
                attemptedProcessNo = "P123",
                attemptedJob = "G2600387",
                activeProcessNo = "",
                activeJob = ""
            )
        )
    }

    @Test
    fun machineCode_acceptsOnlyTheFirstThreeDigits() {
        assertEquals("155", MachineLogLogic.normalizeMachineCode(" 15A5B9 "))
        assertEquals("006", MachineLogLogic.normalizeMachineCode("006"))
    }

    @Test
    fun routing_keepsTheFullScannedJobAndOnlyReplacesAnExistingStep() {
        assertEquals("36786", MachineLogLogic.routingLookupJob(" 36786 "))
        assertEquals(
            "36786003",
            MachineLogLogic.composeRoutingJob("36786", listOf(1, 2, 3), selectedStepNo = 3)
        )
        assertEquals(
            "36786002",
            MachineLogLogic.composeRoutingJob("36786003", listOf(1, 2, 3), selectedStepNo = 2)
        )
    }

    @Test
    fun jigJobs_skipRoutingAndDoNotRequireProductionOrder() {
        assertTrue(MachineLogLogic.isJigJob(" g2600342 "))
        assertFalse(MachineLogLogic.usesRouting("G2600342"))
        assertTrue(MachineLogLogic.usesRouting("36786"))
        assertFalse(
            MachineLogLogic.isSubmitBlocked(
                machineCode = "020",
                machineStatus = "Status: Ready",
                hasActiveProcess = false,
                staffNo = "393",
                jobNo = "G2600342",
                productionOrder = ""
            )
        )
        assertTrue(
            MachineLogLogic.isSubmitBlocked(
                machineCode = "006",
                machineStatus = "Status: Processing",
                hasActiveProcess = false,
                staffNo = "",
                jobNo = "G2600342",
                productionOrder = ""
            )
        )
    }

    @Test
    fun parseQrPayload_supportsStructuredAndUrlEncodedValues() {
        val parsed = MachineLogLogic.parseQrPayload(
            "staffNo=39&mcName=006&jobNo=8S453T%28S1926%29010"
        )

        assertEquals("39", parsed["staffNo"])
        assertEquals("006", parsed["mcName"])
        assertEquals("8S453T(S1926)010", parsed["jobNo"])
    }

    @Test
    fun expandSerialToken_expandsPaddedRangesAndKeepsInvalidRanges() {
        assertEquals(
            listOf("SN001", "SN002", "SN003"),
            MachineLogLogic.expandSerialToken("SN001-SN003")
        )
        assertEquals(
            listOf("SN003-SN001"),
            MachineLogLogic.expandSerialToken("SN003-SN001")
        )
    }

    @Test
    fun summarizeSerialSelection_compressesConsecutiveSerials() {
        assertEquals(
            "SN001-SN003, X9",
            MachineLogLogic.summarizeSerialSelection(
                listOf("SN001", "SN002", "SN003", "X9")
            )
        )
    }

    @Test
    fun serialPicker_expandsApiRangesAndSummarizesDiscontinuousSelectionsInNumericOrder() {
        assertEquals(
            listOf("K1", "K2", "K3", "K5", "K7", "K8"),
            MachineLogLogic.normalizeSerialList(listOf("K1-K3", "K5; K7-K8"))
        )
        assertEquals(
            "K1-K3, K7-K8",
            MachineLogLogic.summarizeSerialSelection(
                listOf("K1", "K3", "K2", "K7", "K8")
            )
        )
    }

    @Test
    fun machineRules_matchWebSourceOfTruth() {
        assertEquals(
            "Đang gia công bởi người khác",
            MachineLogLogic.banner("020", "Status: Processing", hasActiveProcess = false)
        )
        assertTrue(
            MachineLogLogic.isSubmitBlocked(
                machineCode = "020",
                machineStatus = "Status: Processing",
                hasActiveProcess = false,
                staffNo = "39",
                jobNo = "JOB010",
                productionOrder = "PO1"
            )
        )
        assertFalse(
            MachineLogLogic.isSubmitBlocked(
                machineCode = "006",
                machineStatus = "Status: Processing",
                hasActiveProcess = false,
                staffNo = "39",
                jobNo = "36694001",
                productionOrder = "PO1"
            )
        )
        assertFalse(
            MachineLogLogic.isSubmitBlocked(
                machineCode = "006",
                machineStatus = "",
                hasActiveProcess = false,
                staffNo = "39",
                jobNo = "36694001",
                productionOrder = "PO1"
            )
        )
        assertFalse(
            MachineLogLogic.isSubmitBlocked(
                machineCode = "006",
                machineStatus = "Status: Ready",
                hasActiveProcess = false,
                staffNo = "39",
                jobNo = "36694001",
                productionOrder = "PO1"
            )
        )
        assertFalse(MachineLogLogic.shouldLockWorkFields("006", "Status: Processing", hasActiveProcess = false))
        assertFalse(MachineLogLogic.shouldLockWorkFields("013", null, hasActiveProcess = false))
        assertTrue(MachineLogLogic.shouldLockWorkFields("014", "Status: Processing", hasActiveProcess = false))
        assertTrue(MachineLogLogic.shouldLockWorkFields("020", null, hasActiveProcess = false))
        assertFalse(MachineLogLogic.shouldLockWorkFields("020", "Ready", hasActiveProcess = false))
        assertFalse(MachineLogLogic.shouldLockWorkFields("020", "Status: Processing", hasActiveProcess = true))
        assertEquals(
            "Đang gia công. Có thể nhập đè trên máy này",
            MachineLogLogic.banner("006", "Status: Processing", hasActiveProcess = false)
        )
        assertEquals(
            "Không xác định trạng thái máy",
            MachineLogLogic.banner("006", null, hasActiveProcess = false)
        )
    }
}
