package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.friends.FriendSummary

data class NotesListsState(
    val isLoading: Boolean = false,
    val items: List<NotesListSummary> = emptyList(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val errorMessage: String? = null,
    val shareableFriends: List<FriendSummary> = emptyList(),
    val isLoadingShareableFriends: Boolean = false,
    val isSharingList: Boolean = false,
    val shareErrorMessage: String? = null,
    val lastSharedFriendName: String? = null,
)
