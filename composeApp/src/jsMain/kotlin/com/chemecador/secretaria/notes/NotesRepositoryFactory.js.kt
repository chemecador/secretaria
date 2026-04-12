package com.chemecador.secretaria.notes

import com.chemecador.secretaria.firestore.FirebaseJsFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveWebFirebaseProjectId
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseJsIdTokenProvider

actual fun createNotesRepository(authRepository: AuthRepository): NotesRepository =
    FirestoreJsNotesRepository(
        authRepository = authRepository,
        firestore = FirebaseJsFirestoreRestApi(
            projectId = resolveWebFirebaseProjectId(),
            tokenProvider = authRepository.requireFirebaseJsIdTokenProvider(),
        ),
    )

private fun AuthRepository.requireFirebaseJsIdTokenProvider(): FirebaseJsIdTokenProvider =
    this as? FirebaseJsIdTokenProvider
        ?: error("JS Firestore requires FirebaseJsAuthRepository")
