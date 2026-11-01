package org.qownnotes.mobile

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.BackendException

@RunWith(AndroidJUnit4::class)
class ForegroundRefreshDeviceTest {
    private lateinit var application: TestQOwnNotesApplication
    private val component get() = application.component
    private val backend get() = application.fakeBackend

    @Before
    fun setUp() = runBlocking {
        application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as TestQOwnNotesApplication
        application.reset()
        component.accountRepository.save(
            Account("foreground-account", "Account", "https://cloud.example", "sso", "user", "1.4")
        )
    }

    @After
    fun tearDown() = runBlocking { application.reset() }

    @Test
    fun reopeningIsThrottledButManualRefreshBypassesCooldown() = runBlocking {
        component.refreshOnOpenIfDue(ID)
        component.refreshOnOpenIfDue(ID)
        assertEquals(1, backend.checkpoints.size)
        component.refresh(ID)
        assertEquals(2, backend.checkpoints.size)
        component.settings.recordRefreshAttempt(ID, System.currentTimeMillis() - 300_001)
        component.refreshOnOpenIfDue(ID)
        assertEquals(3, backend.checkpoints.size)
    }

    @Test
    fun disabledPreferencePersistsAndStopsOpeningRefresh() = runBlocking {
        assertTrue(component.settings.refreshOnOpen.value)
        component.settings.setRefreshOnOpen(false)
        assertFalse(AppSettings(application, "qownnotes-device-test-settings").refreshOnOpen.value)
        component.refreshOnOpenIfDue(ID)
        assertTrue(backend.checkpoints.isEmpty())
        component.refresh(ID)
        assertEquals(1, backend.checkpoints.size)
    }

    @Test
    fun failedAndBackgroundAttemptsAlsoCoolDown() = runBlocking {
        backend.enqueueFailure(ID, BackendException.Retryable(IOException("offline")))
        component.synchronizeFromWorker(ID)
        component.refreshOnOpenIfDue(ID)
        assertEquals(1, backend.checkpoints.size)
        assertEquals(
            component.settings.lastRefreshAttemptMillis(ID),
            AppSettings(application, "qownnotes-device-test-settings").lastRefreshAttemptMillis(ID)
        )
    }

    @Test
    fun openingWhileSyncIsRunningDoesNotQueueAnotherRefresh() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        backend.pullGate = gate
        val sync = async { component.refresh(ID) }
        try {
            withTimeout(5_000) { while (backend.checkpoints.isEmpty()) delay(10) }
            withTimeout(1_000) { component.refreshOnOpenIfDue(ID) }
        } finally {
            gate.complete(Unit)
            sync.await()
        }
        assertEquals(1, backend.checkpoints.size)
    }

    @Test
    fun returningToMainWindowRefreshesOnlyWhenStale() = runBlocking {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            withTimeout(10_000) { while (backend.checkpoints.isEmpty()) delay(20) }
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertEquals(1, backend.checkpoints.size)
            scenario.moveToState(Lifecycle.State.CREATED)
            component.settings.recordRefreshAttempt(ID, System.currentTimeMillis() - 300_001)
            scenario.moveToState(Lifecycle.State.RESUMED)
            withTimeout(10_000) { while (backend.checkpoints.size < 2) delay(20) }
            assertEquals(listOf(ID, ID), backend.checkpoints.map { it.first })
        }
    }

    private companion object {
        const val ID = "foreground-account"
    }
}
