package com.chemecador.secretaria.notes

import com.chemecador.secretaria.noteslists.SortOption
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class NotesSearchTest {

    private val pan = Note(
        id = "pan",
        title = "Pan",
        content = "Una barra",
        createdAt = Instant.parse("2026-03-01T10:00:00Z"),
        creator = "Alex",
    )
    private val leche = Note(
        id = "leche",
        title = "Leche",
        content = "",
        createdAt = Instant.parse("2026-03-02T10:00:00Z"),
        creator = "Alex",
    )
    private val agua = Note(
        id = "agua",
        title = "Agua",
        content = "Botella de 2L",
        createdAt = Instant.parse("2026-03-03T10:00:00Z"),
        creator = "Alex",
    )
    private val notes = listOf(pan, leche, agua)

    @Test
    fun filter_matchesTitleAndContentIgnoringCase() {
        assertEquals(listOf(pan), notes.filteredBySearch("PAN"))
        assertEquals(listOf(agua), notes.filteredBySearch("botella"))
        assertEquals(emptyList(), notes.filteredBySearch("queso"))
    }

    @Test
    fun filter_ignoresBlankQueriesAndSurroundingSpaces() {
        assertEquals(notes, notes.filteredBySearch(""))
        assertEquals(notes, notes.filteredBySearch("   "))
        assertEquals(listOf(leche), notes.filteredBySearch("  leche  "))
    }

    @Test
    fun sort_ordersByNameAndDate() {
        assertEquals(listOf(agua, leche, pan), notes.sortedByOption(SortOption.NAME_ASC))
        assertEquals(listOf(pan, leche, agua), notes.sortedByOption(SortOption.NAME_DESC))
        assertEquals(listOf(pan, leche, agua), notes.sortedByOption(SortOption.DATE_ASC))
        assertEquals(listOf(agua, leche, pan), notes.sortedByOption(SortOption.DATE_DESC))
    }

    @Test
    fun sort_treatsCustomAsOldestFirst() {
        assertEquals(
            notes.sortedByOption(SortOption.DATE_ASC),
            notes.sortedByOption(SortOption.CUSTOM),
        )
    }
}
