package org.qownnotes.mobile

import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import org.qownnotes.mobile.core.ResolvedNoteLink
import org.qownnotes.mobile.core.SyncState
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
        enableEdgeToEdge()
        setContent {
            QOwnNotesApp(
                onImportAccount = { importAccount() },
                onReconnectAccount = ::reconnectAccount
            )
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(RECONNECT_ACCOUNT_ID, reconnectAccountId)
        super.onSaveInstanceState(outState)
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
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeading by rememberSaveable { mutableStateOf<String?>(null) }
    var navigationRequest by rememberSaveable { mutableStateOf(0) }
    var noteHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(accounts, selectedAccountId) {
        val loadedAccounts = accounts ?: return@LaunchedEffect
        if (loadedAccounts.isNotEmpty() && loadedAccounts.none { it.id == selectedAccountId }) {
            selectedAccountId = loadedAccounts.first().id
        }
    }
    BackHandler(enabled = selectedNoteId != null) {
        selectedNoteId = noteHistory.lastOrNull()
        noteHistory = noteHistory.dropLast(1)
        selectedHeading = null
        navigationRequest++
    }

    val loadedAccounts = accounts
    val activeAccountId = loadedAccounts?.firstOrNull { it.id == selectedAccountId }?.id
        ?: loadedAccounts?.firstOrNull()?.id
    val noteId = selectedNoteId
    if (loadedAccounts == null) {
        LoadingScreen()
    } else if (noteId != null) {
        key(noteId) {
            NoteDetailScreen(
                component = component,
                localId = noteId,
                heading = selectedHeading,
                navigationRequest = navigationRequest,
                onBackToList = {
                    selectedNoteId = null
                    selectedHeading = null
                    noteHistory = emptyList()
                },
                onOpen = { destination ->
                    if (noteId != destination.localId) noteHistory = noteHistory + noteId
                    selectedNoteId = destination.localId
                    selectedHeading = destination.heading
                    navigationRequest++
                }
            )
        }
    } else if (loadedAccounts.isEmpty()) {
        AccountOnboarding(importState, onImportAccount)
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
                    navigationRequest++
                }
            },
            onOpen = {
                noteHistory = emptyList()
                selectedNoteId = it
                selectedHeading = null
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
private fun AccountOnboarding(state: SyncUiState, onImportAccount: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp).testTag("onboarding"),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Your Nextcloud notes, offline", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Choose an account from the Nextcloud Files app to download and cache your notes.",
            modifier = Modifier.padding(vertical = 20.dp)
        )
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

@OptIn(ExperimentalMaterial3Api::class)
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

    LaunchedEffect(accountId) { component.refresh(accountId) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            account.displayName,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .testTag("note-list-actions")
                ) {
                    if (accounts.size > 1) {
                        TextButton(onClick = {
                            val next = (accounts.indexOf(account) + 1) % accounts.size
                            onSelectAccount(accounts[next].id)
                        }, modifier = Modifier.testTag("switch-account")) { Text("Switch") }
                    }
                    TextButton(
                        onClick = { onCreate(accountId) },
                        modifier = Modifier.testTag("create-note")
                    ) { Text("New") }
                    TextButton(
                        onClick = { showRemoveConfirmation = true },
                        modifier = Modifier.testTag("remove-account")
                    ) { Text("Remove") }
                    TextButton(
                        onClick = onImportAccount,
                        modifier = Modifier.testTag("add-account")
                    ) { Text("Add") }
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
                        Column(
                            modifier =
                            Modifier.fillMaxWidth().testTag("note-${note.localId}")
                                .clickable { onOpen(note.localId) }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                note.category.ifBlank { "Uncategorized" },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
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
            is SyncUiState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDetailScreen(
    component: ApplicationComponent,
    localId: String,
    heading: String?,
    navigationRequest: Int,
    onBackToList: () -> Unit,
    onOpen: (ResolvedNoteLink) -> Unit
) {
    val note by component.noteRepository.observeNote(localId)
        .collectAsStateWithLifecycle(initialValue = null)
    val accountNotesFlow = remember(note?.accountId) {
        note?.accountId?.let(component.noteRepository::observeNotes) ?: flowOf(emptyList())
    }
    val accountNotes by accountNotesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    var editing by rememberSaveable(localId) { mutableStateOf(false) }
    var draft by remember(localId) { mutableStateOf<String?>(null) }
    var selectionStart by rememberSaveable(localId) { mutableStateOf(0) }
    var selectionEnd by rememberSaveable(localId) { mutableStateOf(0) }
    var editor by remember { mutableStateOf<MarkdownEditText?>(null) }
    var editorScrollY by remember(localId) { mutableIntStateOf(0) }
    var editorScrollRange by remember(localId) { mutableIntStateOf(0) }
    var editorViewportHeight by remember(localId) { mutableIntStateOf(0) }
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
    LaunchedEffect(editing, editor) {
        val view = editor ?: return@LaunchedEffect
        if (!editing) return@LaunchedEffect
        repeat(2) { withFrameNanos { } }
        view.bringPointIntoView(selectionStart)
    }
    val latestDraft by rememberUpdatedState(draft)
    val latestNote by rememberUpdatedState(note)
    val latestEditing by rememberUpdatedState(editing)
    LaunchedEffect(note?.localId, note?.content) {
        if (draft == null || !editing) {
            draft = note?.let { component.draft(localId, it.content) }
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
                    }
                )
                val current = note
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .testTag("note-actions")
                ) {
                    CompactActionButton(
                        label = "A-",
                        description = "Decrease note text size",
                        testTag = "decrease-note-text-size",
                        enabled = NoteTextSize.canDecrease(noteTextSizeSp),
                        onClick = component.settings::decreaseNoteTextSize
                    )
                    CompactActionButton(
                        label = "A+",
                        description = "Increase note text size",
                        testTag = "increase-note-text-size",
                        enabled = NoteTextSize.canIncrease(noteTextSizeSp),
                        onClick = component.settings::increaseNoteTextSize
                    )
                    if (!editing) {
                        TextButton(
                            onClick = {
                                finding = !finding
                                if (!finding) {
                                    findQuery = ""
                                    currentMatch = 0
                                }
                            },
                            modifier = Modifier.testTag("find-in-note")
                        ) { Text("Find") }
                    }
                    if (editing) {
                        TextButton(
                            onClick = {
                                val source = draft
                                if (source != null) {
                                    scope.launch {
                                        if (component.saveDraft(localId, source)) editing = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag("finish-editing")
                        ) { Text("Done") }
                    } else if (current != null && !current.readOnly && !hasEncryptedContent) {
                        if (current.syncState == SyncState.FAILED) {
                            TextButton(
                                onClick = { scope.launch { component.retryNote(localId) } },
                                modifier = Modifier.testTag("retry-note")
                            ) { Text("Retry") }
                        }
                        if (current.syncState != SyncState.FAILED || current.remoteId != null) {
                            TextButton(
                                onClick = {
                                    scope.launch {
                                        component.beginEditing(localId)?.let { editable ->
                                            draft = editable.content
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
                            ) { Text("Edit") }
                        }
                    } else if (current?.readOnly == true) {
                        Text("Read only", modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    ) { padding ->
        if (editing) {
            Column(modifier = Modifier.fillMaxSize().padding(padding).imePadding()) {
                note?.lastSyncError?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .testTag("format-toolbar")
                ) {
                    // First in the row, so stepping back does not require scrolling the toolbar.
                    EditorHistoryButton("Undo", "undo-edit", canUndo, editor) {
                        editorBinding?.undo()
                    }
                    EditorHistoryButton("Redo", "redo-edit", canRedo, editor) {
                        editorBinding?.redo()
                    }
                    FormatButton("B", MarkdownFormatAction.BOLD, editor)
                    FormatButton("I", MarkdownFormatAction.ITALIC, editor)
                    FormatButton("S", MarkdownFormatAction.STRIKETHROUGH, editor)
                    FormatButton("Code", MarkdownFormatAction.CODE, editor)
                    FormatButton("Link", MarkdownFormatAction.LINK, editor)
                    FormatButton("H", MarkdownFormatAction.HEADING, editor)
                    FormatButton("List", MarkdownFormatAction.BULLET, editor)
                    FormatButton("1.", MarkdownFormatAction.NUMBERED, editor)
                    FormatButton("Task", MarkdownFormatAction.TASK, editor)
                    FormatButton("Quote", MarkdownFormatAction.QUOTE, editor)
                }
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    AndroidView(
                        factory = { context ->
                            MarkdownEditText(context).also { view ->
                                fun updateScrollMetrics() {
                                    editorScrollY = view.scrollY
                                    editorViewportHeight = view.height
                                    editorScrollRange = (
                                        (view.layout?.height ?: 0) + view.totalPaddingTop +
                                            view.totalPaddingBottom - view.height
                                        ).coerceAtLeast(0)
                                }
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
                                view.setOnScrollChangeListener { _, _, _, _, _ ->
                                    updateScrollMetrics()
                                }
                                view.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                                    updateScrollMetrics()
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
                            view.setOnScrollChangeListener(null)
                            view.releaseInputFocus()
                        },
                        modifier = Modifier.fillMaxSize().testTag("markdown-editor")
                    )
                    EditorFastScroller(
                        editor = editor,
                        scrollY = editorScrollY,
                        scrollRange = editorScrollRange,
                        viewportHeight = editorViewportHeight,
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
                    note?.lastSyncError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
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
}

@Composable
private fun EditorFastScroller(
    editor: MarkdownEditText?,
    scrollY: Int,
    scrollRange: Int,
    viewportHeight: Int,
    modifier: Modifier = Modifier
) {
    if (editor == null || scrollRange <= 0 || viewportHeight <= 0) return
    BoxWithConstraints(
        modifier = modifier.fillMaxHeight().width(32.dp)
            .semantics { contentDescription = "Editor fast scroll" }
            .testTag("editor-fast-scroll")
    ) {
        val density = LocalDensity.current
        val trackHeight = constraints.maxHeight.toFloat()
        val contentHeight = viewportHeight + scrollRange
        val thumbHeight = maxOf(
            with(density) { 48.dp.toPx() },
            trackHeight * viewportHeight / contentHeight
        ).coerceAtMost(trackHeight)
        val travel = trackHeight - thumbHeight
        val thumbOffset = if (scrollRange == 0) 0F else travel * scrollY / scrollRange
        fun scrollTo(pointerY: Float) {
            val fraction = ((pointerY - thumbHeight / 2F) / travel).coerceIn(0F, 1F)
            editor.scrollTo(0, (scrollRange * fraction).roundToInt())
        }
        Box(
            modifier = Modifier.fillMaxSize().pointerInput(
                editor,
                scrollRange,
                trackHeight,
                thumbHeight
            ) {
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
                modifier = Modifier.align(Alignment.TopEnd)
                    .offset { IntOffset(0, thumbOffset.roundToInt()) }
                    .width(6.dp)
                    .height(with(density) { thumbHeight.toDp() })
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.65F))
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
        CompactActionButton(
            label = "<",
            description = "Previous match",
            testTag = "find-previous",
            enabled = matchCount > 0,
            onClick = onPrevious
        )
        CompactActionButton(
            label = ">",
            description = "Next match",
            testTag = "find-next",
            enabled = matchCount > 0,
            onClick = onNext
        )
        CompactActionButton(
            label = "X",
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

/**
 * A button labelled with punctuation or a single letter, such as `A+` or `>`. A screen reader
 * cannot announce those usefully, so every caller supplies a description.
 */
@Composable
private fun CompactActionButton(
    label: String,
    description: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.testTag(testTag).semantics { contentDescription = description }
    ) { Text(label) }
}

@Composable
private fun FormatButton(label: String, action: MarkdownFormatAction, editor: MarkdownEditText?) {
    TextButton(
        onClick = {
            editor?.applyFormat(action)
            // Tapping a Compose button moves focus away from the embedded editor, which closes
            // the keyboard. Hand focus back so formatting does not interrupt typing.
            editor?.focusForInput()
        }
    ) { Text(label) }
}

/**
 * Undo or redo. The framework editor has an undo buffer of its own, but it can only be reached
 * with a hardware keyboard, so the writer needs a control that a phone can actually reach.
 */
@Composable
private fun EditorHistoryButton(
    label: String,
    testTag: String,
    enabled: Boolean,
    editor: MarkdownEditText?,
    onClick: () -> Unit
) {
    TextButton(
        onClick = {
            onClick()
            // Like formatting, this moves focus out of the embedded editor. Hand it back.
            editor?.focusForInput()
        },
        enabled = enabled,
        modifier = Modifier.testTag(testTag)
    ) { Text(label) }
}
