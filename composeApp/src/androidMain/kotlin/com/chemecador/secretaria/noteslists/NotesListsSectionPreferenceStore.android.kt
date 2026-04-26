package com.chemecador.secretaria.noteslists

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit

@Composable
internal actual fun rememberNotesListsSectionPreferenceStore(): NotesListsSectionPreferenceStore {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        SharedPreferencesNotesListsSectionPreferenceStore(
            context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE),
        )
    }
}

private class SharedPreferencesNotesListsSectionPreferenceStore(
    private val preferences: SharedPreferences,
) : NotesListsSectionPreferenceStore {

    override suspend fun load(): NotesListsSection =
        preferences.getString(KEY_SELECTED_SECTION, null).toNotesListsSectionOrDefault()

    override suspend fun save(section: NotesListsSection) {
        preferences.edit {
            putString(KEY_SELECTED_SECTION, section.name)
        }
    }

    override suspend fun clear() {
        preferences.edit {
            remove(KEY_SELECTED_SECTION)
        }
    }
}

private const val PREFERENCES_NAME = "secretaria.ui.preferences"
private const val KEY_SELECTED_SECTION = "notes_lists.selected_section"
