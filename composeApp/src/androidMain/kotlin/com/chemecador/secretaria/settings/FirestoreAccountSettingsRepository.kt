package com.chemecador.secretaria.settings

import com.chemecador.secretaria.login.AuthRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.tasks.await

internal class FirestoreAccountSettingsRepository(
    private val authRepository: AuthRepository,
) : AccountSettingsRepository {

    private val firestore = FirebaseFirestore.getInstance()

    override suspend fun getDefaultNoteColor(): Result<Long> =
        runCatching {
            val userDocument = userDocument()
            val document = userDocument.get().await()
            val storedColor = document.getLong(AccountSettingsFirestoreSchema.DEFAULT_NOTE_COLOR)
            if (storedColor != null) {
                storedColor
            } else {
                val color = randomDefaultNoteColor()
                userDocument
                    .set(
                        mapOf(AccountSettingsFirestoreSchema.DEFAULT_NOTE_COLOR to color),
                        SetOptions.merge(),
                    )
                    .await()
                color
            }
        }

    override suspend fun setDefaultNoteColor(color: Long): Result<Unit> =
        runCatching {
            userDocument()
                .set(
                    mapOf(AccountSettingsFirestoreSchema.DEFAULT_NOTE_COLOR to color),
                    SetOptions.merge(),
                )
                .await()
        }

    private fun userDocument() =
        firestore.collection(AccountSettingsFirestoreSchema.USERS)
            .document(requireUserId())

    private fun requireUserId(): String =
        authRepository.currentUserId ?: error("User not logged in")
}
