package com.chemecador.secretaria.di

import com.chemecador.secretaria.friends.FakeFriendsRepository
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.friends.FriendsViewModel
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FakeAuthRepository
import com.chemecador.secretaria.login.LoginViewModel
import com.chemecador.secretaria.messaging.FcmTokenRegister
import com.chemecador.secretaria.messaging.NoopFcmTokenRegister
import com.chemecador.secretaria.notes.FakeNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.noteslists.FakeNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsViewModel
import com.chemecador.secretaria.settings.AccountSettingsRepository
import com.chemecador.secretaria.settings.FakeAccountSettingsRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val sharedAppModule = module {
    viewModel { LoginViewModel(get()) }
    viewModel { NotesListsViewModel(get(), get(), get()) }
    viewModel { FriendsViewModel(get()) }
    viewModel { params ->
        NotesViewModel(
            repository = get(),
            accountSettingsRepository = get(),
            ownerId = params.get(),
            listId = params.get(),
        )
    }
}

internal fun appModules(
    platformModule: Module = platformModule(),
): List<Module> = listOf(sharedAppModule, platformModule)

internal fun previewAppModules(): List<Module> = appModules(previewPlatformModule())

private fun previewPlatformModule(): Module = module {
    single<AuthRepository> { FakeAuthRepository() }
    single<NotesListsRepository> { FakeNotesListsRepository() }
    single<NotesRepository> { FakeNotesRepository() }
    single<FriendsRepository> { FakeFriendsRepository() }
    single<AccountSettingsRepository> { FakeAccountSettingsRepository() }
    single<FcmTokenRegister> { NoopFcmTokenRegister() }
}
