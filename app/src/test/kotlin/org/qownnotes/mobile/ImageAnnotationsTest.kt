package org.qownnotes.mobile

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageAnnotationsTest {
    private val stroke = ImageAnnotation.Stroke(listOf(Offset(0f, 0f), Offset(100f, 0f)), 0, 4f)
    private val arrow = ImageAnnotation.Arrow(Offset(0f, 0f), Offset(0f, 100f), 0, 4f)

    @Test
    fun historyUndoesAndRedoesCompletedChanges() {
        val history = AnnotationHistory()
        assertFalse(history.canUndo)
        history.commit(listOf(stroke))
        history.commit(listOf(stroke, arrow))
        history.commit(listOf(stroke, arrow))
        history.undo()
        assertEquals(listOf(stroke), history.annotations)
        assertTrue(history.canRedo)
        history.redo()
        assertEquals(listOf(stroke, arrow), history.annotations)

        history.undo()
        history.commit(emptyList())
        // A new change after undoing starts a new branch; what was undone cannot come back.
        assertFalse(history.canRedo)
        history.undo()
        history.undo()
        assertEquals(emptyList<ImageAnnotation>(), history.annotations)
        assertFalse(history.canUndo)
    }

    @Test
    fun eraserHitsLinesNearTheirPathAndRectanglesOnTheirOutline() {
        assertTrue(stroke.isHit(Offset(50f, 8f), 6f))
        assertFalse(stroke.isHit(Offset(50f, 9f), 6f))
        assertTrue(stroke.isHit(Offset(104f, 0f), 6f))
        assertTrue(arrow.isHit(Offset(3f, 50f), 2f))

        val rectangle = ImageAnnotation.Rectangle(Offset(100f, 100f), Offset(0f, 0f), 0, 2f)
        assertTrue(rectangle.isHit(Offset(100f, 50f), 2f))
        assertFalse(rectangle.isHit(Offset(50f, 50f), 2f))
    }

    @Test
    fun strokesSkipPointsTooCloseToTheLastOne() {
        val points = listOf(
            Offset.Zero
        ).plusPoint(Offset(0.5f, 0f), 1f).plusPoint(Offset(2f, 0f), 1f)
        assertEquals(listOf(Offset.Zero, Offset(2f, 0f)), points)
    }

    @Test
    fun recognizesLightColorsForTheTextOutline() {
        assertTrue(isLight(0xFFFFFFFF.toInt()))
        assertTrue(isLight(0xFFFDD835.toInt()))
        assertFalse(isLight(0xFF000000.toInt()))
        assertFalse(isLight(0xFFE53935.toInt()))
    }
}
