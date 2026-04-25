package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.time.Instant

class FirestoreNotesListsRepository(
    private val authRepository: AuthRepository,
) : NotesListsRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun requireUserId(): String =
        authRepository.currentUserId
            ?: error("User not logged in")

    private fun listDocument(ownerId: String, listId: String) =
        firestore.collection(USERS).document(ownerId)
            .collection(NOTES_LIST).document(listId)

    override suspend fun getLists(): Result<List<NotesListSummary>> {
        return try {
            val userId = requireUserId()
            val snapshot = firestore.collectionGroup(NOTES_LIST)
                .whereArrayContains(CONTRIBUTORS, userId)
                .get()
                .await()
            val lists = snapshot.documents.map { doc ->
                doc.toNotesListSummary(userId)
            }
            Result.success(lists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createList(
        name: String,
        ordered: Boolean,
        isGroup: Boolean,
    ): Result<NotesListSummary> {
        return try {
            val userId = requireUserId()
            val creator = authRepository.currentUserEmail ?: userId
            val colRef = firestore.collection(USERS).document(userId).collection(NOTES_LIST)
            val docRef = colRef.document()
            val data = hashMapOf(
                "name" to name,
                CONTRIBUTORS to listOf(userId),
                "creator" to creator,
                "date" to FieldValue.serverTimestamp(),
                "ordered" to ordered,
                IS_GROUP to isGroup,
                GROUP_ID to null,
                GROUP_OWNER_ID to null,
                GROUP_ORDER to 0,
                DIRECT_CONTRIBUTORS to listOf(userId),
                INHERITED_GROUP_CONTRIBUTORS to emptyList<String>(),
                ARCHIVED_BY to emptyList<String>(),
                ARCHIVED_AT_BY to emptyMap<String, Any>(),
            )
            docRef.set(data).await()
            val created = docRef.get().await()
            Result.success(created.toNotesListSummary(userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteList(listId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val listRef = listDocument(userId, listId)
            val listSnapshot = listRef.get().await()
            if (listSnapshot.getBoolean(IS_GROUP) == true) {
                val childLists = firestore.collectionGroup(NOTES_LIST)
                    .whereArrayContains(CONTRIBUTORS, userId)
                    .get()
                    .await()
                val batch = firestore.batch()
                childLists.documents
                    .filter { childDoc ->
                        childDoc.getString(GROUP_ID) == listId &&
                            childDoc.groupOwnerId() == userId
                    }
                    .forEach { childDoc ->
                        val directContributors = childDoc.directContributors()
                        val childOwnerId = childDoc.ownerId()
                        batch.update(
                            childDoc.reference,
                            mapOf(
                                DIRECT_CONTRIBUTORS to directContributors,
                                GROUP_ID to null,
                                GROUP_OWNER_ID to null,
                                GROUP_ORDER to 0,
                                INHERITED_GROUP_CONTRIBUTORS to emptyList<String>(),
                                CONTRIBUTORS to effectiveContributors(
                                    ownerId = childOwnerId,
                                    directContributors = directContributors,
                                    inheritedGroupContributors = emptyList(),
                                ),
                            ),
                        )
                    }
                batch.delete(listRef)
                batch.commit().await()
                return Result.success(Unit)
            }
            val notesSnapshot = listRef.collection(NOTES).get().await()
            val batch = firestore.batch()
            for (noteDoc in notesSnapshot.documents) {
                batch.delete(noteDoc.reference)
            }
            batch.delete(listRef)
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateList(
        listId: String,
        name: String,
        ordered: Boolean,
    ): Result<NotesListSummary> {
        return try {
            val userId = requireUserId()
            val docRef = listDocument(userId, listId)
            docRef.update(
                mapOf(
                    "name" to name,
                    "ordered" to ordered,
                )
            ).await()
            val updated = docRef.get().await()
            Result.success(updated.toNotesListSummary(userId))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val docRef = listDocument(userId, listId)
            val listSnapshot = docRef.get().await()
            val directContributors = (listSnapshot.directContributors() + friendUserId).distinct()
            val inheritedContributors = listSnapshot.inheritedGroupContributors()
            val batch = firestore.batch()
            batch.update(
                docRef,
                mapOf(
                    DIRECT_CONTRIBUTORS to directContributors,
                    CONTRIBUTORS to effectiveContributors(userId, directContributors, inheritedContributors),
                ),
            )
            if (listSnapshot.getBoolean(IS_GROUP) == true) {
                val childLists = firestore.collectionGroup(NOTES_LIST)
                    .whereArrayContains(CONTRIBUTORS, userId)
                    .get()
                    .await()
                childLists.documents
                    .filter { childDoc ->
                        childDoc.getString(GROUP_ID) == listId &&
                            childDoc.groupOwnerId() == userId
                    }
                    .forEach { childDoc ->
                        val childDirectContributors = childDoc.directContributors()
                        val childInheritedContributors =
                            (childDoc.inheritedGroupContributors() + friendUserId).distinct()
                        val childOwnerId = childDoc.ownerId()
                        batch.update(
                            childDoc.reference,
                            mapOf(
                                DIRECT_CONTRIBUTORS to childDirectContributors,
                                INHERITED_GROUP_CONTRIBUTORS to childInheritedContributors,
                                CONTRIBUTORS to effectiveContributors(
                                    ownerId = childOwnerId,
                                    directContributors = childDirectContributors,
                                    inheritedGroupContributors = childInheritedContributors,
                                ),
                            ),
                        )
                    }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val docRef = listDocument(userId, listId)
            val listSnapshot = docRef.get().await()
            val directContributors = listSnapshot.directContributors().filterNot { contributorId ->
                contributorId == friendUserId
            }
            val inheritedContributors = listSnapshot.inheritedGroupContributors()
            val batch = firestore.batch()
            batch.update(
                docRef,
                mapOf(
                    DIRECT_CONTRIBUTORS to directContributors,
                    CONTRIBUTORS to effectiveContributors(userId, directContributors, inheritedContributors),
                ),
            )
            if (listSnapshot.getBoolean(IS_GROUP) == true) {
                val childLists = firestore.collectionGroup(NOTES_LIST)
                    .whereArrayContains(CONTRIBUTORS, userId)
                    .get()
                    .await()
                childLists.documents
                    .filter { childDoc ->
                        childDoc.getString(GROUP_ID) == listId &&
                            childDoc.groupOwnerId() == userId
                    }
                    .forEach { childDoc ->
                        val childDirectContributors = childDoc.directContributors()
                        val childInheritedContributors =
                            childDoc.inheritedGroupContributors().filterNot { contributorId ->
                                contributorId == friendUserId
                            }
                        val childOwnerId = childDoc.ownerId()
                        batch.update(
                            childDoc.reference,
                            mapOf(
                                DIRECT_CONTRIBUTORS to childDirectContributors,
                                INHERITED_GROUP_CONTRIBUTORS to childInheritedContributors,
                                CONTRIBUTORS to effectiveContributors(
                                    ownerId = childOwnerId,
                                    directContributors = childDirectContributors,
                                    inheritedGroupContributors = childInheritedContributors,
                                ),
                            ),
                        )
                    }
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setListGroup(
        listOwnerId: String,
        listId: String,
        groupOwnerId: String?,
        groupId: String?,
    ): Result<Unit> {
        return try {
            val userId = requireUserId()
            if ((groupId == null) != (groupOwnerId == null)) {
                error("Group owner required")
            }
            val docRef = listDocument(listOwnerId, listId)
            val listSnapshot = docRef.get().await()
            if (listSnapshot.getBoolean(IS_GROUP) == true) {
                error("A group cannot be added to another group")
            }
            if (userId !in listSnapshot.stringList(CONTRIBUTORS)) {
                error("List not found")
            }
            val currentGroupOwnerId = listSnapshot.groupOwnerId()
            if (currentGroupOwnerId != null && currentGroupOwnerId != userId) {
                error("Only the group owner can modify this grouping")
            }
            val groupSnapshot = if (groupId == null || groupOwnerId == null) {
                null
            } else {
                if (groupOwnerId != userId) error("Only the group owner can modify this grouping")
                listDocument(groupOwnerId, groupId).get().await()
            }
            if (groupSnapshot != null && groupSnapshot.getBoolean(IS_GROUP) != true) {
                error("Group not found")
            }
            val inheritedContributors = groupSnapshot?.directContributors().orEmpty()
            val groupOrder = if (groupId == null || groupOwnerId == null) {
                0
            } else {
                firestore.collectionGroup(NOTES_LIST)
                    .whereArrayContains(CONTRIBUTORS, userId)
                    .get()
                    .await()
                    .documents
                    .count { doc ->
                        doc.getString(GROUP_ID) == groupId &&
                            doc.groupOwnerId() == groupOwnerId &&
                            (doc.ownerId() != listOwnerId || doc.id != listId)
                    }
            }
            docRef.update(
                mapOf(
                    DIRECT_CONTRIBUTORS to listSnapshot.directContributors(),
                    GROUP_ID to groupId,
                    GROUP_OWNER_ID to groupOwnerId,
                    GROUP_ORDER to groupOrder,
                    INHERITED_GROUP_CONTRIBUTORS to inheritedContributors,
                    CONTRIBUTORS to effectiveContributors(
                        ownerId = listOwnerId,
                        directContributors = listSnapshot.directContributors(),
                        inheritedGroupContributors = inheritedContributors,
                    ),
                ),
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorderGroupedLists(
        groupOwnerId: String,
        groupId: String,
        listKeysInOrder: List<NotesListKey>,
    ): Result<Unit> {
        return try {
            val userId = requireUserId()
            if (groupOwnerId != userId) error("Only the group owner can modify this grouping")
            val batch = firestore.batch()
            listKeysInOrder.forEachIndexed { index, listKey ->
                batch.update(listDocument(listKey.ownerId, listKey.listId), GROUP_ORDER, index)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun setListArchived(
        ownerId: String,
        listId: String,
        archived: Boolean,
    ): Result<Unit> {
        return try {
            val userId = requireUserId()
            val archiveUpdate = if (archived) {
                FieldValue.arrayUnion(userId)
            } else {
                FieldValue.arrayRemove(userId)
            }
            val archivedAtUpdate = if (archived) {
                FieldValue.serverTimestamp()
            } else {
                FieldValue.delete()
            }
            listDocument(ownerId, listId).update(
                FieldPath.of(ARCHIVED_BY),
                archiveUpdate,
                FieldPath.of(ARCHIVED_AT_BY, userId),
                archivedAtUpdate,
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val USERS = "users"
        private const val NOTES_LIST = "noteslist"
        private const val NOTES = "notes"
        private const val CONTRIBUTORS = "contributors"
        private const val DIRECT_CONTRIBUTORS = "directContributors"
        private const val INHERITED_GROUP_CONTRIBUTORS = "inheritedGroupContributors"
        private const val IS_GROUP = "isGroup"
        private const val GROUP_ID = "groupId"
        private const val GROUP_OWNER_ID = "groupOwnerId"
        private const val GROUP_ORDER = "groupOrder"
        private const val ARCHIVED_BY = "archivedBy"
        private const val ARCHIVED_AT_BY = "archivedAtBy"
    }
}

private fun DocumentSnapshot.stringList(field: String): List<String> =
    (get(field) as? List<*>)?.mapNotNull { it as? String }.orEmpty().distinct()

private fun DocumentSnapshot.directContributors(): List<String> =
    stringList("directContributors").ifEmpty { stringList("contributors") }

private fun DocumentSnapshot.inheritedGroupContributors(): List<String> =
    stringList("inheritedGroupContributors")

private fun DocumentSnapshot.ownerId(): String =
    reference.parent.parent?.id.orEmpty()

private fun DocumentSnapshot.groupOwnerId(): String? {
    getString("groupId")?.takeIf { it.isNotBlank() } ?: return null
    return getString("groupOwnerId")?.takeIf { it.isNotBlank() } ?: ownerId()
}

private fun DocumentSnapshot.toNotesListSummary(
    currentUserId: String,
): NotesListSummary {
    val timestamp = getTimestamp("date")
    val ownerId = ownerId()
    val contributors = stringList("contributors")
    val directContributors = directContributors()
    val inheritedGroupContributors = inheritedGroupContributors()
    val groupId = getString("groupId")?.takeIf { it.isNotBlank() }
    val groupOwnerId = groupId?.let {
        getString("groupOwnerId")?.takeIf { ownerId -> ownerId.isNotBlank() } ?: ownerId
    }
    val archivedBy = stringList("archivedBy")
    val archivedAtBy = (get("archivedAtBy") as? Map<*, *>)
        ?.mapNotNull { (key, value) ->
            val userId = key as? String ?: return@mapNotNull null
            val timestamp = value as? Timestamp ?: return@mapNotNull null
            userId to Instant.fromEpochMilliseconds(timestamp.toDate().time)
        }
        ?.toMap()
        .orEmpty()
    return NotesListSummary(
        id = id,
        ownerId = ownerId,
        name = getString("name").orEmpty(),
        creator = getString("creator").orEmpty(),
        createdAt = timestamp?.let {
            Instant.fromEpochMilliseconds(it.toDate().time)
        } ?: Instant.fromEpochMilliseconds(0),
        isOrdered = getBoolean("ordered") ?: false,
        isGroup = getBoolean("isGroup") ?: false,
        groupId = groupId,
        groupOwnerId = groupOwnerId,
        groupOrder = getLong("groupOrder")?.toInt() ?: 0,
        isShared = ownerId != currentUserId || contributors.distinct().size > 1,
        contributors = contributors.distinct(),
        directContributors = directContributors,
        inheritedGroupContributors = inheritedGroupContributors,
        archivedBy = archivedBy.distinct(),
        archivedAtBy = archivedAtBy,
    )
}
