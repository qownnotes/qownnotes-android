package org.qownnotes.mobile

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.NoteCategories
import org.qownnotes.mobile.core.NoteCategoryScope
import org.qownnotes.mobile.core.NoteExcerpt
import org.qownnotes.mobile.core.NoteListItem

sealed interface WidgetRequest {
    data class OpenNoteList(val accountId: String) : WidgetRequest

    data class OpenNote(val localId: String) : WidgetRequest

    data class CreateNote(val accountId: String) : WidgetRequest
}

internal object WidgetIntents {
    const val ACTION_OPEN_NOTE_LIST = "org.qownnotes.mobile.widget.OPEN_NOTE_LIST"
    const val ACTION_OPEN_NOTE = "org.qownnotes.mobile.widget.OPEN_NOTE"
    const val ACTION_CREATE_NOTE = "org.qownnotes.mobile.widget.CREATE_NOTE"
    const val EXTRA_ACCOUNT_ID = "accountId"
    const val EXTRA_NOTE_ID = "noteId"

    fun request(intent: Intent?): WidgetRequest? = when (intent?.action) {
        ACTION_OPEN_NOTE_LIST -> intent.getStringExtra(EXTRA_ACCOUNT_ID)
            ?.takeIf(String::isNotBlank)?.let(WidgetRequest::OpenNoteList)
        ACTION_OPEN_NOTE -> noteId(intent)?.let(WidgetRequest::OpenNote)
        ACTION_CREATE_NOTE -> intent.getStringExtra(
            EXTRA_ACCOUNT_ID
        )?.let(WidgetRequest::CreateNote)
        else -> null
    }

    private fun noteId(intent: Intent): String? =
        intent.getStringExtra(EXTRA_NOTE_ID)?.takeIf(String::isNotBlank)
            ?: intent.data?.takeIf { uri ->
                uri.scheme == "qownnotes" &&
                    uri.host == "widget" &&
                    uri.pathSegments.size == 2 &&
                    uri.pathSegments.first() == "note"
            }?.lastPathSegment?.takeIf(String::isNotBlank)

    fun openNote(context: Context, localId: String): Intent =
        Intent(context, WidgetActionActivity::class.java)
            .setAction(ACTION_OPEN_NOTE)
            .putExtra(EXTRA_NOTE_ID, localId)
            .setData("qownnotes://widget/note/$localId".toUri())

    fun openNoteList(context: Context, accountId: String): Intent =
        Intent(context, WidgetActionActivity::class.java)
            .setAction(ACTION_OPEN_NOTE_LIST)
            .putExtra(EXTRA_ACCOUNT_ID, accountId)
            .setData("qownnotes://widget/account/${Uri.encode(accountId)}/list".toUri())

    fun openNoteTemplate(context: Context): Intent =
        Intent(context, WidgetActionActivity::class.java).setAction(ACTION_OPEN_NOTE)

    fun openNoteFillIn(localId: String): Intent = Intent()
        .putExtra(EXTRA_NOTE_ID, localId)
        .setData("qownnotes://widget/note/$localId".toUri())

    fun createNote(context: Context, accountId: String): Intent =
        Intent(context, WidgetActionActivity::class.java)
            .setAction(ACTION_CREATE_NOTE)
            .putExtra(EXTRA_ACCOUNT_ID, accountId)
            .setData("qownnotes://widget/account/${Uri.encode(accountId)}/create".toUri())
}

internal object WidgetPreferences {
    private const val NAME = "qownnotes-widgets"
    private const val ACCOUNT_PREFIX = "account."
    private const val ACCOUNT_NAME_PREFIX = "accountName."
    private const val NOTE_PREFIX = "note."
    private const val CATEGORY_SCOPE_PREFIX = "categoryScope."
    private const val BACKGROUND_PREFIX = "background."
    private const val OPACITY_PREFIX = "backgroundOpacity."
    private const val HEADER_PREFIX = "header."
    private const val ROW_PREFIX = "row."
    private const val FRAME_PREFIX = "frame."
    private const val FRAME_COLOR_PREFIX = "frameColor."
    private val APPEARANCE_PREFIXES = listOf(
        BACKGROUND_PREFIX,
        OPACITY_PREFIX,
        HEADER_PREFIX,
        ROW_PREFIX,
        FRAME_PREFIX,
        FRAME_COLOR_PREFIX
    )

    fun saveAccount(context: Context, widgetId: Int, account: Account) {
        preferences(context).edit {
            putString("$ACCOUNT_PREFIX$widgetId", account.id)
            putString("$ACCOUNT_NAME_PREFIX$widgetId", account.displayName)
        }
    }

    fun saveNoteList(context: Context, widgetId: Int, account: Account, scope: NoteCategoryScope) {
        saveAccount(context, widgetId, account)
        preferences(context).edit {
            putString("$CATEGORY_SCOPE_PREFIX$widgetId", NoteCategoryScopeCodec.encode(scope))
        }
    }

    fun saveAppearance(context: Context, widgetId: Int, appearance: WidgetAppearance) {
        preferences(context).edit {
            putColor("$BACKGROUND_PREFIX$widgetId", appearance.background)
            putInt(
                "$OPACITY_PREFIX$widgetId",
                AppearanceColors.coerceOpacity(appearance.backgroundOpacityPercent)
            )
            putColor("$HEADER_PREFIX$widgetId", appearance.header)
            putColor("$ROW_PREFIX$widgetId", appearance.row)
            putBoolean("$FRAME_PREFIX$widgetId", appearance.frame)
            putColor("$FRAME_COLOR_PREFIX$widgetId", appearance.frameColor)
        }
    }

    /** Widgets configured before appearance options existed keep the default look. */
    fun appearance(context: Context, widgetId: Int): WidgetAppearance {
        val preferences = preferences(context)
        fun color(prefix: String): Int? = "$prefix$widgetId".let { key ->
            if (preferences.contains(
                    key
                )
            ) {
                AppearanceColors.opaque(preferences.getInt(key, 0))
            } else {
                null
            }
        }
        return WidgetAppearance(
            background = color(BACKGROUND_PREFIX),
            backgroundOpacityPercent = AppearanceColors.coerceOpacity(
                preferences.getInt(
                    "$OPACITY_PREFIX$widgetId",
                    AppearanceColors.MAX_OPACITY_PERCENT
                )
            ),
            header = color(HEADER_PREFIX),
            row = color(ROW_PREFIX),
            frame = preferences.getBoolean("$FRAME_PREFIX$widgetId", false),
            frameColor = color(FRAME_COLOR_PREFIX)
        )
    }

    private fun android.content.SharedPreferences.Editor.putColor(key: String, color: Int?) {
        if (color == null) remove(key) else putInt(key, AppearanceColors.opaque(color))
    }

    /** Widgets configured before category filtering existed keep showing every note. */
    fun categoryScope(context: Context, widgetId: Int): NoteCategoryScope =
        NoteCategoryScopeCodec.decode(
            preferences(context).getString("$CATEGORY_SCOPE_PREFIX$widgetId", null),
            default = NoteCategoryScope.All
        )

    fun saveNote(context: Context, widgetId: Int, account: Account, localId: String) {
        saveAccount(context, widgetId, account)
        preferences(context).edit { putString("$NOTE_PREFIX$widgetId", localId) }
    }

    fun accountId(context: Context, widgetId: Int): String? =
        preferences(context).getString("$ACCOUNT_PREFIX$widgetId", null)

    fun accountName(context: Context, widgetId: Int): String? =
        preferences(context).getString("$ACCOUNT_NAME_PREFIX$widgetId", null)

    fun noteId(context: Context, widgetId: Int): String? =
        preferences(context).getString("$NOTE_PREFIX$widgetId", null)

    fun remove(context: Context, widgetId: Int) {
        preferences(context).edit {
            remove("$ACCOUNT_PREFIX$widgetId")
            remove("$ACCOUNT_NAME_PREFIX$widgetId")
            remove("$NOTE_PREFIX$widgetId")
            remove("$CATEGORY_SCOPE_PREFIX$widgetId")
            APPEARANCE_PREFIXES.forEach { remove("$it$widgetId") }
        }
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}

class NoteListWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { update(context, manager, it) }
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        widgetIds.forEach { WidgetPreferences.remove(context, it) }
    }

    companion object {
        fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = remoteViews(context, widgetId) ?: return
            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_note_list)
        }

        internal fun remoteViews(context: Context, widgetId: Int): RemoteViews? {
            val accountId = WidgetPreferences.accountId(context, widgetId) ?: return null
            val views = RemoteViews(context.packageName, R.layout.widget_note_list)
            val appearance = WidgetPreferences.appearance(context, widgetId)
            WidgetAppearanceViews.applyContainer(views, appearance)
            NoteListWidgetHeader.apply(
                context,
                views,
                WidgetPreferences.categoryScope(context, widgetId),
                WidgetPreferences.accountName(context, widgetId),
                appearance
            )
            WidgetAppearanceViews.setSecondaryText(
                context,
                views,
                R.id.widget_empty,
                appearance.background
            )
            views.setRemoteAdapter(
                R.id.widget_note_list,
                Intent(context, NoteListWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .setData("qownnotes://widget/list/$widgetId".toUri())
            )
            views.setEmptyView(R.id.widget_note_list, R.id.widget_empty)
            views.setOnClickPendingIntent(
                R.id.widget_header,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    WidgetIntents.openNoteList(context, accountId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setPendingIntentTemplate(
                R.id.widget_note_list,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    WidgetIntents.openNoteTemplate(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_create,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    WidgetIntents.createNote(context, accountId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_camera,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    CaptureNoteActivity.intent(context, accountId, widgetId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            return views
        }
    }
}

/** Receives only app-owned pending intents, then hands their request to the main activity. */
class WidgetActionActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WidgetIntents.request(intent)?.let(application()::receiveWidgetRequest)
        startActivity(Intent(this, MainActivity::class.java).setAction(Intent.ACTION_MAIN))
        finish()
    }

    private fun application() = (applicationContext as QOwnNotesApplication).component
}

class NoteListWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        NoteListWidgetFactory(applicationContext, intent)
}

private class NoteListWidgetFactory(context: Context, intent: Intent) :
    RemoteViewsService.RemoteViewsFactory {
    private val context = context.applicationContext
    private val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    private var notes = emptyList<NoteListItem>()
    private var display = NoteListWidgetDisplay.DEFAULT
    private var appearance = WidgetAppearance.DEFAULT

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val accountId = WidgetPreferences.accountId(context, widgetId)
        val scope = WidgetPreferences.categoryScope(context, widgetId)
        appearance = WidgetPreferences.appearance(context, widgetId)
        display = accountId?.let {
            NoteListWidgetDisplay.of(application(context).component.settings, it)
        } ?: NoteListWidgetDisplay.DEFAULT
        notes = if (accountId == null) {
            emptyList()
        } else {
            runBlocking(Dispatchers.IO) {
                application(context).component.noteRepository.observeNotes(accountId).first()
            }.filter { NoteCategories.matches(it.category, scope) }
        }
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews? = notes.getOrNull(position)?.let { note ->
        NoteListWidgetRows.row(context, note, display, appearance)
    }

    override fun getLoadingView(): RemoteViews? = null

    // Both row layouts are declared so a density change does not reuse a recycled row of the
    // other layout while the collection refreshes.
    override fun getViewTypeCount(): Int = 2

    override fun getItemId(position: Int): Long =
        notes.getOrNull(position)?.localId?.hashCode()?.toLong() ?: 0

    override fun hasStableIds(): Boolean = true
}

/**
 * Applies a [WidgetAppearance] to widget views.
 *
 * Launchers may reapply new RemoteViews onto the existing view tree, so every property is set
 * explicitly, including the defaults, or a previously chosen color would survive a reset.
 * A zero color filter leaves the day/night resource drawables untouched.
 */
internal object WidgetAppearanceViews {
    private const val NO_TINT = 0

    fun applyContainer(views: RemoteViews, appearance: WidgetAppearance) {
        views.setInt(R.id.widget_background, "setColorFilter", appearance.background ?: NO_TINT)
        views.setInt(
            R.id.widget_background,
            "setImageAlpha",
            AppearanceColors.alpha(appearance.backgroundOpacityPercent)
        )
        views.setViewVisibility(
            R.id.widget_frame,
            if (appearance.frame) View.VISIBLE else View.GONE
        )
        views.setInt(R.id.widget_frame, "setColorFilter", appearance.frameColor ?: NO_TINT)
        views.setViewVisibility(
            R.id.widget_header_background,
            if (appearance.header == null) View.GONE else View.VISIBLE
        )
        views.setInt(R.id.widget_header_background, "setColorFilter", appearance.header ?: NO_TINT)
    }

    /** Surface under the header text; `null` when it is the default widget background. */
    fun headerSurface(appearance: WidgetAppearance): Int? =
        appearance.header ?: appearance.background

    fun setPrimaryText(context: Context, views: RemoteViews, viewId: Int, surface: Int?) =
        setTextColor(
            context,
            views,
            viewId,
            surface?.let(AppearanceColors::contentColor),
            R.color.widget_text
        )

    fun setSecondaryText(context: Context, views: RemoteViews, viewId: Int, surface: Int?) =
        setTextColor(
            context,
            views,
            viewId,
            surface?.let(AppearanceColors::secondaryContentColor),
            R.color.widget_text_secondary
        )

    fun setIconTint(views: RemoteViews, viewId: Int, surface: Int?) {
        views.setInt(
            viewId,
            "setColorFilter",
            surface?.let(AppearanceColors::contentColor) ?: NO_TINT
        )
    }

    fun setTextColor(
        context: Context,
        views: RemoteViews,
        viewId: Int,
        custom: Int?,
        defaultResource: Int
    ) {
        when {
            custom != null -> views.setTextColor(viewId, custom)
            // Resolved by the launcher when applied, so it still follows day and night mode.
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                views.setColor(viewId, "setTextColor", defaultResource)
            else -> views.setTextColor(viewId, context.getColor(defaultResource))
        }
    }
}

/** Two-line note-list widget header: what the widget shows, then whose notes they are. */
internal object NoteListWidgetHeader {
    fun title(context: Context, scope: NoteCategoryScope): String = when (scope) {
        NoteCategoryScope.All -> context.getString(R.string.widget_all_notes)
        NoteCategoryScope.Undefined -> context.getString(R.string.widget_uncategorized)
        is NoteCategoryScope.Category -> scope.value
    }

    fun apply(
        context: Context,
        views: RemoteViews,
        scope: NoteCategoryScope,
        accountName: String?,
        appearance: WidgetAppearance = WidgetAppearance.DEFAULT
    ) {
        views.setTextViewText(R.id.widget_title, title(context, scope))
        views.setTextViewText(R.id.widget_subtitle, accountName.orEmpty())
        views.setViewVisibility(
            R.id.widget_subtitle,
            if (accountName.isNullOrBlank()) View.GONE else View.VISIBLE
        )
        val header = WidgetAppearanceViews.headerSurface(appearance)
        WidgetAppearanceViews.setPrimaryText(context, views, R.id.widget_title, header)
        WidgetAppearanceViews.setSecondaryText(context, views, R.id.widget_subtitle, header)
        WidgetAppearanceViews.setIconTint(views, R.id.widget_camera, header)
        WidgetAppearanceViews.setIconTint(views, R.id.widget_create, header)
    }
}

/**
 * The app's note-list settings that also shape note-list widget rows, so a widget lists notes
 * the way the app does.
 */
internal data class NoteListWidgetDisplay(
    val compact: Boolean = false,
    val showPreview: Boolean = true,
    val showCategory: Boolean = false,
    val highlightCategories: Boolean = false,
    val categoryHighlight: Int? = null
) {
    companion object {
        val DEFAULT = NoteListWidgetDisplay()

        fun of(settings: AppSettings, accountId: String): NoteListWidgetDisplay {
            val appearance = settings.appearance.value
            return NoteListWidgetDisplay(
                compact = settings.compactNoteList.value,
                showPreview = settings.showNotePreview.value,
                showCategory = settings.showCategory(accountId).value,
                highlightCategories = appearance.highlightCategories,
                categoryHighlight = appearance.categoryHighlight
            )
        }
    }
}

internal object NoteListWidgetRows {
    private const val NO_TINT = 0

    fun layout(compact: Boolean): Int =
        if (compact) R.layout.widget_note_item_compact else R.layout.widget_note_item

    /** Every row view is set explicitly, because launchers may reapply onto a recycled row. */
    fun row(
        context: Context,
        note: NoteListItem,
        display: NoteListWidgetDisplay = NoteListWidgetDisplay.DEFAULT,
        appearance: WidgetAppearance = WidgetAppearance.DEFAULT
    ): RemoteViews = RemoteViews(context.packageName, layout(display.compact)).apply {
        setInt(R.id.widget_note_item_background, "setColorFilter", appearance.row ?: NO_TINT)
        WidgetAppearanceViews.setPrimaryText(context, this, R.id.widget_note_title, appearance.row)
        WidgetAppearanceViews.setSecondaryText(
            context,
            this,
            R.id.widget_note_excerpt,
            appearance.row
        )
        setTextViewText(R.id.widget_note_title, note.title)
        category(context, this, note, display, appearance)
        val excerpt = if (display.showPreview) NoteExcerpt.of(note.excerpt, note.title) else ""
        setTextViewText(R.id.widget_note_excerpt, excerpt)
        setViewVisibility(
            R.id.widget_note_excerpt,
            if (excerpt.isBlank()) View.GONE else View.VISIBLE
        )
        setOnClickFillInIntent(R.id.widget_note_item, WidgetIntents.openNoteFillIn(note.localId))
    }

    private fun category(
        context: Context,
        views: RemoteViews,
        note: NoteListItem,
        display: NoteListWidgetDisplay,
        appearance: WidgetAppearance
    ) {
        views.setViewVisibility(
            R.id.widget_note_category_container,
            if (display.showCategory) View.VISIBLE else View.GONE
        )
        if (!display.showCategory) return
        views.setTextViewText(
            R.id.widget_note_category,
            note.category.ifBlank { context.getString(R.string.widget_uncategorized) }
        )
        val highlight = display.highlightCategories
        views.setViewVisibility(
            R.id.widget_note_category_background,
            if (highlight) View.VISIBLE else View.GONE
        )
        views.setInt(
            R.id.widget_note_category_background,
            "setColorFilter",
            display.categoryHighlight?.takeIf { highlight } ?: NO_TINT
        )
        val density = context.resources.displayMetrics.density
        val horizontal = if (highlight) (6 * density).toInt() else 0
        val vertical = if (highlight) (1 * density).toInt() else 0
        views.setViewPadding(R.id.widget_note_category, horizontal, vertical, horizontal, vertical)
        if (highlight) {
            WidgetAppearanceViews.setTextColor(
                context,
                views,
                R.id.widget_note_category,
                display.categoryHighlight?.let(AppearanceColors::contentColor),
                R.color.widget_category_text
            )
        } else {
            WidgetAppearanceViews.setPrimaryText(
                context,
                views,
                R.id.widget_note_category,
                appearance.row
            )
        }
    }
}

class SingleNoteWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val result = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                widgetIds.forEach { update(context, manager, it) }
            } finally {
                result.finish()
            }
        }
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        widgetIds.forEach { WidgetPreferences.remove(context, it) }
    }

    companion object {
        suspend fun update(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val localId = WidgetPreferences.noteId(context, widgetId) ?: return
            val note = application(context).component.noteRepository.get(localId)
            updateSnapshot(
                context,
                manager,
                widgetId,
                localId,
                note?.title ?: context.getString(R.string.widget_note_unavailable)
            )
        }

        fun updateSnapshot(
            context: Context,
            manager: AppWidgetManager,
            widgetId: Int,
            localId: String,
            title: String
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_single_note)
            views.setTextViewText(R.id.widget_single_title, title)
            val appearance = WidgetPreferences.appearance(context, widgetId)
            WidgetAppearanceViews.applyContainer(views, appearance)
            WidgetAppearanceViews.setPrimaryText(
                context,
                views,
                R.id.widget_single_title,
                WidgetAppearanceViews.headerSurface(appearance)
            )
            views.setRemoteAdapter(
                R.id.widget_single_content,
                Intent(context, SingleNoteWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .setData("qownnotes://widget/single/$widgetId".toUri())
            )
            views.setPendingIntentTemplate(
                R.id.widget_single_content,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    WidgetIntents.openNoteTemplate(context),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                )
            )
            views.setOnClickPendingIntent(
                R.id.widget_single_note,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    WidgetIntents.openNote(context, localId),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_single_content)
        }
    }
}

class SingleNoteWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory =
        SingleNoteWidgetFactory(applicationContext, intent)
}

private class SingleNoteWidgetFactory(context: Context, intent: Intent) :
    RemoteViewsService.RemoteViewsFactory {
    private val context = context.applicationContext
    private val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
    private var localId: String? = null
    private var lines = emptyList<String>()
    private var background: Int? = null

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        localId = WidgetPreferences.noteId(context, widgetId)
        background = WidgetPreferences.appearance(context, widgetId).background
        val noteId = localId
        lines = if (noteId == null) {
            emptyList()
        } else {
            runBlocking(Dispatchers.IO) {
                application(context).component.noteRepository.get(noteId)?.content
            }?.take(8_000)?.lines().orEmpty()
        }
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = lines.size

    override fun getViewAt(position: Int): RemoteViews? = lines.getOrNull(position)?.let { line ->
        RemoteViews(context.packageName, R.layout.widget_single_note_line).apply {
            setTextViewText(R.id.widget_single_note_line, line)
            WidgetAppearanceViews.setSecondaryText(
                context,
                this,
                R.id.widget_single_note_line,
                background
            )
            localId?.let {
                setOnClickFillInIntent(
                    R.id.widget_single_note_line,
                    WidgetIntents.openNoteFillIn(it)
                )
            }
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long = position.toLong()

    override fun hasStableIds(): Boolean = true
}

class WidgetConfigurationActivity : ComponentActivity() {
    private var completingConfiguration = false
    private val widgetId by lazy {
        intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        val provider = AppWidgetManager.getInstance(this).getAppWidgetInfo(widgetId)?.provider
        val singleNote = provider?.className == SingleNoteWidgetProvider::class.java.name
        setContent {
            QOwnNotesTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigurationScreen(
                        singleNote,
                        onNoteList = ::completeNoteList,
                        onSingleNote = ::completeSingleNote
                    )
                }
            }
        }
    }

    private fun completeNoteList(
        account: Account,
        scope: NoteCategoryScope,
        appearance: WidgetAppearance
    ) {
        if (completingConfiguration) return
        completingConfiguration = true
        WidgetPreferences.saveNoteList(this, widgetId, account, scope)
        WidgetPreferences.saveAppearance(this, widgetId, appearance)
        NoteListWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId)
        finishConfigured()
    }

    private fun completeSingleNote(
        account: Account,
        note: NoteListItem,
        appearance: WidgetAppearance
    ) {
        if (completingConfiguration) return
        completingConfiguration = true
        val manager = AppWidgetManager.getInstance(this)
        WidgetPreferences.saveNote(this, widgetId, account, note.localId)
        WidgetPreferences.saveAppearance(this, widgetId, appearance)
        SingleNoteWidgetProvider.updateSnapshot(this, manager, widgetId, note.localId, note.title)
        CoroutineScope(Dispatchers.IO).launch {
            SingleNoteWidgetProvider.update(this@WidgetConfigurationActivity, manager, widgetId)
        }
        finishConfigured()
    }

    private fun finishConfigured() {
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }

    @Composable
    private fun ConfigurationScreen(
        singleNote: Boolean,
        onNoteList: (Account, NoteCategoryScope, WidgetAppearance) -> Unit,
        onSingleNote: (Account, NoteListItem, WidgetAppearance) -> Unit
    ) {
        val component = application(this).component
        val accounts by component.accountRepository.observeAccounts()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        var selectedAccount by remember { mutableStateOf<Account?>(null) }
        var chosenScope by remember { mutableStateOf<NoteCategoryScope?>(null) }
        var chosenNote by remember { mutableStateOf<NoteListItem?>(null) }
        // Reconfiguring starts from the widget's current look; new widgets start from defaults.
        var appearance by remember { mutableStateOf(WidgetPreferences.appearance(this, widgetId)) }
        val account = selectedAccount
        val notes by (
            account?.let { component.noteRepository.observeNotes(it.id) }
                ?: kotlinx.coroutines.flow.flowOf(emptyList())
            )
            .collectAsStateWithLifecycle(initialValue = emptyList())
        val categories = remember(notes) { NoteCategories.selectable(notes) }
        BackHandler(enabled = account != null) {
            if (chosenScope != null || chosenNote != null) {
                chosenScope = null
                chosenNote = null
            } else {
                selectedAccount = null
            }
        }
        val scope = chosenScope
        val note = chosenNote
        if (account != null && (scope != null || note != null)) {
            WidgetAppearanceStep(
                appearance = appearance,
                singleNote = singleNote,
                onChange = { appearance = it },
                onSave = {
                    if (note != null) {
                        onSingleNote(account, note, appearance)
                    } else if (scope != null) {
                        onNoteList(account, scope, appearance)
                    }
                }
            )
            return
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    text = getString(
                        when {
                            account == null -> R.string.widget_choose_account
                            singleNote -> R.string.widget_choose_note
                            else -> R.string.widget_choose_category
                        }
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(16.dp)
                )
            }
            if (accounts.isEmpty()) {
                item {
                    Text(
                        getString(R.string.widget_no_accounts),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else if (account == null) {
                items(accounts, key = Account::id) { item ->
                    WidgetConfigurationItem(item.displayName, "widget-account-${item.id}") {
                        selectedAccount = item
                    }
                }
            } else if (singleNote) {
                items(notes, key = NoteListItem::localId) { item ->
                    WidgetConfigurationItem(item.title, "widget-note-${item.localId}") {
                        chosenNote = item
                    }
                }
            } else {
                widgetCategoryChoices(categories) { chosenScope = it }
            }
        }
    }
}

/** Last configuration step: adjust the look, then save the whole widget configuration. */
@Composable
internal fun WidgetAppearanceStep(
    appearance: WidgetAppearance,
    singleNote: Boolean,
    onChange: (WidgetAppearance) -> Unit,
    onSave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("widget-appearance")
        ) {
            Text(
                stringResource(R.string.widget_customize_appearance),
                style = MaterialTheme.typography.headlineSmall
            )
            WidgetAppearanceEditor(appearance, singleNote, onChange)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(
                onClick = { onChange(WidgetAppearance.DEFAULT) },
                modifier = Modifier.testTag("widget-appearance-reset")
            ) { Text(stringResource(R.string.widget_reset_appearance)) }
            Button(onClick = onSave, modifier = Modifier.testTag("widget-appearance-save")) {
                Text(stringResource(R.string.widget_save))
            }
        }
    }
}

/**
 * Category choices for a note-list widget. "All categories" comes first because it matches what
 * widgets showed before they could be filtered.
 */
internal fun LazyListScope.widgetCategoryChoices(
    categories: List<String>,
    onChoose: (NoteCategoryScope) -> Unit
) {
    item(key = "all") {
        WidgetConfigurationItem(
            stringResource(R.string.widget_all_categories),
            "widget-category-all"
        ) { onChoose(NoteCategoryScope.All) }
    }
    item(key = "undefined") {
        WidgetConfigurationItem(
            stringResource(R.string.widget_uncategorized),
            "widget-category-undefined"
        ) { onChoose(NoteCategoryScope.Undefined) }
    }
    items(categories, key = { "category:$it" }) { category ->
        WidgetConfigurationItem(category, "widget-category-$category") {
            onChoose(NoteCategoryScope.Category(category))
        }
    }
}

@Composable
private fun WidgetConfigurationItem(text: String, testTag: String, onClick: () -> Unit) {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp)
            .testTag(testTag),
        style = MaterialTheme.typography.bodyLarge
    )
}

internal object NoteWidgetUpdater {
    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, NoteListWidgetProvider::class.java))
            .forEach { NoteListWidgetProvider.update(context, manager, it) }
        val resultIds =
            manager.getAppWidgetIds(ComponentName(context, SingleNoteWidgetProvider::class.java))
        CoroutineScope(Dispatchers.IO).launch {
            resultIds.forEach { SingleNoteWidgetProvider.update(context, manager, it) }
        }
    }
}

private fun application(context: Context) = context.applicationContext as QOwnNotesApplication
