package org.qownnotes.mobile

import android.content.Context
import android.media.MediaPlayer
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Play, pause, and seek controls for a local audio file. Playback pauses when the screen stops and
 * the player is released when the controls leave the composition.
 */
@Composable
internal fun AudioPlayer(file: File, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playing by remember(file) { mutableStateOf(false) }
    var duration by remember(file) { mutableIntStateOf(0) }
    var position by remember(file) { mutableIntStateOf(0) }
    var seeking by remember(file) { mutableStateOf<Float?>(null) }
    val player = remember(file) {
        runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
        }.getOrNull()
    }
    DisposableEffect(player, lifecycleOwner) {
        if (player != null) {
            duration = player.duration.coerceAtLeast(0)
            player.setOnCompletionListener {
                playing = false
                position = duration
            }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && player != null && playing) {
                player.pause()
                playing = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            player?.release()
        }
    }
    LaunchedEffect(player, playing) {
        while (player != null && playing) {
            position = runCatching { player.currentPosition }.getOrDefault(position)
            delay(200)
        }
    }
    if (player == null) {
        Text(
            stringResource(R.string.audio_play_failed),
            color = MaterialTheme.colorScheme.error,
            modifier = modifier
        )
        return
    }
    Row(
        modifier = modifier.fillMaxWidth().testTag("audio-player"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalIconButton(
            onClick = {
                if (playing) {
                    player.pause()
                    playing = false
                } else {
                    if (position >= duration) {
                        player.seekTo(0)
                        position = 0
                    }
                    player.start()
                    playing = true
                }
            },
            modifier = Modifier.testTag("audio-play-pause")
        ) {
            Icon(
                if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.audio_pause else R.string.audio_play
                )
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Slider(
                value = seeking ?: position.toFloat(),
                valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                onValueChange = { seeking = it },
                onValueChangeFinished = {
                    seeking?.let {
                        player.seekTo(it.toInt())
                        position = it.toInt()
                    }
                    seeking = null
                },
                enabled = duration > 0,
                modifier = Modifier.testTag("audio-seek")
            )
            Text(
                "${formatAudioTime((seeking ?: position.toFloat()).toInt())} / " +
                    formatAudioTime(duration),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatAudioTime(milliseconds: Int): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)
}

/**
 * Opens an audio attachment link: asks whether to play it in QOwnNotes or another app unless a
 * choice was remembered, then plays it in a dialog or hands it to the external viewer.
 */
@Composable
internal fun AudioAttachmentDialog(
    component: ApplicationComponent,
    localId: String,
    path: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val remembered = remember(path) { component.settings.audioLinkOpening() }
    var internal by rememberSaveable(localId, path) {
        mutableStateOf(remembered == AudioLinkOpening.INTERNAL)
    }
    var rememberChoice by rememberSaveable(localId, path) { mutableStateOf(false) }
    LaunchedEffect(localId, path) {
        if (remembered == AudioLinkOpening.EXTERNAL) {
            showAttachmentResult(context, component.openAttachment(localId, path))
            onDismiss()
        }
    }
    if (internal) {
        AudioPlayerDialog(component, localId, path, onDismiss)
    } else if (remembered != AudioLinkOpening.EXTERNAL) {
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(Icons.Filled.Audiotrack, contentDescription = null) },
            title = { Text(stringResource(R.string.audio_open_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.audio_open_question))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .toggleable(
                                value = rememberChoice,
                                role = Role.Checkbox,
                                onValueChange = { rememberChoice = it }
                            )
                            .padding(vertical = 8.dp)
                            .testTag("audio-remember-opening")
                    ) {
                        Checkbox(checked = rememberChoice, onCheckedChange = null)
                        Text(stringResource(R.string.audio_remember_choice))
                    }
                    FilledTonalButton(
                        onClick = {
                            if (rememberChoice) {
                                component.settings.setAudioLinkOpening(AudioLinkOpening.INTERNAL)
                            }
                            internal = true
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .testTag("audio-open-internal")
                    ) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.audio_play_internal))
                    }
                    OutlinedButton(
                        onClick = {
                            if (rememberChoice) {
                                component.settings.setAudioLinkOpening(AudioLinkOpening.EXTERNAL)
                            }
                            scope.launch {
                                showAttachmentResult(
                                    context,
                                    component.openAttachment(localId, path)
                                )
                            }
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth().testTag("audio-open-external")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.audio_open_external))
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.ui_cancel)) }
            },
            modifier = Modifier.testTag("audio-opening-dialog")
        )
    }
}

@Composable
private fun AudioPlayerDialog(
    component: ApplicationComponent,
    localId: String,
    path: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember(localId, path) { mutableStateOf(true) }
    var file by remember(localId, path) { mutableStateOf<File?>(null) }
    LaunchedEffect(localId, path) {
        file = component.resolveAttachment(localId, path)
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Audiotrack, contentDescription = null) },
        title = { Text(path.substringAfterLast('/')) },
        text = {
            val current = file
            when {
                loading -> CircularProgressIndicator(modifier = Modifier.testTag("audio-loading"))
                current == null -> Text(
                    stringResource(R.string.attachment_download_failed),
                    color = MaterialTheme.colorScheme.error
                )
                else -> AudioPlayer(current)
            }
        },
        dismissButton = {
            TextButton(
                enabled = file != null,
                onClick = {
                    scope.launch {
                        showAttachmentResult(context, component.openAttachment(localId, path))
                    }
                    onDismiss()
                },
                modifier = Modifier.testTag("audio-player-open-external")
            ) { Text(stringResource(R.string.audio_open_external)) }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("audio-player-close")
            ) { Text(stringResource(R.string.action_close)) }
        },
        modifier = Modifier.testTag("audio-player-dialog")
    )
}

internal fun showAttachmentResult(context: Context, result: AttachmentOpenResult) {
    val message = when (result) {
        AttachmentOpenResult.OPENED -> return
        AttachmentOpenResult.FETCH_FAILED -> R.string.attachment_download_failed
        AttachmentOpenResult.NO_VIEWER -> R.string.attachment_no_viewer
    }
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
