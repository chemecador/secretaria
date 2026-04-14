package com.chemecador.secretaria.login

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class FirebaseRestAuthRepositoryRestoreTest {

    @Test
    fun restoreSession_noPersistedSession_returnsFalse() = runTest {
        val store = FakeSessionStore()
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = ArrayDequeTransport(emptyList()),
            sessionStore = store,
        )

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
        assertNull(repository.currentUserId)
        assertEquals(0, store.clearCount)
    }

    @Test
    fun restoreSession_freshSession_populatesRepositoryWithoutNetworkCall() = runTest {
        val now = Instant.parse("2026-04-12T10:00:00Z")
        val store = FakeSessionStore(
            initial = PersistedAuthSession(
                userId = "user-123",
                email = "alex@example.com",
                idToken = "fresh-id-token",
                refreshToken = "refresh-token-1",
                expiresAtEpochSeconds = (now.epochSeconds + 3600),
            ),
        )
        val transport = ArrayDequeTransport(emptyList())
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
            nowProvider = { now },
            sessionStore = store,
        )

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
        assertEquals("user-123", repository.currentUserId)
        assertEquals("alex@example.com", repository.currentUserEmail)
        assertEquals(0, transport.calls)
    }

    @Test
    fun restoreSession_expiredSession_refreshesAndPersistsNewTokens() = runTest {
        val now = Instant.parse("2026-04-12T10:00:00Z")
        val store = FakeSessionStore(
            initial = PersistedAuthSession(
                userId = "user-123",
                email = "alex@example.com",
                idToken = "stale-id-token",
                refreshToken = "refresh-token-1",
                expiresAtEpochSeconds = now.epochSeconds - 60,
            ),
        )
        val transport = ArrayDequeTransport(
            listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "user_id":"user-123",
                          "id_token":"fresh-id-token",
                          "refresh_token":"refresh-token-2",
                          "expires_in":"3600"
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
            nowProvider = { now },
            sessionStore = store,
        )

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow())
        assertEquals("user-123", repository.currentUserId)
        assertEquals(1, transport.calls)
        val persisted = assertNotNull(store.stored)
        assertEquals("fresh-id-token", persisted.idToken)
        assertEquals("refresh-token-2", persisted.refreshToken)
    }

    @Test
    fun restoreSession_expiredSessionWithFailingRefresh_clearsStoreAndReturnsFalse() = runTest {
        val now = Instant.parse("2026-04-12T10:00:00Z")
        val store = FakeSessionStore(
            initial = PersistedAuthSession(
                userId = "user-123",
                email = "alex@example.com",
                idToken = "stale-id-token",
                refreshToken = "revoked-refresh-token",
                expiresAtEpochSeconds = now.epochSeconds - 60,
            ),
        )
        val transport = ArrayDequeTransport(
            listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 401,
                    body = """{"error":{"message":"TOKEN_EXPIRED"}}""",
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
            nowProvider = { now },
            sessionStore = store,
        )

        val result = repository.restoreSession()

        assertTrue(result.isSuccess)
        assertFalse(result.getOrThrow())
        assertNull(repository.currentUserId)
        assertNull(store.stored)
        assertTrue(store.clearCount > 0)
    }

    @Test
    fun login_successPersistsSessionToStore() = runTest {
        val store = FakeSessionStore()
        val transport = ArrayDequeTransport(
            listOf(
                FirebaseAuthHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "localId":"user-123",
                          "idToken":"id-token-1",
                          "refreshToken":"refresh-token-1",
                          "expiresIn":"3600"
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = transport,
            sessionStore = store,
        )

        val result = repository.login("alex@example.com", "clave123")

        assertTrue(result.isSuccess)
        val persisted = assertNotNull(store.stored)
        assertEquals("user-123", persisted.userId)
        assertEquals("id-token-1", persisted.idToken)
        assertEquals("refresh-token-1", persisted.refreshToken)
    }

    @Test
    fun logout_clearsPersistedSession() = runTest {
        val store = FakeSessionStore(
            initial = PersistedAuthSession(
                userId = "user-123",
                email = "alex@example.com",
                idToken = "id-token",
                refreshToken = "refresh-token",
                expiresAtEpochSeconds = 9_999_999_999L,
            ),
        )
        val repository = FirebaseRestAuthRepository(
            apiKey = "test-key",
            transport = ArrayDequeTransport(emptyList()),
            sessionStore = store,
        )

        val result = repository.logout()

        assertTrue(result.isSuccess)
        assertNull(store.stored)
        assertTrue(store.clearCount > 0)
    }

    private class ArrayDequeTransport(
        responses: List<FirebaseAuthHttpResponse>,
    ) : FirebaseAuthTransport {

        private val pending = ArrayDeque(responses)
        var calls: Int = 0
            private set

        override suspend fun post(
            url: String,
            body: String,
            contentType: String,
        ): FirebaseAuthHttpResponse {
            calls += 1
            return pending.removeFirst()
        }
    }
}
