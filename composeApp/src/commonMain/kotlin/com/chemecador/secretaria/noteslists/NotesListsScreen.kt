package com.chemecador.secretaria.noteslists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatListNumbered
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import com.chemecador.secretaria.PlatformBackHandler
import com.chemecador.secretaria.SecretariaOverflowMenu
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.notes.NotesReorderState
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.app_name
import secretaria.composeapp.generated.resources.archive_list
import secretaria.composeapp.generated.resources.archive_list_error
import secretaria.composeapp.generated.resources.archive_list_success
import secretaria.composeapp.generated.resources.archived_lists_title
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.add_list_to_group
import secretaria.composeapp.generated.resources.create_group_button
import secretaria.composeapp.generated.resources.create_group_inline_title
import secretaria.composeapp.generated.resources.create_list_group
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
import secretaria.composeapp.generated.resources.list_archived_on
import secretaria.composeapp.generated.resources.list_archived_on_unknown
import secretaria.composeapp.generated.resources.list_group_badge
import secretaria.composeapp.generated.resources.list_options
import secretaria.composeapp.generated.resources.list_ordered_badge
import secretaria.composeapp.generated.resources.group_lists_empty
import secretaria.composeapp.generated.resources.notes_lists_empty
import secretaria.composeapp.generated.resources.notes_lists_empty_active_mine
import secretaria.composeapp.generated.resources.notes_lists_empty_active_shared
import secretaria.composeapp.generated.resources.notes_lists_empty_archived
import secretaria.composeapp.generated.resources.notes_lists_error_generic
import secretaria.composeapp.generated.resources.notes_lists_mine_tab
import secretaria.composeapp.generated.resources.notes_lists_search_clear
import secretaria.composeapp.generated.resources.notes_lists_search_close
import secretaria.composeapp.generated.resources.notes_lists_search_empty
import secretaria.composeapp.generated.resources.notes_lists_search_hint
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
import secretaria.composeapp.generated.resources.remove_list_from_group
import secretaria.composeapp.generated.resources.reorder_list_handle
import secretaria.composeapp.generated.resources.select_group_empty
import secretaria.composeapp.generated.resources.select_group_title
import secretaria.composeapp.generated.resources.sort_date_asc
import secretaria.composeapp.generated.resources.sort_date_desc
import secretaria.composeapp.generated.resources.sort_name_asc
import secretaria.composeapp.generated.resources.sort_name_desc
import secretaria.composeapp.generated.resources.unshare_list
import secretaria.composeapp.generated.resources.unshare_list_success
import secretaria.composeapp.generated.resources.unarchive_list
import secretaria.composeapp.generated.resources.unarchive_list_error
import secretaria.composeapp.generated.resources.unarchive_list_success
import kotlin.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListsScreen(
    viewModel: NotesListsViewModel,
    selectedSection: NotesListsSection = NotesListsSection.MINE,
    onSectionSelected: (NotesListsSection) -> Unit = {},
    onListSelected: (id: String, ownerId: String, name: String, isOrdered: Boolean) -> Unit,
    onGroupSelected: (id: String, ownerId: String, name: String, isOrdered: Boolean) -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
    groupOwnerId: String? = null,
    groupId: String? = null,
    groupName: String? = null,
    groupIsOrdered: Boolean = false,
    onBack: (() -> Unit)? = null,
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
    var listToGroup by remember { mutableStateOf<NotesListSummary?>(null) }
    var showArchivedLists by remember { mutableStateOf(false) }
    var showSearchInput by remember { mutableStateOf(false) }
    val isSearchInputVisible = showSearchInput || state.searchQuery.isNotBlank()
    val isGroupScreen = groupId != null
    val currentGroup = state.items.firstOrNull { item ->
        item.ownerId == groupOwnerId && item.id == groupId && item.isGroup
    }
    val currentGroupKey = groupOwnerId?.let { ownerId ->
        groupId?.let { id -> NotesListKey(ownerId, id) }
    }
    val currentGroupName = currentGroup?.name ?: groupName.orEmpty()
    val currentGroupIsOrdered = currentGroup?.isOrdered ?: groupIsOrdered
    val openListOptions: (NotesListSummary) -> Unit = { item ->
        if (currentUserId != null) {
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
    val archiveFeedback = state.archiveFeedback
    val archiveFeedbackMessage = archiveFeedback?.let { feedback ->
        when (feedback.action) {
            ListArchiveAction.ARCHIVED -> if (feedback.isSuccess) {
                stringResource(Res.string.archive_list_success)
            } else {
                stringResource(Res.string.archive_list_error)
            }

            ListArchiveAction.UNARCHIVED -> if (feedback.isSuccess) {
                stringResource(Res.string.unarchive_list_success)
            } else {
                stringResource(Res.string.unarchive_list_error)
            }
        }
    }
    val visibleRootGroups = state.items.filter { item ->
        item.isGroup && item.isVisibleRootGroup(
            currentUserId = currentUserId,
            showArchivedLists = showArchivedLists,
        )
    }
    val visibleGroupKeys = visibleRootGroups.map { item -> item.key }.toSet()
    val visibleItems = if (isGroupScreen) {
        state.items.filter { item ->
            item.groupKey == currentGroupKey &&
                !item.isGroup &&
                (currentUserId == null || currentUserId !in item.archivedBy)
        }
    } else {
        state.items.filter { item ->
            item.isVisibleInRoot(
                currentUserId = currentUserId,
                selectedSection = selectedSection,
                showArchivedLists = showArchivedLists,
            ) && (item.groupKey == null || item.groupKey !in visibleGroupKeys)
        }
    }
    val emptyMessage = if (isGroupScreen) {
        stringResource(Res.string.group_lists_empty)
    } else if (showArchivedLists) {
        stringResource(Res.string.notes_lists_empty_archived)
    } else {
        when (selectedSection) {
            NotesListsSection.MINE -> stringResource(Res.string.notes_lists_empty_active_mine)
            NotesListsSection.SHARED -> stringResource(Res.string.notes_lists_empty_active_shared)
        }
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

    LaunchedEffect(archiveFeedback) {
        val feedback = archiveFeedback ?: return@LaunchedEffect
        val message = archiveFeedbackMessage ?: return@LaunchedEffect
        if (feedback.action == ListArchiveAction.ARCHIVED && feedback.isSuccess) {
            showArchivedLists = true
        }
        snackbarHostState.showSnackbar(message)
        viewModel.consumeArchiveFeedback()
    }

    PlatformBackHandler(
        enabled = showArchivedLists &&
            !showCreateDialog &&
            listForOptions == null &&
            listToEdit == null &&
            listToDelete == null &&
            listToShare == null &&
            listToGroup == null,
        onBack = { showArchivedLists = false },
    )
    PlatformBackHandler(
        enabled = isGroupScreen &&
            !showCreateDialog &&
            listForOptions == null &&
            listToEdit == null &&
            listToDelete == null &&
            listToShare == null &&
            listToGroup == null &&
            onBack != null,
        onBack = { onBack?.invoke() },
    )

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isGroupScreen) {
                            currentGroupName
                        } else if (showArchivedLists) {
                            stringResource(Res.string.archived_lists_title)
                        } else {
                            stringResource(Res.string.app_name)
                        },
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecretariaTopBarColor,
                    titleContentColor = SecretariaTopBarContentColor,
                    navigationIconContentColor = SecretariaTopBarContentColor,
                    actionIconContentColor = SecretariaTopBarContentColor,
                ),
                navigationIcon = {
                    if (isGroupScreen) {
                        IconButton(onClick = { onBack?.invoke() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    } else if (showArchivedLists) {
                        IconButton(onClick = { showArchivedLists = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                        }
                    }
                },
                actions = {
                    SecretariaOverflowMenu(
                        onOpenFriends = onOpenFriends,
                        onOpenSettings = onOpenSettings,
                        onLogout = onLogout,
                        onOpenArchivedLists = if (showArchivedLists || isGroupScreen) {
                            null
                        } else {
                            { showArchivedLists = true }
                        },
                    )
                },
            )
        },
        floatingActionButton = {
            if (!showArchivedLists && !isGroupScreen) {
                FloatingActionButton(
                    onClick = { showCreateDialog = true },
                    containerColor = SecretariaTopBarColor,
                    contentColor = SecretariaTopBarContentColor,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                }
            }
        },
    ) { innerPadding ->

        if (showCreateDialog) {
            CreateListDialog(
                onDismiss = { showCreateDialog = false },
                onCreate = { name, ordered, isGroup ->
                    onSectionSelected(NotesListsSection.MINE)
                    viewModel.createList(name, ordered, isGroup)
                    showCreateDialog = false
                },
            )
        }

        listForOptions?.let { list ->
            val isArchived = currentUserId != null && currentUserId in list.archivedBy
            val isGroupOwner = list.groupKey?.ownerId == currentUserId
            val canManageGrouping = !list.isGroup &&
                currentUserId != null &&
                (list.groupKey == null || isGroupOwner)
            ListOptionsDialog(
                listName = list.name,
                isOwner = list.ownerId == currentUserId,
                isArchived = isArchived,
                isGrouped = list.groupId != null,
                canManageGrouping = canManageGrouping,
                onArchive = {
                    viewModel.setListArchived(list, archived = !isArchived)
                    listForOptions = null
                },
                onShare = {
                    listToShare = list
                    listForOptions = null
                },
                onAddToGroup = {
                    listToGroup = list
                    listForOptions = null
                },
                onRemoveFromGroup = {
                    viewModel.setListGroup(list, group = null)
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

        listToGroup?.let { list ->
            GroupSelectionDialog(
                listName = list.name,
                groups = state.items
                    .filter { item ->
                        item.isGroup &&
                            item.ownerId == currentUserId &&
                            item.id != list.id &&
                            currentUserId !in item.archivedBy
                    }
                    .sortedBy { item -> item.name.lowercase() },
                onGroupSelected = { group ->
                    viewModel.setListGroup(list, group)
                    listToGroup = null
                },
                onCreateGroup = { name, ordered ->
                    onSectionSelected(NotesListsSection.MINE)
                    viewModel.createGroupAndAddList(list, name, ordered)
                    listToGroup = null
                },
                onDismiss = { listToGroup = null },
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
            if (!showArchivedLists && !isGroupScreen) {
                ListsSectionTabs(
                    selectedSection = selectedSection,
                    onSectionSelected = onSectionSelected,
                )
            }

            SortSelector(
                selected = state.sortOption,
                searchQuery = state.searchQuery,
                isSearchInputVisible = isSearchInputVisible,
                onSearchClick = { showSearchInput = true },
                onSearchQueryChange = viewModel::setSearchQuery,
                onCloseSearch = {
                    showSearchInput = false
                    viewModel.setSearchQuery("")
                },
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
                                text = when {
                                    state.searchQuery.isNotBlank() -> {
                                        stringResource(Res.string.notes_lists_search_empty)
                                    }

                                    !isGroupScreen && !showArchivedLists && state.items.isEmpty() -> {
                                        stringResource(Res.string.notes_lists_empty)
                                    }

                                    else -> emptyMessage
                                },
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                        }

                        else -> NotesListsContent(
                            items = visibleItems,
                            collaboratorsByListId = state.collaboratorsByListId,
                            onListSelected = onListSelected,
                            onGroupSelected = onGroupSelected,
                            currentUserId = currentUserId,
                            onListLongClick = openListOptions,
                            onListOptionsClick = openListOptions,
                            isGroupOrdered = isGroupScreen && currentGroupIsOrdered,
                            onListsReordered = currentGroup?.let { group ->
                                { listKeysInOrder -> viewModel.reorderGroupedLists(group, listKeysInOrder) }
                            },
                            archivedUserId = currentUserId.takeIf { showArchivedLists },
                            onUnarchiveClick = if (showArchivedLists) {
                                { list -> viewModel.setListArchived(list, archived = false) }
                            } else {
                                null
                            },
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
    searchQuery: String,
    isSearchInputVisible: Boolean,
    onSearchClick: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onCloseSearch: () -> Unit,
    onSortSelected: (SortOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    val visibleOptions = listOf(
        SortOption.NAME_ASC,
        SortOption.NAME_DESC,
        SortOption.DATE_ASC,
        SortOption.DATE_DESC,
    )

    LaunchedEffect(isSearchInputVisible) {
        if (isSearchInputVisible) {
            searchFocusRequester.requestFocus()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(48.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onSearchClick) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = stringResource(Res.string.notes_lists_search_hint),
            )
        }

        if (isSearchInputVisible) {
            SearchInput(
                query = searchQuery,
                focusRequester = searchFocusRequester,
                onQueryChange = onSearchQueryChange,
                onClose = onCloseSearch,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = stringResource(Res.string.order_by),
                style = MaterialTheme.typography.bodyMedium,
            )

            Spacer(modifier = Modifier.width(8.dp))

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
}

@Composable
private fun SearchInput(
    query: String,
    focusRequester: FocusRequester,
    onQueryChange: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)

    Row(
        modifier = modifier
            .height(40.dp)
            .clip(shape)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .padding(start = 12.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurface,
            ),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.notes_lists_search_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    innerTextField()
                }
            },
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = if (query.isBlank()) {
                    stringResource(Res.string.notes_lists_search_close)
                } else {
                    stringResource(Res.string.notes_lists_search_clear)
                },
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun NotesListsContent(
    items: List<NotesListSummary>,
    collaboratorsByListId: Map<String, List<ListCollaborator>>,
    onListSelected: (id: String, ownerId: String, name: String, isOrdered: Boolean) -> Unit,
    onGroupSelected: (id: String, ownerId: String, name: String, isOrdered: Boolean) -> Unit,
    currentUserId: String?,
    onListLongClick: (NotesListSummary) -> Unit,
    onListOptionsClick: (NotesListSummary) -> Unit,
    isGroupOrdered: Boolean = false,
    onListsReordered: ((List<NotesListKey>) -> Unit)? = null,
    archivedUserId: String? = null,
    onUnarchiveClick: ((NotesListSummary) -> Unit)? = null,
) {
    if (isGroupOrdered && onListsReordered != null) {
        OrderedNotesListsContent(
            items = items.sortedBy { item -> item.groupOrder },
            collaboratorsByListId = collaboratorsByListId,
            currentUserId = currentUserId,
            onListSelected = onListSelected,
            onListLongClick = onListLongClick,
            onListOptionsClick = onListOptionsClick,
            onListsReordered = onListsReordered,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { "${it.ownerId}/${it.id}" }) { item ->
            NotesListCard(
                item = item,
                collaborators = collaboratorsByListId[item.id].orEmpty(),
                currentUserId = currentUserId,
                onClick = {
                    if (item.isGroup) {
                        onGroupSelected(item.id, item.ownerId, item.name, item.isOrdered)
                    } else {
                        onListSelected(item.id, item.ownerId, item.name, item.isOrdered)
                    }
                },
                onLongClick = { onListLongClick(item) },
                onOptionsClick = { onListOptionsClick(item) },
                archivedAt = archivedUserId?.let { userId -> item.archivedAtBy[userId] },
                onUnarchiveClick = onUnarchiveClick?.let { unarchive -> { unarchive(item) } },
            )
        }
    }
}

@Composable
private fun OrderedNotesListsContent(
    items: List<NotesListSummary>,
    collaboratorsByListId: Map<String, List<ListCollaborator>>,
    currentUserId: String?,
    onListSelected: (id: String, ownerId: String, name: String, isOrdered: Boolean) -> Unit,
    onListLongClick: (NotesListSummary) -> Unit,
    onListOptionsClick: (NotesListSummary) -> Unit,
    onListsReordered: (List<NotesListKey>) -> Unit,
) {
    val lazyListState = rememberLazyListState()
    var displayItems by remember { mutableStateOf(items) }
    var pressedDragHandleListId by remember { mutableStateOf<String?>(null) }
    val reorderState = remember(lazyListState) {
        NotesReorderState(lazyListState) { fromIndex, toIndex ->
            displayItems = displayItems.moveList(fromIndex, toIndex)
        }
    }

    LaunchedEffect(items) {
        if (!reorderState.isDragging) {
            displayItems = items
        }
    }

    LazyColumn(
        state = lazyListState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        itemsIndexed(displayItems, key = { _, item -> "${item.ownerId}/${item.id}" }) { index, item ->
            val currentIndex by rememberUpdatedState(index)
            val dragHandleModifier = Modifier
                .size(40.dp)
                .pointerInput(item.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        pressedDragHandleListId = item.id
                        waitForUpOrCancellation()
                        pressedDragHandleListId = null
                    }
                }
                .pointerInput(item.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { reorderState.startDrag(currentIndex) },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            reorderState.dragBy(dragAmount.y)
                        },
                        onDragEnd = {
                            onListsReordered(displayItems.map(NotesListSummary::key))
                            reorderState.endDrag()
                        },
                        onDragCancel = {
                            displayItems = items
                            reorderState.endDrag()
                        },
                    )
                }

            NotesListCard(
                item = item,
                collaborators = collaboratorsByListId[item.id].orEmpty(),
                currentUserId = currentUserId,
                onClick = { onListSelected(item.id, item.ownerId, item.name, item.isOrdered) },
                onLongClick = {
                    if (pressedDragHandleListId != item.id) {
                        onListLongClick(item)
                    }
                },
                onOptionsClick = { onListOptionsClick(item) },
                orderIndex = index + 1,
                modifier = Modifier
                    .graphicsLayer {
                        translationY = reorderState.translationFor(index)
                    }
                    .zIndex(if (reorderState.draggingItemIndex == index) 1f else 0f),
                dragHandle = {
                    Box(
                        modifier = dragHandleModifier,
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.DragIndicator,
                            contentDescription = stringResource(Res.string.reorder_list_handle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
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
    orderIndex: Int? = null,
    modifier: Modifier = Modifier,
    dragHandle: (@Composable () -> Unit)? = null,
    archivedAt: Instant? = null,
    onUnarchiveClick: (() -> Unit)? = null,
) {
    val sharingSummary = sharingSummaryText(
        item = item,
        collaborators = collaborators,
        currentUserId = currentUserId,
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = if (onUnarchiveClick == null) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = if (onUnarchiveClick == null) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        } else {
            null
        },
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
                    text = orderIndex?.let { index -> "$index. ${item.name}" } ?: item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                dragHandle?.invoke()
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
                    modifier = Modifier.weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (item.isGroup) {
                        ListTypeBadge(
                            icon = Icons.Outlined.Folder,
                            label = stringResource(Res.string.list_group_badge),
                        )
                    }
                    if (item.isOrdered) {
                        ListTypeBadge(
                            icon = Icons.Outlined.FormatListNumbered,
                            contentDescription = stringResource(Res.string.list_ordered_badge),
                            label = if (item.isGroup) {
                                null
                            } else {
                                stringResource(Res.string.list_ordered_badge)
                            },
                        )
                    }
                }
            }
            if (archivedAt != null || onUnarchiveClick != null) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = archivedAt?.let { instant ->
                            stringResource(
                                Res.string.list_archived_on,
                                formatNotesListDate(instant),
                            )
                        } ?: stringResource(Res.string.list_archived_on_unknown),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                    )

                    onUnarchiveClick?.let { onUnarchive ->
                        FilledTonalButton(onClick = onUnarchive) {
                            Icon(
                                imageVector = Icons.Outlined.Unarchive,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(Res.string.unarchive_list))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListTypeBadge(
    icon: ImageVector,
    label: String?,
    contentDescription: String? = label,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        label?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
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

    val sharedCount = item.directSharedWithUserIds.size
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
    onCreate: (name: String, ordered: Boolean, isGroup: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ordered by remember { mutableStateOf(false) }
    var isGroup by remember { mutableStateOf(false) }

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
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(stringResource(Res.string.create_list_group))
                    Switch(checked = isGroup, onCheckedChange = { isGroup = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim(), ordered, isGroup) },
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
    isArchived: Boolean,
    isGrouped: Boolean,
    canManageGrouping: Boolean,
    onArchive: () -> Unit,
    onShare: () -> Unit,
    onAddToGroup: () -> Unit,
    onRemoveFromGroup: () -> Unit,
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
                OptionRow(
                    icon = if (isArchived) Icons.Outlined.Unarchive else Icons.Outlined.Archive,
                    label = if (isArchived) {
                        stringResource(Res.string.unarchive_list)
                    } else {
                        stringResource(Res.string.archive_list)
                    },
                    onClick = onArchive,
                )
                if (canManageGrouping) {
                    OptionRow(
                        icon = if (isGrouped) Icons.Outlined.FolderOff else Icons.Outlined.CreateNewFolder,
                        label = if (isGrouped) {
                            stringResource(Res.string.remove_list_from_group)
                        } else {
                            stringResource(Res.string.add_list_to_group)
                        },
                        onClick = if (isGrouped) onRemoveFromGroup else onAddToGroup,
                    )
                }
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
private fun GroupSelectionDialog(
    listName: String,
    groups: List<NotesListSummary>,
    onGroupSelected: (NotesListSummary) -> Unit,
    onCreateGroup: (name: String, ordered: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateGroup by remember(groups.isEmpty()) { mutableStateOf(groups.isEmpty()) }
    var newGroupName by remember { mutableStateOf("") }
    var newGroupOrdered by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.select_group_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                Text(
                    text = listName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (groups.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        groups.forEach { group ->
                            OptionRow(
                                icon = Icons.Outlined.CreateNewFolder,
                                label = group.name,
                                onClick = { onGroupSelected(group) },
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(Res.string.select_group_empty),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )

                if (showCreateGroup) {
                    Text(
                        text = stringResource(Res.string.create_group_inline_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    OutlinedTextField(
                        value = newGroupName,
                        onValueChange = { newGroupName = it },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(stringResource(Res.string.create_list_ordered))
                        Switch(
                            checked = newGroupOrdered,
                            onCheckedChange = { newGroupOrdered = it },
                        )
                    }
                    FilledTonalButton(
                        onClick = { onCreateGroup(newGroupName.trim(), newGroupOrdered) },
                        enabled = newGroupName.isNotBlank(),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CreateNewFolder,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(Res.string.create_group_button))
                    }
                } else {
                    OptionRow(
                        icon = Icons.Outlined.CreateNewFolder,
                        label = stringResource(Res.string.create_group_button),
                        onClick = { showCreateGroup = true },
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

private fun NotesListSummary.isVisibleInRoot(
    currentUserId: String?,
    selectedSection: NotesListsSection,
    showArchivedLists: Boolean,
): Boolean {
    val isArchived = currentUserId != null && currentUserId in archivedBy
    return if (showArchivedLists) {
        isArchived
    } else {
        when (selectedSection) {
            NotesListsSection.MINE -> !isShared && !isArchived
            NotesListsSection.SHARED -> isShared && !isArchived
        }
    }
}

private fun NotesListSummary.isVisibleRootGroup(
    currentUserId: String?,
    showArchivedLists: Boolean,
): Boolean {
    val isArchived = currentUserId != null && currentUserId in archivedBy
    val isAccessible = ownerId == currentUserId || isShared
    return isAccessible && if (showArchivedLists) {
        isArchived
    } else {
        !isArchived
    }
}

enum class NotesListsSection {
    MINE,
    SHARED,
}
