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
)
