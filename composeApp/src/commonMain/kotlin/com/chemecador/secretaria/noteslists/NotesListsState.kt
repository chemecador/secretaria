package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.friends.FriendSummary

data class NotesListsState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val items: List<NotesListSummary> = emptyList(),
    val sortOption: SortOption = SortOption.DATE_DESC,
    val searchQuery: String = "",
    val errorMessage: String? = null,
    val validationError: NotesListsError? = null,
    val collaboratorsByListId: Map<String, List<ListCollaborator>> = emptyMap(),
    val shareableFriends: List<FriendSummary> = emptyList(),
    val isLoadingShareableFriends: Boolean = false,
    val isUpdatingSharing: Boolean = false,
    val shareErrorMessage: String? = null,
    val shareValidationError: NotesListsError? = null,
    val shareFeedback: ListSharingFeedback? = null,
    val archiveFeedback: ListArchiveFeedback? = null,
    val leaveSharedListFeedback: ListLeaveSharedFeedback? = null,
)

/**
 * Routes a failure to the typed field when the ViewModel produced it, and to the raw message field
 * when it came from a repository. Only one of the two is ever set at a time.
 */
internal fun NotesListsState.withError(throwable: Throwable): NotesListsState =
    if (throwable is NotesListsValidationException) {
        copy(errorMessage = null, validationError = throwable.error)
    } else {
        copy(errorMessage = throwable.message, validationError = null)
    }

internal fun NotesListsState.withShareError(throwable: Throwable): NotesListsState =
    if (throwable is NotesListsValidationException) {
        copy(shareErrorMessage = null, shareValidationError = throwable.error)
    } else {
        copy(shareErrorMessage = throwable.message, shareValidationError = null)
    }
