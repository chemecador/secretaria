package com.chemecador.secretaria.noteslists

import kotlin.time.Instant

data class NotesListSummary(
    val id: String,
    val ownerId: String,
    val name: String,
    val creator: String,
    val createdAt: Instant,
    val isOrdered: Boolean,
    val isShared: Boolean = false,
    val contributors: List<String> = emptyList(),
    val archivedBy: List<String> = emptyList(),
    val archivedAtBy: Map<String, Instant> = emptyMap(),
)

val NotesListSummary.sharedWithUserIds: List<String>
    get() = contributors
        .distinct()
        .filterNot { contributorId -> contributorId == ownerId }
