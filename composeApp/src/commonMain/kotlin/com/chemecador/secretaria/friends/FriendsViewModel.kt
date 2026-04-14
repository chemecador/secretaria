package com.chemecador.secretaria.friends

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FriendsViewModel(
    private val repository: FriendsRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(FriendsState())
    val state: StateFlow<FriendsState> = _state.asStateFlow()

    fun load() {
        viewModelScope.launch {
            refresh(showLoading = shouldShowFullScreenLoading())
        }
    }

    fun refresh() {
        viewModelScope.launch {
            refresh(showLoading = shouldShowFullScreenLoading())
        }
    }

    fun sendFriendRequest(friendCode: String) {
        val trimmedCode = friendCode.trim()
        when {
            trimmedCode.length < 3 || !trimmedCode.all(Char::isDigit) -> {
                publishMessage(FriendsMessage.INVALID_FRIEND_CODE)
                return
            }

            trimmedCode == state.value.userCode -> {
                publishMessage(FriendsMessage.OWN_FRIEND_CODE)
                return
            }

            state.value.outgoingRequests.any { it.receiverCode == trimmedCode } -> {
                publishMessage(FriendsMessage.REQUEST_ALREADY_EXISTS)
                return
            }
        }

        performAction(
            successMessage = FriendsMessage.REQUEST_SENT,
            failureFallback = FriendsMessage.ACTION_FAILED,
        ) {
            repository.sendFriendRequest(trimmedCode)
        }
    }

    fun acceptFriendRequest(requestId: String) {
        performAction(
            successMessage = FriendsMessage.REQUEST_ACCEPTED,
            failureFallback = FriendsMessage.ACTION_FAILED,
        ) {
            repository.acceptFriendRequest(requestId)
        }
    }

    fun rejectFriendRequest(requestId: String) {
        performAction(
            successMessage = FriendsMessage.REQUEST_REJECTED,
            failureFallback = FriendsMessage.ACTION_FAILED,
        ) {
            repository.rejectFriendRequest(requestId)
        }
    }

    fun cancelFriendRequest(requestId: String) {
        performAction(
            successMessage = FriendsMessage.REQUEST_CANCELLED,
            failureFallback = FriendsMessage.ACTION_FAILED,
        ) {
            repository.cancelFriendRequest(requestId)
        }
    }

    fun deleteFriend(friendshipId: String) {
        performAction(
            successMessage = FriendsMessage.FRIEND_REMOVED,
            failureFallback = FriendsMessage.ACTION_FAILED,
        ) {
            repository.deleteFriend(friendshipId)
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    private fun performAction(
        successMessage: FriendsMessage,
        failureFallback: FriendsMessage,
        block: suspend () -> Result<Unit>,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true) }
            block()
                .onSuccess {
                    refresh(
                        showLoading = false,
                        successMessage = successMessage,
                    )
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isWorking = false,
                            message = throwable.toFriendsMessage(failureFallback),
                        )
                    }
                }
        }
    }

    private suspend fun refresh(
        showLoading: Boolean,
        successMessage: FriendsMessage? = null,
    ) {
        _state.update {
            it.copy(
                isLoading = showLoading,
                isWorking = !showLoading,
                contentError = if (showLoading) null else it.contentError,
            )
        }

        try {
            val snapshot = coroutineScope {
                val userCode = async { repository.getMyFriendCode().getOrThrow() }
                val friends = async { repository.getFriends().getOrThrow() }
                val incoming = async { repository.getIncomingRequests().getOrThrow() }
                val outgoing = async { repository.getOutgoingRequests().getOrThrow() }
                FriendsSnapshot(
                    userCode = userCode.await(),
                    friends = friends.await().sortedBy { it.name.lowercase() },
                    incomingRequests = incoming.await().sortedByDescending { it.requestedAt },
                    outgoingRequests = outgoing.await().sortedByDescending { it.requestedAt },
                )
            }

            _state.update {
                it.copy(
                    isLoading = false,
                    isWorking = false,
                    userCode = snapshot.userCode,
                    friends = snapshot.friends,
                    incomingRequests = snapshot.incomingRequests,
                    outgoingRequests = snapshot.outgoingRequests,
                    contentError = null,
                    message = successMessage ?: it.message,
                )
            }
        } catch (_: Exception) {
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    isWorking = false,
                    contentError = if (hasContent(current)) null else FriendsMessage.LOAD_FAILED,
                    message = if (showLoading) current.message else FriendsMessage.ACTION_FAILED,
                )
            }
        }
    }

    private fun publishMessage(message: FriendsMessage) {
        _state.update { it.copy(message = message) }
    }

    private fun shouldShowFullScreenLoading(): Boolean =
        state.value.userCode == null &&
                state.value.friends.isEmpty() &&
                state.value.incomingRequests.isEmpty() &&
                state.value.outgoingRequests.isEmpty()

    private fun hasContent(state: FriendsState): Boolean =
        state.userCode != null ||
                state.friends.isNotEmpty() ||
                state.incomingRequests.isNotEmpty() ||
                state.outgoingRequests.isNotEmpty()
}

private data class FriendsSnapshot(
    val userCode: String,
    val friends: List<FriendSummary>,
    val incomingRequests: List<IncomingFriendRequest>,
    val outgoingRequests: List<OutgoingFriendRequest>,
)

private fun Throwable.toFriendsMessage(default: FriendsMessage): FriendsMessage =
    when (this) {
        is FriendUserNotFoundException -> FriendsMessage.USER_NOT_FOUND
        is FriendAlreadyExistsException -> {
            if (pending) {
                FriendsMessage.REQUEST_ALREADY_EXISTS
            } else {
                FriendsMessage.ALREADY_FRIENDS
            }
        }

        is SelfFriendRequestException -> FriendsMessage.OWN_FRIEND_CODE
        else -> default
    }
