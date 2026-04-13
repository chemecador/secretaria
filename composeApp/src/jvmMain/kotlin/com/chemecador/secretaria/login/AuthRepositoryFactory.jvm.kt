package com.chemecador.secretaria.login

import com.chemecador.secretaria.config.DesktopBuildConfig
import com.chemecador.secretaria.config.readLocalProperty

private const val FIREBASE_API_KEY_PROPERTY = "secretaria.firebaseApiKey"
private const val FIREBASE_API_KEY_ENV = "SECRETARIA_FIREBASE_API_KEY"
private const val GOOGLE_DESKTOP_CLIENT_ID_PROPERTY = "secretaria.googleDesktopClientId"
private const val GOOGLE_DESKTOP_CLIENT_ID_ENV = "SECRETARIA_GOOGLE_DESKTOP_CLIENT_ID"
private const val GOOGLE_DESKTOP_CLIENT_SECRET_PROPERTY = "secretaria.googleDesktopClientSecret"
private const val GOOGLE_DESKTOP_CLIENT_SECRET_ENV = "SECRETARIA_GOOGLE_DESKTOP_CLIENT_SECRET"

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

internal fun resolveGoogleDesktopClientId(
    propertyProvider: (String) -> String? = System::getProperty,
    environmentProvider: (String) -> String? = System::getenv,
    localPropertiesProvider: (String) -> String? = ::readLocalProperty,
    buildConfigProvider: () -> String? = { DesktopBuildConfig.googleDesktopClientId },
): String? =
    propertyProvider(GOOGLE_DESKTOP_CLIENT_ID_PROPERTY)
        .takeUnless { it.isNullOrBlank() }
        ?: environmentProvider(GOOGLE_DESKTOP_CLIENT_ID_ENV)
            .takeUnless { it.isNullOrBlank() }
        ?: localPropertiesProvider(GOOGLE_DESKTOP_CLIENT_ID_PROPERTY)
            .takeUnless { it.isNullOrBlank() }
        ?: buildConfigProvider()
            .takeUnless { it.isNullOrBlank() }

internal fun resolveGoogleDesktopClientSecret(
    propertyProvider: (String) -> String? = System::getProperty,
    environmentProvider: (String) -> String? = System::getenv,
    localPropertiesProvider: (String) -> String? = ::readLocalProperty,
    buildConfigProvider: () -> String? = { DesktopBuildConfig.googleDesktopClientSecret },
): String? =
    propertyProvider(GOOGLE_DESKTOP_CLIENT_SECRET_PROPERTY)
        .takeUnless { it.isNullOrBlank() }
        ?: environmentProvider(GOOGLE_DESKTOP_CLIENT_SECRET_ENV)
            .takeUnless { it.isNullOrBlank() }
        ?: localPropertiesProvider(GOOGLE_DESKTOP_CLIENT_SECRET_PROPERTY)
            .takeUnless { it.isNullOrBlank() }
        ?: buildConfigProvider()
            .takeUnless { it.isNullOrBlank() }
