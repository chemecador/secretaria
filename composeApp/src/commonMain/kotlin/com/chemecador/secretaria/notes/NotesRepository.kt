package com.chemecador.secretaria.notes

interface NotesRepository {
    suspend fun getNotesForList(listId: String): Result<List<Note>>
    suspend fun createNote(listId: String, title: String, content: String): Result<Note>
}
