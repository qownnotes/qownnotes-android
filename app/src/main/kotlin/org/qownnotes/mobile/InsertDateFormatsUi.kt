package org.qownnotes.mobile

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.ZonedDateTime
import kotlinx.coroutines.delay
import org.qownnotes.mobile.core.DatePatternPitfall
import org.qownnotes.mobile.core.InsertDateFormats
import org.qownnotes.mobile.core.InsertDateKind
import org.qownnotes.mobile.core.datePatternPitfalls
import org.qownnotes.mobile.core.formatDatePattern

/** The current moment, refreshed every second so previews with seconds stay truthful. */
@Composable
private fun rememberTickingNow(): ZonedDateTime {
    val now by produceState(ZonedDateTime.now()) {
        while (true) {
            delay(1_000 - System.currentTimeMillis() % 1_000)
            value = ZonedDateTime.now()
        }
    }
    return now
}

/** Settings entry showing what the two insert actions currently produce. */
@Composable
internal fun InsertDateFormatsSettingsRow(formats: InsertDateFormats, onClick: () -> Unit) {
    val now = rememberTickingNow()
    val locale = LocalConfiguration.current.locales[0]
    SettingsActionRow(
        icon = Icons.Filled.EditCalendar,
        label = stringResource(R.string.insert_date_formats),
        description = InsertDateKind.entries.joinToString("  ·  ") {
            formats.format(it, now, locale)
        },
        onClick = onClick,
        testTag = "open-insert-date-formats"
    )
}

/**
 * Chooses the patterns of the insert-date and insert-date-and-time actions from common formats or
 * a custom pattern, with a live preview. Custom patterns are stored only while they are usable.
 */
@Composable
internal fun InsertDateFormatsDialog(
    formats: InsertDateFormats,
    onChange: (InsertDateFormats) -> Unit,
    onDismiss: () -> Unit
) {
    val now = rememberTickingNow()
    val locale = LocalConfiguration.current.locales[0]
    var kind by rememberSaveable { mutableStateOf(InsertDateKind.DATE) }
    // Each kind keeps its own draft and choice, so switching between them loses nothing.
    val customPatterns = remember {
        mutableStateMapOf<InsertDateKind, TextFieldValue>().apply {
            InsertDateKind.entries.forEach { put(it, TextFieldValue(formats.pattern(it))) }
        }
    }
    val customChosen = remember {
        mutableStateMapOf<InsertDateKind, Boolean>().apply {
            InsertDateKind.entries.forEach { put(it, formats.pattern(it) !in it.presets) }
        }
    }
    val pattern = formats.pattern(kind)
    val custom = customChosen[kind] == true
    val draft = customPatterns.getValue(kind)
    val focusRequester = remember { FocusRequester() }
    var focusCustom by remember { mutableStateOf(false) }

    fun choose(selected: String) {
        customChosen[kind] = false
        onChange(formats.with(kind, selected))
    }

    fun editCustom(value: TextFieldValue) {
        customPatterns[kind] = value
        if (formatDatePattern(value.text, now, locale) != null) {
            onChange(formats.with(kind, value.text))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.EditCalendar, contentDescription = null) },
        title = { Text(stringResource(R.string.insert_date_formats)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 560.dp)
            ) {
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth().testTag("insert-date-kind")
                ) {
                    InsertDateKind.entries.forEachIndexed { index, entry ->
                        SegmentedButton(
                            selected = entry == kind,
                            onClick = { kind = entry },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = InsertDateKind.entries.size
                            ),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = entry == kind) {
                                    Icon(
                                        entry.icon,
                                        contentDescription = null,
                                        modifier = Modifier.size(SegmentedButtonDefaults.IconSize)
                                    )
                                }
                            },
                            label = {
                                Text(
                                    stringResource(entry.label),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            modifier = Modifier.testTag("insert-date-kind-${entry.tag}")
                        )
                    }
                }
                InsertDatePreviewCard(
                    kind = kind,
                    preview = formats.format(kind, now, locale),
                    pattern = pattern
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .selectableGroup()
                        .testTag("insert-date-formats-list")
                ) {
                    Text(
                        stringResource(R.string.insert_date_common_formats),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                    kind.presets.forEach { preset ->
                        FormatChoiceRow(
                            selected = !custom && preset == pattern,
                            title = formatDatePattern(preset, now, locale).orEmpty(),
                            pattern = preset,
                            isDefault = preset == kind.defaultPattern,
                            onClick = { choose(preset) },
                            testTag = "insert-date-preset-$preset"
                        )
                    }
                    FormatChoiceRow(
                        selected = custom,
                        title = stringResource(R.string.insert_date_custom),
                        pattern = null,
                        isDefault = false,
                        onClick = {
                            customChosen[kind] = true
                            focusCustom = true
                            editCustom(draft)
                        },
                        testTag = "insert-date-custom"
                    )
                    AnimatedVisibility(
                        visible = custom,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        CustomPatternEditor(
                            value = draft,
                            preview = formatDatePattern(draft.text, now, locale),
                            onValueChange = ::editCustom,
                            focusRequester = focusRequester
                        )
                    }
                }
            }
            LaunchedEffect(focusCustom, custom) {
                if (focusCustom && custom) {
                    // Wait for the field to enter the composition before focusing it.
                    delay(50)
                    runCatching { focusRequester.requestFocus() }
                    focusCustom = false
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    InsertDateKind.entries.forEach {
                        customChosen[it] = false
                        customPatterns[it] = TextFieldValue(it.defaultPattern)
                    }
                    onChange(InsertDateFormats())
                },
                modifier = Modifier.testTag("reset-insert-date-formats")
            ) { IconLabel(Icons.Filled.RestartAlt, stringResource(R.string.action_reset)) }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close-insert-date-formats")
            ) { Text(stringResource(R.string.action_close)) }
        },
        modifier = Modifier.testTag("insert-date-formats-dialog")
    )
}

/** What the toolbar action inserts right now, with the pattern that produces it. */
@Composable
private fun InsertDatePreviewCard(kind: InsertDateKind, preview: String, pattern: String) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            ) {
                Icon(kind.icon, contentDescription = null, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    stringResource(R.string.insert_date_preview_title),
                    style = MaterialTheme.typography.labelMedium
                )
                AnimatedContent(
                    targetState = preview,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "insert-date-preview"
                ) { text ->
                    Text(
                        text,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.testTag("insert-date-preview")
                    )
                }
                Text(
                    pattern,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.testTag("insert-date-preview-pattern")
                )
            }
        }
    }
}

@Composable
private fun FormatChoiceRow(
    selected: Boolean,
    title: String,
    pattern: String?,
    isDefault: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp)
            .testTag(testTag)
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            pattern?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (isDefault) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Text(
                    stringResource(R.string.insert_date_default),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomPatternEditor(
    value: TextFieldValue,
    preview: String?,
    onValueChange: (TextFieldValue) -> Unit,
    focusRequester: FocusRequester
) {
    val pitfalls = remember(value.text) { datePatternPitfalls(value.text) }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 8.dp)
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(R.string.insert_date_custom_label)) },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace),
            isError = preview == null,
            supportingText = {
                Text(
                    if (preview == null) {
                        stringResource(R.string.insert_date_custom_invalid)
                    } else {
                        stringResource(R.string.insert_date_custom_preview, preview)
                    },
                    modifier = Modifier.testTag("insert-date-custom-supporting")
                )
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                .testTag("insert-date-custom-pattern")
        )
        pitfalls.forEach { pitfall ->
            DialogNotice(
                stringResource(pitfall.message),
                modifier = Modifier.testTag("insert-date-pitfall-${pitfall.name.lowercase()}")
            )
        }
        Text(
            stringResource(R.string.insert_date_tokens_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PATTERN_TOKENS.forEach { (token, label) ->
                SuggestionChip(
                    onClick = { onValueChange(value.inserting(token)) },
                    label = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                token,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(stringResource(label))
                        }
                    },
                    modifier = Modifier.testTag("insert-date-token-$token")
                )
            }
        }
        Text(
            stringResource(R.string.insert_date_tokens_quote_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Replaces the selection with [token] and places the cursor after it. */
private fun TextFieldValue.inserting(token: String): TextFieldValue {
    val start = minOf(selection.start, selection.end).coerceIn(0, text.length)
    val end = maxOf(selection.start, selection.end).coerceIn(0, text.length)
    return TextFieldValue(
        text = text.replaceRange(start, end, token),
        selection = TextRange(start + token.length)
    )
}

private val PATTERN_TOKENS = listOf(
    "yyyy" to R.string.insert_date_token_year,
    "yy" to R.string.insert_date_token_short_year,
    "MM" to R.string.insert_date_token_month,
    "MMMM" to R.string.insert_date_token_month_name,
    "dd" to R.string.insert_date_token_day,
    "EEEE" to R.string.insert_date_token_weekday,
    "HH" to R.string.insert_date_token_hour,
    "mm" to R.string.insert_date_token_minute,
    "ss" to R.string.insert_date_token_second,
    "a" to R.string.insert_date_token_am_pm
)

private val InsertDateKind.icon: ImageVector
    get() = when (this) {
        InsertDateKind.DATE -> Icons.Filled.Today
        InsertDateKind.DATE_TIME -> Icons.Filled.MoreTime
    }

@get:StringRes
private val InsertDateKind.label: Int
    get() = when (this) {
        InsertDateKind.DATE -> R.string.insert_date_kind_date
        InsertDateKind.DATE_TIME -> R.string.insert_date_kind_date_time
    }

private val InsertDateKind.tag: String
    get() = when (this) {
        InsertDateKind.DATE -> "date"
        InsertDateKind.DATE_TIME -> "datetime"
    }

@get:StringRes
private val DatePatternPitfall.message: Int
    get() = when (this) {
        DatePatternPitfall.WEEK_BASED_YEAR -> R.string.insert_date_pitfall_week_year
        DatePatternPitfall.DAY_OF_YEAR -> R.string.insert_date_pitfall_day_of_year
    }
