package com.chemecador.secretaria.noteslists

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotesListsPresenterTest {

    @Test
    fun load_transitionsFromLoadingToContent() = runTest {
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
        val presenter = NotesListsPresenter(repository)

        val job = backgroundScope.launch {
            presenter.load()
        }

        runCurrent()
        assertTrue(presenter.state.value.isLoading)

        repository.release()
        job.join()

        assertFalse(presenter.state.value.isLoading)
        assertEquals(1, presenter.state.value.items.size)
        assertEquals(null, presenter.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToEmptyContent() = runTest {
        val repository = ControlledRepository(Result.success(emptyList()))
        val presenter = NotesListsPresenter(repository)

        val job = backgroundScope.launch {
            presenter.load()
        }

        runCurrent()
        assertTrue(presenter.state.value.isLoading)

        repository.release()
        job.join()

        assertFalse(presenter.state.value.isLoading)
        assertTrue(presenter.state.value.items.isEmpty())
        assertEquals(null, presenter.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToError() = runTest {
        val repository = ControlledRepository(
            Result.failure(IllegalStateException("fallo de prueba")),
        )
        val presenter = NotesListsPresenter(repository)

        val job = backgroundScope.launch {
            presenter.load()
        }

        runCurrent()
        assertTrue(presenter.state.value.isLoading)

        repository.release()
        job.join()

        assertFalse(presenter.state.value.isLoading)
        assertTrue(presenter.state.value.items.isEmpty())
        assertEquals("fallo de prueba", presenter.state.value.errorMessage)
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
