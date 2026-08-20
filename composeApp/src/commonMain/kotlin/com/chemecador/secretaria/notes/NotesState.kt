package com.chemecador.secretaria.notes

import com.chemecador.secretaria.noteslists.SortOption

data class NotesState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    /** Always the full list: reordering and the photo sync both address notes by id. */
    val notes: List<Note> = emptyList(),
    val searchQuery: String = "",
    /** Oldest first keeps unordered lists looking exactly as they did before sorting existed. */
    val sortOption: SortOption = SortOption.DATE_ASC,
    val errorMessage: String? = null,
)
