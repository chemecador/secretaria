package com.chemecador.secretaria.noteslists

import com.chemecador.secretaria.friends.FriendSummary
import com.chemecador.secretaria.friends.FriendsRepository
import com.chemecador.secretaria.login.AuthRepository
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
            Result.success(listOf(listSummary(id = "1", name = "Compra semanal"))),
        )
        val viewModel = buildViewModel(repository)

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
        val viewModel = buildViewModel(repository)

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
        val viewModel = buildViewModel(repository)

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
            listSummary(
                id = "1",
                name = "Compra semanal",
                createdAt = Instant.parse("2026-03-01T10:00:00Z"),
            ),
            listSummary(
                id = "2",
                name = "Trabajo",
                createdAt = Instant.parse("2026-03-15T10:00:00Z"),
            ),
        )
        val repository = ImmediateRepository(Result.success(items))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertEquals("Trabajo", viewModel.state.value.items[0].name)
        assertEquals("Compra semanal", viewModel.state.value.items[1].name)

        viewModel.setSort(SortOption.NAME_ASC)

        assertEquals(SortOption.NAME_ASC, viewModel.state.value.sortOption)
        assertEquals("Compra semanal", viewModel.state.value.items[0].name)
        assertEquals("Trabajo", viewModel.state.value.items[1].name)
    }

    @Test
    fun createList_addsNewItemToState() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.items.size)

        viewModel.createList("Mi nueva lista", false)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.size)
        assertEquals("Mi nueva lista", viewModel.state.value.items[0].name)
        assertFalse(viewModel.state.value.items[0].isOrdered)
    }

    @Test
    fun createList_errorSetsErrorMessage() = runTest(dispatcher) {
        val repository = FailingCreateRepository()
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.createList("Falla", false)
        advanceUntilIdle()

        assertEquals("error al crear", viewModel.state.value.errorMessage)
    }

    @Test
    fun load_respectsCurrentSortOption() = runTest(dispatcher) {
        val items = listOf(
            listSummary(
                id = "1",
                name = "Compra semanal",
                createdAt = Instant.parse("2026-03-01T10:00:00Z"),
            ),
            listSummary(
                id = "2",
                name = "Trabajo",
                createdAt = Instant.parse("2026-03-15T10:00:00Z"),
            ),
        )
        val repository = ImmediateRepository(Result.success(items))
        val viewModel = buildViewModel(repository)

        viewModel.setSort(SortOption.NAME_ASC)
        viewModel.load()
        advanceUntilIdle()

        assertEquals(SortOption.NAME_ASC, viewModel.state.value.sortOption)
        assertEquals("Compra semanal", viewModel.state.value.items[0].name)
        assertEquals("Trabajo", viewModel.state.value.items[1].name)
    }

    @Test
    fun deleteList_removesItemFromState() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.createList("Para borrar", false)
        advanceUntilIdle()
        val list = viewModel.state.value.items.single()

        viewModel.deleteList(list)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.items.isEmpty())
        assertEquals(1, repository.deleteCalls)
    }

    @Test
    fun deleteList_sharedListSetsErrorAndSkipsRepository() = runTest(dispatcher) {
        val repository = MutableRepository()
        val sharedList = listSummary(id = "shared", name = "Compartida", ownerId = SHARED_OWNER_ID)
        repository.seed(sharedList)
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.deleteList(sharedList)
        advanceUntilIdle()

        assertEquals("Solo el propietario puede modificar esta lista", viewModel.state.value.errorMessage)
        assertEquals(0, repository.deleteCalls)
        assertEquals(1, viewModel.state.value.items.size)
    }

    @Test
    fun deleteList_errorSetsErrorMessage() = runTest(dispatcher) {
        val repository = FailingDeleteRepository()
        val viewModel = buildViewModel(repository)
        val list = listSummary(id = "any", name = "Any")

        viewModel.load()
        advanceUntilIdle()

        viewModel.deleteList(list)
        advanceUntilIdle()

        assertEquals("error al eliminar", viewModel.state.value.errorMessage)
    }

    @Test
    fun updateList_updatesItemInState() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.createList("Original", true)
        advanceUntilIdle()
        val list = viewModel.state.value.items.single()

        viewModel.updateList(list, "Editada", false)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.size)
        assertEquals("Editada", viewModel.state.value.items[0].name)
        assertFalse(viewModel.state.value.items[0].isOrdered)
        assertEquals(1, repository.updateCalls)
    }

    @Test
    fun updateList_sharedListSetsErrorAndSkipsRepository() = runTest(dispatcher) {
        val repository = MutableRepository()
        val sharedList = listSummary(id = "shared", name = "Compartida", ownerId = SHARED_OWNER_ID)
        repository.seed(sharedList)
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.updateList(sharedList, "Editada", false)
        advanceUntilIdle()

        assertEquals("Solo el propietario puede modificar esta lista", viewModel.state.value.errorMessage)
        assertEquals(0, repository.updateCalls)
        assertEquals("Compartida", viewModel.state.value.items.single().name)
    }

    @Test
    fun updateList_errorSetsErrorMessage() = runTest(dispatcher) {
        val repository = FailingUpdateRepository()
        val viewModel = buildViewModel(repository)
        val list = listSummary(id = "any", name = "Any")

        viewModel.load()
        advanceUntilIdle()

        viewModel.updateList(list, "Nuevo nombre", false)
        advanceUntilIdle()

        assertEquals("error al actualizar", viewModel.state.value.errorMessage)
    }

    @Test
    fun loadShareableFriends_loadsFriendsIntoState() = runTest(dispatcher) {
        val repository = ImmediateRepository(Result.success(emptyList()))
        val friendsRepository = FakeFriendsRepository(
            friendsResult = Result.success(
                listOf(
                    FriendSummary("friendship-2", "friend-2", "Marina"),
                    FriendSummary("friendship-1", "friend-1", "Ana"),
                ),
            ),
        )
        val viewModel = buildViewModel(repository, friendsRepository = friendsRepository)

        viewModel.loadShareableFriends(listSummary(id = "list-1", name = "Trabajo"))
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoadingShareableFriends)
        assertEquals(listOf("Ana", "Marina"), viewModel.state.value.shareableFriends.map { it.name })
        assertEquals(null, viewModel.state.value.shareErrorMessage)
    }

    @Test
    fun load_mapsCollaboratorsForOwnerSharedList() = runTest(dispatcher) {
        val sharedList = listSummary(
            id = "list-1",
            name = "Trabajo",
            contributors = listOf(OWNER_ID, "friend-1"),
        )
        val repository = ImmediateRepository(Result.success(listOf(sharedList)))
        val friendsRepository = FakeFriendsRepository(
            friendsResult = Result.success(listOf(FriendSummary("friendship-1", "friend-1", "Marina"))),
        )
        val viewModel = buildViewModel(repository, friendsRepository = friendsRepository)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(
            listOf("Marina"),
            viewModel.state.value.collaboratorsByListId["list-1"]?.map { collaborator -> collaborator.name },
        )
    }

    @Test
    fun loadShareableFriends_excludesExistingCollaborators() = runTest(dispatcher) {
        val repository = ImmediateRepository(Result.success(emptyList()))
        val friendsRepository = FakeFriendsRepository(
            friendsResult = Result.success(
                listOf(
                    FriendSummary("friendship-1", "friend-1", "Marina"),
                    FriendSummary("friendship-2", "friend-2", "Ana"),
                ),
            ),
        )
        val viewModel = buildViewModel(repository, friendsRepository = friendsRepository)

        viewModel.loadShareableFriends(
            listSummary(
                id = "list-1",
                name = "Trabajo",
                contributors = listOf(OWNER_ID, "friend-1"),
            ),
        )
        advanceUntilIdle()

        assertEquals(listOf("Ana"), viewModel.state.value.shareableFriends.map { it.name })
        assertEquals(
            listOf("Marina"),
            viewModel.state.value.collaboratorsByListId["list-1"]?.map { collaborator -> collaborator.name },
        )
    }

    @Test
    fun shareList_removesFriendFromCandidatesAndPublishesSuccess() = runTest(dispatcher) {
        val repository = MutableRepository()
        val friend = FriendSummary("friendship-1", "friend-1", "Marina")
        val viewModel = buildViewModel(
            repository,
            friendsRepository = FakeFriendsRepository(friendsResult = Result.success(listOf(friend))),
        )
        repository.seed(listSummary(id = "list-1", name = "Trabajo"))

        viewModel.load()
        advanceUntilIdle()
        val list = viewModel.state.value.items.single()

        viewModel.loadShareableFriends(list)
        advanceUntilIdle()
        viewModel.shareList(list, friend)
        advanceUntilIdle()

        assertEquals(1, repository.shareCalls)
        assertEquals("friend-1", repository.lastSharedFriendId)
        assertTrue(viewModel.state.value.shareableFriends.isEmpty())
        assertTrue(viewModel.state.value.items.single().isShared)
        assertEquals("Marina", viewModel.state.value.shareFeedback?.friendName)
        assertEquals(ListSharingAction.SHARED, viewModel.state.value.shareFeedback?.action)
        assertEquals(
            listOf("Marina"),
            viewModel.state.value.collaboratorsByListId["list-1"]?.map { collaborator -> collaborator.name },
        )
        assertEquals(null, viewModel.state.value.shareErrorMessage)
    }

    @Test
    fun shareList_sharedListSetsErrorAndSkipsRepository() = runTest(dispatcher) {
        val repository = MutableRepository()
        val friend = FriendSummary("friendship-1", "friend-1", "Marina")
        val viewModel = buildViewModel(repository)
        val sharedList = listSummary(id = "shared", name = "Compartida", ownerId = SHARED_OWNER_ID)

        viewModel.shareList(sharedList, friend)
        advanceUntilIdle()

        assertEquals(0, repository.shareCalls)
        assertEquals("Solo el propietario puede modificar esta lista", viewModel.state.value.shareErrorMessage)
    }

    @Test
    fun unshareList_removesCollaboratorAndPublishesSuccess() = runTest(dispatcher) {
        val repository = MutableRepository()
        val friend = FriendSummary("friendship-1", "friend-1", "Marina")
        val viewModel = buildViewModel(
            repository,
            friendsRepository = FakeFriendsRepository(friendsResult = Result.success(listOf(friend))),
        )
        repository.seed(
            listSummary(
                id = "list-1",
                name = "Trabajo",
                contributors = listOf(OWNER_ID, "friend-1"),
            ),
        )

        viewModel.load()
        advanceUntilIdle()
        val list = viewModel.state.value.items.single()

        viewModel.loadShareableFriends(list)
        advanceUntilIdle()
        val collaborator = viewModel.state.value.collaboratorsByListId["list-1"].orEmpty().single()

        viewModel.unshareList(list, collaborator)
        advanceUntilIdle()

        assertEquals(1, repository.unshareCalls)
        assertEquals("friend-1", repository.lastUnsharedFriendId)
        assertFalse(viewModel.state.value.items.single().isShared)
        assertTrue(viewModel.state.value.collaboratorsByListId["list-1"].isNullOrEmpty())
        assertEquals(listOf("Marina"), viewModel.state.value.shareableFriends.map { it.name })
        assertEquals("Marina", viewModel.state.value.shareFeedback?.friendName)
        assertEquals(ListSharingAction.UNSHARED, viewModel.state.value.shareFeedback?.action)
        assertEquals(null, viewModel.state.value.shareErrorMessage)
    }

    private fun buildViewModel(
        repository: NotesListsRepository,
        authRepository: AuthRepository = LoggedInAuthRepository(OWNER_ID),
        friendsRepository: FriendsRepository = FakeFriendsRepository(),
    ): NotesListsViewModel = NotesListsViewModel(repository, authRepository, friendsRepository)

    private class ImmediateRepository(
        private val result: Result<List<NotesListSummary>>,
    ) : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> = result
        override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())
    }

    private class ControlledRepository(
        private val result: Result<List<NotesListSummary>>,
    ) : NotesListsRepository {
        private val gate = CompletableDeferred<Unit>()

        override suspend fun getLists(): Result<List<NotesListSummary>> {
            gate.await()
            return result
        }

        override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())

        fun release() {
            gate.complete(Unit)
        }
    }

    private class MutableRepository : NotesListsRepository {
        private val lists = mutableListOf<NotesListSummary>()
        var deleteCalls = 0
            private set
        var updateCalls = 0
            private set
        var shareCalls = 0
            private set
        var unshareCalls = 0
            private set
        var lastSharedFriendId: String? = null
            private set
        var lastUnsharedFriendId: String? = null
            private set

        fun seed(list: NotesListSummary) {
            lists += list
        }

        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(lists.toList())

        override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> {
            val item = listSummary(
                id = "new-${lists.size + 1}",
                name = name,
                createdAt = Instant.parse("2026-04-09T12:00:00Z"),
                isOrdered = ordered,
            )
            lists.add(item)
            return Result.success(item)
        }

        override suspend fun deleteList(listId: String): Result<Unit> {
            deleteCalls += 1
            lists.removeAll { it.id == listId }
            return Result.success(Unit)
        }

        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> {
            shareCalls += 1
            lastSharedFriendId = friendUserId
            val index = lists.indexOfFirst { it.id == listId }
            if (index != -1) {
                val contributors = (lists[index].contributors + friendUserId).distinct()
                lists[index] = lists[index].copy(
                    isShared = contributors.size > 1,
                    contributors = contributors,
                )
            }
            return Result.success(Unit)
        }

        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> {
            unshareCalls += 1
            lastUnsharedFriendId = friendUserId
            val index = lists.indexOfFirst { it.id == listId }
            if (index != -1) {
                val contributors = lists[index].contributors.filterNot { contributorId ->
                    contributorId == friendUserId
                }
                lists[index] = lists[index].copy(
                    isShared = contributors.distinct().size > 1,
                    contributors = contributors,
                )
            }
            return Result.success(Unit)
        }

        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> {
            updateCalls += 1
            val index = lists.indexOfFirst { it.id == listId }
            if (index == -1) return Result.failure(IllegalStateException("List not found"))
            val updated = lists[index].copy(name = name, isOrdered = ordered)
            lists[index] = updated
            return Result.success(updated)
        }
    }

    private class FailingCreateRepository : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(emptyList())

        override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> =
            Result.failure(IllegalStateException("error al crear"))
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())
    }

    private class FailingDeleteRepository : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(emptyList())
        override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(IllegalStateException("error al eliminar"))
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())
    }

    private class FailingUpdateRepository : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(emptyList())
        override suspend fun createList(name: String, ordered: Boolean): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(IllegalStateException("error al actualizar"))
    }

    private class LoggedInAuthRepository(
        override val currentUserId: String?,
        override val currentUserEmail: String? = null,
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signup(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun loginWithGoogle(idToken: String?): Result<Unit> = Result.success(Unit)
        override suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
        override suspend fun logout(): Result<Unit> = Result.success(Unit)
        override suspend fun restoreSession(): Result<Boolean> = Result.success(currentUserId != null)
    }

    private class FakeFriendsRepository(
        private val friendsResult: Result<List<FriendSummary>> = Result.success(emptyList()),
    ) : FriendsRepository {
        override suspend fun getMyFriendCode(): Result<String> = Result.success("123456")
        override suspend fun getFriends(): Result<List<FriendSummary>> = friendsResult
        override suspend fun getIncomingRequests(): Result<List<com.chemecador.secretaria.friends.IncomingFriendRequest>> =
            Result.success(emptyList())
        override suspend fun getOutgoingRequests(): Result<List<com.chemecador.secretaria.friends.OutgoingFriendRequest>> =
            Result.success(emptyList())
        override suspend fun sendFriendRequest(friendCode: String) = Result.success(Unit)
        override suspend fun acceptFriendRequest(requestId: String) = Result.success(Unit)
        override suspend fun rejectFriendRequest(requestId: String) = Result.success(Unit)
        override suspend fun cancelFriendRequest(requestId: String) = Result.success(Unit)
        override suspend fun deleteFriend(friendshipId: String) = Result.success(Unit)
    }

    private companion object {
        const val OWNER_ID = "Alex"
        const val SHARED_OWNER_ID = "Marta"

        fun listSummary(
            id: String,
            name: String,
            ownerId: String = OWNER_ID,
            createdAt: Instant = Instant.parse("2026-03-28T12:00:00Z"),
            isOrdered: Boolean = false,
            isShared: Boolean? = null,
            contributors: List<String> = emptyList(),
        ): NotesListSummary = NotesListSummary(
            id = id,
            ownerId = ownerId,
            name = name,
            creator = ownerId,
            createdAt = createdAt,
            isOrdered = isOrdered,
            isShared = isShared ?: run {
                val resolvedContributors = if (contributors.isEmpty()) {
                    if (ownerId == OWNER_ID) {
                        listOf(ownerId)
                    } else {
                        listOf(ownerId, OWNER_ID)
                    }
                } else {
                    contributors
                }
                ownerId != OWNER_ID || resolvedContributors.distinct().size > 1
            },
            contributors = if (contributors.isEmpty()) {
                if (ownerId == OWNER_ID) {
                    listOf(ownerId)
                } else {
                    listOf(ownerId, OWNER_ID)
                }
            } else {
                contributors
            },
        )
    }
}
