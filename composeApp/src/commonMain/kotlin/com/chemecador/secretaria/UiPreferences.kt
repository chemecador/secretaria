package com.chemecador.secretaria

import androidx.compose.runtime.Composable

/**
 * Almacen clave-valor de texto para preferencias de UI. Deliberadamente minimo: DataStore sigue
 * fuera del alcance de la migracion y cada plataforma ya trae un almacen nativo suficiente para
 * unas pocas claves.
 *
 * No guarda datos de negocio ni nada que deba sobrevivir a un cambio de dispositivo: solo el
 * estado de navegacion que hace que la app se reabra donde el usuario la dejo.
 */
internal interface UiPreferences {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
    suspend fun remove(key: String)
}

internal object NoOpUiPreferences : UiPreferences {
    override suspend fun getString(key: String): String? = null
    override suspend fun putString(key: String, value: String) = Unit
    override suspend fun remove(key: String) = Unit
}

@Composable
internal expect fun rememberUiPreferences(): UiPreferences
