package com.chemecador.secretaria.di

import com.chemecador.secretaria.firestore.FirebaseJsFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveWebFirebaseProjectId
import com.chemecador.secretaria.friends.FirestoreJsFriendsRepository
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseJsAuthRepository
import com.chemecador.secretaria.login.FirebaseJsIdTokenProvider
import com.chemecador.secretaria.login.resolveWebFirebaseApiKey
import com.chemecador.secretaria.notes.FirestoreJsNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.noteslists.FirestoreJsNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformModule(): Module = module {
    single<AuthRepository> {
        FirebaseJsAuthRepository(apiKey = resolveWebFirebaseApiKey())
    }
    single<NotesListsRepository> {
        val authRepository: AuthRepository = get()
        FirestoreJsNotesListsRepository(
            authRepository = authRepository,
            firestore = FirebaseJsFirestoreRestApi(
                projectId = resolveWebFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseJsIdTokenProvider(),
            ),
        )
    }
    single<NotesRepository> {
        val authRepository: AuthRepository = get()
        FirestoreJsNotesRepository(
            authRepository = authRepository,
            firestore = FirebaseJsFirestoreRestApi(
                projectId = resolveWebFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseJsIdTokenProvider(),
            ),
        )
    }
    single<FriendsRepository> {
        val authRepository: AuthRepository = get()
        FirestoreJsFriendsRepository(
            authRepository = authRepository,
            firestore = FirebaseJsFirestoreRestApi(
                projectId = resolveWebFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseJsIdTokenProvider(),
            ),
        )
    }
}

private fun AuthRepository.requireFirebaseJsIdTokenProvider(): FirebaseJsIdTokenProvider =
    this as? FirebaseJsIdTokenProvider
        ?: error("JS Firestore requires FirebaseJsAuthRepository")
