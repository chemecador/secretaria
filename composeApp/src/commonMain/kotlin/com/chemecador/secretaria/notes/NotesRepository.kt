package com.chemecador.secretaria.notes

interface NotesRepository {
    suspend fun getNotesForList(listId: String): Result<List<Note>>
    suspend fun createNote(listId: String, title: String, content: String): Result<Note>
    suspend fun deleteNote(listId: String, noteId: String): Result<Unit>
    suspend fun updateNote(listId: String, noteId: String, title: String, content: String): Result<Note>
}
