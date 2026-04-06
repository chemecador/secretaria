package com.chemecador.secretaria.noteslists

interface NotesListsRepository {
    suspend fun getLists(): Result<List<NotesListSummary>>
}
