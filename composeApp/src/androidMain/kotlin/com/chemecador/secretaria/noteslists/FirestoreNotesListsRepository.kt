package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

class FirestoreNotesListsRepository(
    private val authRepository: AuthRepository,
) : NotesListsRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun requireUserId(): String =
        authRepository.currentUserId
            ?: error("User not logged in")

    override suspend fun getLists(): Result<List<NotesListSummary>> {
        return try {
            val userId = requireUserId()
            val snapshot = firestore.collectionGroup(NOTES_LIST)
                .whereArrayContains(CONTRIBUTORS, userId)
                .get()
                .await()
            val lists = snapshot.documents.map { doc ->
                doc.toNotesListSummary()
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
            )
            docRef.set(data).await()
            val created = docRef.get().await()
            Result.success(created.toNotesListSummary())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteList(listId: String): Result<Unit> {
        return try {
            val userId = requireUserId()
            val listRef = firestore.collection(USERS).document(userId)
                .collection(NOTES_LIST).document(listId)
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
            val docRef = firestore.collection(USERS).document(userId)
                .collection(NOTES_LIST).document(listId)
            docRef.update(
                mapOf(
                    "name" to name,
                    "ordered" to ordered,
                )
            ).await()
            val updated = docRef.get().await()
            Result.success(updated.toNotesListSummary())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val USERS = "users"
        private const val NOTES_LIST = "noteslist"
        private const val NOTES = "notes"
        private const val CONTRIBUTORS = "contributors"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toNotesListSummary(): NotesListSummary {
    val timestamp = getTimestamp("date")
    return NotesListSummary(
        id = id,
        name = getString("name").orEmpty(),
        creator = getString("creator").orEmpty(),
        createdAt = timestamp?.let {
            kotlin.time.Instant.fromEpochMilliseconds(it.toDate().time)
        } ?: kotlin.time.Instant.fromEpochMilliseconds(0),
        isOrdered = getBoolean("ordered") ?: false,
    )
}
