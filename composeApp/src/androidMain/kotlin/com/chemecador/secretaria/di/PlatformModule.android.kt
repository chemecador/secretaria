package com.chemecador.secretaria.di

import com.chemecador.secretaria.friends.FirestoreFriendsRepository
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseAuthRepository
import com.chemecador.secretaria.notes.FirestoreNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.noteslists.FirestoreNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformModule(): Module = module {
    single<AuthRepository> { FirebaseAuthRepository() }
    single<NotesListsRepository> { FirestoreNotesListsRepository(get()) }
    single<NotesRepository> { FirestoreNotesRepository(get()) }
    single<FriendsRepository> { FirestoreFriendsRepository(get()) }
}
