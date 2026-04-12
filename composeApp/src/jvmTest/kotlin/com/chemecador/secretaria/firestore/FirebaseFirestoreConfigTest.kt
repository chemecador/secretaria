package com.chemecador.secretaria.firestore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class FirebaseFirestoreConfigTest {

    @Test
    fun resolveFirebaseProjectId_prefersSystemPropertyThenEnv() {
        val projectId = resolveFirebaseProjectId(
            propertyProvider = { name ->
                if (name == "secretaria.firebaseProjectId") "property-project" else null
            },
            environmentProvider = { "env-project" },
            localPropertiesProvider = { "local-project" },
            googleServicesReader = { """{"project_info":{"project_id":"json-project"}}""" },
        )

        assertEquals("property-project", projectId)
    }

    @Test
    fun resolveFirebaseProjectId_fallsBackToGoogleServicesJson() {
        val projectId = resolveFirebaseProjectId(
            propertyProvider = { null },
            environmentProvider = { null },
            localPropertiesProvider = { null },
            googleServicesReader = {
                """
                    {
                      "project_info": {
                        "project_id": "kotlin-secretaria"
                      }
                    }
                """.trimIndent()
            },
        )

        assertEquals("kotlin-secretaria", projectId)
    }

    @Test
    fun resolveFirebaseProjectId_fallsBackToLocalProperties() {
        val projectId = resolveFirebaseProjectId(
            propertyProvider = { null },
            environmentProvider = { null },
            localPropertiesProvider = { name ->
                if (name == "secretaria.firebaseProjectId") "local-project" else null
            },
            googleServicesReader = { null },
        )

        assertEquals("local-project", projectId)
    }

    @Test
    fun resolveFirebaseProjectId_failsWithClearMessageWhenMissing() {
        val error = assertFailsWith<IllegalStateException> {
            resolveFirebaseProjectId(
                propertyProvider = { null },
                environmentProvider = { null },
                localPropertiesProvider = { null },
                googleServicesReader = { null },
            )
        }

        assertTrue(error.message!!.contains("secretaria.firebaseProjectId"))
        assertTrue(error.message!!.contains("SECRETARIA_FIREBASE_PROJECT_ID"))
        assertTrue(error.message!!.contains("local.properties"))
        assertTrue(error.message!!.contains("google-services.json"))
    }
}
