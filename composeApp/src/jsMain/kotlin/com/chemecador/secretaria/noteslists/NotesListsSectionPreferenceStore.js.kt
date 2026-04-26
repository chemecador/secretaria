package com.chemecador.secretaria.noteslists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import kotlinx.browser.window

@Composable
internal actual fun rememberNotesListsSectionPreferenceStore(): NotesListsSectionPreferenceStore =
    remember { LocalStorageNotesListsSectionPreferenceStore() }

private class LocalStorageNotesListsSectionPreferenceStore(
    private val key: String = KEY_SELECTED_SECTION,
) : NotesListsSectionPreferenceStore {

    override suspend fun load(): NotesListsSection =
        try {
            window.localStorage.getItem(key).toNotesListsSectionOrDefault()
        } catch (_: Throwable) {
            NotesListsSection.MINE
        }

    override suspend fun save(section: NotesListsSection) {
        try {
            window.localStorage.setItem(key, section.name)
        } catch (_: Throwable) {
            // ignore
        }
    }

    override suspend fun clear() {
        try {
            window.localStorage.removeItem(key)
        } catch (_: Throwable) {
            // ignore
        }
    }
}

private const val KEY_SELECTED_SECTION = "secretaria.notesLists.selectedSection"
