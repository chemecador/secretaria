package com.chemecador.secretaria.noteslists

interface NotesListsRepository {
    suspend fun getLists(): Result<List<NotesListSummary>>
    suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary>
    suspend fun deleteList(listId: String): Result<Unit>
    suspend fun updateList(listId: String, name: String, ordered: Boolean): Result<NotesListSummary>
    suspend fun shareList(listId: String, friendUserId: String): Result<Unit>
}
