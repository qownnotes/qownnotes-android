package org.qownnotes.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.qownnotes.mobile.core.Note
import org.qownnotes.mobile.core.NoteFactory
import org.qownnotes.mobile.core.NoteRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { QOwnNotesApp() }
    }
}

@Composable
fun QOwnNotesApp() {
    val application = LocalContext.current.applicationContext as QOwnNotesApplication
    val component = application.component
    LaunchedEffect(component) { component.ensureLocalAccount() }
    QOwnNotesTheme {
        NotesNavigation(component.repository, component.noteFactory)
    }
}

@Composable
private fun QOwnNotesTheme(content: @Composable () -> Unit) {
    val darkTheme = androidx.compose.foundation.isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
        content = content
    )
}

@Composable
private fun NotesNavigation(repository: NoteRepository, noteFactory: NoteFactory) {
    var selectedNoteId by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(enabled = selectedNoteId != null) { selectedNoteId = null }

    val localId = selectedNoteId
    if (localId == null) {
        NoteListScreen(repository, noteFactory, onOpen = { selectedNoteId = it })
    } else {
        NoteDetailScreen(repository, localId)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteListScreen(
    repository: NoteRepository,
    noteFactory: NoteFactory,
    onOpen: (String) -> Unit
) {
    val notes by repository.observeNotes(ApplicationComponent.LOCAL_ACCOUNT_ID)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    val application = LocalContext.current.applicationContext as QOwnNotesApplication
    Scaffold(
        topBar = { TopAppBar(title = { Text("QOwnNotes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                scope.launch {
                    application.component.ensureLocalAccount()
                    val note = noteFactory.create(ApplicationComponent.LOCAL_ACCOUNT_ID)
                    repository.save(note)
                    onOpen(note.localId)
                }
            }) { Text("+") }
        }
    ) { padding ->
        if (notes.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Your notes, available offline",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    "Create a note to verify the local Room foundation.",
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(notes, key = Note::localId) { note ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable {
                            onOpen(note.localId)
                        }.padding(20.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(note.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                note.category.ifBlank {
                                    "Uncategorized"
                                },
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            note.syncState.name.replace('_', ' ').lowercase(),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteDetailScreen(repository: NoteRepository, localId: String) {
    val note by repository.observeNote(localId).collectAsStateWithLifecycle(initialValue = null)
    Scaffold(topBar = { TopAppBar(title = { Text(note?.title ?: "Note") }) }) { padding ->
        Text(
            text = note?.content ?: "Loading...",
            modifier = Modifier.fillMaxSize().padding(padding).padding(20.dp),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
