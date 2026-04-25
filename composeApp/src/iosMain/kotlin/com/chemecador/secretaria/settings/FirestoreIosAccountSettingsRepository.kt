package com.chemecador.secretaria.settings

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.firestoreLong
import com.chemecador.secretaria.login.AuthRepository
import kotlinx.serialization.json.buildJsonObject

internal class FirestoreIosAccountSettingsRepository(
    private val authRepository: AuthRepository,
    private val firestore: FirebaseIosFirestoreRestApi,
) : AccountSettingsRepository {

    override suspend fun getDefaultNoteColor(): Result<Long> =
        runCatching {
            val storedColor = firestore.getDocumentOrNull(userDocumentPath())
                ?.fields
                ?.firestoreLong(AccountSettingsFirestoreSchema.DEFAULT_NOTE_COLOR)
            if (storedColor != null) {
                storedColor
            } else {
                val color = randomDefaultNoteColor()
                setDefaultNoteColor(color).getOrThrow()
                color
            }
        }

    override suspend fun setDefaultNoteColor(color: Long): Result<Unit> =
        runCatching {
            firestore.patchDocument(
                documentPath = userDocumentPath(),
                fields = buildJsonObject {
                    put(AccountSettingsFirestoreSchema.DEFAULT_NOTE_COLOR, firestoreLong(color))
                },
                updateMask = listOf(AccountSettingsFirestoreSchema.DEFAULT_NOTE_COLOR),
            )
        }

    private fun userDocumentPath(): String =
        "${AccountSettingsFirestoreSchema.USERS}/${requireUserId()}"

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")
}
