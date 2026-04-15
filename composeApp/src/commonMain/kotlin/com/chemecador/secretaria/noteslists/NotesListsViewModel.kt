package com.chemecador.secretaria.noteslists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotesListsViewModel(
    private val repository: NotesListsRepository,
    private val authRepository: AuthRepository,
    private val friendsRepository: FriendsRepository,
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

    fun createList(name: String, ordered: Boolean) {
        viewModelScope.launch {
            repository.createList(name, ordered)
                .onSuccess { fetchLists() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun loadShareableFriends(list: NotesListSummary) {
        viewModelScope.launch {
            requireOwnership(list)
                .fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isLoadingShareableFriends = true,
                                shareableFriends = emptyList(),
                                shareErrorMessage = null,
                                lastSharedFriendName = null,
                            )
                        }
                        friendsRepository.getFriends()
                            .onSuccess { friends ->
                                _state.update {
                                    it.copy(
                                        isLoadingShareableFriends = false,
                                        shareableFriends = friends.sortedBy { friend -> friend.name.lowercase() },
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isLoadingShareableFriends = false,
                                        shareableFriends = emptyList(),
                                        shareErrorMessage = throwable.message,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.copy(shareErrorMessage = throwable.message) }
                    },
                )
        }
    }

    fun shareList(list: NotesListSummary, friend: FriendSummary) {
        viewModelScope.launch {
            requireOwnership(list)
                .fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isSharingList = true,
                                shareErrorMessage = null,
                                lastSharedFriendName = null,
                            )
                        }
                        repository.shareList(list.id, friend.userId)
                            .onSuccess {
                                val updatedItems = allItems.map { currentList ->
                                    if (currentList.id == list.id) {
                                        currentList.copy(isShared = true)
                                    } else {
                                        currentList
                                    }
                                }
                                allItems = updatedItems
                                _state.update {
                                    it.copy(
                                        isSharingList = false,
                                        items = updatedItems.sortedByOption(it.sortOption),
                                        shareableFriends = it.shareableFriends.filterNot { candidate ->
                                            candidate.userId == friend.userId
                                        },
                                        lastSharedFriendName = friend.name,
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isSharingList = false,
                                        shareErrorMessage = throwable.message,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.copy(shareErrorMessage = throwable.message) }
                    },
                )
        }
    }

    fun deleteList(list: NotesListSummary) {
        viewModelScope.launch {
            requireOwnership(list)
                .fold(
                    onSuccess = { repository.deleteList(list.id) },
                    onFailure = { Result.failure(it) },
                )
                .onSuccess { fetchLists() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun updateList(list: NotesListSummary, name: String, ordered: Boolean) {
        viewModelScope.launch {
            requireOwnership(list)
                .fold(
                    onSuccess = { repository.updateList(list.id, name, ordered) },
                    onFailure = { Result.failure(it) },
                )
                .onSuccess { fetchLists() }
                .onFailure { throwable ->
                    _state.update { it.copy(errorMessage = throwable.message) }
                }
        }
    }

    fun clearShareState() {
        _state.update {
            it.copy(
                shareableFriends = emptyList(),
                isLoadingShareableFriends = false,
                isSharingList = false,
                shareErrorMessage = null,
                lastSharedFriendName = null,
            )
        }
    }

    fun consumeShareSuccess() {
        _state.update { it.copy(lastSharedFriendName = null) }
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

    private fun requireOwnership(list: NotesListSummary): Result<Unit> {
        val currentUserId = authRepository.currentUserId
        return if (list.ownerId == currentUserId) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException(OWNERSHIP_ERROR_MESSAGE))
        }
    }

    private companion object {
        const val OWNERSHIP_ERROR_MESSAGE = "Solo el propietario puede modificar esta lista"
    }
}
