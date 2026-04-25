package com.chemecador.secretaria.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VolunteerActivism
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.chemecador.secretaria.PlatformBackHandler
import com.chemecador.secretaria.SecretariaOverflowMenu
import com.chemecador.secretaria.SecretariaTheme
import com.chemecador.secretaria.SecretariaTopBarColor
import com.chemecador.secretaria.SecretariaTopBarContentColor
import com.chemecador.secretaria.config.AppBuildInfo
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.notes.DEFAULT_NOTE_COLOR
import com.chemecador.secretaria.notes.noteColor
import com.chemecador.secretaria.notes.noteColorNeedsDarkForeground
import com.chemecador.secretaria.notes.noteColorPalette
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.settings_account_email
import secretaria.composeapp.generated.resources.settings_account_section
import secretaria.composeapp.generated.resources.settings_account_user_code
import secretaria.composeapp.generated.resources.settings_app_section
import secretaria.composeapp.generated.resources.settings_contact_dialog_confirm
import secretaria.composeapp.generated.resources.settings_contact_dialog_message
import secretaria.composeapp.generated.resources.settings_contact_dialog_title
import secretaria.composeapp.generated.resources.settings_contact_email
import secretaria.composeapp.generated.resources.settings_data_not_provided
import secretaria.composeapp.generated.resources.settings_default_note_color
import secretaria.composeapp.generated.resources.settings_default_note_color_dialog_title
import secretaria.composeapp.generated.resources.settings_developer
import secretaria.composeapp.generated.resources.settings_project_label
import secretaria.composeapp.generated.resources.settings_project_summary
import secretaria.composeapp.generated.resources.settings_support_description
import secretaria.composeapp.generated.resources.settings_support_label
import secretaria.composeapp.generated.resources.settings_support_section
import secretaria.composeapp.generated.resources.settings_title
import secretaria.composeapp.generated.resources.settings_version

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSupportCreator: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val authRepository = koinInject<AuthRepository>()
    val friendsRepository = koinInject<FriendsRepository>()
    val accountSettingsRepository = koinInject<AccountSettingsRepository>()
    val accountEmail = authRepository.currentUserEmail
        ?: stringResource(Res.string.settings_data_not_provided)
    val accountUserCode by produceState<String?>(initialValue = null, authRepository.currentUserId, friendsRepository) {
        value = authRepository.currentUserId
            ?.let { friendsRepository.getMyFriendCode().getOrNull() }
    }
    var defaultNoteColor by remember { mutableStateOf(DEFAULT_NOTE_COLOR) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(accountSettingsRepository, authRepository.currentUserId) {
        defaultNoteColor = accountSettingsRepository.getDefaultNoteColor()
            .getOrDefault(DEFAULT_NOTE_COLOR)
    }

    SettingsScreenContent(
        accountEmail = accountEmail,
        accountUserCode = accountUserCode ?: " ",
        defaultNoteColor = defaultNoteColor,
        onDefaultNoteColorSelected = { color ->
            coroutineScope.launch {
                accountSettingsRepository.setDefaultNoteColor(color)
                    .onSuccess { defaultNoteColor = color }
            }
        },
        onOpenSupportCreator = onOpenSupportCreator,
        onBack = onBack,
        onOpenFriends = onOpenFriends,
        onOpenSettings = onOpenSettings,
        onLogout = onLogout,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsScreenContent(
    accountEmail: String,
    accountUserCode: String,
    defaultNoteColor: Long,
    onDefaultNoteColorSelected: (Long) -> Unit,
    onOpenSupportCreator: () -> Unit,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showSendMailDialog by remember { mutableStateOf(false) }
    var showDefaultNoteColorDialog by remember { mutableStateOf(false) }

    PlatformBackHandler(
        enabled = !showSendMailDialog && !showDefaultNoteColorDialog,
        onBack = onBack,
    )

    if (showSendMailDialog) {
        AlertDialog(
            onDismissRequest = { showSendMailDialog = false },
            title = { Text(stringResource(Res.string.settings_contact_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        Res.string.settings_contact_dialog_message,
                        AppMetadata.CONTACT_EMAIL,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSendMailDialog = false
                        uriHandler.openUri(AppMetadata.CONTACT_URI)
                    },
                ) {
                    Text(stringResource(Res.string.settings_contact_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showSendMailDialog = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }

    if (showDefaultNoteColorDialog) {
        DefaultNoteColorDialog(
            selectedColor = defaultNoteColor,
            onColorSelected = { color ->
                showDefaultNoteColorDialog = false
                onDefaultNoteColorSelected(color)
            },
            onDismiss = { showDefaultNoteColorDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SecretariaTopBarColor,
                    titleContentColor = SecretariaTopBarContentColor,
                    navigationIconContentColor = SecretariaTopBarContentColor,
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            SettingsSection(title = stringResource(Res.string.settings_account_section)) {
                SettingsRow(
                    icon = Icons.Default.AlternateEmail,
                    label = stringResource(Res.string.settings_account_email),
                    value = accountEmail,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Default.Person,
                    label = stringResource(Res.string.settings_account_user_code),
                    value = accountUserCode,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DefaultNoteColorRow(
                    color = defaultNoteColor,
                    onClick = { showDefaultNoteColorDialog = true },
                )
            }

            SettingsSection(title = stringResource(Res.string.settings_app_section)) {
                SettingsRow(
                    icon = Icons.Default.Info,
                    label = stringResource(Res.string.settings_version),
                    value = AppBuildInfo.versionName,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Default.Computer,
                    label = stringResource(Res.string.settings_developer),
                    value = AppMetadata.DEVELOPER,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SettingsRow(
                    icon = Icons.Default.Email,
                    label = stringResource(Res.string.settings_contact_email),
                    value = AppMetadata.CONTACT_EMAIL,
                    isClickable = true,
                    onClick = { showSendMailDialog = true },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ProjectCard(
                    onOpenGithub = { uriHandler.openUri(AppMetadata.URL_GITHUB) },
                )
            }

            SettingsSection(title = stringResource(Res.string.settings_support_section)) {
                SupportCreatorNavigationRow(onClick = onOpenSupportCreator)
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        ) {
            Column {
                content()
            }
        }
    }
}

@Composable
private fun ProjectCard(
    onOpenGithub: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_project_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.settings_project_summary),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = AppMetadata.URL_GITHUB,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(onClick = onOpenGithub),
            )
        }
    }
}

@Composable
private fun SupportCreatorNavigationRow(
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.VolunteerActivism,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(Res.string.settings_support_label),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(Res.string.settings_support_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DefaultNoteColorRow(
    color: Long,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Palette,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = stringResource(Res.string.settings_default_note_color),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        ColorSwatch(color = color)
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.padding(start = 8.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DefaultNoteColorDialog(
    selectedColor: Long,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
        textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        title = { Text(stringResource(Res.string.settings_default_note_color_dialog_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp),
            ) {
                items(noteColorPalette) { color ->
                    DefaultNoteColorOption(
                        color = color,
                        isSelected = color == selectedColor,
                        onClick = { onColorSelected(color) },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.cancel))
            }
        },
    )
}

@Composable
private fun DefaultNoteColorOption(
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

@Composable
private fun ColorSwatch(
    color: Long,
) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(noteColor(color))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            ),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    label: String,
    value: String,
    isClickable: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isClickable && onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isClickable) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

private object AppMetadata {
    const val DEVELOPER = "chemecador"
    const val CONTACT_EMAIL = "chemecador@gmail.com"
    const val CONTACT_URI = "mailto:chemecador@gmail.com?subject=Contacto%20desde%20Secretaria"
    const val URL_GITHUB = "https://github.com/chemecador/secretaria"
}

@Preview(
    name = "Ajustes",
    showBackground = true,
    widthDp = 400,
    heightDp = 900,
)
@Composable
private fun SettingsScreenPreview() {
    SecretariaTheme {
        SettingsScreenContent(
            accountEmail = "user@example.com",
            accountUserCode = "261051",
            defaultNoteColor = 0xFFFFF9C4L,
            onDefaultNoteColorSelected = {},
            onOpenSupportCreator = {},
            onBack = {},
            onOpenFriends = {},
            onOpenSettings = {},
            onLogout = {},
        )
    }
}
