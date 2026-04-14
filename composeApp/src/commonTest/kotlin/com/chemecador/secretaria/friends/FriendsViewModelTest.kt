package com.chemecador.secretaria.friends

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class FriendsViewModelTest {

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
    fun load_populatesFriendsRequestsAndCode() = runTest(dispatcher) {
        val repository = MutableFriendsRepository()
        val viewModel = FriendsViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertFalse(state.isLoading)
        assertEquals("261051", state.userCode)
        assertEquals(1, state.friends.size)
        assertEquals(1, state.incomingRequests.size)
        assertEquals(1, state.outgoingRequests.size)
        assertEquals(null, state.contentError)
    }

    @Test
    fun sendFriendRequest_withInvalidCodePublishesValidationMessage() = runTest(dispatcher) {
        val repository = MutableFriendsRepository()
        val viewModel = FriendsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.sendFriendRequest("abc")
        advanceUntilIdle()

        assertEquals(FriendsMessage.INVALID_FRIEND_CODE, viewModel.state.value.message)
        assertTrue(repository.sentCodes.isEmpty())
    }

    @Test
    fun sendFriendRequest_successRefreshesOutgoingRequests() = runTest(dispatcher) {
        val repository = MutableFriendsRepository()
        val viewModel = FriendsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.sendFriendRequest("26010602")
        advanceUntilIdle()

        assertEquals(FriendsMessage.REQUEST_SENT, viewModel.state.value.message)
        assertEquals(listOf("26010602"), repository.sentCodes)
        assertEquals(2, viewModel.state.value.outgoingRequests.size)
    }

    @Test
    fun acceptFriendRequest_movesRequestToFriends() = runTest(dispatcher) {
        val repository = MutableFriendsRepository()
        val viewModel = FriendsViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.acceptFriendRequest("incoming-1")
        advanceUntilIdle()

        assertEquals(FriendsMessage.REQUEST_ACCEPTED, viewModel.state.value.message)
        assertTrue(viewModel.state.value.incomingRequests.isEmpty())
        assertEquals(2, viewModel.state.value.friends.size)
    }
}

private class MutableFriendsRepository : FriendsRepository {
    val sentCodes = mutableListOf<String>()

    private var userCode = "261051"
    private val friends = mutableListOf(
        FriendSummary(
            friendshipId = "friendship-1",
            userId = "friend-1",
            name = "Marina",
        ),
    )
    private val incomingRequests = mutableListOf(
        IncomingFriendRequest(
            id = "incoming-1",
            senderId = "friend-2",
            senderName = "Carlos",
            requestedAt = Instant.parse("2026-04-13T10:00:00Z"),
        ),
    )
    private val outgoingRequests = mutableListOf(
        OutgoingFriendRequest(
            id = "outgoing-1",
            receiverId = "friend-3",
            receiverCode = "2610499",
            requestedAt = Instant.parse("2026-04-12T10:00:00Z"),
        ),
    )

    override suspend fun getMyFriendCode(): Result<String> = Result.success(userCode)

    override suspend fun getFriends(): Result<List<FriendSummary>> =
        Result.success(friends.toList())

    override suspend fun getIncomingRequests(): Result<List<IncomingFriendRequest>> =
        Result.success(incomingRequests.toList())

    override suspend fun getOutgoingRequests(): Result<List<OutgoingFriendRequest>> =
        Result.success(outgoingRequests.toList())

    override suspend fun sendFriendRequest(friendCode: String): Result<Unit> =
        runCatching {
            sentCodes += friendCode
            outgoingRequests += OutgoingFriendRequest(
                id = "outgoing-${outgoingRequests.size + 1}",
                receiverId = "friend-${outgoingRequests.size + 10}",
                receiverCode = friendCode,
                requestedAt = Instant.parse("2026-04-14T10:00:00Z"),
            )
        }

    override suspend fun acceptFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            val request = incomingRequests.first { it.id == requestId }
            incomingRequests.removeAll { it.id == requestId }
            friends += FriendSummary(
                friendshipId = request.id,
                userId = request.senderId,
                name = request.senderName,
            )
        }

    override suspend fun rejectFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            incomingRequests.removeAll { it.id == requestId }
        }

    override suspend fun cancelFriendRequest(requestId: String): Result<Unit> =
        runCatching {
            outgoingRequests.removeAll { it.id == requestId }
        }

    override suspend fun deleteFriend(friendshipId: String): Result<Unit> =
        runCatching {
            friends.removeAll { it.friendshipId == friendshipId }
        }
}
