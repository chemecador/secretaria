package com.chemecador.secretaria

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chemecador.secretaria.di.appModules
import com.chemecador.secretaria.di.previewAppModules
import com.chemecador.secretaria.friends.FriendsScreen
import com.chemecador.secretaria.friends.FriendsViewModel
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.LoginScreen
import com.chemecador.secretaria.login.LoginViewModel
import com.chemecador.secretaria.login.rememberGoogleSignInController
import com.chemecador.secretaria.notes.Note
import com.chemecador.secretaria.notes.NoteDetailScreen
import com.chemecador.secretaria.notes.NotesScreen
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.noteslists.NotesListsScreen
import com.chemecador.secretaria.noteslists.NotesListsViewModel
import com.chemecador.secretaria.settings.SettingsScreen
import kotlinx.coroutines.launch
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import org.koin.dsl.koinConfiguration

private sealed class Screen {
    data object Restoring : Screen()
    data object Login : Screen()
    data object Lists : Screen()
    data object Friends : Screen()
    data object Settings : Screen()
    data class Notes(
        val ownerId: String,
        val listId: String,
        val listName: String,
        val isOrdered: Boolean,
    ) : Screen()

    data class NoteDetail(
        val ownerId: String,
        val listId: String,
        val listName: String,
        val isOrdered: Boolean,
        val note: Note,
    ) : Screen()
}

private fun Screen.canRestoreAfterUtilityScreen(): Boolean {
    return this is Screen.Lists || this is Screen.Notes || this is Screen.NoteDetail
}

@Composable
@Preview
fun App(
    googleServerClientId: String? = null,
) {
    val inspectionMode = LocalInspectionMode.current
    val modules = remember(inspectionMode) {
        if (inspectionMode) previewAppModules() else appModules()
    }
    val koinConfig = remember(modules) {
        koinConfiguration {
            modules(modules)
        }
    }

    KoinApplication(koinConfig) {
        val authRepository = koinInject<AuthRepository>()
        val googleSignInController = rememberGoogleSignInController(googleServerClientId)
        val loginViewModel = koinViewModel<LoginViewModel>()
        val listsViewModel = koinViewModel<NotesListsViewModel>()
        val friendsViewModel = koinViewModel<FriendsViewModel>()

        var screen by remember { mutableStateOf<Screen>(Screen.Restoring) }
        var utilityReturnScreen by remember { mutableStateOf<Screen>(Screen.Lists) }
        val coroutineScope = rememberCoroutineScope()

        val openFriends = {
            if (screen.canRestoreAfterUtilityScreen()) {
                utilityReturnScreen = screen
            }
            screen = Screen.Friends
        }
        val openSettings = {
            if (screen.canRestoreAfterUtilityScreen()) {
                utilityReturnScreen = screen
            }
            screen = Screen.Settings
        }
        val closeUtilityScreen = {
            screen = utilityReturnScreen
        }
        val logout: () -> Unit = {
            coroutineScope.launch {
                authRepository.logout()
                googleSignInController?.clearCredentialState()
                loginViewModel.resetState()
                utilityReturnScreen = Screen.Lists
                screen = Screen.Login
            }
        }

        LaunchedEffect(authRepository) {
            val restored = authRepository.restoreSession().getOrDefault(false)
            screen = if (restored) Screen.Lists else Screen.Login
            utilityReturnScreen = Screen.Lists
        }

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
                        is Screen.Restoring -> {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator()
                            }
                        }

                        is Screen.Login -> {
                            LoginScreen(
                                viewModel = loginViewModel,
                                onLoginSuccess = {
                                    utilityReturnScreen = Screen.Lists
                                    screen = Screen.Lists
                                },
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
                                onListSelected = { id, ownerId, name, isOrdered ->
                                    screen = Screen.Notes(ownerId, id, name, isOrdered)
                                },
                                onOpenFriends = openFriends,
                                onOpenSettings = openSettings,
                                onLogout = logout,
                            )
                        }

                        is Screen.Friends -> {
                            FriendsScreen(
                                viewModel = friendsViewModel,
                                onBack = closeUtilityScreen,
                                onOpenFriends = openFriends,
                                onOpenSettings = openSettings,
                                onLogout = logout,
                            )
                        }

                        is Screen.Settings -> {
                            SettingsScreen(
                                onBack = closeUtilityScreen,
                                onOpenFriends = openFriends,
                                onOpenSettings = openSettings,
                                onLogout = logout,
                            )
                        }

                        is Screen.Notes -> {
                            val notesViewModel =
                                koinViewModel<NotesViewModel>(key = "${current.ownerId}:${current.listId}") {
                                    parametersOf(current.ownerId, current.listId)
                                }
                            NotesScreen(
                                viewModel = notesViewModel,
                                listName = current.listName,
                                isOrdered = current.isOrdered,
                                onNoteClick = { note ->
                                    screen = Screen.NoteDetail(
                                        current.ownerId,
                                        current.listId,
                                        current.listName,
                                        current.isOrdered,
                                        note,
                                    )
                                },
                                onBack = { screen = Screen.Lists },
                                onOpenFriends = openFriends,
                                onOpenSettings = openSettings,
                                onLogout = logout,
                            )
                        }

                        is Screen.NoteDetail -> {
                            val notesViewModel =
                                koinViewModel<NotesViewModel>(key = "${current.ownerId}:${current.listId}") {
                                    parametersOf(current.ownerId, current.listId)
                                }
                            val backToNotes = Screen.Notes(
                                current.ownerId,
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
                                onOpenFriends = openFriends,
                                onOpenSettings = openSettings,
                                onLogout = logout,
                            )
                        }
                    }
                }
            }
        }
    }
}
