package com.chemecador.secretaria.noteslists

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.app_name
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.create_list_button
import secretaria.composeapp.generated.resources.create_list_name_hint
import secretaria.composeapp.generated.resources.create_list_ordered
import secretaria.composeapp.generated.resources.create_list_title
import secretaria.composeapp.generated.resources.list_created_by
import secretaria.composeapp.generated.resources.list_ordered_badge
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
    onListSelected: (id: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.load()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.app_name)) },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
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
    onListSelected: (id: String, name: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            NotesListCard(
                item = item,
                onClick = { onListSelected(item.id, item.name) },
            )
        }
    }
}

@Composable
private fun NotesListCard(
    item: NotesListSummary,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
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
        title = { Text(stringResource(Res.string.create_list_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(Res.string.create_list_name_hint)) },
                    singleLine = true,
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
private fun SortOption.label(): String {
    return when (this) {
        SortOption.NAME_ASC -> stringResource(Res.string.sort_name_asc)
        SortOption.NAME_DESC -> stringResource(Res.string.sort_name_desc)
        SortOption.DATE_ASC -> stringResource(Res.string.sort_date_asc)
        SortOption.DATE_DESC,
        SortOption.CUSTOM -> stringResource(Res.string.sort_date_desc)
    }
}
