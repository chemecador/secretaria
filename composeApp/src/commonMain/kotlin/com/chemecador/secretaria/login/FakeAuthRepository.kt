package com.chemecador.secretaria.login

import kotlinx.coroutines.delay

class FakeAuthRepository : AuthRepository {

    private var _userId: String? = null
    override val currentUserId: String? get() = _userId

    override suspend fun login(email: String, password: String): Result<Unit> {
        delay(500)
        _userId = "fake-user"
        return Result.success(Unit)
    }

    override suspend fun signup(email: String, password: String): Result<Unit> {
        delay(500)
        _userId = "fake-user"
        return Result.success(Unit)
    }

    override suspend fun loginWithGoogle(): Result<Unit> {
        delay(500)
        _userId = "fake-user"
        return Result.success(Unit)
    }

    override suspend fun loginAsGuest(): Result<Unit> {
        delay(300)
        _userId = "fake-guest"
        return Result.success(Unit)
    }
}
