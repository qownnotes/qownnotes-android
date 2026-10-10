package org.qownnotes.mobile

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.format.Formatter
import android.util.TypedValue
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatTextView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditOff
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TextDecrease
import androidx.compose.material.icons.filled.TextIncrease
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewKanban
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
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
import java.text.DateFormat
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qownnotes.mobile.BuildConfig
import org.qownnotes.mobile.core.Account
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteCategories
import org.qownnotes.mobile.core.NoteConflict
import org.qownnotes.mobile.core.NoteExcerpt
import org.qownnotes.mobile.core.NoteFolder
import org.qownnotes.mobile.core.NoteFolderScope
import org.qownnotes.mobile.core.NoteFolders
import org.qownnotes.mobile.core.NoteListItem
import org.qownnotes.mobile.core.NoteMergeField
import org.qownnotes.mobile.core.NoteNames
import org.qownnotes.mobile.core.NoteSearchScope
import org.qownnotes.mobile.core.NoteSettings
import org.qownnotes.mobile.core.NoteSortOrder
import org.qownnotes.mobile.core.NoteTag
import org.qownnotes.mobile.core.NoteTagAvailability
import org.qownnotes.mobile.core.NoteTagState
import org.qownnotes.mobile.core.NoteTags
import org.qownnotes.mobile.core.NoteVersionSnapshot
import org.qownnotes.mobile.core.RemoteNoteVersion
import org.qownnotes.mobile.core.ResolvedNoteLink
import org.qownnotes.mobile.core.SharedText
import org.qownnotes.mobile.core.SyncState
import org.qownnotes.mobile.core.TrashedNote
import org.qownnotes.mobile.core.findTextMatches
import org.qownnotes.mobile.core.mergeNoteConflict
import org.qownnotes.mobile.core.parseBookmarksSource
import org.qownnotes.mobile.core.resolveInternalNoteLink
import org.qownnotes.mobile.markdown.MarkdownEditText
import org.qownnotes.mobile.markdown.MarkdownEditorBinding
import org.qownnotes.mobile.markdown.MarkdownFormatAction
import org.qownnotes.mobile.markdown.MarkdownRenderer
import org.qownnotes.mobile.markdown.NoteSearchColors
import org.qownnotes.mobile.markdown.NoteTextSize
import org.qownnotes.mobile.markdown.highlightNoteSearchMatches
import org.qownnotes.mobile.markdown.noteSearchMatchTop
import org.qownnotes.mobile.markdown.supportsMarkdownSourceHighlighting
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
internal fun QOwnNotesTheme(content: @Composable () -> Unit) {
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

/**
 * The thread the user interface lives on, named rather than inherited.
 *
 * Compose hands an effect and a remembered scope the dispatcher of the composition they belong to.
 * In the running application that is the main thread, but the interceptor a Compose test installs
 * is not a dispatcher at all, so a call that waits for the database resumes on whichever thread
 * finished it, which is one of Room's executor threads. Everything that follows then runs there:
 * the state writes, the snapshot notification Compose sends on every resumption, and any call into
 * a hosted Android view. None of that may leave the thread the composition and its views live on,
 * so every coroutine that ends in the user interface names the dispatcher it needs.
 */
internal val UiDispatcher get() = Dispatchers.Main.immediate

@Composable
private fun NotesNavigation(
    component: ApplicationComponent,
    onImportAccount: () -> Unit,
    onReconnectAccount: (String) -> Unit
) {
    val accounts by component.accountRepository.observeAccounts()
        .collectAsStateWithLifecycle(initialValue = null as List<Account>?, context = UiDispatcher)
    val importState by component.importState.collectAsStateWithLifecycle(context = UiDispatcher)
    val scope = rememberCoroutineScope { UiDispatcher }
    var selectedAccountId by rememberSaveable { mutableStateOf<String?>(null) }
    var observedAccountIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedHeading by rememberSaveable { mutableStateOf<String?>(null) }
    var editOnOpenNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    var navigationRequest by rememberSaveable { mutableStateOf(0) }
    // The note-list search that was active when a note was opened from the list, so the opened
    // note starts by finding the same text. Every other way of opening a note clears it.
    var findOnOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var noteHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    var managingAccounts by rememberSaveable { mutableStateOf(false) }
    var browsingBookmarks by rememberSaveable { mutableStateOf(false) }
    val noteListStateHolder = rememberSaveableStateHolder()
    val bookmarksStateHolder = rememberSaveableStateHolder()

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
        findOnOpen = null
        navigationRequest++
    }
    BackHandler(enabled = managingAccounts && selectedNoteId == null) {
        managingAccounts = false
    }
    BackHandler(enabled = browsingBookmarks && selectedNoteId == null) {
        browsingBookmarks = false
    }

    val loadedAccounts = accounts
    val activeAccountId = loadedAccounts?.firstOrNull { it.id == selectedAccountId }?.id
        ?: loadedAccounts?.firstOrNull()?.id
    val pendingShare by component.pendingShare.collectAsStateWithLifecycle(context = UiDispatcher)
    val widgetRequest by component.widgetRequest.collectAsStateWithLifecycle(context = UiDispatcher)
    // Text another application shared becomes a note in the account that is being looked at, and
    // that note is opened, so the share ends where the user can see and correct it. A share that
    // arrives before any account exists waits here until onboarding has produced one.
    LaunchedEffect(pendingShare, activeAccountId, managingAccounts) {
        if (pendingShare == null || activeAccountId == null || managingAccounts) {
            return@LaunchedEffect
        }
        val shared = component.takePendingShare() ?: return@LaunchedEffect
        // Taking the share clears the state this effect is keyed on, so the effect is on its way
        // to being cancelled and restarted from here on. Writing the note and opening it belongs
        // to the screen rather than to this run of the effect, and the screen's scope survives.
        scope.launch {
            val note = component.createSharedNote(activeAccountId, shared)
            browsingBookmarks = false
            noteHistory = emptyList()
            selectedNoteId = note.localId
            selectedHeading = null
            findOnOpen = null
            navigationRequest++
        }
    }
    LaunchedEffect(widgetRequest, loadedAccounts) {
        if (widgetRequest == null) return@LaunchedEffect
        val availableAccounts = loadedAccounts ?: return@LaunchedEffect
        val request = component.takeWidgetRequest() ?: return@LaunchedEffect
        // Taking the request restarts this effect. Navigation belongs to the screen scope so a
        // repository suspension cannot cancel it between accepting the tap and opening the note.
        scope.launch {
            when (request) {
                is WidgetRequest.OpenNoteList -> {
                    selectedAccountId = request.accountId.takeIf { requested ->
                        availableAccounts.any { it.id == requested }
                    } ?: availableAccounts.firstOrNull()?.id
                    browsingBookmarks = false
                    managingAccounts = false
                    noteHistory = emptyList()
                    selectedNoteId = null
                    selectedHeading = null
                    editOnOpenNoteId = null
                    findOnOpen = null
                    navigationRequest++
                }
                is WidgetRequest.OpenNote -> if (
                    component.noteRepository.get(request.localId) != null
                ) {
                    browsingBookmarks = false
                    managingAccounts = false
                    noteHistory = emptyList()
                    selectedNoteId = request.localId
                    selectedHeading = null
                    editOnOpenNoteId = null
                    findOnOpen = null
                    navigationRequest++
                }
                is WidgetRequest.CreateNote -> {
                    val accountId = request.accountId.takeIf { requested ->
                        availableAccounts.any { it.id == requested }
                    } ?: availableAccounts.firstOrNull()?.id ?: return@launch
                    val note = component.createNote(accountId)
                    browsingBookmarks = false
                    managingAccounts = false
                    noteHistory = emptyList()
                    selectedNoteId = note.localId
                    selectedHeading = null
                    editOnOpenNoteId = note.localId
                    findOnOpen = null
                    navigationRequest++
                }
            }
        }
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
                initialFindQuery = findOnOpen,
                onInitialEditStarted = { editOnOpenNoteId = null },
                onBackToList = {
                    selectedNoteId = null
                    selectedHeading = null
                    editOnOpenNoteId = null
                    findOnOpen = null
                    noteHistory = emptyList()
                },
                onOpen = { destination ->
                    if (noteId != destination.localId) noteHistory = noteHistory + noteId
                    selectedNoteId = destination.localId
                    selectedHeading = destination.heading
                    editOnOpenNoteId = null
                    findOnOpen = null
                    navigationRequest++
                }
            )
        }
    } else if (loadedAccounts.isEmpty()) {
        AccountOnboarding(importState, pendingShare != null, onImportAccount)
    } else if (managingAccounts) {
        AccountManagementScreen(
            component = component,
            accounts = loadedAccounts,
            onBack = { managingAccounts = false },
            onImportAccount = onImportAccount,
            onRemoveAccount = { accountId ->
                scope.launch {
                    component.removeLocalData(accountId)
                    if (selectedAccountId == accountId) selectedAccountId = null
                }
            }
        )
    } else if (browsingBookmarks) {
        bookmarksStateHolder.SaveableStateProvider("bookmarks-$activeAccountId") {
            key(activeAccountId) {
                BookmarkScreen(
                    component = component,
                    accountId = requireNotNull(activeAccountId),
                    onBack = { browsingBookmarks = false },
                    onOpenSourceNote = { localId ->
                        noteHistory = emptyList()
                        selectedNoteId = localId
                        selectedHeading = null
                        editOnOpenNoteId = null
                        findOnOpen = null
                        navigationRequest++
                    }
                )
            }
        }
    } else {
        noteListStateHolder.SaveableStateProvider("note-list") {
            NoteListScreen(
                component = component,
                accounts = loadedAccounts,
                accountId = requireNotNull(activeAccountId),
                importState = importState,
                onSelectAccount = { selectedAccountId = it },
                onImportAccount = onImportAccount,
                onReconnectAccount = onReconnectAccount,
                onManageAccounts = { managingAccounts = true },
                onOpenBookmarks = { browsingBookmarks = true },
                onCreate = { accountId, category, name ->
                    scope.launch {
                        val note = if (name == null) {
                            component.createNote(accountId, category)
                        } else {
                            component.createNamedNote(accountId, name, category)
                        }
                        noteHistory = emptyList()
                        selectedNoteId = note.localId
                        selectedHeading = null
                        editOnOpenNoteId = note.localId
                        findOnOpen = null
                        navigationRequest++
                    }
                },
                onOpen = { localId, searchText ->
                    noteHistory = emptyList()
                    selectedNoteId = localId
                    selectedHeading = null
                    editOnOpenNoteId = null
                    findOnOpen = searchText
                    navigationRequest++
                }
            )
        }
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
        Text(
            stringResource(R.string.onboarding_title),
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            stringResource(R.string.onboarding_description),
            modifier = Modifier.padding(vertical = 20.dp)
        )
        // A note needs an account to belong to. Say why the shared text is not a note yet rather
        // than leaving the sharer in front of an unexplained onboarding screen.
        if (sharedTextWaiting) {
            Text(
                stringResource(R.string.onboarding_shared_text_waiting),
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
            Text(stringResource(R.string.action_add_nextcloud_account))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountManagementScreen(
    component: ApplicationComponent,
    accounts: List<Account>,
    onBack: () -> Unit,
    onImportAccount: () -> Unit,
    onRemoveAccount: (String) -> Unit
) {
    var settingsAccount by remember { mutableStateOf<Account?>(null) }
    var removalAccount by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.manage_accounts)) },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("close-manage-accounts")
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                }
            )
        },
        modifier = Modifier.fillMaxSize().testTag("account-management")
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(accounts, key = Account::id) { account ->
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp)
                        .testTag("managed-account-${account.id}")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatar(component, account, Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(account.displayName, style = MaterialTheme.typography.titleMedium)
                            Text(
                                account.serverUrl,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { settingsAccount = account },
                            modifier = Modifier.testTag("account-settings-${account.id}")
                        ) { Text(stringResource(R.string.note_settings_title)) }
                        TextButton(
                            onClick = { removalAccount = account },
                            modifier = Modifier.testTag("remove-account-${account.id}")
                        ) { Text(stringResource(R.string.action_remove)) }
                    }
                }
            }
            item {
                Button(
                    onClick = onImportAccount,
                    modifier = Modifier.fillMaxWidth().testTag("add-managed-account")
                ) { Text(stringResource(R.string.action_add_nextcloud_account)) }
            }
        }
    }

    settingsAccount?.let { account ->
        AccountNoteSettingsDialog(
            component = component,
            account = account,
            onDismiss = { settingsAccount = null }
        )
    }
    removalAccount?.let { account ->
        RemoveAccountDialog(
            account = account,
            onDismiss = { removalAccount = null },
            onRemove = {
                removalAccount = null
                onRemoveAccount(account.id)
            }
        )
    }
}

@Composable
private fun AccountNoteSettingsDialog(
    component: ApplicationComponent,
    account: Account,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope { UiDispatcher }
    var settings by remember(account.id) { mutableStateOf<NoteSettings?>(null) }
    var notesPath by rememberSaveable(account.id) { mutableStateOf("") }
    var fileExtension by rememberSaveable(account.id) { mutableStateOf("") }
    var error by remember(account.id) { mutableStateOf<String?>(null) }
    var saving by remember(account.id) { mutableStateOf(false) }
    val loadFailedMessage = stringResource(R.string.note_settings_load_failed)
    val saveFailedMessage = stringResource(R.string.note_settings_save_failed)

    LaunchedEffect(account.id) {
        try {
            val loaded = withContext(UiDispatcher) { component.noteSettings(account.id) }
            settings = loaded
            notesPath = loaded.notesPath
            fileExtension = loaded.fileSuffix.removePrefix(".")
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: loadFailedMessage
        }
    }

    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
        title = { Text(stringResource(R.string.note_settings_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    account.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                if (settings == null && error == null) {
                    DialogLoadingIndicator("account-settings-loading")
                } else {
                    OutlinedTextField(
                        value = notesPath,
                        onValueChange = { notesPath = it },
                        label = { Text(stringResource(R.string.note_settings_note_folder)) },
                        leadingIcon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        singleLine = true,
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().testTag("notes-path")
                    )
                    OutlinedTextField(
                        value = fileExtension,
                        onValueChange = { fileExtension = it.removePrefix(".") },
                        label = { Text(stringResource(R.string.note_settings_file_extension)) },
                        leadingIcon = {
                            Icon(
                                Icons.AutoMirrored.Filled.InsertDriveFile,
                                contentDescription = null
                            )
                        },
                        prefix = { Text(".") },
                        singleLine = true,
                        enabled = !saving,
                        supportingText = {
                            Text(stringResource(R.string.note_settings_file_extension_hint))
                        },
                        modifier = Modifier.fillMaxWidth().testTag("file-extension")
                    )
                    DialogNotice(stringResource(R.string.note_settings_folder_warning))
                }
                error?.let {
                    DialogErrorPanel(message = it, messageTag = "account-settings-error")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    saving = true
                    error = null
                    scope.launch {
                        try {
                            component.updateNoteSettings(
                                account.id,
                                requireNotNull(settings),
                                notesPath,
                                fileExtension
                            )
                            onDismiss()
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            error = failure.message ?: saveFailedMessage
                            saving = false
                        }
                    }
                },
                enabled = settings != null && notesPath.isNotBlank() &&
                    fileExtension.isNotBlank() && !saving,
                modifier = Modifier.testTag("save-account-settings")
            ) {
                Text(
                    stringResource(if (saving) R.string.action_saving else R.string.action_save)
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        modifier = Modifier.testTag("account-settings-dialog")
    )
}

@Composable
private fun RemoveAccountDialog(account: Account, onDismiss: () -> Unit, onRemove: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.PersonRemove, contentDescription = null) },
        title = { Text(stringResource(R.string.remove_account_title)) },
        text = {
            Text(
                stringResource(R.string.remove_account_message, account.displayName)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onRemove,
                colors = ButtonDefaults.textButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                modifier = Modifier.testTag("confirm-remove-account")
            ) { Text(stringResource(R.string.action_remove)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
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
    onManageAccounts: () -> Unit,
    onOpenBookmarks: () -> Unit,
    /** Creates a note in an account and category, with the given name or an automatic one. */
    onCreate: (String, String, String?) -> Unit,
    /** Opens a note with the active search text, if any, so the note can find it too. */
    onOpen: (String, String?) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchScope by rememberSaveable { mutableStateOf(NoteSearchScope.TITLE_AND_CONTENT) }
    var sortOrder by rememberSaveable { mutableStateOf(NoteSortOrder.LATEST_FIRST) }
    var searchFocused by remember { mutableStateOf(false) }
    var showSettings by rememberSaveable(accountId) { mutableStateOf(false) }
    var showDeckBrowser by rememberSaveable(accountId) { mutableStateOf(false) }
    val deckAvailableFlow = remember(accountId) { component.nextcloudDeckAvailable(accountId) }
    val deckAvailable by deckAvailableFlow.collectAsStateWithLifecycle(context = UiDispatcher)
    LaunchedEffect(accountId) { component.refreshDeckAvailability(accountId) }
    var showAppearance by rememberSaveable(accountId) { mutableStateOf(false) }
    var showDiagnostics by rememberSaveable(accountId) { mutableStateOf(false) }
    var diagnosticReport by remember(accountId) { mutableStateOf<String?>(null) }
    var showAbout by rememberSaveable(accountId) { mutableStateOf(false) }
    var accountMenuOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    var noteListMenuOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    var selectionMenuOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    var trashConfirmationIds by rememberSaveable(accountId) {
        mutableStateOf<List<String>?>(null)
    }
    var sortMenuOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    var storedFolderScope by remember(accountId) {
        mutableStateOf(component.settings.noteFolderScope(accountId))
    }
    val useSubfoldersFlow = remember(accountId) { component.settings.useSubfolders(accountId) }
    val useSubfolders by useSubfoldersFlow.collectAsStateWithLifecycle(context = UiDispatcher)
    // Without subfolders the account behaves like a QOwnNotes note folder with subfolders turned
    // off: only root notes are listed and created. The remembered folder is kept for later.
    val folderScope = if (useSubfolders) storedFolderScope else NoteFolderScope()
    val nestedFolders = component.nestedFolders
    // Expanded folder keys; the selected folder's ancestors start expanded so it is visible.
    var expandedFolders by rememberSaveable(accountId) {
        mutableStateOf(
            NoteFolders.key(folderScope.path).split('/').dropLast(1)
                .runningReduce { parent, segment -> "$parent/$segment" }
        )
    }
    var searchAllFolders by rememberSaveable(accountId) { mutableStateOf(false) }
    val folderDrawerState = rememberDrawerState(DrawerValue.Closed)
    var selectedNoteIds by rememberSaveable(accountId) { mutableStateOf(emptyList<String>()) }
    var trashState by remember(accountId) {
        mutableStateOf<ArchiveLoadState<TrashedNote>>(ArchiveLoadState.Idle)
    }
    var trashToRestore by remember(accountId) { mutableStateOf<TrashedNote?>(null) }
    var trashRequestId by remember(accountId) { mutableIntStateOf(0) }
    val allNotesFlow = remember(accountId) { component.noteRepository.observeNotes(accountId) }
    val allNotes by allNotesFlow
        .collectAsStateWithLifecycle(
            initialValue = null as List<NoteListItem>?,
            context = UiDispatcher
        )
    // Searching all folders only widens an actual search; the list itself stays in its folder.
    val listedFolder = folderScope.takeUnless {
        useSubfolders && searchAllFolders && query.isNotBlank()
    }
    val notesFlow = remember(accountId, query, searchScope, sortOrder, listedFolder) {
        if (accountId.isBlank()) {
            flowOf(emptyList())
        } else {
            component.noteRepository.searchNotes(
                accountId,
                query,
                searchScope,
                sortOrder,
                listedFolder,
                nestedFolders
            )
        }
    }
    val notes by notesFlow
        .collectAsStateWithLifecycle(
            initialValue = null as List<NoteListItem>?,
            context = UiDispatcher
        )
    val syncStates by component.syncStates.collectAsStateWithLifecycle(context = UiDispatcher)
    val syncState = syncStates[accountId] ?: SyncUiState.Idle
    val account = accounts.first { it.id == accountId }
    val scope = rememberCoroutineScope { UiDispatcher }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val trashLoadFailedMessage = stringResource(R.string.trash_load_failed)
    val trashRestoreFailedMessage = stringResource(R.string.trash_restore_failed)
    // Leaving search only gives the toolbar back. The typed query stays, so the filtered list and
    // the note actions above it remain usable, and the field's clear action is what empties it.
    //
    // Whether search owns the top bar is state of its own rather than a reading of input focus. A
    // field keeps its focus when the window loses and regains it, and a focus clear is declined
    // while the input method holds a session, which would strand the expanded bar with no way
    // back. Asking for the focus and the keyboard to go stays a courtesy on top of that.
    val leaveSearch = {
        keyboard?.hide()
        focusManager.clearFocus(force = true)
        searchFocused = false
    }
    val selectionActive = selectedNoteIds.isNotEmpty()
    val showNotePreview by component.settings.showNotePreview
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val swipeNoteActions by component.settings.swipeNoteActions
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val hideCreateButtonOnScroll by component.settings.hideCreateButtonOnScroll
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val compactNoteList by component.settings.compactNoteList
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val askForNewNoteName by component.settings.askForNewNoteName
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val showEditorToolbarLabels by component.settings.showEditorToolbarLabels
        .collectAsStateWithLifecycle(context = UiDispatcher)
    // Visibility is kept apart from the name because the field may report a last value change
    // while the dialog closes, which must not reopen it.
    var namingNewNote by rememberSaveable(accountId) { mutableStateOf(false) }
    var newNoteName by rememberSaveable(accountId) { mutableStateOf("") }
    val appearance by component.settings.appearance
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val headerContainer =
        appearance.headerColor?.let(::Color) ?: MaterialTheme.colorScheme.surface
    // Null keeps the theme's own content colors.
    val headerContent = appearance.headerColor?.let { Color(AppearanceColors.contentColor(it)) }
    val headerSecondary =
        appearance.headerColor?.let { Color(AppearanceColors.secondaryContentColor(it)) }
    StatusBarIconsFor(appearance.headerColor)
    val showCategoryFlow = remember(accountId) { component.settings.showCategory(accountId) }
    val showCategory by showCategoryFlow
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val bookmarksPathFlow = remember(accountId) { component.settings.bookmarksPath(accountId) }
    val bookmarksPath by bookmarksPathFlow.collectAsStateWithLifecycle(context = UiDispatcher)
    val folderTree = remember(allNotes, nestedFolders) {
        NoteFolders.tree(allNotes.orEmpty().map(NoteListItem::category), nestedFolders)
    }
    val tagStateFlow = remember(accountId) { component.observeNoteTags(accountId) }
    val tagState by tagStateFlow.collectAsStateWithLifecycle(
        initialValue = NoteTagState(),
        context = UiDispatcher
    )
    var tagFilter by rememberSaveable(accountId) { mutableStateOf(emptyList<String>()) }
    var tagFilterOpen by rememberSaveable(accountId) { mutableStateOf(false) }
    val selectedTagIds = remember(tagState, tagFilter) {
        NoteTags.withPaths(tagState.tags)
            .filter { (_, path) -> NoteTags.pathKey(path) in tagFilter }
            .mapTo(mutableSetOf()) { (tag, _) -> tag.id }
    }
    val tagFilterActive = tagFilter.isNotEmpty() &&
        tagState.availability == NoteTagAvailability.AVAILABLE
    val visibleNotes = remember(notes, tagState, selectedTagIds, tagFilterActive) {
        notes?.filter { note ->
            !tagFilterActive ||
                NoteTags.matches(
                    tagState.tagIdsByNote[NoteTags.keyOf(note)].orEmpty(),
                    selectedTagIds
                )
        }
    }
    val noteListState = key(accountId) { rememberLazyListState() }
    var createButtonVisible by remember(accountId) { mutableStateOf(true) }
    // List refreshes restore the action; only subsequent viewport movement controls visibility.
    LaunchedEffect(accountId, noteListState, visibleNotes) {
        withFrameNanos {}
        createButtonVisible = true
        var previousIndex = noteListState.firstVisibleItemIndex
        var previousOffset = noteListState.firstVisibleItemScrollOffset
        snapshotFlow {
            Pair(
                noteListState.firstVisibleItemIndex,
                noteListState.firstVisibleItemScrollOffset
            )
        }.collect { (index, offset) ->
            if (index > previousIndex || index == previousIndex && offset > previousOffset) {
                createButtonVisible = false
            } else if (
                index < previousIndex || index == previousIndex && offset < previousOffset
            ) {
                createButtonVisible = true
            }
            previousIndex = index
            previousOffset = offset
        }
    }
    val createNamedNote = { name: String? ->
        onCreate(accountId, folderScope.path, name)
    }
    val createNote = {
        val searchName = query.takeIf(NoteNames::isValid)
        if (askForNewNoteName) {
            // Offer the name the note would have been given without asking.
            newNoteName = component.defaultNoteName(searchName)
            namingNewNote = true
        } else {
            createNamedNote(searchName)
        }
    }

    LaunchedEffect(accountId) { withContext(UiDispatcher) { component.refresh(accountId) } }
    val selectFolderScope = { selected: NoteFolderScope ->
        storedFolderScope = selected
        component.settings.setNoteFolderScope(accountId, selected)
    }
    LaunchedEffect(allNotes, folderTree, folderScope) {
        allNotes ?: return@LaunchedEffect
        if (!useSubfolders) return@LaunchedEffect
        // A folder exists only while it holds notes, so a remembered one may have disappeared.
        if (!folderTree.contains(folderScope.path)) selectFolderScope(folderScope.copy(path = ""))
    }
    LaunchedEffect(tagState) {
        // Forget selected tags that no longer exist, so the list cannot stay filtered to nothing.
        if (tagState.availability != NoteTagAvailability.AVAILABLE) return@LaunchedEffect
        val existing = NoteTags.withPaths(tagState.tags)
            .mapTo(mutableSetOf()) { (_, path) -> NoteTags.pathKey(path) }
        tagFilter = tagFilter.filter { it in existing }
    }
    LaunchedEffect(visibleNotes) {
        val visibleIds = visibleNotes?.mapTo(mutableSetOf()) { it.localId }
            ?: return@LaunchedEffect
        selectedNoteIds = selectedNoteIds.filter { it in visibleIds }
    }
    BackHandler(enabled = selectionActive) {
        selectionMenuOpen = false
        selectedNoteIds = emptyList()
    }
    BackHandler(enabled = searchFocused && !selectionActive) { leaveSearch() }
    BackHandler(enabled = folderDrawerState.isOpen) { scope.launch { folderDrawerState.close() } }

    ModalNavigationDrawer(
        drawerState = folderDrawerState,
        // Opening is left to the buttons, since a swipe from the edge competes with the swipe
        // actions of the note rows.
        gesturesEnabled = useSubfolders && folderDrawerState.isOpen,
        drawerContent = {
            NoteFolderDrawerSheet(
                accountName = account.displayName,
                tree = folderTree,
                scope = folderScope,
                nested = nestedFolders,
                expanded = expandedFolders.toSet(),
                onToggleExpanded = { folder ->
                    val key = NoteFolders.key(folder.path)
                    expandedFolders = if (key in expandedFolders) {
                        expandedFolders - key
                    } else {
                        expandedFolders + key
                    }
                },
                onSelect = { path ->
                    selectFolderScope(folderScope.copy(path = path))
                    scope.launch { folderDrawerState.close() }
                },
                onIncludeSubfoldersChange = { include ->
                    selectFolderScope(folderScope.copy(includeSubfolders = include))
                },
                visible = folderDrawerState.isOpen ||
                    folderDrawerState.targetValue == DrawerValue.Open
            )
        }
    ) {
        Scaffold(
            floatingActionButton = {
                if (
                    !selectionActive &&
                    (!hideCreateButtonOnScroll || createButtonVisible)
                ) {
                    if (NoteNames.isValid(query)) {
                        ExtendedFloatingActionButton(
                            onClick = createNote,
                            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                            text = { Text(stringResource(R.string.action_create_from_search)) },
                            modifier = Modifier.testTag("create-note-from-search")
                        )
                    } else {
                        FloatingActionButton(
                            onClick = createNote,
                            modifier = Modifier.testTag("create-note")
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.action_new_note)
                            )
                        }
                    }
                }
            },
            topBar = {
                Column(modifier = Modifier.background(headerContainer)) {
                    TopAppBar(
                        colors = if (headerContent == null) {
                            TopAppBarDefaults.topAppBarColors()
                        } else {
                            TopAppBarDefaults.topAppBarColors(
                                containerColor = headerContainer,
                                scrolledContainerColor = headerContainer,
                                navigationIconContentColor = headerContent,
                                titleContentColor = headerContent,
                                actionIconContentColor = headerContent
                            )
                        },
                        navigationIcon = {
                            if (selectionActive) {
                                IconButton(
                                    onClick = { selectedNoteIds = emptyList() },
                                    modifier = Modifier.testTag("clear-note-selection")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(
                                            R.string.action_clear_selection
                                        )
                                    )
                                }
                            } else if (searchFocused) {
                                IconButton(
                                    onClick = leaveSearch,
                                    modifier = Modifier.testTag("close-note-search")
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = stringResource(
                                            R.string.action_close_search
                                        )
                                    )
                                }
                            } else {
                                Box {
                                    IconButton(
                                        onClick = { accountMenuOpen = true },
                                        modifier = Modifier.testTag("account-menu")
                                    ) {
                                        AccountAvatar(component, account)
                                    }
                                    DropdownMenu(
                                        expanded = accountMenuOpen,
                                        onDismissRequest = { accountMenuOpen = false }
                                    ) {
                                        accounts.forEach { choice ->
                                            val current = choice.id == accountId
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        choice.displayName,
                                                        fontWeight = if (current) {
                                                            FontWeight.Bold
                                                        } else {
                                                            null
                                                        },
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                },
                                                leadingIcon = {
                                                    AccountAvatar(
                                                        component,
                                                        choice,
                                                        Modifier.size(32.dp)
                                                    )
                                                },
                                                trailingIcon = if (current) {
                                                    {
                                                        Icon(
                                                            Icons.Filled.Check,
                                                            contentDescription = stringResource(
                                                                R.string.account_current
                                                            )
                                                        )
                                                    }
                                                } else {
                                                    null
                                                },
                                                onClick = {
                                                    accountMenuOpen = false
                                                    if (!current) onSelectAccount(choice.id)
                                                },
                                                modifier = Modifier
                                                    .semantics { selected = current }
                                                    .testTag("account-choice-${choice.id}")
                                            )
                                        }
                                        HorizontalDivider(
                                            modifier = Modifier.padding(vertical = 4.dp)
                                                .testTag("account-menu-divider")
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.action_add_account))
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Add,
                                                    contentDescription = null,
                                                    modifier = Modifier.testTag("add-account-icon")
                                                )
                                            },
                                            onClick = {
                                                accountMenuOpen = false
                                                onImportAccount()
                                            },
                                            modifier = Modifier.testTag("add-account")
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.manage_accounts))
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Settings,
                                                    contentDescription = null,
                                                    modifier = Modifier.testTag(
                                                        "manage-accounts-icon"
                                                    )
                                                )
                                            },
                                            onClick = {
                                                accountMenuOpen = false
                                                onManageAccounts()
                                            },
                                            modifier = Modifier.testTag("manage-accounts")
                                        )
                                    }
                                }
                            }
                        },
                        title = {
                            if (selectionActive) {
                                Text(
                                    pluralStringResource(
                                        R.plurals.notes_selected,
                                        selectedNoteIds.size,
                                        selectedNoteIds.size
                                    )
                                )
                            } else {
                                CompactSearchField(
                                    value = query,
                                    onValueChange = { query = it },
                                    onClear = { query = "" },
                                    searchScope = searchScope,
                                    onSearchScopeChange = { searchScope = it },
                                    searchAllFolders = searchAllFolders.takeIf { useSubfolders },
                                    onSearchAllFoldersChange = { searchAllFolders = it },
                                    // Only losing the focus closes search. Regaining it does
                                    // not reopen it, because hiding the input method hands the
                                    // focus back to the field, which would undo the reader
                                    // leaving search.
                                    onFocusChange = { focused ->
                                        if (!focused) searchFocused = false
                                    },
                                    onPress = { searchFocused = true },
                                    contentColor = headerContent,
                                    modifier = Modifier.fillMaxWidth().testTag("note-search")
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
                                            contentDescription = stringResource(
                                                R.string.selected_note_actions
                                            )
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = selectionMenuOpen,
                                        onDismissRequest = { selectionMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.action_move_to_trash))
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.DeleteOutline,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                selectionMenuOpen = false
                                                trashConfirmationIds = selectedNoteIds
                                            },
                                            modifier = Modifier.testTag("move-notes-to-trash")
                                        )
                                    }
                                }
                            } else if (!searchFocused) {
                                if (useSubfolders) {
                                    IconButton(
                                        onClick = { scope.launch { folderDrawerState.open() } },
                                        modifier = Modifier.testTag("folder-navigation")
                                    ) {
                                        Icon(
                                            Icons.Filled.Folder,
                                            contentDescription =
                                            stringResource(R.string.folders_open)
                                        )
                                    }
                                }
                                Box {
                                    IconButton(
                                        onClick = { noteListMenuOpen = true },
                                        modifier = Modifier.testTag("note-list-menu")
                                    ) {
                                        Icon(
                                            Icons.Filled.MoreVert,
                                            contentDescription = stringResource(
                                                R.string.note_actions
                                            )
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = noteListMenuOpen,
                                        onDismissRequest = { noteListMenuOpen = false }
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    when (sortOrder) {
                                                        NoteSortOrder.LATEST_FIRST ->
                                                            stringResource(
                                                                R.string.sort_latest_first
                                                            )
                                                        NoteSortOrder.TITLE_ASCENDING ->
                                                            stringResource(
                                                                R.string.sort_title_ascending
                                                            )
                                                        NoteSortOrder.TITLE_DESCENDING ->
                                                            stringResource(
                                                                R.string.sort_title_descending
                                                            )
                                                    }
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.AutoMirrored.Filled.Sort,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                noteListMenuOpen = false
                                                sortMenuOpen = true
                                            },
                                            modifier = Modifier.testTag("sort-selector")
                                        )
                                        if (useSubfolders) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(
                                                        NoteFolderLabels.menu(
                                                            LocalContext.current,
                                                            folderScope
                                                        )
                                                    )
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Filled.Folder,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    noteListMenuOpen = false
                                                    scope.launch { folderDrawerState.open() }
                                                },
                                                modifier = Modifier.testTag("category-selector")
                                            )
                                        }
                                        if (tagState.availability ==
                                            NoteTagAvailability.AVAILABLE
                                        ) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(tagFilterLabel(tagState, tagFilter))
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Filled.FilterList,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    noteListMenuOpen = false
                                                    tagFilterOpen = true
                                                },
                                                modifier = Modifier.testTag("tag-filter-selector")
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.bookmarks)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Bookmarks,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                noteListMenuOpen = false
                                                onOpenBookmarks()
                                            },
                                            modifier = Modifier.testTag("bookmarks-menu")
                                        )
                                        if (deckAvailable) {
                                            DropdownMenuItem(
                                                text = {
                                                    Text(stringResource(R.string.deck_cards))
                                                },
                                                leadingIcon = {
                                                    Icon(
                                                        Icons.Filled.ViewKanban,
                                                        contentDescription = null
                                                    )
                                                },
                                                onClick = {
                                                    noteListMenuOpen = false
                                                    showDeckBrowser = true
                                                },
                                                modifier = Modifier.testTag("browse-deck-cards")
                                            )
                                        }
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.trash)) },
                                            leadingIcon = {
                                                Icon(Icons.Filled.Delete, contentDescription = null)
                                            },
                                            onClick = {
                                                noteListMenuOpen = false
                                                val requestId = ++trashRequestId
                                                trashState = ArchiveLoadState.Loading
                                                scope.launch {
                                                    val result = runCatching {
                                                        component.trashedNotes(
                                                            accountId,
                                                            allNotes.orEmpty().mapTo(
                                                                mutableSetOf()
                                                            ) {
                                                                it.category
                                                            }
                                                        )
                                                    }.fold(
                                                        onSuccess = { ArchiveLoadState.Loaded(it) },
                                                        onFailure = {
                                                            ArchiveLoadState.Failed(
                                                                it.message
                                                                    ?: trashLoadFailedMessage
                                                            )
                                                        }
                                                    )
                                                    if (trashRequestId ==
                                                        requestId
                                                    ) {
                                                        trashState = result
                                                    }
                                                }
                                            },
                                            modifier = Modifier.testTag("remote-trash")
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.settings_title))
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Settings,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                noteListMenuOpen = false
                                                showSettings = true
                                            },
                                            modifier = Modifier.testTag("settings")
                                        )
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.about)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.Info,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                noteListMenuOpen = false
                                                showAbout = true
                                            },
                                            modifier = Modifier.testTag("about")
                                        )
                                    }
                                    DropdownMenu(
                                        expanded = sortMenuOpen,
                                        onDismissRequest = { sortMenuOpen = false }
                                    ) {
                                        fun select(order: NoteSortOrder) {
                                            sortOrder = order
                                            sortMenuOpen = false
                                        }
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        R.string.sort_option_latest_first
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                RadioButton(
                                                    selected =
                                                    sortOrder == NoteSortOrder.LATEST_FIRST,
                                                    onClick = null
                                                )
                                            },
                                            onClick = { select(NoteSortOrder.LATEST_FIRST) },
                                            modifier = Modifier.testTag("sort-option-latest")
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        R.string.sort_option_title_ascending
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                RadioButton(
                                                    selected =
                                                    sortOrder == NoteSortOrder.TITLE_ASCENDING,
                                                    onClick = null
                                                )
                                            },
                                            onClick = { select(NoteSortOrder.TITLE_ASCENDING) },
                                            modifier = Modifier.testTag(
                                                "sort-option-title-ascending"
                                            )
                                        )
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    stringResource(
                                                        R.string.sort_option_title_descending
                                                    )
                                                )
                                            },
                                            leadingIcon = {
                                                RadioButton(
                                                    selected =
                                                    sortOrder == NoteSortOrder.TITLE_DESCENDING,
                                                    onClick = null
                                                )
                                            },
                                            onClick = { select(NoteSortOrder.TITLE_DESCENDING) },
                                            modifier = Modifier.testTag(
                                                "sort-option-title-descending"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    )
                    if (appearance.showListHeader) {
                        NoteListHeader(
                            title = NoteFolderLabels.title(LocalContext.current, folderScope),
                            accountName = account.displayName,
                            contentColor = headerContent ?: MaterialTheme.colorScheme.onSurface,
                            secondaryColor =
                            headerSecondary ?: MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = syncState is SyncUiState.Refreshing,
                onRefresh = { scope.launch { component.refresh(accountId) } },
                modifier = Modifier.fillMaxSize().padding(padding).testTag("pull-to-refresh")
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    SyncStatus(syncState, reconnect = { onReconnectAccount(accountId) })
                    LazyColumn(
                        state = noteListState,
                        modifier = Modifier.weight(1f).testTag("note-list")
                    ) {
                        if (importState is SyncUiState.Failed) {
                            item {
                                Text(
                                    importState.message,
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )
                            }
                        }
                        if (visibleNotes == null) {
                            item {
                                CircularProgressIndicator(
                                    modifier = Modifier.padding(24.dp).testTag("notes-loading")
                                )
                            }
                        } else if (visibleNotes.isEmpty()) {
                            item {
                                Text(
                                    when {
                                        query.isNotBlank() -> stringResource(
                                            R.string.notes_empty_search
                                        )
                                        tagFilterActive -> stringResource(R.string.notes_empty_tags)
                                        else -> stringResource(R.string.notes_empty_category)
                                    },
                                    modifier = Modifier.padding(24.dp),
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        } else {
                            items(visibleNotes, key = { it.localId }) { note ->
                                val selected = note.localId in selectedNoteIds
                                NoteListItem(
                                    note = note,
                                    selected = selected,
                                    selectionActive = selectionActive,
                                    showCategory = showCategory,
                                    showNotePreview = showNotePreview,
                                    tags = tagState.tagsOf(NoteTags.keyOf(note)),
                                    compact = compactNoteList,
                                    appearance = appearance,
                                    swipeEnabled = swipeNoteActions,
                                    onClick = {
                                        if (selectionActive) {
                                            selectedNoteIds =
                                                if (selected) {
                                                    selectedNoteIds - note.localId
                                                } else {
                                                    selectedNoteIds + note.localId
                                                }
                                        } else {
                                            onOpen(
                                                note.localId,
                                                query.trim().takeIf(String::isNotEmpty)
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        if (!selected) selectedNoteIds += note.localId
                                    },
                                    onToggleFavorite = {
                                        scope.launch {
                                            component.setFavorite(note.localId, !note.favorite)
                                        }
                                    },
                                    onTrash = {
                                        scope.launch {
                                            component.moveNotesToTrash(
                                                accountId,
                                                listOf(note.localId)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    if (tagFilterOpen) {
        NoteTagFilterDialog(
            state = tagState,
            selected = tagFilter,
            onChange = { tagFilter = it },
            onDismiss = { tagFilterOpen = false }
        )
    }
    if (namingNewNote) {
        NewNoteNameDialog(
            name = newNoteName,
            onNameChange = { newNoteName = it },
            onDismiss = { namingNewNote = false },
            onConfirm = {
                namingNewNote = false
                createNamedNote(newNoteName)
            }
        )
    }
    if (showDeckBrowser) {
        DeckCardBrowserDialog(component, account, onDismiss = { showDeckBrowser = false })
    }
    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text(stringResource(R.string.settings_title)) },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SettingsSectionHeader(stringResource(R.string.settings_section_note_list))
                    SettingsToggle(
                        label = stringResource(R.string.settings_show_note_preview),
                        checked = showNotePreview,
                        onCheckedChange = component.settings::setShowNotePreview,
                        testTag = "toggle-note-preview"
                    )
                    SettingsToggle(
                        label = stringResource(R.string.settings_use_subfolders),
                        description = stringResource(R.string.settings_use_subfolders_description),
                        checked = useSubfolders,
                        onCheckedChange = { component.settings.setUseSubfolders(accountId, it) },
                        testTag = "toggle-use-subfolders"
                    )
                    if (useSubfolders) {
                        SettingsToggle(
                            label = stringResource(R.string.settings_show_category),
                            checked = showCategory,
                            onCheckedChange = { component.settings.setShowCategory(accountId, it) },
                            testTag = "toggle-category"
                        )
                    }
                    SettingsToggle(
                        label = stringResource(R.string.settings_compact_note_list),
                        description = stringResource(
                            R.string.settings_compact_note_list_description
                        ),
                        checked = compactNoteList,
                        onCheckedChange = component.settings::setCompactNoteList,
                        testTag = "toggle-compact-note-list"
                    )
                    SettingsToggle(
                        label = stringResource(R.string.settings_swipe_note_actions),
                        description = stringResource(
                            R.string.settings_swipe_note_actions_description
                        ),
                        checked = swipeNoteActions,
                        onCheckedChange = component.settings::setSwipeNoteActions,
                        testTag = "toggle-swipe-note-actions"
                    )
                    SettingsToggle(
                        label = stringResource(R.string.settings_hide_create_button),
                        description = stringResource(
                            R.string.settings_hide_create_button_description
                        ),
                        checked = hideCreateButtonOnScroll,
                        onCheckedChange = component.settings::setHideCreateButtonOnScroll,
                        testTag = "toggle-hide-create-button-on-scroll"
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingsSectionHeader(stringResource(R.string.settings_section_notes))
                    SettingsToggle(
                        label = stringResource(R.string.settings_ask_for_new_note_name),
                        description = stringResource(
                            R.string.settings_ask_for_new_note_name_description
                        ),
                        checked = askForNewNoteName,
                        onCheckedChange = component.settings::setAskForNewNoteName,
                        testTag = "toggle-ask-for-new-note-name"
                    )
                    SettingsToggle(
                        label = stringResource(R.string.settings_show_toolbar_labels),
                        description = stringResource(
                            R.string.settings_show_toolbar_labels_description
                        ),
                        checked = showEditorToolbarLabels,
                        onCheckedChange = component.settings::setShowEditorToolbarLabels,
                        testTag = "toggle-editor-toolbar-labels"
                    )
                    OutlinedTextField(
                        value = bookmarksPath,
                        onValueChange = { component.settings.setBookmarksPath(accountId, it) },
                        label = { Text(stringResource(R.string.settings_bookmarks_file)) },
                        leadingIcon = { Icon(Icons.Filled.Bookmarks, contentDescription = null) },
                        singleLine = true,
                        isError = parseBookmarksSource(bookmarksPath) == null,
                        supportingText = {
                            if (parseBookmarksSource(bookmarksPath) == null) {
                                Text(stringResource(R.string.settings_bookmarks_file_invalid))
                            } else {
                                Text(stringResource(R.string.settings_bookmarks_file_examples))
                            }
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                            .testTag("bookmarks-path")
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    SettingsSectionHeader(stringResource(R.string.settings_section_more))
                    SettingsActionRow(
                        icon = Icons.Filled.Palette,
                        label = stringResource(R.string.appearance),
                        onClick = {
                            showSettings = false
                            showAppearance = true
                        },
                        testTag = "open-appearance"
                    )
                    SettingsActionRow(
                        icon = Icons.Filled.ViewKanban,
                        label = stringResource(R.string.deck_reset_opening),
                        onClick = { component.settings.resetDeckLinkOpening() },
                        testTag = "reset-deck-opening",
                        navigates = false
                    )
                    SettingsActionRow(
                        icon = Icons.Filled.BugReport,
                        label = stringResource(R.string.debug_diagnostics),
                        onClick = {
                            showSettings = false
                            diagnosticReport = null
                            showDiagnostics = true
                        },
                        testTag = "open-diagnostics"
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showSettings = false },
                    modifier = Modifier.testTag("close-settings")
                ) { Text(stringResource(R.string.action_close)) }
            },
            modifier = Modifier.testTag("settings-dialog")
        )
    }
    if (showAppearance) {
        AlertDialog(
            onDismissRequest = { showAppearance = false },
            icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
            title = { Text(stringResource(R.string.appearance)) },
            text = {
                AppAppearanceEditor(
                    appearance = appearance,
                    onChange = component.settings::setAppearance,
                    modifier = Modifier.heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState())
                )
            },
            dismissButton = {
                TextButton(
                    onClick = { component.settings.setAppearance(AppAppearance()) },
                    modifier = Modifier.testTag("reset-appearance")
                ) { IconLabel(Icons.Filled.RestartAlt, stringResource(R.string.action_reset)) }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAppearance = false },
                    modifier = Modifier.testTag("close-appearance")
                ) { Text(stringResource(R.string.action_close)) }
            },
            modifier = Modifier.testTag("appearance-dialog")
        )
    }
    if (showDiagnostics) {
        val context = LocalContext.current
        val diagnosticsLoadFailedMessage = stringResource(R.string.diagnostics_load_failed)
        val diagnosticsClearFailedMessage = stringResource(R.string.diagnostics_clear_failed)
        val diagnosticClipLabel = stringResource(R.string.diagnostics_clip_label)
        LaunchedEffect(showDiagnostics) {
            diagnosticReport = withContext(UiDispatcher) {
                try {
                    component.syncDiagnosticReport()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    diagnosticsLoadFailedMessage
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            icon = { Icon(Icons.Filled.BugReport, contentDescription = null) },
            title = { Text(stringResource(R.string.debug_diagnostics)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                        .testTag("diagnostic-report")
                ) {
                    DialogNotice(stringResource(R.string.diagnostics_privacy_notice))
                    val report = diagnosticReport
                    if (report == null) {
                        DialogLoadingIndicator("diagnostics-loading")
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    report,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                        .testTag("diagnostic-report-text")
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        diagnosticReport?.let { report ->
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                ClipData.newPlainText(diagnosticClipLabel, report)
                            )
                        }
                    },
                    enabled = diagnosticReport != null,
                    modifier = Modifier.testTag("copy-diagnostic-report")
                ) {
                    IconLabel(
                        Icons.Filled.ContentCopy,
                        stringResource(R.string.diagnostics_copy_report)
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            scope.launch {
                                diagnosticReport = try {
                                    component.clearSyncDiagnostics()
                                    component.syncDiagnosticReport()
                                } catch (cancelled: CancellationException) {
                                    throw cancelled
                                } catch (_: Exception) {
                                    diagnosticsClearFailedMessage
                                }
                            }
                        },
                        enabled = diagnosticReport != null,
                        modifier = Modifier.testTag("clear-diagnostics")
                    ) { Text(stringResource(R.string.action_clear)) }
                    TextButton(
                        onClick = { showDiagnostics = false },
                        modifier = Modifier.testTag("close-diagnostics")
                    ) { Text(stringResource(R.string.action_close)) }
                }
            },
            modifier = Modifier.testTag("diagnostics-dialog")
        )
    }
    trashConfirmationIds?.let { ids ->
        AlertDialog(
            onDismissRequest = { trashConfirmationIds = null },
            icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
            title = {
                Text(pluralStringResource(R.plurals.delete_notes_title, ids.size, ids.size))
            },
            text = { Text(stringResource(R.string.delete_notes_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        trashConfirmationIds = null
                        selectedNoteIds = emptyList()
                        scope.launch { component.moveNotesToTrash(accountId, ids) }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm-move-notes-to-trash")
                ) { Text(stringResource(R.string.action_move_to_trash)) }
            },
            dismissButton = {
                TextButton(onClick = { trashConfirmationIds = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
            modifier = Modifier.testTag("move-notes-to-trash-dialog")
        )
    }
    if (showAbout) {
        val context = LocalContext.current
        val packageInfo = remember {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        val repoUrl = "https://github.com/qownnotes/qownnotes-android"
        AlertDialog(
            onDismissRequest = { showAbout = false },
            icon = {
                Image(
                    painterResource(R.drawable.qownnotes_logo),
                    contentDescription = null,
                    modifier = Modifier.size(64.dp)
                )
            },
            title = { Text(stringResource(R.string.about_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(R.string.about_version, packageInfo.versionName.orEmpty()),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("$repoUrl/releases/tag/v${packageInfo.versionName}")
                                )
                            )
                        }
                    )
                    Text(
                        stringResource(R.string.about_commit, BuildConfig.GIT_COMMIT.take(7)),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("$repoUrl/commit/${BuildConfig.GIT_COMMIT}")
                                )
                            )
                        }
                    )
                    Text(stringResource(R.string.about_copyright))
                    Text(stringResource(R.string.about_license))
                    Text(
                        stringResource(R.string.about_view_source),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    Uri.parse("$repoUrl/blob/${BuildConfig.GIT_COMMIT}/LICENSE")
                                )
                            )
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showAbout = false
                }) { Text(stringResource(R.string.action_close)) }
            }
        )
    }
    if (trashToRestore == null) {
        when (val state = trashState) {
            ArchiveLoadState.Idle -> Unit
            ArchiveLoadState.Loading -> ArchiveLoadingDialog(
                title = stringResource(R.string.remote_trash),
                onDismiss = {
                    trashRequestId++
                    trashState = ArchiveLoadState.Idle
                }
            )
            is ArchiveLoadState.Failed -> ArchiveErrorDialog(
                title = stringResource(R.string.remote_trash),
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
            icon = { Icon(Icons.Filled.RestoreFromTrash, contentDescription = null) },
            title = { Text(stringResource(R.string.trash_restore_title, trashed.name)) },
            text = { Text(stringResource(R.string.trash_restore_message)) },
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
                                    allNotes.orEmpty().mapTo(mutableSetOf()) { it.category }
                                )
                            }.fold(
                                onSuccess = { ArchiveLoadState.Loaded(it) },
                                onFailure = {
                                    ArchiveLoadState.Failed(
                                        it.message ?: trashRestoreFailedMessage
                                    )
                                }
                            )
                            if (trashRequestId == requestId) trashState = result
                        }
                    },
                    modifier = Modifier.testTag("confirm-restore-trashed-note")
                ) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    trashToRestore = null
                }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
private fun CompactSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    searchScope: NoteSearchScope,
    onSearchScopeChange: (NoteSearchScope) -> Unit,
    /** Whether a search looks beyond the listed folder; `null` hides the choice. */
    searchAllFolders: Boolean?,
    onSearchAllFoldersChange: (Boolean) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    /** Content color on a custom header; `null` uses the theme's surface colors. */
    contentColor: Color? = null
) {
    val shape = RoundedCornerShape(20.dp)
    val currentOnPress by rememberUpdatedState(onPress)
    var filterMenuOpen by rememberSaveable { mutableStateOf(false) }
    val textColor = contentColor ?: MaterialTheme.colorScheme.onSurface
    val secondaryColor = contentColor?.copy(alpha = 0.75f)
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val accentColor = contentColor ?: MaterialTheme.colorScheme.primary
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
        cursorBrush = SolidColor(accentColor),
        modifier = modifier.onFocusChanged { onFocusChange(it.isFocused) }
            // Reaching for the field opens search even when it already holds the focus that the
            // previous search left behind. The touch is only observed, never taken, so the field
            // still places the cursor where it was tapped.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    currentOnPress()
                }
            }
            .height(40.dp)
            .border(
                1.dp,
                contentColor?.copy(alpha = 0.6f) ?: MaterialTheme.colorScheme.outline,
                shape
            )
            .padding(horizontal = 10.dp),
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = secondaryColor
                )
                Box(
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.search_notes),
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryColor
                        )
                    }
                    innerTextField()
                }
                Box {
                    IconButton(
                        onClick = { filterMenuOpen = true },
                        modifier = Modifier.size(32.dp).testTag("note-search-filter")
                    ) {
                        Icon(
                            Icons.Filled.FilterList,
                            contentDescription = stringResource(R.string.search_filter),
                            tint = if (
                                searchScope == NoteSearchScope.TITLE || searchAllFolders == true
                            ) {
                                accentColor
                            } else {
                                secondaryColor
                            }
                        )
                    }
                    DropdownMenu(
                        expanded = filterMenuOpen,
                        onDismissRequest = { filterMenuOpen = false }
                    ) {
                        fun select(scope: NoteSearchScope) {
                            onSearchScopeChange(scope)
                            filterMenuOpen = false
                        }
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.search_filter_title_and_content))
                            },
                            leadingIcon = {
                                RadioButton(
                                    selected = searchScope == NoteSearchScope.TITLE_AND_CONTENT,
                                    onClick = null
                                )
                            },
                            onClick = { select(NoteSearchScope.TITLE_AND_CONTENT) },
                            modifier = Modifier.testTag("search-filter-title-content")
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.search_filter_title_only)) },
                            leadingIcon = {
                                RadioButton(
                                    selected = searchScope == NoteSearchScope.TITLE,
                                    onClick = null
                                )
                            },
                            onClick = { select(NoteSearchScope.TITLE) },
                            modifier = Modifier.testTag("search-filter-title")
                        )
                        if (searchAllFolders != null) {
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.search_all_folders)) },
                                leadingIcon = {
                                    Checkbox(checked = searchAllFolders, onCheckedChange = null)
                                },
                                onClick = {
                                    onSearchAllFoldersChange(!searchAllFolders)
                                    filterMenuOpen = false
                                },
                                modifier = Modifier.testTag("search-all-folders")
                            )
                        }
                    }
                }
                if (value.isNotEmpty()) {
                    IconButton(
                        onClick = onClear,
                        modifier = Modifier.size(32.dp).testTag("clear-note-search")
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.action_clear_search)
                        )
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun NoteListItem(
    note: NoteListItem,
    selected: Boolean,
    selectionActive: Boolean,
    showCategory: Boolean,
    showNotePreview: Boolean,
    tags: List<NoteTag>,
    compact: Boolean,
    appearance: AppAppearance,
    swipeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTrash: () -> Unit
) {
    // Compact rows remove only decorative spacing. The favorite button keeps its minimum touch
    // target, which also bounds the height of a title-only row.
    val rowVerticalPadding = if (compact) 0.dp else 6.dp
    val textVerticalPadding = if (compact) 4.dp else 8.dp
    val previewMaxLines = if (compact) 1 else 2
    val favoriteDescription =
        stringResource(
            if (note.favorite) R.string.favorite_remove else R.string.favorite_add
        )
    // A selected row keeps the theme's selection color over any custom row color.
    val customBackground = appearance.noteBackground?.takeUnless { selected }
    val rowBackground = when {
        selected -> MaterialTheme.colorScheme.secondaryContainer
        customBackground != null -> Color(customBackground)
        else -> MaterialTheme.colorScheme.surface
    }
    val rowContent = customBackground?.let { Color(AppearanceColors.contentColor(it)) }
        ?: if (selected) {
            MaterialTheme.colorScheme.onSecondaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val rowSecondary = customBackground?.let {
        Color(AppearanceColors.secondaryContentColor(it))
    } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val favoriteTint = when {
        customBackground != null ->
            if (note.favorite) rowContent else rowContent.copy(alpha = 0.35f)
        note.favorite -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    // Cards wrap the whole item, including the swipe action background, so swiping reveals the
    // action inside the card outline.
    val cardShape = RoundedCornerShape(12.dp)
    val itemModifier = if (appearance.noteCards) {
        Modifier.padding(horizontal = 12.dp, vertical = if (compact) 2.dp else 4.dp)
            .clip(cardShape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, cardShape)
    } else {
        Modifier
    }
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth()
                // Custom-colored flat rows are separated by a hairline of the list surface;
                // cards are separated by their gap instead.
                .then(
                    if (appearance.noteBackground != null && !appearance.noteCards) {
                        Modifier.background(MaterialTheme.colorScheme.surface)
                            .padding(bottom = 1.dp)
                    } else {
                        Modifier
                    }
                )
                .testTag("note-${note.localId}")
                .background(rowBackground)
                .semantics { this.selected = selected }
                // A card is tappable as a whole so its press ripple follows the card outline; the
                // favorite button keeps its own click handling.
                .then(
                    if (appearance.noteCards) {
                        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    } else {
                        Modifier
                    }
                )
                .padding(
                    start = 20.dp,
                    end = 8.dp,
                    top = rowVerticalPadding,
                    bottom = rowVerticalPadding
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompositionLocalProvider(LocalContentColor provides rowContent) {
                Column(
                    modifier = Modifier.weight(1f)
                        .then(
                            if (appearance.noteCards) {
                                Modifier
                            } else {
                                Modifier.combinedClickable(
                                    onClick = onClick,
                                    onLongClick = onLongClick
                                )
                            }
                        )
                        .padding(vertical = textVerticalPadding)
                ) {
                    Text(note.title, style = MaterialTheme.typography.titleMedium)
                    if (showCategory) {
                        NoteCategoryLabel(
                            category = note.category.ifBlank {
                                stringResource(R.string.note_uncategorized)
                            },
                            highlight = appearance.highlightCategories,
                            highlightColor = appearance.categoryHighlight,
                            testTag = "note-category-${note.localId}"
                        )
                    }
                    NoteTagLine(
                        tags = tags,
                        testTag = "note-tags-${note.localId}",
                        color = if (customBackground != null) rowSecondary else null
                    )
                    if (showNotePreview) {
                        val excerpt = remember(note.excerpt, note.title) {
                            NoteExcerpt.of(note.excerpt, note.title)
                        }
                        if (excerpt.isNotBlank()) {
                            Text(
                                excerpt,
                                style = MaterialTheme.typography.bodySmall,
                                color = rowSecondary,
                                maxLines = previewMaxLines,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                IconButton(
                    onClick = onToggleFavorite,
                    enabled = !selectionActive,
                    modifier = Modifier.testTag("favorite-${note.localId}")
                ) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = favoriteDescription,
                        tint = favoriteTint
                    )
                }
            }
        }
    }

    if (!swipeEnabled || selectionActive) {
        Box(modifier = itemModifier.testTag("swipe-note-${note.localId}")) { content() }
        return
    }

    val currentOnToggleFavorite by rememberUpdatedState(onToggleFavorite)
    val swipeState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    currentOnToggleFavorite()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    onTrash()
                    true
                }
                SwipeToDismissBoxValue.Settled -> true
            }
        }
    )
    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            val favoriteAction = swipeState.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val actionIcon = if (favoriteAction) Icons.Filled.Star else Icons.Filled.Delete
            val actionLabel =
                if (favoriteAction) {
                    stringResource(
                        if (note.favorite) R.string.action_unfavorite else R.string.action_favorite
                    )
                } else {
                    stringResource(R.string.action_move_to_trash)
                }
            Row(
                modifier = Modifier.fillMaxSize()
                    .background(
                        if (favoriteAction) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.errorContainer
                        }
                    )
                    .clearAndSetSemantics {}
                    .padding(horizontal = 24.dp),
                horizontalArrangement =
                if (favoriteAction) Arrangement.Start else Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(actionIcon, contentDescription = null)
                Text(actionLabel, modifier = Modifier.padding(start = 8.dp))
            }
        },
        modifier = itemModifier.testTag("swipe-note-${note.localId}"),
        content = { content() }
    )
}

@Composable
private fun AccountAvatar(
    component: ApplicationComponent,
    account: Account,
    modifier: Modifier = Modifier
) {
    var avatar by remember(account.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(account.id) { avatar = component.accountAvatar(account) }
    val description = stringResource(R.string.account_avatar_description, account.displayName)
    val avatarModifier = modifier.size(40.dp).clip(CircleShape)
        .testTag("account-avatar-${account.id}")
    val bitmap = avatar
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            modifier = avatarModifier
        )
    } else {
        Box(
            modifier = avatarModifier.background(MaterialTheme.colorScheme.primaryContainer)
                .clearAndSetSemantics { contentDescription = description },
            contentAlignment = Alignment.Center
        ) {
            Text(
                account.userId.firstOrNull()?.uppercase() ?: "?",
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun SyncStatus(state: SyncUiState, reconnect: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("sync-status"),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        when (state) {
            SyncUiState.Idle -> Text(
                stringResource(R.string.sync_available_offline),
                style = MaterialTheme.typography.labelMedium
            )
            SyncUiState.Refreshing -> Text(stringResource(R.string.sync_refreshing))
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
            TextButton(onClick = reconnect) { Text(stringResource(R.string.action_reconnect)) }
        }
    }
}

@Composable
private fun ArchiveLoadingDialog(title: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
            ) {
                CircularProgressIndicator(modifier = Modifier.testTag("archive-loading"))
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun ArchiveErrorDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        }
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
    initialFindQuery: String?,
    onInitialEditStarted: () -> Unit,
    onBackToList: () -> Unit,
    onOpen: (ResolvedNoteLink) -> Unit
) {
    val note by component.noteRepository.observeNote(localId)
        .collectAsStateWithLifecycle(initialValue = null, context = UiDispatcher)
    val noteSyncDiagnostics by component.noteSyncDiagnostics
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val accountNotesFlow = remember(note?.accountId) {
        note?.accountId?.let(component.noteRepository::observeNotes) ?: flowOf(emptyList())
    }
    val accountNotes by accountNotesFlow
        .collectAsStateWithLifecycle(initialValue = emptyList(), context = UiDispatcher)
    val accounts by component.accountRepository.observeAccounts()
        .collectAsStateWithLifecycle(initialValue = emptyList(), context = UiDispatcher)
    val account = remember(note?.accountId, accounts) {
        accounts.firstOrNull { it.id == note?.accountId }
    }
    val useSubfoldersFlow = remember(note?.accountId) {
        note?.accountId?.let(component.settings::useSubfolders) ?: MutableStateFlow(false)
    }
    val useSubfolders by useSubfoldersFlow.collectAsStateWithLifecycle(context = UiDispatcher)
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope { UiDispatcher }
    val context = LocalContext.current
    val resources = LocalResources.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var editing by rememberSaveable(localId) { mutableStateOf(false) }
    var draft by remember(localId) { mutableStateOf<String?>(null) }
    // What the note held when editing started. Typing is saved continuously, so discarding means
    // restoring this, not merely dropping what has not been written yet.
    var contentBeforeEditing by rememberSaveable(localId) { mutableStateOf<String?>(null) }
    var showDiscardConfirmation by rememberSaveable(localId) { mutableStateOf(false) }
    var showDeleteConfirmation by rememberSaveable(localId) { mutableStateOf(false) }
    var showConflictResolution by rememberSaveable(localId) { mutableStateOf(false) }
    var resolvingConflict by rememberSaveable(localId) { mutableStateOf(false) }
    var conflictResolutionError by rememberSaveable(localId) { mutableStateOf<String?>(null) }
    var conflictSnapshot by remember(localId) { mutableStateOf<NoteConflict?>(null) }
    var showRemoteMissingResolution by rememberSaveable(localId) { mutableStateOf(false) }
    var resolvingRemoteMissing by rememberSaveable(localId) { mutableStateOf(false) }
    var remoteMissingResolutionError by rememberSaveable(localId) {
        mutableStateOf<String?>(null)
    }
    var renaming by rememberSaveable(localId) { mutableStateOf(false) }
    var changingCategory by rememberSaveable(localId) { mutableStateOf(false) }
    var editingTags by rememberSaveable(localId) { mutableStateOf(false) }
    val noteTagStateFlow = remember(note?.accountId) {
        note?.accountId?.let(component::observeNoteTags) ?: flowOf(NoteTagState())
    }
    val noteTagState by noteTagStateFlow
        .collectAsStateWithLifecycle(initialValue = NoteTagState(), context = UiDispatcher)
    val noteTagKey = note?.let(NoteTags::keyOf)
    val noteTags = remember(noteTagState, noteTagKey) {
        noteTagKey?.let(noteTagState::tagsOf).orEmpty()
    }
    var showingInformation by rememberSaveable(localId) { mutableStateOf(false) }
    var noteMenuOpen by rememberSaveable(localId) { mutableStateOf(false) }
    var noteName by rememberSaveable(localId) { mutableStateOf("") }
    var updateHeading by rememberSaveable(localId) { mutableStateOf(true) }
    var selectionStart by rememberSaveable(localId) { mutableStateOf(0) }
    var selectionEnd by rememberSaveable(localId) { mutableStateOf(0) }
    var editor by remember { mutableStateOf<MarkdownEditText?>(null) }
    var linkDialogUrl by rememberSaveable(localId) { mutableStateOf<String?>(null) }
    var linkDialogTitle by rememberSaveable(localId) { mutableStateOf("") }
    var importingImage by remember(localId) { mutableStateOf(false) }
    val nextcloudDeckFlow = remember(note?.accountId) {
        note?.accountId?.takeIf { component.supportsNextcloudDeck }
            ?.let(component::nextcloudDeckAvailable)
            ?: flowOf(false)
    }
    val nextcloudDeckAvailable by nextcloudDeckFlow
        .collectAsStateWithLifecycle(initialValue = false, context = UiDispatcher)
    LaunchedEffect(note?.accountId) {
        note?.accountId?.let { component.refreshDeckAvailability(it) }
    }
    // The selected text when the dialog opened, offered as the card title. Null hides the dialog.
    var deckCardTitle by rememberSaveable(localId) { mutableStateOf<String?>(null) }
    var showNoteDeckBrowser by rememberSaveable(localId) { mutableStateOf(false) }
    var deckLinkUrl by rememberSaveable(localId) { mutableStateOf<String?>(null) }
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                importingImage = true
                scope.launch {
                    runCatching { component.importImage(localId, uri) }
                        .onSuccess { image ->
                            editor?.insertImage(image.description, image.markdownPath)
                            editor?.focusForInput()
                        }
                        .onFailure { error ->
                            Toast.makeText(
                                context,
                                error.message ?: resources.getString(R.string.insert_image_failed),
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    importingImage = false
                }
            }
        }
    // Hiding the keyboard and the state change that takes the editor away have to happen in one
    // go on [UiDispatcher]: the input method is only reachable while the editor still has a window
    // token, and editing is left from callbacks that have already waited for a repository call.
    val leaveEditMode = {
        scope.launch {
            editor?.releaseInputFocus()
            editing = false
        }
        Unit
    }
    val leaveNoteScreen = {
        scope.launch {
            editor?.releaseInputFocus()
            onBackToList()
        }
        Unit
    }
    var editorScrollValue by remember { mutableIntStateOf(0) }
    var editorScrollRange by remember { mutableIntStateOf(0) }
    var renderedView by remember { mutableStateOf<AppCompatTextView?>(null) }
    var editorBinding by remember { mutableStateOf<MarkdownEditorBinding?>(null) }
    var canUndo by remember(localId) { mutableStateOf(false) }
    var canRedo by remember(localId) { mutableStateOf(false) }
    var pendingHeading by remember(localId, heading, navigationRequest) { mutableStateOf(heading) }
    var loadImages by remember(localId) { mutableStateOf(true) }
    // A note opened from an active note-list search starts by finding that text, so the matches
    // that made the note appear in the list are highlighted and the first one is scrolled to.
    var finding by rememberSaveable(localId) { mutableStateOf(initialFindQuery != null) }
    var togglingTask by remember(localId) { mutableStateOf(false) }
    var findQuery by rememberSaveable(localId) { mutableStateOf(initialFindQuery.orEmpty()) }
    // Find opened for the list search was not a request to type, so it must not raise the keyboard.
    var findFromListSearch by rememberSaveable(localId) {
        mutableStateOf(initialFindQuery != null)
    }
    var currentMatch by rememberSaveable(localId) { mutableStateOf(0) }
    var findNavigationRequest by remember(localId) { mutableIntStateOf(0) }
    var matches by remember(localId) { mutableStateOf(emptyList<IntRange>()) }
    val closeFind = {
        finding = false
        findQuery = ""
        findFromListSearch = false
        currentMatch = 0
        if (editing) editor?.focusForInput()
        Unit
    }
    var versionsState by remember(localId) {
        mutableStateOf<ArchiveLoadState<RemoteNoteVersion>>(ArchiveLoadState.Idle)
    }
    var versionToRestore by remember(localId) { mutableStateOf<RemoteNoteVersion?>(null) }
    var versionsRequestId by remember(localId) { mutableIntStateOf(0) }
    val renderedNote = remember(localId) { RenderedNote() }
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
    val noteTextSizeSp by component.settings.noteTextSizeSp
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val showToolbarLabels by component.settings.showEditorToolbarLabels
        .collectAsStateWithLifecycle(context = UiDispatcher)
    val toolbarHintDismissed by component.settings.editorToolbarHintDismissed
        .collectAsStateWithLifecycle(context = UiDispatcher)
    var showToolbarHelp by rememberSaveable(localId) { mutableStateOf(false) }
    val openToolbarHelp = {
        showToolbarHelp = true
        component.settings.setEditorToolbarHintDismissed(true)
    }
    // Applied from a composition effect rather than from an `AndroidView` update block. An update
    // block that observes this value is rescheduled through the holder's `View.getHandler()`,
    // which is null while the view is detached, and the view/edit transition detaches one of them.
    LaunchedEffect(noteTextSizeSp, editor, renderedView) {
        editor?.setTextSize(TypedValue.COMPLEX_UNIT_SP, noteTextSizeSp.toFloat())
        // Markwon sizes headings and code relative to the view's own text size, so the existing
        // spans rescale without re-rendering the note.
        renderedView?.setTextSize(TypedValue.COMPLEX_UNIT_SP, noteTextSizeSp.toFloat())
    }
    // Keep the caret line inside the editor's viewport as typing, formatting, and history actions
    // move the selection.
    LaunchedEffect(editing, editor, selectionStart) {
        val view = editor ?: return@LaunchedEffect
        if (!editing || view.height == 0) return@LaunchedEffect
        withFrameNanos { }
        val layout = view.layout ?: return@LaunchedEffect
        val line = layout.getLineForOffset(selectionStart.coerceIn(0, view.length()))
        val caretTop = layout.getLineTop(line) + view.totalPaddingTop
        val caretBottom = layout.getLineBottom(line) + view.totalPaddingTop
        val viewportTop = view.scrollY
        val viewportBottom = viewportTop + view.height
        val target = when {
            caretTop < viewportTop -> caretTop
            caretBottom > viewportBottom -> caretBottom - view.height
            else -> null
        }
        target?.let(view::scrollVerticallyTo)
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
        val editable = withContext(UiDispatcher) { component.beginEditing(localId) }
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
        withContext(UiDispatcher) { component.saveDraft(localId, source) }
    }
    LaunchedEffect(localId, editing) {
        if (!editing) return@LaunchedEffect
        while (true) {
            delay(component.draftCheckpointIntervalMillis)
            val source = latestDraft
            val current = latestNote
            if (source != null && current != null && source != current.content) {
                withContext(UiDispatcher) { component.checkpointDraft(localId, source) }
            }
        }
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
                if (component.saveDraft(localId, source)) leaveEditMode()
            }
        } else {
            leaveEditMode()
        }
    }
    // Registered after the edit-mode handler so Back closes Find before it leaves the editor.
    BackHandler(enabled = finding) { closeFind() }
    LaunchedEffect(localId, navigationRequest) {
        if (heading == null) scrollState.scrollTo(0)
    }
    LaunchedEffect(matches, currentMatch, renderedView) {
        if (editing) return@LaunchedEffect
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
    LaunchedEffect(editing, finding, findQuery, currentMatch, editor, draft, searchColors) {
        val view = editor ?: return@LaunchedEffect
        if (!editing) return@LaunchedEffect
        if (!finding && matches.isEmpty()) return@LaunchedEffect
        val found = highlightNoteSearchMatches(
            view = view,
            query = if (finding) findQuery else "",
            currentMatch = currentMatch,
            colors = searchColors
        )
        if (found != matches) matches = found
        if (found.isNotEmpty() && currentMatch !in found.indices) {
            currentMatch = found.lastIndex
        }
    }
    // Refreshing highlights after an edit must not reselect a search result underneath the
    // writer's caret or IME composition. Only an explicit find action moves the selection.
    LaunchedEffect(editing, finding, findQuery, findNavigationRequest, editor) {
        val view = editor ?: return@LaunchedEffect
        if (!editing || !finding) return@LaunchedEffect
        val match = findTextMatches(view.text ?: return@LaunchedEffect, findQuery)
            .getOrNull(currentMatch)
            ?: return@LaunchedEffect
        view.setSelection(match.first, match.last + 1)
    }
    val showVersions = {
        val requestId = ++versionsRequestId
        versionsState = ArchiveLoadState.Loading
        scope.launch {
            val result = runCatching { component.noteVersions(localId) }.fold(
                onSuccess = { ArchiveLoadState.Loaded(it) },
                onFailure = {
                    ArchiveLoadState.Failed(
                        it.message ?: resources.getString(R.string.note_versions_load_failed)
                    )
                }
            )
            if (versionsRequestId == requestId) versionsState = result
        }
    }
    val openConflictResolution = {
        scope.launch {
            // Finish persisting the live editor draft before capturing the version to review.
            if (editing) {
                val source = draft
                if (source != null && !component.saveDraft(localId, source)) return@launch
                editor?.releaseInputFocus()
                editing = false
            }
            conflictResolutionError = null
            conflictSnapshot = null
            showConflictResolution = true
        }
        Unit
    }
    val resolveConflict = { keepLocalCopy: Boolean, merge: Boolean ->
        val reviewed = conflictSnapshot
        if (reviewed != null) {
            resolvingConflict = true
            conflictResolutionError = null
            scope.launch {
                runCatching {
                    component.resolveNoteConflict(
                        localId,
                        reviewed.localRevision,
                        requireNotNull(reviewed.remote.etag),
                        keepLocalCopy,
                        merge
                    )
                }
                    .onSuccess { resolved ->
                        if (resolved) {
                            showConflictResolution = false
                        } else {
                            conflictResolutionError =
                                resources.getString(R.string.conflict_changed)
                        }
                    }
                    .onFailure {
                        conflictResolutionError = it.message
                            ?: resources.getString(R.string.conflict_server_version_load_failed)
                    }
                resolvingConflict = false
            }
        }
    }
    LaunchedEffect(showConflictResolution, note?.localRevision) {
        if (!showConflictResolution) return@LaunchedEffect
        resolvingConflict = true
        conflictResolutionError = null
        try {
            conflictSnapshot = withContext(UiDispatcher) { component.noteConflict(localId) }
            if (conflictSnapshot == null) {
                conflictResolutionError = resources.getString(R.string.conflict_changed)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            conflictResolutionError = error.message
                ?: resources.getString(R.string.conflict_server_version_load_failed)
        } finally {
            resolvingConflict = false
        }
    }
    val resolveRemoteMissing = { recreate: Boolean ->
        resolvingRemoteMissing = true
        remoteMissingResolutionError = null
        scope.launch {
            runCatching { component.resolveRemoteMissing(localId, recreate) }
                .onSuccess { resolved ->
                    if (resolved) {
                        showRemoteMissingResolution = false
                        if (!recreate) onBackToList()
                    } else {
                        remoteMissingResolutionError =
                            resources.getString(R.string.remote_missing_changed)
                    }
                }
                .onFailure {
                    remoteMissingResolutionError =
                        it.message ?: resources.getString(R.string.remote_missing_resolve_failed)
                }
            resolvingRemoteMissing = false
        }
    }
    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                TopAppBar(
                    title = {
                        Text(
                            note?.title ?: stringResource(R.string.note_title_fallback),
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
                                        if (component.saveDraft(localId, source)) leaveNoteScreen()
                                    }
                                } else {
                                    leaveNoteScreen()
                                }
                            },
                            modifier = Modifier.testTag("back-to-note-list")
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back_to_notes)
                            )
                        }
                    },
                    actions = {
                        val current = note
                        IconButton(
                            onClick = {
                                finding = !finding
                                if (!finding) closeFind()
                            },
                            modifier = Modifier.testTag("find-in-note")
                        ) {
                            Icon(
                                Icons.Filled.Search,
                                contentDescription = stringResource(R.string.find_in_note)
                            )
                        }
                        if (!editing) {
                            if (
                                current != null &&
                                !current.readOnly &&
                                !hasEncryptedContent &&
                                current.syncState !in
                                setOf(
                                    SyncState.CONFLICT,
                                    SyncState.REMOTE_MISSING,
                                    SyncState.READ_ONLY_CONFLICT
                                ) &&
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
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = stringResource(
                                            R.string.action_edit_note
                                        )
                                    )
                                }
                            }
                            Box {
                                IconButton(
                                    onClick = { noteMenuOpen = true },
                                    modifier = Modifier.testTag("note-menu")
                                ) {
                                    Icon(
                                        Icons.Filled.MoreVert,
                                        contentDescription = stringResource(
                                            R.string.more_note_actions
                                        )
                                    )
                                }
                                DropdownMenu(
                                    expanded = noteMenuOpen,
                                    onDismissRequest = { noteMenuOpen = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.text_size_decrease))
                                        },
                                        onClick = {
                                            noteMenuOpen = false
                                            component.settings.decreaseNoteTextSize()
                                        },
                                        enabled = NoteTextSize.canDecrease(noteTextSizeSp),
                                        modifier = Modifier.testTag("decrease-note-text-size")
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Text(stringResource(R.string.text_size_increase))
                                        },
                                        onClick = {
                                            noteMenuOpen = false
                                            component.settings.increaseNoteTextSize()
                                        },
                                        enabled = NoteTextSize.canIncrease(noteTextSizeSp),
                                        modifier = Modifier.testTag("increase-note-text-size")
                                    )
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Checkbox(
                                                    checked = loadImages,
                                                    onCheckedChange = { loadImages = it }
                                                )
                                                Text(stringResource(R.string.load_images))
                                            }
                                        },
                                        onClick = { loadImages = !loadImages },
                                        modifier = Modifier.testTag("toggle-load-images")
                                    )
                                    if (current?.remoteId != null) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.note_menu_versions))
                                            },
                                            onClick = {
                                                noteMenuOpen = false
                                                showVersions()
                                            },
                                            modifier = Modifier.testTag("note-versions")
                                        )
                                    }
                                    if (nextcloudDeckAvailable) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.deck_cards)) },
                                            leadingIcon = {
                                                Icon(
                                                    Icons.Filled.ViewKanban,
                                                    contentDescription = null
                                                )
                                            },
                                            onClick = {
                                                noteMenuOpen = false
                                                showNoteDeckBrowser = true
                                            },
                                            modifier = Modifier.testTag("browse-note-deck-cards")
                                        )
                                    }
                                    if (
                                        current != null &&
                                        noteTagState.availability != NoteTagAvailability.UNKNOWN
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.note_menu_tags))
                                            },
                                            onClick = {
                                                noteMenuOpen = false
                                                editingTags = true
                                            },
                                            modifier = Modifier.testTag("edit-note-tags")
                                        )
                                    }
                                    if (current != null) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.note_menu_information))
                                            },
                                            onClick = {
                                                noteMenuOpen = false
                                                showingInformation = true
                                            },
                                            modifier = Modifier.testTag("note-information")
                                        )
                                    }
                                    if (
                                        current != null &&
                                        useSubfolders &&
                                        !current.readOnly &&
                                        current.syncState !in
                                        setOf(
                                            SyncState.CONFLICT,
                                            SyncState.REMOTE_MISSING,
                                            SyncState.READ_ONLY_CONFLICT
                                        )
                                    ) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.change_category))
                                            },
                                            onClick = {
                                                noteMenuOpen = false
                                                changingCategory = true
                                            },
                                            modifier = Modifier.testTag("change-note-category")
                                        )
                                    }
                                    if (
                                        current != null &&
                                        !current.readOnly &&
                                        current.syncState !in
                                        setOf(
                                            SyncState.CONFLICT,
                                            SyncState.REMOTE_MISSING,
                                            SyncState.READ_ONLY_CONFLICT
                                        )
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text(stringResource(R.string.action_rename)) },
                                            onClick = {
                                                noteMenuOpen = false
                                                noteName = current.title
                                                updateHeading = true
                                                renaming = true
                                            },
                                            modifier = Modifier.testTag("rename-note")
                                        )
                                    }
                                    if (current != null) {
                                        DropdownMenuItem(
                                            text = {
                                                Text(stringResource(R.string.action_move_to_trash))
                                            },
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
                                            text = { Text(stringResource(R.string.retry_sync)) },
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
                            description = stringResource(R.string.note_text_size_decrease),
                            testTag = "decrease-note-text-size",
                            enabled = NoteTextSize.canDecrease(noteTextSizeSp),
                            onClick = component.settings::decreaseNoteTextSize
                        )
                        ActionIconButton(
                            icon = Icons.Filled.TextIncrease,
                            description = stringResource(R.string.note_text_size_increase),
                            testTag = "increase-note-text-size",
                            enabled = NoteTextSize.canIncrease(noteTextSizeSp),
                            onClick = component.settings::increaseNoteTextSize
                        )
                        ActionIconButton(
                            icon = Icons.Filled.Close,
                            description = stringResource(R.string.action_cancel_editing),
                            testTag = "cancel-editing",
                            onClick = {
                                if (draft != contentBeforeEditing) {
                                    showDiscardConfirmation = true
                                } else {
                                    draft?.let { source ->
                                        scope.launch {
                                            if (component.saveDraft(localId, source)) {
                                                leaveEditMode()
                                            }
                                        }
                                    } ?: leaveEditMode()
                                }
                            }
                        )
                        ActionIconButton(
                            icon = Icons.Filled.Done,
                            description = stringResource(R.string.action_finish_editing),
                            testTag = "finish-editing",
                            onClick = {
                                val source = draft
                                if (source != null) {
                                    scope.launch {
                                        if (component.saveDraft(localId, source)) leaveEditMode()
                                    }
                                }
                            }
                        )
                    }
                } else if (current?.readOnly == true) {
                    Text(
                        stringResource(R.string.read_only),
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
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
                    if (
                        note?.syncState in setOf(SyncState.CONFLICT, SyncState.READ_ONLY_CONFLICT)
                    ) {
                        Text(
                            stringResource(R.string.local_changes_safe),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        TextButton(
                            onClick = openConflictResolution,
                            modifier = Modifier.padding(horizontal = 4.dp)
                                .testTag("resolve-note-conflict")
                        ) { Text(stringResource(R.string.resolve_conflict)) }
                    } else if (note?.syncState == SyncState.REMOTE_MISSING) {
                        Text(
                            stringResource(R.string.local_changes_safe_finish_editing),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
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
                                findNavigationRequest++
                            }
                        },
                        onNext = {
                            if (matches.isNotEmpty()) {
                                currentMatch = (currentMatch + 1) % matches.size
                                findNavigationRequest++
                            }
                        },
                        onClose = closeFind,
                        focusOnOpen = !findFromListSearch
                    )
                } else {
                    // Undo and redo stay first, so stepping back does not require scrolling.
                    EditorToolbar(
                        deckAvailable = nextcloudDeckAvailable,
                        showLabels = showToolbarLabels,
                        isEnabled = { tool ->
                            when (tool) {
                                EditorTool.UNDO -> canUndo
                                EditorTool.REDO -> canRedo
                                EditorTool.IMAGE -> !importingImage
                                else -> true
                            }
                        },
                        onTool = { tool ->
                            fun format(action: MarkdownFormatAction) {
                                editor?.applyFormat(action)
                                // Tapping a Compose button moves focus away from the embedded
                                // editor, which closes the keyboard. Hand focus back so
                                // formatting does not interrupt typing.
                                editor?.focusForInput()
                            }
                            when (tool) {
                                // The framework editor's own undo buffer is only reachable
                                // with a hardware keyboard, so phones need these controls.
                                EditorTool.UNDO -> {
                                    editorBinding?.undo()
                                    editor?.focusForInput()
                                }
                                EditorTool.REDO -> {
                                    editorBinding?.redo()
                                    editor?.focusForInput()
                                }
                                EditorTool.HEADING -> format(MarkdownFormatAction.HEADING)
                                EditorTool.BOLD -> format(MarkdownFormatAction.BOLD)
                                EditorTool.ITALIC -> format(MarkdownFormatAction.ITALIC)
                                EditorTool.STRIKETHROUGH ->
                                    format(MarkdownFormatAction.STRIKETHROUGH)
                                EditorTool.CODE -> format(MarkdownFormatAction.CODE)
                                EditorTool.QUOTE -> format(MarkdownFormatAction.QUOTE)
                                EditorTool.BULLET_LIST -> format(MarkdownFormatAction.BULLET)
                                EditorTool.NUMBERED_LIST -> format(MarkdownFormatAction.NUMBERED)
                                EditorTool.CHECKBOX_LIST -> format(MarkdownFormatAction.TASK)
                                EditorTool.INDENT -> format(MarkdownFormatAction.INDENT)
                                EditorTool.OUTDENT -> format(MarkdownFormatAction.OUTDENT)
                                EditorTool.LINK -> {
                                    val current = editor
                                    val source = current?.text?.toString().orEmpty()
                                    val start = (current?.selectionStart ?: 0)
                                        .coerceIn(0, source.length)
                                    val end = (current?.selectionEnd ?: start)
                                        .coerceIn(0, source.length)
                                    linkDialogTitle =
                                        source.substring(minOf(start, end), maxOf(start, end))
                                    linkDialogUrl = current?.clipboardWebUrl().orEmpty()
                                }
                                EditorTool.IMAGE -> imagePicker.launch("image/*")
                                EditorTool.DATE -> {
                                    editor?.insertText(
                                        LocalDateTime.now()
                                            .format(DateTimeFormatter.ISO_LOCAL_DATE)
                                    )
                                    editor?.focusForInput()
                                }
                                EditorTool.DATE_TIME -> {
                                    editor?.insertText(
                                        LocalDateTime.now().format(
                                            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                        )
                                    )
                                    editor?.focusForInput()
                                }
                                EditorTool.CREATE_DECK_CARD -> {
                                    val current = editor
                                    deckCardTitle = deckCardTitleFromSelection(
                                        current?.text,
                                        current?.selectionStart ?: 0,
                                        current?.selectionEnd ?: 0
                                    )
                                }
                                EditorTool.BROWSE_DECK_CARDS -> showNoteDeckBrowser = true
                            }
                        },
                        onHelp = openToolbarHelp
                    )
                    if (!toolbarHintDismissed) {
                        EditorToolbarHint(
                            onShowHelp = openToolbarHelp,
                            onDismiss = { component.settings.setEditorToolbarHintDismissed(true) }
                        )
                    }
                }
                if (!supportsMarkdownSourceHighlighting(draft.orEmpty().length)) {
                    Text(
                        stringResource(R.string.large_note_highlighting_disabled),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("large-note-highlighting-disabled")
                    )
                }
                Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
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
                                view.onVerticalScrollChanged = { value, range ->
                                    editorScrollValue = value
                                    editorScrollRange = range
                                }
                                view.setOnFocusChangeListener { focusedView, hasFocus ->
                                    if (!hasFocus && editing) {
                                        component.checkpointDraftInBackground(
                                            localId,
                                            (focusedView as MarkdownEditText).text
                                                ?.toString()
                                                .orEmpty()
                                        )
                                    }
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
                                if (!finding) view.focusForInput()
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
                            view.onVerticalScrollChanged = null
                            view.releaseInputFocus()
                        },
                        modifier = Modifier.fillMaxSize().padding(end = 48.dp)
                            .testTag("markdown-editor")
                    )
                    EditorFastScroller(
                        scrollValue = editorScrollValue,
                        scrollRange = editorScrollRange,
                        onScrollTo = { editor?.scrollVerticallyTo(it) },
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
                            closeFind()
                        },
                        focusOnOpen = !findFromListSearch
                    )
                }
                if (noteTags.isNotEmpty()) {
                    Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                        NoteTagLine(tags = noteTags, testTag = "note-tags")
                    }
                }
                note?.lastSyncError?.let { message ->
                    ExpandableSyncError(
                        message = message,
                        technicalDetails = noteSyncDiagnostics[localId],
                        testTag = "note-sync-error",
                        modifier = Modifier.padding(16.dp)
                    )
                    if (
                        note?.syncState in
                        setOf(SyncState.CONFLICT, SyncState.READ_ONLY_CONFLICT)
                    ) {
                        Text(
                            stringResource(R.string.local_changes_safe),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        TextButton(
                            onClick = openConflictResolution,
                            modifier = Modifier.padding(horizontal = 4.dp)
                                .testTag("resolve-note-conflict")
                        ) { Text(stringResource(R.string.resolve_conflict)) }
                    } else if (note?.syncState == SyncState.REMOTE_MISSING) {
                        Text(
                            stringResource(R.string.local_changes_safe),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        TextButton(
                            onClick = {
                                remoteMissingResolutionError = null
                                showRemoteMissingResolution = true
                            },
                            modifier = Modifier.padding(horizontal = 4.dp)
                                .testTag("resolve-remote-missing")
                        ) { Text(stringResource(R.string.resolve_missing_note)) }
                    }
                }
                Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    AndroidView(
                        factory = { context ->
                            AppCompatTextView(context).also {
                                it.id = R.id.markdown_view
                                it.setTextSize(
                                    TypedValue.COMPLEX_UNIT_SP,
                                    noteTextSizeSp.toFloat()
                                )
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
                                listOf(
                                    source,
                                    accountNotes,
                                    pendingHeading,
                                    loadImages,
                                    source?.remoteId,
                                    account?.ssoAccountName
                                )
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
                                    onAttachmentLink = { path ->
                                        val remoteId = source?.remoteId
                                        val accountName = account?.ssoAccountName
                                        if (remoteId != null && !accountName.isNullOrBlank()) {
                                            scope.launch {
                                                val result = component.openAttachment(
                                                    remoteId,
                                                    path,
                                                    accountName
                                                )
                                                val message = when (result) {
                                                    AttachmentOpenResult.OPENED -> null
                                                    AttachmentOpenResult.FETCH_FAILED ->
                                                        view.context.getString(
                                                            R.string.attachment_download_failed
                                                        )
                                                    AttachmentOpenResult.NO_VIEWER ->
                                                        view.context.getString(
                                                            R.string.attachment_no_viewer
                                                        )
                                                }
                                                message?.let {
                                                    Toast.makeText(
                                                        view.context,
                                                        it,
                                                        Toast.LENGTH_SHORT
                                                    )
                                                        .show()
                                                }
                                            }
                                        }
                                    },
                                    onExternalLink = { url ->
                                        val currentAccount = account
                                        if (currentAccount != null &&
                                            org.qownnotes.mobile.core.NextcloudDeck.parseCardLink(
                                                url,
                                                currentAccount.serverUrl
                                            ) != null
                                        ) {
                                            deckLinkUrl = url
                                            true
                                        } else {
                                            false
                                        }
                                    },
                                    onTaskToggle = if (
                                        source != null &&
                                        !source.readOnly &&
                                        source.syncState !in
                                        setOf(
                                            SyncState.CONFLICT,
                                            SyncState.REMOTE_MISSING,
                                            SyncState.READ_ONLY_CONFLICT
                                        )
                                    ) {
                                        { taskIndex ->
                                            if (!togglingTask) {
                                                toggleTaskListItem(source.content, taskIndex)?.let {
                                                    togglingTask = true
                                                    scope.launch {
                                                        component.replaceNoteContent(
                                                            localId,
                                                            it
                                                        )
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
                                    loadRemoteImages = loadImages,
                                    remoteId = source?.remoteId,
                                    accountName = account?.ssoAccountName.orEmpty()
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
                        modifier = Modifier.fillMaxWidth().padding(end = 48.dp)
                            .verticalScroll(scrollState).padding(20.dp).testTag("markdown-view")
                    )
                    NoteFastScroller(
                        scrollState = scrollState,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
        }
    }
    if (showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirmation = false },
            icon = { Icon(Icons.Filled.EditOff, contentDescription = null) },
            title = { Text(stringResource(R.string.discard_changes_title)) },
            text = {
                Text(
                    stringResource(R.string.discard_changes_message)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardConfirmation = false
                        val restored = contentBeforeEditing
                        if (restored != null) {
                            scope.launch {
                                component.replaceNoteContent(localId, restored)
                                draft = restored
                                leaveEditMode()
                            }
                        } else {
                            leaveEditMode()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm-discard-changes")
                ) { Text(stringResource(R.string.action_discard)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardConfirmation = false }) {
                    Text(stringResource(R.string.action_keep_editing))
                }
            }
        )
    }
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            icon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = {
                Text(
                    stringResource(R.string.delete_note_message)
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
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm-delete-note")
                ) { Text(stringResource(R.string.action_move_to_trash)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    if (showConflictResolution) {
        val mergeResult = conflictSnapshot?.let(::mergeNoteConflict)
        AlertDialog(
            onDismissRequest = {
                if (!resolvingConflict) showConflictResolution = false
            },
            icon = { Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null) },
            title = { Text(stringResource(R.string.resolve_note_conflict)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState())
                ) {
                    Text(
                        stringResource(
                            if (note?.syncState == SyncState.READ_ONLY_CONFLICT) {
                                R.string.conflict_read_only_message
                            } else {
                                R.string.conflict_message
                            }
                        )
                    )
                    conflictResolutionError?.let {
                        DialogErrorPanel(message = it, messageTag = "conflict-resolution-error")
                    }
                    if (resolvingConflict) {
                        DialogLoadingIndicator("conflict-resolution-progress")
                    }
                    conflictSnapshot?.let { conflict ->
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            if (maxWidth >= 480.dp) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ConflictVersionCard(
                                        stringResource(R.string.conflict_local_version),
                                        conflict.local,
                                        Modifier.weight(1f).testTag("conflict-local-version")
                                    )
                                    ConflictVersionCard(
                                        stringResource(R.string.conflict_server_version),
                                        conflict.remote,
                                        Modifier.weight(1f).testTag("conflict-server-version")
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    ConflictVersionCard(
                                        stringResource(R.string.conflict_local_version),
                                        conflict.local,
                                        Modifier.testTag("conflict-local-version")
                                    )
                                    ConflictVersionCard(
                                        stringResource(R.string.conflict_server_version),
                                        conflict.remote,
                                        Modifier.testTag("conflict-server-version")
                                    )
                                }
                            }
                        }
                        ConflictVersionCard(
                            stringResource(R.string.conflict_common_base),
                            conflict.base,
                            Modifier.testTag("conflict-base-version")
                        )
                        if (mergeResult?.isClean == false) {
                            val fieldNames = mapOf(
                                NoteMergeField.TITLE to stringResource(R.string.merge_field_title),
                                NoteMergeField.CONTENT to
                                    stringResource(R.string.merge_field_content),
                                NoteMergeField.CATEGORY to
                                    stringResource(R.string.merge_field_category),
                                NoteMergeField.FAVORITE to
                                    stringResource(R.string.merge_field_favorite)
                            )
                            DialogErrorPanel(
                                message = stringResource(
                                    R.string.conflict_merge_unavailable,
                                    mergeResult.conflicts.joinToString(
                                        stringResource(R.string.list_separator)
                                    ) {
                                        fieldNames.getValue(it)
                                    }
                                ),
                                messageTag = "conflict-merge-unavailable"
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = { resolveConflict(false, true) },
                                enabled = !resolvingConflict && mergeResult?.isClean == true,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("merge-conflict-versions")
                            ) {
                                IconLabel(
                                    Icons.AutoMirrored.Filled.MergeType,
                                    stringResource(R.string.conflict_use_merged)
                                )
                            }
                            OutlinedButton(
                                onClick = { resolveConflict(true, false) },
                                enabled = !resolvingConflict,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("keep-local-conflict-copy")
                            ) {
                                IconLabel(
                                    Icons.Filled.ContentCopy,
                                    stringResource(R.string.conflict_keep_local_copy)
                                )
                            }
                            OutlinedButton(
                                onClick = { resolveConflict(false, false) },
                                enabled = !resolvingConflict,
                                modifier = Modifier.fillMaxWidth()
                                    .testTag("use-server-conflict-version")
                            ) {
                                IconLabel(
                                    Icons.Filled.CloudDownload,
                                    stringResource(R.string.conflict_use_server)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showConflictResolution = false },
                    enabled = !resolvingConflict
                ) { Text(stringResource(R.string.action_close)) }
            }
        )
    }
    if (showRemoteMissingResolution) {
        AlertDialog(
            onDismissRequest = {
                if (!resolvingRemoteMissing) showRemoteMissingResolution = false
            },
            icon = { Icon(Icons.Filled.CloudOff, contentDescription = null) },
            title = { Text(stringResource(R.string.resolve_missing_note)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.remote_missing_message)
                    )
                    remoteMissingResolutionError?.let {
                        DialogErrorPanel(
                            message = it,
                            messageTag = "remote-missing-resolution-error"
                        )
                    }
                    if (resolvingRemoteMissing) {
                        DialogLoadingIndicator("remote-missing-resolution-progress")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { resolveRemoteMissing(true) },
                    enabled = !resolvingRemoteMissing,
                    modifier = Modifier.testTag("recreate-remote-missing")
                ) { Text(stringResource(R.string.remote_missing_recreate)) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = { resolveRemoteMissing(false) },
                        enabled = !resolvingRemoteMissing,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.testTag("discard-remote-missing")
                    ) { Text(stringResource(R.string.remote_missing_discard_local)) }
                    TextButton(
                        onClick = { showRemoteMissingResolution = false },
                        enabled = !resolvingRemoteMissing
                    ) { Text(stringResource(R.string.action_cancel)) }
                }
            }
        )
    }
    val pendingLinkUrl = linkDialogUrl
    if (pendingLinkUrl != null && editing) {
        InsertLinkDialog(
            initialUrl = pendingLinkUrl,
            initialTitle = linkDialogTitle,
            onDismiss = {
                linkDialogUrl = null
                editor?.focusForInput()
            },
            onInsert = { link ->
                linkDialogUrl = null
                editor?.insertText(link)
                editor?.focusForInput()
            }
        )
    }
    if (showToolbarHelp && editing) {
        EditorToolbarHelpDialog(
            deckAvailable = nextcloudDeckAvailable,
            showLabels = showToolbarLabels,
            onShowLabelsChange = component.settings::setShowEditorToolbarLabels,
            onDismiss = {
                showToolbarHelp = false
                editor?.focusForInput()
            }
        )
    }
    val deckAccountId = account?.id
    if (showNoteDeckBrowser && account != null) {
        DeckCardBrowserDialog(
            component,
            account,
            onDismiss = {
                showNoteDeckBrowser = false
                if (editing) editor?.focusForInput()
            },
            onInsert = if (editing) {
                { link ->
                    showNoteDeckBrowser = false
                    editor?.insertText(link)
                    editor?.focusForInput()
                }
            } else {
                null
            }
        )
    }
    val pendingDeckLink = deckLinkUrl
    if (pendingDeckLink != null && account != null) {
        DeckCardLinkDialog(component, account, pendingDeckLink, onDismiss = {
            deckLinkUrl = null
        })
    }
    val pendingDeckCardTitle = deckCardTitle
    if (pendingDeckCardTitle != null && editing && nextcloudDeckAvailable &&
        deckAccountId != null
    ) {
        NextcloudDeckCardDialog(
            component = component,
            accountId = deckAccountId,
            initialTitle = pendingDeckCardTitle,
            onDismiss = {
                deckCardTitle = null
                editor?.focusForInput()
            },
            onCreated = { link ->
                deckCardTitle = null
                // The modal dialog kept the editor's selection, so the link replaces the text that
                // became the card title, as in the desktop application.
                editor?.insertText(link)
                editor?.focusForInput()
            }
        )
    }
    if (renaming) {
        RenameNoteDialog(
            name = noteName,
            onNameChange = { noteName = it },
            updateHeading = updateHeading,
            onUpdateHeadingChange = { updateHeading = it },
            onDismiss = { renaming = false },
            onConfirm = {
                renaming = false
                scope.launch {
                    component.renameNote(localId, noteName, updateHeading)
                }
                updateHeading = true
            }
        )
    }
    if (changingCategory) {
        val current = note
        if (current != null) {
            ChangeNoteCategoryDialog(
                currentCategory = current.category,
                folders = remember(accountNotes) {
                    NoteFolders.tree(
                        accountNotes.map(NoteListItem::category),
                        component.nestedFolders
                    ).flatten()
                },
                onDismiss = { changingCategory = false },
                onConfirm = { category ->
                    changingCategory = false
                    scope.launch { component.moveNoteToCategory(localId, category) }
                }
            )
        }
    }
    if (editingTags) {
        NoteTagsDialog(
            state = noteTagState,
            noteTagIds = noteTags.mapTo(mutableSetOf(), NoteTag::id),
            onToggle = { path, linked ->
                scope.launch { component.setNoteTag(localId, path, linked) }
            },
            onDismiss = { editingTags = false }
        )
    }
    if (showingInformation) {
        note?.let { current ->
            NoteInformationDialog(
                note = current,
                account = account,
                onDismiss = { showingInformation = false }
            )
        }
    }
    if (versionToRestore == null) {
        when (val state = versionsState) {
            ArchiveLoadState.Idle -> Unit
            ArchiveLoadState.Loading -> ArchiveLoadingDialog(
                title = stringResource(R.string.note_versions),
                onDismiss = {
                    versionsRequestId++
                    versionsState = ArchiveLoadState.Idle
                }
            )
            is ArchiveLoadState.Failed -> ArchiveErrorDialog(
                title = stringResource(R.string.note_versions),
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
            icon = { Icon(Icons.Filled.Restore, contentDescription = null) },
            title = { Text(stringResource(R.string.restore_version_title)) },
            text = {
                Text(
                    stringResource(R.string.restore_version_message)
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
                                    resources.getString(R.string.restore_version_update_failed)
                                }
                            }
                            val result = restored.fold(
                                onSuccess = { ArchiveLoadState.Idle },
                                onFailure = {
                                    ArchiveLoadState.Failed(
                                        it.message
                                            ?: resources.getString(R.string.restore_version_failed)
                                    )
                                }
                            )
                            if (versionsRequestId == requestId) versionsState = result
                        }
                    },
                    modifier = Modifier.testTag("confirm-restore-note-version")
                ) { Text(stringResource(R.string.action_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { versionToRestore = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun NoteInformationDialog(note: Note, account: Account?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val modified = remember(note.modifiedAtEpochSeconds) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(note.modifiedAtEpochSeconds * 1_000))
    }
    val markdownSize = remember(note.content) {
        Formatter.formatShortFileSize(context, note.content.encodeToByteArray().size.toLong())
    }
    val wordCount = remember(note.content) { Regex("\\S+").findAll(note.content).count() }
    val lineCount = remember(note.content) {
        if (note.content.isEmpty()) 0 else note.content.count { it == '\n' } + 1
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Info, contentDescription = null) },
        title = { Text(stringResource(R.string.note_information_title)) },
        text = {
            SelectionContainer {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                        .testTag("note-information-dialog"),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NoteInformationRow(stringResource(R.string.note_info_modified), modified)
                    NoteInformationRow(
                        stringResource(R.string.note_info_markdown_size),
                        markdownSize
                    )
                    NoteInformationRow(
                        stringResource(R.string.note_info_words),
                        wordCount.toString()
                    )
                    NoteInformationRow(
                        stringResource(R.string.note_info_characters),
                        note.content.length.toString()
                    )
                    NoteInformationRow(
                        stringResource(R.string.note_info_lines),
                        lineCount.toString()
                    )
                    NoteInformationRow(
                        stringResource(R.string.note_info_category),
                        note.category.ifBlank { stringResource(R.string.note_info_category_root) }
                    )
                    account?.let {
                        NoteInformationRow(
                            stringResource(R.string.note_info_account),
                            it.displayName
                        )
                    }
                    NoteInformationRow(
                        stringResource(R.string.note_info_synchronization),
                        stringResource(note.syncState.displayNameRes())
                    )
                    NoteInformationRow(
                        stringResource(R.string.note_info_access),
                        stringResource(
                            if (note.readOnly) R.string.read_only else R.string.note_info_writable
                        )
                    )
                    NoteInformationRow(
                        stringResource(R.string.note_info_favorite),
                        stringResource(if (note.favorite) R.string.yes else R.string.no)
                    )
                    note.remoteId?.let {
                        NoteInformationRow(
                            stringResource(R.string.note_info_note_id),
                            it.toString()
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("close-note-information")
            ) { Text(stringResource(R.string.action_close)) }
        }
    )
}

@Composable
private fun NoteInformationRow(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@StringRes
private fun SyncState.displayNameRes(): Int = when (this) {
    SyncState.SYNCHRONIZED -> R.string.sync_state_synchronized
    SyncState.LOCALLY_CREATED -> R.string.sync_state_locally_created
    SyncState.LOCALLY_MODIFIED -> R.string.sync_state_locally_modified
    SyncState.PENDING_DELETION -> R.string.sync_state_pending_deletion
    SyncState.SYNCHRONIZING -> R.string.sync_state_synchronizing
    SyncState.CONFLICT -> R.string.sync_state_conflict
    SyncState.REMOTE_MISSING -> R.string.sync_state_remote_missing
    SyncState.READ_ONLY_CONFLICT -> R.string.sync_state_read_only_conflict
    SyncState.FAILED -> R.string.sync_state_failed
}

@Composable
private fun ConflictVersionCard(
    label: String,
    version: NoteVersionSnapshot,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            stringResource(
                R.string.conflict_version_summary,
                version.title,
                version.category.ifBlank { stringResource(R.string.category_option_undefined) },
                stringResource(if (version.favorite) R.string.yes else R.string.no)
            ),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                version.content,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().heightIn(max = 96.dp)
                    .verticalScroll(rememberScrollState())
            )
        }
    }
}

@Composable
private fun RenameNoteDialog(
    name: String,
    onNameChange: (String) -> Unit,
    updateHeading: Boolean,
    onUpdateHeadingChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(name, selection = TextRange(0, name.length)))
    }
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
        icon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
        title = { Text(stringResource(R.string.rename_note_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = fieldValue,
                    onValueChange = {
                        fieldValue = it
                        onNameChange(it.text)
                    },
                    label = { Text(stringResource(R.string.rename_note_file_name)) },
                    singleLine = true,
                    supportingText = {
                        Text(stringResource(R.string.note_name_invalid_characters_hint))
                    },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                        .onPlaced { fieldPlaced = true }
                        .testTag("note-name-field")
                )
                // The whole row toggles, so the label is part of the target and the option
                // reports one checked state rather than a box beside unrelated text.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .toggleable(
                            value = updateHeading,
                            onValueChange = onUpdateHeadingChange,
                            role = Role.Checkbox
                        )
                        .padding(vertical = 8.dp)
                        .testTag("update-heading-checkbox")
                ) {
                    Checkbox(checked = updateHeading, onCheckedChange = null)
                    Text(stringResource(R.string.rename_note_update_heading))
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = NoteNames.isValid(name),
                modifier = Modifier.testTag("confirm-rename-note")
            ) { Text(stringResource(R.string.action_rename)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

/** Asks for a new note's name, starting from the name it would otherwise have been given. */
@Composable
private fun NewNoteNameDialog(
    name: String,
    onNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    // The suggested name is selected, so typing replaces it and accepting keeps it.
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(name, selection = TextRange(0, name.length)))
    }
    // Focus is taken once the field is placed in the dialog's window, as in RenameNoteDialog.
    var fieldPlaced by remember { mutableStateOf(false) }
    LaunchedEffect(fieldPlaced) {
        if (fieldPlaced) focusRequester.requestFocus()
    }
    val valid = NoteNames.isValid(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.NoteAdd, contentDescription = null) },
        title = { Text(stringResource(R.string.action_new_note)) },
        text = {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = {
                    fieldValue = it
                    onNameChange(it.text)
                },
                label = { Text(stringResource(R.string.new_note_name)) },
                singleLine = true,
                supportingText = {
                    Text(stringResource(R.string.note_name_invalid_characters_hint))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
                    .onPlaced { fieldPlaced = true }
                    .testTag("new-note-name-field")
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = valid,
                modifier = Modifier.testTag("confirm-new-note")
            ) { Text(stringResource(R.string.action_create)) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel-new-note")
            ) { Text(stringResource(R.string.action_cancel)) }
        },
        modifier = Modifier.testTag("new-note-name-dialog")
    )
}

@Composable
private fun ChangeNoteCategoryDialog(
    currentCategory: String,
    /** Existing folders in tree order, including intermediate folders without notes. */
    folders: List<NoteFolder>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var selectedCategory by rememberSaveable(currentCategory) { mutableStateOf(currentCategory) }
    var newCategory by rememberSaveable(currentCategory) { mutableStateOf("") }
    var creatingCategory by rememberSaveable(currentCategory) { mutableStateOf(false) }
    val normalizedNewCategory = NoteCategories.normalize(newCategory)
    val destination = if (creatingCategory) normalizedNewCategory else selectedCategory
    val validDestination = destination != currentCategory &&
        (!creatingCategory || normalizedNewCategory.isNotEmpty()) &&
        !NoteCategories.isInternal(destination)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
        title = { Text(stringResource(R.string.change_category)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                CategoryDestinationRow(
                    label = stringResource(R.string.category_undefined_root),
                    selected = !creatingCategory && selectedCategory.isEmpty(),
                    icon = Icons.Filled.Home,
                    testTag = "note-category-root",
                    onClick = {
                        creatingCategory = false
                        selectedCategory = ""
                    }
                )
                folders.forEach { folder ->
                    CategoryDestinationRow(
                        label = folder.name,
                        selected = !creatingCategory &&
                            NoteFolders.key(selectedCategory) == NoteFolders.key(folder.path),
                        testTag = "note-category-${folder.path}",
                        depth = folder.depth + 1,
                        onClick = {
                            creatingCategory = false
                            selectedCategory = folder.path
                        }
                    )
                }
                CategoryDestinationRow(
                    label = stringResource(R.string.category_new),
                    selected = creatingCategory,
                    icon = Icons.Filled.CreateNewFolder,
                    testTag = "note-category-new",
                    onClick = { creatingCategory = true }
                )
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = {
                        newCategory = it
                        creatingCategory = true
                    },
                    label = { Text(stringResource(R.string.category_new_path)) },
                    supportingText = { Text(stringResource(R.string.category_new_path_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        .testTag("new-note-category-field")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(destination) },
                enabled = validDestination,
                modifier = Modifier.testTag("confirm-note-category")
            ) { Text(stringResource(R.string.action_move)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

@Composable
private fun CategoryDestinationRow(
    label: String,
    selected: Boolean,
    testTag: String,
    /** Nesting level, indenting folders below the root. */
    depth: Int = 0,
    icon: ImageVector = Icons.Filled.Folder,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(start = (12 + depth * 16).dp, end = 12.dp, top = 10.dp, bottom = 10.dp)
            .testTag(testTag)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun NoteFastScroller(scrollState: ScrollState, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope { UiDispatcher }
    FastScroller(
        scrollValue = scrollState.value,
        scrollRange = scrollState.maxValue,
        onScrollTo = { value -> scope.launch { scrollState.scrollTo(value) } },
        contentDescription = stringResource(R.string.note_fast_scroll),
        testTag = "note-fast-scroll",
        modifier = modifier
    )
}

@Composable
internal fun EditorFastScroller(
    scrollValue: Int,
    scrollRange: Int,
    onScrollTo: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    FastScroller(
        scrollValue = scrollValue,
        scrollRange = scrollRange,
        onScrollTo = onScrollTo,
        contentDescription = stringResource(R.string.editor_fast_scroll),
        testTag = "editor-fast-scroll",
        modifier = modifier
    )
}

@Composable
private fun FastScroller(
    scrollValue: Int,
    scrollRange: Int,
    onScrollTo: (Int) -> Unit,
    contentDescription: String,
    testTag: String,
    modifier: Modifier = Modifier
) {
    if (scrollRange <= 0) return
    BoxWithConstraints(
        modifier = modifier.fillMaxHeight().width(48.dp)
            .semantics { this.contentDescription = contentDescription }
            .testTag(testTag)
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
        val thumbOffset = if (scrollRange == 0) 0F else travel * scrollValue / scrollRange
        fun scrollTo(pointerY: Float) {
            val fraction = ((pointerY - thumbHeight / 2F) / travel).coerceIn(0F, 1F)
            onScrollTo((scrollRange * fraction).roundToInt())
        }
        Box(
            modifier = Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35F))
                .pointerInput(scrollRange, trackHeight, thumbHeight) {
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
 * Finds text in either the rendered note or its editable Markdown source. The bar stays above the
 * text while it scrolls, so the query and position remain visible while moving through matches.
 */
@Composable
private fun FindInNoteBar(
    query: String,
    matchCount: Int,
    currentMatch: Int,
    onQueryChange: (String) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
    focusOnOpen: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    // Opening the bar is a request to type, so take the focus instead of asking for a second tap.
    LaunchedEffect(Unit) { if (focusOnOpen) focusRequester.requestFocus() }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            label = { Text(stringResource(R.string.find_in_note)) },
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
            description = stringResource(R.string.find_previous_match),
            testTag = "find-previous",
            enabled = matchCount > 0,
            onClick = onPrevious
        )
        ActionIconButton(
            icon = Icons.Filled.KeyboardArrowDown,
            description = stringResource(R.string.find_next_match),
            testTag = "find-next",
            enabled = matchCount > 0,
            onClick = onNext
        )
        ActionIconButton(
            icon = Icons.Filled.Close,
            description = stringResource(R.string.find_close),
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
@Composable
private fun findMatchStatus(query: String, matchCount: Int, currentMatch: Int): String = when {
    query.isBlank() -> ""
    matchCount == 0 -> stringResource(R.string.find_no_matches)
    else -> stringResource(
        R.string.find_match_position,
        currentMatch.coerceIn(0, matchCount - 1) + 1,
        matchCount
    )
}

@Composable
private fun ExpandableSyncError(
    message: String,
    technicalDetails: String? = null,
    testTag: String,
    modifier: Modifier = Modifier
) {
    val conflictMessage = when (message) {
        "The note changed on the server" -> R.string.conflict_message
        "The note became read-only while local changes were pending" ->
            R.string.conflict_read_only_message
        else -> null
    }
    // Conflicts have a recovery flow; exception diagnostics remain available in Settings.
    val diagnosticDetails = technicalDetails.takeIf { conflictMessage == null }
    val explanation = syncErrorExplanation(message)?.let { stringResource(it) }
    val context = LocalContext.current
    var showDetails by rememberSaveable(message) { mutableStateOf(false) }
    Column(modifier = modifier.testTag(testTag)) {
        Text(
            conflictMessage?.let { stringResource(it) } ?: message,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.testTag("$testTag-summary")
        )
        if (explanation != null || diagnosticDetails != null) {
            TextButton(
                onClick = { showDetails = true },
                modifier = Modifier.testTag("$testTag-toggle")
            ) {
                Text(stringResource(R.string.sync_error_details))
            }
        }
    }
    if (showDetails) {
        val exceptionClipLabel = stringResource(R.string.sync_error_clip_label)
        AlertDialog(
            onDismissRequest = { showDetails = false },
            icon = { Icon(Icons.Filled.ErrorOutline, contentDescription = null) },
            title = { Text(stringResource(R.string.sync_error_details_title)) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())
                        .testTag("$testTag-details")
                ) {
                    explanation?.let { Text(it) }
                    diagnosticDetails?.let { diagnostic ->
                        Text(
                            stringResource(R.string.sync_error_exception_text),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SelectionContainer {
                                Text(
                                    diagnostic,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(12.dp)
                                        .testTag("$testTag-diagnostic")
                                )
                            }
                        }
                        DialogNotice(stringResource(R.string.sync_error_exception_privacy))
                    }
                }
            },
            confirmButton = {
                diagnosticDetails?.let { diagnostic ->
                    TextButton(
                        onClick = {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                ClipData.newPlainText(exceptionClipLabel, diagnostic)
                            )
                        },
                        modifier = Modifier.testTag("$testTag-copy")
                    ) {
                        IconLabel(
                            Icons.Filled.ContentCopy,
                            stringResource(R.string.sync_error_copy_exception)
                        )
                    }
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDetails = false },
                    modifier = Modifier.testTag("$testTag-close")
                ) { Text(stringResource(R.string.action_close)) }
            },
            modifier = Modifier.testTag("$testTag-dialog")
        )
    }
}

@StringRes
private fun syncErrorExplanation(message: String): Int? = when (message) {
    "The server could not be reached" -> R.string.sync_error_server_unreachable_explanation
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
