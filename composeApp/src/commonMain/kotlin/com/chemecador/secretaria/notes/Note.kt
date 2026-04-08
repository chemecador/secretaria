package com.chemecador.secretaria.notes

import kotlin.time.Instant

data class Note(
    val id: String,
    val title: String,
    val content: String,
    val createdAt: Instant,
    val completed: Boolean = false,
    val order: Int = 0,
    val creator: String,
    val color: Long = 0xFFFFFFFFL,
)
