package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.FirestoreIosDocument
import com.chemecador.secretaria.firestore.FirestoreIosDocumentPatch
import com.chemecador.secretaria.firestore.FirestorePrecondition
import com.chemecador.secretaria.firestore.arrayContainsFilter
import com.chemecador.secretaria.firestore.collectionQuery
import com.chemecador.secretaria.firestore.firestoreArray
import com.chemecador.secretaria.firestore.firestoreBoolean
import com.chemecador.secretaria.firestore.firestoreInt
import com.chemecador.secretaria.firestore.firestoreInteger
import com.chemecador.secretaria.firestore.firestoreInstant
import com.chemecador.secretaria.firestore.firestoreInstantMap
import com.chemecador.secretaria.firestore.firestoreNull
import com.chemecador.secretaria.firestore.firestoreStringList
import com.chemecador.secretaria.firestore.firestoreString
import com.chemecador.secretaria.firestore.firestoreTimestamp
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.serialization.json.buildJsonObject
import kotlin.time.Clock
import kotlin.time.Instant

internal class FirestoreIosNotesListsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseIosFirestoreRestApi,
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

    override suspend fun createList(
        name: String,
        ordered: Boolean,
        isGroup: Boolean,
    ): Result<NotesListSummary> =
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
                    put(IS_GROUP, firestoreBoolean(isGroup))
                    put(GROUP_ID, firestoreNull())
                    put(GROUP_ORDER, firestoreInteger(0))
                    put(DIRECT_CONTRIBUTORS, firestoreArray(firestoreString(userId)))
                    put(INHERITED_GROUP_CONTRIBUTORS, firestoreArray())
                    put(ARCHIVED_BY, firestoreArray())
                    put(ARCHIVED_AT_BY, firestoreInstantMap(emptyMap()))
                },
            )
            created.toNotesListSummary(userId)
        }

    override suspend fun deleteList(listId: String): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val document = firestore.getDocumentOrNull(listDocumentPath(listId))
                ?: error("List not found")
            if (document.isGroup) {
                val groupDirectContributors = document.fields.directContributors()
                val childPatches = listDocumentsForCurrentUser()
                    .filter { child -> child.fields.firestoreString(GROUP_ID) == listId }
                    .map { child ->
                        child.withClearedGroupPatch(
                            ownerId = userId,
                            inheritedContributorsToRemove = groupDirectContributors,
                        )
                    }
                firestore.commitPatches(childPatches)
                firestore.commitDeletes(listOf(listDocumentPath(listId)))
                return@runCatching
            }
            val notes = firestore.listDocuments(collectionPath = notesCollectionPath(listId))
            firestore.commitDeletes(
                notes.map { note -> "${notesCollectionPath(listId)}/${note.id}" } + listDocumentPath(
                    listId
                ),
            )
        }

    override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val document = firestore.getDocumentOrNull(listDocumentPath(listId))
                ?: error("List not found")
            val directContributors = (document.fields.directContributors() + friendUserId).distinct()
            val patches = mutableListOf(
                document.withDirectContributorsPatch(userId, directContributors),
            )
            if (document.isGroup) {
                patches += listDocumentsForCurrentUser()
                    .filter { child -> child.fields.firestoreString(GROUP_ID) == listId }
                    .map { child ->
                        child.withInheritedContributorPatch(userId, friendUserId, added = true)
                    }
                firestore.commitPatches(patches)
            } else {
                val patch = patches.single()
                firestore.patchDocument(
                    documentPath = listDocumentPath(listId),
                    fields = patch.fields,
                    updateMask = patch.updateMask,
                    currentDocument = document.toPrecondition(),
                )
            }
        }

    override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val document = firestore.getDocumentOrNull(listDocumentPath(listId))
                ?: error("List not found")
            val directContributors = document.fields.directContributors()
                .filterNot { contributorId -> contributorId == friendUserId }
            val patches = mutableListOf(
                document.withDirectContributorsPatch(userId, directContributors),
            )
            if (document.isGroup) {
                patches += listDocumentsForCurrentUser()
                    .filter { child -> child.fields.firestoreString(GROUP_ID) == listId }
                    .map { child ->
                        child.withInheritedContributorPatch(userId, friendUserId, added = false)
                    }
                firestore.commitPatches(patches)
            } else {
                val patch = patches.single()
                firestore.patchDocument(
                    documentPath = listDocumentPath(listId),
                    fields = patch.fields,
                    updateMask = patch.updateMask,
                    currentDocument = document.toPrecondition(),
                )
            }
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
            updated.toNotesListSummary(userId)
        }

    override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> =
        runCatching {
            val userId = requireUserId()
            val document = firestore.getDocumentOrNull(listDocumentPath(listId))
                ?: error("List not found")
            if (document.isGroup) error("A group cannot be added to another group")
            val group = groupId?.let { id ->
                firestore.getDocumentOrNull(listDocumentPath(id))
                    ?.takeIf { candidate -> candidate.isGroup }
                    ?: error("Group not found")
            }
            val inheritedContributors = group?.fields?.directContributors().orEmpty()
            val groupOrder = if (groupId == null) {
                0
            } else {
                listDocumentsForCurrentUser().count { child ->
                    child.fields.firestoreString(GROUP_ID) == groupId && child.id != listId
                }
            }
            firestore.patchDocument(
                documentPath = listDocumentPath(listId),
                fields = buildJsonObject {
                    put(
                        DIRECT_CONTRIBUTORS,
                        firestoreArray(*document.fields.directContributors().map(::firestoreString).toTypedArray()),
                    )
                    put(GROUP_ID, groupId?.let(::firestoreString) ?: firestoreNull())
                    put(GROUP_ORDER, firestoreInteger(groupOrder))
                    put(
                        INHERITED_GROUP_CONTRIBUTORS,
                        firestoreArray(*inheritedContributors.map(::firestoreString).toTypedArray()),
                    )
                    put(
                        CONTRIBUTORS,
                        firestoreArray(
                            *effectiveContributors(
                                ownerId = userId,
                                directContributors = document.fields.directContributors(),
                                inheritedGroupContributors = inheritedContributors,
                            ).map(::firestoreString).toTypedArray(),
                        ),
                    )
                },
                updateMask = listOf(
                    DIRECT_CONTRIBUTORS,
                    GROUP_ID,
                    GROUP_ORDER,
                    INHERITED_GROUP_CONTRIBUTORS,
                    CONTRIBUTORS,
                ),
                currentDocument = document.toPrecondition(),
            )
        }

    override suspend fun reorderGroupedLists(
        groupId: String,
        listIdsInOrder: List<String>,
    ): Result<Unit> =
        runCatching {
            firestore.commitPatches(
                listIdsInOrder.mapIndexed { index, listId ->
                    FirestoreIosDocumentPatch(
                        documentPath = listDocumentPath(listId),
                        fields = buildJsonObject {
                            put(GROUP_ORDER, firestoreInteger(index))
                        },
                        updateMask = listOf(GROUP_ORDER),
                    )
                },
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

    private suspend fun listDocumentsForCurrentUser(): List<FirestoreIosDocument> =
        firestore.listDocuments(collectionPath = listsCollectionPath())

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
        const val DIRECT_CONTRIBUTORS = "directContributors"
        const val INHERITED_GROUP_CONTRIBUTORS = "inheritedGroupContributors"
        const val IS_GROUP = "isGroup"
        const val GROUP_ID = "groupId"
        const val GROUP_ORDER = "groupOrder"
        const val ARCHIVED_BY = "archivedBy"
        const val ARCHIVED_AT_BY = "archivedAtBy"
    }
}

private fun FirestoreIosDocument.toNotesListSummary(
    currentUserId: String,
): NotesListSummary {
    val documentFields = fields
    val ownerId = ownerIdFromDocumentName(name)
    val contributors = documentFields.firestoreStringList("contributors")
    val directContributors = documentFields.directContributors()
    val inheritedGroupContributors = documentFields.inheritedGroupContributors()
    val archivedBy = documentFields.firestoreStringList("archivedBy")
    val archivedAtBy = documentFields.firestoreInstantMap("archivedAtBy")
    return NotesListSummary(
        id = id,
        ownerId = ownerId,
        name = documentFields.firestoreString("name").orEmpty(),
        creator = documentFields.firestoreString("creator").orEmpty(),
        createdAt = documentFields.firestoreInstant("date") ?: Instant.fromEpochMilliseconds(0),
        isOrdered = documentFields.firestoreBoolean("ordered") ?: false,
        isGroup = documentFields.firestoreBoolean("isGroup") ?: false,
        groupId = documentFields.firestoreString("groupId")?.takeIf { it.isNotBlank() },
        groupOrder = documentFields.firestoreInt("groupOrder") ?: 0,
        isShared = ownerId != currentUserId || contributors.distinct().size > 1,
        contributors = contributors.distinct(),
        directContributors = directContributors,
        inheritedGroupContributors = inheritedGroupContributors,
        archivedBy = archivedBy.distinct(),
        archivedAtBy = archivedAtBy,
    )
}

private fun FirestoreIosDocument.toPrecondition(): FirestorePrecondition =
    FirestorePrecondition(updateTime = updateTime ?: error("Missing update time for $name"))

private val FirestoreIosDocument.isGroup: Boolean
    get() = fields.firestoreBoolean("isGroup") == true

private fun kotlinx.serialization.json.JsonObject.directContributors(): List<String> =
    firestoreStringList("directContributors").ifEmpty { firestoreStringList("contributors") }.distinct()

private fun kotlinx.serialization.json.JsonObject.inheritedGroupContributors(): List<String> =
    firestoreStringList("inheritedGroupContributors").distinct()

private fun FirestoreIosDocument.withDirectContributorsPatch(
    ownerId: String,
    directContributors: List<String>,
): FirestoreIosDocumentPatch {
    val inheritedContributors = fields.inheritedGroupContributors()
    val contributors = effectiveContributors(ownerId, directContributors, inheritedContributors)
    return FirestoreIosDocumentPatch(
        documentPath = relativeDocumentPath(),
        fields = buildJsonObject {
            put(
                "directContributors",
                firestoreArray(*directContributors.map(::firestoreString).toTypedArray()),
            )
            put(
                "contributors",
                firestoreArray(*contributors.map(::firestoreString).toTypedArray()),
            )
        },
        updateMask = listOf("directContributors", "contributors"),
    )
}

private fun FirestoreIosDocument.withInheritedContributorPatch(
    ownerId: String,
    friendUserId: String,
    added: Boolean,
): FirestoreIosDocumentPatch {
    val inheritedContributors = if (added) {
        (fields.inheritedGroupContributors() + friendUserId).distinct()
    } else {
        fields.inheritedGroupContributors().filterNot { contributorId ->
            contributorId == friendUserId
        }
    }
    return withInheritedContributorsPatch(ownerId, inheritedContributors)
}

private fun FirestoreIosDocument.withClearedGroupPatch(
    ownerId: String,
    inheritedContributorsToRemove: List<String>,
): FirestoreIosDocumentPatch {
    val inheritedContributors = fields.inheritedGroupContributors().filterNot { contributorId ->
        contributorId in inheritedContributorsToRemove
    }
    val directContributors = fields.directContributors()
    val contributors = effectiveContributors(ownerId, directContributors, inheritedContributors)
    return FirestoreIosDocumentPatch(
        documentPath = relativeDocumentPath(),
        fields = buildJsonObject {
            put(
                "directContributors",
                firestoreArray(*directContributors.map(::firestoreString).toTypedArray()),
            )
            put("groupId", firestoreNull())
            put("groupOrder", firestoreInteger(0))
            put(
                "inheritedGroupContributors",
                firestoreArray(*inheritedContributors.map(::firestoreString).toTypedArray()),
            )
            put(
                "contributors",
                firestoreArray(*contributors.map(::firestoreString).toTypedArray()),
            )
        },
        updateMask = listOf(
            "directContributors",
            "groupId",
            "groupOrder",
            "inheritedGroupContributors",
            "contributors",
        ),
    )
}

private fun FirestoreIosDocument.withInheritedContributorsPatch(
    ownerId: String,
    inheritedContributors: List<String>,
): FirestoreIosDocumentPatch {
    val directContributors = fields.directContributors()
    val contributors = effectiveContributors(ownerId, directContributors, inheritedContributors)
    return FirestoreIosDocumentPatch(
        documentPath = relativeDocumentPath(),
        fields = buildJsonObject {
            put(
                "directContributors",
                firestoreArray(*directContributors.map(::firestoreString).toTypedArray()),
            )
            put(
                "inheritedGroupContributors",
                firestoreArray(*inheritedContributors.map(::firestoreString).toTypedArray()),
            )
            put(
                "contributors",
                firestoreArray(*contributors.map(::firestoreString).toTypedArray()),
            )
        },
        updateMask = listOf("directContributors", "inheritedGroupContributors", "contributors"),
    )
}

private fun FirestoreIosDocument.relativeDocumentPath(): String =
    name.substringAfter("/documents/")
