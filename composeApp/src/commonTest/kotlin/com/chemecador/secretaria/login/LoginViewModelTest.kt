package com.chemecador.secretaria.login

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

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

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
    fun login_transitionsFromLoadingToLoggedIn() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository().apply {
            loginResult = Result.success(Unit)
            gate(Operation.LOGIN)
        }
        val viewModel = LoginViewModel(repository)

        viewModel.login("alex@example.com", "secreta")
        runCurrent()

        assertTrue(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoggedIn)
        assertNull(viewModel.state.value.error)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isLoggedIn)
        assertNull(viewModel.state.value.error)
        assertEquals(listOf("alex@example.com" to "secreta"), repository.loginCalls)
    }

    @Test
    fun login_transitionsFromLoadingToError() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository().apply {
            loginResult = Result.failure(AuthException(AuthError.WRONG_PASSWORD))
            gate(Operation.LOGIN)
        }
        val viewModel = LoginViewModel(repository)

        viewModel.login("alex@example.com", "incorrecta")
        runCurrent()

        assertTrue(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)

        repository.release()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoggedIn)
        assertEquals(AuthError.WRONG_PASSWORD, viewModel.state.value.error)
    }

    @Test
    fun signup_successClearsPreviousErrorAndMarksLoggedIn() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository().apply {
            loginResult = Result.failure(AuthException(AuthError.INVALID_USER))
            signupResult = Result.success(Unit)
        }
        val viewModel = LoginViewModel(repository)

        viewModel.login("alex@example.com", "incorrecta")
        advanceUntilIdle()
        assertEquals(AuthError.INVALID_USER, viewModel.state.value.error)

        viewModel.signup("alex@example.com", "secreta")
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isLoggedIn)
        assertNull(viewModel.state.value.error)
        assertEquals(listOf("alex@example.com" to "secreta"), repository.signupCalls)
    }

    @Test
    fun loginWithGoogle_surfacesRepositoryError() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository().apply {
            googleResult = Result.failure(AuthException(AuthError.NOT_SUPPORTED))
        }
        val viewModel = LoginViewModel(repository)

        viewModel.loginWithGoogle()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoggedIn)
        assertEquals(AuthError.NOT_SUPPORTED, viewModel.state.value.error)
        assertEquals(1, repository.googleLoginCalls)
    }

    @Test
    fun loginWithGoogle_usesProvidedToken() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository().apply {
            googleResult = Result.success(Unit)
        }
        val viewModel = LoginViewModel(repository)

        viewModel.loginWithGoogle {
            Result.success("google-id-token")
        }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isLoggedIn)
        assertNull(viewModel.state.value.error)
        assertEquals(listOf<String?>("google-id-token"), repository.googleTokens)
    }

    @Test
    fun loginWithGoogle_cancelledProviderSurfacesCancelledError() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository()
        val viewModel = LoginViewModel(repository)

        viewModel.loginWithGoogle {
            Result.failure(AuthException(AuthError.CANCELLED))
        }
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isLoggedIn)
        assertEquals(AuthError.CANCELLED, viewModel.state.value.error)
        assertEquals(0, repository.googleLoginCalls)
    }

    @Test
    fun loginAsGuest_marksUserAsLoggedIn() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository().apply {
            guestResult = Result.success(Unit)
        }
        val viewModel = LoginViewModel(repository)

        viewModel.loginAsGuest()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.isLoggedIn)
        assertNull(viewModel.state.value.error)
        assertEquals(1, repository.guestLoginCalls)
    }

    @Test
    fun resetState_clearsLoginState() = runTest(dispatcher) {
        val repository = ScriptedAuthRepository().apply {
            loginResult = Result.success(Unit)
        }
        val viewModel = LoginViewModel(repository)

        viewModel.login("alex@example.com", "secreta")
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isLoggedIn)

        viewModel.resetState()

        assertFalse(viewModel.state.value.isLoggedIn)
        assertFalse(viewModel.state.value.isLoading)
        assertNull(viewModel.state.value.error)
    }

    private class ScriptedAuthRepository : AuthRepository {
        override var currentUserId: String? = null
        override var currentUserEmail: String? = null

        var loginResult: Result<Unit> = Result.success(Unit)
        var signupResult: Result<Unit> = Result.success(Unit)
        var googleResult: Result<Unit> = Result.success(Unit)
        var guestResult: Result<Unit> = Result.success(Unit)

        val loginCalls = mutableListOf<Pair<String, String>>()
        val signupCalls = mutableListOf<Pair<String, String>>()
        val googleTokens = mutableListOf<String?>()
        var googleLoginCalls = 0
        var guestLoginCalls = 0

        private var gatedOperation: Operation? = null
        private var gate: CompletableDeferred<Unit>? = null

        override suspend fun login(email: String, password: String): Result<Unit> {
            loginCalls += email to password
            awaitIfNeeded(Operation.LOGIN)
            return loginResult.alsoUpdateUser("email-user")
        }

        override suspend fun signup(email: String, password: String): Result<Unit> {
            signupCalls += email to password
            awaitIfNeeded(Operation.SIGNUP)
            return signupResult.alsoUpdateUser("email-user")
        }

        override suspend fun loginWithGoogle(idToken: String?): Result<Unit> {
            googleLoginCalls += 1
            googleTokens += idToken
            awaitIfNeeded(Operation.GOOGLE)
            return googleResult.alsoUpdateUser("google-user")
        }

        override suspend fun loginAsGuest(): Result<Unit> {
            guestLoginCalls += 1
            awaitIfNeeded(Operation.GUEST)
            return guestResult.alsoUpdateUser("guest-user")
        }

        override suspend fun logout(): Result<Unit> {
            currentUserId = null
            return Result.success(Unit)
        }

        override suspend fun restoreSession(): Result<Boolean> =
            Result.success(currentUserId != null)

        fun gate(operation: Operation) {
            gatedOperation = operation
            gate = CompletableDeferred()
        }

        fun release() {
            gate?.complete(Unit)
        }

        private suspend fun awaitIfNeeded(operation: Operation) {
            if (gatedOperation == operation) {
                gate?.await()
            }
        }

        private fun Result<Unit>.alsoUpdateUser(userId: String): Result<Unit> {
            if (isSuccess) {
                currentUserId = userId
            }
            return this
        }
    }

    private enum class Operation {
        LOGIN,
        SIGNUP,
        GOOGLE,
        GUEST,
    }
}
