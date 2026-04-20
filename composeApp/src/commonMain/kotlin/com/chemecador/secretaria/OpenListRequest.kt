package com.chemecador.secretaria

data class OpenListRequest(
    val ownerId: String,
    val listId: String,
    val listName: String,
    val isOrdered: Boolean,
)
