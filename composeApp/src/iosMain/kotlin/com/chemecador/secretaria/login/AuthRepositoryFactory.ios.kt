package com.chemecador.secretaria.login

actual fun createAuthRepository(): AuthRepository =
    FirebaseIosAuthRepository(apiKey = resolveIosFirebaseApiKey())
