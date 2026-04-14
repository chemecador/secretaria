package com.chemecador.secretaria.friends

interface FriendsRepository {
    suspend fun getMyFriendCode(): Result<String>
    suspend fun getFriends(): Result<List<FriendSummary>>
    suspend fun getIncomingRequests(): Result<List<IncomingFriendRequest>>
    suspend fun getOutgoingRequests(): Result<List<OutgoingFriendRequest>>
    suspend fun sendFriendRequest(friendCode: String): Result<Unit>
    suspend fun acceptFriendRequest(requestId: String): Result<Unit>
    suspend fun rejectFriendRequest(requestId: String): Result<Unit>
    suspend fun cancelFriendRequest(requestId: String): Result<Unit>
    suspend fun deleteFriend(friendshipId: String): Result<Unit>
}
