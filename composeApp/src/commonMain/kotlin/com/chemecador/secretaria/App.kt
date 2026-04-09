package com.chemecador.secretaria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chemecador.secretaria.notes.FakeNotesRepository
import com.chemecador.secretaria.notes.Note
import com.chemecador.secretaria.notes.NoteDetailScreen
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.notes.NotesScreen
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.noteslists.FakeNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsScreen
import com.chemecador.secretaria.noteslists.NotesListsViewModel

private sealed class Screen {
    data object Lists : Screen()
    data class Notes(val listId: String, val listName: String, val isOrdered: Boolean) : Screen()
    data class NoteDetail(
        val listId: String,
        val listName: String,
        val isOrdered: Boolean,
        val note: Note,
    ) : Screen()
}

@Composable
@Preview
fun App() {
    val listsViewModel = viewModel { NotesListsViewModel(FakeNotesListsRepository()) }
    val notesRepository: NotesRepository = remember { FakeNotesRepository() }

    var screen by remember { mutableStateOf<Screen>(Screen.Lists) }

    SecretariaTheme {
        when (val current = screen) {
            is Screen.Lists -> {
                NotesListsScreen(
                    viewModel = listsViewModel,
                    onListSelected = { id, name, isOrdered ->
                        screen = Screen.Notes(id, name, isOrdered)
                    },
                )
            }

            is Screen.Notes -> {
                val notesViewModel = viewModel(key = current.listId) {
                    NotesViewModel(notesRepository, current.listId)
                }
                NotesScreen(
                    viewModel = notesViewModel,
                    listName = current.listName,
                    isOrdered = current.isOrdered,
                    onNoteClick = { note ->
                        screen = Screen.NoteDetail(
                            current.listId,
                            current.listName,
                            current.isOrdered,
                            note,
                        )
                    },
                    onBack = { screen = Screen.Lists },
                )
            }

            is Screen.NoteDetail -> {
                val notesViewModel = viewModel(key = current.listId) {
                    NotesViewModel(notesRepository, current.listId)
                }
                val backToNotes = Screen.Notes(
                    current.listId,
                    current.listName,
                    current.isOrdered,
                )
                NoteDetailScreen(
                    note = current.note,
                    onSave = { title, content ->
                        notesViewModel.updateNote(current.note.id, title, content)
                        screen = backToNotes
                    },
                    onDelete = {
                        notesViewModel.deleteNote(current.note.id)
                        screen = backToNotes
                    },
                    onBack = { screen = backToNotes },
                )
            }
        }
    }
}
