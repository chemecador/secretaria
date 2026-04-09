package com.chemecador.secretaria.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: NotesRepository,
    private val listId: String,
) : ViewModel() {
    private val _state = MutableStateFlow(NotesState())
    val state: StateFlow<NotesState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            fetchNotes()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            fetchNotes()
        }
    }

    fun createNote(title: String, content: String) {
        viewModelScope.launch {
            repository.createNote(listId, title, content)
                .onSuccess { fetchNotes() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(listId, noteId)
                .onSuccess { fetchNotes() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
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
