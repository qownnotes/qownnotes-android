package org.qownnotes.mobile

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag

/**
 * Shows [description] in a tooltip when [content] is long-pressed. The popup is not focusable, so
 * it cannot take focus from the note editor and close the keyboard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun WithTooltip(
    description: String,
    above: Boolean = false,
    content: @Composable () -> Unit
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            if (above) TooltipAnchorPosition.Above else TooltipAnchorPosition.Below
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = rememberTooltipState(),
        focusable = false,
        content = content
    )
}

/** An icon button whose name appears when it is long-pressed. */
@Composable
internal fun TooltipIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTag: String? = null,
    tooltipAbove: Boolean = false
) {
    WithTooltip(description, above = tooltipAbove) {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = if (testTag == null) modifier else modifier.testTag(testTag)
        ) {
            Icon(icon, contentDescription = description)
        }
    }
}
