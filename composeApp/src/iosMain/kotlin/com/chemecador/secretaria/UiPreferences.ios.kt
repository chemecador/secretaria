package com.chemecador.secretaria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberUiPreferences(): UiPreferences = remember { UserDefaultsUiPreferences() }

private class UserDefaultsUiPreferences(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : UiPreferences {

    override suspend fun getString(key: String): String? = defaults.stringForKey(key.namespaced())

    override suspend fun putString(key: String, value: String) {
        defaults.setObject(value, forKey = key.namespaced())
    }

    override suspend fun remove(key: String) {
        defaults.removeObjectForKey(key.namespaced())
    }

    /** NSUserDefaults es un espacio plano compartido con el host, asi que las claves van con prefijo. */
    private fun String.namespaced(): String = "$KEY_PREFIX$this"
}

private const val KEY_PREFIX = "com.chemecador.secretaria."
