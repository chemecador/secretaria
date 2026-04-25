package com.chemecador.secretaria.noteslists

import kotlin.time.Clock
import kotlin.time.Instant

class FakeNotesListsRepository(
    private val resultProvider: suspend () -> Result<List<NotesListSummary>> = { Result.success(seedLists) },
) : NotesListsRepository {

    private val lists = mutableListOf<NotesListSummary>()
    private var seeded = false

    override suspend fun getLists(): Result<List<NotesListSummary>> {
        if (!seeded) {
            resultProvider().onSuccess { lists.addAll(it) }
            seeded = true
        }
        return Result.success(lists.toList())
    }

    override suspend fun createList(
        name: String,
        ordered: Boolean,
        isGroup: Boolean,
    ): Result<NotesListSummary> {
        val newList = NotesListSummary(
            id = "list-${lists.size + 1}",
            ownerId = "Alex",
            name = name,
            creator = "Alex",
            createdAt = Clock.System.now(),
            isOrdered = ordered,
            isGroup = isGroup,
            groupId = null,
            groupOwnerId = null,
            groupOrder = 0,
            isShared = false,
            contributors = listOf("Alex"),
            directContributors = listOf("Alex"),
            inheritedGroupContributors = emptyList(),
            archivedBy = emptyList(),
            archivedAtBy = emptyMap(),
        )
        lists.add(newList)
        return Result.success(newList)
    }

    override suspend fun deleteList(listId: String): Result<Unit> {
        val list = lists.firstOrNull { it.id == listId }
        if (list?.isGroup == true) {
            val directContributors = list.directContributors
            for (index in lists.indices) {
                if (lists[index].groupKey == list.key) {
                    lists[index] = lists[index].withGroup(null, groupOrder = 0)
                        .withInheritedGroupContributors(
                            lists[index].inheritedGroupContributors.filterNot { contributorId ->
                                contributorId in directContributors
                            },
                        )
                }
            }
        }
        lists.removeAll { it.id == listId }
        return Result.success(Unit)
    }

    override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> {
        val index = lists.indexOfFirst { it.id == listId }
        if (index != -1) {
            val updatedDirectContributors = (lists[index].directContributors + friendUserId).distinct()
            lists[index] = lists[index].withDirectContributors(updatedDirectContributors)
            if (lists[index].isGroup) {
                lists.propagateGroupContributor(lists[index], friendUserId, added = true)
            }
        }
        return Result.success(Unit)
    }

    override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> {
        val index = lists.indexOfFirst { it.id == listId }
        if (index != -1) {
            val updatedDirectContributors = lists[index].directContributors.filterNot { contributorId ->
                contributorId == friendUserId
            }
            lists[index] = lists[index].withDirectContributors(updatedDirectContributors)
            if (lists[index].isGroup) {
                lists.propagateGroupContributor(lists[index], friendUserId, added = false)
            }
        }
        return Result.success(Unit)
    }

    override suspend fun updateList(listId: String, name: String, ordered: Boolean): Result<NotesListSummary> {
        val index = lists.indexOfFirst { it.id == listId }
        if (index == -1) return Result.failure(IllegalStateException("List not found"))
        val updated = lists[index].copy(name = name, isOrdered = ordered)
        lists[index] = updated
        return Result.success(updated)
    }

    override suspend fun setListGroup(
        listOwnerId: String,
        listId: String,
        groupOwnerId: String?,
        groupId: String?,
    ): Result<Unit> {
        if ((groupId == null) != (groupOwnerId == null)) {
            return Result.failure(IllegalStateException("Group owner required"))
        }
        val index = lists.indexOfFirst { it.ownerId == listOwnerId && it.id == listId }
        if (index == -1) return Result.failure(IllegalStateException("List not found"))
        val group = if (groupId == null || groupOwnerId == null) {
            null
        } else {
            lists.firstOrNull { it.ownerId == groupOwnerId && it.id == groupId && it.isGroup }
                ?: return Result.failure(IllegalStateException("Group not found"))
        }
        val groupOrder = if (group == null) {
            0
        } else {
            lists.count { it.groupKey == group.key && it.key != NotesListKey(listOwnerId, listId) }
        }
        lists[index] = lists[index].withGroup(group, groupOrder)
        return Result.success(Unit)
    }

    override suspend fun reorderGroupedLists(
        groupOwnerId: String,
        groupId: String,
        listKeysInOrder: List<NotesListKey>,
    ): Result<Unit> {
        val groupKey = NotesListKey(groupOwnerId, groupId)
        val reordered = lists
            .filter { it.groupKey == groupKey }
            .applyGroupOrder(listKeysInOrder)
            ?: return Result.failure(IllegalStateException("Invalid group order"))
        reordered.forEach { reorderedList ->
            val index = lists.indexOfFirst { it.key == reorderedList.key }
            if (index != -1) {
                lists[index] = reorderedList
            }
        }
        return Result.success(Unit)
    }

    override suspend fun setListArchived(
        ownerId: String,
        listId: String,
        archived: Boolean,
    ): Result<Unit> {
        val index = lists.indexOfFirst { it.ownerId == ownerId && it.id == listId }
        if (index == -1) return Result.failure(IllegalStateException("List not found"))
        val archivedBy = if (archived) {
            (lists[index].archivedBy + "Alex").distinct()
        } else {
            lists[index].archivedBy.filterNot { userId -> userId == "Alex" }
        }
        val archivedAtBy = if (archived) {
            lists[index].archivedAtBy + ("Alex" to Clock.System.now())
        } else {
            lists[index].archivedAtBy - "Alex"
        }
        lists[index] = lists[index].copy(
            archivedBy = archivedBy,
            archivedAtBy = archivedAtBy,
        )
        return Result.success(Unit)
    }

    companion object {
        val seedLists = listOf(
            NotesListSummary(
                id = "shopping",
                ownerId = "Alex",
                name = "Compra semanal",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-28T12:00:00Z"),
                isOrdered = false,
                isShared = false,
                contributors = listOf("Alex"),
                directContributors = listOf("Alex"),
                inheritedGroupContributors = emptyList(),
                archivedBy = emptyList(),
                archivedAtBy = emptyMap(),
            ),
            NotesListSummary(
                id = "work",
                ownerId = "Alex",
                name = "Trabajo",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-22T12:00:00Z"),
                isOrdered = true,
                isShared = false,
                contributors = listOf("Alex"),
                directContributors = listOf("Alex"),
                inheritedGroupContributors = emptyList(),
                archivedBy = emptyList(),
                archivedAtBy = emptyMap(),
            ),
            NotesListSummary(
                id = "travel",
                ownerId = "Alex",
                name = "Viaje a Japón",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-30T12:00:00Z"),
                isOrdered = false,
                isShared = false,
                contributors = listOf("Alex"),
                directContributors = listOf("Alex"),
                inheritedGroupContributors = emptyList(),
                archivedBy = emptyList(),
                archivedAtBy = emptyMap(),
            ),
            NotesListSummary(
                id = "books",
                ownerId = "Marta",
                name = "Libros pendientes",
                creator = "Marta",
                createdAt = Instant.parse("2026-02-18T12:00:00Z"),
                isOrdered = true,
                isShared = true,
                contributors = listOf("Marta", "Alex"),
                directContributors = listOf("Marta", "Alex"),
                inheritedGroupContributors = emptyList(),
                archivedBy = emptyList(),
                archivedAtBy = emptyMap(),
            ),
        )
    }
}

private fun NotesListSummary.withDirectContributors(
    updatedDirectContributors: List<String>,
): NotesListSummary {
    val directContributors = updatedDirectContributors.distinct()
    val contributors = effectiveContributors(ownerId, directContributors, inheritedGroupContributors)
    return copy(
        contributors = contributors,
        directContributors = directContributors,
        isShared = contributors.size > 1,
    )
}

private fun NotesListSummary.withInheritedGroupContributors(
    updatedInheritedGroupContributors: List<String>,
): NotesListSummary {
    val inheritedGroupContributors = updatedInheritedGroupContributors.distinct()
    val contributors = effectiveContributors(ownerId, directContributors, inheritedGroupContributors)
    return copy(
        contributors = contributors,
        inheritedGroupContributors = inheritedGroupContributors,
        isShared = contributors.size > 1,
    )
}

private fun NotesListSummary.withGroup(
    group: NotesListSummary?,
    groupOrder: Int,
): NotesListSummary {
    val inheritedContributors = group?.directContributors.orEmpty()
    return copy(
        groupId = group?.id,
        groupOwnerId = group?.ownerId,
        groupOrder = if (group == null) 0 else groupOrder,
    ).withInheritedGroupContributors(inheritedContributors)
}

private fun MutableList<NotesListSummary>.propagateGroupContributor(
    group: NotesListSummary,
    friendUserId: String,
    added: Boolean,
) {
    for (index in indices) {
        val item = this[index]
        if (item.groupKey == group.key) {
            val inheritedContributors = if (added) {
                item.inheritedGroupContributors + friendUserId
            } else {
                item.inheritedGroupContributors.filterNot { contributorId ->
                    contributorId == friendUserId
                }
            }
            this[index] = item.withInheritedGroupContributors(inheritedContributors)
        }
    }
}
