package com.chemecador.secretaria.notes

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotesViewModelTest {

    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_transitionsFromLoadingToContent() = runTest(dispatcher) {
        val repository = ControlledRepository(
            Result.success(
                listOf(
                    Note(
                        id = "n1",
                        title = "Leche",
                        content = "2 litros",
                        createdAt = Instant.parse("2026-03-28T12:05:00Z"),
                        creator = "Alex",
                    ),
                ),
            ),
        )
        val viewModel = NotesViewModel(repository, listId = "shopping")

        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, viewModel.state.value.notes.size)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToEmptyContent() = runTest(dispatcher) {
        val repository = ControlledRepository(Result.success(emptyList()))
        val viewModel = NotesViewModel(repository, listId = "unknown")

        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.notes.isEmpty())
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToError() = runTest(dispatcher) {
        val repository = ControlledRepository(
            Result.failure(IllegalStateException("fallo de prueba")),
        )
        val viewModel = NotesViewModel(repository, listId = "shopping")

        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.notes.isEmpty())
        assertEquals("fallo de prueba", viewModel.state.value.errorMessage)
    }

    @Test
    fun refresh_recoversAfterInitialFailure() = runTest(dispatcher) {
        val successNotes = listOf(
            Note(
                id = "n1",
                title = "Pan",
                content = "Barra rústica",
                createdAt = Instant.parse("2026-03-28T12:06:00Z"),
                creator = "Alex",
            ),
        )
        val repository = SwitchableRepository(
            Result.failure(IllegalStateException("fallo inicial")),
        )
        val viewModel = NotesViewModel(repository, listId = "shopping")

        viewModel.load()
        advanceUntilIdle()
        assertEquals("fallo inicial", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.notes.isEmpty())

        repository.result = Result.success(successNotes)
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(successNotes, viewModel.state.value.notes)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun viewModel_passesListIdToRepository() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val viewModel = NotesViewModel(repository, listId = "travel")

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf("travel"), repository.requestedIds)
    }

    private class ControlledRepository(
        private val result: Result<List<Note>>,
    ) : NotesRepository {
        private val gate = CompletableDeferred<Unit>()

        override suspend fun getNotesForList(listId: String): Result<List<Note>> {
            gate.await()
            return result
        }

        fun release() {
            gate.complete(Unit)
        }
    }

    private class SwitchableRepository(
        var result: Result<List<Note>>,
    ) : NotesRepository {
        override suspend fun getNotesForList(listId: String): Result<List<Note>> = result
    }

    private class RecordingRepository : NotesRepository {
        val requestedIds = mutableListOf<String>()

        override suspend fun getNotesForList(listId: String): Result<List<Note>> {
            requestedIds += listId
            return Result.success(emptyList())
        }
    }
}
