package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveIosFirebaseProjectId
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIosIdTokenProvider

actual fun createNotesListsRepository(authRepository: AuthRepository): NotesListsRepository =
    FirestoreIosNotesListsRepository(
        authRepository = authRepository,
        firestore = FirebaseIosFirestoreRestApi(
            projectId = resolveIosFirebaseProjectId(),
            tokenProvider = authRepository.requireFirebaseIosIdTokenProvider(),
        ),
    )

private fun AuthRepository.requireFirebaseIosIdTokenProvider(): FirebaseIosIdTokenProvider =
    this as? FirebaseIosIdTokenProvider
        ?: error("iOS Firestore requires FirebaseIosAuthRepository")
