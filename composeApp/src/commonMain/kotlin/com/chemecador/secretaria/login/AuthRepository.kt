package com.chemecador.secretaria.login

interface AuthRepository {
    suspend fun login(email: String, password: String): Result<Unit>
    suspend fun signup(email: String, password: String): Result<Unit>
    suspend fun loginWithGoogle(): Result<Unit>
    suspend fun loginAsGuest(): Result<Unit>
}
