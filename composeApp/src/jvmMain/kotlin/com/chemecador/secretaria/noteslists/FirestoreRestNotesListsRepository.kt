package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.FirestoreDocument
import com.chemecador.secretaria.firestore.FirestorePrecondition
import com.chemecador.secretaria.firestore.arrayContainsFilter
import com.chemecador.secretaria.firestore.collectionQuery
import com.chemecador.secretaria.firestore.firestoreArray
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreStringList
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant

internal class FirestoreRestNotesListsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseFirestoreRestApi,
    private val nowProvider: () -> Instant = { Clock.System.now() },
) : NotesListsRepository {

    override suspend fun getLists(): Result<List<NotesListSummary>> =
        runCatching {
            val userId = requireUserId()
            firestore.runQuery(
                structuredQuery = collectionQuery(
                    collectionId = NOTES_LIST,
                    arrayContainsFilter(CONTRIBUTORS, firestoreString(userId)),
                    allDescendants = true,
                ),
            ).map { document ->
                document.toNotesListSummary(userId)
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
                ownerId = ownerIdFromDocumentName(created.name),
                name = fields.firestoreString("name").orEmpty(),
                creator = fields.firestoreString("creator").orEmpty(),
                createdAt = fields.firestoreInstant("date") ?: nowProvider(),
                isOrdered = fields.firestoreBoolean("ordered") ?: false,
                isShared = false,
            )
        }

    override suspend fun deleteList(listId: String): Result<Unit> =
        runCatching {
            val notes = firestore.listDocuments(collectionPath = notesCollectionPath(listId))
            firestore.commitDeletes(
                notes.map { note -> "${notesCollectionPath(listId)}/${note.id}" } + listDocumentPath(listId),
            )
        }

    override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
        runCatching {
            val document = firestore.getDocumentOrNull(listDocumentPath(listId))
                ?: error("List not found")
            val contributors = (document.fields.firestoreStringList(CONTRIBUTORS) + friendUserId)
                .distinct()
            firestore.patchDocument(
                documentPath = listDocumentPath(listId),
                fields = buildJsonObject {
                    put(
                        CONTRIBUTORS,
                        firestoreArray(*contributors.map(::firestoreString).toTypedArray()),
                    )
                },
                updateMask = listOf(CONTRIBUTORS),
                currentDocument = document.toPrecondition(),
            )
        }

    override suspend fun updateList(
        listId: String,
        name: String,
        ordered: Boolean,
    ): Result<NotesListSummary> =
        runCatching {
            val userId = requireUserId()
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
                ownerId = ownerIdFromDocumentName(updated.name),
                name = fields.firestoreString("name").orEmpty(),
                creator = fields.firestoreString("creator").orEmpty(),
                createdAt = fields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
                isOrdered = fields.firestoreBoolean("ordered") ?: false,
                isShared = ownerIdFromDocumentName(updated.name) != userId ||
                    fields.firestoreStringList("contributors").distinct().size > 1,
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
        const val CONTRIBUTORS = "contributors"
    }
}

private fun FirestoreDocument.toNotesListSummary(
    currentUserId: String,
): NotesListSummary {
    val documentFields = fields
    val ownerId = ownerIdFromDocumentName(name)
    val contributors = documentFields.firestoreStringList("contributors")
    return NotesListSummary(
        id = id,
        ownerId = ownerId,
        name = documentFields.firestoreString("name").orEmpty(),
        creator = documentFields.firestoreString("creator").orEmpty(),
        createdAt = documentFields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
        isOrdered = documentFields.firestoreBoolean("ordered") ?: false,
        isShared = ownerId != currentUserId || contributors.distinct().size > 1,
    )
}

private fun FirestoreDocument.toPrecondition(): FirestorePrecondition =
    FirestorePrecondition(updateTime = updateTime ?: error("Missing update time for $name"))
