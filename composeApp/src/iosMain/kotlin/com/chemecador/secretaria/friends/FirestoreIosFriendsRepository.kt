package com.chemecador.secretaria.friends

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.FirestoreIosDocument
import com.chemecador.secretaria.firestore.FirestorePrecondition
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreLong
import com.chemecador.secretaria.firestore.firestoreNull
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant

internal class FirestoreIosFriendsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseIosFirestoreRestApi,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : FriendsRepository {

    override suspend fun getMyFriendCode(): Result<String> =
        runCatching {
            repeat(MAX_CODE_RESERVATION_ATTEMPTS) {
                try {
                    reserveFriendCode()
                        .let { return@runCatching it }
                } catch (throwable: Throwable) {
                    if (!throwable.isRetriableFriendCodeReservationFailure()) {
                        throw throwable
                    }
                }
            }
            error("Unable to allocate friend code")
        }

    override suspend fun getFriends(): Result<List<FriendSummary>> =
        runCatching {
            val userId = requireUserId()
            val sent = firestore.runQuery(
                structuredQuery = collectionQuery(
                    collectionId = FriendsFirestoreSchema.FRIENDSHIPS,
                    equalsFilter(FriendsFirestoreSchema.SENDER_ID, firestoreString(userId)),
                    isNotNullFilter(FriendsFirestoreSchema.ACCEPTANCE_DATE),
                ),
            )
            val received = firestore.runQuery(
                structuredQuery = collectionQuery(
                    collectionId = FriendsFirestoreSchema.FRIENDSHIPS,
                    equalsFilter(FriendsFirestoreSchema.RECEIVER_ID, firestoreString(userId)),
                    isNotNullFilter(FriendsFirestoreSchema.ACCEPTANCE_DATE),
                ),
            )

            (sent.map { document ->
                FriendSummary(
                    friendshipId = document.id,
                    userId = document.fields.firestoreString(FriendsFirestoreSchema.RECEIVER_ID)
                        .orEmpty(),
                    name = document.fields.firestoreString(FriendsFirestoreSchema.RECEIVER_NAME)
                        .orEmpty()
                        .ifBlank {
                            document.fields.firestoreString(FriendsFirestoreSchema.RECEIVER_ID)
                                .orEmpty()
                        },
                )
            } + received.map { document ->
                FriendSummary(
                    friendshipId = document.id,
                    userId = document.fields.firestoreString(FriendsFirestoreSchema.SENDER_ID)
                        .orEmpty(),
                    name = document.fields.firestoreString(FriendsFirestoreSchema.SENDER_NAME)
                        .orEmpty()
                        .ifBlank {
                            document.fields.firestoreString(FriendsFirestoreSchema.SENDER_ID)
                                .orEmpty()
                        },
                )
            }).distinctBy { it.friendshipId }
        }

    override suspend fun getIncomingRequests(): Result<List<IncomingFriendRequest>> =
        runCatching {
            firestore.runQuery(
                structuredQuery = collectionQuery(
                    collectionId = FriendsFirestoreSchema.FRIENDSHIPS,
                    equalsFilter(
                        FriendsFirestoreSchema.RECEIVER_ID,
                        firestoreString(requireUserId())
                    ),
                    isNullFilter(FriendsFirestoreSchema.ACCEPTANCE_DATE),
                ),
            ).map { document ->
                IncomingFriendRequest(
                    id = document.id,
                    senderId = document.fields.firestoreString(FriendsFirestoreSchema.SENDER_ID)
                        .orEmpty(),
                    senderName = document.fields.firestoreString(FriendsFirestoreSchema.SENDER_NAME)
                        .orEmpty()
                        .ifBlank {
                            document.fields.firestoreString(FriendsFirestoreSchema.SENDER_ID)
                                .orEmpty()
                        },
                    requestedAt = document.fields.firestoreInstant(FriendsFirestoreSchema.REQUEST_DATE)
                        ?: Instant.fromEpochMilliseconds(0),
                )
            }
        }

    override suspend fun getOutgoingRequests(): Result<List<OutgoingFriendRequest>> =
        runCatching {
            firestore.runQuery(
                structuredQuery = collectionQuery(
                    collectionId = FriendsFirestoreSchema.FRIENDSHIPS,
                    equalsFilter(
                        FriendsFirestoreSchema.SENDER_ID,
                        firestoreString(requireUserId())
                    ),
                    isNullFilter(FriendsFirestoreSchema.ACCEPTANCE_DATE),
                ),
            ).map { document ->
                OutgoingFriendRequest(
                    id = document.id,
                    receiverId = document.fields.firestoreString(FriendsFirestoreSchema.RECEIVER_ID)
                        .orEmpty(),
                    receiverCode = document.fields.firestoreString(FriendsFirestoreSchema.RECEIVER_CODE)
                        .orEmpty(),
                    requestedAt = document.fields.firestoreInstant(FriendsFirestoreSchema.REQUEST_DATE)
                        ?: Instant.fromEpochMilliseconds(0),
                )
            }
        }

    override suspend fun sendFriendRequest(friendCode: String): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val friendId = findUserIdByCode(friendCode) ?: throw FriendUserNotFoundException()
            if (friendId == userId) {
                throw SelfFriendRequestException()
            }

            val existingFriendship = findExistingFriendship(userId, friendId)
            if (existingFriendship != null) {
                throw FriendAlreadyExistsException(pending = existingFriendship.acceptedAt == null)
            }

            firestore.createDocument(
                parentPath = "",
                collectionId = FriendsFirestoreSchema.FRIENDSHIPS,
                fields = buildJsonObject {
                    put(FriendsFirestoreSchema.SENDER_ID, firestoreString(userId))
                    put(
                        FriendsFirestoreSchema.SENDER_NAME,
                        firestoreString(authRepository.currentUserEmail ?: userId),
                    )
                    put(FriendsFirestoreSchema.RECEIVER_ID, firestoreString(friendId))
                    put(FriendsFirestoreSchema.RECEIVER_CODE, firestoreString(friendCode))
                    put(FriendsFirestoreSchema.REQUEST_DATE, firestoreTimestamp(nowProvider()))
                    put(FriendsFirestoreSchema.ACCEPTANCE_DATE, firestoreNull())
                },
            )
        }

    override suspend fun acceptFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            firestore.patchDocument(
                documentPath = friendshipDocumentPath(requestId),
                fields = buildJsonObject {
                    put(FriendsFirestoreSchema.ACCEPTANCE_DATE, firestoreTimestamp(nowProvider()))
                    put(
                        FriendsFirestoreSchema.RECEIVER_NAME,
                        firestoreString(authRepository.currentUserEmail ?: userId),
                    )
                },
                updateMask = listOf(
                    FriendsFirestoreSchema.ACCEPTANCE_DATE,
                    FriendsFirestoreSchema.RECEIVER_NAME,
                ),
            )
        }

    override suspend fun rejectFriendRequest(requestId: String): Result<Unit> =
        deleteOwnedRequest(
            requestId = requestId,
            ownerField = FriendsFirestoreSchema.RECEIVER_ID,
        )

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> =
        deleteOwnedRequest(
            requestId = requestId,
            ownerField = FriendsFirestoreSchema.SENDER_ID,
        )

    override suspend fun deleteFriend(friendshipId: String): Result<Unit> =
        runCatching {
            val document = firestore.getDocumentOrNull(friendshipDocumentPath(friendshipId))
                ?: error("Friendship not found")
            val userId = requireUserId()
            val isParticipant =
                document.fields.firestoreString(FriendsFirestoreSchema.SENDER_ID) == userId ||
                        document.fields.firestoreString(FriendsFirestoreSchema.RECEIVER_ID) == userId
            check(isParticipant)
            firestore.deleteDocument(friendshipDocumentPath(friendshipId))
        }

    private suspend fun deleteOwnedRequest(
        requestId: String,
        ownerField: String,
    ): Result<Unit> =
        runCatching {
            val document = firestore.getDocumentOrNull(friendshipDocumentPath(requestId))
                ?: error("Friendship not found")
            check(document.fields.firestoreString(ownerField) == requireUserId())
            firestore.deleteDocument(friendshipDocumentPath(requestId))
        }

    private suspend fun findUserIdByCode(friendCode: String): String? =
        firestore.runQuery(
            structuredQuery = collectionQuery(
                collectionId = FriendsFirestoreSchema.USERS,
                equalsFilter(FriendsFirestoreSchema.USERCODE, firestoreString(friendCode)),
                limit = 1,
            ),
        ).firstOrNull()?.id

    private suspend fun findExistingFriendship(
        userId: String,
        friendId: String,
    ): FriendshipDocument? {
        val direct = firestore.runQuery(
            structuredQuery = collectionQuery(
                collectionId = FriendsFirestoreSchema.FRIENDSHIPS,
                equalsFilter(FriendsFirestoreSchema.SENDER_ID, firestoreString(userId)),
                equalsFilter(FriendsFirestoreSchema.RECEIVER_ID, firestoreString(friendId)),
                limit = 1,
            ),
        ).firstOrNull()
        if (direct != null) {
            return direct.toFriendshipDocument()
        }

        return firestore.runQuery(
            structuredQuery = collectionQuery(
                collectionId = FriendsFirestoreSchema.FRIENDSHIPS,
                equalsFilter(FriendsFirestoreSchema.SENDER_ID, firestoreString(friendId)),
                equalsFilter(FriendsFirestoreSchema.RECEIVER_ID, firestoreString(userId)),
                limit = 1,
            ),
        ).firstOrNull()?.toFriendshipDocument()
    }

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")

    private suspend fun reserveFriendCode(): String {
        val userId = requireUserId()
        val userDocument = firestore.getDocumentOrNull(userDocumentPath(userId))
        val existingCode = userDocument?.fields?.firestoreString(FriendsFirestoreSchema.USERCODE)
        if (!existingCode.isNullOrBlank()) {
            return existingCode
        }

        val dateKey = currentFriendCodeDateKey(nowProvider())
        val counterDocument = firestore.getDocumentOrNull(counterDocumentPath(dateKey))
        val newCounter =
            (counterDocument?.fields?.firestoreLong(FriendsFirestoreSchema.COUNTER) ?: 0L) + 1L
        val newCode = buildFriendCode(dateKey, newCounter)

        firestore.patchDocument(
            documentPath = counterDocumentPath(dateKey),
            fields = buildJsonObject {
                put(FriendsFirestoreSchema.COUNTER, firestoreLong(newCounter))
            },
            updateMask = listOf(FriendsFirestoreSchema.COUNTER),
            currentDocument = counterDocument.toPrecondition(),
        )

        firestore.patchDocument(
            documentPath = userDocumentPath(userId),
            fields = buildJsonObject {
                put(FriendsFirestoreSchema.USERCODE, firestoreString(newCode))
            },
            updateMask = listOf(FriendsFirestoreSchema.USERCODE),
            currentDocument = userDocument.toPrecondition(),
        )

        return newCode
    }

    private fun userDocumentPath(userId: String): String =
        "${FriendsFirestoreSchema.USERS}/$userId"

    private fun counterDocumentPath(dateKey: String): String =
        "${FriendsFirestoreSchema.USERCODES}/$dateKey"

    private fun friendshipDocumentPath(requestId: String): String =
        "${FriendsFirestoreSchema.FRIENDSHIPS}/$requestId"

    private companion object {
        const val MAX_CODE_RESERVATION_ATTEMPTS = 8
    }
}

private data class FriendshipDocument(
    val acceptedAt: Instant?,
)

private fun FirestoreIosDocument.toFriendshipDocument(): FriendshipDocument =
    FriendshipDocument(
        acceptedAt = fields.firestoreInstant(FriendsFirestoreSchema.ACCEPTANCE_DATE),
    )

private fun FirestoreIosDocument?.toPrecondition(): FirestorePrecondition =
    if (this == null) {
        FirestorePrecondition(exists = false)
    } else {
        FirestorePrecondition(updateTime = updateTime ?: error("Missing update time for ${name}"))
    }

private fun Throwable.isRetriableFriendCodeReservationFailure(): Boolean {
    val message = message.orEmpty()
    return "ABORTED" in message ||
            "FAILED_PRECONDITION" in message ||
            "precondition" in message.lowercase()
}
