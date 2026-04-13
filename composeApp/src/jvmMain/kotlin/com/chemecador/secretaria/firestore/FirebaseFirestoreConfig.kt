package com.chemecador.secretaria.firestore

import com.chemecador.secretaria.config.DesktopBuildConfig
import com.chemecador.secretaria.config.readLocalProperty
import com.chemecador.secretaria.config.readNearbyFileText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val FIREBASE_PROJECT_ID_PROPERTY = "secretaria.firebaseProjectId"
private const val FIREBASE_PROJECT_ID_ENV = "SECRETARIA_FIREBASE_PROJECT_ID"
private const val GOOGLE_SERVICES_PATH = "androidApp/google-services.json"

internal fun resolveFirebaseProjectId(
    propertyProvider: (String) -> String? = System::getProperty,
    environmentProvider: (String) -> String? = System::getenv,
    localPropertiesProvider: (String) -> String? = ::readLocalProperty,
    googleServicesReader: () -> String? = ::readGoogleServicesJson,
    buildConfigProvider: () -> String? = { DesktopBuildConfig.firebaseProjectId },
): String =
    propertyProvider(FIREBASE_PROJECT_ID_PROPERTY)
        .takeUnless { it.isNullOrBlank() }
        ?: environmentProvider(FIREBASE_PROJECT_ID_ENV)
            .takeUnless { it.isNullOrBlank() }
        ?: localPropertiesProvider(FIREBASE_PROJECT_ID_PROPERTY)
            .takeUnless { it.isNullOrBlank() }
        ?: googleServicesReader()
            ?.let(::parseProjectIdFromGoogleServicesJson)
        ?: buildConfigProvider()
            .takeUnless { it.isNullOrBlank() }
        ?: error(
            "Missing Firebase project id for JVM/Desktop Firestore. " +
                "Set -Dsecretaria.firebaseProjectId=..., SECRETARIA_FIREBASE_PROJECT_ID, " +
                "secretaria.firebaseProjectId in local.properties, " +
                "or keep androidApp/google-services.json available locally.",
        )

private fun readGoogleServicesJson(): String? {
    return readNearbyFileText(GOOGLE_SERVICES_PATH)
}

internal fun parseProjectIdFromGoogleServicesJson(json: String): String? =
    runCatching {
        Json.parseToJsonElement(json)
            .jsonObject["project_info"]
            ?.jsonObject
            ?.get("project_id")
            ?.jsonPrimitive
            ?.contentOrNull
    }.getOrNull()
