package com.chemecador.secretaria.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.CreationExtras
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FakeAuthRepository
import com.chemecador.secretaria.notes.NotesViewModel
import com.chemecador.secretaria.noteslists.NotesListsViewModel
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.koin.core.Koin
import org.koin.core.annotation.KoinInternalApi
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.test.KoinTest
import org.koin.test.get
import org.koin.viewmodel.resolveViewModel
import kotlin.reflect.KClass

@OptIn(ExperimentalCoroutinesApi::class, KoinInternalApi::class)
class AppModulesTest : KoinTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var koin: Koin

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        koin = startKoin {
            modules(previewAppModules())
        }.koin
    }

    @AfterTest
    fun tearDown() {
        stopKoin()
        Dispatchers.resetMain()
    }

    @Test
    fun resolvesAuthRepositoryFromSharedGraph() {
        assertIs<FakeAuthRepository>(get<AuthRepository>())
    }

    @Test
    fun resolvesNotesListsViewModelFromSharedGraph() {
        assertIs<NotesListsViewModel>(resolveTestViewModel(NotesListsViewModel::class))
    }

    @Test
    fun resolvesParameterizedNotesViewModel() = runTest(dispatcher) {
        val viewModel = resolveTestViewModel(NotesViewModel::class, key = "Alex:work") {
            parametersOf("Alex", "work")
        }

        viewModel.load()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notes.isNotEmpty())
        assertEquals("work-1", viewModel.state.value.notes.first().id)
    }

    private fun <T : ViewModel> resolveTestViewModel(
        modelClass: KClass<T>,
        key: String? = null,
        parameters: (() -> org.koin.core.parameter.ParametersHolder)? = null,
    ): T = resolveViewModel(
        vmClass = modelClass,
        viewModelStore = ViewModelStore(),
        key = key,
        extras = CreationExtras.Empty,
        qualifier = null,
        scope = koin.scopeRegistry.rootScope,
        parameters = parameters,
    )
}
