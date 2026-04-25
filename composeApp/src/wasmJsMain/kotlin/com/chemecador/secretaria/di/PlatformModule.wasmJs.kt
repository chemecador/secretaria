package com.chemecador.secretaria.di

import com.chemecador.secretaria.friends.FakeFriendsRepository
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FakeAuthRepository
import com.chemecador.secretaria.messaging.FcmTokenRegister
import com.chemecador.secretaria.messaging.NoopFcmTokenRegister
import com.chemecador.secretaria.notes.FakeNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.noteslists.FakeNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsRepository
import com.chemecador.secretaria.settings.AccountSettingsRepository
import com.chemecador.secretaria.settings.FakeAccountSettingsRepository
import org.koin.core.module.Module
import org.koin.dsl.module

internal actual fun platformModule(): Module = module {
    single<AuthRepository> { FakeAuthRepository() }
    single<NotesListsRepository> { FakeNotesListsRepository() }
    single<NotesRepository> { FakeNotesRepository() }
    single<FriendsRepository> { FakeFriendsRepository() }
    single<AccountSettingsRepository> { FakeAccountSettingsRepository() }
    single<FcmTokenRegister> { NoopFcmTokenRegister() }
}
