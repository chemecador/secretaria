package com.chemecador.secretaria.notes

import com.chemecador.secretaria.noteslists.SortOption

/** Title and content both match, because a note is often findable only by what it says. */
internal fun List<Note>.filteredBySearch(query: String): List<Note> {
    val searchText = query.trim()
    if (searchText.isBlank()) return this

    return filter { note ->
        note.title.contains(searchText, ignoreCase = true) ||
            note.content.contains(searchText, ignoreCase = true)
    }
}

/**
 * Only meaningful for unordered lists. An ordered list keeps its manual `order`, which is why the
 * notes screen hides the sort control there.
 */
internal fun List<Note>.sortedByOption(option: SortOption): List<Note> =
    when (option) {
        SortOption.NAME_ASC -> sortedBy { note -> note.title.lowercase() }
        SortOption.NAME_DESC -> sortedByDescending { note -> note.title.lowercase() }
        SortOption.DATE_DESC -> sortedByDescending(Note::createdAt)
        SortOption.DATE_ASC,
        SortOption.CUSTOM,
        -> sortedBy(Note::createdAt)
    }
