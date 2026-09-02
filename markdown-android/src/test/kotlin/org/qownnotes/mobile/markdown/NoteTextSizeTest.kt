package org.qownnotes.mobile.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTextSizeTest {
    @Test
    fun stepsAreAscendingAndContainTheDefault() {
        assertEquals(NoteTextSize.steps.sorted(), NoteTextSize.steps)
        assertEquals(NoteTextSize.steps.distinct(), NoteTextSize.steps)
        assertTrue(NoteTextSize.DEFAULT_SP in NoteTextSize.steps)
    }

    @Test
    fun increasingAndDecreasingWalkTheSteps() {
        assertEquals(18, NoteTextSize.increase(16))
        assertEquals(20, NoteTextSize.increase(18))
        assertEquals(14, NoteTextSize.decrease(16))
        assertEquals(12, NoteTextSize.decrease(14))
    }

    @Test
    fun stepsSaturateAtBothEnds() {
        assertEquals(NoteTextSize.largest, NoteTextSize.increase(NoteTextSize.largest))
        assertEquals(NoteTextSize.smallest, NoteTextSize.decrease(NoteTextSize.smallest))
        assertFalse(NoteTextSize.canIncrease(NoteTextSize.largest))
        assertFalse(NoteTextSize.canDecrease(NoteTextSize.smallest))
        assertTrue(NoteTextSize.canIncrease(NoteTextSize.smallest))
        assertTrue(NoteTextSize.canDecrease(NoteTextSize.largest))
    }

    @Test
    fun unknownStoredValuesSnapToTheNearestStep() {
        assertEquals(16, NoteTextSize.coerce(16))
        assertEquals(16, NoteTextSize.coerce(17))
        assertEquals(18, NoteTextSize.coerce(19))
        assertEquals(NoteTextSize.smallest, NoteTextSize.coerce(-40))
        assertEquals(NoteTextSize.largest, NoteTextSize.coerce(500))
    }

    @Test
    fun steppingAnUnknownValueFirstSnapsIt() {
        // 17 snaps to 16, so increasing must reach 18 rather than skipping a step.
        assertEquals(18, NoteTextSize.increase(17))
        assertEquals(14, NoteTextSize.decrease(17))
    }
}
