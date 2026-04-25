package com.chemecador.secretaria.noteslists

import kotlin.time.Instant

data class NotesListSummary(
    val id: String,
    val ownerId: String,
    val name: String,
    val creator: String,
    val createdAt: Instant,
    val isOrdered: Boolean,
    val isGroup: Boolean = false,
    val groupId: String? = null,
    val groupOwnerId: String? = null,
    val groupOrder: Int = 0,
    val isShared: Boolean = false,
    val contributors: List<String> = emptyList(),
    val directContributors: List<String> = contributors,
    val inheritedGroupContributors: List<String> = emptyList(),
    val archivedBy: List<String> = emptyList(),
    val archivedAtBy: Map<String, Instant> = emptyMap(),
)

data class NotesListKey(
    val ownerId: String,
    val listId: String,
)

val NotesListSummary.key: NotesListKey
    get() = NotesListKey(ownerId, id)

val NotesListSummary.groupKey: NotesListKey?
    get() = groupId?.let { listId ->
        NotesListKey(
            ownerId = groupOwnerId ?: ownerId,
            listId = listId,
        )
    }

val NotesListSummary.directSharedWithUserIds: List<String>
    get() = directContributors
        .distinct()
        .filterNot { contributorId -> contributorId == ownerId }

fun effectiveContributors(
    ownerId: String,
    directContributors: List<String>,
    inheritedGroupContributors: List<String>,
): List<String> =
    (listOf(ownerId) + directContributors + inheritedGroupContributors)
        .filter { contributorId -> contributorId.isNotBlank() }
        .distinct()
