package com.chemecador.secretaria.notes

import com.chemecador.secretaria.firestore.FirebaseFirestoreHttpResponse
import com.chemecador.secretaria.firestore.FirebaseFirestoreRequest
import com.chemecador.secretaria.firestore.FirebaseFirestoreRestApi
import com.chemecador.secretaria.firestore.FirebaseFirestoreTransport
import com.chemecador.secretaria.login.AuthRepository
import com.chemecador.secretaria.login.FirebaseIdTokenProvider
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FirestoreRestNotesRepositoryTest {

    @Test
    fun createNote_usesNextOrderAndMapsCreatedDocument() = kotlinx.coroutines.test.runTest {
        val transport = RecordingFirestoreTransport(
            responses = listOf(
                FirebaseFirestoreHttpResponse(
                    statusCode = 200,
                    body = """{"documents":[]}""",
                ),
                FirebaseFirestoreHttpResponse(
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
        val repository = FirestoreRestNotesRepository(
            authRepository = LoggedInAuthRepository("user-123"),
            firestore = FirebaseFirestoreRestApi(
                projectId = "project-id",
                tokenProvider = StaticTokenProvider("desktop-token"),
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
            "Bearer desktop-token",
            transport.requests[1].headers["Authorization"],
        )
        assertTrue(transport.requests[1].body!!.contains(""""order":{"integerValue":"0"}"""))
        assertTrue(transport.requests[1].body!!.contains(""""creator":{"stringValue":"user-123"}"""))
    }

    private class RecordingFirestoreTransport(
        responses: List<FirebaseFirestoreHttpResponse>,
    ) : FirebaseFirestoreTransport {

        private val pendingResponses = ArrayDeque(responses)
        val requests = mutableListOf<FirebaseFirestoreRequest>()

        override suspend fun execute(request: FirebaseFirestoreRequest): FirebaseFirestoreHttpResponse {
            requests += request
            return pendingResponses.removeFirst()
        }
    }

    private class StaticTokenProvider(
        private val token: String,
    ) : FirebaseIdTokenProvider {
        override suspend fun getFreshIdToken(): String = token
    }

    private class LoggedInAuthRepository(
        override val currentUserId: String?,
    ) : AuthRepository {
        override suspend fun login(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signup(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun loginWithGoogle(): Result<Unit> = Result.success(Unit)
        override suspend fun loginAsGuest(): Result<Unit> = Result.success(Unit)
    }
}
