package com.chemecador.secretaria.notes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chemecador.secretaria.SecretariaOverflowMenu
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.noteslists.formatNotesListDate
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.create_note_button
import secretaria.composeapp.generated.resources.delete
import secretaria.composeapp.generated.resources.delete_note_message
import secretaria.composeapp.generated.resources.delete_note_title
import secretaria.composeapp.generated.resources.create_note_content_hint
import secretaria.composeapp.generated.resources.create_note_name_hint
import secretaria.composeapp.generated.resources.create_note_title
import secretaria.composeapp.generated.resources.list_created_by
import secretaria.composeapp.generated.resources.note_completed_badge
import secretaria.composeapp.generated.resources.notes_empty
import secretaria.composeapp.generated.resources.notes_error_generic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    listName: String,
    isOrdered: Boolean,
    onNoteClick: (Note) -> Unit,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var noteToDelete by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(listName) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecretariaTopBarColor,
                    titleContentColor = SecretariaTopBarContentColor,
                    navigationIconContentColor = SecretariaTopBarContentColor,
                    actionIconContentColor = SecretariaTopBarContentColor,
                ),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    SecretariaOverflowMenu(
                        onOpenFriends = onOpenFriends,
                        onOpenSettings = onOpenSettings,
                        onLogout = onLogout,
                    )
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = SecretariaTopBarColor,
                contentColor = SecretariaTopBarContentColor,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null)
            }
        },
    ) { innerPadding ->

        if (showCreateDialog) {
            CreateNoteDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { title, content ->
                    viewModel.createNote(title, content)
                    showCreateDialog = false
                },
            )
        }

        noteToDelete?.let { note ->
            DeleteNoteDialog(
                noteTitle = note.title,
                onDismiss = { noteToDelete = null },
                onConfirm = {
                    viewModel.deleteNote(note.id)
                    noteToDelete = null
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> CenteredMessage {
                    CircularProgressIndicator()
                }

                state.errorMessage != null -> CenteredMessage {
                    val errorMessage = state.errorMessage?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.notes_error_generic)
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                state.notes.isEmpty() -> CenteredMessage {
                    Text(
                        text = stringResource(Res.string.notes_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> {
                    val displayNotes = if (isOrdered) {
                        state.notes.sortedBy { it.order }
                    } else {
                        state.notes
                    }
                    NotesContent(
                        notes = displayNotes,
                        isOrdered = isOrdered,
                        onNoteClick = onNoteClick,
                        onNoteLongClick = { noteToDelete = it },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesContent(
    notes: List<Note>,
    isOrdered: Boolean,
    onNoteClick: (Note) -> Unit,
    onNoteLongClick: (Note) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
            NoteCard(
                note = note,
                isOrdered = isOrdered,
                orderIndex = index + 1,
                onClick = { onNoteClick(note) },
                onLongClick = { onNoteLongClick(note) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(
    note: Note,
    isOrdered: Boolean,
    orderIndex: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (isOrdered) "$orderIndex. ${note.title}" else note.title,
                style = MaterialTheme.typography.titleMedium,
            )
            if (note.content.isNotBlank()) {
                Text(
                    text = note.content,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(Res.string.list_created_by, note.creator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatNotesListDate(note.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (note.completed) {
                    Text(
                        text = stringResource(Res.string.note_completed_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun DeleteNoteDialog(
    noteTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.delete_note_title)) },
        text = { Text(stringResource(Res.string.delete_note_message, noteTitle)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun CreateNoteDialog(
    onDismiss: () -> Unit,
    onCreate: (title: String, content: String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.create_note_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(Res.string.create_note_name_hint)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text(stringResource(Res.string.create_note_content_hint)) },
                    minLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim(), content.trim()) },
                enabled = title.isNotBlank(),
            ) {
                Text(stringResource(Res.string.create_note_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}
