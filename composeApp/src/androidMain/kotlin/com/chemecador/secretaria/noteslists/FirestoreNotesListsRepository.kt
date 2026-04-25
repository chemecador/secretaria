package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.Timestamp
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

    override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> {
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
            docRef.update(CONTRIBUTORS, FieldValue.arrayUnion(friendUserId)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val docRef = listDocument(userId, listId)
            docRef.update(CONTRIBUTORS, FieldValue.arrayRemove(friendUserId)).await()
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
        private const val ARCHIVED_BY = "archivedBy"
        private const val ARCHIVED_AT_BY = "archivedAtBy"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toNotesListSummary(
    currentUserId: String,
): NotesListSummary {
    val timestamp = getTimestamp("date")
    val ownerId = reference.parent.parent?.id.orEmpty()
    val contributors = (get("contributors") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
    val archivedBy = (get("archivedBy") as? List<*>)?.mapNotNull { it as? String }.orEmpty()
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
        isShared = ownerId != currentUserId || contributors.distinct().size > 1,
        contributors = contributors.distinct(),
        archivedBy = archivedBy.distinct(),
        archivedAtBy = archivedAtBy,
    )
}
