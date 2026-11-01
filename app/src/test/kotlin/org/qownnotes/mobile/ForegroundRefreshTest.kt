package org.qownnotes.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ForegroundRefreshTest {
    @Test
    fun firstOpeningRefreshes() {
        assertTrue(foregroundRefreshDue(null, 1_000_000))
    }

    @Test
    fun rapidReopeningWaitsFiveMinutes() {
        assertFalse(foregroundRefreshDue(1_000_000, 1_000_000))
        assertFalse(foregroundRefreshDue(1_000_000, 1_299_999))
        assertTrue(foregroundRefreshDue(1_000_000, 1_300_000))
    }

    @Test
    fun clockMovingBackwardsDoesNotBlockRefreshIndefinitely() {
        assertTrue(foregroundRefreshDue(1_000_000, 999_999))
    }
}
