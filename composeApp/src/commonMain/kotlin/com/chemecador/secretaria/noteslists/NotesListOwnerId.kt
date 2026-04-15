package com.chemecador.secretaria.noteslists

internal fun ownerIdFromDocumentName(documentName: String): String =
    documentName.substringAfter("/documents/users/").substringBefore("/noteslist/")
