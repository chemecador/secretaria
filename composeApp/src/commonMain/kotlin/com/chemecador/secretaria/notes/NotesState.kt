package com.chemecador.secretaria.notes

data class NotesState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val notes: List<Note> = emptyList(),
    val errorMessage: String? = null,
)
