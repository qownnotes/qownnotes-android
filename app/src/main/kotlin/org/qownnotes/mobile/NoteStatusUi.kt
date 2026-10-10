package org.qownnotes.mobile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

internal enum class NoteStatusTone { ERROR, INFO }

/**
 * A note's state, such as a sync error, a conflict, or read-only access, on a tinted panel with
 * an icon and optional actions below the message.
 */
@Composable
internal fun NoteStatusPanel(
    icon: ImageVector,
    message: String,
    tone: NoteStatusTone,
    modifier: Modifier = Modifier,
    messageTestTag: String? = null,
    detail: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val (container, content) = when (tone) {
        NoteStatusTone.ERROR ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        NoteStatusTone.INFO ->
            MaterialTheme.colorScheme.surfaceContainerHighest to
                MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        color = container,
        contentColor = content,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(
                start = 12.dp,
                end = 12.dp,
                top = 10.dp,
                bottom = if (actions == null) 10.dp else 0.dp
            )
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.padding(top = 2.dp).size(18.dp)
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = if (messageTestTag == null) {
                            Modifier
                        } else {
                            Modifier.testTag(messageTestTag)
                        }
                    )
                    detail?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                    content = actions
                )
            }
        }
    }
}
