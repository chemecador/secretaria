package com.chemecador.secretaria.noteslists

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.about_author
import secretaria.composeapp.generated.resources.about_ok
import secretaria.composeapp.generated.resources.about_title
import secretaria.composeapp.generated.resources.about_version
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
import secretaria.composeapp.generated.resources.list_ordered_badge
import secretaria.composeapp.generated.resources.logout_confirm
import secretaria.composeapp.generated.resources.logout_message
import secretaria.composeapp.generated.resources.logout_title
import secretaria.composeapp.generated.resources.menu_about
import secretaria.composeapp.generated.resources.menu_friends
import secretaria.composeapp.generated.resources.menu_logout
import secretaria.composeapp.generated.resources.notes_lists_empty
import secretaria.composeapp.generated.resources.notes_lists_error_generic
import secretaria.composeapp.generated.resources.order_by
import secretaria.composeapp.generated.resources.sort_date_asc
import secretaria.composeapp.generated.resources.sort_date_desc
import secretaria.composeapp.generated.resources.sort_name_asc
import secretaria.composeapp.generated.resources.sort_name_desc

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListsScreen(
    viewModel: NotesListsViewModel,
    onListSelected: (id: String, name: String, isOrdered: Boolean) -> Unit,
    onOpenFriends: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }
    var listForOptions by remember { mutableStateOf<NotesListSummary?>(null) }
    var listToEdit by remember { mutableStateOf<NotesListSummary?>(null) }
    var listToDelete by remember { mutableStateOf<NotesListSummary?>(null) }
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecretariaTopBarColor,
                    titleContentColor = SecretariaTopBarContentColor,
                    actionIconContentColor = SecretariaTopBarContentColor,
                ),
                actions = {
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = null)
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.menu_friends)) },
                                onClick = {
                                    showOverflowMenu = false
                                    onOpenFriends()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.menu_about)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showAboutDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.menu_logout)) },
                                onClick = {
                                    showOverflowMenu = false
                                    showLogoutConfirmation = true
                                },
                            )
                        }
                    }
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
                    viewModel.createList(name, ordered)
                    showCreateDialog = false
                },
            )
        }

        listForOptions?.let { list ->
            ListOptionsDialog(
                listName = list.name,
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

        listToEdit?.let { list ->
            EditListDialog(
                list = list,
                onDismiss = { listToEdit = null },
                onSave = { name, ordered ->
                    viewModel.updateList(list.id, name, ordered)
                    listToEdit = null
                },
            )
        }

        listToDelete?.let { list ->
            DeleteListDialog(
                listName = list.name,
                onDismiss = { listToDelete = null },
                onConfirm = {
                    viewModel.deleteList(list.id)
                    listToDelete = null
                },
            )
        }

        if (showLogoutConfirmation) {
            LogoutConfirmationDialog(
                onDismiss = { showLogoutConfirmation = false },
                onConfirm = {
                    showLogoutConfirmation = false
                    onLogout()
                },
            )
        }

        if (showAboutDialog) {
            AboutDialog(onDismiss = { showAboutDialog = false })
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SortSelector(
                selected = state.sortOption,
                onSortSelected = viewModel::setSort,
            )

            when {
                state.isLoading -> CenteredMessage {
                    CircularProgressIndicator()
                }

                state.errorMessage != null -> CenteredMessage {
                    val errorMessage = state.errorMessage?.takeIf { it.isNotBlank() }
                        ?: stringResource(Res.string.notes_lists_error_generic)
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                state.items.isEmpty() -> CenteredMessage {
                    Text(
                        text = stringResource(Res.string.notes_lists_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center,
                    )
                }

                else -> NotesListsContent(
                    items = state.items,
                    onListSelected = onListSelected,
                    onListLongClick = { listForOptions = it },
                )
            }
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
    onListSelected: (id: String, name: String, isOrdered: Boolean) -> Unit,
    onListLongClick: (NotesListSummary) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            NotesListCard(
                item = item,
                onClick = { onListSelected(item.id, item.name, item.isOrdered) },
                onLongClick = { onListLongClick(item) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NotesListCard(
    item: NotesListSummary,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
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
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(Res.string.list_created_by, item.creator),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
private fun CreateListDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, ordered: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var ordered by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
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
            Column {
                TextButton(
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(Res.string.edit_list))
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
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
private fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.logout_title)) },
        text = { Text(stringResource(Res.string.logout_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(Res.string.logout_confirm))
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
private fun AboutDialog(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.about_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(Res.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(Res.string.about_version, "1.0"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = stringResource(Res.string.about_author, "chemecador"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.about_ok))
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
