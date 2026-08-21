package com.chemecador.secretaria.reminders

import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.friends.IncomingFriendRequest
import com.chemecador.secretaria.friends.OutgoingFriendRequest
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.noteslists.ListCollaborator
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
        assertEquals(listOf(keys("caducado")), repository.deleteCalls)
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

        viewModel.setReminderCompleted(key("r1"), completed = true)

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

        viewModel.setReminderCompleted(key("r1"), completed = true)
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

        viewModel.setReminderCompleted(key("restaurable"), completed = false)
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

        viewModel.reorderReminders(keys("c", "a", "b"))

        assertEquals(
            listOf("c", "a", "b"),
            viewModel.state.value.reminders.sortedBy(Reminder::order).map(Reminder::id),
        )

        advanceUntilIdle()
        assertEquals(listOf(keys("c", "a", "b")), repository.reorderCalls)
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

        viewModel.reorderReminders(keys("b", "a"))
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

        viewModel.reorderReminders(keys("b", "a"))
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

        viewModel.setReminderCompleted(key("r1"), completed = true)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.feedback != null)

        viewModel.consumeFeedback()

        assertNull(viewModel.state.value.feedback)
    }

    @Test
    fun loadShareableFriends_hidesFriendsThatAlreadyHaveAccess() = runTest(dispatcher) {
        val shared = pending("r1", order = 0).withContributors(listOf("marta"))
        val repository = TestRemindersRepository(initial = listOf(shared))
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.loadShareableFriends(shared)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.shareableFriends.isEmpty())
        assertEquals(
            listOf("Marta"),
            viewModel.state.value.collaboratorsByReminderId["r1"]?.map { it.name },
        )
    }

    @Test
    fun createReminder_sharesWithTheFriendsPickedWhileCreating() = runTest(dispatcher) {
        val repository = TestRemindersRepository()
        val viewModel = buildViewModel(repository)

        viewModel.createReminder("Comprar pan", shareWith = listOf(FRIEND))
        advanceUntilIdle()

        assertEquals(listOf("created-1" to "marta"), repository.shareCalls)
        assertEquals(listOf("marta"), viewModel.state.value.reminders.single().sharedWithUserIds)
    }

    @Test
    fun createReminder_keepsTheReminderWhenTheShareFails() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            shareFailure = IllegalStateException("sin permiso"),
        )
        val viewModel = buildViewModel(repository)

        viewModel.createReminder("Comprar pan", shareWith = listOf(FRIEND))
        advanceUntilIdle()

        val created = viewModel.state.value.reminders.single()
        assertEquals("Comprar pan", created.text)
        assertTrue(created.sharedWithUserIds.isEmpty())
        assertEquals(ReminderSharingAction.SHARED, viewModel.state.value.shareFeedback?.action)
        assertFalse(viewModel.state.value.shareFeedback?.isSuccess == true)
    }

    @Test
    fun loadShareableFriendsForNewReminder_offersEveryFriend() = runTest(dispatcher) {
        val viewModel = buildViewModel(TestRemindersRepository())

        viewModel.loadShareableFriendsForNewReminder()
        advanceUntilIdle()

        assertEquals(
            listOf("marta"),
            viewModel.state.value.shareableFriends.map(FriendSummary::userId),
        )
        assertFalse(viewModel.state.value.isLoadingShareableFriends)
    }

    @Test
    fun shareReminder_addsTheContributorAndReportsIt() = runTest(dispatcher) {
        val reminder = pending("r1", order = 0)
        val repository = TestRemindersRepository(initial = listOf(reminder))
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.shareReminder(reminder, FRIEND)
        advanceUntilIdle()

        assertEquals(listOf("r1" to "marta"), repository.shareCalls)
        val updated = viewModel.state.value.reminders.single()
        assertEquals(listOf("marta"), updated.sharedWithUserIds)
        assertTrue(updated.isShared)
        assertEquals(ReminderSharingAction.SHARED, viewModel.state.value.shareFeedback?.action)
    }

    @Test
    fun shareReminder_isRejectedForRemindersOwnedBySomeoneElse() = runTest(dispatcher) {
        val foreign = pending("r1", order = 0, ownerId = "marta")
        val repository = TestRemindersRepository(initial = listOf(foreign))
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.shareReminder(foreign, FRIEND)
        advanceUntilIdle()

        assertTrue(repository.shareCalls.isEmpty())
        assertTrue(viewModel.state.value.shareErrorMessage != null)
    }

    @Test
    fun unshareReminder_removesTheContributorAndReportsIt() = runTest(dispatcher) {
        val shared = pending("r1", order = 0).withContributors(listOf("marta"))
        val repository = TestRemindersRepository(initial = listOf(shared))
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.unshareReminder(
            shared,
            ListCollaborator(userId = "marta", name = "Marta"),
        )
        advanceUntilIdle()

        assertEquals(listOf("r1" to "marta"), repository.unshareCalls)
        val updated = viewModel.state.value.reminders.single()
        assertTrue(updated.sharedWithUserIds.isEmpty())
        assertEquals(ReminderSharingAction.UNSHARED, viewModel.state.value.shareFeedback?.action)
    }

    @Test
    fun leaveSharedReminder_dropsItFromTheListWithFeedback() = runTest(dispatcher) {
        val foreign = pending("r1", order = 0, ownerId = "marta")
        val repository = TestRemindersRepository(initial = listOf(foreign))
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.leaveSharedReminder(foreign)
        advanceUntilIdle()

        assertEquals(listOf(ReminderKey("marta", "r1")), repository.leaveCalls)
        assertTrue(viewModel.state.value.reminders.isEmpty())
        assertEquals(ReminderFeedbackAction.LEFT_SHARED, viewModel.state.value.feedback?.action)
        assertTrue(viewModel.state.value.feedback?.isSuccess == true)
    }

    @Test
    fun load_purgeOnlyTouchesRemindersOwnedByTheCurrentUser() = runTest(dispatcher) {
        val repository = TestRemindersRepository(
            initial = listOf(
                completed("propio", completedAt = NOW - 40.days),
                Reminder(
                    id = "ajeno",
                    ownerId = "marta",
                    text = "ajeno",
                    createdAt = NOW - 100.days,
                    completed = true,
                    completedAt = NOW - 40.days,
                    order = 99,
                ),
            ),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf(keys("propio")), repository.deleteCalls)
        assertEquals(listOf("ajeno"), viewModel.state.value.reminders.map(Reminder::id))
    }

    private fun buildViewModel(
        repository: RemindersRepository,
        friendsRepository: FriendsRepository = TestFriendsRepository(),
        currentUserId: String? = OWNER_ID,
    ): RemindersViewModel =
        RemindersViewModel(
            repository = repository,
            authRepository = TestAuthRepository(currentUserId),
            friendsRepository = friendsRepository,
            nowProvider = { NOW },
        )

    private fun key(id: String, ownerId: String = OWNER_ID): ReminderKey = ReminderKey(ownerId, id)

    private fun keys(vararg ids: String): List<ReminderKey> = ids.map { id -> key(id) }

    private fun pending(id: String, order: Int, ownerId: String = OWNER_ID): Reminder = Reminder(
        id = id,
        ownerId = ownerId,
        text = id,
        createdAt = NOW - 100.days,
        order = order,
    )

    private fun completed(id: String, completedAt: Instant): Reminder = Reminder(
        id = id,
        ownerId = OWNER_ID,
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
            key: ReminderKey,
            text: String,
            due: ReminderDue?,
        ): Result<Reminder> = throw UnsupportedOperationException()

        override suspend fun setReminderCompleted(
            key: ReminderKey,
            completed: Boolean,
            completedAt: Instant?,
            order: Int,
        ): Result<Unit> = throw UnsupportedOperationException()

        override suspend fun reorderReminders(reminderKeysInOrder: List<ReminderKey>): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun deleteReminders(reminderKeys: List<ReminderKey>): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun shareReminder(reminderId: String, friendUserId: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun unshareReminder(reminderId: String, friendUserId: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun leaveSharedReminder(key: ReminderKey): Result<Unit> =
            throw UnsupportedOperationException()
    }

    /** Doble en memoria con inyeccion de fallos por operacion. */
    private class TestRemindersRepository(
        initial: List<Reminder> = emptyList(),
        var getFailure: Throwable? = null,
        private val completeFailure: Throwable? = null,
        private val reorderFailure: Throwable? = null,
        private val deleteFailure: Throwable? = null,
        private val shareFailure: Throwable? = null,
        private val leaveFailure: Throwable? = null,
    ) : RemindersRepository {

        val reminders = initial.toMutableList()
        val deleteCalls = mutableListOf<List<ReminderKey>>()
        val reorderCalls = mutableListOf<List<ReminderKey>>()
        val shareCalls = mutableListOf<Pair<String, String>>()
        val unshareCalls = mutableListOf<Pair<String, String>>()
        val leaveCalls = mutableListOf<ReminderKey>()

        override suspend fun getReminders(): Result<List<Reminder>> =
            getFailure?.let { Result.failure(it) } ?: Result.success(reminders.toList())

        override suspend fun createReminder(text: String, due: ReminderDue?): Result<Reminder> {
            val created = Reminder(
                id = "created-${reminders.size + 1}",
                ownerId = OWNER_ID,
                text = text,
                createdAt = Instant.fromEpochMilliseconds(0),
                due = due,
                order = reminders.size,
            )
            reminders += created
            return Result.success(created)
        }

        override suspend fun updateReminder(
            key: ReminderKey,
            text: String,
            due: ReminderDue?,
        ): Result<Reminder> {
            val index = reminders.indexOfFirst { it.key == key }
            if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
            val updated = reminders[index].copy(text = text, due = due)
            reminders[index] = updated
            return Result.success(updated)
        }

        override suspend fun setReminderCompleted(
            key: ReminderKey,
            completed: Boolean,
            completedAt: Instant?,
            order: Int,
        ): Result<Unit> {
            completeFailure?.let { return Result.failure(it) }
            val index = reminders.indexOfFirst { it.key == key }
            if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
            reminders[index] = reminders[index].copy(
                completed = completed,
                completedAt = completedAt,
                order = order,
            )
            return Result.success(Unit)
        }

        override suspend fun reorderReminders(reminderKeysInOrder: List<ReminderKey>): Result<Unit> {
            reorderCalls += reminderKeysInOrder
            reorderFailure?.let { return Result.failure(it) }
            reminderKeysInOrder.forEachIndexed { index, key ->
                val position = reminders.indexOfFirst { it.key == key }
                if (position != -1) {
                    reminders[position] = reminders[position].copy(order = index)
                }
            }
            return Result.success(Unit)
        }

        override suspend fun deleteReminders(reminderKeys: List<ReminderKey>): Result<Unit> {
            deleteCalls += reminderKeys
            deleteFailure?.let { return Result.failure(it) }
            reminders.removeAll { it.key in reminderKeys }
            return Result.success(Unit)
        }

        override suspend fun shareReminder(reminderId: String, friendUserId: String): Result<Unit> {
            shareCalls += reminderId to friendUserId
            shareFailure?.let { return Result.failure(it) }
            val index = reminders.indexOfFirst { it.id == reminderId }
            if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
            reminders[index] = reminders[index].withContributors(
                reminders[index].contributors + friendUserId,
            )
            return Result.success(Unit)
        }

        override suspend fun unshareReminder(
            reminderId: String,
            friendUserId: String,
        ): Result<Unit> {
            unshareCalls += reminderId to friendUserId
            shareFailure?.let { return Result.failure(it) }
            val index = reminders.indexOfFirst { it.id == reminderId }
            if (index == -1) return Result.failure(IllegalStateException("Reminder not found"))
            reminders[index] = reminders[index].withContributors(
                reminders[index].contributors.filterNot { it == friendUserId },
            )
            return Result.success(Unit)
        }

        override suspend fun leaveSharedReminder(key: ReminderKey): Result<Unit> {
            leaveCalls += key
            leaveFailure?.let { return Result.failure(it) }
            reminders.removeAll { it.key == key }
            return Result.success(Unit)
        }
    }

    private class TestAuthRepository(
        override val currentUserId: String?,
    ) : AuthRepository {
        override val currentUserEmail: String? = null
        override suspend fun login(email: String, password: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun signup(email: String, password: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun loginWithGoogle(idToken: String?): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun loginAsGuest(): Result<Unit> = throw UnsupportedOperationException()
        override suspend fun logout(): Result<Unit> = throw UnsupportedOperationException()
        override suspend fun restoreSession(): Result<Boolean> = Result.success(false)
    }

    private class TestFriendsRepository(
        private val friends: List<FriendSummary> = listOf(FRIEND),
        private val failure: Throwable? = null,
    ) : FriendsRepository {
        override suspend fun getMyFriendCode(): Result<String> = Result.success("code")

        override suspend fun getFriends(): Result<List<FriendSummary>> =
            failure?.let { Result.failure(it) } ?: Result.success(friends)

        override suspend fun getIncomingRequests(): Result<List<IncomingFriendRequest>> =
            Result.success(emptyList())

        override suspend fun getOutgoingRequests(): Result<List<OutgoingFriendRequest>> =
            Result.success(emptyList())

        override suspend fun sendFriendRequest(friendCode: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun acceptFriendRequest(requestId: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun rejectFriendRequest(requestId: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun cancelFriendRequest(requestId: String): Result<Unit> =
            throw UnsupportedOperationException()

        override suspend fun deleteFriend(friendshipId: String): Result<Unit> =
            throw UnsupportedOperationException()
    }

    private companion object {
        const val OWNER_ID = "alex"
        val NOW: Instant = Instant.parse("2026-08-13T10:00:00Z")
        val FRIEND = FriendSummary(friendshipId = "f1", userId = "marta", name = "Marta")
    }
}
