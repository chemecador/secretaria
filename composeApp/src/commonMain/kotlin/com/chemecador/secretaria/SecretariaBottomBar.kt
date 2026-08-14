package com.chemecador.secretaria

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.stringResource
import secretaria.composeapp.generated.resources.Res
import secretaria.composeapp.generated.resources.mode_lists
import secretaria.composeapp.generated.resources.mode_reminders

/**
 * Modos raiz de la app. Son destinos de primer nivel, no una pila: cambiar de modo no deja
 * nada que deshacer con el boton atras.
 */
enum class SecretariaRootMode {
    REMINDERS,
    LISTS,
}

/**
 * La barra inferior es el unico mecanismo de navegacion entre modos. Dentro de Listas, la
 * separacion entre propias y compartidas es un filtro (chips), no navegacion, para que los dos
 * niveles nunca compitan visualmente.
 */
@Composable
fun SecretariaBottomBar(
    selected: SecretariaRootMode,
    onSelect: (SecretariaRootMode) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        NavigationBarItem(
            selected = selected == SecretariaRootMode.REMINDERS,
            onClick = { onSelect(SecretariaRootMode.REMINDERS) },
            icon = {
                Icon(
                    imageVector = Icons.Filled.NotificationsNone,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(Res.string.mode_reminders)) },
            colors = secretariaNavigationBarItemColors(),
        )
        NavigationBarItem(
            selected = selected == SecretariaRootMode.LISTS,
            onClick = { onSelect(SecretariaRootMode.LISTS) },
            icon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ListAlt,
                    contentDescription = null,
                )
            },
            label = { Text(stringResource(Res.string.mode_lists)) },
            colors = secretariaNavigationBarItemColors(),
        )
    }
}

@Composable
private fun secretariaNavigationBarItemColors() = NavigationBarItemDefaults.colors(
    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
    selectedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
)
