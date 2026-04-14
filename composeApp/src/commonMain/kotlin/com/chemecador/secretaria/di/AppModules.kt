package com.chemecador.secretaria.di

import com.chemecador.secretaria.friends.FakeFriendsRepository
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.friends.FriendsViewModel
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FakeAuthRepository
import com.chemecador.secretaria.login.LoginViewModel
import com.chemecador.secretaria.notes.FakeNotesRepository
import com.chemecador.secretaria.notes.NotesRepository
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.noteslists.FakeNotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsRepository
import com.chemecador.secretaria.noteslists.NotesListsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

private val sharedAppModule = module {
    viewModel { LoginViewModel(get()) }
    viewModel { NotesListsViewModel(get()) }
    viewModel { FriendsViewModel(get()) }
    viewModel { params ->
        NotesViewModel(
            repository = get(),
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
}
