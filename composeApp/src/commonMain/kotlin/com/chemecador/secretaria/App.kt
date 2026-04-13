package com.chemecador.secretaria

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import com.chemecador.secretaria.login.LoginScreen
import com.chemecador.secretaria.login.LoginViewModel
import com.chemecador.secretaria.login.createAuthRepository
import com.chemecador.secretaria.login.rememberGoogleSignInController
import com.chemecador.secretaria.notes.Note
import com.chemecador.secretaria.notes.NoteDetailScreen
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.notes.NotesScreen
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.notes.createNotesRepository
import com.chemecador.secretaria.noteslists.NotesListsScreen
import com.chemecador.secretaria.noteslists.NotesListsViewModel
import com.chemecador.secretaria.noteslists.createNotesListsRepository

private sealed class Screen {
    data object Login : Screen()
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
    val authRepository = remember { createAuthRepository() }
    val googleSignInController = rememberGoogleSignInController()
    val loginViewModel = viewModel { LoginViewModel(authRepository) }
    val notesListsRepository = remember { createNotesListsRepository(authRepository) }
    val listsViewModel = viewModel { NotesListsViewModel(notesListsRepository) }
    val notesRepository: NotesRepository = remember { createNotesRepository(authRepository) }

    var screen by remember { mutableStateOf<Screen>(Screen.Login) }
    val coroutineScope = rememberCoroutineScope()

    SecretariaTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .fillMaxSize(),
            ) {
                when (val current = screen) {
                    is Screen.Login -> {
                        LoginScreen(
                            viewModel = loginViewModel,
                            onLoginSuccess = { screen = Screen.Lists },
                            onGoogleLogin = {
                                loginViewModel.loginWithGoogle(
                                    tokenProvider = googleSignInController?.let { controller ->
                                        suspend { controller.getIdToken() }
                                    },
                                )
                            },
                        )
                    }

                    is Screen.Lists -> {
                        NotesListsScreen(
                            viewModel = listsViewModel,
                            onListSelected = { id, name, isOrdered ->
                                screen = Screen.Notes(id, name, isOrdered)
                            },
                            onLogout = {
                                coroutineScope.launch {
                                    authRepository.logout()
                                    googleSignInController?.clearCredentialState()
                                    loginViewModel.resetState()
                                    screen = Screen.Login
                                }
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
    }
}
