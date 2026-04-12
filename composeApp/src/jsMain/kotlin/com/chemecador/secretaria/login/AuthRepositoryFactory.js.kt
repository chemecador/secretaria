package com.chemecador.secretaria.login

actual fun createAuthRepository(): AuthRepository =
    FirebaseJsAuthRepository(apiKey = resolveWebFirebaseApiKey())
