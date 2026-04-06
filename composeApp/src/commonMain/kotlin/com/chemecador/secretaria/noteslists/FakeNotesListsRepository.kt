package com.chemecador.secretaria.noteslists

import kotlin.time.Instant

class FakeNotesListsRepository(
    private val resultProvider: suspend () -> Result<List<NotesListSummary>> = { Result.success(seedLists) },
) : NotesListsRepository {

    override suspend fun getLists(): Result<List<NotesListSummary>> = resultProvider()

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
