package org.qownnotes.mobile.data

import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PendingSharedAttachmentsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test
    fun keepsTheBytesAndUploadCheckpointAcrossStoreRecreation() = runBlocking {
        val id = UUID.randomUUID()
        val directory = temporary.newFolder()
        val bytes = byteArrayOf(1, 2, 3)
        val store = PendingSharedAttachments(directory)
        val entry = store.save("account", id, "file.pdf", "application/pdf", 3) {
            bytes.inputStream()
        }
        assertFalse(entry.uploaded)
        assertTrue(entry.isLinkedIn("[File](../../attachments/file.pdf)"))
        assertFalse(entry.isLinkedIn("[Another](attachments/file.pdf.txt)"))
        assertTrue(store.entries("another-account").isEmpty())
        entry.markUploaded()

        val reopened = PendingSharedAttachments(directory).entries("account").single()
        assertArrayEquals(bytes, reopened.file.readBytes())
        assertTrue(reopened.uploaded)
        reopened.remove()
        assertTrue(store.entries("account").isEmpty())
    }

    @Test
    fun rejectsOversizedStreamsWithoutLeavingPartialFiles() = runBlocking {
        val directory = temporary.newFolder()
        val store = PendingSharedAttachments(directory)
        val result = runCatching {
            store.save("account", UUID.randomUUID(), "file.pdf", "application/pdf", 3) {
                byteArrayOf(1, 2, 3, 4).inputStream()
            }
        }
        assertTrue(result.exceptionOrNull() is SharedAttachmentTooLargeException)
        assertTrue(directory.walk().none { it.isFile })
    }
}
