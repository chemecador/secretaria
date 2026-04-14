package com.chemecador.secretaria.friends

import kotlin.time.Clock
import kotlin.time.Instant

class FakeFriendsRepository(
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : FriendsRepository {
    private val myCode = "261051"
    private val friends = mutableListOf(
        FriendSummary(
            friendshipId = "friendship-1",
            userId = "friend-1",
            name = "Marina",
        ),
    )
    private val incomingRequests = mutableListOf(
        IncomingFriendRequest(
            id = "incoming-1",
            senderId = "friend-2",
            senderName = "Carlos",
            requestedAt = nowProvider(),
        ),
    )
    private val outgoingRequests = mutableListOf(
        OutgoingFriendRequest(
            id = "outgoing-1",
            receiverId = "friend-3",
            receiverCode = "2610599",
            requestedAt = nowProvider(),
        ),
    )

    override suspend fun getMyFriendCode(): Result<String> = Result.success(myCode)

    override suspend fun getFriends(): Result<List<FriendSummary>> =
        Result.success(friends.toList())

    override suspend fun getIncomingRequests(): Result<List<IncomingFriendRequest>> =
        Result.success(incomingRequests.toList())

    override suspend fun getOutgoingRequests(): Result<List<OutgoingFriendRequest>> =
        Result.success(outgoingRequests.toList())

    override suspend fun sendFriendRequest(friendCode: String): Result<Unit> =
        runCatching {
            when {
                friendCode == myCode -> throw SelfFriendRequestException()
                outgoingRequests.any { it.receiverCode == friendCode } -> {
                    throw FriendAlreadyExistsException(pending = true)
                }

                friendCode == "000" -> throw FriendUserNotFoundException()
                else -> {
                    outgoingRequests += OutgoingFriendRequest(
                        id = "outgoing-${outgoingRequests.size + 1}",
                        receiverId = "friend-${outgoingRequests.size + 10}",
                        receiverCode = friendCode,
                        requestedAt = nowProvider(),
                    )
                }
            }
        }

    override suspend fun acceptFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            val request = incomingRequests.first { it.id == requestId }
            incomingRequests.removeAll { it.id == requestId }
            friends += FriendSummary(
                friendshipId = request.id,
                userId = request.senderId,
                name = request.senderName,
            )
        }

    override suspend fun rejectFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            incomingRequests.removeAll { it.id == requestId }
        }

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            outgoingRequests.removeAll { it.id == requestId }
        }

    override suspend fun deleteFriend(friendshipId: String): Result<Unit> =
        runCatching {
            friends.removeAll { it.friendshipId == friendshipId }
        }
}
