package com.chemecador.secretaria.di

import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveFirebaseProjectId
import com.chemecador.secretaria.friends.FirestoreRestFriendsRepository
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FileSessionStore
import com.chemecador.secretaria.login.FirebaseIdTokenProvider
import com.chemecador.secretaria.login.FirebaseRestAuthRepository
import com.chemecador.secretaria.login.SessionStore
import com.chemecador.secretaria.login.resolveFirebaseApiKey
import com.chemecador.secretaria.notes.FirestoreRestNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.noteslists.FirestoreRestNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformModule(): Module = module {
    single<SessionStore> { FileSessionStore() }
    single<AuthRepository> {
        FirebaseRestAuthRepository(
            apiKey = resolveFirebaseApiKey(),
            sessionStore = get(),
        )
    }
    single<NotesListsRepository> {
        val authRepository: AuthRepository = get()
        FirestoreRestNotesListsRepository(
            authRepository = authRepository,
            firestore = FirebaseFirestoreRestApi(
                projectId = resolveFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseIdTokenProvider(),
            ),
        )
    }
    single<NotesRepository> {
        val authRepository: AuthRepository = get()
        FirestoreRestNotesRepository(
            authRepository = authRepository,
            firestore = FirebaseFirestoreRestApi(
                projectId = resolveFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseIdTokenProvider(),
            ),
        )
    }
    single<FriendsRepository> {
        val authRepository: AuthRepository = get()
        FirestoreRestFriendsRepository(
            authRepository = authRepository,
            firestore = FirebaseFirestoreRestApi(
                projectId = resolveFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseIdTokenProvider(),
            ),
        )
    }
}

private fun AuthRepository.requireFirebaseIdTokenProvider(): FirebaseIdTokenProvider =
    this as? FirebaseIdTokenProvider
        ?: error("JVM Firestore requires FirebaseRestAuthRepository")
