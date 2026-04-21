package com.chemecador.secretaria.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
    val accountEmail = authRepository.currentUserEmail
        ?: stringResource(Res.string.settings_data_not_provided)
    val accountUserCode by produceState<String?>(initialValue = null, authRepository.currentUserId, friendsRepository) {
        value = authRepository.currentUserId
            ?.let { friendsRepository.getMyFriendCode().getOrNull() }
    }

    SettingsScreenContent(
        accountEmail = accountEmail,
        accountUserCode = accountUserCode ?: stringResource(Res.string.settings_data_not_provided),
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
    onOpenSupportCreator: () -> Unit,
    onBack: () -> Unit,
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uriHandler = LocalUriHandler.current
    var showSendMailDialog by remember { mutableStateOf(false) }

    PlatformBackHandler(
        enabled = !showSendMailDialog,
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
            onOpenSupportCreator = {},
            onBack = {},
            onOpenFriends = {},
            onOpenSettings = {},
            onLogout = {},
        )
    }
}
