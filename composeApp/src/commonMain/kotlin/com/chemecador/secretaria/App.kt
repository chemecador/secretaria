package com.chemecador.secretaria

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.chemecador.secretaria.notes.FakeNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.notes.NotesScreen
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.noteslists.FakeNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsScreen
import com.chemecador.secretaria.noteslists.NotesListsViewModel

@Composable
@Preview
fun App() {
    val listsViewModel = viewModel { NotesListsViewModel(FakeNotesListsRepository()) }
    val notesRepository: NotesRepository = remember { FakeNotesRepository() }

    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedListName by remember { mutableStateOf("") }

    MaterialTheme {
        val currentListId = selectedListId
        if (currentListId == null) {
            NotesListsScreen(
                viewModel = listsViewModel,
                onListSelected = { id, name ->
                    selectedListId = id
                    selectedListName = name
                },
            )
        } else {
            val notesViewModel = viewModel(key = currentListId) {
                NotesViewModel(notesRepository, currentListId)
            }
            NotesScreen(
                viewModel = notesViewModel,
                listName = selectedListName,
                onBack = {
                    selectedListId = null
                    selectedListName = ""
                },
            )
        }
    }
}
