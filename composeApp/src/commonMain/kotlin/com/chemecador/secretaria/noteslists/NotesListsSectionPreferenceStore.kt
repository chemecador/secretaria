package com.chemecador.secretaria.noteslists

import androidx.compose.runtime.Composable

internal interface NotesListsSectionPreferenceStore {
    suspend fun load(): NotesListsSection
    suspend fun save(section: NotesListsSection)
    suspend fun clear()
}

internal object NoOpNotesListsSectionPreferenceStore : NotesListsSectionPreferenceStore {
    override suspend fun load(): NotesListsSection = NotesListsSection.MINE
    override suspend fun save(section: NotesListsSection) = Unit
    override suspend fun clear() = Unit
}

internal fun String?.toNotesListsSectionOrDefault(): NotesListsSection =
    runCatching {
        NotesListsSection.valueOf(this ?: return NotesListsSection.MINE)
    }.getOrDefault(NotesListsSection.MINE)

@Composable
internal expect fun rememberNotesListsSectionPreferenceStore(): NotesListsSectionPreferenceStore
