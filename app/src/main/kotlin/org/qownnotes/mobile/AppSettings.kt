package org.qownnotes.mobile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.qownnotes.mobile.core.NoteCategoryScope
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

    private val mutableSwipeNoteActions =
        MutableStateFlow(preferences.getBoolean(SWIPE_NOTE_ACTIONS, false))

    /** Whether horizontal note-list swipes toggle favorites and move notes to trash. */
    val swipeNoteActions: StateFlow<Boolean> = mutableSwipeNoteActions.asStateFlow()

    fun setSwipeNoteActions(enabled: Boolean) {
        if (enabled == mutableSwipeNoteActions.value) return
        preferences.edit().putBoolean(SWIPE_NOTE_ACTIONS, enabled).apply()
        mutableSwipeNoteActions.value = enabled
    }

    private val mutableShowCategories = mutableMapOf<String, MutableStateFlow<Boolean>>()

    /** Whether the note list shows each note's category for this account. */
    fun showCategory(accountId: String): StateFlow<Boolean> =
        mutableShowCategory(accountId).asStateFlow()

    fun setShowCategory(accountId: String, enabled: Boolean) {
        val state = mutableShowCategory(accountId)
        if (enabled == state.value) return
        preferences.edit().putBoolean("$SHOW_CATEGORY_PREFIX$accountId", enabled).apply()
        state.value = enabled
    }

    fun removeShowCategory(accountId: String) {
        preferences.edit().remove("$SHOW_CATEGORY_PREFIX$accountId").apply()
        mutableShowCategories.remove(accountId)
    }

    fun migrateShowCategory(accountIds: List<String>) {
        if (!preferences.contains(SHOW_CATEGORY)) return
        val enabled = preferences.getBoolean(SHOW_CATEGORY, false)
        val editor = preferences.edit().remove(SHOW_CATEGORY)
        accountIds.forEach { accountId ->
            val key = "$SHOW_CATEGORY_PREFIX$accountId"
            if (!preferences.contains(key)) {
                editor.putBoolean(key, enabled)
                mutableShowCategories[accountId]?.value = enabled
            }
        }
        editor.apply()
    }

    fun noteCategoryScope(accountId: String): NoteCategoryScope =
        when (val stored = preferences.getString("$NOTE_CATEGORY_SCOPE_PREFIX$accountId", null)) {
            ALL_CATEGORIES -> NoteCategoryScope.All
            null, UNDEFINED_CATEGORY -> NoteCategoryScope.Undefined
            else -> NoteCategoryScope.Category(stored.removePrefix(CATEGORY_PREFIX))
        }

    fun setNoteCategoryScope(accountId: String, scope: NoteCategoryScope) {
        val stored = when (scope) {
            NoteCategoryScope.Undefined -> UNDEFINED_CATEGORY
            NoteCategoryScope.All -> ALL_CATEGORIES
            is NoteCategoryScope.Category -> "$CATEGORY_PREFIX${scope.value}"
        }
        preferences.edit().putString("$NOTE_CATEGORY_SCOPE_PREFIX$accountId", stored).apply()
    }

    fun removeNoteCategoryScope(accountId: String) {
        preferences.edit().remove("$NOTE_CATEGORY_SCOPE_PREFIX$accountId").apply()
    }

    private fun mutableShowCategory(accountId: String): MutableStateFlow<Boolean> =
        mutableShowCategories.getOrPut(accountId) {
            MutableStateFlow(
                preferences.getBoolean("$SHOW_CATEGORY_PREFIX$accountId", false)
            )
        }

    private companion object {
        const val PREFERENCES = "qownnotes-settings"
        const val NOTE_TEXT_SIZE_SP = "noteTextSizeSp"
        const val SHOW_NOTE_PREVIEW = "showNotePreview"
        const val SWIPE_NOTE_ACTIONS = "swipeNoteActions"

        // Legacy global key migrated to existing accounts when the application starts.
        const val SHOW_CATEGORY = "showCategory"
        const val SHOW_CATEGORY_PREFIX = "showCategory."
        const val NOTE_CATEGORY_SCOPE_PREFIX = "noteCategoryScope."
        const val UNDEFINED_CATEGORY = "undefined"
        const val ALL_CATEGORIES = "all"
        const val CATEGORY_PREFIX = "category:"
    }
}
