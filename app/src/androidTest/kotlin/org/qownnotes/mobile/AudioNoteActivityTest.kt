package org.qownnotes.mobile

import android.Manifest
import android.app.Activity
import android.os.SystemClock
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account

@RunWith(AndroidJUnit4::class)
class AudioNoteActivityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var application: TestQOwnNotesApplication

    @Before
    fun setUp() = runBlocking {
        application = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as TestQOwnNotesApplication
        application.reset()
        InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
            application.packageName,
            Manifest.permission.RECORD_AUDIO
        )
        application.fakeSyncScheduler.pause()
        application.component.accountRepository.save(
            Account("audio-account", "Audio", "https://example.com", "user", "user", "1.3")
        )
    }

    @After
    fun tearDown() = runBlocking {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .filterIsInstance<MainActivity>().forEach(Activity::finish)
        }
        application.reset()
    }

    @Test
    fun backgroundStopsRecordingAndRecreationPreservesAudioForOfflineSave() {
        ActivityScenario.launch<AudioNoteActivity>(
            AudioNoteActivity.intent(application, "audio-account", category = "Meetings")
        ).use { scenario ->
            compose.onNodeWithTag("audio-record").performClick()
            // Allow the real recorder to produce AAC frames before stopping it.
            SystemClock.sleep(1_500)
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.onNodeWithTag("audio-save").assertIsEnabled()
            scenario.recreate()
            compose.onNodeWithTag("audio-save").assertIsEnabled().performClick()
            val note = runBlocking {
                withTimeout(5_000) {
                    val item = application.component.noteRepository.observeNotes("audio-account")
                        .first { it.isNotEmpty() }.single()
                    application.component.noteRepository.get(item.localId)!!
                }
            }
            assertTrue(note.content.contains("(../attachments/audio-${note.localId}.m4a)"))
            val media = File(application.filesDir, "pending-captures/audio-${note.localId}.m4a")
            assertTrue(media.length() > 0)
            assertTrue(media.readBytes().copyOfRange(4, 8).decodeToString() == "ftyp")
            assertTrue(application.fakeBackend.uploadedImages.isEmpty())
            assertTrue(application.fakeBackend.pushedNotes.isEmpty())
        }
    }
}
