package org.qownnotes.mobile

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Leading checkbox of a note list row in selection mode. It slides in while notes are being
 * selected and toggles the row like a tap on the row itself.
 *
 * The checkbox is purely visual for accessibility services: the row already exposes its selected
 * state and a select or deselect click action, so a second focusable control would only repeat it.
 */
@Composable
internal fun RowScope.NoteSelectionCheckbox(
    visible: Boolean,
    selected: Boolean,
    uncheckedColor: Color,
    onClick: () -> Unit,
    testTag: String
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandHorizontally(expandFrom = Alignment.Start) +
            fadeIn() +
            scaleIn(initialScale = 0.6f),
        exit = shrinkHorizontally(shrinkTowards = Alignment.Start) +
            fadeOut() +
            scaleOut(targetScale = 0.6f)
    ) {
        Box(
            modifier = Modifier.padding(end = 4.dp)
                .size(48.dp)
                .testTag(testTag)
                .clearAndSetSemantics {}
                .clip(CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            NoteSelectionMark(selected = selected, uncheckedColor = uncheckedColor)
        }
    }
}

@Composable
private fun NoteSelectionMark(selected: Boolean, uncheckedColor: Color) {
    val checkedColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(7.dp)
    val container by animateColorAsState(
        targetValue = if (selected) checkedColor else Color.Transparent,
        label = "note-selection-container"
    )
    val outline by animateColorAsState(
        targetValue = if (selected) checkedColor else uncheckedColor,
        label = "note-selection-outline"
    )
    Box(
        modifier = Modifier.size(22.dp)
            .clip(shape)
            .background(container)
            .border(2.dp, outline, shape),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
