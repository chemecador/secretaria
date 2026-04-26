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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.chemecador.secretaria.messaging.FcmTokenRegister
import com.chemecador.secretaria.notes.Note
import com.chemecador.secretaria.notes.NoteDetailScreen
import com.chemecador.secretaria.notes.NotesScreen
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.noteslists.NotesListsScreen
import com.chemecador.secretaria.noteslists.NotesListsSection
import com.chemecador.secretaria.noteslists.NotesListsViewModel
import com.chemecador.secretaria.noteslists.rememberNotesListsSectionPreferenceStore
import com.chemecador.secretaria.settings.SettingsScreen
import com.chemecador.secretaria.settings.SupportCreatorScreen
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
    data object SupportCreator : Screen()
    data class Notes(
        val ownerId: String,
        val listId: String,
        val listName: String,
        val isOrdered: Boolean,
        val backTarget: Screen = Lists,
    ) : Screen()

    data class ListGroup(
        val ownerId: String,
        val groupId: String,
        val groupName: String,
        val isOrdered: Boolean,
    ) : Screen()

    data class NoteDetail(
        val ownerId: String,
        val listId: String,
        val listName: String,
        val isOrdered: Boolean,
        val note: Note,
        val backTarget: Screen = Lists,
    ) : Screen()
}

@Composable
@Preview
fun App(
    googleServerClientId: String? = null,
    openListRequest: OpenListRequest? = null,
    onOpenListRequestConsumed: () -> Unit = {},
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
        val fcmTokenRegister = koinInject<FcmTokenRegister>()
        val googleSignInController = rememberGoogleSignInController(googleServerClientId)
        val notesListsSectionPreferenceStore = rememberNotesListsSectionPreferenceStore()
        val loginViewModel = koinViewModel<LoginViewModel>()
        val listsViewModel = koinViewModel<NotesListsViewModel>()
        val friendsViewModel = koinViewModel<FriendsViewModel>()

        var screen by remember { mutableStateOf<Screen>(Screen.Restoring) }
        var utilityBackStack by remember { mutableStateOf<List<Screen>>(emptyList()) }
        var selectedListsSection by rememberSaveable { mutableStateOf(NotesListsSection.MINE) }
        val coroutineScope = rememberCoroutineScope()

        fun selectListsSection(section: NotesListsSection) {
            selectedListsSection = section
            coroutineScope.launch {
                notesListsSectionPreferenceStore.save(section)
            }
        }

        fun openRequestedList(request: OpenListRequest) {
            utilityBackStack = emptyList()
            screen = if (request.isGroup) {
                Screen.ListGroup(
                    ownerId = request.ownerId,
                    groupId = request.listId,
                    groupName = request.listName,
                    isOrdered = request.isOrdered,
                )
            } else {
                Screen.Notes(
                    ownerId = request.ownerId,
                    listId = request.listId,
                    listName = request.listName,
                    isOrdered = request.isOrdered,
                )
            }
            onOpenListRequestConsumed()
        }

        fun openUtilityScreen(destination: Screen) {
            if (screen == destination) return

            val existingDestinationIndex = utilityBackStack.indexOfLast { it == destination }
            if (existingDestinationIndex >= 0) {
                utilityBackStack = utilityBackStack.take(existingDestinationIndex)
                screen = destination
                return
            }

            if (screen !is Screen.Restoring && screen !is Screen.Login) {
                utilityBackStack = utilityBackStack + screen
            }
            screen = destination
        }

        val openFriends = {
            openUtilityScreen(Screen.Friends)
        }
        val openSettings = {
            openUtilityScreen(Screen.Settings)
        }
        val openSupportCreator = {
            openUtilityScreen(Screen.SupportCreator)
        }
        val closeUtilityScreen = {
            val returnScreen = utilityBackStack.lastOrNull() ?: Screen.Lists
            utilityBackStack = utilityBackStack.dropLast(1)
            screen = returnScreen
        }
        val logout: () -> Unit = {
            coroutineScope.launch {
                fcmTokenRegister.unregisterCurrentToken()
                authRepository.logout()
                googleSignInController?.clearCredentialState()
                loginViewModel.resetState()
                utilityBackStack = emptyList()
                selectedListsSection = NotesListsSection.MINE
                notesListsSectionPreferenceStore.clear()
                screen = Screen.Login
            }
        }

        LaunchedEffect(authRepository, notesListsSectionPreferenceStore) {
            val restored = authRepository.restoreSession().getOrDefault(false)
            selectedListsSection = if (restored) {
                notesListsSectionPreferenceStore.load()
            } else {
                notesListsSectionPreferenceStore.clear()
                NotesListsSection.MINE
            }
            screen = if (restored) Screen.Lists else Screen.Login
            utilityBackStack = emptyList()
            if (restored) {
                fcmTokenRegister.registerCurrentToken()
            }
        }

        LaunchedEffect(openListRequest, screen) {
            val request = openListRequest ?: return@LaunchedEffect
            if (screen is Screen.Restoring || screen is Screen.Login) return@LaunchedEffect
            openRequestedList(request)
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
                                    utilityBackStack = emptyList()
                                    openListRequest?.let(::openRequestedList) ?: run {
                                        screen = Screen.Lists
                                    }
                                    coroutineScope.launch {
                                        fcmTokenRegister.registerCurrentToken()
                                    }
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
                                selectedSection = selectedListsSection,
                                onSectionSelected = ::selectListsSection,
                                onListSelected = { id, ownerId, name, isOrdered ->
                                    screen = Screen.Notes(ownerId, id, name, isOrdered)
                                },
                                onGroupSelected = { id, ownerId, name, isOrdered ->
                                    screen = Screen.ListGroup(ownerId, id, name, isOrdered)
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
                                onOpenSupportCreator = openSupportCreator,
                                onLogout = logout,
                            )
                        }

                        is Screen.SupportCreator -> {
                            SupportCreatorScreen(
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
                                        current.backTarget,
                                    )
                                },
                                onBack = { screen = current.backTarget },
                                onOpenFriends = openFriends,
                                onOpenSettings = openSettings,
                                onLogout = logout,
                            )
                        }

                        is Screen.ListGroup -> {
                            NotesListsScreen(
                                viewModel = listsViewModel,
                                groupOwnerId = current.ownerId,
                                groupId = current.groupId,
                                groupName = current.groupName,
                                groupIsOrdered = current.isOrdered,
                                onListSelected = { id, ownerId, name, isOrdered ->
                                    screen = Screen.Notes(
                                        ownerId = ownerId,
                                        listId = id,
                                        listName = name,
                                        isOrdered = isOrdered,
                                        backTarget = current,
                                    )
                                },
                                onGroupSelected = { _, _, _, _ -> },
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
                                current.backTarget,
                            )
                            NoteDetailScreen(
                                note = current.note,
                                onSave = { title, content, completed, color ->
                                    notesViewModel.updateNote(
                                        noteId = current.note.id,
                                        title = title,
                                        content = content,
                                        completed = completed,
                                        color = color,
                                    )
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
