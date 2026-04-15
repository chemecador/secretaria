package com.chemecador.secretaria.login

import kotlinx.coroutines.delay

class FakeAuthRepository : AuthRepository {

    private var _userId: String? = null
    private var _userEmail: String? = null
    override val currentUserId: String? get() = _userId
    override val currentUserEmail: String? get() = _userEmail

    override suspend fun login(email: String, password: String): Result<Unit> {
        delay(500)
        _userId = "Alex"
        _userEmail = email
        return Result.success(Unit)
    }

    override suspend fun signup(email: String, password: String): Result<Unit> {
        delay(500)
        _userId = "Alex"
        _userEmail = email
        return Result.success(Unit)
    }

    override suspend fun loginWithGoogle(idToken: String?): Result<Unit> {
        delay(500)
        _userId = "Alex"
        _userEmail = "fake@example.com"
        return Result.success(Unit)
    }

    override suspend fun loginAsGuest(): Result<Unit> {
        delay(300)
        _userId = "fake-guest"
        _userEmail = null
        return Result.success(Unit)
    }

    override suspend fun logout(): Result<Unit> {
        _userId = null
        _userEmail = null
        return Result.success(Unit)
    }

    override suspend fun restoreSession(): Result<Boolean> =
        Result.success(_userId != null)
}
