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
        val viewModel = buildViewModel(repository, listId = "shopping")

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
        val viewModel = buildViewModel(repository, listId = "unknown")

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
        val viewModel = buildViewModel(repository, listId = "shopping")

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
        val viewModel = buildViewModel(repository, listId = "shopping")

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
    fun viewModel_passesOwnerIdAndListIdToRepository() = runTest(dispatcher) {
        val repository = RecordingRepository()
        val viewModel = buildViewModel(
            repository = repository,
            ownerId = SHARED_OWNER_ID,
            listId = "travel",
        )

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf("$SHARED_OWNER_ID:travel"), repository.requestedKeys)
    }

    @Test
    fun createNote_addsNewNoteToState() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository, listId = "test-list")

        viewModel.load()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.notes.isEmpty())

        viewModel.createNote("Titulo", "Contenido")
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.notes.size)
        assertEquals("Titulo", viewModel.state.value.notes.first().title)
        assertEquals("Contenido", viewModel.state.value.notes.first().content)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun createNote_errorSetsErrorMessage() = runTest(dispatcher) {
        val repository = FailingCreateRepository()
        val viewModel = buildViewModel(repository, listId = "test-list")

        viewModel.load()
        advanceUntilIdle()

        viewModel.createNote("Titulo", "Contenido")
        advanceUntilIdle()

        assertEquals("fallo al crear", viewModel.state.value.errorMessage)
    }

    @Test
    fun deleteNote_removesNoteFromState() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository, listId = "test-list")

        viewModel.load()
        advanceUntilIdle()

        viewModel.createNote("Para borrar", "contenido")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.notes.size)

        val noteId = viewModel.state.value.notes[0].id
        viewModel.deleteNote(noteId)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.notes.isEmpty())
    }

    @Test
    fun deleteNote_errorSetsErrorMessage() = runTest(dispatcher) {
        val repository = FailingDeleteRepository()
        val viewModel = buildViewModel(repository, listId = "test-list")

        viewModel.load()
        advanceUntilIdle()

        viewModel.deleteNote("any")
        advanceUntilIdle()

        assertEquals("fallo al eliminar", viewModel.state.value.errorMessage)
    }

    @Test
    fun updateNote_updatesNoteInState() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository, listId = "test-list")

        viewModel.load()
        advanceUntilIdle()

        viewModel.createNote("Original", "contenido original")
        advanceUntilIdle()
        assertEquals(1, viewModel.state.value.notes.size)

        val noteId = viewModel.state.value.notes[0].id
        viewModel.updateNote(
            noteId = noteId,
            title = "Editado",
            content = "contenido editado",
            completed = true,
            color = 0xFFC8E6C9L,
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.notes.size)
        assertEquals("Editado", viewModel.state.value.notes[0].title)
        assertEquals("contenido editado", viewModel.state.value.notes[0].content)
        assertTrue(viewModel.state.value.notes[0].completed)
        assertEquals(0xFFC8E6C9L, viewModel.state.value.notes[0].color)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun updateNote_errorSetsErrorMessage() = runTest(dispatcher) {
        val repository = FailingUpdateRepository()
        val viewModel = buildViewModel(repository, listId = "test-list")

        viewModel.load()
        advanceUntilIdle()

        viewModel.updateNote(
            noteId = "any",
            title = "titulo",
            content = "contenido",
            completed = false,
            color = 0xFFFFFFFFL,
        )
        advanceUntilIdle()

        assertEquals("fallo al actualizar", viewModel.state.value.errorMessage)
    }

    private fun buildViewModel(
        repository: NotesRepository,
        ownerId: String = DEFAULT_OWNER_ID,
        listId: String,
    ): NotesViewModel = NotesViewModel(
        repository = repository,
        ownerId = ownerId,
        listId = listId,
    )

    private class ControlledRepository(
        private val result: Result<List<Note>>,
    ) : NotesRepository {
        private val gate = CompletableDeferred<Unit>()

        override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> {
            gate.await()
            return result
        }

        override suspend fun createNote(
            ownerId: String,
            listId: String,
            title: String,
            content: String,
        ): Result<Note> = throw UnsupportedOperationException()

        override suspend fun deleteNote(
            ownerId: String,
            listId: String,
            noteId: String,
        ): Result<Unit> = throw UnsupportedOperationException()

        override suspend fun updateNote(
            ownerId: String,
            listId: String,
            noteId: String,
            title: String,
            content: String,
            completed: Boolean,
            color: Long,
        ): Result<Note> = throw UnsupportedOperationException()

        fun release() {
            gate.complete(Unit)
        }
    }

    private class SwitchableRepository(
        var result: Result<List<Note>>,
    ) : NotesRepository {
        override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> =
            result

        override suspend fun createNote(
            ownerId: String,
            listId: String,
            title: String,
            content: String,
        ): Result<Note> = throw UnsupportedOperationException()

        override suspend fun deleteNote(
            ownerId: String,
            listId: String,
            noteId: String,
        ): Result<Unit> = throw UnsupportedOperationException()

        override suspend fun updateNote(
            ownerId: String,
            listId: String,
            noteId: String,
            title: String,
            content: String,
            completed: Boolean,
            color: Long,
        ): Result<Note> = throw UnsupportedOperationException()
    }

    private class RecordingRepository : NotesRepository {
        val requestedKeys = mutableListOf<String>()

        override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> {
            requestedKeys += notesKey(ownerId, listId)
            return Result.success(emptyList())
        }

        override suspend fun createNote(
            ownerId: String,
            listId: String,
            title: String,
            content: String,
        ): Result<Note> = throw UnsupportedOperationException()

        override suspend fun deleteNote(
            ownerId: String,
            listId: String,
            noteId: String,
        ): Result<Unit> = throw UnsupportedOperationException()

        override suspend fun updateNote(
            ownerId: String,
            listId: String,
            noteId: String,
            title: String,
            content: String,
            completed: Boolean,
            color: Long,
        ): Result<Note> = throw UnsupportedOperationException()
    }

    private class MutableRepository : NotesRepository {
        private val notes = mutableMapOf<String, MutableList<Note>>()

        override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> =
            Result.success(notes[notesKey(ownerId, listId)].orEmpty())

        override suspend fun createNote(
            ownerId: String,
            listId: String,
            title: String,
            content: String,
        ): Result<Note> {
            val list = notes.getOrPut(notesKey(ownerId, listId)) { mutableListOf() }
            val note = Note(
                id = "note-${list.size + 1}",
                title = title,
                content = content,
                createdAt = Instant.parse("2026-04-01T10:00:00Z"),
                order = list.size,
                creator = "Test",
            )
            list.add(note)
            return Result.success(note)
        }

        override suspend fun deleteNote(ownerId: String, listId: String, noteId: String): Result<Unit> {
            notes[notesKey(ownerId, listId)]?.removeAll { it.id == noteId }
            return Result.success(Unit)
        }

        override suspend fun updateNote(
            ownerId: String,
            listId: String,
            noteId: String,
            title: String,
            content: String,
            completed: Boolean,
            color: Long,
        ): Result<Note> {
            val list = notes[notesKey(ownerId, listId)]
                ?: return Result.failure(IllegalStateException("List not found"))
            val index = list.indexOfFirst { it.id == noteId }
            if (index == -1) return Result.failure(IllegalStateException("Note not found"))
            val updated = list[index].copy(
                title = title,
                content = content,
                completed = completed,
                color = color,
            )
            list[index] = updated
            return Result.success(updated)
        }
    }

    private class FailingCreateRepository : NotesRepository {
        override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> =
            Result.success(emptyList())

        override suspend fun createNote(
            ownerId: String,
            listId: String,
            title: String,
            content: String,
        ): Result<Note> = Result.failure(IllegalStateException("fallo al crear"))

        override suspend fun deleteNote(
            ownerId: String,
            listId: String,
            noteId: String,
        ): Result<Unit> = throw UnsupportedOperationException()

        override suspend fun updateNote(
            ownerId: String,
            listId: String,
            noteId: String,
            title: String,
            content: String,
            completed: Boolean,
            color: Long,
        ): Result<Note> = throw UnsupportedOperationException()
    }

    private class FailingDeleteRepository : NotesRepository {
        override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> =
            Result.success(emptyList())

        override suspend fun createNote(
            ownerId: String,
            listId: String,
            title: String,
            content: String,
        ): Result<Note> = throw UnsupportedOperationException()

        override suspend fun deleteNote(
            ownerId: String,
            listId: String,
            noteId: String,
        ): Result<Unit> = Result.failure(IllegalStateException("fallo al eliminar"))

        override suspend fun updateNote(
            ownerId: String,
            listId: String,
            noteId: String,
            title: String,
            content: String,
            completed: Boolean,
            color: Long,
        ): Result<Note> = throw UnsupportedOperationException()
    }

    private class FailingUpdateRepository : NotesRepository {
        override suspend fun getNotesForList(ownerId: String, listId: String): Result<List<Note>> =
            Result.success(emptyList())

        override suspend fun createNote(
            ownerId: String,
            listId: String,
            title: String,
            content: String,
        ): Result<Note> = throw UnsupportedOperationException()

        override suspend fun deleteNote(
            ownerId: String,
            listId: String,
            noteId: String,
        ): Result<Unit> = throw UnsupportedOperationException()

        override suspend fun updateNote(
            ownerId: String,
            listId: String,
            noteId: String,
            title: String,
            content: String,
            completed: Boolean,
            color: Long,
        ): Result<Note> = Result.failure(IllegalStateException("fallo al actualizar"))
    }

    private companion object {
        const val DEFAULT_OWNER_ID = "Alex"
        const val SHARED_OWNER_ID = "Marta"

        fun notesKey(ownerId: String, listId: String): String = "$ownerId:$listId"
    }
}
