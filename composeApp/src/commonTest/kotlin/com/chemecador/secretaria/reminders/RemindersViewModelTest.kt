package com.chemecador.secretaria.reminders

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
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class RemindersViewModelTest {

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
        val repository = GatedRemindersRepository(Result.success(listOf(pending("r1", order = 0))))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        runCurrent()
        assertTrue(viewModel.state.value.isLoading)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(1, viewModel.state.value.reminders.size)
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun load_failureSurfacesTheErrorMessage() = runTest(dispatcher) {
        val repository = GatedRemindersRepository(Result.failure(IllegalStateException("sin conexion")))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.reminders.isEmpty())
        assertEquals("sin conexion", viewModel.state.value.errorMessage)
    }

    @Test
    fun refresh_recoversAfterAnInitialFailure() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(pending("r1", order = 0)),
            getFailure = IllegalStateException("sin conexion"),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()
        assertEquals("sin conexion", viewModel.state.value.errorMessage)

        repository.getFailure = null
        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isRefreshing)
        assertNull(viewModel.state.value.errorMessage)
        assertEquals(1, viewModel.state.value.reminders.size)
    }

    @Test
    fun load_purgesCompletedRemindersOlderThanRetention() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(
                pending("vigente", order = 0),
                completed("reciente", completedAt = NOW - 10.days),
                completed("caducado", completedAt = NOW - 40.days),
            ),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf("vigente", "reciente"), viewModel.state.value.reminders.map(Reminder::id))
        assertEquals(listOf(listOf("caducado")), repository.deleteCalls)
    }

    @Test
    fun load_purgeFailureKeepsEverythingVisibleAndSilent() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(completed("caducado", completedAt = NOW - 40.days)),
            deleteFailure = IllegalStateException("fallo al borrar"),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf("caducado"), viewModel.state.value.reminders.map(Reminder::id))
        assertNull(viewModel.state.value.errorMessage)
    }

    @Test
    fun refresh_doesNotPurge() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(completed("caducado", completedAt = NOW - 40.days)),
        )
        val viewModel = buildViewModel(repository)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(listOf("caducado"), viewModel.state.value.reminders.map(Reminder::id))
        assertTrue(repository.deleteCalls.isEmpty())
    }

    @Test
    fun setReminderCompleted_appliesOptimisticallyBeforeThePersistenceResolves() = runTest(dispatcher) {
        val repository = TestRemindersRepository(initial = listOf(pending("r1", order = 0)))
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setReminderCompleted("r1", completed = true)

        // Sin avanzar el dispatcher: el estado ya refleja el cambio.
        val optimistic = viewModel.state.value.reminders.single()
        assertTrue(optimistic.completed)
        assertEquals(NOW, optimistic.completedAt)
        assertEquals(ReminderFeedbackAction.COMPLETED, viewModel.state.value.feedback?.action)
        assertTrue(viewModel.state.value.feedback?.isSuccess == true)

        advanceUntilIdle()
        assertTrue(repository.reminders.single().completed)
    }

    @Test
    fun setReminderCompleted_failureRollsBackAndReportsIt() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(pending("r1", order = 0)),
            completeFailure = IllegalStateException("fallo al completar"),
        )
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setReminderCompleted("r1", completed = true)
        advanceUntilIdle()

        val reminder = viewModel.state.value.reminders.single()
        assertFalse(reminder.completed)
        assertNull(reminder.completedAt)
        assertEquals("fallo al completar", viewModel.state.value.errorMessage)
        assertFalse(viewModel.state.value.feedback?.isSuccess == true)
    }

    @Test
    fun setReminderCompleted_restoringSendsTheReminderToTheEndOfThePendingList() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(
                pending("a", order = 0),
                pending("b", order = 1),
                completed("restaurable", completedAt = NOW - 1.days),
            ),
        )
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setReminderCompleted("restaurable", completed = false)
        advanceUntilIdle()

        val restored = viewModel.state.value.reminders.single { it.id == "restaurable" }
        assertFalse(restored.completed)
        assertNull(restored.completedAt)
        assertEquals(2, restored.order)
    }

    @Test
    fun reorderReminders_appliesOptimisticallyAndPersistsTheNewOrder() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(pending("a", order = 0), pending("b", order = 1), pending("c", order = 2)),
        )
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.reorderReminders(listOf("c", "a", "b"))

        assertEquals(
            listOf("c", "a", "b"),
            viewModel.state.value.reminders.sortedBy(Reminder::order).map(Reminder::id),
        )

        advanceUntilIdle()
        assertEquals(listOf(listOf("c", "a", "b")), repository.reorderCalls)
    }

    @Test
    fun reorderReminders_failureRestoresThePreviousOrder() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(pending("a", order = 0), pending("b", order = 1)),
            reorderFailure = IllegalStateException("fallo al reordenar"),
        )
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.reorderReminders(listOf("b", "a"))
        advanceUntilIdle()

        assertEquals(
            listOf("a", "b"),
            viewModel.state.value.reminders.sortedBy(Reminder::order).map(Reminder::id),
        )
        assertEquals("fallo al reordenar", viewModel.state.value.errorMessage)
    }

    @Test
    fun reorderReminders_ignoresCompletedRemindersWhenApplyingTheNewOrder() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(
                pending("a", order = 0),
                pending("b", order = 1),
                completed("hecho", completedAt = NOW - 1.days),
            ),
        )
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.reorderReminders(listOf("b", "a"))
        advanceUntilIdle()

        assertEquals(
            listOf("b", "a"),
            viewModel.state.value.pendingReminders.map(Reminder::id),
        )
        assertEquals(1, viewModel.state.value.completedReminders.size)
    }

    @Test
    fun consumeFeedback_clearsIt() = runTest(dispatcher) {
        val repository = TestRemindersRepository(initial = listOf(pending("r1", order = 0)))
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.setReminderCompleted("r1", completed = true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.feedback != null)

        viewModel.consumeFeedback()

        assertNull(viewModel.state.value.feedback)
    }

    private fun buildViewModel(repository: RemindersRepository): RemindersViewModel =
        RemindersViewModel(repository = repository, nowProvider = { NOW })

    private fun pending(id: String, order: Int): Reminder = Reminder(
        id = id,
        text = id,
        createdAt = NOW - 100.days,
        order = order,
    )

    private fun completed(id: String, completedAt: Instant): Reminder = Reminder(
        id = id,
        text = id,
        createdAt = NOW - 100.days,
        completed = true,
        completedAt = completedAt,
        order = 99,
    )

    /** Deja `getReminders()` suspendido hasta [release] para poder asertar el estado intermedio. */
    private class GatedRemindersRepository(
        private val result: Result<List<Reminder>>,
    ) : RemindersRepository {

        private val gate = CompletableDeferred<Unit>()

        fun release() {
            gate.complete(Unit)
        }

        override suspend fun getReminders(): Result<List<Reminder>> {
            gate.await()
            return result
        }

        override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> =
            throw UnsupportedOperationException()

        override suspend fun updateReminder(
            reminderId: String,
            text: String,
            due: ReminderDue?,
        ): Result<Reminder> = throw UnsupportedOperationException()

        override suspend fun setReminderCompleted(
            reminderId: String,
            completed: Boolean,
            completedAt: Instant?,
            order: Int,
        ): Result<Unit> = throw UnsupportedOperationException()

        override suspend fun reorderReminders(reminderIdsInOrder: List<String>): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun deleteReminders(reminderIds: List<String>): Result<Unit> =
            throw UnsupportedOperationException()
    }

    /** Doble en memoria con inyeccion de fallos por operacion. */
    private class TestRemindersRepository(
        initial: List<Reminder> = emptyList(),
        var getFailure: Throwable? = null,
        private val completeFailure: Throwable? = null,
        private val reorderFailure: Throwable? = null,
        private val deleteFailure: Throwable? = null,
    ) : RemindersRepository {

        val reminders = initial.toMutableList()
        val deleteCalls = mutableListOf<List<String>>()
        val reorderCalls = mutableListOf<List<String>>()

        override suspend fun getReminders(): Result<List<Reminder>> =
            getFailure?.let { Result.failure(it) } ?: Result.success(reminders.toList())

        override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> {
            val created = Reminder(
                id = "created-${reminders.size + 1}",
                text = text,
                createdAt = Instant.fromEpochMilliseconds(0),
                due = due,
                order = reminders.size,
            )
            reminders += created
            return Result.success(created)
        }

        override suspend fun updateReminder(
            reminderId: String,
            text: String,
            due: ReminderDue?,
        ): Result<Reminder> {
            val index = reminders.indexOfFirst { it.id == reminderId }
            if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
            val updated = reminders[index].copy(text = text, due = due)
            reminders[index] = updated
            return Result.success(updated)
        }

        override suspend fun setReminderCompleted(
            reminderId: String,
            completed: Boolean,
            completedAt: Instant?,
            order: Int,
        ): Result<Unit> {
            completeFailure?.let { return Result.failure(it) }
            val index = reminders.indexOfFirst { it.id == reminderId }
            if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
            reminders[index] = reminders[index].copy(
                completed = completed,
                completedAt = completedAt,
                order = order,
            )
            return Result.success(Unit)
        }

        override suspend fun reorderReminders(reminderIdsInOrder: List<String>): Result<Unit> {
            reorderCalls += reminderIdsInOrder
            reorderFailure?.let { return Result.failure(it) }
            reminderIdsInOrder.forEachIndexed { index, reminderId ->
                val position = reminders.indexOfFirst { it.id == reminderId }
                if (position != -1) {
                    reminders[position] = reminders[position].copy(order = index)
                }
            }
            return Result.success(Unit)
        }

        override suspend fun deleteReminders(reminderIds: List<String>): Result<Unit> {
            deleteCalls += reminderIds
            deleteFailure?.let { return Result.failure(it) }
            reminders.removeAll { it.id in reminderIds }
            return Result.success(Unit)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-13T10:00:00Z")
    }
}
