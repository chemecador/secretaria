package com.chemecador.secretaria.friends

data class FriendsState(
    val isLoading: Boolean = false,
    val isWorking: Boolean = false,
    val userCode: String? = null,
    val friends: List<FriendSummary> = emptyList(),
    val incomingRequests: List<IncomingFriendRequest> = emptyList(),
    val outgoingRequests: List<OutgoingFriendRequest> = emptyList(),
    val contentError: FriendsMessage? = null,
    val message: FriendsMessage? = null,
)

enum class FriendsMessage {
    LOAD_FAILED,
    ACTION_FAILED,
    INVALID_FRIEND_CODE,
    OWN_FRIEND_CODE,
    USER_NOT_FOUND,
    ALREADY_FRIENDS,
    REQUEST_ALREADY_EXISTS,
    REQUEST_SENT,
    REQUEST_ACCEPTED,
    REQUEST_REJECTED,
    REQUEST_CANCELLED,
    FRIEND_REMOVED,
}
