package com.chemecador.secretaria.noteslists

data class ListCollaborator(
    val userId: String,
    val name: String,
    val isResolvedName: Boolean = true,
)
