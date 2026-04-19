package com.chemecador.secretaria.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.chemecador.secretaria.PlatformBackHandler
import com.chemecador.secretaria.SecretariaOverflowMenu
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.noteslists.formatNotesListDate
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.action_collapse_colors
import secretaria.composeapp.generated.resources.action_expand_colors
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.delete
import secretaria.composeapp.generated.resources.delete_note_confirmation
import secretaria.composeapp.generated.resources.list_created_by
import secretaria.composeapp.generated.resources.note_completed_badge
import secretaria.composeapp.generated.resources.note_detail_color
import secretaria.composeapp.generated.resources.note_detail_content_hint
import secretaria.composeapp.generated.resources.note_detail_save
import secretaria.composeapp.generated.resources.note_detail_title_hint
import secretaria.composeapp.generated.resources.note_detail_unsaved_discard
import secretaria.composeapp.generated.resources.note_detail_unsaved_keep
import secretaria.composeapp.generated.resources.note_detail_unsaved_message
import secretaria.composeapp.generated.resources.note_detail_unsaved_title

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    note: Note,
    onSave: (title: String, content: String, completed: Boolean, color: Long) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var title by remember { mutableStateOf(note.title) }
    var content by remember { mutableStateOf(note.content) }
    var completed by remember { mutableStateOf(note.completed) }
    var selectedColor by remember { mutableStateOf(note.color) }
    var isColorExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showUnsavedDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    val hasUnsavedChanges by remember {
        derivedStateOf {
            title != note.title ||
                content != note.content ||
                completed != note.completed ||
                selectedColor != note.color
        }
    }

    val attemptBack = {
        if (hasUnsavedChanges) {
            showUnsavedDialog = true
        } else {
            onBack()
        }
    }

    PlatformBackHandler(
        enabled = !showDeleteDialog && !showUnsavedDialog,
        onBack = attemptBack,
    )

    if (showUnsavedDialog) {
        AlertDialog(
            onDismissRequest = { showUnsavedDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(stringResource(Res.string.note_detail_unsaved_title)) },
            text = { Text(stringResource(Res.string.note_detail_unsaved_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnsavedDialog = false
                        onBack()
                    },
                ) {
                    Text(
                        text = stringResource(Res.string.note_detail_unsaved_discard),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnsavedDialog = false }) {
                    Text(stringResource(Res.string.note_detail_unsaved_keep))
                }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text(title.ifBlank { note.title }) },
            text = { Text(stringResource(Res.string.delete_note_confirmation)) },
            confirmButton = {
                TextButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title.ifBlank { note.title }) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecretariaTopBarColor,
                    titleContentColor = SecretariaTopBarContentColor,
                    navigationIconContentColor = SecretariaTopBarContentColor,
                    actionIconContentColor = SecretariaTopBarContentColor,
                ),
                navigationIcon = {
                    IconButton(onClick = attemptBack) {
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(noteColor(selectedColor))
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = formatNotesListDate(note.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.list_created_by, note.creator),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(Res.string.note_detail_title_hint)) },
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
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(Res.string.note_detail_content_hint)) },
                minLines = 5,
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
            ) {
                Checkbox(
                    checked = completed,
                    onCheckedChange = { completed = it },
                )
                Text(
                    text = stringResource(Res.string.note_completed_badge),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .clickable { isColorExpanded = !isColorExpanded }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.note_detail_color),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(noteColor(selectedColor))
                            .border(
                                width = 1.dp,
                                color = MaterialTheme.colorScheme.outline,
                                shape = CircleShape,
                            ),
                    )
                    Icon(
                        imageVector = if (isColorExpanded) {
                            Icons.Default.ExpandLess
                        } else {
                            Icons.Default.ExpandMore
                        },
                        contentDescription = if (isColorExpanded) {
                            stringResource(Res.string.action_collapse_colors)
                        } else {
                            stringResource(Res.string.action_expand_colors)
                        },
                    )
                }
            }
            AnimatedVisibility(
                visible = isColorExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(6),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                ) {
                    items(noteColorPalette) { color ->
                        ColorOption(
                            color = color,
                            isSelected = color == selectedColor,
                            onClick = { selectedColor = color },
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        onSave(
                            title.trim(),
                            content.trim(),
                            completed,
                            selectedColor,
                        )
                    },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(Res.string.note_detail_save))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ColorOption(
    color: Long,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .padding(6.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(noteColor(color))
            .border(
                width = if (isSelected) 3.dp else 1.dp,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = if (noteColorNeedsDarkForeground(color)) Color.Black else Color.White,
            )
        }
    }
}
