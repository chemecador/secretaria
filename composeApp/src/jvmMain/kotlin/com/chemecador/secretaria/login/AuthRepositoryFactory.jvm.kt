package com.chemecador.secretaria.login

import com.chemecador.secretaria.config.DesktopBuildConfig
import com.chemecador.secretaria.config.readLocalProperty

private const val FIREBASE_API_KEY_PROPERTY = "secretaria.firebaseApiKey"
private const val FIREBASE_API_KEY_ENV = "SECRETARIA_FIREBASE_API_KEY"

actual fun createAuthRepository(): AuthRepository =
    FirebaseRestAuthRepository(apiKey = resolveFirebaseApiKey())

internal fun resolveFirebaseApiKey(
    propertyProvider: (String) -> String? = System::getProperty,
    environmentProvider: (String) -> String? = System::getenv,
    localPropertiesProvider: (String) -> String? = ::readLocalProperty,
    buildConfigProvider: () -> String? = { DesktopBuildConfig.firebaseApiKey },
): String =
    propertyProvider(FIREBASE_API_KEY_PROPERTY)
        .takeUnless { it.isNullOrBlank() }
        ?: environmentProvider(FIREBASE_API_KEY_ENV)
            .takeUnless { it.isNullOrBlank() }
        ?: localPropertiesProvider(FIREBASE_API_KEY_PROPERTY)
            .takeUnless { it.isNullOrBlank() }
        ?: buildConfigProvider()
            .takeUnless { it.isNullOrBlank() }
        ?: error(
            "Missing Firebase API key for JVM/Desktop auth. " +
                "Set -Dsecretaria.firebaseApiKey=..., SECRETARIA_FIREBASE_API_KEY, " +
                "or secretaria.firebaseApiKey in local.properties.",
        )
