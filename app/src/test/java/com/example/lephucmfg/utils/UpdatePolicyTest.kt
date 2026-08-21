package com.example.lephucmfg.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {

    @Test
    fun evaluate_usesVersionCodesAndMinimumSupportedVersion() {
        assertEquals(UpdateDecision.NONE, UpdatePolicy.evaluate(6, 6, 1))
        assertEquals(UpdateDecision.NONE, UpdatePolicy.evaluate(7, 6, 1))
        assertEquals(UpdateDecision.OPTIONAL, UpdatePolicy.evaluate(5, 6, 1))
        assertEquals(UpdateDecision.REQUIRED, UpdatePolicy.evaluate(5, 6, 6))
    }

    @Test
    fun automaticCheck_runsAtStartupThenAtTheConfiguredInterval() {
        val interval = 5 * 60 * 1000L
        assertTrue(UpdatePolicy.shouldCheckAutomatically(0, 1234, interval))
        assertFalse(UpdatePolicy.shouldCheckAutomatically(1_000, 1_000 + interval - 1, interval))
        assertTrue(UpdatePolicy.shouldCheckAutomatically(1_000, 1_000 + interval, interval))
        assertTrue(UpdatePolicy.shouldCheckAutomatically(5_000, 4_000, interval))
    }
}
