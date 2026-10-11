package org.qownnotes.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Saves a photo note locally and opens it without waiting for Nextcloud. */
class CaptureNoteActivity : ComponentActivity() {
    private var imageUri: Uri? = null
    private var localId = UUID.randomUUID().toString()
    private var processing by mutableStateOf(false)
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = imageUri
            if (!saved || uri == null) {
                capturedFile()?.delete()
                finish()
                return@registerForActivityResult
            }
            processing = true
            // The last saved instance state may still describe the camera being open if
            // Android kills us after the result callback but before another state save.
            runCatching { captureAcceptedFile().createNewFile() }
            savePhoto(uri)
        }

    private fun savePhoto(uri: Uri) {
        val accountId = intent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID)
        if (accountId == null) {
            finish()
            return
        }
        lifecycleScope.launch {
            val component = application().component
            val result = runCatching {
                component.createCapturedNote(accountId, localId, uri).localId
            }
            result.onSuccess { localId ->
                capturedFile()?.delete()
                captureAcceptedFile().delete()
                NoteWidgetUpdater.updateAll(this@CaptureNoteActivity)
                component.receiveWidgetRequest(WidgetRequest.OpenNote(localId))
                startActivity(
                    Intent(this@CaptureNoteActivity, MainActivity::class.java)
                        .setAction(Intent.ACTION_MAIN)
                )
            }.onFailure { error ->
                if (error is CancellationException) throw error
                Toast.makeText(
                    this@CaptureNoteActivity,
                    error.message ?: getString(R.string.widget_picture_failed),
                    Toast.LENGTH_LONG
                ).show()
                startActivity(Intent(this@CaptureNoteActivity, MainActivity::class.java))
            }
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        imageUri = savedInstanceState?.getString(STATE_IMAGE_URI)?.let(Uri::parse)
        localId = savedInstanceState?.getString(STATE_LOCAL_ID) ?: localId
        processing = savedInstanceState?.getBoolean(STATE_PROCESSING) == true ||
            captureAcceptedFile().exists()
        setContent {
            QOwnNotesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        if (processing) Text(stringResource(R.string.action_saving))
                    }
                }
            }
        }
        if (savedInstanceState != null) {
            if (processing) imageUri?.let(::savePhoto)
            return
        }
        val file = File(filesDir, "camera/widget-$localId.jpg")
        file.parentFile?.mkdirs()
        imageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        runCatching { takePicture.launch(requireNotNull(imageUri)) }
            .onFailure {
                file.delete()
                Toast.makeText(this, R.string.widget_camera_unavailable, Toast.LENGTH_LONG).show()
                finish()
            }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_IMAGE_URI, imageUri?.toString())
        outState.putString(STATE_LOCAL_ID, localId)
        outState.putBoolean(STATE_PROCESSING, processing)
        super.onSaveInstanceState(outState)
    }

    private fun capturedFile(): File? = imageUri?.lastPathSegment
        ?.substringAfter("camera/")
        ?.let { File(filesDir, "camera/$it") }

    private fun captureAcceptedFile() = File(filesDir, "camera/widget-$localId.accepted")

    private fun application() = applicationContext as QOwnNotesApplication

    companion object {
        private const val STATE_IMAGE_URI = "imageUri"
        private const val STATE_LOCAL_ID = "localId"
        private const val STATE_PROCESSING = "processing"

        fun intent(context: Context, accountId: String, widgetId: Int): Intent =
            Intent(context, CaptureNoteActivity::class.java)
                .putExtra(WidgetIntents.EXTRA_ACCOUNT_ID, accountId)
                .setData("qownnotes://widget/camera/$widgetId".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
