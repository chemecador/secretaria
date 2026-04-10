package com.chemecador.secretaria.login

import kotlinx.coroutines.delay

class FakeAuthRepository : AuthRepository {

    override suspend fun login(email: String, password: String): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun signup(email: String, password: String): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun loginWithGoogle(): Result<Unit> {
        delay(500)
        return Result.success(Unit)
    }

    override suspend fun loginAsGuest(): Result<Unit> {
        delay(300)
        return Result.success(Unit)
    }
}
