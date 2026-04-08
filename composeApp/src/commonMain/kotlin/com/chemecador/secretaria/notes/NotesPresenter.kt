package com.chemecador.secretaria.notes

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class NotesPresenter(
    private val repository: NotesRepository,
    private val listId: String,
) {
    private val _state = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = _state.asStateFlow()

    suspend fun load() {
        fetchNotes()
    }

    suspend fun refresh() {
        fetchNotes()
    }

    private suspend fun fetchNotes() {
        _state.update { currentState ->
            currentState.copy(isLoading = true, errorMessage = null)
        }

        repository.getNotesForList(listId)
            .onSuccess { items ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    notes = items,
                    errorMessage = null,
                )
            }
            .onFailure { throwable ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    notes = emptyList(),
                    errorMessage = throwable.message,
                )
            }
    }
}
