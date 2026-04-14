package com.chemecador.secretaria.friends

import kotlin.time.Instant

data class FriendSummary(
    val friendshipId: String,
    val userId: String,
    val name: String,
)

data class IncomingFriendRequest(
    val id: String,
    val senderId: String,
    val senderName: String,
    val requestedAt: Instant,
)

data class OutgoingFriendRequest(
    val id: String,
    val receiverId: String,
    val receiverCode: String,
    val requestedAt: Instant,
)
