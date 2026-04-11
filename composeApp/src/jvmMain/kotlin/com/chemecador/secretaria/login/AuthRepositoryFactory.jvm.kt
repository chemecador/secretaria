package com.chemecador.secretaria.login

private const val FIREBASE_API_KEY_PROPERTY = "secretaria.firebaseApiKey"
private const val FIREBASE_API_KEY_ENV = "SECRETARIA_FIREBASE_API_KEY"

actual fun createAuthRepository(): AuthRepository =
    FirebaseRestAuthRepository(apiKey = resolveFirebaseApiKey())

internal fun resolveFirebaseApiKey(
    propertyProvider: (String) -> String? = System::getProperty,
    environmentProvider: (String) -> String? = System::getenv,
): String =
    propertyProvider(FIREBASE_API_KEY_PROPERTY)
        .takeUnless { it.isNullOrBlank() }
        ?: environmentProvider(FIREBASE_API_KEY_ENV)
            .takeUnless { it.isNullOrBlank() }
        ?: error(
            "Missing Firebase API key for JVM/Desktop auth. " +
                "Set -Dsecretaria.firebaseApiKey=... or SECRETARIA_FIREBASE_API_KEY.",
        )
