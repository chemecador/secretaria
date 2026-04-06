package com.chemecador.secretaria.noteslists

fun List<NotesListSummary>.sortedByOption(sortOption: SortOption): List<NotesListSummary> {
    return when (sortOption) {
        SortOption.NAME_ASC -> sortedBy { it.name.lowercase() }
        SortOption.NAME_DESC -> sortedByDescending { it.name.lowercase() }
        SortOption.DATE_ASC -> sortedBy { it.createdAt }
        SortOption.DATE_DESC -> sortedByDescending { it.createdAt }
        SortOption.CUSTOM -> sortedByDescending { it.createdAt }
    }
}
