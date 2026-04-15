package com.chemecador.secretaria

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.cancel
import secretaria.composeapp.generated.resources.logout_confirm
import secretaria.composeapp.generated.resources.logout_message
import secretaria.composeapp.generated.resources.logout_title
import secretaria.composeapp.generated.resources.menu_friends
import secretaria.composeapp.generated.resources.menu_logout
import secretaria.composeapp.generated.resources.menu_settings

@Composable
fun SecretariaOverflowMenu(
    onOpenFriends: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit,
) {
    var showMenu by remember { mutableStateOf(false) }
    var showLogoutConfirmation by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.menu_friends)) },
                onClick = {
                    showMenu = false
                    onOpenFriends()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.menu_settings)) },
                onClick = {
                    showMenu = false
                    onOpenSettings()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.menu_logout)) },
                onClick = {
                    showMenu = false
                    showLogoutConfirmation = true
                },
            )
        }
    }

    if (showLogoutConfirmation) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmation = false },
            title = { Text(stringResource(Res.string.logout_title)) },
            text = { Text(stringResource(Res.string.logout_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirmation = false
                        onLogout()
                    },
                ) {
                    Text(stringResource(Res.string.logout_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirmation = false }) {
                    Text(stringResource(Res.string.cancel))
                }
            },
        )
    }
}
