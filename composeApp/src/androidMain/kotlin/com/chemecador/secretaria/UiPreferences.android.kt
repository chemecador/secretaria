package com.chemecador.secretaria

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

@Composable
internal actual fun rememberUiPreferences(): UiPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        SharedPreferencesUiPreferences(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }
}

private class SharedPreferencesUiPreferences(
    private val preferences: SharedPreferences,
) : UiPreferences {

    override suspend fun getString(key: String): String? = preferences.getString(key, null)

    override suspend fun putString(key: String, value: String) {
        preferences.edit { putString(key, value) }
    }

    override suspend fun remove(key: String) {
        preferences.edit { remove(key) }
    }
}

private const val PREFERENCES_NAME = "secretaria.ui.preferences"
