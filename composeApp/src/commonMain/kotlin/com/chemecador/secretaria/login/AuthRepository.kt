package com.chemecador.secretaria.login

interface AuthRepository {
    val currentUserId: String?
    val currentUserEmail: String?
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun signup(email: String, password: String): Result<Unit>
    suspend fun loginWithGoogle(idToken: String? = null): Result<Unit>
    suspend fun loginAsGuest(): Result<Unit>
    suspend fun logout(): Result<Unit>
}
