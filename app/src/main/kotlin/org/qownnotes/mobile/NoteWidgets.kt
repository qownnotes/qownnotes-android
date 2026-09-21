package org.qownnotes.mobile

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import org.qownnotes.mobile.core.NoteListItem

sealed interface WidgetRequest {
    data class OpenNote(val localId: String) : WidgetRequest

    data class CreateNote(val accountId: String) : WidgetRequest
}

internal object WidgetIntents {
    const val ACTION_OPEN_NOTE = "org.qownnotes.mobile.widget.OPEN_NOTE"
    const val ACTION_CREATE_NOTE = "org.qownnotes.mobile.widget.CREATE_NOTE"
    const val EXTRA_ACCOUNT_ID = "accountId"
    const val EXTRA_NOTE_ID = "noteId"

    fun request(intent: Intent?): WidgetRequest? = when (intent?.action) {
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

    fun saveAccount(context: Context, widgetId: Int, account: Account) {
        preferences(context).edit {
            putString("$ACCOUNT_PREFIX$widgetId", account.id)
            putString("$ACCOUNT_NAME_PREFIX$widgetId", account.displayName)
        }
    }

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
            val accountId = WidgetPreferences.accountId(context, widgetId) ?: return
            val views = RemoteViews(context.packageName, R.layout.widget_note_list)
            views.setTextViewText(
                R.id.widget_title,
                WidgetPreferences.accountName(context, widgetId)
                    ?: context.getString(R.string.app_name)
            )
            views.setRemoteAdapter(
                R.id.widget_note_list,
                Intent(context, NoteListWidgetService::class.java)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    .setData("qownnotes://widget/list/$widgetId".toUri())
            )
            views.setEmptyView(R.id.widget_note_list, R.id.widget_empty)
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
            manager.updateAppWidget(widgetId, views)
            manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_note_list)
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

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        val accountId = WidgetPreferences.accountId(context, widgetId)
        notes = if (accountId == null) {
            emptyList()
        } else {
            runBlocking(Dispatchers.IO) {
                application(context).component.noteRepository.observeNotes(accountId).first()
            }
        }
    }

    override fun onDestroy() = Unit

    override fun getCount(): Int = notes.size

    override fun getViewAt(position: Int): RemoteViews? = notes.getOrNull(position)?.let { note ->
        RemoteViews(context.packageName, R.layout.widget_note_item).apply {
            setTextViewText(R.id.widget_note_title, note.title)
            setTextViewText(R.id.widget_note_excerpt, note.excerpt)
            setViewVisibility(
                R.id.widget_note_excerpt,
                if (note.excerpt.isBlank()) View.GONE else View.VISIBLE
            )
            val open = WidgetIntents.openNoteFillIn(note.localId)
            setOnClickFillInIntent(R.id.widget_note_item, open)
        }
    }

    override fun getLoadingView(): RemoteViews? = null

    override fun getViewTypeCount(): Int = 1

    override fun getItemId(position: Int): Long =
        notes.getOrNull(position)?.localId?.hashCode()?.toLong() ?: 0

    override fun hasStableIds(): Boolean = true
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

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        localId = WidgetPreferences.noteId(context, widgetId)
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ConfigurationScreen(singleNote, ::completeConfiguration)
                }
            }
        }
    }

    private fun completeConfiguration(account: Account, note: NoteListItem?) {
        if (completingConfiguration) return
        completingConfiguration = true
        val manager = AppWidgetManager.getInstance(this)
        if (note == null) {
            WidgetPreferences.saveAccount(this, widgetId, account)
            NoteListWidgetProvider.update(this, manager, widgetId)
        } else {
            WidgetPreferences.saveNote(this, widgetId, account, note.localId)
            SingleNoteWidgetProvider.updateSnapshot(
                this,
                manager,
                widgetId,
                note.localId,
                note.title
            )
            CoroutineScope(Dispatchers.IO).launch {
                SingleNoteWidgetProvider.update(
                    this@WidgetConfigurationActivity,
                    manager,
                    widgetId
                )
            }
        }
        setResult(
            Activity.RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
        )
        finish()
    }

    @Composable
    private fun ConfigurationScreen(
        singleNote: Boolean,
        onConfigured: (Account, NoteListItem?) -> Unit
    ) {
        val component = application(this).component
        val accounts by component.accountRepository.observeAccounts()
            .collectAsStateWithLifecycle(initialValue = emptyList())
        var selectedAccount by remember { mutableStateOf<Account?>(null) }
        val account = selectedAccount
        val notes by (
            account?.let { component.noteRepository.observeNotes(it.id) }
                ?: kotlinx.coroutines.flow.flowOf(emptyList())
            )
            .collectAsStateWithLifecycle(initialValue = emptyList())
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            item {
                Text(
                    text = if (singleNote) {
                        getString(R.string.widget_choose_note)
                    } else {
                        getString(R.string.widget_choose_account)
                    },
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
                    ConfigurationItem(item.displayName) {
                        if (singleNote) selectedAccount = item else onConfigured(item, null)
                    }
                }
            } else {
                items(notes, key = NoteListItem::localId) { note ->
                    ConfigurationItem(note.title) { onConfigured(account, note) }
                }
            }
        }
    }

    @Composable
    private fun ConfigurationItem(text: String, onClick: () -> Unit) {
        Text(
            text,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
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
