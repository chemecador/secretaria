package com.chemecador.secretaria.noteslists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.chemecador.secretaria.SecretariaOverflowMenu
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.login.AuthRepository
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.app_name
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.create_list_button
import secretaria.composeapp.generated.resources.create_list_name_hint
import secretaria.composeapp.generated.resources.create_list_ordered
import secretaria.composeapp.generated.resources.create_list_title
import secretaria.composeapp.generated.resources.delete
import secretaria.composeapp.generated.resources.delete_list_message
import secretaria.composeapp.generated.resources.delete_list_title
import secretaria.composeapp.generated.resources.edit_list
import secretaria.composeapp.generated.resources.edit_list_button
import secretaria.composeapp.generated.resources.edit_list_title
import secretaria.composeapp.generated.resources.list_created_by
import secretaria.composeapp.generated.resources.list_options
import secretaria.composeapp.generated.resources.list_ordered_badge
import secretaria.composeapp.generated.resources.notes_lists_empty
import secretaria.composeapp.generated.resources.notes_lists_empty_mine
import secretaria.composeapp.generated.resources.notes_lists_empty_shared
import secretaria.composeapp.generated.resources.notes_lists_error_generic
import secretaria.composeapp.generated.resources.notes_lists_mine_tab
import secretaria.composeapp.generated.resources.notes_lists_shared_tab
import secretaria.composeapp.generated.resources.order_by
import secretaria.composeapp.generated.resources.share_list
import secretaria.composeapp.generated.resources.share_list_available_friends
import secretaria.composeapp.generated.resources.share_list_current_access
import secretaria.composeapp.generated.resources.share_list_empty_friends
import secretaria.composeapp.generated.resources.share_list_no_available_friends
import secretaria.composeapp.generated.resources.share_list_private
import secretaria.composeapp.generated.resources.share_list_shared_with_count_many
import secretaria.composeapp.generated.resources.share_list_shared_with_count_one
import secretaria.composeapp.generated.resources.share_list_shared_with_many
import secretaria.composeapp.generated.resources.share_list_shared_with_one
import secretaria.composeapp.generated.resources.share_list_success
import secretaria.composeapp.generated.resources.sort_date_asc
import secretaria.composeapp.generated.resources.sort_date_desc
import secretaria.composeapp.generated.resources.sort_name_asc
import secretaria.composeapp.generated.resources.sort_name_desc
import secretaria.composeapp.generated.resources.unshare_list
import secretaria.composeapp.generated.resources.unshare_list_success

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListsScreen(
    viewModel: NotesListsViewModel,
    onListSelected: (id: String, ownerId: String, name: String, isOrdered: Boolean) -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authRepository = koinInject<AuthRepository>()
    val currentUserId = authRepository.currentUserId
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var listForOptions by remember { mutableStateOf<NotesListSummary?>(null) }
    var listToEdit by remember { mutableStateOf<NotesListSummary?>(null) }
    var listToDelete by remember { mutableStateOf<NotesListSummary?>(null) }
    var listToShare by remember { mutableStateOf<NotesListSummary?>(null) }
    var selectedSection by remember { mutableStateOf(NotesListsSection.MINE) }
    val openListOptions: (NotesListSummary) -> Unit = { item ->
        if (item.ownerId == currentUserId) {
            listForOptions = item
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    val shareFeedbackMessage = state.shareFeedback?.let { feedback ->
        when (feedback.action) {
            ListSharingAction.SHARED -> stringResource(
                Res.string.share_list_success,
                feedback.friendName
            )

            ListSharingAction.UNSHARED -> stringResource(
                Res.string.unshare_list_success,
                feedback.friendName
            )
        }
    }
    val visibleItems = state.items.filter { item ->
        when (selectedSection) {
            NotesListsSection.MINE -> !item.isShared
            NotesListsSection.SHARED -> item.isShared
        }
    }
    val emptyMessage = when (selectedSection) {
        NotesListsSection.MINE -> stringResource(Res.string.notes_lists_empty_mine)
        NotesListsSection.SHARED -> stringResource(Res.string.notes_lists_empty_shared)
    }

    LaunchedEffect(listToShare?.id) {
        val selectedList = listToShare ?: return@LaunchedEffect
        viewModel.loadShareableFriends(selectedList)
    }

    LaunchedEffect(shareFeedbackMessage) {
        val message = shareFeedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeShareFeedback()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecretariaTopBarColor,
                    titleContentColor = SecretariaTopBarContentColor,
                    actionIconContentColor = SecretariaTopBarContentColor,
                ),
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
            CreateListDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, ordered ->
                    selectedSection = NotesListsSection.MINE
                    viewModel.createList(name, ordered)
                    showCreateDialog = false
                },
            )
        }

        listForOptions?.let { list ->
            ListOptionsDialog(
                listName = list.name,
                isOwner = list.ownerId == currentUserId,
                onShare = {
                    listToShare = list
                    listForOptions = null
                },
                onEdit = {
                    listToEdit = list
                    listForOptions = null
                },
                onDelete = {
                    listToDelete = list
                    listForOptions = null
                },
                onDismiss = { listForOptions = null },
            )
        }

        listToShare?.let { list ->
            ShareListDialog(
                listName = list.name,
                collaborators = state.collaboratorsByListId[list.id].orEmpty(),
                friends = state.shareableFriends,
                isLoading = state.isLoadingShareableFriends,
                isUpdatingSharing = state.isUpdatingSharing,
                errorMessage = state.shareErrorMessage,
                onShare = { friend ->
                    viewModel.shareList(list, friend)
                },
                onUnshare = { collaborator ->
                    viewModel.unshareList(list, collaborator)
                },
                onDismiss = {
                    listToShare = null
                    viewModel.clearShareState()
                },
            )
        }

        listToEdit?.let { list ->
            EditListDialog(
                list = list,
                onDismiss = { listToEdit = null },
                onSave = { name, ordered ->
                    viewModel.updateList(list, name, ordered)
                    listToEdit = null
                },
            )
        }

        listToDelete?.let { list ->
            DeleteListDialog(
                listName = list.name,
                onDismiss = { listToDelete = null },
                onConfirm = {
                    viewModel.deleteList(list)
                    listToDelete = null
                },
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            ListsSectionTabs(
                selectedSection = selectedSection,
                onSectionSelected = { selectedSection = it },
            )

            SortSelector(
                selected = state.sortOption,
                onSortSelected = viewModel::setSort,
            )

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
                    when {
                        state.errorMessage != null -> ScrollableCenteredMessage {
                            val errorMessage = state.errorMessage?.takeIf { it.isNotBlank() }
                                ?: stringResource(Res.string.notes_lists_error_generic)
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }

                        visibleItems.isEmpty() -> ScrollableCenteredMessage {
                            Text(
                                text = if (state.items.isEmpty()) {
                                    stringResource(Res.string.notes_lists_empty)
                                } else {
                                    emptyMessage
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }

                        else -> NotesListsContent(
                            items = visibleItems,
                            collaboratorsByListId = state.collaboratorsByListId,
                            onListSelected = onListSelected,
                            currentUserId = currentUserId,
                            onListLongClick = openListOptions,
                            onListOptionsClick = openListOptions,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ListsSectionTabs(
    selectedSection: NotesListsSection,
    onSectionSelected: (NotesListsSection) -> Unit,
) {
    val sections = listOf(NotesListsSection.MINE, NotesListsSection.SHARED)
    val selectedTabIndex = sections.indexOf(selectedSection)

    PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
        sections.forEachIndexed { index, section ->
            Tab(
                selected = index == selectedTabIndex,
                onClick = { onSectionSelected(section) },
                text = {
                    Text(
                        text = when (section) {
                            NotesListsSection.MINE -> stringResource(Res.string.notes_lists_mine_tab)
                            NotesListsSection.SHARED -> stringResource(Res.string.notes_lists_shared_tab)
                        },
                    )
                },
            )
        }
    }
}

@Composable
private fun SortSelector(
    selected: SortOption,
    onSortSelected: (SortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val visibleOptions = listOf(
        SortOption.NAME_ASC,
        SortOption.NAME_DESC,
        SortOption.DATE_ASC,
        SortOption.DATE_DESC,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(Res.string.order_by),
            style = MaterialTheme.typography.bodyMedium,
        )

        Box {
            TextButton(onClick = { expanded = true }) {
                Text(selected.label())
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                visibleOptions.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label()) },
                        onClick = {
                            onSortSelected(option)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesListsContent(
    items: List<NotesListSummary>,
    collaboratorsByListId: Map<String, List<ListCollaborator>>,
    onListSelected: (id: String, ownerId: String, name: String, isOrdered: Boolean) -> Unit,
    currentUserId: String?,
    onListLongClick: (NotesListSummary) -> Unit,
    onListOptionsClick: (NotesListSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            NotesListCard(
                item = item,
                collaborators = collaboratorsByListId[item.id].orEmpty(),
                currentUserId = currentUserId,
                onClick = { onListSelected(item.id, item.ownerId, item.name, item.isOrdered) },
                onLongClick = if (item.ownerId == currentUserId) {
                    { onListLongClick(item) }
                } else {
                    null
                },
                onOptionsClick = if (item.ownerId == currentUserId) {
                    { onListOptionsClick(item) }
                } else {
                    null
                },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesListCard(
    item: NotesListSummary,
    collaborators: List<ListCollaborator>,
    currentUserId: String?,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    onOptionsClick: (() -> Unit)?,
) {
    val sharingSummary = sharingSummaryText(
        item = item,
        collaborators = collaborators,
        currentUserId = currentUserId,
    )

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                onOptionsClick?.let { onOptions ->
                    IconButton(onClick = onOptions) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(Res.string.list_options),
                        )
                    }
                }
            }
            Text(
                text = stringResource(Res.string.list_created_by, item.creator),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sharingSummary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatNotesListDate(item.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (item.isOrdered) {
                    Text(
                        text = stringResource(Res.string.list_ordered_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun sharingSummaryText(
    item: NotesListSummary,
    collaborators: List<ListCollaborator>,
    currentUserId: String?,
): String? {
    if (item.ownerId != currentUserId) return null

    val sharedCount = item.sharedWithUserIds.size
    if (sharedCount == 0) return null

    return if (collaborators.isNotEmpty()) {
        val firstResolvedCollaborator =
            collaborators.firstOrNull { collaborator -> collaborator.isResolvedName }
        if (firstResolvedCollaborator != null && collaborators.size == 1) {
            stringResource(Res.string.share_list_shared_with_one, firstResolvedCollaborator.name)
        } else if (firstResolvedCollaborator != null) {
            stringResource(
                Res.string.share_list_shared_with_many,
                firstResolvedCollaborator.name,
                collaborators.size - 1,
            )
        } else {
            if (sharedCount == 1) {
                stringResource(Res.string.share_list_shared_with_count_one)
            } else {
                stringResource(Res.string.share_list_shared_with_count_many, sharedCount)
            }
        }
    } else if (sharedCount == 1) {
        stringResource(Res.string.share_list_shared_with_count_one)
    } else {
        stringResource(Res.string.share_list_shared_with_count_many, sharedCount)
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
private fun ScrollableCenteredMessage(
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
private fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, ordered: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ordered by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
        ),
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.create_list_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.create_list_name_hint)) },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.create_list_ordered))
                    Switch(checked = ordered, onCheckedChange = { ordered = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), ordered) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(Res.string.create_list_button))
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
private fun DeleteListDialog(
    listName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.delete_list_title)) },
        text = { Text(stringResource(Res.string.delete_list_message, listName)) },
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
private fun ListOptionsDialog(
    listName: String,
    isOwner: Boolean,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(listName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 4.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                if (isOwner) {
                    OptionRow(
                        icon = Icons.Outlined.Share,
                        label = stringResource(Res.string.share_list),
                        onClick = onShare,
                    )
                    OptionRow(
                        icon = Icons.Outlined.Edit,
                        label = stringResource(Res.string.edit_list),
                        onClick = onEdit,
                    )
                    OptionRow(
                        icon = Icons.Outlined.DeleteOutline,
                        label = stringResource(Res.string.delete),
                        tint = MaterialTheme.colorScheme.error,
                        textColor = MaterialTheme.colorScheme.error,
                        onClick = onDelete,
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
private fun OptionRow(
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

@Composable
private fun ShareListDialog(
    listName: String,
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
        title = { Text(listName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            CircularProgressIndicator()
                        }
                    }

                    else -> {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            SharingSectionTitle(stringResource(Res.string.share_list_current_access))

                            if (collaborators.isEmpty()) {
                                Text(
                                    text = stringResource(Res.string.share_list_private),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                collaborators.forEach { collaborator ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = collaborator.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            enabled = !isUpdatingSharing,
                                            onClick = { onUnshare(collaborator) },
                                        ) {
                                            Text(
                                                text = stringResource(Res.string.unshare_list),
                                                color = MaterialTheme.colorScheme.error,
                                            )
                                        }
                                    }
                                }
                            }

                            SharingSectionTitle(stringResource(Res.string.share_list_available_friends))

                            if (friends.isEmpty()) {
                                Text(
                                    text = if (collaborators.isEmpty()) {
                                        stringResource(Res.string.share_list_empty_friends)
                                    } else {
                                        stringResource(Res.string.share_list_no_available_friends)
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            } else {
                                friends.forEach { friend ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text(
                                            text = friend.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            modifier = Modifier.weight(1f),
                                        )
                                        TextButton(
                                            enabled = !isUpdatingSharing,
                                            onClick = { onShare(friend) },
                                        ) {
                                            Text(stringResource(Res.string.share_list))
                                        }
                                    }
                                }
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
private fun SharingSectionTitle(
    text: String,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun EditListDialog(
    list: NotesListSummary,
    onDismiss: () -> Unit,
    onSave: (name: String, ordered: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(list.name) }
    var ordered by remember { mutableStateOf(list.isOrdered) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.edit_list_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.create_list_name_hint)) },
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.create_list_ordered))
                    Switch(checked = ordered, onCheckedChange = { ordered = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), ordered) },
                enabled = name.isNotBlank(),
            ) {
                Text(stringResource(Res.string.edit_list_button))
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
private fun SortOption.label(): String {
    return when (this) {
        SortOption.NAME_ASC -> stringResource(Res.string.sort_name_asc)
        SortOption.NAME_DESC -> stringResource(Res.string.sort_name_desc)
        SortOption.DATE_ASC -> stringResource(Res.string.sort_date_asc)
        SortOption.DATE_DESC,
        SortOption.CUSTOM -> stringResource(Res.string.sort_date_desc)
    }
}

private enum class NotesListsSection {
    MINE,
    SHARED,
}
