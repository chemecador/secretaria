package com.chemecador.secretaria.reminders

internal fun reminderOwnerIdFromDocumentName(documentName: String): String =
    documentName.substringAfter("/documents/users/").substringBefore("/reminders/")
