package org.qownnotes.mobile

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.nextcloud.android.sso.AccountImporter
import com.nextcloud.android.sso.exceptions.AccountImportCancelledException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.ResolvedNoteLink
import org.qownnotes.mobile.core.resolveInternalNoteLink
import org.qownnotes.mobile.markdown.MarkdownRenderer

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QOwnNotesApp(onImportAccount = ::importAccount) }
    }

    fun importAccount() {
        applicationComponent().beginAccountImport()
        runCatching { AccountImporter.pickNewAccount(this) }
            .onFailure(::handleImportFailure)
    }

    @Deprecated("Required by Nextcloud SSO 1.3.x")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        runCatching {
            AccountImporter.onActivityResult(requestCode, resultCode, data, this) { account ->
                lifecycleScope.launch {
                    try {
                        applicationComponent().importAccount(account)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        applicationComponent().reportImportError(error)
                    }
                }
            }
        }.onFailure(::handleImportFailure)
    }

    private fun applicationComponent() = (applicationContext as QOwnNotesApplication).component

    private fun handleImportFailure(error: Throwable) {
        if (error is AccountImportCancelledException) {
            applicationComponent().cancelAccountImport()
        } else {
            applicationComponent().reportImportError(error)
        }
    }
}

@Composable
fun QOwnNotesApp(onImportAccount: () -> Unit = {}) {
    val application = LocalContext.current.applicationContext as QOwnNotesApplication
    QOwnNotesTheme { NotesNavigation(application.component, onImportAccount) }
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
private fun NotesNavigation(component: ApplicationComponent, onImportAccount: () -> Unit) {
    val accounts by component.accountRepository.observeAccounts()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val importState by component.importState.collectAsStateWithLifecycle()
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeading by rememberSaveable { mutableStateOf<String?>(null) }
    var navigationRequest by rememberSaveable { mutableStateOf(0) }
    var noteHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }

    LaunchedEffect(accounts, selectedAccountId) {
        if (accounts.isNotEmpty() && accounts.none { it.id == selectedAccountId }) {
            selectedAccountId = accounts.first().id
        }
    }
    BackHandler(enabled = selectedNoteId != null) {
        selectedNoteId = noteHistory.lastOrNull()
        noteHistory = noteHistory.dropLast(1)
        selectedHeading = null
        navigationRequest++
    }

    val activeAccountId = accounts.firstOrNull { it.id == selectedAccountId }?.id
        ?: accounts.firstOrNull()?.id
    val noteId = selectedNoteId
    if (noteId != null) {
        NoteDetailScreen(
            component = component,
            localId = noteId,
            heading = selectedHeading,
            navigationRequest = navigationRequest,
            onOpen = { destination ->
                if (noteId != destination.localId) noteHistory = noteHistory + noteId
                selectedNoteId = destination.localId
                selectedHeading = destination.heading
                navigationRequest++
            }
        )
    } else if (accounts.isEmpty()) {
        AccountOnboarding(importState, onImportAccount)
    } else {
        NoteListScreen(
            component = component,
            accounts = accounts,
            accountId = requireNotNull(activeAccountId),
            importState = importState,
            onSelectAccount = { selectedAccountId = it },
            onImportAccount = onImportAccount,
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
private fun AccountOnboarding(state: SyncUiState, onImportAccount: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
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
        Button(onClick = onImportAccount, enabled = state !is SyncUiState.Refreshing) {
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
    onOpen: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val notesFlow = remember(accountId, query) {
        if (accountId.isBlank()) {
            flowOf(emptyList())
        } else {
            component.noteRepository.searchNotes(accountId, query)
        }
    }
    val notes by notesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val syncStates by component.syncStates.collectAsStateWithLifecycle()
    val syncState = syncStates[accountId] ?: SyncUiState.Idle
    val account = accounts.first { it.id == accountId }

    LaunchedEffect(accountId) { component.refresh(accountId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(account.displayName) },
                actions = {
                    if (accounts.size > 1) {
                        TextButton(onClick = {
                            val next = (accounts.indexOf(account) + 1) % accounts.size
                            onSelectAccount(accounts[next].id)
                        }) { Text("Switch") }
                    }
                    TextButton(onClick = onImportAccount) { Text("Add") }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (importState is SyncUiState.Failed) {
                Text(
                    importState.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search title and content") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )
            SyncStatus(syncState, refresh = {
                component.refresh(accountId)
            }, reconnect = onImportAccount)
            if (notes.isEmpty()) {
                Text(
                    if (query.isBlank()) "No cached notes" else "No matching notes",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.titleMedium
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(notes, key = Note::localId) { note ->
                        Column(
                            modifier =
                            Modifier.fillMaxWidth().clickable { onOpen(note.localId) }
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
}

@Composable
private fun SyncStatus(state: SyncUiState, refresh: suspend () -> Unit, reconnect: () -> Unit) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (state) {
            SyncUiState.Idle -> Text(
                "Available offline",
                style = MaterialTheme.typography.labelMedium
            )
            SyncUiState.Refreshing -> CircularProgressIndicator()
            is SyncUiState.Failed -> Text(state.message, color = MaterialTheme.colorScheme.error)
            is SyncUiState.AuthenticationRequired ->
                Text(state.message, color = MaterialTheme.colorScheme.error)
            is SyncUiState.AccountRemoved ->
                Text(state.message, color = MaterialTheme.colorScheme.error)
        }
        val reconnectRequired = state is SyncUiState.AuthenticationRequired ||
            state is SyncUiState.AccountRemoved
        TextButton(
            onClick = {
                if (reconnectRequired) reconnect() else scope.launch { refresh() }
            },
            enabled = state !is SyncUiState.Refreshing
        ) {
            Text(if (reconnectRequired) "Reconnect" else "Refresh")
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
    var pendingHeading by remember(localId, heading, navigationRequest) { mutableStateOf(heading) }
    var loadRemoteImages by remember(localId) { mutableStateOf(false) }
    val hasRemoteImages = remember(note?.content) {
        component.markdownRenderer.hasRemoteImages(note?.content.orEmpty())
    }
    LaunchedEffect(localId, navigationRequest) {
        if (heading == null) scrollState.scrollTo(0)
    }
    Scaffold(topBar = { TopAppBar(title = { Text(note?.title ?: "Note") }) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(scrollState)) {
            if (hasRemoteImages && !loadRemoteImages) {
                TextButton(onClick = { loadRemoteImages = true }) { Text("Load remote images") }
            }
            AndroidView(
                factory = { context -> AppCompatTextView(context) },
                update = { view ->
                    val source = note
                    component.markdownRenderer.render(
                        view = view,
                        markdown = source?.content.orEmpty(),
                        resolveInternalLink = { link ->
                            source?.let { resolveInternalNoteLink(it, accountNotes, link) }
                        },
                        onInternalLink = onOpen,
                        heading = if (source != null) pendingHeading else null,
                        onHeadingPositioned = { top ->
                            if (pendingHeading != null) {
                                pendingHeading = null
                                if (top != null) scope.launch { scrollState.scrollTo(top) }
                            }
                        },
                        loadRemoteImages = loadRemoteImages
                    )
                },
                modifier = Modifier.fillMaxWidth().padding(20.dp)
            )
        }
    }
}
