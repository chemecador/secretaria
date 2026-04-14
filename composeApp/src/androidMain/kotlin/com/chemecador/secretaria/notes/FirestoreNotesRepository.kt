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

    private fun notesCollection(listId: String) =
        firestore.collection(USERS).document(requireUserId())
            .collection(NOTES_LIST).document(listId)
            .collection(NOTES)

    override suspend fun getNotesForList(listId: String): Result<List<Note>> {
        return try {
            val snapshot = notesCollection(listId)
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
        listId: String,
        title: String,
        content: String,
    ): Result<Note> {
        return try {
            val userId = requireUserId()
            val creator = authRepository.currentUserEmail ?: userId
            val colRef = notesCollection(listId)
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
                "color" to COLOR_DEFAULT,
            )
            docRef.set(data).await()
            val created = docRef.get().await()
            Result.success(created.toNote())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteNote(listId: String, noteId: String): Result<Unit> {
        return try {
            notesCollection(listId).document(noteId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun updateNote(
        listId: String,
        noteId: String,
        title: String,
        content: String,
    ): Result<Note> {
        return try {
            val docRef = notesCollection(listId).document(noteId)
            docRef.update(
                mapOf(
                    "title" to title,
                    "content" to content,
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
        private const val COLOR_DEFAULT = 0xFFFFFFFFL
    }
}

private fun com.google.firebase.firestore.DocumentSnapshot.toNote(): Note {
    val timestamp = getTimestamp("date")
    val rawColor = getLong("color") ?: 0xFFFFFFFFL
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
