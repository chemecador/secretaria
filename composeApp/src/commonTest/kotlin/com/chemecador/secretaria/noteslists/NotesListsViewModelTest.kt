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
import kotlinx.coroutines.test.advanceTimeBy
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

        viewModel.createList("Mi nueva lista", false, isGroup = false)
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

        viewModel.createList("Falla", false, isGroup = false)
        advanceUntilIdle()

        assertEquals("error al crear", viewModel.state.value.errorMessage)
    }

    @Test
    fun createList_canCreateListGroup() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.createList("Viajes", true, isGroup = true)
        advanceUntilIdle()

        val group = viewModel.state.value.items.single()
        assertEquals("Viajes", group.name)
        assertTrue(group.isGroup)
        assertTrue(group.isOrdered)
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
    fun setSearchQuery_filtersLoadedItemsAfterDebounce() = runTest(dispatcher) {
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
            listSummary(
                id = "3",
                name = "Viaje",
                createdAt = Instant.parse("2026-03-20T10:00:00Z"),
            ),
        )
        val repository = ImmediateRepository(Result.success(items))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.setSearchQuery("tra")
        runCurrent()

        assertEquals("tra", viewModel.state.value.searchQuery)
        assertEquals(listOf("Viaje", "Trabajo", "Compra semanal"), viewModel.state.value.items.map { it.name })

        advanceTimeBy(249)
        runCurrent()

        assertEquals(listOf("Viaje", "Trabajo", "Compra semanal"), viewModel.state.value.items.map { it.name })

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("Trabajo"), viewModel.state.value.items.map { it.name })
    }

    @Test
    fun setSearchQuery_cancelsPendingSearch() = runTest(dispatcher) {
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
            listSummary(
                id = "3",
                name = "Viaje",
                createdAt = Instant.parse("2026-03-20T10:00:00Z"),
            ),
        )
        val repository = ImmediateRepository(Result.success(items))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.setSearchQuery("tra")
        runCurrent()
        advanceTimeBy(150)

        viewModel.setSearchQuery("comp")
        runCurrent()
        advanceTimeBy(249)
        runCurrent()

        assertEquals("comp", viewModel.state.value.searchQuery)
        assertEquals(listOf("Viaje", "Trabajo", "Compra semanal"), viewModel.state.value.items.map { it.name })

        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("Compra semanal"), viewModel.state.value.items.map { it.name })
    }

    @Test
    fun deleteList_removesItemFromState() = runTest(dispatcher) {
        val repository = MutableRepository()
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.createList("Para borrar", false, isGroup = false)
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

        viewModel.createList("Original", true, isGroup = false)
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
    fun setListGroup_groupsListAndAppliesInheritedContributors() = runTest(dispatcher) {
        val repository = MutableRepository()
        val group = listSummary(
            id = "group-1",
            name = "Viajes",
            isGroup = true,
            directContributors = listOf(OWNER_ID, "friend-1"),
        )
        repository.seed(group)
        repository.seed(listSummary(id = "list-1", name = "Maleta"))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.setListGroup(
            list = viewModel.state.value.items.first { item -> item.id == "list-1" },
            group = viewModel.state.value.items.first { item -> item.id == "group-1" },
        )
        advanceUntilIdle()

        val groupedList = viewModel.state.value.items.first { item -> item.id == "list-1" }
        assertEquals("group-1", groupedList.groupId)
        assertEquals(0, groupedList.groupOrder)
        assertTrue("friend-1" in groupedList.inheritedGroupContributors)
        assertTrue("friend-1" in groupedList.contributors)
        assertTrue(groupedList.directSharedWithUserIds.isEmpty())
    }

    @Test
    fun setListGroup_removesOnlyInheritedGroupAccessWhenUngrouping() = runTest(dispatcher) {
        val repository = MutableRepository()
        val group = listSummary(
            id = "group-1",
            name = "Viajes",
            isGroup = true,
            directContributors = listOf(OWNER_ID, "friend-1"),
        )
        repository.seed(group)
        repository.seed(
            listSummary(
                id = "list-1",
                name = "Maleta",
                groupId = "group-1",
                directContributors = listOf(OWNER_ID, "friend-2"),
                inheritedGroupContributors = listOf(OWNER_ID, "friend-1"),
            ),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.setListGroup(
            list = viewModel.state.value.items.first { item -> item.id == "list-1" },
            group = null,
        )
        advanceUntilIdle()

        val ungroupedList = viewModel.state.value.items.first { item -> item.id == "list-1" }
        assertEquals(null, ungroupedList.groupId)
        assertTrue(ungroupedList.inheritedGroupContributors.isEmpty())
        assertEquals(listOf("friend-2"), ungroupedList.directSharedWithUserIds)
        assertTrue("friend-1" !in ungroupedList.contributors)
        assertTrue("friend-2" in ungroupedList.contributors)
    }

    @Test
    fun deleteList_groupKeepsChildrenUngrouped() = runTest(dispatcher) {
        val repository = MutableRepository()
        val group = listSummary(
            id = "group-1",
            name = "Viajes",
            isGroup = true,
            directContributors = listOf(OWNER_ID, "friend-1"),
        )
        repository.seed(group)
        repository.seed(
            listSummary(
                id = "list-1",
                name = "Maleta",
                groupId = "group-1",
                inheritedGroupContributors = listOf(OWNER_ID, "friend-1"),
            ),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.deleteList(viewModel.state.value.items.first { item -> item.id == "group-1" })
        advanceUntilIdle()

        assertEquals(1, repository.deleteCalls)
        assertEquals(listOf("list-1"), viewModel.state.value.items.map { item -> item.id })
        assertEquals(null, viewModel.state.value.items.single().groupId)
        assertTrue(viewModel.state.value.items.single().inheritedGroupContributors.isEmpty())
    }

    @Test
    fun setListArchived_archivesListLocallyAndKeepsCurrentSort() = runTest(dispatcher) {
        val repository = MutableRepository()
        repository.seed(listSummary(id = "z", name = "Zeta"))
        repository.seed(listSummary(id = "a", name = "Alfa"))
        val viewModel = buildViewModel(repository)

        viewModel.setSort(SortOption.NAME_ASC)
        viewModel.load()
        advanceUntilIdle()

        val list = viewModel.state.value.items.last()
        viewModel.setListArchived(list, archived = true)
        advanceUntilIdle()

        assertEquals(listOf("Alfa", "Zeta"), viewModel.state.value.items.map { it.name })
        assertEquals(listOf(OWNER_ID), viewModel.state.value.items.last().archivedBy)
        assertTrue(viewModel.state.value.items.last().archivedAtBy[OWNER_ID] != null)
        assertEquals(1, repository.archiveCalls)
        assertEquals(OWNER_ID, repository.lastArchiveOwnerId)
        assertEquals("z", repository.lastArchiveListId)
        assertEquals(true, repository.lastArchiveValue)
        assertEquals(ListArchiveAction.ARCHIVED, viewModel.state.value.archiveFeedback?.action)
        assertTrue(viewModel.state.value.archiveFeedback?.isSuccess == true)
    }

    @Test
    fun setListArchived_unarchivesOnlyCurrentUser() = runTest(dispatcher) {
        val repository = MutableRepository()
        repository.seed(
            listSummary(
                id = "archived",
                name = "Archivada",
                archivedBy = listOf(OWNER_ID, "other-user"),
                archivedAtBy = mapOf(
                    OWNER_ID to ARCHIVED_AT,
                    "other-user" to OTHER_ARCHIVED_AT,
                ),
            ),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()
        val list = viewModel.state.value.items.single()

        viewModel.setListArchived(list, archived = false)
        advanceUntilIdle()

        assertEquals(listOf("other-user"), viewModel.state.value.items.single().archivedBy)
        assertEquals(mapOf("other-user" to OTHER_ARCHIVED_AT), viewModel.state.value.items.single().archivedAtBy)
        assertEquals(ListArchiveAction.UNARCHIVED, viewModel.state.value.archiveFeedback?.action)
        assertTrue(viewModel.state.value.archiveFeedback?.isSuccess == true)
    }

    @Test
    fun setListArchived_allowsSharedList() = runTest(dispatcher) {
        val repository = MutableRepository()
        repository.seed(
            listSummary(
                id = "shared",
                name = "Compartida",
                ownerId = SHARED_OWNER_ID,
            ),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()
        val list = viewModel.state.value.items.single()

        viewModel.setListArchived(list, archived = true)
        advanceUntilIdle()

        assertEquals(1, repository.archiveCalls)
        assertEquals(SHARED_OWNER_ID, repository.lastArchiveOwnerId)
        assertEquals(listOf(OWNER_ID), viewModel.state.value.items.single().archivedBy)
        assertTrue(viewModel.state.value.items.single().archivedAtBy[OWNER_ID] != null)
        assertTrue(viewModel.state.value.archiveFeedback?.isSuccess == true)
    }

    @Test
    fun setListArchived_errorKeepsPreviousStateAndPublishesFailure() = runTest(dispatcher) {
        val list = listSummary(id = "list-1", name = "Trabajo")
        val repository = FailingArchiveRepository(list)
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.setListArchived(list, archived = true)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.items.single().archivedBy.isEmpty())
        assertTrue(viewModel.state.value.items.single().archivedAtBy.isEmpty())
        assertEquals(ListArchiveAction.ARCHIVED, viewModel.state.value.archiveFeedback?.action)
        assertFalse(viewModel.state.value.archiveFeedback?.isSuccess == true)
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

    @Test
    fun shareList_groupPropagatesInheritedAccessToChildren() = runTest(dispatcher) {
        val repository = MutableRepository()
        val friend = FriendSummary("friendship-1", "friend-1", "Marina")
        repository.seed(listSummary(id = "group-1", name = "Viajes", isGroup = true))
        repository.seed(listSummary(id = "list-1", name = "Maleta", groupId = "group-1"))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.shareList(
            list = viewModel.state.value.items.first { item -> item.id == "group-1" },
            friend = friend,
        )
        advanceUntilIdle()

        val group = viewModel.state.value.items.first { item -> item.id == "group-1" }
        val child = viewModel.state.value.items.first { item -> item.id == "list-1" }
        assertEquals(listOf("friend-1"), group.directSharedWithUserIds)
        assertTrue("friend-1" in child.inheritedGroupContributors)
        assertTrue("friend-1" in child.contributors)
        assertTrue(child.directSharedWithUserIds.isEmpty())
    }

    @Test
    fun unshareList_groupPreservesIndividualChildShare() = runTest(dispatcher) {
        val repository = MutableRepository()
        repository.seed(
            listSummary(
                id = "group-1",
                name = "Viajes",
                isGroup = true,
                directContributors = listOf(OWNER_ID, "friend-1"),
            ),
        )
        repository.seed(
            listSummary(
                id = "list-1",
                name = "Maleta",
                groupId = "group-1",
                directContributors = listOf(OWNER_ID, "friend-2"),
                inheritedGroupContributors = listOf(OWNER_ID, "friend-1"),
            ),
        )
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.unshareList(
            list = viewModel.state.value.items.first { item -> item.id == "group-1" },
            collaborator = ListCollaborator("friend-1", "Marina"),
        )
        advanceUntilIdle()

        val child = viewModel.state.value.items.first { item -> item.id == "list-1" }
        assertTrue("friend-1" !in child.inheritedGroupContributors)
        assertTrue("friend-1" !in child.contributors)
        assertEquals(listOf("friend-2"), child.directSharedWithUserIds)
        assertTrue("friend-2" in child.contributors)
    }

    @Test
    fun reorderGroupedLists_updatesChildOrder() = runTest(dispatcher) {
        val repository = MutableRepository()
        val group = listSummary(id = "group-1", name = "Viajes", isGroup = true)
        repository.seed(group)
        repository.seed(listSummary(id = "list-a", name = "A", groupId = "group-1", groupOrder = 0))
        repository.seed(listSummary(id = "list-b", name = "B", groupId = "group-1", groupOrder = 1))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.reorderGroupedLists(group, listOf("list-b", "list-a"))
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.items.first { item -> item.id == "list-a" }.groupOrder)
        assertEquals(0, viewModel.state.value.items.first { item -> item.id == "list-b" }.groupOrder)
    }

    @Test
    fun reorderGroupedLists_rollsBackOnError() = runTest(dispatcher) {
        val repository = MutableRepository(failReorder = true)
        val group = listSummary(id = "group-1", name = "Viajes", isGroup = true)
        repository.seed(group)
        repository.seed(listSummary(id = "list-a", name = "A", groupId = "group-1", groupOrder = 0))
        repository.seed(listSummary(id = "list-b", name = "B", groupId = "group-1", groupOrder = 1))
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        viewModel.reorderGroupedLists(group, listOf("list-b", "list-a"))
        advanceUntilIdle()

        assertEquals(0, viewModel.state.value.items.first { item -> item.id == "list-a" }.groupOrder)
        assertEquals(1, viewModel.state.value.items.first { item -> item.id == "list-b" }.groupOrder)
        assertEquals("error al reordenar", viewModel.state.value.errorMessage)
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
        override suspend fun createList(
            name: String,
            ordered: Boolean,
            isGroup: Boolean,
        ): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun reorderGroupedLists(groupId: String, listIdsInOrder: List<String>): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())

        override suspend fun setListArchived(
            ownerId: String,
            listId: String,
            archived: Boolean,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    private class ControlledRepository(
        private val result: Result<List<NotesListSummary>>,
    ) : NotesListsRepository {
        private val gate = CompletableDeferred<Unit>()

        override suspend fun getLists(): Result<List<NotesListSummary>> {
            gate.await()
            return result
        }

        override suspend fun createList(
            name: String,
            ordered: Boolean,
            isGroup: Boolean,
        ): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun reorderGroupedLists(groupId: String, listIdsInOrder: List<String>): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())

        override suspend fun setListArchived(
            ownerId: String,
            listId: String,
            archived: Boolean,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())

        fun release() {
            gate.complete(Unit)
        }
    }

    private class MutableRepository(
        private val failReorder: Boolean = false,
    ) : NotesListsRepository {
        private val lists = mutableListOf<NotesListSummary>()
        var deleteCalls = 0
            private set
        var updateCalls = 0
            private set
        var shareCalls = 0
            private set
        var unshareCalls = 0
            private set
        var archiveCalls = 0
            private set
        var lastSharedFriendId: String? = null
            private set
        var lastUnsharedFriendId: String? = null
            private set
        var lastArchiveOwnerId: String? = null
            private set
        var lastArchiveListId: String? = null
            private set
        var lastArchiveValue: Boolean? = null
            private set

        fun seed(list: NotesListSummary) {
            lists += list
        }

        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(lists.toList())

        override suspend fun createList(
            name: String,
            ordered: Boolean,
            isGroup: Boolean,
        ): Result<NotesListSummary> {
            val item = listSummary(
                id = "new-${lists.size + 1}",
                name = name,
                createdAt = Instant.parse("2026-04-09T12:00:00Z"),
                isOrdered = ordered,
                isGroup = isGroup,
            )
            lists.add(item)
            return Result.success(item)
        }

        override suspend fun deleteList(listId: String): Result<Unit> {
            deleteCalls += 1
            val list = lists.firstOrNull { it.id == listId }
            if (list?.isGroup == true) {
                lists.indices.forEach { index ->
                    if (lists[index].ownerId == list.ownerId && lists[index].groupId == list.id) {
                        lists[index] = lists[index].withGroup(null, groupOrder = 0)
                    }
                }
            }
            lists.removeAll { it.id == listId }
            return Result.success(Unit)
        }

        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> {
            shareCalls += 1
            lastSharedFriendId = friendUserId
            val index = lists.indexOfFirst { it.id == listId }
            if (index != -1) {
                val directContributors = (lists[index].directContributors + friendUserId).distinct()
                lists[index] = lists[index].withDirectContributors(directContributors)
                if (lists[index].isGroup) {
                    lists.propagateGroupContributor(lists[index], friendUserId, added = true)
                }
            }
            return Result.success(Unit)
        }

        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> {
            unshareCalls += 1
            lastUnsharedFriendId = friendUserId
            val index = lists.indexOfFirst { it.id == listId }
            if (index != -1) {
                val directContributors = lists[index].directContributors.filterNot { contributorId ->
                    contributorId == friendUserId
                }
                lists[index] = lists[index].withDirectContributors(directContributors)
                if (lists[index].isGroup) {
                    lists.propagateGroupContributor(lists[index], friendUserId, added = false)
                }
            }
            return Result.success(Unit)
        }

        override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> {
            val index = lists.indexOfFirst { it.id == listId }
            if (index == -1) return Result.failure(IllegalStateException("List not found"))
            val group = groupId?.let { id -> lists.firstOrNull { it.id == id && it.isGroup } }
                ?: if (groupId == null) null else return Result.failure(IllegalStateException("Group not found"))
            val groupOrder = if (group == null) {
                0
            } else {
                lists.count { it.groupId == group.id && it.id != listId }
            }
            lists[index] = lists[index].withGroup(group, groupOrder)
            return Result.success(Unit)
        }

        override suspend fun reorderGroupedLists(groupId: String, listIdsInOrder: List<String>): Result<Unit> {
            if (failReorder) return Result.failure(IllegalStateException("error al reordenar"))
            val reordered = lists
                .filter { it.groupId == groupId }
                .applyGroupOrder(listIdsInOrder)
                ?: return Result.failure(IllegalStateException("Invalid group order"))
            reordered.forEach { reorderedList ->
                val index = lists.indexOfFirst { it.id == reorderedList.id }
                if (index != -1) {
                    lists[index] = reorderedList
                }
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

        override suspend fun setListArchived(
            ownerId: String,
            listId: String,
            archived: Boolean,
        ): Result<Unit> {
            archiveCalls += 1
            lastArchiveOwnerId = ownerId
            lastArchiveListId = listId
            lastArchiveValue = archived
            val index = lists.indexOfFirst { it.ownerId == ownerId && it.id == listId }
            if (index == -1) return Result.failure(IllegalStateException("List not found"))
            val archivedBy = if (archived) {
                (lists[index].archivedBy + OWNER_ID).distinct()
            } else {
                lists[index].archivedBy.filterNot { userId -> userId == OWNER_ID }
            }
            val archivedAtBy = if (archived) {
                lists[index].archivedAtBy + (OWNER_ID to ARCHIVED_AT)
            } else {
                lists[index].archivedAtBy - OWNER_ID
            }
            lists[index] = lists[index].copy(
                archivedBy = archivedBy,
                archivedAtBy = archivedAtBy,
            )
            return Result.success(Unit)
        }
    }

    private class FailingCreateRepository : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(emptyList())

        override suspend fun createList(
            name: String,
            ordered: Boolean,
            isGroup: Boolean,
        ): Result<NotesListSummary> =
            Result.failure(IllegalStateException("error al crear"))
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun reorderGroupedLists(groupId: String, listIdsInOrder: List<String>): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())

        override suspend fun setListArchived(
            ownerId: String,
            listId: String,
            archived: Boolean,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    private class FailingDeleteRepository : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(emptyList())
        override suspend fun createList(
            name: String,
            ordered: Boolean,
            isGroup: Boolean,
        ): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(IllegalStateException("error al eliminar"))
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun reorderGroupedLists(groupId: String, listIdsInOrder: List<String>): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())

        override suspend fun setListArchived(
            ownerId: String,
            listId: String,
            archived: Boolean,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    private class FailingUpdateRepository : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(emptyList())
        override suspend fun createList(
            name: String,
            ordered: Boolean,
            isGroup: Boolean,
        ): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())
        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun reorderGroupedLists(groupId: String, listIdsInOrder: List<String>): Result<Unit> =
            Result.failure(UnsupportedOperationException())
        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(IllegalStateException("error al actualizar"))

        override suspend fun setListArchived(
            ownerId: String,
            listId: String,
            archived: Boolean,
        ): Result<Unit> = Result.failure(UnsupportedOperationException())
    }

    private class FailingArchiveRepository(
        private val list: NotesListSummary,
    ) : NotesListsRepository {
        override suspend fun getLists(): Result<List<NotesListSummary>> =
            Result.success(listOf(list))

        override suspend fun createList(
            name: String,
            ordered: Boolean,
            isGroup: Boolean,
        ): Result<NotesListSummary> =
            Result.failure(UnsupportedOperationException())

        override suspend fun deleteList(listId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun shareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun unshareList(listId: String, friendUserId: String): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun setListGroup(listId: String, groupId: String?): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun reorderGroupedLists(groupId: String, listIdsInOrder: List<String>): Result<Unit> =
            Result.failure(UnsupportedOperationException())

        override suspend fun updateList(
            listId: String,
            name: String,
            ordered: Boolean,
        ): Result<NotesListSummary> = Result.failure(UnsupportedOperationException())

        override suspend fun setListArchived(
            ownerId: String,
            listId: String,
            archived: Boolean,
        ): Result<Unit> = Result.failure(IllegalStateException("error al archivar"))
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
        val ARCHIVED_AT = Instant.parse("2026-04-10T12:00:00Z")
        val OTHER_ARCHIVED_AT = Instant.parse("2026-04-11T12:00:00Z")

        fun listSummary(
            id: String,
            name: String,
            ownerId: String = OWNER_ID,
            createdAt: Instant = Instant.parse("2026-03-28T12:00:00Z"),
            isOrdered: Boolean = false,
            isGroup: Boolean = false,
            groupId: String? = null,
            groupOrder: Int = 0,
            isShared: Boolean? = null,
            contributors: List<String> = emptyList(),
            directContributors: List<String>? = null,
            inheritedGroupContributors: List<String> = emptyList(),
            archivedBy: List<String> = emptyList(),
            archivedAtBy: Map<String, Instant> = emptyMap(),
        ): NotesListSummary {
            val resolvedDirectContributors = directContributors ?: if (contributors.isEmpty()) {
                if (ownerId == OWNER_ID) {
                    listOf(ownerId)
                } else {
                    listOf(ownerId, OWNER_ID)
                }
            } else {
                contributors
            }
            val resolvedContributors = if (contributors.isEmpty()) {
                effectiveContributors(
                    ownerId = ownerId,
                    directContributors = resolvedDirectContributors,
                    inheritedGroupContributors = inheritedGroupContributors,
                )
            } else {
                contributors
            }
            return NotesListSummary(
                id = id,
                ownerId = ownerId,
                name = name,
                creator = ownerId,
                createdAt = createdAt,
                isOrdered = isOrdered,
                isGroup = isGroup,
                groupId = groupId,
                groupOrder = groupOrder,
                isShared = isShared ?: (ownerId != OWNER_ID || resolvedContributors.distinct().size > 1),
                contributors = resolvedContributors,
                directContributors = resolvedDirectContributors,
                inheritedGroupContributors = inheritedGroupContributors,
                archivedBy = archivedBy,
                archivedAtBy = archivedAtBy,
            )
        }
    }
}

private fun NotesListSummary.withDirectContributors(updatedContributors: List<String>): NotesListSummary {
    val directContributors = updatedContributors.distinct()
    val contributors = effectiveContributors(
        ownerId = ownerId,
        directContributors = directContributors,
        inheritedGroupContributors = inheritedGroupContributors,
    )
    return copy(
        contributors = contributors,
        directContributors = directContributors,
        isShared = contributors.any { contributorId -> contributorId != ownerId },
    )
}

private fun NotesListSummary.withInheritedGroupContributors(
    updatedContributors: List<String>,
): NotesListSummary {
    val inheritedGroupContributors = updatedContributors.distinct()
    val contributors = effectiveContributors(
        ownerId = ownerId,
        directContributors = directContributors,
        inheritedGroupContributors = inheritedGroupContributors,
    )
    return copy(
        contributors = contributors,
        inheritedGroupContributors = inheritedGroupContributors,
        isShared = contributors.any { contributorId -> contributorId != ownerId },
    )
}

private fun NotesListSummary.withGroup(group: NotesListSummary?, groupOrder: Int): NotesListSummary =
    copy(
        groupId = group?.id,
        groupOrder = if (group == null) 0 else groupOrder,
    ).withInheritedGroupContributors(group?.directContributors.orEmpty())

private fun MutableList<NotesListSummary>.propagateGroupContributor(
    group: NotesListSummary,
    friendUserId: String,
    added: Boolean,
) {
    indices.forEach { index ->
        val item = this[index]
        if (item.ownerId == group.ownerId && item.groupId == group.id) {
            val inheritedContributors = if (added) {
                item.inheritedGroupContributors + friendUserId
            } else {
                item.inheritedGroupContributors.filterNot { contributorId -> contributorId == friendUserId }
            }
            this[index] = item.withInheritedGroupContributors(inheritedContributors)
        }
    }
}
