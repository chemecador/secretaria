package com.chemecador.secretaria.noteslists

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
class NotesListsViewModelTest {

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
                    NotesListSummary(
                        id = "1",
                        name = "Compra semanal",
                        creator = "Alex",
                        createdAt = Instant.parse("2026-03-28T12:00:00Z"),
                        isOrdered = false,
                    ),
                ),
            ),
        )
        val viewModel = NotesListsViewModel(repository)

        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, viewModel.state.value.items.size)
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToEmptyContent() = runTest(dispatcher) {
        val repository = ControlledRepository(Result.success(emptyList()))
        val viewModel = NotesListsViewModel(repository)

        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertEquals(null, viewModel.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToError() = runTest(dispatcher) {
        val repository = ControlledRepository(
            Result.failure(IllegalStateException("fallo de prueba")),
        )
        val viewModel = NotesListsViewModel(repository)

        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.items.isEmpty())
        assertEquals("fallo de prueba", viewModel.state.value.errorMessage)
    }

    @Test
    fun setSort_reordersLoadedItems() = runTest(dispatcher) {
        val items = listOf(
            NotesListSummary(
                id = "1",
                name = "Compra semanal",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-01T10:00:00Z"),
                isOrdered = false,
            ),
            NotesListSummary(
                id = "2",
                name = "Trabajo",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-15T10:00:00Z"),
                isOrdered = false,
            ),
        )
        val repository = ImmediateRepository(Result.success(items))
        val viewModel = NotesListsViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        // Default sort is DATE_DESC → "Trabajo" (Mar 15) first
        assertEquals("Trabajo", viewModel.state.value.items[0].name)
        assertEquals("Compra semanal", viewModel.state.value.items[1].name)

        viewModel.setSort(SortOption.NAME_ASC)

        assertEquals(SortOption.NAME_ASC, viewModel.state.value.sortOption)
        assertEquals("Compra semanal", viewModel.state.value.items[0].name)
        assertEquals("Trabajo", viewModel.state.value.items[1].name)
    }

    @Test
    fun load_respectsCurrentSortOption() = runTest(dispatcher) {
        val items = listOf(
            NotesListSummary(
                id = "1",
                name = "Compra semanal",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-01T10:00:00Z"),
                isOrdered = false,
            ),
            NotesListSummary(
                id = "2",
                name = "Trabajo",
                creator = "Alex",
                createdAt = Instant.parse("2026-03-15T10:00:00Z"),
                isOrdered = false,
            ),
        )
        val repository = ImmediateRepository(Result.success(items))
        val viewModel = NotesListsViewModel(repository)

        viewModel.setSort(SortOption.NAME_ASC)
        viewModel.load()
        advanceUntilIdle()

        // After reload, items should still respect NAME_ASC
        assertEquals(SortOption.NAME_ASC, viewModel.state.value.sortOption)
        assertEquals("Compra semanal", viewModel.state.value.items[0].name)
        assertEquals("Trabajo", viewModel.state.value.items[1].name)
    }

    private class ImmediateRepository(
        private val result: Result<List<NotesListSummary>>,
    ) : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> = result
    }

    private class ControlledRepository(
        private val result: Result<List<NotesListSummary>>,
    ) : NotesListsRepository {
        private val gate = CompletableDeferred<Unit>()

        override suspend fun getLists(): Result<List<NotesListSummary>> {
            gate.await()
            return result
        }

        fun release() {
            gate.complete(Unit)
        }
    }
}
