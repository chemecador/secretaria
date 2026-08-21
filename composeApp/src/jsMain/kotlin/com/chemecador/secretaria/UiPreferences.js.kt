package com.chemecador.secretaria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
internal actual fun rememberUiPreferences(): UiPreferences = remember { LocalStorageUiPreferences() }

private class LocalStorageUiPreferences : UiPreferences {

    override suspend fun getString(key: String): String? =
        try {
            window.localStorage.getItem(key.namespaced())
        } catch (_: Throwable) {
            null
        }

    override suspend fun putString(key: String, value: String) {
        try {
            window.localStorage.setItem(key.namespaced(), value)
        } catch (_: Throwable) {
            // localStorage puede estar bloqueado; una preferencia de UI no justifica romper nada.
        }
    }

    override suspend fun remove(key: String) {
        try {
            window.localStorage.removeItem(key.namespaced())
        } catch (_: Throwable) {
            // ignore
        }
    }

    /** localStorage es por origen y puede compartirse con otras paginas del mismo dominio. */
    private fun String.namespaced(): String = "$KEY_PREFIX$this"
}

private const val KEY_PREFIX = "secretaria."
