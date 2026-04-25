package com.chemecador.secretaria.di

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.resolveIosFirebaseProjectId
import com.chemecador.secretaria.friends.FirestoreIosFriendsRepository
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIosAuthRepository
import com.chemecador.secretaria.login.FirebaseIosIdTokenProvider
import com.chemecador.secretaria.login.IosSessionStore
import com.chemecador.secretaria.login.SessionStore
import com.chemecador.secretaria.login.resolveIosFirebaseApiKey
import com.chemecador.secretaria.messaging.FcmTokenRegister
import com.chemecador.secretaria.messaging.NoopFcmTokenRegister
import com.chemecador.secretaria.notes.FirestoreIosNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.noteslists.FirestoreIosNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsRepository
import com.chemecador.secretaria.settings.AccountSettingsRepository
import com.chemecador.secretaria.settings.FirestoreIosAccountSettingsRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformModule(): Module = module {
    single<SessionStore> { IosSessionStore() }
    single<FcmTokenRegister> { NoopFcmTokenRegister() }
    single<AuthRepository> {
        FirebaseIosAuthRepository(
            apiKey = resolveIosFirebaseApiKey(),
            sessionStore = get(),
        )
    }
    single<NotesListsRepository> {
        val authRepository: AuthRepository = get()
        FirestoreIosNotesListsRepository(
            authRepository = authRepository,
            firestore = FirebaseIosFirestoreRestApi(
                projectId = resolveIosFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseIosIdTokenProvider(),
            ),
        )
    }
    single<NotesRepository> {
        val authRepository: AuthRepository = get()
        FirestoreIosNotesRepository(
            authRepository = authRepository,
            firestore = FirebaseIosFirestoreRestApi(
                projectId = resolveIosFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseIosIdTokenProvider(),
            ),
        )
    }
    single<FriendsRepository> {
        val authRepository: AuthRepository = get()
        FirestoreIosFriendsRepository(
            authRepository = authRepository,
            firestore = FirebaseIosFirestoreRestApi(
                projectId = resolveIosFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseIosIdTokenProvider(),
            ),
        )
    }
    single<AccountSettingsRepository> {
        val authRepository: AuthRepository = get()
        FirestoreIosAccountSettingsRepository(
            authRepository = authRepository,
            firestore = FirebaseIosFirestoreRestApi(
                projectId = resolveIosFirebaseProjectId(),
                tokenProvider = authRepository.requireFirebaseIosIdTokenProvider(),
            ),
        )
    }
}

private fun AuthRepository.requireFirebaseIosIdTokenProvider(): FirebaseIosIdTokenProvider =
    this as? FirebaseIosIdTokenProvider
        ?: error("iOS Firestore requires FirebaseIosAuthRepository")
