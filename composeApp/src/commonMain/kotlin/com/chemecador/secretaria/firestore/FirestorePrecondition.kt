package com.chemecador.secretaria.firestore

internal data class FirestorePrecondition(
    val exists: Boolean? = null,
    val updateTime: String? = null,
)
