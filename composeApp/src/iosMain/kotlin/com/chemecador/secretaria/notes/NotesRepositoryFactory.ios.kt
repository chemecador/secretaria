package com.chemecador.secretaria.notes

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveIosFirebaseProjectId
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIosIdTokenProvider

actual fun createNotesRepository(authRepository: AuthRepository): NotesRepository =
    FirestoreIosNotesRepository(
        authRepository = authRepository,
        firestore = FirebaseIosFirestoreRestApi(
            projectId = resolveIosFirebaseProjectId(),
            tokenProvider = authRepository.requireFirebaseIosIdTokenProvider(),
        ),
    )

private fun AuthRepository.requireFirebaseIosIdTokenProvider(): FirebaseIosIdTokenProvider =
    this as? FirebaseIosIdTokenProvider
        ?: error("iOS Firestore requires FirebaseIosAuthRepository")
