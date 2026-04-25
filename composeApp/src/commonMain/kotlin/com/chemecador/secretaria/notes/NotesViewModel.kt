package com.chemecador.secretaria.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chemecador.secretaria.settings.AccountSettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesViewModel(
    private val repository: NotesRepository,
    private val accountSettingsRepository: AccountSettingsRepository,
    private val ownerId: String,
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
            fetchNotes(isRefresh = true)
        }
    }

    fun createNote(title: String, content: String) {
        viewModelScope.launch {
            val color = accountSettingsRepository.getDefaultNoteColor()
                .getOrDefault(DEFAULT_NOTE_COLOR)
            repository.createNote(ownerId, listId, title, content, color)
                .onSuccess { fetchNotes() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun updateNote(
        noteId: String,
        title: String,
        content: String,
        completed: Boolean,
        color: Long,
    ) {
        viewModelScope.launch {
            repository.updateNote(
                ownerId = ownerId,
                listId = listId,
                noteId = noteId,
                title = title,
                content = content,
                completed = completed,
                color = color,
            )
                .onSuccess { fetchNotes() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(ownerId, listId, noteId)
                .onSuccess { fetchNotes() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun reorderNotes(noteIdsInOrder: List<String>) {
        val currentNotes = _state.value.notes.sortedBy(Note::order)
        val reorderedNotes = currentNotes.applyNoteOrder(noteIdsInOrder) ?: return

        if (currentNotes.map(Note::id) == reorderedNotes.map(Note::id)) {
            return
        }

        _state.update { it.copy(notes = reorderedNotes, errorMessage = null) }

        viewModelScope.launch {
            repository.reorderNotes(ownerId, listId, noteIdsInOrder)
                .onFailure { throwable ->
                    _state.update { it.copy(notes = currentNotes, errorMessage = throwable.message) }
                }
        }
    }

    private suspend fun fetchNotes(isRefresh: Boolean = false) {
        _state.update { currentState ->
            if (isRefresh) {
                currentState.copy(isRefreshing = true, errorMessage = null)
            } else {
                currentState.copy(isLoading = true, errorMessage = null)
            }
        }

        repository.getNotesForList(ownerId, listId)
            .onSuccess { items ->
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    notes = items,
                    errorMessage = null,
                )
            }
            .onFailure { throwable ->
                if (isRefresh) {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        errorMessage = throwable.message,
                    )
                } else {
                    _state.value = _state.value.copy(
                        isLoading = false,
                        notes = emptyList(),
                        errorMessage = throwable.message,
                    )
                }
            }
    }
}
