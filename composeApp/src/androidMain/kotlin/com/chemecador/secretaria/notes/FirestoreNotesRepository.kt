package com.chemecador.secretaria.notes

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

class FirestoreNotesRepository(
    private val authRepository: AuthRepository,
) : NotesRepository {

    private val firestore = FirebaseFirestore.getInstance()

    private fun requireUserId(): String =
        authRepository.currentUserId
            ?: error("User not logged in")

    private fun notesCollection(ownerId: String, listId: String) =
        firestore.collection(USERS).document(ownerId)
            .collection(NOTES_LIST).document(listId)
            .collection(NOTES)

    override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> {
        return try {
            val snapshot = notesCollection(ownerId, listId)
                .orderBy("order", Query.Direction.ASCENDING)
                .get()
                .await()
            val notes = snapshot.documents.map { doc -> doc.toNote() }
            Result.success(notes)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createNote(
        ownerId: String,
        listId: String,
        title: String,
        content: String,
        color: Long,
    ): Result<Note> {
        return try {
            val userId = requireUserId()
            val creator = authRepository.currentUserEmail ?: userId
            val colRef = notesCollection(ownerId, listId)
            val countSnapshot = colRef.get().await()
            val nextOrder = countSnapshot.size()
            val docRef = colRef.document()
            val data = hashMapOf(
                "title" to title,
                "content" to content,
                "date" to FieldValue.serverTimestamp(),
                "completed" to false,
                "order" to nextOrder,
                "creator" to creator,
                "creatorId" to userId,
                "color" to color,
            )
            docRef.set(data).await()
            val created = docRef.get().await()
            Result.success(created.toNote())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteNote(ownerId: String, listId: String, noteId: String): Result<Unit> {
        return try {
            notesCollection(ownerId, listId).document(noteId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reorderNotes(
        ownerId: String,
        listId: String,
        noteIdsInOrder: List<String>,
    ): Result<Unit> {
        return try {
            val batch = firestore.batch()
            noteIdsInOrder.forEachIndexed { index, noteId ->
                batch.update(notesCollection(ownerId, listId).document(noteId), "order", index)
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateNote(
        ownerId: String,
        listId: String,
        noteId: String,
        title: String,
        content: String,
        completed: Boolean,
        color: Long,
    ): Result<Note> {
        return try {
            val docRef = notesCollection(ownerId, listId).document(noteId)
            docRef.update(
                mapOf(
                    "title" to title,
                    "content" to content,
                    "completed" to completed,
                    "color" to color,
                )
            ).await()
            val updated = docRef.get().await()
            Result.success(updated.toNote())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val USERS = "users"
        private const val NOTES_LIST = "noteslist"
        private const val NOTES = "notes"
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toNote(): Note {
    val timestamp = getTimestamp("date")
    val rawColor = getLong("color") ?: DEFAULT_NOTE_COLOR
    return Note(
        id = id,
        title = getString("title").orEmpty(),
        content = getString("content").orEmpty(),
        createdAt = timestamp?.let {
            kotlin.time.Instant.fromEpochMilliseconds(it.toDate().time)
        } ?: kotlin.time.Instant.fromEpochMilliseconds(0),
        completed = getBoolean("completed") ?: false,
        order = getLong("order")?.toInt() ?: 0,
        creator = getString("creator").orEmpty(),
        color = rawColor,
    )
}
