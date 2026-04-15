package com.chemecador.secretaria.noteslists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class NotesListsSortingTest {

    private val lists = listOf(
        NotesListSummary(
            id = "b",
            ownerId = "Alex",
            name = "Beta",
            creator = "Alex",
            createdAt = Instant.parse("2026-03-10T12:00:00Z"),
            isOrdered = false,
        ),
        NotesListSummary(
            id = "a",
            ownerId = "Alex",
            name = "Alpha",
            creator = "Alex",
            createdAt = Instant.parse("2026-01-10T12:00:00Z"),
            isOrdered = false,
        ),
        NotesListSummary(
            id = "c",
            ownerId = "Alex",
            name = "Charlie",
            creator = "Alex",
            createdAt = Instant.parse("2026-05-10T12:00:00Z"),
            isOrdered = false,
        ),
    )

    @Test
    fun sortByNameAsc_ordersAlphabetically() {
        assertEquals(
            listOf("Alpha", "Beta", "Charlie"),
            lists.sortedByOption(SortOption.NAME_ASC).map { it.name },
        )
    }

    @Test
    fun sortByNameDesc_ordersAlphabeticallyDescending() {
        assertEquals(
            listOf("Charlie", "Beta", "Alpha"),
            lists.sortedByOption(SortOption.NAME_DESC).map { it.name },
        )
    }

    @Test
    fun sortByDateAsc_ordersOldestFirst() {
        assertEquals(
            listOf("a", "b", "c"),
            lists.sortedByOption(SortOption.DATE_ASC).map { it.id },
        )
    }

    @Test
    fun sortByDateDesc_ordersNewestFirst() {
        assertEquals(
            listOf("c", "b", "a"),
            lists.sortedByOption(SortOption.DATE_DESC).map { it.id },
        )
    }
}
