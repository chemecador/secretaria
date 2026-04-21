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
    private var knownFriendsByUserId: Map<String, FriendSummary> = emptyMap()

    fun load() {
        viewModelScope.launch {
            fetchLists()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            fetchLists(isRefresh = true)
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
            requireOwnedList(list)
                .fold(
                    onSuccess = { currentList ->
                        _state.update {
                            it.copy(
                                isLoadingShareableFriends = true,
                                shareableFriends = emptyList(),
                                shareErrorMessage = null,
                                shareFeedback = null,
                            )
                        }
                        friendsRepository.getFriends()
                            .onSuccess { friends ->
                                cacheFriends(friends)
                                _state.update {
                                    it.copy(
                                        isLoadingShareableFriends = false,
                                        shareableFriends = friends
                                            .filterNot { friend -> friend.userId in currentList.sharedWithUserIds }
                                            .sortedByName(),
                                        collaboratorsByListId = it.collaboratorsByListId.updated(
                                            listId = currentList.id,
                                            collaborators = buildCollaborators(currentList),
                                        ),
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isLoadingShareableFriends = false,
                                        shareableFriends = emptyList(),
                                        collaboratorsByListId = it.collaboratorsByListId.updated(
                                            listId = currentList.id,
                                            collaborators = buildCollaborators(
                                                currentList,
                                                friendsByUserId = emptyMap(),
                                            ),
                                        ),
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
            requireOwnedList(list)
                .fold(
                    onSuccess = { currentList ->
                        knownFriendsByUserId = knownFriendsByUserId + (friend.userId to friend)
                        _state.update {
                            it.copy(
                                isUpdatingSharing = true,
                                shareErrorMessage = null,
                                shareFeedback = null,
                            )
                        }
                        repository.shareList(currentList.id, friend.userId)
                            .onSuccess {
                                val updatedList = updateLocalList(currentList.id) { existingList ->
                                    existingList.withContributors(existingList.contributors + friend.userId)
                                } ?: currentList.withContributors(currentList.contributors + friend.userId)
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        items = allItems.sortedByOption(it.sortOption),
                                        shareableFriends = it.shareableFriends.filterNot { candidate ->
                                            candidate.userId == friend.userId
                                        },
                                        collaboratorsByListId = it.collaboratorsByListId.updated(
                                            listId = currentList.id,
                                            collaborators = buildCollaborators(updatedList),
                                        ),
                                        shareFeedback = ListSharingFeedback(
                                            friendName = friend.name,
                                            action = ListSharingAction.SHARED,
                                        ),
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
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

    fun unshareList(list: NotesListSummary, collaborator: ListCollaborator) {
        viewModelScope.launch {
            requireOwnedList(list)
                .fold(
                    onSuccess = { currentList ->
                        _state.update {
                            it.copy(
                                isUpdatingSharing = true,
                                shareErrorMessage = null,
                                shareFeedback = null,
                            )
                        }
                        repository.unshareList(currentList.id, collaborator.userId)
                            .onSuccess {
                                val updatedList = updateLocalList(currentList.id) { existingList ->
                                    existingList.withContributors(
                                        existingList.contributors.filterNot { contributorId ->
                                            contributorId == collaborator.userId
                                        },
                                    )
                                } ?: currentList.withContributors(
                                    currentList.contributors.filterNot { contributorId ->
                                        contributorId == collaborator.userId
                                    },
                                )
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        items = allItems.sortedByOption(it.sortOption),
                                        shareableFriends = it.shareableFriends.withFriend(
                                            knownFriendsByUserId[collaborator.userId],
                                        ),
                                        collaboratorsByListId = it.collaboratorsByListId.updated(
                                            listId = currentList.id,
                                            collaborators = buildCollaborators(updatedList),
                                        ),
                                        shareFeedback = ListSharingFeedback(
                                            friendName = collaborator.name,
                                            action = ListSharingAction.UNSHARED,
                                        ),
                                        shareErrorMessage = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
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
            requireOwnedList(list)
                .fold(
                    onSuccess = { currentList -> repository.deleteList(currentList.id) },
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
            requireOwnedList(list)
                .fold(
                    onSuccess = { currentList -> repository.updateList(currentList.id, name, ordered) },
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
                isUpdatingSharing = false,
                shareErrorMessage = null,
            )
        }
    }

    fun consumeShareFeedback() {
        _state.update { it.copy(shareFeedback = null) }
    }

    private suspend fun fetchLists(isRefresh: Boolean = false) {
        _state.update { currentState ->
            if (isRefresh) {
                currentState.copy(isRefreshing = true, errorMessage = null)
            } else {
                currentState.copy(isLoading = true, errorMessage = null)
            }
        }

        repository.getLists()
            .onSuccess { items ->
                allItems = items
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    items = items.sortedByOption(_state.value.sortOption),
                    errorMessage = null,
                    collaboratorsByListId = emptyMap(),
                )
                refreshCollaborators(items)
            }
            .onFailure { throwable ->
                if (isRefresh) {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        errorMessage = throwable.message,
                    )
                } else {
                    allItems = emptyList()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        items = emptyList(),
                        errorMessage = throwable.message,
                        collaboratorsByListId = emptyMap(),
                    )
                }
            }
    }

    private suspend fun refreshCollaborators(items: List<NotesListSummary>) {
        val currentUserId = authRepository.currentUserId ?: return
        val ownedSharedLists = items.filter { list ->
            list.ownerId == currentUserId && list.sharedWithUserIds.isNotEmpty()
        }
        if (ownedSharedLists.isEmpty()) {
            _state.update { it.copy(collaboratorsByListId = emptyMap()) }
            return
        }

        friendsRepository.getFriends()
            .onSuccess { friends ->
                cacheFriends(friends)
                _state.update { state ->
                    state.copy(
                        collaboratorsByListId = ownedSharedLists.associate { list ->
                            list.id to buildCollaborators(list)
                        }.filterValues { collaborators -> collaborators.isNotEmpty() },
                    )
                }
            }
            .onFailure {
                _state.update { it.copy(collaboratorsByListId = emptyMap()) }
            }
    }

    private fun requireOwnedList(list: NotesListSummary): Result<NotesListSummary> {
        val currentList = allItems.firstOrNull { item -> item.id == list.id } ?: list
        val currentUserId = authRepository.currentUserId
        return if (currentList.ownerId == currentUserId) {
            Result.success(currentList)
        } else {
            Result.failure(IllegalStateException(OWNERSHIP_ERROR_MESSAGE))
        }
    }

    private fun cacheFriends(friends: List<FriendSummary>) {
        knownFriendsByUserId = friends.associateBy { friend -> friend.userId }
    }

    private fun buildCollaborators(
        list: NotesListSummary,
        friendsByUserId: Map<String, FriendSummary> = knownFriendsByUserId,
    ): List<ListCollaborator> = list.sharedWithUserIds
        .map { userId ->
            val friend = friendsByUserId[userId]
            ListCollaborator(
                userId = userId,
                name = friend?.name ?: userId,
                isResolvedName = friend != null,
            )
        }
        .sortedBy { collaborator -> collaborator.name.lowercase() }

    private fun updateLocalList(
        listId: String,
        update: (NotesListSummary) -> NotesListSummary,
    ): NotesListSummary? {
        var updatedList: NotesListSummary? = null
        allItems = allItems.map { currentList ->
            if (currentList.id == listId) {
                update(currentList).also { candidate -> updatedList = candidate }
            } else {
                currentList
            }
        }
        return updatedList
    }

    private fun NotesListSummary.withContributors(updatedContributors: List<String>): NotesListSummary {
        val contributors = updatedContributors.distinct()
        val currentUserId = authRepository.currentUserId
        return copy(
            contributors = contributors,
            isShared = if (currentUserId == null) {
                contributors.size > 1
            } else {
                ownerId != currentUserId || contributors.size > 1
            },
        )
    }

    private companion object {
        const val OWNERSHIP_ERROR_MESSAGE = "Solo el propietario puede modificar esta lista"
    }
}

private fun List<FriendSummary>.sortedByName(): List<FriendSummary> =
    distinctBy { friend -> friend.userId }
        .sortedBy { friend -> friend.name.lowercase() }

private fun List<FriendSummary>.withFriend(friend: FriendSummary?): List<FriendSummary> =
    if (friend == null) {
        this
    } else {
        (this + friend).sortedByName()
    }

private fun Map<String, List<ListCollaborator>>.updated(
    listId: String,
    collaborators: List<ListCollaborator>,
): Map<String, List<ListCollaborator>> =
    if (collaborators.isEmpty()) {
        this - listId
    } else {
        this + (listId to collaborators)
    }
