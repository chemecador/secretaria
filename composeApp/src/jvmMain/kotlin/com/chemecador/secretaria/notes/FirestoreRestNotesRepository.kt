package com.chemecador.secretaria.notes

import com.chemecador.secretaria.firestore.FirestoreDocumentPatch
import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInt
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreInteger
import com.chemecador.secretaria.firestore.firestoreLong
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant

internal class FirestoreRestNotesRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestoreRestApi,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : NotesRepository {

    override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> =
        runCatching {
            firestore.listDocuments(
                collectionPath = notesCollectionPath(ownerId, listId),
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
        ownerId: String,
        listId: String,
        title: String,
        content: String,
    ): Result<Note> =
        runCatching {
            val userId = requireUserId()
            val creator = authRepository.currentUserEmail ?: userId
            val nextOrder =
                firestore.listDocuments(collectionPath = notesCollectionPath(ownerId, listId)).size
            val created = firestore.createDocument(
                parentPath = listDocumentPath(ownerId, listId),
                collectionId = NOTES,
                fields = buildJsonObject {
                    put("title", firestoreString(title))
                    put("content", firestoreString(content))
                    put("date", firestoreTimestamp(nowProvider()))
                    put("completed", firestoreBoolean(false))
                    put("order", firestoreInteger(nextOrder))
                    put("creator", firestoreString(creator))
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

    override suspend fun deleteNote(ownerId: String, listId: String, noteId: String): Result<Unit> =
        runCatching {
            firestore.deleteDocument(noteDocumentPath(ownerId, listId, noteId))
        }

    override suspend fun reorderNotes(
        ownerId: String,
        listId: String,
        noteIdsInOrder: List<String>,
    ): Result<Unit> =
        runCatching {
            firestore.commitPatches(
                noteIdsInOrder.mapIndexed { index, noteId ->
                    FirestoreDocumentPatch(
                        documentPath = noteDocumentPath(ownerId, listId, noteId),
                        fields = buildJsonObject {
                            put("order", firestoreInteger(index))
                        },
                        updateMask = listOf("order"),
                    )
                },
            )
        }

    override suspend fun updateNote(
        ownerId: String,
        listId: String,
        noteId: String,
        title: String,
        content: String,
        completed: Boolean,
        color: Long,
    ): Result<Note> =
        runCatching {
            val updated = firestore.patchDocument(
                documentPath = noteDocumentPath(ownerId, listId, noteId),
                fields = buildJsonObject {
                    put("title", firestoreString(title))
                    put("content", firestoreString(content))
                    put("completed", firestoreBoolean(completed))
                    put("color", firestoreLong(color))
                },
                updateMask = listOf("title", "content", "completed", "color"),
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

    private fun userDocumentPath(ownerId: String): String =
        "$USERS/$ownerId"

    private fun listDocumentPath(ownerId: String, listId: String): String =
        "${userDocumentPath(ownerId)}/$NOTES_LIST/$listId"

    private fun notesCollectionPath(ownerId: String, listId: String): String =
        "${listDocumentPath(ownerId, listId)}/$NOTES"

    private fun noteDocumentPath(ownerId: String, listId: String, noteId: String): String =
        "${notesCollectionPath(ownerId, listId)}/$noteId"

    private companion object {
        const val USERS = "users"
        const val NOTES_LIST = "noteslist"
        const val NOTES = "notes"
        const val COLOR_DEFAULT = 0xFFFFFFFFL
    }
}
