package com.chemecador.secretaria.reminders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.chemecador.secretaria.PlatformBackHandler
import com.chemecador.secretaria.SecretariaOverflowMenu
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.notes.NotesReorderState
import com.chemecador.secretaria.noteslists.ListCollaborator
import com.chemecador.secretaria.noteslists.formatNotesListDate
import com.chemecador.secretaria.rememberNotificationPermissionController
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.completed_reminders_empty
import secretaria.composeapp.generated.resources.completed_reminders_title
import secretaria.composeapp.generated.resources.create_reminder_button
import secretaria.composeapp.generated.resources.create_reminder_title
import secretaria.composeapp.generated.resources.delete
import secretaria.composeapp.generated.resources.delete_reminder_message
import secretaria.composeapp.generated.resources.delete_reminder_title
import secretaria.composeapp.generated.resources.edit_reminder_button
import secretaria.composeapp.generated.resources.edit_reminder_text_hint
import secretaria.composeapp.generated.resources.edit_reminder_title
import secretaria.composeapp.generated.resources.leave_shared_reminder
import secretaria.composeapp.generated.resources.leave_shared_reminder_error
import secretaria.composeapp.generated.resources.leave_shared_reminder_message
import secretaria.composeapp.generated.resources.leave_shared_reminder_success
import secretaria.composeapp.generated.resources.leave_shared_reminder_title
import secretaria.composeapp.generated.resources.notifications_disabled_enable
import secretaria.composeapp.generated.resources.notifications_disabled_message
import secretaria.composeapp.generated.resources.notifications_disabled_title
import secretaria.composeapp.generated.resources.reminder_complete_action
import secretaria.composeapp.generated.resources.reminder_completed_error
import secretaria.composeapp.generated.resources.reminder_completed_feedback
import secretaria.composeapp.generated.resources.reminder_completed_on
import secretaria.composeapp.generated.resources.reminder_completed_retention
import secretaria.composeapp.generated.resources.reminder_deleted_error
import secretaria.composeapp.generated.resources.reminder_due_add
import secretaria.composeapp.generated.resources.reminder_due_add_time
import secretaria.composeapp.generated.resources.reminder_due_clear
import secretaria.composeapp.generated.resources.reminder_due_clear_time
import secretaria.composeapp.generated.resources.reminder_due_switch
import secretaria.composeapp.generated.resources.reminder_due_with_time
import secretaria.composeapp.generated.resources.reminder_options
import secretaria.composeapp.generated.resources.reminder_restore_action
import secretaria.composeapp.generated.resources.reminder_restored_error
import secretaria.composeapp.generated.resources.reminder_restored_feedback
import secretaria.composeapp.generated.resources.reminder_shared_badge
import secretaria.composeapp.generated.resources.reminders_empty
import secretaria.composeapp.generated.resources.reminders_error_generic
import secretaria.composeapp.generated.resources.reminders_title
import secretaria.composeapp.generated.resources.reorder_reminder_handle
import secretaria.composeapp.generated.resources.share_list
import secretaria.composeapp.generated.resources.share_list_available_friends
import secretaria.composeapp.generated.resources.share_list_private
import secretaria.composeapp.generated.resources.share_list_shared_with_count_many
import secretaria.composeapp.generated.resources.share_list_shared_with_count_one
import secretaria.composeapp.generated.resources.share_list_shared_with_many
import secretaria.composeapp.generated.resources.share_list_shared_with_one
import secretaria.composeapp.generated.resources.share_list_shared_with_you
import secretaria.composeapp.generated.resources.share_reminder_current_access
import secretaria.composeapp.generated.resources.share_reminder_empty_friends
import secretaria.composeapp.generated.resources.share_reminder_no_available_friends
import secretaria.composeapp.generated.resources.share_reminder_success
import secretaria.composeapp.generated.resources.share_reminder_title
import secretaria.composeapp.generated.resources.unshare_list
import secretaria.composeapp.generated.resources.unshare_reminder_success
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    onOpenCompleted: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    /** Nulo cuando la pantalla es un destino raiz: sin flecha atras y sin back handler. */
    onBack: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
) {
    val currentUserId = koinInject<AuthRepository>().currentUserId
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<Reminder?>(null) }
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }
    var reminderForOptions by remember { mutableStateOf<Reminder?>(null) }
    var reminderToShare by remember { mutableStateOf<Reminder?>(null) }
    var reminderToLeave by remember { mutableStateOf<Reminder?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    ReminderFeedbackHost(
        feedback = state.feedback,
        snackbarHostState = snackbarHostState,
        onConsume = viewModel::consumeFeedback,
    )

    ReminderSharingFeedbackHost(
        feedback = state.shareFeedback,
        snackbarHostState = snackbarHostState,
        onConsume = viewModel::consumeShareFeedback,
    )

    LaunchedEffect(reminderToShare?.key) {
        val selectedReminder = reminderToShare ?: return@LaunchedEffect
        viewModel.loadShareableFriends(selectedReminder)
    }

    onBack?.let { navigateBack ->
        PlatformBackHandler(
            enabled = !showCreateDialog &&
                reminderToEdit == null &&
                reminderToDelete == null &&
                reminderForOptions == null &&
                reminderToShare == null &&
                reminderToLeave == null,
            onBack = navigateBack,
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.reminders_title)) },
                colors = remindersTopAppBarColors(),
                navigationIcon = {
                    onBack?.let { navigateBack ->
                        IconButton(onClick = navigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    SecretariaOverflowMenu(
                        onOpenFriends = onOpenFriends,
                        onOpenSettings = onOpenSettings,
                        onLogout = onLogout,
                        onOpenCompletedReminders = onOpenCompleted,
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
            ReminderEditorDialog(
                title = stringResource(Res.string.create_reminder_title),
                confirmLabel = stringResource(Res.string.create_reminder_button),
                initialText = "",
                initialDue = null,
                onDismiss = { showCreateDialog = false },
                onConfirm = { text, due ->
                    viewModel.createReminder(text, due)
                    showCreateDialog = false
                },
            )
        }

        reminderToEdit?.let { reminder ->
            ReminderEditorDialog(
                title = stringResource(Res.string.edit_reminder_title),
                confirmLabel = stringResource(Res.string.edit_reminder_button),
                initialText = reminder.text,
                initialDue = reminder.due,
                onDismiss = { reminderToEdit = null },
                onConfirm = { text, due ->
                    viewModel.updateReminder(reminder.key, text, due)
                    reminderToEdit = null
                },
            )
        }

        reminderToDelete?.let { reminder ->
            DeleteReminderDialog(
                reminderText = reminder.text,
                onDismiss = { reminderToDelete = null },
                onConfirm = {
                    viewModel.deleteReminder(reminder.key)
                    reminderToDelete = null
                },
            )
        }

        reminderForOptions?.let { reminder ->
            ReminderOptionsDialog(
                reminder = reminder,
                currentUserId = currentUserId,
                onShare = {
                    reminderToShare = reminder
                    reminderForOptions = null
                },
                onDelete = {
                    reminderToDelete = reminder
                    reminderForOptions = null
                },
                onLeaveShared = {
                    reminderToLeave = reminder
                    reminderForOptions = null
                },
                onDismiss = { reminderForOptions = null },
            )
        }

        reminderToShare?.let { reminder ->
            ShareReminderDialog(
                reminderText = reminder.text,
                collaborators = state.collaboratorsByReminderId[reminder.id].orEmpty(),
                friends = state.shareableFriends,
                isLoading = state.isLoadingShareableFriends,
                isUpdatingSharing = state.isUpdatingSharing,
                errorMessage = state.shareErrorMessage,
                onShare = { friend -> viewModel.shareReminder(reminder, friend) },
                onUnshare = { collaborator -> viewModel.unshareReminder(reminder, collaborator) },
                onDismiss = {
                    reminderToShare = null
                    viewModel.clearShareState()
                },
            )
        }

        reminderToLeave?.let { reminder ->
            LeaveSharedReminderDialog(
                reminderText = reminder.text,
                onDismiss = { reminderToLeave = null },
                onConfirm = {
                    viewModel.leaveSharedReminder(reminder)
                    reminderToLeave = null
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading) {
                CenteredMessage {
                    CircularProgressIndicator()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val pending = state.pendingReminders
                    when {
                        state.errorMessage != null -> ScrollableCenteredMessage {
                            Text(
                                text = state.errorMessage?.takeIf { it.isNotBlank() }
                                    ?: stringResource(Res.string.reminders_error_generic),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }

                        pending.isEmpty() -> ScrollableCenteredMessage {
                            Text(
                                text = stringResource(Res.string.reminders_empty),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp),
                            )
                        }

                        else -> PendingRemindersContent(
                            reminders = pending,
                            currentUserId = currentUserId,
                            collaboratorsByReminderId = state.collaboratorsByReminderId,
                            onReminderClick = { reminderToEdit = it },
                            onReminderLongClick = { reminderForOptions = it },
                            onReminderCompleted = { reminder ->
                                viewModel.setReminderCompleted(reminder.key, completed = true)
                            },
                            onRemindersReordered = viewModel::reorderReminders,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Comparte instancia de [RemindersViewModel] con [RemindersScreen], asi que NO llama a `load()`:
 * si lo hiciera, cada visita relanzaria la carga y el purgado de 30 dias.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompletedRemindersScreen(
    viewModel: RemindersViewModel,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentUserId = koinInject<AuthRepository>().currentUserId
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }
    var reminderForOptions by remember { mutableStateOf<Reminder?>(null) }
    var reminderToLeave by remember { mutableStateOf<Reminder?>(null) }

    ReminderFeedbackHost(
        feedback = state.feedback,
        snackbarHostState = snackbarHostState,
        onConsume = viewModel::consumeFeedback,
    )

    PlatformBackHandler(
        enabled = reminderToDelete == null &&
            reminderForOptions == null &&
            reminderToLeave == null,
        onBack = onBack,
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.completed_reminders_title)) },
                colors = remindersTopAppBarColors(),
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
    ) { innerPadding ->

        reminderToDelete?.let { reminder ->
            DeleteReminderDialog(
                reminderText = reminder.text,
                onDismiss = { reminderToDelete = null },
                onConfirm = {
                    viewModel.deleteReminder(reminder.key)
                    reminderToDelete = null
                },
            )
        }

        reminderForOptions?.let { reminder ->
            ReminderOptionsDialog(
                reminder = reminder,
                currentUserId = currentUserId,
                // Compartir un completado no aporta nada: solo queda gestionar el acceso ya dado.
                canShare = false,
                onShare = {},
                onDelete = {
                    reminderToDelete = reminder
                    reminderForOptions = null
                },
                onLeaveShared = {
                    reminderToLeave = reminder
                    reminderForOptions = null
                },
                onDismiss = { reminderForOptions = null },
            )
        }

        reminderToLeave?.let { reminder ->
            LeaveSharedReminderDialog(
                reminderText = reminder.text,
                onDismiss = { reminderToLeave = null },
                onConfirm = {
                    viewModel.leaveSharedReminder(reminder)
                    reminderToLeave = null
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // El borrado automatico debe ser visible, no una sorpresa.
            Text(
                text = stringResource(Res.string.reminder_completed_retention),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )

            val completed = state.completedReminders
            if (completed.isEmpty()) {
                ScrollableCenteredMessage {
                    Text(
                        text = stringResource(Res.string.completed_reminders_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(completed, key = { reminder -> "${reminder.ownerId}/${reminder.id}" }) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            textDecoration = TextDecoration.LineThrough,
                            onClick = {},
                            onLongClick = { reminderForOptions = reminder },
                            sharingSummary = reminderSharingSummary(
                                reminder = reminder,
                                collaborators = state.collaboratorsByReminderId[reminder.id].orEmpty(),
                                currentUserId = currentUserId,
                            ),
                            leading = {
                                IconButton(
                                    onClick = {
                                        viewModel.setReminderCompleted(reminder.key, completed = false)
                                    },
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckBox,
                                        contentDescription = stringResource(
                                            Res.string.reminder_restore_action,
                                        ),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            secondaryContent = reminder.completedAt?.let { completedAt ->
                                {
                                    Text(
                                        text = stringResource(
                                            Res.string.reminder_completed_on,
                                            formatNotesListDate(completedAt),
                                        ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Drag and drop calcado de `notes.OrderedNotesContent`: se reutiliza [NotesReorderState], que ya
 * es el controlador compartido de reordenacion pese a vivir en el paquete `notes`.
 */
@Composable
private fun PendingRemindersContent(
    reminders: List<Reminder>,
    currentUserId: String?,
    collaboratorsByReminderId: Map<String, List<ListCollaborator>>,
    onReminderClick: (Reminder) -> Unit,
    onReminderLongClick: (Reminder) -> Unit,
    onReminderCompleted: (Reminder) -> Unit,
    onRemindersReordered: (List<ReminderKey>) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val now = remember { Clock.System.now() }
    var displayReminders by remember { mutableStateOf(reminders) }
    var pressedDragHandleReminderKey by remember { mutableStateOf<ReminderKey?>(null) }
    val reorderState = remember(lazyListState) {
        NotesReorderState(lazyListState) { fromIndex, toIndex ->
            displayReminders = displayReminders.moveReminder(fromIndex, toIndex)
        }
    }

    LaunchedEffect(reminders) {
        if (!reorderState.isDragging) {
            displayReminders = reminders
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(
            displayReminders,
            key = { _, reminder -> "${reminder.ownerId}/${reminder.id}" },
        ) { index, reminder ->
            val currentIndex by rememberUpdatedState(index)
            val dragHandleModifier = Modifier
                .size(40.dp)
                .pointerInput(reminder.key) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressedDragHandleReminderKey = reminder.key
                        waitForUpOrCancellation()
                        pressedDragHandleReminderKey = null
                    }
                }
                .pointerInput(reminder.key) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { reorderState.startDrag(currentIndex) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            reorderState.dragBy(dragAmount.y)
                        },
                        onDragEnd = {
                            onRemindersReordered(displayReminders.map(Reminder::key))
                            reorderState.endDrag()
                        },
                        onDragCancel = {
                            displayReminders = reminders
                            reorderState.endDrag()
                        },
                    )
                }

            val isDragged = reorderState.draggingItemIndex == index

            ReminderCard(
                reminder = reminder,
                modifier = Modifier
                    // El arrastrado se mueve con translationY; el resto se desliza a su hueco.
                    .then(if (isDragged) Modifier else Modifier.animateItem())
                    .graphicsLayer {
                        translationY = reorderState.translationFor(index)
                    }
                    .zIndex(if (isDragged) 1f else 0f),
                onClick = { onReminderClick(reminder) },
                onLongClick = {
                    if (pressedDragHandleReminderKey != reminder.key) {
                        onReminderLongClick(reminder)
                    }
                },
                sharingSummary = reminderSharingSummary(
                    reminder = reminder,
                    collaborators = collaboratorsByReminderId[reminder.id].orEmpty(),
                    currentUserId = currentUserId,
                ),
                leading = {
                    IconButton(onClick = { onReminderCompleted(reminder) }) {
                        Icon(
                            imageVector = Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = stringResource(Res.string.reminder_complete_action),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                trailing = {
                    Box(
                        modifier = dragHandleModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragIndicator,
                            contentDescription = stringResource(Res.string.reorder_reminder_handle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                secondaryContent = reminder.due?.let { due ->
                    { ReminderDueChip(due = due, now = now) }
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun ReminderCard(
    reminder: Reminder,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    leading: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    secondaryContent: (@Composable () -> Unit)? = null,
    sharingSummary: String? = null,
    textDecoration: TextDecoration? = null,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = reminder.text,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = textDecoration,
                )
                sharingSummary?.let { summary -> ReminderSharingBadge(summary) }
                secondaryContent?.invoke()
            }
            trailing?.invoke()
        }
    }
}

/**
 * Mismo dialogo para crear y editar: el texto es lo unico obligatorio, y la hora solo se ofrece
 * cuando ya hay fecha, porque el modelo no admite hora suelta.
 *
 * El vencimiento esta detras de un interruptor: la mayoria de recordatorios no lo llevan, asi que
 * los botones de fecha y hora solo aparecen cuando el usuario los pide.
 *
 * Encender el interruptor es el momento de comprobar el permiso de notificaciones: es justo cuando
 * el usuario espera un aviso, y el estado se consulta entonces porque pudo cambiarlo en ajustes.
 */
@Composable
private fun ReminderEditorDialog(
    title: String,
    confirmLabel: String,
    initialText: String,
    initialDue: ReminderDue?,
    onDismiss: () -> Unit,
    onConfirm: (String, ReminderDue?) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }
    var due by remember { mutableStateOf(initialDue) }
    var isDueEnabled by remember { mutableStateOf(initialDue != null) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var showNotificationsDisabled by remember { mutableStateOf(false) }
    val notificationPermission = rememberNotificationPermissionController()

    if (showNotificationsDisabled) {
        NotificationsDisabledDialog(
            onDismiss = { showNotificationsDisabled = false },
            onEnable = {
                showNotificationsDisabled = false
                notificationPermission?.requestNotifications()
            },
        )
    }

    if (showDatePicker) {
        ReminderDatePickerDialog(
            initialDate = due?.date,
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                due = due?.copy(date = date) ?: ReminderDue(date)
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        ReminderTimePickerDialog(
            initialTime = due?.time,
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                // Poner hora sin fecha asume hoy: es el caso mas comun y ahorra elegir el dia.
                due = due?.copy(time = time) ?: ReminderDue(today(), time)
                showTimePicker = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(title) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(Res.string.edit_reminder_text_hint)) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedLabelColor = MaterialTheme.colorScheme.primary,
                        cursorColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.reminder_due_switch))
                    Switch(
                        checked = isDueEnabled,
                        onCheckedChange = { enabled ->
                            isDueEnabled = enabled
                            // Apagar el interruptor es la forma de quitar el vencimiento.
                            if (!enabled) {
                                due = null
                            } else if (notificationPermission?.areNotificationsEnabled() == false) {
                                showNotificationsDisabled = true
                            }
                        },
                    )
                }

                if (isDueEnabled) {
                    ReminderDueEditor(
                        due = due,
                        onPickDate = { showDatePicker = true },
                        onPickTime = { showTimePicker = true },
                        onClearDate = { due = null },
                        onClearTime = { due = due?.copy(time = null) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim(), due.takeIf { isDueEnabled }) },
                enabled = text.isNotBlank(),
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

/**
 * Solo informa: el vencimiento se guarda de todas formas. Sin permiso se pierde el aviso, no la
 * fecha, que se sigue viendo en la lista y se sigue resaltando al vencer.
 */
@Composable
private fun NotificationsDisabledDialog(
    onDismiss: () -> Unit,
    onEnable: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.notifications_disabled_title)) },
        text = { Text(stringResource(Res.string.notifications_disabled_message)) },
        confirmButton = {
            TextButton(onClick = onEnable) {
                Text(stringResource(Res.string.notifications_disabled_enable))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

/**
 * Fecha y hora se ofrecen siempre por separado. Elegir solo la hora es el caso frecuente
 * ("hoy a las siete"), y entonces la fecha se rellena con hoy en lugar de obligar a pasar por
 * el calendario.
 */
@Composable
private fun ReminderDueEditor(
    due: ReminderDue?,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
    onClearDate: () -> Unit,
    onClearTime: () -> Unit,
) {
    ReminderDueRow(
        icon = Icons.Filled.CalendarToday,
        label = due?.let { formatNotesListDate(it.date) }
            ?: stringResource(Res.string.reminder_due_add),
        clearDescription = stringResource(Res.string.reminder_due_clear),
        onClick = onPickDate,
        onClear = onClearDate.takeIf { due != null },
    )

    ReminderDueRow(
        icon = Icons.Filled.Schedule,
        label = due?.time?.let { time -> formatReminderTime(time) }
            ?: stringResource(Res.string.reminder_due_add_time),
        clearDescription = stringResource(Res.string.reminder_due_clear_time),
        onClick = onPickTime,
        onClear = onClearTime.takeIf { due?.time != null },
    )
}

@Composable
private fun ReminderDueRow(
    icon: ImageVector,
    label: String,
    clearDescription: String,
    onClick: () -> Unit,
    onClear: (() -> Unit)?,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onClick, modifier = Modifier.weight(1f)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = label,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
            )
        }
        if (onClear != null) {
            IconButton(onClick = onClear) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = clearDescription,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            Spacer(modifier = Modifier.size(48.dp))
        }
    }
}

private fun today(): LocalDate =
    Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Traduce el feedback fuera del [LaunchedEffect] porque `stringResource` es composable, igual que
 * hace `NotesListsScreen` con el feedback de archivado.
 */
@Composable
private fun ReminderFeedbackHost(
    feedback: ReminderFeedback?,
    snackbarHostState: SnackbarHostState,
    onConsume: () -> Unit,
) {
    val message = feedback?.let { current ->
        when (current.action) {
            ReminderFeedbackAction.COMPLETED -> if (current.isSuccess) {
                stringResource(Res.string.reminder_completed_feedback)
            } else {
                stringResource(Res.string.reminder_completed_error)
            }

            ReminderFeedbackAction.RESTORED -> if (current.isSuccess) {
                stringResource(Res.string.reminder_restored_feedback)
            } else {
                stringResource(Res.string.reminder_restored_error)
            }

            ReminderFeedbackAction.DELETED -> stringResource(Res.string.reminder_deleted_error)

            ReminderFeedbackAction.LEFT_SHARED -> if (current.isSuccess) {
                stringResource(Res.string.leave_shared_reminder_success)
            } else {
                stringResource(Res.string.leave_shared_reminder_error)
            }
        }
    }

    LaunchedEffect(feedback) {
        feedback ?: return@LaunchedEffect
        val currentMessage = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentMessage)
        onConsume()
    }
}

@Composable
private fun reminderDueLabel(due: ReminderDue): String {
    val date = formatNotesListDate(due.date)
    return due.time?.let { time ->
        stringResource(Res.string.reminder_due_with_time, date, formatReminderTime(time))
    } ?: date
}

/** Lo vencido se resalta, pero no se mueve de sitio: el orden es del usuario. */
@Composable
private fun ReminderDueChip(due: ReminderDue, now: Instant) {
    val isOverdue = due.isOverdue(now)
    val containerColor = if (isOverdue) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isOverdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = if (due.time != null) Icons.Filled.Schedule else Icons.Filled.CalendarToday,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = reminderDueLabel(due),
            style = MaterialTheme.typography.labelMedium,
            color = contentColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun remindersTopAppBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = SecretariaTopBarColor,
    titleContentColor = SecretariaTopBarContentColor,
    navigationIconContentColor = SecretariaTopBarContentColor,
    actionIconContentColor = SecretariaTopBarContentColor,
)

@Composable
internal fun CenteredMessage(
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
internal fun ScrollableCenteredMessage(
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun DeleteReminderDialog(
    reminderText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.delete_reminder_title)) },
        text = { Text(stringResource(Res.string.delete_reminder_message, reminderText)) },
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

/** Mismo patron que [ReminderFeedbackHost]: traducir fuera del `LaunchedEffect`. */
@Composable
private fun ReminderSharingFeedbackHost(
    feedback: ReminderSharingFeedback?,
    snackbarHostState: SnackbarHostState,
    onConsume: () -> Unit,
) {
    val message = feedback?.let { current ->
        when (current.action) {
            ReminderSharingAction.SHARED -> stringResource(
                Res.string.share_reminder_success,
                current.friendName,
            )

            ReminderSharingAction.UNSHARED -> stringResource(
                Res.string.unshare_reminder_success,
                current.friendName,
            )
        }
    }

    LaunchedEffect(message) {
        val currentMessage = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(currentMessage)
        onConsume()
    }
}

/**
 * La pulsacion larga abre las opciones en lugar de borrar directamente: es el mismo patron que
 * `ListOptionsDialog`, y ahora hay mas de una accion posible sobre un recordatorio.
 */
@Composable
private fun ReminderOptionsDialog(
    reminder: Reminder,
    currentUserId: String?,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onLeaveShared: () -> Unit,
    onDismiss: () -> Unit,
    canShare: Boolean = true,
) {
    val isOwner = currentUserId != null && reminder.ownerId == currentUserId

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(reminder.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                if (isOwner) {
                    if (canShare) {
                        ReminderOptionRow(
                            icon = Icons.Outlined.Share,
                            label = stringResource(Res.string.share_list),
                            onClick = onShare,
                        )
                    }
                    ReminderOptionRow(
                        icon = Icons.Outlined.DeleteOutline,
                        label = stringResource(Res.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = onDelete,
                    )
                } else {
                    ReminderOptionRow(
                        icon = Icons.Outlined.DeleteOutline,
                        label = stringResource(Res.string.leave_shared_reminder),
                        tint = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = onLeaveShared,
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ReminderOptionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** Calcado de `ShareListDialog`: compartir un recordatorio funciona igual que compartir una lista. */
@Composable
private fun ShareReminderDialog(
    reminderText: String,
    collaborators: List<ListCollaborator>,
    friends: List<FriendSummary>,
    isLoading: Boolean,
    isUpdatingSharing: Boolean,
    errorMessage: String?,
    onShare: (FriendSummary) -> Unit,
    onUnshare: (ListCollaborator) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.share_reminder_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = reminderText,
                    style = MaterialTheme.typography.bodyLarge,
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (isLoading) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ReminderSharingSectionTitle(
                            stringResource(Res.string.share_reminder_current_access),
                        )

                        if (collaborators.isEmpty()) {
                            Text(
                                text = stringResource(Res.string.share_list_private),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            collaborators.forEach { collaborator ->
                                ReminderSharingRow(
                                    name = collaborator.name,
                                    actionLabel = stringResource(Res.string.unshare_list),
                                    actionColor = MaterialTheme.colorScheme.error,
                                    enabled = !isUpdatingSharing,
                                    onAction = { onUnshare(collaborator) },
                                )
                            }
                        }

                        ReminderSharingSectionTitle(
                            stringResource(Res.string.share_list_available_friends),
                        )

                        if (friends.isEmpty()) {
                            Text(
                                text = if (collaborators.isEmpty()) {
                                    stringResource(Res.string.share_reminder_empty_friends)
                                } else {
                                    stringResource(Res.string.share_reminder_no_available_friends)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            friends.forEach { friend ->
                                ReminderSharingRow(
                                    name = friend.name,
                                    actionLabel = stringResource(Res.string.share_list),
                                    actionColor = MaterialTheme.colorScheme.primary,
                                    enabled = !isUpdatingSharing,
                                    onAction = { onShare(friend) },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                enabled = !isUpdatingSharing,
                onClick = onDismiss,
            ) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun ReminderSharingRow(
    name: String,
    actionLabel: String,
    actionColor: Color,
    enabled: Boolean,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(enabled = enabled, onClick = onAction) {
            Text(text = actionLabel, color = actionColor)
        }
    }
}

@Composable
private fun ReminderSharingSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun LeaveSharedReminderDialog(
    reminderText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.leave_shared_reminder_title)) },
        text = { Text(stringResource(Res.string.leave_shared_reminder_message, reminderText)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.leave_shared_reminder))
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
private fun ReminderSharingBadge(summary: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Share,
            contentDescription = stringResource(Res.string.reminder_shared_badge),
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = summary,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** Mismo texto que en las listas: "contigo" en lo ajeno y "con Fulano" en lo propio. */
@Composable
private fun reminderSharingSummary(
    reminder: Reminder,
    collaborators: List<ListCollaborator>,
    currentUserId: String?,
): String? {
    if (currentUserId == null) return null
    if (reminder.ownerId != currentUserId) {
        return if (reminder.isShared) stringResource(Res.string.share_list_shared_with_you) else null
    }

    val sharedCount = reminder.sharedWithUserIds.size
    if (sharedCount == 0) return null

    val firstResolvedCollaborator = collaborators.firstOrNull { it.isResolvedName }
    return when {
        firstResolvedCollaborator == null -> if (sharedCount == 1) {
            stringResource(Res.string.share_list_shared_with_count_one)
        } else {
            stringResource(Res.string.share_list_shared_with_count_many, sharedCount)
        }

        sharedCount == 1 -> stringResource(
            Res.string.share_list_shared_with_one,
            firstResolvedCollaborator.name,
        )

        else -> stringResource(
            Res.string.share_list_shared_with_many,
            firstResolvedCollaborator.name,
            sharedCount - 1,
        )
    }
}
