package com.chemecador.secretaria.noteslists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Clock

class NotesListsViewModel(
    private val repository: NotesListsRepository,
    private val authRepository: AuthRepository,
    private val friendsRepository: FriendsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(NotesListsState())
    val state: StateFlow<NotesListsState> = _state.asStateFlow()

    private var allItems: List<NotesListSummary> = emptyList()
    private var knownFriendsByUserId: Map<String, FriendSummary> = emptyMap()
    private var searchJob: Job? = null

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
                items = allItems.filteredBySearch(currentState.searchQuery).sortedByOption(sortOption),
            )
        }
    }

    fun setSearchQuery(query: String) {
        if (query == _state.value.searchQuery) return

        _state.update { it.copy(searchQuery = query) }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE_MS)
            applyCurrentItems()
        }
    }

    fun createList(name: String, ordered: Boolean, isGroup: Boolean) {
        viewModelScope.launch {
            repository.createList(name, ordered, isGroup)
                .onSuccess { fetchLists() }
                .onFailure { throwable ->
                    _state.update { it.withError(throwable) }
                }
        }
    }

    fun createGroupAndAddList(list: NotesListSummary, groupName: String, ordered: Boolean) {
        viewModelScope.launch {
            requireAccessibleList(list)
                .fold(
                    onSuccess = { currentList ->
                        if (currentList.isGroup) {
                            Result.failure(NotesListsValidationException(NotesListsError.GROUP_INSIDE_GROUP))
                        } else {
                            repository.createList(groupName, ordered, isGroup = true)
                                .onSuccess { group ->
                                    repository.setListGroup(
                                        listOwnerId = currentList.ownerId,
                                        listId = currentList.id,
                                        groupOwnerId = group.ownerId,
                                        groupId = group.id,
                                    )
                                        .onSuccess { fetchLists() }
                                        .onFailure { throwable ->
                                            fetchLists()
                                            _state.update { it.withError(throwable) }
                                        }
                                }
                        }
                    },
                    onFailure = { Result.failure(it) },
                )
                .onFailure { throwable ->
                    _state.update { it.withError(throwable) }
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
                                shareValidationError = null,
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
                                            .filterNot { friend -> friend.userId in currentList.directSharedWithUserIds }
                                            .sortedByName(),
                                        collaboratorsByListId = it.collaboratorsByListId.updated(
                                            listId = currentList.id,
                                            collaborators = buildCollaborators(currentList),
                                        ),
                                        shareErrorMessage = null,
                                        shareValidationError = null,
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
                                        shareValidationError = null,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.withShareError(throwable) }
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
                                shareValidationError = null,
                                shareFeedback = null,
                            )
                        }
                        repository.shareList(currentList.id, friend.userId)
                            .onSuccess {
                                val updatedList = updateLocalList(currentList.ownerId, currentList.id) { existingList ->
                                    existingList.withDirectContributors(existingList.directContributors + friend.userId)
                                } ?: currentList.withDirectContributors(currentList.directContributors + friend.userId)
                                if (updatedList.isGroup) {
                                    propagateGroupContributor(updatedList, friend.userId, added = true)
                                }
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        items = allItems.filteredBySearch(it.searchQuery).sortedByOption(it.sortOption),
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
                                        shareValidationError = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        shareErrorMessage = throwable.message,
                                        shareValidationError = null,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.withShareError(throwable) }
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
                                shareValidationError = null,
                                shareFeedback = null,
                            )
                        }
                        repository.unshareList(currentList.id, collaborator.userId)
                            .onSuccess {
                                val updatedList = updateLocalList(currentList.ownerId, currentList.id) { existingList ->
                                    existingList.withDirectContributors(
                                        existingList.directContributors.filterNot { contributorId ->
                                            contributorId == collaborator.userId
                                        },
                                    )
                                } ?: currentList.withDirectContributors(
                                    currentList.directContributors.filterNot { contributorId ->
                                        contributorId == collaborator.userId
                                    },
                                )
                                if (updatedList.isGroup) {
                                    propagateGroupContributor(updatedList, collaborator.userId, added = false)
                                }
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        items = allItems.filteredBySearch(it.searchQuery).sortedByOption(it.sortOption),
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
                                        shareValidationError = null,
                                    )
                                }
                            }
                            .onFailure { throwable ->
                                _state.update {
                                    it.copy(
                                        isUpdatingSharing = false,
                                        shareErrorMessage = throwable.message,
                                        shareValidationError = null,
                                    )
                                }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.withShareError(throwable) }
                    },
                )
        }
    }

    fun setListArchived(list: NotesListSummary, archived: Boolean) {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUserId
            val action = if (archived) {
                ListArchiveAction.ARCHIVED
            } else {
                ListArchiveAction.UNARCHIVED
            }

            if (currentUserId == null) {
                _state.update {
                    it.copy(archiveFeedback = ListArchiveFeedback(action = action, isSuccess = false))
                }
                return@launch
            }

            val currentList = findCurrentList(list)
            repository.setListArchived(currentList.ownerId, currentList.id, archived)
                .onSuccess {
                    updateLocalList(currentList.ownerId, currentList.id) { existingList ->
                        existingList.withArchivedBy(currentUserId, archived)
                    }
                    _state.update {
                        it.copy(
                            items = allItems.filteredBySearch(it.searchQuery).sortedByOption(it.sortOption),
                            archiveFeedback = ListArchiveFeedback(action = action, isSuccess = true),
                        )
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(archiveFeedback = ListArchiveFeedback(action = action, isSuccess = false))
                    }
                }
        }
    }

    fun leaveSharedList(list: NotesListSummary) {
        viewModelScope.launch {
            val currentUserId = authRepository.currentUserId
            val currentList = findCurrentList(list)
            if (
                currentUserId == null ||
                currentList.ownerId == currentUserId ||
                currentUserId !in currentList.directContributors
            ) {
                _state.update {
                    it.copy(leaveSharedListFeedback = ListLeaveSharedFeedback(isSuccess = false))
                }
                return@launch
            }

            repository.leaveSharedList(currentList.ownerId, currentList.id)
                .onSuccess {
                    fetchLists()
                    _state.update {
                        it.copy(leaveSharedListFeedback = ListLeaveSharedFeedback(isSuccess = true))
                    }
                }
                .onFailure {
                    _state.update {
                        it.copy(leaveSharedListFeedback = ListLeaveSharedFeedback(isSuccess = false))
                    }
                }
        }
    }

    fun setListGroup(list: NotesListSummary, group: NotesListSummary?) {
        viewModelScope.launch {
            requireAccessibleList(list)
                .fold(
                    onSuccess = { currentList ->
                        if (currentList.isGroup) {
                            Result.failure(NotesListsValidationException(NotesListsError.GROUP_INSIDE_GROUP))
                        } else {
                            val currentGroup = group?.let(::findCurrentList)
                            val currentUserId = authRepository.currentUserId
                            val existingGroupOwnerId = currentList.groupKey?.ownerId
                            when {
                                currentGroup == null && existingGroupOwnerId != null &&
                                    existingGroupOwnerId != currentUserId -> {
                                    Result.failure(NotesListsValidationException(NotesListsError.NOT_GROUP_OWNER))
                                }
                                currentGroup == null -> Result.success(currentList to null)
                                !currentGroup.isGroup -> {
                                    Result.failure(NotesListsValidationException(NotesListsError.TARGET_IS_NOT_A_GROUP))
                                }
                                currentGroup.ownerId != currentUserId -> {
                                    Result.failure(NotesListsValidationException(NotesListsError.NOT_GROUP_OWNER))
                                }
                                existingGroupOwnerId != null && existingGroupOwnerId != currentUserId -> {
                                    Result.failure(NotesListsValidationException(NotesListsError.NOT_GROUP_OWNER))
                                }
                                else -> Result.success(currentList to currentGroup)
                            }
                        }
                    },
                    onFailure = { Result.failure(it) },
                )
                .fold(
                    onSuccess = { (currentList, currentGroup) ->
                        repository.setListGroup(
                            listOwnerId = currentList.ownerId,
                            listId = currentList.id,
                            groupOwnerId = currentGroup?.ownerId,
                            groupId = currentGroup?.id,
                        )
                            .onSuccess {
                                val nextOrder = currentGroup?.let { groupList ->
                                    allItems.count { item ->
                                        item.groupKey == groupList.key &&
                                            item.key != currentList.key
                                    }
                                } ?: 0
                                updateLocalList(currentList.ownerId, currentList.id) { existingList ->
                                    existingList.withGroup(currentGroup, nextOrder)
                                }
                                publishCurrentItems()
                            }
                            .onFailure { throwable ->
                                _state.update { it.withError(throwable) }
                            }
                    },
                    onFailure = { throwable ->
                        _state.update { it.withError(throwable) }
                    },
                )
        }
    }

    fun reorderGroupedLists(group: NotesListSummary, listKeysInOrder: List<NotesListKey>) {
        val currentGroup = findCurrentList(group)
        if (!currentGroup.isGroup || currentGroup.ownerId != authRepository.currentUserId) {
            _state.update { it.copy(validationError = NotesListsError.NOT_GROUP_OWNER, errorMessage = null) }
            return
        }

        val currentChildren = allItems
            .filter { item -> item.groupKey == currentGroup.key }
            .sortedBy { item -> item.groupOrder }
        val reorderedChildren = currentChildren.applyGroupOrder(listKeysInOrder) ?: return
        if (currentChildren.map(NotesListSummary::key) == reorderedChildren.map(NotesListSummary::key)) {
            return
        }

        replaceLocalLists(reorderedChildren)
        publishCurrentItems()

        viewModelScope.launch {
            repository.reorderGroupedLists(currentGroup.ownerId, currentGroup.id, listKeysInOrder)
                .onFailure { throwable ->
                    replaceLocalLists(currentChildren)
                    _state.update {
                        it
                            .copy(items = allItems.filteredBySearch(it.searchQuery).sortedByOption(it.sortOption))
                            .withError(throwable)
                    }
                }
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
                    _state.update { it.withError(throwable) }
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
                    _state.update { it.withError(throwable) }
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
                shareValidationError = null,
            )
        }
    }

    fun consumeShareFeedback() {
        _state.update { it.copy(shareFeedback = null) }
    }

    fun consumeArchiveFeedback() {
        _state.update { it.copy(archiveFeedback = null) }
    }

    fun consumeLeaveSharedListFeedback() {
        _state.update { it.copy(leaveSharedListFeedback = null) }
    }

    private suspend fun fetchLists(isRefresh: Boolean = false) {
        _state.update { currentState ->
            if (isRefresh) {
                currentState.copy(isRefreshing = true, errorMessage = null, validationError = null)
            } else {
                currentState.copy(isLoading = true, errorMessage = null, validationError = null)
            }
        }

        repository.getLists()
            .onSuccess { items ->
                allItems = items
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    items = items
                        .filteredBySearch(_state.value.searchQuery)
                        .sortedByOption(_state.value.sortOption),
                    errorMessage = null,
                    validationError = null,
                    collaboratorsByListId = emptyMap(),
                )
                refreshCollaborators(items)
            }
            .onFailure { throwable ->
                if (isRefresh) {
                    _state.value = _state.value.copy(
                        isRefreshing = false,
                        errorMessage = throwable.message,
                        validationError = null,
                    )
                } else {
                    allItems = emptyList()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        items = emptyList(),
                        errorMessage = throwable.message,
                        validationError = null,
                        collaboratorsByListId = emptyMap(),
                    )
                }
            }
    }

    private suspend fun refreshCollaborators(items: List<NotesListSummary>) {
        val currentUserId = authRepository.currentUserId ?: return
        val ownedSharedLists = items.filter { list ->
            list.ownerId == currentUserId && list.directSharedWithUserIds.isNotEmpty()
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
        val currentList = findCurrentList(list)
        val currentUserId = authRepository.currentUserId
        return if (currentList.ownerId == currentUserId) {
            Result.success(currentList)
        } else {
            Result.failure(NotesListsValidationException(NotesListsError.NOT_OWNER))
        }
    }

    private fun findCurrentList(list: NotesListSummary): NotesListSummary =
        allItems.firstOrNull { item -> item.id == list.id && item.ownerId == list.ownerId } ?: list

    private fun cacheFriends(friends: List<FriendSummary>) {
        knownFriendsByUserId = friends.associateBy { friend -> friend.userId }
    }

    private fun applyCurrentItems() {
        _state.update {
            it.copy(
                items = allItems.filteredBySearch(it.searchQuery).sortedByOption(it.sortOption),
            )
        }
    }

    private fun buildCollaborators(
        list: NotesListSummary,
        friendsByUserId: Map<String, FriendSummary> = knownFriendsByUserId,
    ): List<ListCollaborator> = list.directSharedWithUserIds
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
        ownerId: String,
        listId: String,
        update: (NotesListSummary) -> NotesListSummary,
    ): NotesListSummary? {
        var updatedList: NotesListSummary? = null
        allItems = allItems.map { currentList ->
            if (currentList.id == listId && currentList.ownerId == ownerId) {
                update(currentList).also { candidate -> updatedList = candidate }
            } else {
                currentList
            }
        }
        return updatedList
    }

    private fun replaceLocalLists(lists: List<NotesListSummary>) {
        val replacementsByKey = lists.associateBy { list -> list.ownerId to list.id }
        allItems = allItems.map { currentList ->
            replacementsByKey[currentList.ownerId to currentList.id] ?: currentList
        }
    }

    private fun requireAccessibleList(list: NotesListSummary): Result<NotesListSummary> {
        val currentList = findCurrentList(list)
        val currentUserId = authRepository.currentUserId
        return if (currentUserId != null && currentUserId in currentList.contributors) {
            Result.success(currentList)
        } else {
            Result.failure(NotesListsValidationException(NotesListsError.NO_ACCESS))
        }
    }

    private fun publishCurrentItems() {
        _state.update {
            it.copy(
                items = allItems.filteredBySearch(it.searchQuery).sortedByOption(it.sortOption),
            )
        }
    }

    private fun NotesListSummary.withDirectContributors(updatedContributors: List<String>): NotesListSummary {
        val directContributors = updatedContributors.distinct()
        val contributors = effectiveContributors(
            ownerId = ownerId,
            directContributors = directContributors,
            inheritedGroupContributors = inheritedGroupContributors,
        )
        val currentUserId = authRepository.currentUserId
        return copy(
            contributors = contributors,
            directContributors = directContributors,
            isShared = if (currentUserId == null) {
                contributors.size > 1
            } else {
                ownerId != currentUserId || contributors.size > 1
            },
        )
    }

    private fun NotesListSummary.withInheritedGroupContributors(
        updatedInheritedGroupContributors: List<String>,
    ): NotesListSummary {
        val inheritedGroupContributors = updatedInheritedGroupContributors.distinct()
        val contributors = effectiveContributors(
            ownerId = ownerId,
            directContributors = directContributors,
            inheritedGroupContributors = inheritedGroupContributors,
        )
        val currentUserId = authRepository.currentUserId
        return copy(
            contributors = contributors,
            inheritedGroupContributors = inheritedGroupContributors,
            isShared = if (currentUserId == null) {
                contributors.size > 1
            } else {
                ownerId != currentUserId || contributors.size > 1
            },
        )
    }

    private fun NotesListSummary.withGroup(
        group: NotesListSummary?,
        groupOrder: Int,
    ): NotesListSummary {
        val inheritedContributors = group?.directContributors.orEmpty()
        return copy(
            groupId = group?.id,
            groupOwnerId = group?.ownerId,
            groupOrder = if (group == null) 0 else groupOrder,
        ).withInheritedGroupContributors(inheritedContributors)
    }

    private fun propagateGroupContributor(
        group: NotesListSummary,
        friendUserId: String,
        added: Boolean,
    ) {
        allItems = allItems.map { item ->
            if (item.groupKey == group.key) {
                val inheritedContributors = if (added) {
                    item.inheritedGroupContributors + friendUserId
                } else {
                    item.inheritedGroupContributors.filterNot { contributorId ->
                        contributorId == friendUserId
                    }
                }
                item.withInheritedGroupContributors(inheritedContributors)
            } else {
                item
            }
        }
    }

    private fun NotesListSummary.withArchivedBy(
        currentUserId: String,
        archived: Boolean,
    ): NotesListSummary {
        val updatedArchivedBy = if (archived) {
            (archivedBy + currentUserId).distinct()
        } else {
            archivedBy.filterNot { userId -> userId == currentUserId }
        }
        val updatedArchivedAtBy = if (archived) {
            archivedAtBy + (currentUserId to Clock.System.now())
        } else {
            archivedAtBy - currentUserId
        }
        return copy(
            archivedBy = updatedArchivedBy,
            archivedAtBy = updatedArchivedAtBy,
        )
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 250L
    }
}

private fun List<NotesListSummary>.filteredBySearch(query: String): List<NotesListSummary> {
    val searchText = query.trim()
    return if (searchText.isBlank()) {
        this
    } else {
        filter { item -> item.name.contains(searchText, ignoreCase = true) }
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
