package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseJsFirestoreRestApi
import com.chemecador.secretaria.firestore.firestoreArray
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.serialization.json.buildJsonObject
import kotlin.js.Date
import kotlin.time.Instant

internal class FirestoreJsNotesListsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseJsFirestoreRestApi,
    private val nowProvider: () -> Instant = { Instant.fromEpochMilliseconds(Date.now().toLong()) },
) : NotesListsRepository {

    override suspend fun getLists(): Result<List<NotesListSummary>> =
        runCatching {
            firestore.listDocuments(collectionPath = listsCollectionPath())
                .map { document ->
                    val fields = document.fields
                    NotesListSummary(
                        id = document.id,
                        name = fields.firestoreString("name").orEmpty(),
                        creator = fields.firestoreString("creator").orEmpty(),
                        createdAt = fields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
                        isOrdered = fields.firestoreBoolean("ordered") ?: false,
                    )
                }
        }

    override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> =
        runCatching {
            val userId = requireUserId()
            val creator = authRepository.currentUserEmail ?: userId
            val created = firestore.createDocument(
                parentPath = userDocumentPath(),
                collectionId = NOTES_LIST,
                fields = buildJsonObject {
                    put("name", firestoreString(name))
                    put("contributors", firestoreArray(firestoreString(userId)))
                    put("creator", firestoreString(creator))
                    put("date", firestoreTimestamp(nowProvider()))
                    put("ordered", firestoreBoolean(ordered))
                },
            )
            val fields = created.fields
            NotesListSummary(
                id = created.id,
                name = fields.firestoreString("name").orEmpty(),
                creator = fields.firestoreString("creator").orEmpty(),
                createdAt = fields.firestoreInstant("date") ?: nowProvider(),
                isOrdered = fields.firestoreBoolean("ordered") ?: false,
            )
        }

    override suspend fun deleteList(listId: String): Result<Unit> =
        runCatching {
            val notes = firestore.listDocuments(collectionPath = notesCollectionPath(listId))
            firestore.commitDeletes(
                notes.map { note -> "${notesCollectionPath(listId)}/${note.id}" } + listDocumentPath(listId),
            )
        }

    override suspend fun updateList(
        listId: String,
        name: String,
        ordered: Boolean,
    ): Result<NotesListSummary> =
        runCatching {
            val updated = firestore.patchDocument(
                documentPath = listDocumentPath(listId),
                fields = buildJsonObject {
                    put("name", firestoreString(name))
                    put("ordered", firestoreBoolean(ordered))
                },
                updateMask = listOf("name", "ordered"),
            )
            val fields = updated.fields
            NotesListSummary(
                id = updated.id,
                name = fields.firestoreString("name").orEmpty(),
                creator = fields.firestoreString("creator").orEmpty(),
                createdAt = fields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
                isOrdered = fields.firestoreBoolean("ordered") ?: false,
            )
        }

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")

    private fun userDocumentPath(): String =
        "$USERS/${requireUserId()}"

    private fun listsCollectionPath(): String =
        "${userDocumentPath()}/$NOTES_LIST"

    private fun listDocumentPath(listId: String): String =
        "${listsCollectionPath()}/$listId"

    private fun notesCollectionPath(listId: String): String =
        "${listDocumentPath(listId)}/$NOTES"

    private companion object {
        const val USERS = "users"
        const val NOTES_LIST = "noteslist"
        const val NOTES = "notes"
    }
}
