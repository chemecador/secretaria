package com.chemecador.secretaria.reminders

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.chemecador.secretaria.notes.NotesReorderState
import com.chemecador.secretaria.noteslists.formatNotesListDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.stringResource
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
import secretaria.composeapp.generated.resources.reminder_due_with_time
import secretaria.composeapp.generated.resources.reminder_restore_action
import secretaria.composeapp.generated.resources.reminder_restored_error
import secretaria.composeapp.generated.resources.reminder_restored_feedback
import secretaria.composeapp.generated.resources.reminders_empty
import secretaria.composeapp.generated.resources.reminders_error_generic
import secretaria.composeapp.generated.resources.reminders_title
import secretaria.composeapp.generated.resources.reorder_reminder_handle
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
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var reminderToEdit by remember { mutableStateOf<Reminder?>(null) }
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    ReminderFeedbackHost(
        feedback = state.feedback,
        snackbarHostState = snackbarHostState,
        onConsume = viewModel::consumeFeedback,
    )

    onBack?.let { navigateBack ->
        PlatformBackHandler(
            enabled = !showCreateDialog && reminderToEdit == null && reminderToDelete == null,
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
                    viewModel.updateReminder(reminder.id, text, due)
                    reminderToEdit = null
                },
            )
        }

        reminderToDelete?.let { reminder ->
            DeleteReminderDialog(
                reminderText = reminder.text,
                onDismiss = { reminderToDelete = null },
                onConfirm = {
                    viewModel.deleteReminder(reminder.id)
                    reminderToDelete = null
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
                            onReminderClick = { reminderToEdit = it },
                            onReminderLongClick = { reminderToDelete = it },
                            onReminderCompleted = { reminder ->
                                viewModel.setReminderCompleted(reminder.id, completed = true)
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
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var reminderToDelete by remember { mutableStateOf<Reminder?>(null) }

    ReminderFeedbackHost(
        feedback = state.feedback,
        snackbarHostState = snackbarHostState,
        onConsume = viewModel::consumeFeedback,
    )

    PlatformBackHandler(
        enabled = reminderToDelete == null,
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
                    viewModel.deleteReminder(reminder.id)
                    reminderToDelete = null
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
                    items(completed, key = Reminder::id) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            textDecoration = TextDecoration.LineThrough,
                            onClick = {},
                            onLongClick = { reminderToDelete = reminder },
                            leading = {
                                IconButton(
                                    onClick = {
                                        viewModel.setReminderCompleted(reminder.id, completed = false)
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
    onReminderClick: (Reminder) -> Unit,
    onReminderLongClick: (Reminder) -> Unit,
    onReminderCompleted: (Reminder) -> Unit,
    onRemindersReordered: (List<String>) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    val now = remember { Clock.System.now() }
    var displayReminders by remember { mutableStateOf(reminders) }
    var pressedDragHandleReminderId by remember { mutableStateOf<String?>(null) }
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
        itemsIndexed(displayReminders, key = { _, reminder -> reminder.id }) { index, reminder ->
            val currentIndex by rememberUpdatedState(index)
            val dragHandleModifier = Modifier
                .size(40.dp)
                .pointerInput(reminder.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressedDragHandleReminderId = reminder.id
                        waitForUpOrCancellation()
                        pressedDragHandleReminderId = null
                    }
                }
                .pointerInput(reminder.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { reorderState.startDrag(currentIndex) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            reorderState.dragBy(dragAmount.y)
                        },
                        onDragEnd = {
                            onRemindersReordered(displayReminders.map(Reminder::id))
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
                    if (pressedDragHandleReminderId != reminder.id) {
                        onReminderLongClick(reminder)
                    }
                },
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
                secondaryContent?.invoke()
            }
            trailing?.invoke()
        }
    }
}

/**
 * Mismo dialogo para crear y editar: el texto es lo unico obligatorio, y la hora solo se ofrece
 * cuando ya hay fecha, porque el modelo no admite hora suelta.
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
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

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

                ReminderDueEditor(
                    due = due,
                    onPickDate = { showDatePicker = true },
                    onPickTime = { showTimePicker = true },
                    onClearDate = { due = null },
                    onClearTime = { due = due?.copy(time = null) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text.trim(), due) },
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
        label = due?.time?.let(::formatReminderTime)
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
