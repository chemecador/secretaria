package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.firestore.FirebaseJsFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveWebFirebaseProjectId
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseJsIdTokenProvider

actual fun createNotesListsRepository(authRepository: AuthRepository): NotesListsRepository =
    FirestoreJsNotesListsRepository(
        authRepository = authRepository,
        firestore = FirebaseJsFirestoreRestApi(
            projectId = resolveWebFirebaseProjectId(),
            tokenProvider = authRepository.requireFirebaseJsIdTokenProvider(),
        ),
    )

private fun AuthRepository.requireFirebaseJsIdTokenProvider(): FirebaseJsIdTokenProvider =
    this as? FirebaseJsIdTokenProvider
        ?: error("JS Firestore requires FirebaseJsAuthRepository")
