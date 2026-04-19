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

    override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> {
        val newList = NotesListSummary(
            id = "list-${lists.size + 1}",
            ownerId = "Alex",
            name = name,
            creator = "Alex",
            createdAt = Clock.System.now(),
            isOrdered = ordered,
            isShared = false,
            contributors = listOf("Alex"),
        )
        lists.add(newList)
        return Result.success(newList)
    }

    override suspend fun deleteList(listId: String): Result<Unit> {
        lists.removeAll { it.id == listId }
        return Result.success(Unit)
    }

    override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> {
        val index = lists.indexOfFirst { it.id == listId }
        if (index != -1) {
            val updatedContributors = (lists[index].contributors + friendUserId).distinct()
            lists[index] = lists[index].copy(
                isShared = updatedContributors.size > 1,
                contributors = updatedContributors,
            )
        }
        return Result.success(Unit)
    }

    override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> {
        val index = lists.indexOfFirst { it.id == listId }
        if (index != -1) {
            val updatedContributors = lists[index].contributors.filterNot { contributorId ->
                contributorId == friendUserId
            }
            lists[index] = lists[index].copy(
                isShared = updatedContributors.distinct().size > 1,
                contributors = updatedContributors,
            )
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
            ),
        )
    }
}
