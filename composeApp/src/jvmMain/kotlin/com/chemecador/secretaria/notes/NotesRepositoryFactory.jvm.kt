package com.chemecador.secretaria.notes

import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveFirebaseProjectId
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIdTokenProvider

actual fun createNotesRepository(authRepository: AuthRepository): NotesRepository =
    FirestoreRestNotesRepository(
        authRepository = authRepository,
        firestore = FirebaseFirestoreRestApi(
            projectId = resolveFirebaseProjectId(),
            tokenProvider = authRepository.requireFirebaseIdTokenProvider(),
        ),
    )

private fun AuthRepository.requireFirebaseIdTokenProvider(): FirebaseIdTokenProvider =
    this as? FirebaseIdTokenProvider
        ?: error("JVM Firestore requires FirebaseRestAuthRepository")
