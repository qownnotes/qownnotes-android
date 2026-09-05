package org.qownnotes.mobile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.qownnotes.mobile.markdown.NoteTextSize

/**
 * User presentation preferences.
 *
 * These are small, non-syncing, device-local values, so `SharedPreferences` is sufficient and
 * avoids pulling note presentation into the Room schema that models synchronized content.
 */
class AppSettings(context: Context, name: String = PREFERENCES) {
    private val preferences =
        context.applicationContext.getSharedPreferences(name, Context.MODE_PRIVATE)
    private val mutableNoteTextSizeSp =
        MutableStateFlow(
            NoteTextSize.coerce(
                preferences.getInt(NOTE_TEXT_SIZE_SP, NoteTextSize.DEFAULT_SP)
            )
        )

    /** Note body text size in scale-independent pixels, on top of the system font size. */
    val noteTextSizeSp: StateFlow<Int> = mutableNoteTextSizeSp.asStateFlow()

    fun increaseNoteTextSize() = setNoteTextSize(NoteTextSize.increase(mutableNoteTextSizeSp.value))

    fun decreaseNoteTextSize() = setNoteTextSize(NoteTextSize.decrease(mutableNoteTextSizeSp.value))

    fun resetNoteTextSize() = setNoteTextSize(NoteTextSize.DEFAULT_SP)

    private fun setNoteTextSize(sizeSp: Int) {
        val coerced = NoteTextSize.coerce(sizeSp)
        if (coerced == mutableNoteTextSizeSp.value) return
        preferences.edit().putInt(NOTE_TEXT_SIZE_SP, coerced).apply()
        mutableNoteTextSizeSp.value = coerced
    }

    private val mutableShowNotePreview =
        MutableStateFlow(preferences.getBoolean(SHOW_NOTE_PREVIEW, true))

    /** Whether the note list shows a plain-text preview of each note's content. */
    val showNotePreview: StateFlow<Boolean> = mutableShowNotePreview.asStateFlow()

    fun setShowNotePreview(enabled: Boolean) {
        if (enabled == mutableShowNotePreview.value) return
        preferences.edit().putBoolean(SHOW_NOTE_PREVIEW, enabled).apply()
        mutableShowNotePreview.value = enabled
    }

    private val mutableShowCategory =
        MutableStateFlow(preferences.getBoolean(SHOW_CATEGORY, false))

    /** Whether the note list shows each note's category. */
    val showCategory: StateFlow<Boolean> = mutableShowCategory.asStateFlow()

    fun setShowCategory(enabled: Boolean) {
        if (enabled == mutableShowCategory.value) return
        preferences.edit().putBoolean(SHOW_CATEGORY, enabled).apply()
        mutableShowCategory.value = enabled
    }

    private companion object {
        const val PREFERENCES = "qownnotes-settings"
        const val NOTE_TEXT_SIZE_SP = "noteTextSizeSp"
        const val SHOW_NOTE_PREVIEW = "showNotePreview"
        const val SHOW_CATEGORY = "showCategory"
    }
}
