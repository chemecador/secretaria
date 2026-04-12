package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveFirebaseProjectId
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIdTokenProvider

actual fun createNotesListsRepository(authRepository: AuthRepository): NotesListsRepository =
    FirestoreRestNotesListsRepository(
        authRepository = authRepository,
        firestore = FirebaseFirestoreRestApi(
            projectId = resolveFirebaseProjectId(),
            tokenProvider = authRepository.requireFirebaseIdTokenProvider(),
        ),
    )

private fun AuthRepository.requireFirebaseIdTokenProvider(): FirebaseIdTokenProvider =
    this as? FirebaseIdTokenProvider
        ?: error("JVM Firestore requires FirebaseRestAuthRepository")
