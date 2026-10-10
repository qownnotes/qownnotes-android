package org.qownnotes.mobile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.qownnotes.mobile.core.DEFAULT_BOOKMARKS_PATH
import org.qownnotes.mobile.core.DeckStackTarget
import org.qownnotes.mobile.core.NoteCategoryScope
import org.qownnotes.mobile.core.NoteFolderScope
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

    /** Whether the note list and note-list widgets show a plain-text preview of each note. */
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

    private val mutableHideCreateButtonOnScroll =
        MutableStateFlow(preferences.getBoolean(HIDE_CREATE_BUTTON_ON_SCROLL, true))

    /** Whether scrolling down the note list hides its create-note button. */
    val hideCreateButtonOnScroll: StateFlow<Boolean> =
        mutableHideCreateButtonOnScroll.asStateFlow()

    fun setHideCreateButtonOnScroll(enabled: Boolean) {
        if (enabled == mutableHideCreateButtonOnScroll.value) return
        preferences.edit().putBoolean(HIDE_CREATE_BUTTON_ON_SCROLL, enabled).apply()
        mutableHideCreateButtonOnScroll.value = enabled
    }

    private val mutableAskForNewNoteName =
        MutableStateFlow(preferences.getBoolean(ASK_FOR_NEW_NOTE_NAME, false))

    /**
     * Whether the note list's create button first asks for the new note's name, offering the
     * name the note would otherwise have been given.
     */
    val askForNewNoteName: StateFlow<Boolean> = mutableAskForNewNoteName.asStateFlow()

    fun setAskForNewNoteName(enabled: Boolean) {
        if (enabled == mutableAskForNewNoteName.value) return
        preferences.edit().putBoolean(ASK_FOR_NEW_NOTE_NAME, enabled).apply()
        mutableAskForNewNoteName.value = enabled
    }

    private val mutableCompactNoteList =
        MutableStateFlow(preferences.getBoolean(COMPACT_NOTE_LIST, false))

    /** Whether the note list and note-list widgets use reduced row spacing. */
    val compactNoteList: StateFlow<Boolean> = mutableCompactNoteList.asStateFlow()

    fun setCompactNoteList(enabled: Boolean) {
        if (enabled == mutableCompactNoteList.value) return
        preferences.edit().putBoolean(COMPACT_NOTE_LIST, enabled).apply()
        mutableCompactNoteList.value = enabled
    }

    private val mutableAppearance = MutableStateFlow(readAppearance())

    /** Note-list colors and header; custom colors are fixed across light and dark themes. */
    val appearance: StateFlow<AppAppearance> = mutableAppearance.asStateFlow()

    fun setAppearance(appearance: AppAppearance) {
        if (appearance == mutableAppearance.value) return
        preferences.edit().apply {
            putColor(HEADER_COLOR, appearance.headerColor)
            putColor(NOTE_BACKGROUND_COLOR, appearance.noteBackground)
            putBoolean(HIGHLIGHT_CATEGORIES, appearance.highlightCategories)
            putColor(CATEGORY_HIGHLIGHT_COLOR, appearance.categoryHighlight)
            putBoolean(SHOW_LIST_HEADER, appearance.showListHeader)
            putBoolean(NOTE_CARDS, appearance.noteCards)
        }.apply()
        mutableAppearance.value = appearance
    }

    private fun readAppearance(): AppAppearance {
        fun color(key: String): Int? = if (preferences.contains(key)) {
            AppearanceColors.opaque(preferences.getInt(key, 0))
        } else {
            null
        }
        return AppAppearance(
            headerColor = color(HEADER_COLOR),
            noteBackground = color(NOTE_BACKGROUND_COLOR),
            highlightCategories = preferences.getBoolean(HIGHLIGHT_CATEGORIES, false),
            categoryHighlight = color(CATEGORY_HIGHLIGHT_COLOR),
            showListHeader = preferences.getBoolean(SHOW_LIST_HEADER, false),
            noteCards = preferences.getBoolean(NOTE_CARDS, true)
        )
    }

    private fun android.content.SharedPreferences.Editor.putColor(key: String, color: Int?) {
        if (color == null) remove(key) else putInt(key, AppearanceColors.opaque(color))
    }

    private val mutableShowCategories = mutableMapOf<String, MutableStateFlow<Boolean>>()
    private val mutableBookmarksPaths = mutableMapOf<String, MutableStateFlow<String>>()
    private val mutableUseSubfolders = mutableMapOf<String, MutableStateFlow<Boolean>>()

    /**
     * Whether the account's note folder uses subfolders, like the QOwnNotes desktop note-folder
     * setting. Without them the note list shows and creates only notes in the root folder; notes
     * in subfolders are still synchronized.
     */
    fun useSubfolders(accountId: String): StateFlow<Boolean> =
        mutableUseSubfolders.getOrPut(accountId) {
            MutableStateFlow(preferences.getBoolean("$USE_SUBFOLDERS_PREFIX$accountId", false))
        }.asStateFlow()

    fun setUseSubfolders(accountId: String, enabled: Boolean) {
        useSubfolders(accountId)
        val state = mutableUseSubfolders.getValue(accountId)
        if (enabled == state.value) return
        preferences.edit().putBoolean("$USE_SUBFOLDERS_PREFIX$accountId", enabled).apply()
        state.value = enabled
    }

    fun removeUseSubfolders(accountId: String) {
        preferences.edit().remove("$USE_SUBFOLDERS_PREFIX$accountId").apply()
        mutableUseSubfolders.remove(accountId)
    }

    /** Whether the note list and this account's note-list widgets show each note's category. */
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

    /**
     * The folder the note list shows. The path keeps the key of the earlier flat category selector,
     * whose stored choices therefore keep showing the same notes, and the subfolder choice is
     * stored beside it once the reader makes one.
     */
    fun noteFolderScope(accountId: String): NoteFolderScope {
        val subfoldersKey = "$NOTE_FOLDER_SUBFOLDERS_PREFIX$accountId"
        return NoteFolderScope.fromCategoryScope(
            NoteCategoryScopeCodec.decode(
                preferences.getString("$NOTE_CATEGORY_SCOPE_PREFIX$accountId", null),
                default = NoteCategoryScope.Undefined
            ),
            includeSubfolders = if (preferences.contains(subfoldersKey)) {
                preferences.getBoolean(subfoldersKey, false)
            } else {
                null
            }
        )
    }

    fun setNoteFolderScope(accountId: String, scope: NoteFolderScope) {
        preferences.edit()
            .putString(
                "$NOTE_CATEGORY_SCOPE_PREFIX$accountId",
                NoteCategoryScopeCodec.encode(
                    if (scope.isRoot) {
                        NoteCategoryScope.Undefined
                    } else {
                        NoteCategoryScope.Category(scope.path)
                    }
                )
            )
            .putBoolean("$NOTE_FOLDER_SUBFOLDERS_PREFIX$accountId", scope.includeSubfolders)
            .apply()
    }

    fun removeNoteFolderScope(accountId: String) {
        preferences.edit()
            .remove("$NOTE_CATEGORY_SCOPE_PREFIX$accountId")
            .remove("$NOTE_FOLDER_SUBFOLDERS_PREFIX$accountId")
            .apply()
    }

    /** Relative Markdown path of the bookmarks note for this account. */
    fun bookmarksPath(accountId: String): StateFlow<String> =
        mutableBookmarksPath(accountId).asStateFlow()

    fun setBookmarksPath(accountId: String, path: String) {
        val state = mutableBookmarksPath(accountId)
        if (path == state.value) return
        preferences.edit().putString("$BOOKMARKS_PATH_PREFIX$accountId", path).apply()
        state.value = path
    }

    fun removeBookmarksPath(accountId: String) {
        preferences.edit().remove("$BOOKMARKS_PATH_PREFIX$accountId").apply()
        mutableBookmarksPaths.remove(accountId)
    }

    private val mutableNextcloudDeckAvailable = mutableMapOf<String, MutableStateFlow<Boolean>>()

    /**
     * Whether the account's server was last found to support Nextcloud Deck. The note editor
     * offers Deck cards from this cached result, so it also works offline and right after start.
     */
    fun nextcloudDeckAvailable(accountId: String): StateFlow<Boolean> =
        mutableNextcloudDeckAvailable(accountId).asStateFlow()

    fun setNextcloudDeckAvailable(accountId: String, available: Boolean) {
        val state = mutableNextcloudDeckAvailable(accountId)
        if (available == state.value) return
        preferences.edit().putBoolean("$NEXTCLOUD_DECK_AVAILABLE_PREFIX$accountId", available)
            .apply()
        state.value = available
    }

    /** The Deck list that most recently received a card from this account, if any. */
    fun nextcloudDeckTarget(accountId: String): DeckStackTarget? {
        val boardId = preferences.getLong("$NEXTCLOUD_DECK_BOARD_PREFIX$accountId", 0)
        val stackId = preferences.getLong("$NEXTCLOUD_DECK_STACK_PREFIX$accountId", 0)
        return if (boardId > 0 && stackId > 0) DeckStackTarget(boardId, stackId) else null
    }

    fun setNextcloudDeckTarget(accountId: String, target: DeckStackTarget) {
        preferences.edit()
            .putLong("$NEXTCLOUD_DECK_BOARD_PREFIX$accountId", target.boardId)
            .putLong("$NEXTCLOUD_DECK_STACK_PREFIX$accountId", target.stackId)
            .apply()
    }

    fun removeNextcloudDeck(accountId: String) {
        preferences.edit()
            .remove("$NEXTCLOUD_DECK_AVAILABLE_PREFIX$accountId")
            .remove("$NEXTCLOUD_DECK_BOARD_PREFIX$accountId")
            .remove("$NEXTCLOUD_DECK_STACK_PREFIX$accountId")
            .remove("$NEXTCLOUD_DECK_OPEN_PREFIX$accountId")
            .apply()
        mutableNextcloudDeckAvailable.remove(accountId)?.value = false
    }

    fun deckLinkOpening(accountId: String): DeckLinkOpening? =
        preferences.getString("$NEXTCLOUD_DECK_OPEN_PREFIX$accountId", null)?.let { value ->
            DeckLinkOpening.entries.firstOrNull { it.name == value }
        }

    fun setDeckLinkOpening(accountId: String, opening: DeckLinkOpening) {
        preferences.edit().putString("$NEXTCLOUD_DECK_OPEN_PREFIX$accountId", opening.name).apply()
    }

    /** Clears opening choices for every account, without touching availability or target lists. */
    fun resetDeckLinkOpening() {
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(NEXTCLOUD_DECK_OPEN_PREFIX) }
            .forEach(editor::remove)
        editor.apply()
    }

    private fun mutableNextcloudDeckAvailable(accountId: String): MutableStateFlow<Boolean> =
        mutableNextcloudDeckAvailable.getOrPut(accountId) {
            MutableStateFlow(
                preferences.getBoolean("$NEXTCLOUD_DECK_AVAILABLE_PREFIX$accountId", false)
            )
        }

    private fun mutableShowCategory(accountId: String): MutableStateFlow<Boolean> =
        mutableShowCategories.getOrPut(accountId) {
            MutableStateFlow(
                preferences.getBoolean("$SHOW_CATEGORY_PREFIX$accountId", false)
            )
        }

    private fun mutableBookmarksPath(accountId: String): MutableStateFlow<String> =
        mutableBookmarksPaths.getOrPut(accountId) {
            MutableStateFlow(
                preferences.getString("$BOOKMARKS_PATH_PREFIX$accountId", DEFAULT_BOOKMARKS_PATH)
                    ?: DEFAULT_BOOKMARKS_PATH
            )
        }

    private companion object {
        const val PREFERENCES = "qownnotes-settings"
        const val NOTE_TEXT_SIZE_SP = "noteTextSizeSp"
        const val SHOW_NOTE_PREVIEW = "showNotePreview"
        const val SWIPE_NOTE_ACTIONS = "swipeNoteActions"
        const val HIDE_CREATE_BUTTON_ON_SCROLL = "hideCreateButtonOnScroll"
        const val ASK_FOR_NEW_NOTE_NAME = "askForNewNoteName"
        const val COMPACT_NOTE_LIST = "compactNoteList"
        const val HEADER_COLOR = "headerColor"
        const val NOTE_BACKGROUND_COLOR = "noteBackgroundColor"
        const val HIGHLIGHT_CATEGORIES = "highlightCategories"
        const val CATEGORY_HIGHLIGHT_COLOR = "categoryHighlightColor"
        const val SHOW_LIST_HEADER = "showListHeader"
        const val NOTE_CARDS = "noteCards"

        // Legacy global key migrated to existing accounts when the application starts.
        const val SHOW_CATEGORY = "showCategory"
        const val SHOW_CATEGORY_PREFIX = "showCategory."
        const val NOTE_CATEGORY_SCOPE_PREFIX = "noteCategoryScope."
        const val NOTE_FOLDER_SUBFOLDERS_PREFIX = "noteFolderSubfolders."
        const val USE_SUBFOLDERS_PREFIX = "useSubfolders."
        const val BOOKMARKS_PATH_PREFIX = "bookmarksPath."
        const val NEXTCLOUD_DECK_AVAILABLE_PREFIX = "nextcloudDeckAvailable."
        const val NEXTCLOUD_DECK_BOARD_PREFIX = "nextcloudDeckBoardId."
        const val NEXTCLOUD_DECK_STACK_PREFIX = "nextcloudDeckStackId."
        const val NEXTCLOUD_DECK_OPEN_PREFIX = "nextcloudDeckOpen."
    }
}

enum class DeckLinkOpening { QOWNNOTES, DECK }

/**
 * Note-list presentation choices. A `null` color follows the Material theme, including dark mode.
 */
data class AppAppearance(
    /** Top bar and optional list header. */
    val headerColor: Int? = null,
    /** Unselected note rows. */
    val noteBackground: Int? = null,
    /** Draw shown categories as tinted labels instead of plain text. */
    val highlightCategories: Boolean = false,
    val categoryHighlight: Int? = null,
    /** Two-line header below the search bar: listed category, then account name. */
    val showListHeader: Boolean = false,
    /** Draw each note as a separate rounded card with an outline border. */
    val noteCards: Boolean = true
)

/** Stable preference encoding of a category scope, shared by app and widget preferences. */
internal object NoteCategoryScopeCodec {
    private const val UNDEFINED_CATEGORY = "undefined"
    private const val ALL_CATEGORIES = "all"
    private const val CATEGORY_PREFIX = "category:"

    fun encode(scope: NoteCategoryScope): String = when (scope) {
        NoteCategoryScope.Undefined -> UNDEFINED_CATEGORY
        NoteCategoryScope.All -> ALL_CATEGORIES
        is NoteCategoryScope.Category -> "$CATEGORY_PREFIX${scope.value}"
    }

    fun decode(stored: String?, default: NoteCategoryScope): NoteCategoryScope = when {
        stored == null -> default
        stored == ALL_CATEGORIES -> NoteCategoryScope.All
        stored == UNDEFINED_CATEGORY -> NoteCategoryScope.Undefined
        else -> NoteCategoryScope.Category(stored.removePrefix(CATEGORY_PREFIX))
    }
}
