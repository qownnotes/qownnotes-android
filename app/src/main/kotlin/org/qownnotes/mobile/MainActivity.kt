package org.qownnotes.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StrikethroughS
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Title
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException
import com.nextcloud.android.sso.model.SingleSignOnAccount
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteNames
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.ResolvedNoteLink
import org.qownnotes.mobile.core.SharedText
import org.qownnotes.mobile.core.SyncState
import org.qownnotes.mobile.core.TrashedNote
import org.qownnotes.mobile.core.resolveInternalNoteLink
import org.qownnotes.mobile.markdown.MarkdownEditText
import org.qownnotes.mobile.markdown.MarkdownEditorBinding
import org.qownnotes.mobile.markdown.MarkdownFormatAction
import org.qownnotes.mobile.markdown.MarkdownRenderer
import org.qownnotes.mobile.markdown.NoteSearchColors
import org.qownnotes.mobile.markdown.NoteTextSize
import org.qownnotes.mobile.markdown.highlightNoteSearchMatches
import org.qownnotes.mobile.markdown.noteSearchMatchTop
import org.qownnotes.mobile.markdown.toggleTaskListItem

class MainActivity : ComponentActivity() {
    private var reconnectAccountId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reconnectAccountId = savedInstanceState?.getString(RECONNECT_ACCOUNT_ID)
        // A recreated activity is handed the intent it started with once more. Only a first start
        // carries a share that has not been accepted yet, so rotating the device or dying in the
        // background cannot turn one shared text into a second note.
        if (savedInstanceState == null) acceptShare(intent)
        enableEdgeToEdge()
        setContent {
            QOwnNotesApp(
                onImportAccount = { importAccount() },
                onReconnectAccount = ::reconnectAccount
            )
        }
    }

    /**
     * Receives a share that arrives while the application is already running. The activity is a
     * single task, so every share reaches the instance the user is looking at.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        acceptShare(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(RECONNECT_ACCOUNT_ID, reconnectAccountId)
        super.onSaveInstanceState(outState)
    }

    internal fun acceptShare(intent: Intent?) {
        sharedTextOf(intent)?.let(applicationComponent()::receiveShare)
    }

    private fun importAccount(expectedAccountId: String? = null) {
        reconnectAccountId = expectedAccountId
        applicationComponent().beginAccountImport()
        runCatching {
            accountImportGateway().begin(this, ::acceptImportedAccount)
        }
            .onFailure(::handleImportFailure)
    }

    private fun reconnectAccount(accountId: String) = importAccount(accountId)

    @Deprecated("Required by Nextcloud SSO 1.3.x")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        runCatching {
            accountImportGateway().handleActivityResult(
                this,
                requestCode,
                resultCode,
                data,
                ::acceptImportedAccount
            )
        }.onFailure(::handleImportFailure)
    }

    private fun applicationComponent() = (applicationContext as QOwnNotesApplication).component

    private fun accountImportGateway() =
        (applicationContext as QOwnNotesApplication).accountImportGateway

    private fun acceptImportedAccount(account: SingleSignOnAccount) {
        val expectedAccountId = reconnectAccountId
        reconnectAccountId = null
        applicationComponent().launchAccountImport(account, expectedAccountId)
    }

    private fun handleImportFailure(error: Throwable) {
        reconnectAccountId = null
        if (error is AccountImportCancelledException) {
            applicationComponent().cancelAccountImport()
        } else {
            applicationComponent().reportImportError(error)
        }
    }

    private companion object {
        const val RECONNECT_ACCOUNT_ID = "reconnectAccountId"
    }
}

/**
 * Reads the text of a share.
 *
 * Only text is read. An attachment is a stream this release cannot store, and an intent that
 * carries no text at all is an ordinary start rather than a share, so both leave the note list as
 * it was. The text can be styled, and its markup is dropped rather than guessed at, because the
 * note is Markdown and no sharing application promises which formatting its styling stood for.
 */
internal fun sharedTextOf(intent: Intent?): SharedText? {
    if (intent?.action != Intent.ACTION_SEND) return null
    if (intent.type?.startsWith("text/") != true) return null
    val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString().orEmpty()
    val subject = intent.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() }
    if (text.isBlank() && subject == null) return null
    return SharedText(text = text, subject = subject)
}

private sealed interface ArchiveLoadState<out T> {
    data object Idle : ArchiveLoadState<Nothing>

    data object Loading : ArchiveLoadState<Nothing>

    data class Loaded<T>(val items: List<T>) : ArchiveLoadState<T>

    data class Failed(val message: String) : ArchiveLoadState<Nothing>
}

@Composable
fun QOwnNotesApp(onImportAccount: () -> Unit = {}, onReconnectAccount: (String) -> Unit = {}) {
    val application = LocalContext.current.applicationContext as QOwnNotesApplication
    QOwnNotesTheme {
        NotesNavigation(application.component, onImportAccount, onReconnectAccount)
    }
}

@Composable
private fun QOwnNotesTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme =
        if (androidx.compose.foundation.isSystemInDarkTheme()) {
            darkColorScheme()
        } else {
            lightColorScheme()
        },
        content = content
    )
}

@Composable
private fun NotesNavigation(
    component: ApplicationComponent,
    onImportAccount: () -> Unit,
    onReconnectAccount: (String) -> Unit
) {
    val accounts by component.accountRepository.observeAccounts()
        .collectAsStateWithLifecycle(initialValue = null as List<Account>?)
    val importState by component.importState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var observedAccountIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeading by rememberSaveable { mutableStateOf<String?>(null) }
    var editOnOpenNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var navigationRequest by rememberSaveable { mutableStateOf(0) }
    var noteHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(accounts, selectedAccountId) {
        val loadedAccounts = accounts ?: return@LaunchedEffect
        val loadedAccountIds = loadedAccounts.map(Account::id)
        val addedAccountIds = loadedAccountIds.filterNot(observedAccountIds::contains)
        observedAccountIds = loadedAccountIds
        when {
            addedAccountIds.size == 1 -> selectedAccountId = addedAccountIds.single()
            loadedAccounts.isNotEmpty() && loadedAccounts.none { it.id == selectedAccountId } ->
                selectedAccountId = loadedAccounts.first().id
        }
    }
    BackHandler(enabled = selectedNoteId != null) {
        selectedNoteId = noteHistory.lastOrNull()
        noteHistory = noteHistory.dropLast(1)
        selectedHeading = null
        editOnOpenNoteId = null
        navigationRequest++
    }

    val loadedAccounts = accounts
    val activeAccountId = loadedAccounts?.firstOrNull { it.id == selectedAccountId }?.id
        ?: loadedAccounts?.firstOrNull()?.id
    val pendingShare by component.pendingShare.collectAsStateWithLifecycle()
    // Text another application shared becomes a note in the account that is being looked at, and
    // that note is opened, so the share ends where the user can see and correct it. A share that
    // arrives before any account exists waits here until onboarding has produced one.
    LaunchedEffect(pendingShare, activeAccountId) {
        if (pendingShare == null || activeAccountId == null) return@LaunchedEffect
        val shared = component.takePendingShare() ?: return@LaunchedEffect
        val note = component.createSharedNote(activeAccountId, shared)
        noteHistory = emptyList()
        selectedNoteId = note.localId
        selectedHeading = null
        navigationRequest++
    }
    val noteId = selectedNoteId
    if (loadedAccounts == null) {
        LoadingScreen()
    } else if (noteId != null) {
        key(noteId) {
            NoteDetailScreen(
                component = component,
                localId = noteId,
                heading = selectedHeading,
                startEditing = editOnOpenNoteId == noteId,
                navigationRequest = navigationRequest,
                onInitialEditStarted = { editOnOpenNoteId = null },
                onBackToList = {
                    selectedNoteId = null
                    selectedHeading = null
                    editOnOpenNoteId = null
                    noteHistory = emptyList()
                },
                onOpen = { destination ->
                    if (noteId != destination.localId) noteHistory = noteHistory + noteId
                    selectedNoteId = destination.localId
                    selectedHeading = destination.heading
                    editOnOpenNoteId = null
                    navigationRequest++
                }
            )
        }
    } else if (loadedAccounts.isEmpty()) {
        AccountOnboarding(importState, pendingShare != null, onImportAccount)
    } else {
        NoteListScreen(
            component = component,
            accounts = loadedAccounts,
            accountId = requireNotNull(activeAccountId),
            importState = importState,
            onSelectAccount = { selectedAccountId = it },
            onImportAccount = onImportAccount,
            onReconnectAccount = onReconnectAccount,
            onRemoveAccount = { accountId ->
                scope.launch {
                    component.removeLocalData(accountId)
                    if (selectedAccountId == accountId) selectedAccountId = null
                    selectedNoteId = null
                    selectedHeading = null
                    noteHistory = emptyList()
                }
            },
            onCreate = { accountId ->
                scope.launch {
                    val note = component.createNote(accountId)
                    noteHistory = emptyList()
                    selectedNoteId = note.localId
                    selectedHeading = null
                    editOnOpenNoteId = note.localId
                    navigationRequest++
                }
            },
            onOpen = {
                noteHistory = emptyList()
                selectedNoteId = it
                selectedHeading = null
                editOnOpenNoteId = null
                navigationRequest++
            }
        )
    }
}

@Composable
private fun LoadingScreen() {
    Column(
        modifier = Modifier.fillMaxSize().testTag("app-loading"),
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(32.dp))
    }
}

@Composable
private fun AccountOnboarding(
    state: SyncUiState,
    sharedTextWaiting: Boolean,
    onImportAccount: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).testTag("onboarding"),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Your Nextcloud notes, offline", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose an account from the Nextcloud Files app to download and cache your notes.",
            modifier = Modifier.padding(vertical = 20.dp)
        )
        // A note needs an account to belong to. Say why the shared text is not a note yet rather
        // than leaving the sharer in front of an unexplained onboarding screen.
        if (sharedTextWaiting) {
            Text(
                "The shared text is kept and becomes a note once an account has been added.",
                modifier = Modifier.padding(bottom = 20.dp).testTag("shared-text-waiting")
            )
        }
        when (state) {
            SyncUiState.Refreshing -> CircularProgressIndicator()
            is SyncUiState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            else -> Unit
        }
        Button(
            onClick = onImportAccount,
            enabled = state !is SyncUiState.Refreshing,
            modifier = Modifier.testTag("add-account")
        ) {
            Text("Add Nextcloud account")
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NoteListScreen(
    component: ApplicationComponent,
    accounts: List<Account>,
    accountId: String,
    importState: SyncUiState,
    onSelectAccount: (String) -> Unit,
    onImportAccount: () -> Unit,
    onReconnectAccount: (String) -> Unit,
    onRemoveAccount: (String) -> Unit,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var showRemoveConfirmation by rememberSaveable(accountId) { mutableStateOf(false) }
    var showAccountChooser by rememberSaveable(accountId) { mutableStateOf(false) }
    var accountMenuOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    var selectionMenuOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    var selectedNoteIds by rememberSaveable(accountId) { mutableStateOf(emptyList<String>()) }
    var trashState by remember(accountId) {
        mutableStateOf<ArchiveLoadState<TrashedNote>>(ArchiveLoadState.Idle)
    }
    var trashToRestore by remember(accountId) { mutableStateOf<TrashedNote?>(null) }
    var trashRequestId by remember(accountId) { mutableIntStateOf(0) }
    val allNotesFlow = remember(accountId) { component.noteRepository.observeNotes(accountId) }
    val allNotes by allNotesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val notesFlow = remember(accountId, query) {
        if (accountId.isBlank()) {
            flowOf(emptyList())
        } else {
            component.noteRepository.searchNotes(accountId, query)
        }
    }
    val notes by notesFlow.collectAsStateWithLifecycle(initialValue = null as List<Note>?)
    val syncStates by component.syncStates.collectAsStateWithLifecycle()
    val syncState = syncStates[accountId] ?: SyncUiState.Idle
    val account = accounts.first { it.id == accountId }
    val scope = rememberCoroutineScope()
    val selectionActive = selectedNoteIds.isNotEmpty()

    LaunchedEffect(accountId) { component.refresh(accountId) }
    LaunchedEffect(notes) {
        val visibleIds = notes?.mapTo(mutableSetOf(), Note::localId) ?: return@LaunchedEffect
        selectedNoteIds = selectedNoteIds.filter { it in visibleIds }
    }
    BackHandler(enabled = selectionActive) {
        selectionMenuOpen = false
        selectedNoteIds = emptyList()
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    navigationIcon = {
                        if (selectionActive) {
                            IconButton(
                                onClick = { selectedNoteIds = emptyList() },
                                modifier = Modifier.testTag("clear-note-selection")
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Clear selection"
                                )
                            }
                        }
                    },
                    title = {
                        if (selectionActive) {
                            Text("${selectedNoteIds.size} selected")
                        } else {
                            Text(
                                account.displayName,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    actions = {
                        if (selectionActive) {
                            Box {
                                IconButton(
                                    onClick = { selectionMenuOpen = true },
                                    modifier = Modifier.testTag("note-selection-menu")
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Selected note actions"
                                    )
                                }
                                DropdownMenu(
                                    expanded = selectionMenuOpen,
                                    onDismissRequest = { selectionMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Move to trash") },
                                        onClick = {
                                            val ids = selectedNoteIds
                                            selectionMenuOpen = false
                                            selectedNoteIds = emptyList()
                                            scope.launch {
                                                component.moveNotesToTrash(accountId, ids)
                                            }
                                        },
                                        modifier = Modifier.testTag("move-notes-to-trash")
                                    )
                                }
                            }
                        } else {
                            // What these actions act on is the account named beside them, which is
                            // why they sit under it and say so, rather than standing next to a note
                            // action as bare verbs that read as if they applied to the notes list.
                            Box {
                                IconButton(
                                    onClick = { accountMenuOpen = true },
                                    modifier = Modifier.testTag("account-menu")
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "Account actions"
                                    )
                                }
                                DropdownMenu(
                                    expanded = accountMenuOpen,
                                    onDismissRequest = { accountMenuOpen = false }
                                ) {
                                    if (accounts.size > 1) {
                                        DropdownMenuItem(
                                            text = { Text("Switch account") },
                                            onClick = {
                                                accountMenuOpen = false
                                                if (accounts.size > 2) {
                                                    showAccountChooser = true
                                                } else {
                                                    val next =
                                                        (accounts.indexOf(account) + 1) %
                                                            accounts.size
                                                    onSelectAccount(accounts[next].id)
                                                }
                                            },
                                            modifier = Modifier.testTag("switch-account")
                                        )
                                    }
                                    DropdownMenuItem(
                                        text = { Text("Add account") },
                                        onClick = {
                                            accountMenuOpen = false
                                            onImportAccount()
                                        },
                                        modifier = Modifier.testTag("add-account")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Remove account") },
                                        onClick = {
                                            accountMenuOpen = false
                                            showRemoveConfirmation = true
                                        },
                                        modifier = Modifier.testTag("remove-account")
                                    )
                                }
                            }
                        }
                    }
                )
                if (!selectionActive) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .testTag("note-list-actions")
                    ) {
                        TextButton(
                            onClick = { onCreate(accountId) },
                            modifier = Modifier.testTag("create-note")
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("New note")
                        }
                        TextButton(
                            onClick = {
                                val requestId = ++trashRequestId
                                trashState = ArchiveLoadState.Loading
                                scope.launch {
                                    val result = runCatching {
                                        component.trashedNotes(
                                            accountId,
                                            allNotes.mapTo(mutableSetOf(), Note::category)
                                        )
                                    }.fold(
                                        onSuccess = { ArchiveLoadState.Loaded(it) },
                                        onFailure = {
                                            ArchiveLoadState.Failed(
                                                it.message ?: "Could not load remote trash"
                                            )
                                        }
                                    )
                                    if (trashRequestId == requestId) trashState = result
                                }
                            },
                            modifier = Modifier.testTag("remote-trash")
                        ) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Trash")
                        }
                    }
                }
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = syncState is SyncUiState.Refreshing,
            onRefresh = { scope.launch { component.refresh(accountId) } },
            modifier = Modifier.fillMaxSize().padding(padding).testTag("pull-to-refresh")
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize().testTag("note-list")) {
                if (importState is SyncUiState.Failed) {
                    item {
                        Text(
                            importState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search title and content") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .testTag("note-search")
                    )
                }
                item {
                    SyncStatus(syncState, reconnect = { onReconnectAccount(accountId) })
                }
                if (notes == null) {
                    item {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(24.dp).testTag("notes-loading")
                        )
                    }
                } else if (notes!!.isEmpty()) {
                    item {
                        Text(
                            if (query.isBlank()) "No cached notes" else "No matching notes",
                            modifier = Modifier.padding(24.dp),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                } else {
                    items(notes!!, key = Note::localId) { note ->
                        val selected = note.localId in selectedNoteIds
                        Row(
                            modifier =
                            Modifier.fillMaxWidth().testTag("note-${note.localId}")
                                .background(
                                    if (selected) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .semantics { this.selected = selected }
                                .padding(start = 20.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionActive) {
                                                selectedNoteIds =
                                                    if (selected) {
                                                        selectedNoteIds - note.localId
                                                    } else {
                                                        selectedNoteIds + note.localId
                                                    }
                                            } else {
                                                onOpen(note.localId)
                                            }
                                        },
                                        onLongClick = {
                                            if (!selected) selectedNoteIds += note.localId
                                        }
                                    )
                                    .padding(vertical = 8.dp)
                            ) {
                                Text(note.title, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    note.category.ifBlank { "Uncategorized" },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        component.setFavorite(note.localId, !note.favorite)
                                    }
                                },
                                enabled = !selectionActive,
                                modifier = Modifier.testTag("favorite-${note.localId}")
                            ) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription =
                                    if (note.favorite) {
                                        "Remove from favorites"
                                    } else {
                                        "Add to favorites"
                                    },
                                    tint =
                                    if (note.favorite) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (showAccountChooser) {
        AlertDialog(
            onDismissRequest = { showAccountChooser = false },
            title = { Text("Switch account") },
            text = {
                Column {
                    accounts.forEach { choice ->
                        Row(
                            modifier =
                            Modifier.fillMaxWidth()
                                .clickable {
                                    showAccountChooser = false
                                    onSelectAccount(choice.id)
                                }
                                .testTag("account-choice-${choice.id}"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = choice.id == accountId, onClick = null)
                            Text(choice.displayName)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAccountChooser = false }) { Text("Cancel") }
            },
            modifier = Modifier.testTag("account-chooser")
        )
    }
    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = { Text("Remove local data?") },
            text = {
                Text(
                    "Downloaded notes and synchronization history for ${account.displayName} " +
                        "will be removed from this device. The Nextcloud account and server " +
                        "notes will not be deleted."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRemoveConfirmation = false
                        onRemoveAccount(accountId)
                    },
                    modifier = Modifier.testTag("confirm-remove-account")
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmation = false }) { Text("Cancel") }
            }
        )
    }
    if (trashToRestore == null) {
        when (val state = trashState) {
            ArchiveLoadState.Idle -> Unit
            ArchiveLoadState.Loading -> ArchiveLoadingDialog(
                title = "Remote trash",
                onDismiss = {
                    trashRequestId++
                    trashState = ArchiveLoadState.Idle
                }
            )
            is ArchiveLoadState.Failed -> ArchiveErrorDialog(
                title = "Remote trash",
                message = state.message,
                onDismiss = { trashState = ArchiveLoadState.Idle }
            )
            is ArchiveLoadState.Loaded -> TrashedNotesDialog(
                notes = state.items,
                onDismiss = { trashState = ArchiveLoadState.Idle },
                onRestore = { trashToRestore = it }
            )
        }
    }
    trashToRestore?.let { trashed ->
        AlertDialog(
            onDismissRequest = { trashToRestore = null },
            title = { Text("Restore ${trashed.name}?") },
            text = { Text("The note and its server versions will be restored in Nextcloud.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        trashToRestore = null
                        val requestId = ++trashRequestId
                        trashState = ArchiveLoadState.Loading
                        scope.launch {
                            val result = runCatching {
                                component.restoreTrashedNote(accountId, trashed)
                                component.trashedNotes(
                                    accountId,
                                    allNotes.mapTo(mutableSetOf(), Note::category)
                                )
                            }.fold(
                                onSuccess = { ArchiveLoadState.Loaded(it) },
                                onFailure = {
                                    ArchiveLoadState.Failed(
                                        it.message ?: "Could not restore the trashed note"
                                    )
                                }
                            )
                            if (trashRequestId == requestId) trashState = result
                        }
                    },
                    modifier = Modifier.testTag("confirm-restore-trashed-note")
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { trashToRestore = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SyncStatus(state: SyncUiState, reconnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (state) {
            SyncUiState.Idle -> Text(
                "Available offline",
                style = MaterialTheme.typography.labelMedium
            )
            SyncUiState.Refreshing -> Text("Refreshing")
            is SyncUiState.Failed -> ExpandableSyncError(
                message = state.message,
                technicalDetails = state.diagnostic,
                testTag = "account-sync-error",
                modifier = Modifier.weight(1f)
            )
            is SyncUiState.AuthenticationRequired ->
                Text(state.message, color = MaterialTheme.colorScheme.error)
            is SyncUiState.AccountRemoved ->
                Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        val reconnectRequired = state is SyncUiState.AuthenticationRequired ||
            state is SyncUiState.AccountRemoved
        if (reconnectRequired) {
            TextButton(onClick = reconnect) { Text("Reconnect") }
        }
    }
}

@Composable
private fun ArchiveLoadingDialog(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { CircularProgressIndicator(modifier = Modifier.testTag("archive-loading")) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ArchiveErrorDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message, color = MaterialTheme.colorScheme.error) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun NoteVersionsDialog(
    versions: List<RemoteNoteVersion>,
    restoreEnabled: Boolean,
    onDismiss: () -> Unit,
    onRestore: (RemoteNoteVersion) -> Unit
) {
    var selected by remember(versions) { mutableStateOf(versions.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note versions") },
        text = {
            if (versions.isEmpty()) {
                Text("No versions were found for this note.")
            } else {
                Column(modifier = Modifier.heightIn(max = 520.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        versions.forEach { version ->
                            TextButton(
                                onClick = { selected = version },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("note-version-${version.timestamp}")
                            ) { Text(version.displayTimestamp) }
                        }
                    }
                    Text(
                        selected?.content.orEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            .verticalScroll(rememberScrollState()).testTag("note-version-preview")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onRestore) },
                enabled = restoreEnabled && selected != null,
                modifier = Modifier.testTag("restore-note-version")
            ) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun TrashedNotesDialog(
    notes: List<TrashedNote>,
    onDismiss: () -> Unit,
    onRestore: (TrashedNote) -> Unit
) {
    var selected by remember(notes) { mutableStateOf(notes.firstOrNull()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remote trash") },
        text = {
            if (notes.isEmpty()) {
                Text("No trashed notes were found on the server.")
            } else {
                Column(modifier = Modifier.heightIn(max = 520.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 180.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        notes.forEach { note ->
                            TextButton(
                                onClick = { selected = note },
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("trashed-note-${note.timestamp}")
                            ) { Text("${note.name} - ${note.displayTimestamp}") }
                        }
                    }
                    Text(
                        selected?.content.orEmpty(),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                            .verticalScroll(rememberScrollState()).testTag("trashed-note-preview")
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { selected?.let(onRestore) },
                enabled = selected != null,
                modifier = Modifier.testTag("restore-trashed-note")
            ) { Text("Restore") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDetailScreen(
    component: ApplicationComponent,
    localId: String,
    heading: String?,
    startEditing: Boolean,
    navigationRequest: Int,
    onInitialEditStarted: () -> Unit,
    onBackToList: () -> Unit,
    onOpen: (ResolvedNoteLink) -> Unit
) {
    val note by component.noteRepository.observeNote(localId)
        .collectAsStateWithLifecycle(initialValue = null)
    val noteSyncDiagnostics by component.noteSyncDiagnostics.collectAsStateWithLifecycle()
    val accountNotesFlow = remember(note?.accountId) {
        note?.accountId?.let(component.noteRepository::observeNotes) ?: flowOf(emptyList())
    }
    val accountNotes by accountNotesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var editing by rememberSaveable(localId) { mutableStateOf(false) }
    var draft by remember(localId) { mutableStateOf<String?>(null) }
    // What the note held when editing started. Typing is saved continuously, so discarding means
    // restoring this, not merely dropping what has not been written yet.
    var contentBeforeEditing by rememberSaveable(localId) { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by rememberSaveable(localId) { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable(localId) { mutableStateOf(false) }
    var renaming by rememberSaveable(localId) { mutableStateOf(false) }
    var noteMenuOpen by rememberSaveable(localId) { mutableStateOf(false) }
    var noteName by rememberSaveable(localId) { mutableStateOf("") }
    var selectionStart by rememberSaveable(localId) { mutableStateOf(0) }
    var selectionEnd by rememberSaveable(localId) { mutableStateOf(0) }
    var editor by remember { mutableStateOf<MarkdownEditText?>(null) }
    val editorScrollState = rememberScrollState()
    var editorViewportHeight by remember { mutableIntStateOf(0) }
    var renderedView by remember { mutableStateOf<AppCompatTextView?>(null) }
    var editorBinding by remember { mutableStateOf<MarkdownEditorBinding?>(null) }
    var canUndo by remember(localId) { mutableStateOf(false) }
    var canRedo by remember(localId) { mutableStateOf(false) }
    var pendingHeading by remember(localId, heading, navigationRequest) { mutableStateOf(heading) }
    var loadRemoteImages by remember(localId) { mutableStateOf(false) }
    var finding by rememberSaveable(localId) { mutableStateOf(false) }
    var togglingTask by remember(localId) { mutableStateOf(false) }
    var findQuery by rememberSaveable(localId) { mutableStateOf("") }
    var currentMatch by rememberSaveable(localId) { mutableStateOf(0) }
    var matches by remember(localId) { mutableStateOf(emptyList<IntRange>()) }
    var versionsState by remember(localId) {
        mutableStateOf<ArchiveLoadState<RemoteNoteVersion>>(ArchiveLoadState.Idle)
    }
    var versionToRestore by remember(localId) { mutableStateOf<RemoteNoteVersion?>(null) }
    var versionsRequestId by remember(localId) { mutableIntStateOf(0) }
    val renderedNote = remember(localId) { RenderedNote() }
    val hasRemoteImages = remember(note?.content) {
        component.markdownRenderer.hasRemoteImages(note?.content.orEmpty())
    }
    val hasEncryptedContent = remember(note?.content) {
        component.markdownRenderer.hasEncryptedContent(note?.content.orEmpty())
    }
    val editorTextColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val searchColors = NoteSearchColors(
        matchBackground = MaterialTheme.colorScheme.secondaryContainer.toArgb(),
        matchText = MaterialTheme.colorScheme.onSecondaryContainer.toArgb(),
        currentBackground = MaterialTheme.colorScheme.primary.toArgb(),
        currentText = MaterialTheme.colorScheme.onPrimary.toArgb()
    )
    val noteTextSizeSp by component.settings.noteTextSizeSp.collectAsStateWithLifecycle()
    // Applied from a composition effect rather than from an `AndroidView` update block. An update
    // block that observes this value is rescheduled through the holder's `View.getHandler()`,
    // which is null while the view is detached, and the view/edit transition detaches one of them.
    LaunchedEffect(noteTextSizeSp, editor, renderedView) {
        editor?.setTextSize(TypedValue.COMPLEX_UNIT_SP, noteTextSizeSp.toFloat())
        // Markwon sizes headings and code relative to the view's own text size, so the existing
        // spans rescale without re-rendering the note.
        renderedView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, noteTextSizeSp.toFloat())
    }
    // The editor is taller than its scrolling container, so keep its caret line inside the outer
    // viewport as typing, formatting, and history actions move the selection.
    LaunchedEffect(editing, editor, selectionStart, editorViewportHeight) {
        val view = editor ?: return@LaunchedEffect
        if (!editing || editorViewportHeight == 0) return@LaunchedEffect
        withFrameNanos { }
        val layout = view.layout ?: return@LaunchedEffect
        val line = layout.getLineForOffset(selectionStart.coerceIn(0, view.length()))
        val caretTop = layout.getLineTop(line) + view.totalPaddingTop
        val caretBottom = layout.getLineBottom(line) + view.totalPaddingTop
        val viewportTop = editorScrollState.value
        val viewportBottom = viewportTop + editorViewportHeight
        val target = when {
            caretTop < viewportTop -> caretTop
            caretBottom > viewportBottom -> caretBottom - editorViewportHeight
            else -> null
        }
        target?.let { editorScrollState.scrollTo(it.coerceIn(0, editorScrollState.maxValue)) }
    }
    val latestDraft by rememberUpdatedState(draft)
    val latestNote by rememberUpdatedState(note)
    val latestEditing by rememberUpdatedState(editing)
    LaunchedEffect(note?.localId, note?.content) {
        if (draft == null || !editing) {
            draft = note?.let { component.draft(localId, it.content) }
        }
    }
    LaunchedEffect(startEditing, note?.localId) {
        if (!startEditing || note == null) return@LaunchedEffect
        val editable = component.beginEditing(localId)
        onInitialEditStarted()
        editable?.let {
            draft = editable.content
            contentBeforeEditing = editable.content
            selectionStart = editable.content.length
            selectionEnd = selectionStart
            editing = true
        }
    }
    LaunchedEffect(draft, editing) {
        val source = draft ?: return@LaunchedEffect
        val current = note ?: return@LaunchedEffect
        if (!editing || source == current.content) return@LaunchedEffect
        delay(500)
        component.saveDraft(localId, source)
    }
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, localId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && latestEditing) {
                val source = latestDraft
                val current = latestNote
                if (source != null && current != null && source != current.content) {
                    component.saveDraftInBackground(localId, source)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (latestEditing) {
                latestDraft?.let { component.saveDraftInBackground(localId, it) }
            }
            editorBinding?.close()
        }
    }
    BackHandler(enabled = editing) {
        val source = draft
        if (source != null) {
            scope.launch {
                if (component.saveDraft(localId, source)) editing = false
            }
        } else {
            editing = false
        }
    }
    BackHandler(enabled = finding && !editing) {
        finding = false
        findQuery = ""
        currentMatch = 0
    }
    LaunchedEffect(localId, navigationRequest) {
        if (heading == null) scrollState.scrollTo(0)
    }
    LaunchedEffect(matches, currentMatch, renderedView) {
        val view = renderedView ?: return@LaunchedEffect
        val match = matches.getOrNull(currentMatch) ?: return@LaunchedEffect
        // A note that has just been rendered has no layout yet, and offsets cannot be resolved
        // before it has one, so give the view a frame to be measured.
        val top = noteSearchMatchTop(view, match)
            ?: run {
                withFrameNanos { }
                noteSearchMatchTop(view, match)
            }
        top?.let { scrollState.scrollTo(it) }
    }
    val showVersions = {
        val requestId = ++versionsRequestId
        versionsState = ArchiveLoadState.Loading
        scope.launch {
            val result = runCatching { component.noteVersions(localId) }.fold(
                onSuccess = { ArchiveLoadState.Loaded(it) },
                onFailure = {
                    ArchiveLoadState.Failed(it.message ?: "Could not load note versions")
                }
            )
            if (versionsRequestId == requestId) versionsState = result
        }
    }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            note?.title ?: "Note",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                val source = draft
                                if (editing && source != null) {
                                    scope.launch {
                                        if (component.saveDraft(localId, source)) onBackToList()
                                    }
                                } else {
                                    onBackToList()
                                }
                            },
                            modifier = Modifier.testTag("back-to-note-list")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to notes"
                            )
                        }
                    },
                    actions = {
                        val current = note
                        if (!editing) {
                            IconButton(
                                onClick = {
                                    finding = !finding
                                    if (!finding) {
                                        findQuery = ""
                                        currentMatch = 0
                                    }
                                },
                                modifier = Modifier.testTag("find-in-note")
                            ) {
                                Icon(Icons.Filled.Search, contentDescription = "Find in note")
                            }
                            if (
                                current != null &&
                                !current.readOnly &&
                                !hasEncryptedContent &&
                                (current.syncState != SyncState.FAILED || current.remoteId != null)
                            ) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            component.beginEditing(localId)?.let { editable ->
                                                draft = editable.content
                                                contentBeforeEditing = editable.content
                                                selectionStart = sourceOffsetForReadingPosition(
                                                    renderedView,
                                                    scrollState.value,
                                                    editable.content
                                                )
                                                selectionEnd = selectionStart
                                                editing = true
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag("edit-note")
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit note")
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { noteMenuOpen = true },
                                    modifier = Modifier.testTag("note-menu")
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = "More note actions"
                                    )
                                }
                                DropdownMenu(
                                    expanded = noteMenuOpen,
                                    onDismissRequest = { noteMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Decrease text size") },
                                        onClick = {
                                            noteMenuOpen = false
                                            component.settings.decreaseNoteTextSize()
                                        },
                                        enabled = NoteTextSize.canDecrease(noteTextSizeSp),
                                        modifier = Modifier.testTag("decrease-note-text-size")
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Increase text size") },
                                        onClick = {
                                            noteMenuOpen = false
                                            component.settings.increaseNoteTextSize()
                                        },
                                        enabled = NoteTextSize.canIncrease(noteTextSizeSp),
                                        modifier = Modifier.testTag("increase-note-text-size")
                                    )
                                    if (current?.remoteId != null) {
                                        DropdownMenuItem(
                                            text = { Text("Versions") },
                                            onClick = {
                                                noteMenuOpen = false
                                                showVersions()
                                            },
                                            modifier = Modifier.testTag("note-versions")
                                        )
                                    }
                                    if (current != null && !current.readOnly) {
                                        DropdownMenuItem(
                                            text = { Text("Rename") },
                                            onClick = {
                                                noteMenuOpen = false
                                                noteName = current.title
                                                renaming = true
                                            },
                                            modifier = Modifier.testTag("rename-note")
                                        )
                                    }
                                    if (current != null) {
                                        DropdownMenuItem(
                                            text = { Text("Move to trash") },
                                            onClick = {
                                                noteMenuOpen = false
                                                showDeleteConfirmation = true
                                            },
                                            modifier = Modifier.testTag("delete-note")
                                        )
                                    }
                                    if (
                                        current != null &&
                                        !current.readOnly &&
                                        !hasEncryptedContent &&
                                        current.syncState == SyncState.FAILED
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Retry sync") },
                                            onClick = {
                                                noteMenuOpen = false
                                                scope.launch { component.retryNote(localId) }
                                            },
                                            modifier = Modifier.testTag("retry-note")
                                        )
                                    }
                                }
                            }
                        }
                    }
                )
                val current = note
                if (editing) {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                            .testTag("note-actions")
                    ) {
                        ActionIconButton(
                            icon = Icons.Filled.TextDecrease,
                            description = "Decrease note text size",
                            testTag = "decrease-note-text-size",
                            enabled = NoteTextSize.canDecrease(noteTextSizeSp),
                            onClick = component.settings::decreaseNoteTextSize
                        )
                        ActionIconButton(
                            icon = Icons.Filled.TextIncrease,
                            description = "Increase note text size",
                            testTag = "increase-note-text-size",
                            enabled = NoteTextSize.canIncrease(noteTextSizeSp),
                            onClick = component.settings::increaseNoteTextSize
                        )
                        ActionIconButton(
                            icon = Icons.Filled.Close,
                            description = "Cancel editing",
                            testTag = "cancel-editing",
                            onClick = {
                                if (draft != contentBeforeEditing) {
                                    showDiscardConfirmation = true
                                } else {
                                    editing = false
                                }
                            }
                        )
                        ActionIconButton(
                            icon = Icons.Filled.Done,
                            description = "Finish editing",
                            testTag = "finish-editing",
                            onClick = {
                                val source = draft
                                if (source != null) {
                                    scope.launch {
                                        if (component.saveDraft(localId, source)) editing = false
                                    }
                                }
                            }
                        )
                    }
                } else if (current?.readOnly == true) {
                    Text("Read only", modifier = Modifier.padding(horizontal = 12.dp))
                }
            }
        }
    ) { padding ->
        if (editing) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                note?.lastSyncError?.let { message ->
                    ExpandableSyncError(
                        message = message,
                        technicalDetails = noteSyncDiagnostics[localId],
                        testTag = "note-sync-error",
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .testTag("format-toolbar")
                ) {
                    // First in the row, so stepping back does not require scrolling the toolbar.
                    EditorHistoryButton(
                        Icons.AutoMirrored.Filled.Undo,
                        "Undo",
                        "undo-edit",
                        canUndo,
                        editor
                    ) {
                        editorBinding?.undo()
                    }
                    EditorHistoryButton(
                        Icons.AutoMirrored.Filled.Redo,
                        "Redo",
                        "redo-edit",
                        canRedo,
                        editor
                    ) {
                        editorBinding?.redo()
                    }
                    FormatButton(
                        Icons.AutoMirrored.Filled.FormatListBulleted,
                        MarkdownFormatAction.BULLET,
                        editor,
                        "Create list item",
                        "format-list"
                    )
                    FormatButton(
                        Icons.Filled.Checklist,
                        MarkdownFormatAction.TASK,
                        editor,
                        "Create checkbox list item",
                        "format-checkbox-list"
                    )
                    FormatButton(
                        Icons.Filled.FormatBold,
                        MarkdownFormatAction.BOLD,
                        editor,
                        "Bold",
                        "format-bold"
                    )
                    FormatButton(
                        Icons.Filled.FormatItalic,
                        MarkdownFormatAction.ITALIC,
                        editor,
                        "Italic",
                        "format-italic"
                    )
                    FormatButton(
                        Icons.Filled.StrikethroughS,
                        MarkdownFormatAction.STRIKETHROUGH,
                        editor,
                        "Strikethrough",
                        "format-strikethrough"
                    )
                    FormatButton(
                        Icons.Filled.Code,
                        MarkdownFormatAction.CODE,
                        editor,
                        "Inline code",
                        "format-code"
                    )
                    FormatButton(
                        Icons.Filled.Link,
                        MarkdownFormatAction.LINK,
                        editor,
                        "Insert link",
                        "format-link"
                    )
                    FormatButton(
                        Icons.Filled.Title,
                        MarkdownFormatAction.HEADING,
                        editor,
                        "Heading",
                        "format-heading"
                    )
                    FormatButton(
                        Icons.Filled.FormatListNumbered,
                        MarkdownFormatAction.NUMBERED,
                        editor,
                        "Create numbered list item",
                        "format-numbered-list"
                    )
                    FormatButton(
                        Icons.Filled.FormatQuote,
                        MarkdownFormatAction.QUOTE,
                        editor,
                        "Create block quote",
                        "format-quote"
                    )
                }
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    // The editor is laid out at its full text height inside a scrolling
                    // container. An `EditText` only drag-scrolls its own text and never flings,
                    // so scrolling a long note by hand would otherwise crawl line by line. It
                    // still covers at least the viewport, so tapping below a short note keeps
                    // opening the keyboard at the end of the text.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize()
                            .onSizeChanged { editorViewportHeight = it.height }
                    ) {
                        val viewportHeight = maxHeight
                        Column(
                            modifier = Modifier.fillMaxSize().verticalScroll(editorScrollState)
                        ) {
                            AndroidView(
                                factory = { context ->
                                    MarkdownEditText(context).also { view ->
                                        view.id = R.id.markdown_editor
                                        view.setTextSize(
                                            TypedValue.COMPLEX_UNIT_SP,
                                            noteTextSizeSp.toFloat()
                                        )
                                        view.setText(draft.orEmpty())
                                        view.setSelection(
                                            selectionStart.coerceIn(0, view.length()),
                                            selectionEnd.coerceIn(0, view.length())
                                        )
                                        view.onSelectionChanged = { start, end ->
                                            selectionStart = start
                                            selectionEnd = end
                                        }
                                        editorBinding = MarkdownEditorBinding(
                                            context,
                                            view,
                                            onHistoryChanged = { undoable, redoable ->
                                                canUndo = undoable
                                                canRedo = redoable
                                            }
                                        ) {
                                            component.cacheDraft(localId, it)
                                            draft = it
                                        }
                                        editor = view
                                        view.focusForInput()
                                    }
                                },
                                update = { view ->
                                    if (view.currentTextColor != editorTextColor) {
                                        view.setTextColor(editorTextColor)
                                    }
                                },
                                onRelease = { view ->
                                    editorBinding?.close()
                                    editorBinding = null
                                    editor = null
                                    view.releaseInputFocus()
                                },
                                modifier = Modifier.fillMaxWidth()
                                    .heightIn(min = viewportHeight)
                                    .testTag("markdown-editor")
                            )
                        }
                    }
                    EditorFastScroller(
                        scrollState = editorScrollState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                if (finding) {
                    FindInNoteBar(
                        query = findQuery,
                        matchCount = matches.size,
                        currentMatch = currentMatch,
                        onQueryChange = {
                            findQuery = it
                            currentMatch = 0
                        },
                        onPrevious = {
                            if (matches.isNotEmpty()) {
                                currentMatch = (currentMatch + matches.size - 1) % matches.size
                            }
                        },
                        onNext = {
                            if (matches.isNotEmpty()) {
                                currentMatch = (currentMatch + 1) % matches.size
                            }
                        },
                        onClose = {
                            finding = false
                            findQuery = ""
                            currentMatch = 0
                        }
                    )
                }
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(scrollState)
                ) {
                    note?.lastSyncError?.let { message ->
                        ExpandableSyncError(
                            message = message,
                            technicalDetails = noteSyncDiagnostics[localId],
                            testTag = "note-sync-error",
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    if (hasRemoteImages && !loadRemoteImages) {
                        TextButton(onClick = { loadRemoteImages = true }) {
                            Text("Load remote images")
                        }
                    }
                    AndroidView(
                        factory = { context ->
                            AppCompatTextView(context).also {
                                it.id = R.id.markdown_view
                                it.setTextSize(TypedValue.COMPLEX_UNIT_SP, noteTextSizeSp.toFloat())
                                renderedView = it
                            }
                        },
                        onRelease = { renderedView = null },
                        update = { view ->
                            val source = note
                            if (view.currentTextColor != editorTextColor) {
                                view.setTextColor(editorTextColor)
                            }
                            // Rendering is the expensive part of this block, and the block also
                            // re-runs while the reader types a find query. Parse the note again
                            // only when what it renders to can actually have changed.
                            val renderKey =
                                listOf(source, accountNotes, pendingHeading, loadRemoteImages)
                            if (renderedNote.needsRendering(view, renderKey)) {
                                component.markdownRenderer.render(
                                    view = view,
                                    markdown = source?.content.orEmpty(),
                                    resolveInternalLink = { link ->
                                        source?.let {
                                            resolveInternalNoteLink(it, accountNotes, link)
                                        }
                                    },
                                    onInternalLink = onOpen,
                                    onTaskToggle = if (source != null && !source.readOnly) {
                                        { taskIndex ->
                                            if (!togglingTask) {
                                                toggleTaskListItem(source.content, taskIndex)?.let {
                                                    togglingTask = true
                                                    scope.launch {
                                                        component.saveDraft(localId, it)
                                                        togglingTask = false
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    heading = if (source != null) pendingHeading else null,
                                    onHeadingPositioned = { top ->
                                        if (pendingHeading != null) {
                                            if (top != null) {
                                                scope.launch {
                                                    // The Android view is laid out before Compose
                                                    // updates the containing scroll range. Wait
                                                    // through its follow-up measurement frame.
                                                    repeat(2) { withFrameNanos { } }
                                                    scrollState.scrollTo(top)
                                                    pendingHeading = null
                                                }
                                            } else {
                                                pendingHeading = null
                                            }
                                        }
                                    },
                                    loadRemoteImages = loadRemoteImages
                                )
                            }
                            val found = highlightNoteSearchMatches(
                                view = view,
                                query = if (finding) findQuery else "",
                                currentMatch = currentMatch,
                                colors = searchColors
                            )
                            if (found != matches) matches = found
                        },
                        modifier = Modifier.fillMaxWidth().padding(20.dp).testTag("markdown-view")
                    )
                }
            }
        }
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            title = { Text("Discard changes?") },
            text = {
                Text(
                    "This note was modified. Discarding restores it to the text it had when " +
                        "editing started."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        val restored = contentBeforeEditing
                        if (restored != null) {
                            scope.launch {
                                component.saveDraft(localId, restored)
                                draft = restored
                                editing = false
                            }
                        } else {
                            editing = false
                        }
                    },
                    modifier = Modifier.testTag("confirm-discard-changes")
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) { Text("Keep editing") }
            }
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Move note to trash?") },
            text = {
                Text(
                    "The note will disappear from this device now and move to the Nextcloud " +
                        "trash bin when synchronization is available."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        note?.let { current ->
                            scope.launch {
                                component.moveNotesToTrash(current.accountId, listOf(localId))
                                onBackToList()
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm-delete-note")
                ) { Text("Move to trash") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") }
            }
        )
    }
    if (renaming) {
        RenameNoteDialog(
            name = noteName,
            onNameChange = { noteName = it },
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                scope.launch { component.renameNote(localId, noteName) }
            }
        )
    }
    if (versionToRestore == null) {
        when (val state = versionsState) {
            ArchiveLoadState.Idle -> Unit
            ArchiveLoadState.Loading -> ArchiveLoadingDialog(
                title = "Note versions",
                onDismiss = {
                    versionsRequestId++
                    versionsState = ArchiveLoadState.Idle
                }
            )
            is ArchiveLoadState.Failed -> ArchiveErrorDialog(
                title = "Note versions",
                message = state.message,
                onDismiss = { versionsState = ArchiveLoadState.Idle }
            )
            is ArchiveLoadState.Loaded -> NoteVersionsDialog(
                versions = state.items,
                restoreEnabled = note?.readOnly == false,
                onDismiss = { versionsState = ArchiveLoadState.Idle },
                onRestore = { versionToRestore = it }
            )
        }
    }
    versionToRestore?.let { version ->
        AlertDialog(
            onDismissRequest = { versionToRestore = null },
            title = { Text("Restore this version?") },
            text = {
                Text(
                    "The current note content will be replaced and synchronized as a new edit."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        versionToRestore = null
                        val requestId = ++versionsRequestId
                        versionsState = ArchiveLoadState.Loading
                        scope.launch {
                            val restored = runCatching {
                                check(component.restoreNoteVersion(localId, version)) {
                                    "The note could not be updated"
                                }
                            }
                            val result = restored.fold(
                                onSuccess = { ArchiveLoadState.Idle },
                                onFailure = {
                                    ArchiveLoadState.Failed(
                                        it.message ?: "Could not restore the note version"
                                    )
                                }
                            )
                            if (versionsRequestId == requestId) versionsState = result
                        }
                    },
                    modifier = Modifier.testTag("confirm-restore-note-version")
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { versionToRestore = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun RenameNoteDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // The field is the only reason this dialog exists, so it takes the focus rather than asking
    // for another tap. A dialog composes into a window of its own, so the focus is taken once the
    // field has actually been placed rather than after a guessed number of frames. Taking it from
    // an effect rather than from within the layout pass keeps focus work out of measuring.
    var fieldPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(fieldPlaced) {
        if (fieldPlaced) focusRequester.requestFocus()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename note") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("File name") },
                singleLine = true,
                supportingText = {
                    Text("Characters a file name cannot hold are replaced by spaces.")
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    .onPlaced { fieldPlaced = true }
                    .testTag("note-name-field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = NoteNames.isValid(name),
                modifier = Modifier.testTag("confirm-rename-note")
            ) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
internal fun EditorFastScroller(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val scrollRange = scrollState.maxValue
    if (scrollRange <= 0) return
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = modifier.fillMaxHeight().width(48.dp)
            .semantics { contentDescription = "Editor fast scroll" }
            .testTag("editor-fast-scroll")
    ) {
        val density = LocalDensity.current
        val trackHeight = constraints.maxHeight.toFloat()
        val viewportHeight = trackHeight
        val contentHeight = viewportHeight + scrollRange
        val thumbHeight = maxOf(
            with(density) { 48.dp.toPx() },
            trackHeight * viewportHeight / contentHeight
        ).coerceAtMost(trackHeight)
        val travel = trackHeight - thumbHeight
        val thumbOffset = if (scrollRange == 0) 0F else travel * scrollState.value / scrollRange
        fun scrollTo(pointerY: Float) {
            val fraction = ((pointerY - thumbHeight / 2F) / travel).coerceIn(0F, 1F)
            scope.launch { scrollState.scrollTo((scrollRange * fraction).roundToInt()) }
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35F))
                .pointerInput(scrollState, scrollRange, trackHeight, thumbHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { scrollTo(it.y) },
                        onVerticalDrag = { change, _ ->
                            change.consume()
                            scrollTo(change.position.y)
                        }
                    )
                }
        ) {
            Box(
                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(4.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Box(
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffset.roundToInt()) }
                    .width(12.dp)
                    .height(with(density) { thumbHeight.toDp() })
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/**
 * Remembers what a rendered note view was last rendered from.
 *
 * This is deliberately not Compose state. It is read and written inside an `AndroidView` update
 * block, where observing it would make the block invalidate itself, and it tracks the view as well
 * as the inputs so a newly created view is always rendered into.
 */
private class RenderedNote {
    private var view: Any? = null
    private var key: List<Any?>? = null

    fun needsRendering(view: Any, key: List<Any?>): Boolean {
        if (this.view === view && this.key == key) return false
        this.view = view
        this.key = key
        return true
    }
}

private fun sourceOffsetForReadingPosition(
    view: AppCompatTextView?,
    scrollY: Int,
    markdown: String
): Int {
    val layout = view?.layout ?: return markdown.length
    val renderedLength = view.text.length
    if (renderedLength == 0 || markdown.isEmpty()) return 0
    val line = layout.getLineForVertical(scrollY.coerceIn(0, layout.height))
    val renderedOffset = layout.getLineStart(line)
    val approximateOffset = (renderedOffset.toLong() * markdown.length / renderedLength).toInt()
    if (approximateOffset == 0) return 0
    return markdown.lastIndexOf('\n', (approximateOffset - 1).coerceAtLeast(0)) + 1
}

/**
 * Finds text in the note that is being read. The bar stays above the note while the note scrolls,
 * so the query and the position within the matches remain visible while moving through them.
 */
@Composable
private fun FindInNoteBar(
    query: String,
    matchCount: Int,
    currentMatch: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // Opening the bar is a request to type, so take the focus instead of asking for a second tap.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Find in note") },
            singleLine = true,
            supportingText = {
                Text(
                    findMatchStatus(query, matchCount, currentMatch),
                    modifier = Modifier.testTag("find-match-status")
                )
            },
            modifier = Modifier.weight(1f).focusRequester(focusRequester).testTag("note-find-field")
        )
        ActionIconButton(
            icon = Icons.Filled.KeyboardArrowUp,
            description = "Previous match",
            testTag = "find-previous",
            enabled = matchCount > 0,
            onClick = onPrevious
        )
        ActionIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            description = "Next match",
            testTag = "find-next",
            enabled = matchCount > 0,
            onClick = onNext
        )
        ActionIconButton(
            icon = Icons.Filled.Close,
            description = "Close find",
            testTag = "close-find",
            enabled = true,
            onClick = onClose
        )
    }
}

/**
 * The status is always present, even while it is empty, so that typing a query cannot make the
 * note jump by a text line.
 */
private fun findMatchStatus(query: String, matchCount: Int, currentMatch: Int): String = when {
    query.isBlank() -> ""
    matchCount == 0 -> "No matches"
    else -> "${currentMatch.coerceIn(0, matchCount - 1) + 1} of $matchCount"
}

@Composable
private fun ExpandableSyncError(
    message: String,
    technicalDetails: String? = null,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val explanation = syncErrorExplanation(message)
    val context = LocalContext.current
    var showDetails by rememberSaveable(message) { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(
            message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("$testTag-summary")
        )
        if (explanation != null || technicalDetails != null) {
            TextButton(
                onClick = { showDetails = true },
                modifier = Modifier.testTag("$testTag-toggle")
            ) {
                Text("Details")
            }
        }
    }
    if (showDetails) {
        AlertDialog(
            onDismissRequest = { showDetails = false },
            title = { Text("Synchronization details") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())
                        .testTag("$testTag-details")
                ) {
                    explanation?.let { Text(it) }
                    technicalDetails?.let { diagnostic ->
                        val topPadding = if (explanation == null) 0.dp else 16.dp
                        Text(
                            "Exception text",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = topPadding)
                        )
                        SelectionContainer {
                            Text(
                                diagnostic,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.testTag("$testTag-diagnostic")
                            )
                        }
                        Text(
                            "Exception text can contain server or account information. Review it " +
                                "before sharing.",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(top = 12.dp)
                        )
                    }
                }
            },
            confirmButton = {
                technicalDetails?.let { diagnostic ->
                    TextButton(
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                ClipData.newPlainText("Synchronization exception", diagnostic)
                            )
                        },
                        modifier = Modifier.testTag("$testTag-copy")
                    ) { Text("Copy exception") }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDetails = false },
                    modifier = Modifier.testTag("$testTag-close")
                ) { Text("Close") }
            },
            modifier = Modifier.testTag("$testTag-dialog")
        )
    }
}

private fun syncErrorExplanation(message: String): String? = when (message) {
    "The server could not be reached" ->
        "Synchronization stopped before the server responded. Local edits remain saved on this " +
            "device. For a local server, check the phone's Wi-Fi or VPN and confirm that " +
            "Nextcloud Files can reach the account. Verify the server address and port in a " +
            "browser and ensure its HTTPS certificate is trusted by Android. Plain HTTP " +
            "connections can be blocked by Android. Then retry synchronization."
    else -> null
}

@Composable
private fun ActionIconButton(
    icon: ImageVector,
    description: String,
    testTag: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(testTag)
    ) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun FormatButton(
    icon: ImageVector,
    action: MarkdownFormatAction,
    editor: MarkdownEditText?,
    description: String,
    tag: String
) {
    IconButton(
        onClick = {
            editor?.applyFormat(action)
            // Tapping a Compose button moves focus away from the embedded editor, which closes
            // the keyboard. Hand focus back so formatting does not interrupt typing.
            editor?.focusForInput()
        },
        modifier = Modifier.testTag(tag)
    ) {
        Icon(icon, contentDescription = description)
    }
}

/**
 * Undo or redo. The framework editor has an undo buffer of its own, but it can only be reached
 * with a hardware keyboard, so the writer needs a control that a phone can actually reach.
 */
@Composable
private fun EditorHistoryButton(
    icon: ImageVector,
    description: String,
    testTag: String,
    enabled: Boolean,
    editor: MarkdownEditText?,
    onClick: () -> Unit
) {
    IconButton(
        onClick = {
            onClick()
            // Like formatting, this moves focus out of the embedded editor. Hand it back.
            editor?.focusForInput()
        },
        enabled = enabled,
        modifier = Modifier.testTag(testTag)
    ) {
        Icon(icon, contentDescription = description)
    }
}
