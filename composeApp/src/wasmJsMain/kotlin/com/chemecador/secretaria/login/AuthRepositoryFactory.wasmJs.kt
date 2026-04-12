package com.chemecador.secretaria.login

actual fun createAuthRepository(): AuthRepository = FakeAuthRepository()
