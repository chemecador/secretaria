package com.chemecador.secretaria.notes

import com.chemecador.secretaria.firestore.FirebaseJsFirestoreRestApi
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInt
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreInteger
import com.chemecador.secretaria.firestore.firestoreLong
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.serialization.json.buildJsonObject
import kotlin.js.Date
import kotlin.time.Instant

internal class FirestoreJsNotesRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseJsFirestoreRestApi,
    private val nowProvider: () -> Instant = { Instant.fromEpochMilliseconds(Date.now().toLong()) },
) : NotesRepository {

    override suspend fun getNotesForList(listId: String): Result<List<Note>> =
        runCatching {
            firestore.listDocuments(
                collectionPath = notesCollectionPath(listId),
                orderBy = "order",
            ).map { document ->
                val fields = document.fields
                Note(
                    id = document.id,
                    title = fields.firestoreString("title").orEmpty(),
                    content = fields.firestoreString("content").orEmpty(),
                    createdAt = fields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
                    completed = fields.firestoreBoolean("completed") ?: false,
                    order = fields.firestoreInt("order") ?: 0,
                    creator = fields.firestoreString("creator").orEmpty(),
                    color = fields.firestoreLong("color") ?: COLOR_DEFAULT,
                )
            }
        }

    override suspend fun createNote(
        listId: String,
        title: String,
        content: String,
    ): Result<Note> =
        runCatching {
            val userId = requireUserId()
            val nextOrder = firestore.listDocuments(collectionPath = notesCollectionPath(listId)).size
            val created = firestore.createDocument(
                parentPath = listDocumentPath(listId),
                collectionId = NOTES,
                fields = buildJsonObject {
                    put("title", firestoreString(title))
                    put("content", firestoreString(content))
                    put("date", firestoreTimestamp(nowProvider()))
                    put("completed", firestoreBoolean(false))
                    put("order", firestoreInteger(nextOrder))
                    put("creator", firestoreString(userId))
                    put("color", firestoreLong(COLOR_DEFAULT))
                },
            )
            val fields = created.fields
            Note(
                id = created.id,
                title = fields.firestoreString("title").orEmpty(),
                content = fields.firestoreString("content").orEmpty(),
                createdAt = fields.firestoreInstant("date") ?: nowProvider(),
                completed = fields.firestoreBoolean("completed") ?: false,
                order = fields.firestoreInt("order") ?: nextOrder,
                creator = fields.firestoreString("creator").orEmpty(),
                color = fields.firestoreLong("color") ?: COLOR_DEFAULT,
            )
        }

    override suspend fun deleteNote(listId: String, noteId: String): Result<Unit> =
        runCatching {
            firestore.deleteDocument(noteDocumentPath(listId, noteId))
        }

    override suspend fun updateNote(
        listId: String,
        noteId: String,
        title: String,
        content: String,
    ): Result<Note> =
        runCatching {
            val updated = firestore.patchDocument(
                documentPath = noteDocumentPath(listId, noteId),
                fields = buildJsonObject {
                    put("title", firestoreString(title))
                    put("content", firestoreString(content))
                },
                updateMask = listOf("title", "content"),
            )
            val fields = updated.fields
            Note(
                id = updated.id,
                title = fields.firestoreString("title").orEmpty(),
                content = fields.firestoreString("content").orEmpty(),
                createdAt = fields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
                completed = fields.firestoreBoolean("completed") ?: false,
                order = fields.firestoreInt("order") ?: 0,
                creator = fields.firestoreString("creator").orEmpty(),
                color = fields.firestoreLong("color") ?: COLOR_DEFAULT,
            )
        }

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")

    private fun userDocumentPath(): String =
        "$USERS/${requireUserId()}"

    private fun listDocumentPath(listId: String): String =
        "${userDocumentPath()}/$NOTES_LIST/$listId"

    private fun notesCollectionPath(listId: String): String =
        "${listDocumentPath(listId)}/$NOTES"

    private fun noteDocumentPath(listId: String, noteId: String): String =
        "${notesCollectionPath(listId)}/$noteId"

    private companion object {
        const val USERS = "users"
        const val NOTES_LIST = "noteslist"
        const val NOTES = "notes"
        const val COLOR_DEFAULT = 0xFFFFFFFFL
    }
}
