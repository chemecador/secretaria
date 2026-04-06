package com.chemecador.secretaria.noteslists

data class NotesListsState(
    val isLoading: Boolean = false,
    val items: List<NotesListSummary> = emptyList(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val errorMessage: String? = null,
)
