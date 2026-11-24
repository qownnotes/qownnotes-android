package org.qownnotes.mobile

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.SyncState

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class SharedFileUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var application: TestQOwnNotesApplication
    private lateinit var source: Uri
    private val component get() = application.component
    private val contents = "%PDF-1.4\n".toByteArray()

    @Before
    fun setUp() = runBlocking {
        application = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as TestQOwnNotesApplication
        application.reset()
        application.fakeSyncScheduler.pause()
        component.settings.setRefreshOnOpen(false)
        component.accountRepository.save(
            Account("share-account", "Personal", "https://example.com", "user", "user", "1.3")
        )
        source = requireNotNull(
            application.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, "Trip details.pdf")
                    put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
                }
            )
        )
        application.contentResolver.openOutputStream(source)!!.use { it.write(contents) }
        application.grantUriPermission(
            application.packageName,
            source,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    @After
    fun tearDown() = runBlocking {
        application.revokeUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        application.contentResolver.delete(source, null, null)
        application.reset()
    }

    @Test
    fun createsANoteFromTheSharedFileAndOpensIt() {
        share()
        compose.waitUntil(5_000) {
            component.fileShare.value is FileShareState.Ready
        }
        compose.onNodeWithTag("share-note-title").performTextReplacement("Trip paperwork")
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("save-shared-file").performClick()
        awaitNoteOpened()
        runBlocking {
            val note = component.noteRepository.observeNotes("share-account").first().single()
            assertEquals("Trip paperwork", note.title)
            assertTrue(
                component.noteRepository.get(note.localId)!!.content.contains("](attachments/")
            )
        }
    }

    @Test
    fun refusesAnUnsharedContentUri() {
        application.revokeUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        share()
        compose.waitUntil(5_000) { component.fileShare.value is FileShareState.Unavailable }
        compose.onNodeWithText(
            application.getString(R.string.shared_file_read_failed)
        ).assertExists()
        runBlocking {
            assertTrue(component.noteRepository.observeNotes("share-account").first().isEmpty())
        }
    }

    @Test
    fun searchesAndAppendsToAnExistingNestedNoteThenRetriesWithoutTheOriginalFile() {
        val note = runBlocking {
            val created = component.createNamedNote(
                "share-account",
                "Holiday checklist",
                "Trips/Europe"
            )
            component.refresh(created.accountId)
            application.fakeBackend.pushedNotes.clear()
            component.noteRepository.get(created.localId)!!
        }
        share()
        compose.waitUntil(5_000) { component.fileShare.value is FileShareState.Ready }
        compose.onNodeWithTag("share-destination-1").performClick()
        compose.onNodeWithTag("save-shared-file").assertIsNotEnabled()
        compose.onNodeWithTag("share-search-notes").performTextReplacement("Europe")
        val noteTag = "share-target-${note.localId}"
        compose.waitUntil(5_000) {
            compose.onAllNodesWithTag(noteTag).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag(noteTag).performClick()
        compose.onNodeWithTag("save-shared-file").performClick()
        awaitNoteOpened()
        application.revokeUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runBlocking {
            val updated = component.noteRepository.get(note.localId)!!
            val path = updated.content.substringAfter("](").substringBefore(')')
            assertTrue(path.startsWith("../../attachments/"))
            assertTrue(updated.content.startsWith(note.content))
            assertArrayEquals(
                contents,
                component.resolveAttachment(note.localId, path)!!.readBytes()
            )
            application.fakeBackend.imageUploadFailure =
                BackendException.Retryable(IOException("offline"))
            component.refresh(note.accountId)
            assertTrue(application.fakeBackend.pushedNotes.isEmpty())
            assertEquals(
                SyncState.LOCALLY_MODIFIED,
                component.noteRepository.get(note.localId)!!.syncState
            )

            application.fakeBackend.imageUploadFailure = null
            component.refresh(note.accountId)
            assertEquals(
                path.substringAfterLast('/'),
                application.fakeBackend.uploadedImages.single().first
            )
            assertArrayEquals(contents, application.fakeBackend.uploadedImages.single().second)
            assertTrue(application.fakeBackend.pushedNotes.single().content.contains(path))
        }
    }

    private fun share() {
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, source)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        compose.runOnUiThread { compose.activity.acceptShare(intent) }
    }

    private fun awaitNoteOpened() {
        compose.waitUntil(5_000) {
            component.fileShare.value == null &&
                runCatching { compose.onNodeWithTag("edit-note").fetchSemanticsNode() }.isSuccess
        }
    }
}
