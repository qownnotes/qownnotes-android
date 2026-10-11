package org.qownnotes.mobile

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException
import org.qownnotes.mobile.core.SyncState

@RunWith(AndroidJUnit4::class)
class CapturedNoteTest {
    private lateinit var application: TestQOwnNotesApplication
    private lateinit var source: File

    @Before
    fun setUp() = runBlocking {
        application = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as TestQOwnNotesApplication
        application.reset()
        application.fakeSyncScheduler.pause()
        application.component.accountRepository.save(
            Account("camera-account", "Camera", "https://example.com", "user", "user", "1.3")
        )
        source = File(application.filesDir, "camera/test.jpg")
        source.parentFile!!.mkdirs()
        source.writeBytes(byteArrayOf(1, 2, 3))
    }

    @After
    fun tearDown() = runBlocking {
        application.reset()
        source.delete()
        Unit
    }

    @Test
    fun widgetCameraResultOpensLocallySavedNoteWithoutNetwork() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != MediaStore.ACTION_IMAGE_CAPTURE) return null
                @Suppress("DEPRECATION")
                val uri = intent.getParcelableExtra<Uri>(MediaStore.EXTRA_OUTPUT)!!
                application.contentResolver.openOutputStream(uri)!!.use {
                    it.write(byteArrayOf(1, 2, 3))
                }
                return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
            }
        }
        instrumentation.addMonitor(monitor)
        try {
            ActivityScenario.launch<CaptureNoteActivity>(
                CaptureNoteActivity.intent(application, "camera-account", 29)
            ).use { scenario ->
                val note = runBlocking {
                    withTimeout(5_000) {
                        val item = application.component.noteRepository.observeNotes(
                            "camera-account"
                        )
                            .first { it.isNotEmpty() }.single()
                        application.component.noteRepository.get(item.localId)!!
                    }
                }
                assertTrue(note.content.contains("![image](media/"))
                assertTrue(application.fakeBackend.pushedNotes.isEmpty())
                assertTrue(application.fakeBackend.uploadedImages.isEmpty())
                runBlocking {
                    withTimeout(5_000) {
                        while (scenario.state != Lifecycle.State.DESTROYED) delay(10)
                    }
                }
            }
        } finally {
            instrumentation.removeMonitor(monitor)
            instrumentation.runOnMainSync {
                ActivityLifecycleMonitorRegistry.getInstance()
                    .getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>()
                    .forEach(Activity::finish)
            }
        }
    }

    @Test
    fun uploadedPhotoIsRetainedWhenNoteCreationResponseIsLost() = runBlocking {
        val id = UUID.randomUUID().toString()
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            source
        )
        application.component.createCapturedNote("camera-account", id, uri)
        application.fakeBackend.createFailure =
            BackendException.Retryable(IOException("response lost"))

        application.component.refresh("camera-account")

        assertEquals(SyncState.FAILED, application.component.noteRepository.get(id)!!.syncState)
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            File(application.filesDir, "pending-captures/image-$id.jpg").readBytes()
        )
        assertEquals(1, application.fakeBackend.uploadedImages.size)
    }

    @Test
    fun captureDoesNotWaitForAccountSynchronizationLock() = runBlocking {
        val component = application.component
        component.accountRepository.save(
            component.accountRepository.get("camera-account")!!.copy(apiVersion = null)
        )
        application.fakeBackend.validationGate = CompletableDeferred()
        val refresh = launch { component.refresh("camera-account") }
        try {
            withTimeout(5_000) {
                while (application.fakeBackend.validatedAccountIds.isEmpty()) yield()
            }
            val uri = FileProvider.getUriForFile(
                application,
                "${application.packageName}.fileprovider",
                source
            )
            val note = withTimeout(5_000) {
                component.createCapturedNote("camera-account", UUID.randomUUID().toString(), uri)
            }
            assertTrue(note.content.contains("![image]"))
            assertTrue(application.fakeBackend.uploadedImages.isEmpty())
        } finally {
            refresh.cancel()
        }
    }

    @Test
    fun offlineCapturePersistsBeforeUploadAndRetriesWithoutCreatingEmptyNote() = runBlocking {
        val id = UUID.randomUUID().toString()
        val uri = FileProvider.getUriForFile(
            application,
            "${application.packageName}.fileprovider",
            source
        )
        application.fakeBackend.imageUploadFailure =
            BackendException.Retryable(IOException("offline"))
        val note = withTimeout(5_000) {
            application.component.createCapturedNote("camera-account", id, uri)
        }
        assertEquals(SyncState.LOCALLY_CREATED, note.syncState)
        assertTrue(note.content.contains("media/image-$id.jpg"))
        assertTrue(application.fakeBackend.uploadedImages.isEmpty())
        source.delete()
        assertEquals(note, application.component.createCapturedNote("camera-account", id, uri))

        application.component.refresh("camera-account")
        assertTrue(application.fakeBackend.pushedNotes.isEmpty())
        assertEquals(
            SyncState.LOCALLY_CREATED,
            application.component.noteRepository.get(id)!!.syncState
        )
        val pending = File(application.filesDir, "pending-captures/image-$id.jpg")
        assertArrayEquals(byteArrayOf(1, 2, 3), pending.readBytes())

        application.fakeBackend.imageUploadFailure = null
        application.component.refresh("camera-account")
        assertArrayEquals(
            byteArrayOf(1, 2, 3),
            application.fakeBackend.uploadedImages.single().second
        )
        assertEquals(note.content, application.fakeBackend.pushedNotes.single().content)
        assertEquals(
            SyncState.SYNCHRONIZED,
            application.component.noteRepository.get(id)!!.syncState
        )
    }
}
