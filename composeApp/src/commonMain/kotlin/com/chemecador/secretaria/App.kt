package com.chemecador.secretaria

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.chemecador.secretaria.noteslists.FakeNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsPresenter
import com.chemecador.secretaria.noteslists.NotesListsScreen

@Composable
@Preview
fun App() {
    val presenter = remember { NotesListsPresenter(FakeNotesListsRepository()) }

    MaterialTheme {
        NotesListsScreen(presenter = presenter)
    }
}
