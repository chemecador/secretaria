package com.chemecador.secretaria.noteslists

interface NotesListsRepository {
    suspend fun getLists(): Result<List<NotesListSummary>>
    suspend fun createList(name: String, ordered: Boolean, isGroup: Boolean): Result<NotesListSummary>
    suspend fun deleteList(listId: String): Result<Unit>
    suspend fun updateList(listId: String, name: String, ordered: Boolean): Result<NotesListSummary>
    suspend fun shareList(listId: String, friendUserId: String): Result<Unit>
    suspend fun unshareList(listId: String, friendUserId: String): Result<Unit>
    suspend fun setListGroup(
        listOwnerId: String,
        listId: String,
        groupOwnerId: String?,
        groupId: String?,
    ): Result<Unit>
    suspend fun reorderGroupedLists(
        groupOwnerId: String,
        groupId: String,
        listKeysInOrder: List<NotesListKey>,
    ): Result<Unit>
    suspend fun setListArchived(ownerId: String, listId: String, archived: Boolean): Result<Unit>
}
