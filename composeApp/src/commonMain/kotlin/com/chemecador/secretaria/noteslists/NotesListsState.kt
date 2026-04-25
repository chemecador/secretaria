package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.friends.FriendSummary

data class NotesListsState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<NotesListSummary> = emptyList(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val collaboratorsByListId: Map<String, List<ListCollaborator>> = emptyMap(),
    val shareableFriends: List<FriendSummary> = emptyList(),
    val isLoadingShareableFriends: Boolean = false,
    val isUpdatingSharing: Boolean = false,
    val shareErrorMessage: String? = null,
    val shareFeedback: ListSharingFeedback? = null,
    val archiveFeedback: ListArchiveFeedback? = null,
)
