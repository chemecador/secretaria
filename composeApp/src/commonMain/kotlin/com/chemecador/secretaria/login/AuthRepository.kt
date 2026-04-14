package com.chemecador.secretaria.login

interface AuthRepository {
    val currentUserId: String?
    val currentUserEmail: String?
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun signup(email: String, password: String): Result<Unit>
    suspend fun loginWithGoogle(idToken: String? = null): Result<Unit>
    suspend fun loginAsGuest(): Result<Unit>
    suspend fun logout(): Result<Unit>

    /**
     * Attempts to restore a previously persisted session on app startup.
     *
     * Returns [Result.success] with `true` when a valid session (fresh or successfully
     * refreshed) is now active, and `false` when there is no persisted session or the
     * refresh failed. Implementations should never bubble user-facing errors here: a
     * failed restore is expected to fall back silently to the login screen.
     */
    suspend fun restoreSession(): Result<Boolean>
}
