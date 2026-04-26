package com.chemecador.secretaria.noteslists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import platform.Foundation.NSUserDefaults

@Composable
internal actual fun rememberNotesListsSectionPreferenceStore(): NotesListsSectionPreferenceStore =
    remember { UserDefaultsNotesListsSectionPreferenceStore() }

private class UserDefaultsNotesListsSectionPreferenceStore(
    private val defaults: NSUserDefaults = NSUserDefaults.standardUserDefaults,
) : NotesListsSectionPreferenceStore {

    override suspend fun load(): NotesListsSection =
        defaults.stringForKey(KEY_SELECTED_SECTION).toNotesListsSectionOrDefault()

    override suspend fun save(section: NotesListsSection) {
        defaults.setObject(section.name, forKey = KEY_SELECTED_SECTION)
    }

    override suspend fun clear() {
        defaults.removeObjectForKey(KEY_SELECTED_SECTION)
    }
}

private const val KEY_SELECTED_SECTION = "com.chemecador.secretaria.notesLists.selectedSection"
