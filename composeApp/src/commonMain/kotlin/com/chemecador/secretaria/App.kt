package com.chemecador.secretaria

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.Preview
import com.chemecador.secretaria.notes.FakeNotesRepository
import com.chemecador.secretaria.notes.NotesPresenter
import com.chemecador.secretaria.notes.NotesScreen
import com.chemecador.secretaria.noteslists.FakeNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsPresenter
import com.chemecador.secretaria.noteslists.NotesListsScreen

@Composable
@Preview
fun App() {
    val listsPresenter = remember { NotesListsPresenter(FakeNotesListsRepository()) }
    val notesRepository = remember { FakeNotesRepository() }

    var selectedListId by remember { mutableStateOf<String?>(null) }
    var selectedListName by remember { mutableStateOf("") }

    MaterialTheme {
        val currentListId = selectedListId
        if (currentListId == null) {
            NotesListsScreen(
                presenter = listsPresenter,
                onListSelected = { id, name ->
                    selectedListId = id
                    selectedListName = name
                },
            )
        } else {
            val notesPresenter = remember(currentListId) {
                NotesPresenter(notesRepository, currentListId)
            }
            NotesScreen(
                presenter = notesPresenter,
                listName = selectedListName,
                onBack = {
                    selectedListId = null
                    selectedListName = ""
                },
            )
        }
    }
}
