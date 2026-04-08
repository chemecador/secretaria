package com.chemecador.secretaria.notes

interface NotesRepository {
    suspend fun getNotesForList(listId: String): Result<List<Note>>
}
