package com.chemecador.secretaria.noteslists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesListsViewModel(
    private val repository: NotesListsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotesListsState())
    val state: StateFlow<NotesListsState> = _state.asStateFlow()

    private var allItems: List<NotesListSummary> = emptyList()

    fun load() {
        viewModelScope.launch {
            fetchLists()
        }
    }

    fun setSort(sortOption: SortOption) {
        _state.update { currentState ->
            currentState.copy(
                sortOption = sortOption,
                items = allItems.sortedByOption(sortOption),
            )
        }
    }

    private suspend fun fetchLists() {
        _state.update { currentState ->
            currentState.copy(isLoading = true, errorMessage = null)
        }

        repository.getLists()
            .onSuccess { items ->
                allItems = items
                _state.value = _state.value.copy(
                    isLoading = false,
                    items = items.sortedByOption(_state.value.sortOption),
                    errorMessage = null,
                )
            }
            .onFailure { throwable ->
                allItems = emptyList()
                _state.value = _state.value.copy(
                    isLoading = false,
                    items = emptyList(),
                    errorMessage = throwable.message,
                )
            }
    }
}
