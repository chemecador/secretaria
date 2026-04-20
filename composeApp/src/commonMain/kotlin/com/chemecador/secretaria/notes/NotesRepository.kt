package com.chemecador.secretaria.notes

interface NotesRepository {
    suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>>
    suspend fun createNote(ownerId: String, listId: String, title: String, content: String): Result<Note>
    suspend fun deleteNote(ownerId: String, listId: String, noteId: String): Result<Unit>
    suspend fun reorderNotes(ownerId: String, listId: String, noteIdsInOrder: List<String>): Result<Unit>
    suspend fun updateNote(
        ownerId: String,
        listId: String,
        noteId: String,
        title: String,
        content: String,
        completed: Boolean,
        color: Long,
    ): Result<Note>
}
