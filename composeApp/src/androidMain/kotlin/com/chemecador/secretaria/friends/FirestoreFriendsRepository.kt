package com.chemecador.secretaria.friends

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.time.Clock
import kotlin.time.Instant

internal class FirestoreFriendsRepository(
    private val authRepository: AuthRepository,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : FriendsRepository {

    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun getMyFriendCode(): Result<String> =
        runCatching {
            val userId = requireUserId()
            val userDocumentRef =
                firestore.collection(FriendsFirestoreSchema.USERS).document(userId)
            firestore.runTransaction { transaction ->
                val userDocument = transaction.get(userDocumentRef)
                val existingCode = userDocument.getString(FriendsFirestoreSchema.USERCODE)
                if (!existingCode.isNullOrBlank()) {
                    return@runTransaction existingCode
                }

                val dateKey = currentFriendCodeDateKey(nowProvider())
                val counterDocumentRef = firestore.collection(FriendsFirestoreSchema.USERCODES)
                    .document(dateKey)
                val counterDocument = transaction.get(counterDocumentRef)
                val newCounter =
                    (counterDocument.getLong(FriendsFirestoreSchema.COUNTER) ?: 0L) + 1L
                val newCode = buildFriendCode(dateKey, newCounter)

                transaction.set(
                    counterDocumentRef,
                    mapOf(FriendsFirestoreSchema.COUNTER to newCounter),
                )
                if (!userDocument.exists()) {
                    transaction.set(
                        userDocumentRef,
                        mapOf(FriendsFirestoreSchema.USERCODE to newCode),
                    )
                } else {
                    transaction.update(
                        userDocumentRef,
                        FriendsFirestoreSchema.USERCODE,
                        newCode,
                    )
                }
                newCode
            }.await()
        }

    override suspend fun getFriends(): Result<List<FriendSummary>> =
        runCatching {
            val userId = requireUserId()
            val sentSnapshot = firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .whereEqualTo(FriendsFirestoreSchema.SENDER_ID, userId)
                .whereNotEqualTo(FriendsFirestoreSchema.ACCEPTANCE_DATE, null)
                .get()
                .await()
            val receivedSnapshot = firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .whereEqualTo(FriendsFirestoreSchema.RECEIVER_ID, userId)
                .whereNotEqualTo(FriendsFirestoreSchema.ACCEPTANCE_DATE, null)
                .get()
                .await()

            (sentSnapshot.documents.map { document ->
                FriendSummary(
                    friendshipId = document.id,
                    userId = document.getString(FriendsFirestoreSchema.RECEIVER_ID).orEmpty(),
                    name = document.getString(FriendsFirestoreSchema.RECEIVER_NAME)
                        .orEmpty()
                        .ifBlank {
                            document.getString(FriendsFirestoreSchema.RECEIVER_ID).orEmpty()
                        },
                )
            } + receivedSnapshot.documents.map { document ->
                FriendSummary(
                    friendshipId = document.id,
                    userId = document.getString(FriendsFirestoreSchema.SENDER_ID).orEmpty(),
                    name = document.getString(FriendsFirestoreSchema.SENDER_NAME)
                        .orEmpty()
                        .ifBlank { document.getString(FriendsFirestoreSchema.SENDER_ID).orEmpty() },
                )
            }).distinctBy { it.friendshipId }
        }

    override suspend fun getIncomingRequests(): Result<List<IncomingFriendRequest>> =
        runCatching {
            val snapshot = firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .whereEqualTo(FriendsFirestoreSchema.RECEIVER_ID, requireUserId())
                .whereEqualTo(FriendsFirestoreSchema.ACCEPTANCE_DATE, null)
                .get()
                .await()

            snapshot.documents.map { document ->
                IncomingFriendRequest(
                    id = document.id,
                    senderId = document.getString(FriendsFirestoreSchema.SENDER_ID).orEmpty(),
                    senderName = document.getString(FriendsFirestoreSchema.SENDER_NAME)
                        .orEmpty()
                        .ifBlank { document.getString(FriendsFirestoreSchema.SENDER_ID).orEmpty() },
                    requestedAt = document.getTimestamp(FriendsFirestoreSchema.REQUEST_DATE)
                        ?.let { Instant.fromEpochMilliseconds(it.toDate().time) }
                        ?: Instant.fromEpochMilliseconds(0),
                )
            }
        }

    override suspend fun getOutgoingRequests(): Result<List<OutgoingFriendRequest>> =
        runCatching {
            val snapshot = firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .whereEqualTo(FriendsFirestoreSchema.SENDER_ID, requireUserId())
                .whereEqualTo(FriendsFirestoreSchema.ACCEPTANCE_DATE, null)
                .get()
                .await()

            snapshot.documents.map { document ->
                OutgoingFriendRequest(
                    id = document.id,
                    receiverId = document.getString(FriendsFirestoreSchema.RECEIVER_ID).orEmpty(),
                    receiverCode = document.getString(FriendsFirestoreSchema.RECEIVER_CODE)
                        .orEmpty(),
                    requestedAt = document.getTimestamp(FriendsFirestoreSchema.REQUEST_DATE)
                        ?.let { Instant.fromEpochMilliseconds(it.toDate().time) }
                        ?: Instant.fromEpochMilliseconds(0),
                )
            }
        }

    override suspend fun sendFriendRequest(friendCode: String): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val targetSnapshot = firestore.collection(FriendsFirestoreSchema.USERS)
                .whereEqualTo(FriendsFirestoreSchema.USERCODE, friendCode)
                .limit(1)
                .get()
                .await()

            val targetDocument = targetSnapshot.documents.firstOrNull()
                ?: throw FriendUserNotFoundException()
            val friendId = targetDocument.id
            if (friendId == userId) {
                throw SelfFriendRequestException()
            }

            val existingFriendship = findExistingFriendship(userId = userId, friendId = friendId)
            if (existingFriendship != null) {
                throw FriendAlreadyExistsException(
                    pending = existingFriendship.getTimestamp(FriendsFirestoreSchema.ACCEPTANCE_DATE) == null,
                )
            }

            firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .add(
                    mapOf(
                        FriendsFirestoreSchema.SENDER_ID to userId,
                        FriendsFirestoreSchema.SENDER_NAME to currentUserDisplayName(userId),
                        FriendsFirestoreSchema.RECEIVER_ID to friendId,
                        FriendsFirestoreSchema.RECEIVER_CODE to friendCode,
                        FriendsFirestoreSchema.REQUEST_DATE to FieldValue.serverTimestamp(),
                        FriendsFirestoreSchema.ACCEPTANCE_DATE to null,
                    ),
                )
                .await()
        }

    override suspend fun acceptFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .document(requestId)
                .update(
                    mapOf(
                        FriendsFirestoreSchema.ACCEPTANCE_DATE to FieldValue.serverTimestamp(),
                        FriendsFirestoreSchema.RECEIVER_NAME to currentUserDisplayName(userId),
                    ),
                )
                .await()
        }

    override suspend fun rejectFriendRequest(requestId: String): Result<Unit> =
        deleteRequestOwnedBy(
            requestId = requestId,
            ownerField = FriendsFirestoreSchema.RECEIVER_ID,
        )

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> =
        deleteRequestOwnedBy(
            requestId = requestId,
            ownerField = FriendsFirestoreSchema.SENDER_ID,
        )

    override suspend fun deleteFriend(friendshipId: String): Result<Unit> =
        runCatching {
            val document = firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .document(friendshipId)
                .get()
                .await()
            val userId = requireUserId()
            val isParticipant = document.getString(FriendsFirestoreSchema.SENDER_ID) == userId ||
                    document.getString(FriendsFirestoreSchema.RECEIVER_ID) == userId
            check(isParticipant)
            document.reference.delete().await()
        }

    private suspend fun deleteRequestOwnedBy(
        requestId: String,
        ownerField: String,
    ): Result<Unit> =
        runCatching {
            val document = firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
                .document(requestId)
                .get()
                .await()
            check(document.getString(ownerField) == requireUserId())
            document.reference.delete().await()
        }

    private suspend fun findExistingFriendship(
        userId: String,
        friendId: String,
    ): com.google.firebase.firestore.DocumentSnapshot? {
        val sent = firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
            .whereEqualTo(FriendsFirestoreSchema.SENDER_ID, userId)
            .whereEqualTo(FriendsFirestoreSchema.RECEIVER_ID, friendId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        if (sent != null) {
            return sent
        }

        return firestore.collection(FriendsFirestoreSchema.FRIENDSHIPS)
            .whereEqualTo(FriendsFirestoreSchema.SENDER_ID, friendId)
            .whereEqualTo(FriendsFirestoreSchema.RECEIVER_ID, userId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
    }

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")

    private fun currentUserDisplayName(userId: String): String =
        authRepository.currentUserEmail ?: userId
}
