package com.chemecador.secretaria.notes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NotesPresenterTest {

    @Test
    fun load_transitionsFromLoadingToContent() = runTest {
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
        val presenter = NotesPresenter(repository, listId = "shopping")

        val job = backgroundScope.launch {
            presenter.load()
        }

        runCurrent()
        assertTrue(presenter.state.value.isLoading)

        repository.release()
        job.join()

        assertFalse(presenter.state.value.isLoading)
        assertEquals(1, presenter.state.value.notes.size)
        assertNull(presenter.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToEmptyContent() = runTest {
        val repository = ControlledRepository(Result.success(emptyList()))
        val presenter = NotesPresenter(repository, listId = "unknown")

        val job = backgroundScope.launch {
            presenter.load()
        }

        runCurrent()
        assertTrue(presenter.state.value.isLoading)

        repository.release()
        job.join()

        assertFalse(presenter.state.value.isLoading)
        assertTrue(presenter.state.value.notes.isEmpty())
        assertNull(presenter.state.value.errorMessage)
    }

    @Test
    fun load_transitionsFromLoadingToError() = runTest {
        val repository = ControlledRepository(
            Result.failure(IllegalStateException("fallo de prueba")),
        )
        val presenter = NotesPresenter(repository, listId = "shopping")

        val job = backgroundScope.launch {
            presenter.load()
        }

        runCurrent()
        assertTrue(presenter.state.value.isLoading)

        repository.release()
        job.join()

        assertFalse(presenter.state.value.isLoading)
        assertTrue(presenter.state.value.notes.isEmpty())
        assertEquals("fallo de prueba", presenter.state.value.errorMessage)
    }

    @Test
    fun refresh_recoversAfterInitialFailure() = runTest {
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
        val presenter = NotesPresenter(repository, listId = "shopping")

        presenter.load()
        assertEquals("fallo inicial", presenter.state.value.errorMessage)
        assertTrue(presenter.state.value.notes.isEmpty())

        repository.result = Result.success(successNotes)
        presenter.refresh()

        assertFalse(presenter.state.value.isLoading)
        assertEquals(successNotes, presenter.state.value.notes)
        assertNull(presenter.state.value.errorMessage)
    }

    @Test
    fun presenter_passesListIdToRepository() = runTest {
        val repository = RecordingRepository()
        val presenter = NotesPresenter(repository, listId = "travel")

        presenter.load()

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
