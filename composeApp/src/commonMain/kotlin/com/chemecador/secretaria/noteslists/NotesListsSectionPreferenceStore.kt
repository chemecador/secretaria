package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.UiPreferences

/**
 * Recuerda si el usuario estaba mirando sus listas o las compartidas. Se borra al cerrar sesion:
 * es una preferencia de la sesion, no del dispositivo.
 */
internal class NotesListsSectionPreferenceStore(
    private val preferences: UiPreferences,
) {
    suspend fun load(): NotesListsSection =
        preferences.getString(KEY_SELECTED_SECTION).toNotesListsSectionOrDefault()

    suspend fun save(section: NotesListsSection) {
        preferences.putString(KEY_SELECTED_SECTION, section.name)
    }

    suspend fun clear() {
        preferences.remove(KEY_SELECTED_SECTION)
    }
}

internal fun String?.toNotesListsSectionOrDefault(): NotesListsSection =
    runCatching {
        NotesListsSection.valueOf(this ?: return NotesListsSection.MINE)
    }.getOrDefault(NotesListsSection.MINE)

private const val KEY_SELECTED_SECTION = "notes_lists.selected_section"
