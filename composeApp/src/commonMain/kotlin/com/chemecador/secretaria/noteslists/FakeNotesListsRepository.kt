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
            name = name,
            creator = "Alex",
            createdAt = Clock.System.now(),
            isOrdered = ordered,
        )
        lists.add(newList)
        return Result.success(newList)
    }

    companion object {
        val seedLists = listOf(
            NotesListSummary(
                id = "shopping",
                name = "Compra semanal",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-28T12:00:00Z"),
                isOrdered = false,
            ),
            NotesListSummary(
                id = "work",
                name = "Trabajo",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-22T12:00:00Z"),
                isOrdered = true,
            ),
            NotesListSummary(
                id = "travel",
                name = "Viaje a Japón",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-30T12:00:00Z"),
                isOrdered = false,
            ),
            NotesListSummary(
                id = "books",
                name = "Libros pendientes",
                creator = "Marta",
                createdAt = Instant.parse("2026-02-18T12:00:00Z"),
                isOrdered = true,
            ),
        )
    }
}
