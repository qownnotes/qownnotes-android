package org.qownnotes.mobile

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import java.io.File
import kotlinx.coroutines.launch

/** Captures a photo, uploads it through the existing note-media path, and opens its new note. */
class CaptureNoteActivity : ComponentActivity() {
    private var imageUri: Uri? = null
    private val takePicture =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
            val uri = imageUri
            if (!saved || uri == null) {
                capturedFile()?.delete()
                finish()
                return@registerForActivityResult
            }
            val accountId = intent.getStringExtra(WidgetIntents.EXTRA_ACCOUNT_ID)
            if (accountId == null) {
                finish()
                return@registerForActivityResult
            }
            lifecycleScope.launch {
                val component = application().component
                val result = runCatching {
                    val note = component.createNote(accountId)
                    val image = component.importImage(note.localId, uri)
                    check(
                        component.replaceNoteContent(
                            note.localId,
                            "${note.content}![${image.description}](${image.markdownPath})\n"
                        )
                    ) { "The captured image could not be added to the note" }
                    note.localId
                }
                capturedFile()?.delete()
                result.onSuccess { localId ->
                    NoteWidgetUpdater.updateAll(this@CaptureNoteActivity)
                    component.receiveWidgetRequest(WidgetRequest.OpenNote(localId))
                    startActivity(
                        Intent(this@CaptureNoteActivity, MainActivity::class.java)
                            .setAction(Intent.ACTION_MAIN)
                    )
                }.onFailure { error ->
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
        if (savedInstanceState != null) return
        val file = File(cacheDir, "camera/widget-${System.currentTimeMillis()}.jpg")
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
        super.onSaveInstanceState(outState)
    }

    private fun capturedFile(): File? = imageUri?.lastPathSegment
        ?.substringAfter("camera/")
        ?.let { File(cacheDir, "camera/$it") }

    private fun application() = applicationContext as QOwnNotesApplication

    companion object {
        private const val STATE_IMAGE_URI = "imageUri"

        fun intent(context: Context, accountId: String, widgetId: Int): Intent =
            Intent(context, CaptureNoteActivity::class.java)
                .putExtra(WidgetIntents.EXTRA_ACCOUNT_ID, accountId)
                .setData("qownnotes://widget/camera/$widgetId".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
