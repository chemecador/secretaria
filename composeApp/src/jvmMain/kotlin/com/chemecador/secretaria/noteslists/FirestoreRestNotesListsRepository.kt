package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.FirestoreDocument
import com.chemecador.secretaria.firestore.FirestorePrecondition
import com.chemecador.secretaria.firestore.arrayContainsFilter
import com.chemecador.secretaria.firestore.collectionQuery
import com.chemecador.secretaria.firestore.firestoreArray
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreInstantMap
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
                    put(ARCHIVED_BY, firestoreArray())
                    put(ARCHIVED_AT_BY, firestoreInstantMap(emptyMap()))
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
                contributors = fields.firestoreStringList(CONTRIBUTORS).ifEmpty { listOf(userId) },
                archivedBy = fields.firestoreStringList(ARCHIVED_BY).distinct(),
                archivedAtBy = fields.firestoreInstantMap(ARCHIVED_AT_BY),
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

    override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
        runCatching {
            val document = firestore.getDocumentOrNull(listDocumentPath(listId))
                ?: error("List not found")
            val contributors = document.fields.firestoreStringList(CONTRIBUTORS)
                .filterNot { contributorId -> contributorId == friendUserId }
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
                contributors = fields.firestoreStringList(CONTRIBUTORS).distinct(),
                archivedBy = fields.firestoreStringList(ARCHIVED_BY).distinct(),
                archivedAtBy = fields.firestoreInstantMap(ARCHIVED_AT_BY),
            )
        }

    override suspend fun setListArchived(
        ownerId: String,
        listId: String,
        archived: Boolean,
    ): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val documentPath = listDocumentPath(ownerId, listId)
            val document = firestore.getDocumentOrNull(documentPath)
                ?: error("List not found")
            val archivedBy = if (archived) {
                (document.fields.firestoreStringList(ARCHIVED_BY) + userId).distinct()
            } else {
                document.fields.firestoreStringList(ARCHIVED_BY)
                    .filterNot { archivedUserId -> archivedUserId == userId }
            }
            val archivedAtBy = if (archived) {
                document.fields.firestoreInstantMap(ARCHIVED_AT_BY) + (userId to nowProvider())
            } else {
                document.fields.firestoreInstantMap(ARCHIVED_AT_BY) - userId
            }
            firestore.patchDocument(
                documentPath = documentPath,
                fields = buildJsonObject {
                    put(
                        ARCHIVED_BY,
                        firestoreArray(*archivedBy.map(::firestoreString).toTypedArray()),
                    )
                    put(ARCHIVED_AT_BY, firestoreInstantMap(archivedAtBy))
                },
                updateMask = listOf(ARCHIVED_BY, ARCHIVED_AT_BY),
                currentDocument = document.toPrecondition(),
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

    private fun listDocumentPath(ownerId: String, listId: String): String =
        "$USERS/$ownerId/$NOTES_LIST/$listId"

    private fun notesCollectionPath(listId: String): String =
        "${listDocumentPath(listId)}/$NOTES"

    private companion object {
        const val USERS = "users"
        const val NOTES_LIST = "noteslist"
        const val NOTES = "notes"
        const val CONTRIBUTORS = "contributors"
        const val ARCHIVED_BY = "archivedBy"
        const val ARCHIVED_AT_BY = "archivedAtBy"
    }
}

private fun FirestoreDocument.toNotesListSummary(
    currentUserId: String,
): NotesListSummary {
    val documentFields = fields
    val ownerId = ownerIdFromDocumentName(name)
    val contributors = documentFields.firestoreStringList("contributors")
    val archivedBy = documentFields.firestoreStringList("archivedBy")
    val archivedAtBy = documentFields.firestoreInstantMap("archivedAtBy")
    return NotesListSummary(
        id = id,
        ownerId = ownerId,
        name = documentFields.firestoreString("name").orEmpty(),
        creator = documentFields.firestoreString("creator").orEmpty(),
        createdAt = documentFields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
        isOrdered = documentFields.firestoreBoolean("ordered") ?: false,
        isShared = ownerId != currentUserId || contributors.distinct().size > 1,
        contributors = contributors.distinct(),
        archivedBy = archivedBy.distinct(),
        archivedAtBy = archivedAtBy,
    )
}

private fun FirestoreDocument.toPrecondition(): FirestorePrecondition =
    FirestorePrecondition(updateTime = updateTime ?: error("Missing update time for $name"))
