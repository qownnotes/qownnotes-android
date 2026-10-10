package org.qownnotes.mobile

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.qownnotes.mobile.markdown.NoteTextSize

/**
 * Note text size as one menu row with smaller and larger buttons. The menu stays open, so the
 * reader can step through sizes while watching the note change behind it.
 */
@Composable
internal fun TextSizeMenuRow(sizeSp: Int, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.widthIn(min = 220.dp).padding(start = 12.dp, end = 4.dp)
            .testTag("note-text-size-row")
    ) {
        Icon(
            Icons.Filled.FormatSize,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(12.dp))
        Text(
            stringResource(R.string.text_size),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f)
        )
        TooltipIconButton(
            icon = Icons.Filled.TextDecrease,
            description = stringResource(R.string.text_size_decrease),
            onClick = onDecrease,
            enabled = NoteTextSize.canDecrease(sizeSp),
            testTag = "decrease-note-text-size"
        )
        TooltipIconButton(
            icon = Icons.Filled.TextIncrease,
            description = stringResource(R.string.text_size_increase),
            onClick = onIncrease,
            enabled = NoteTextSize.canIncrease(sizeSp),
            testTag = "increase-note-text-size"
        )
    }
}
