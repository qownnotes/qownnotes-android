package org.qownnotes.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownEncryptionTest {
    @Test
    fun redactsQOwnNotesEncryptedPayloadAndPreservesSurroundingMarkdown() {
        val source = """
            # Visible title

            <!-- BEGIN ENCRYPTED TEXT --
            qon-crypto: 2
            ciphertext with [[Secret link]]
            -- END ENCRYPTED TEXT -->

            Visible suffix
        """.trimIndent()

        val result = redactEncryptedMarkdown(source, "[locked]")

        assertEquals(1, result.blockCount)
        assertFalse(result.malformed)
        assertEquals("# Visible title\n\n[locked]\n\nVisible suffix", result.markdown)
        assertFalse(result.markdown.contains("ciphertext"))
        assertFalse(result.markdown.contains("Secret link"))
    }

    @Test
    fun redactsMultipleBlocksWithDifferentLineEndings() {
        val source = "before\r\n<!-- BEGIN ENCRYPTED TEXT --\r\none\r\n" +
            "-- END ENCRYPTED TEXT -->\rbetween\r<!-- BEGIN ENCRYPTED TEXT --\rtwo\r" +
            "-- END ENCRYPTED TEXT -->\rafter"

        val result = redactEncryptedMarkdown(source, "LOCKED")

        assertEquals(2, result.blockCount)
        assertFalse(result.malformed)
        assertEquals("before\r\nLOCKED\rbetween\rLOCKED\rafter", result.markdown)
    }

    @Test
    fun missingEndMarkerFailsClosedThroughEndOfNote() {
        val result = redactEncryptedMarkdown(
            "visible\n<!-- BEGIN ENCRYPTED TEXT --\nsecret\n[[Hidden]]",
            "LOCKED"
        )

        assertEquals("visible\nLOCKED", result.markdown)
        assertEquals(1, result.blockCount)
        assertTrue(result.malformed)
    }

    @Test
    fun markerInsideFenceIsDetectedForDesktopCompatibility() {
        val source = "```\n<!-- BEGIN ENCRYPTED TEXT --\nsecret\n-- END ENCRYPTED TEXT -->\n```"

        val result = redactEncryptedMarkdown(source, "LOCKED")

        assertEquals("```\nLOCKED\n```", result.markdown)
        assertEquals(1, result.blockCount)
    }

    @Test
    fun orphanEndMarkerRemainsPlainMarkdown() {
        val source = "before\n-- END ENCRYPTED TEXT -->\nafter"

        assertEquals(
            RedactedEncryptedMarkdown(source, blockCount = 0, malformed = false),
            redactEncryptedMarkdown(source, "LOCKED")
        )
    }
}
