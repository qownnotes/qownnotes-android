package org.qownnotes.mobile.core

import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedAttachmentTest {
    @Test
    fun `uses a safe independently unique file name without losing the extension`() {
        val id = UUID.fromString("093650ab-a8ef-46f7-af82-fb2c43f15f46")
        assertEquals("A-report-$id.pdf", sharedAttachmentFileName("A [report] #.pdf", id))
        assertEquals("attachment-$id", sharedAttachmentFileName("..", id))
    }

    @Test
    fun `appends a link without changing existing whitespace and escapes its label`() {
        assertEquals(
            "# Trip\n\nExisting note  \n\n" +
                "[Budget \\[2026\\]\\.pdf](../../attachments/budget.pdf)\n",
            appendSharedAttachment(
                "# Trip\n\nExisting note  ",
                "Budget [2026].pdf",
                "Work/Travel",
                "budget.pdf",
                "application/pdf"
            )
        )
    }

    @Test
    fun `renders a screenshot inline but links to formats not handled by the image viewer`() {
        assertEquals(
            "# Note\n\n![Screenshot](media/screenshot.png)\n",
            appendSharedAttachment("# Note\n\n", "Screenshot", "", "screenshot.png", "image/png")
        )
        assertEquals(
            "[Drawing](media/drawing.svg)\n",
            appendSharedAttachment("", "Drawing", "", "drawing.svg", "image/svg+xml")
        )
    }
}
