package com.chemecador.secretaria.notes

import com.chemecador.secretaria.firestore.FirebaseIosFirestoreHttpResponse
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRequest
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreRestApi
import com.chemecador.secretaria.firestore.FirebaseIosFirestoreTransport
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIosIdTokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreIosNotesRepositoryTest {

    @Test
    fun createNote_usesNextOrderAndMapsCreatedDocument() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """{"documents":[]}""",
                ),
                FirebaseIosFirestoreHttpResponse(
                    statusCode = 200,
                    body = """
                        {
                          "name": "projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1/notes/note-1",
                          "fields": {
                            "title": { "stringValue": "Nueva nota" },
                            "content": { "stringValue": "Contenido" },
                            "date": { "timestampValue": "2026-04-12T10:00:00Z" },
                            "completed": { "booleanValue": false },
                            "order": { "integerValue": "0" },
                            "creator": { "stringValue": "user-123" },
                            "color": { "integerValue": "4294967295" }
                          }
                        }
                    """.trimIndent(),
                ),
            ),
        )
        val repository = FirestoreIosNotesRepository(
            authRepository = LoggedInAuthRepository("user-123"),
            firestore = FirebaseIosFirestoreRestApi(
                projectId = "project-id",
                tokenProvider = StaticTokenProvider("ios-token"),
                transport = transport,
            ),
        )

        val result = repository.createNote(
            listId = "list-1",
            title = "Nueva nota",
            content = "Contenido",
        )

        assertTrue(result.isSuccess)
        val note = result.getOrThrow()
        assertEquals("note-1", note.id)
        assertEquals("Nueva nota", note.title)
        assertEquals(0, note.order)
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1/notes",
            transport.requests[0].url,
        )
        assertEquals(
            "https://firestore.googleapis.com/v1/projects/project-id/databases/(default)/documents/users/user-123/noteslist/list-1/notes",
            transport.requests[1].url,
        )
        assertEquals(
            "Bearer ios-token",
            transport.requests[1].headers["Authorization"],
        )
        assertTrue(transport.requests[1].body!!.contains(""""order":{"integerValue":"0"}"""))
        assertTrue(transport.requests[1].body!!.contains(""""creator":{"stringValue":"user-123"}"""))
    }

    private class RecordingFirestoreTransport(
        responses: List<FirebaseIosFirestoreHttpResponse>,
    ) : FirebaseIosFirestoreTransport {

        private val pendingResponses = ArrayDeque(responses)
        val requests = mutableListOf<FirebaseIosFirestoreRequest>()

        override suspend fun execute(request: FirebaseIosFirestoreRequest): FirebaseIosFirestoreHttpResponse {
            requests += request
            return pendingResponses.removeFirst()
        }
    }

    private class StaticTokenProvider(
        private val token: String,
    ) : FirebaseIosIdTokenProvider {
        override suspend fun getFreshIdToken(): String = token
    }

    private class LoggedInAuthRepository(
        override val currentUserId: String?,
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun signup(email: String, password: String): Result<Unit> =
            Result.success(Unit)

        override suspend fun loginWithGoogle(): Result<Unit> = Result.success(Unit)
        override suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
    }
}
