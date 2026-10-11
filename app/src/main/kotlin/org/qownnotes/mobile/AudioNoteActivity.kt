package org.qownnotes.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Foreground-only AAC recording; accepted bytes are persisted before any network work. */
class AudioNoteActivity : ComponentActivity() {
    private var localId = UUID.randomUUID().toString()
    private var recorder: MediaRecorder? = null
    private var recording by mutableStateOf(false)
    private var ready by mutableStateOf(false)
    private var saving by mutableStateOf(false)
    private val permission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            if (it) startRecording() else showError(R.string.audio_permission_required)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        localId = savedInstanceState?.getString("localId") ?: localId
        if (intent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID) == null) {
            finish()
            return
        }
        ready = recordingFile().length() > 0
        saving = acceptedFile().exists()
        setContent {
            QOwnNotesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(
                                if (recording) {
                                    R.string.audio_recording_active
                                } else {
                                    R.string.audio_note
                                }
                            )
                        )
                        Button(
                            enabled = !saving && !ready,
                            onClick = {
                                if (recording) stopRecording() else requestRecording()
                            },
                            modifier = Modifier.testTag("audio-record")
                        ) {
                            Text(
                                stringResource(
                                    if (recording) R.string.audio_stop else R.string.audio_record
                                )
                            )
                        }
                        if (ready) {
                            AudioPlayer(
                                recordingFile(),
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                            )
                        }
                        Button(
                            enabled = ready && !saving,
                            onClick = { saveRecording() },
                            modifier = Modifier.testTag("audio-save")
                        ) {
                            Text(
                                stringResource(
                                    if (saving) R.string.action_saving else R.string.audio_save
                                )
                            )
                        }
                        Button(enabled = !saving, onClick = {
                            stopRecording()
                            recordingFile().delete()
                            finish()
                        }) { Text(stringResource(android.R.string.cancel)) }
                    }
                }
            }
        }
        if (saving) saveRecording()
    }

    private fun requestRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            permission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    @Suppress("DEPRECATION")
    private fun startRecording() {
        val file = recordingFile()
        file.parentFile?.mkdirs()
        val next = MediaRecorder()
        recorder = next
        try {
            next.setAudioSource(MediaRecorder.AudioSource.MIC)
            next.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            next.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            next.setAudioEncodingBitRate(96000)
            next.setAudioSamplingRate(44100)
            next.setOutputFile(file.absolutePath)
            // Bound memory used by the existing media upload path.
            next.setMaxFileSize(10L * 1024 * 1024)
            next.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) stopRecording()
            }
            next.setOnErrorListener { _, _, _ ->
                stopRecording()
                showError(R.string.audio_record_failed)
            }
            next.prepare()
            next.start()
            recording = true
        } catch (_: Exception) {
            next.release()
            recorder = null
            file.delete()
            showError(R.string.audio_record_failed)
        }
    }

    private fun stopRecording() {
        val current = recorder ?: return
        recorder = null
        val stopped = runCatching { current.stop() }.isSuccess
        current.release()
        recording = false
        ready = stopped && recordingFile().length() > 0
        if (!ready) {
            recordingFile().delete()
            showError(R.string.audio_record_failed)
        }
    }

    private fun saveRecording() {
        saving = true
        lifecycleScope.launch {
            try {
                check(acceptedFile().exists() || acceptedFile().createNewFile())
                val component = (application as QOwnNotesApplication).component
                val note = component.createCapturedNote(
                    requireNotNull(intent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID)),
                    localId,
                    Uri.fromFile(recordingFile()),
                    audio = true,
                    category = intent.getStringExtra(EXTRA_CATEGORY).orEmpty()
                )
                recordingFile().delete()
                acceptedFile().delete()
                NoteWidgetUpdater.updateAll(this@AudioNoteActivity)
                component.receiveWidgetRequest(WidgetRequest.OpenNote(note.localId))
                startActivity(Intent(this@AudioNoteActivity, MainActivity::class.java))
                finish()
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                saving = false
                showError(R.string.audio_save_failed)
            }
        }
    }

    override fun onStop() {
        // Never keep the microphone active when the recording screen is no longer visible.
        stopRecording()
        super.onStop()
    }

    override fun onDestroy() {
        stopRecording()
        if (isFinishing && !acceptedFile().exists()) recordingFile().delete()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("localId", localId)
        super.onSaveInstanceState(outState)
    }

    private fun recordingFile() = File(filesDir, "audio/$localId.m4a")
    private fun acceptedFile() = File(filesDir, "audio/$localId.accepted")
    private fun showError(message: Int) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    companion object {
        private const val EXTRA_CATEGORY = "category"

        fun intent(
            context: Context,
            accountId: String,
            widgetId: Int = 0,
            category: String = ""
        ): Intent = Intent(context, AudioNoteActivity::class.java)
            .putExtra(WidgetIntents.EXTRA_ACCOUNT_ID, accountId)
            .putExtra(EXTRA_CATEGORY, category)
            .setData("qownnotes://widget/audio/$widgetId".toUri())
    }
}
