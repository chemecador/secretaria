package com.chemecador.secretaria.config

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LocalPropertiesTest {

    @Test
    fun readLocalProperty_findsNearestParentOfWorkingDirectory() {
        val projectRoot = createTempDirectory()
        val nestedWorkingDirectory = File(projectRoot, "composeApp/build/run").apply { mkdirs() }
        File(projectRoot, "local.properties").writeText(
            """
                secretaria.firebaseApiKey=working-dir-key
            """.trimIndent(),
        )

        try {
            val value = readLocalProperty(
                propertyName = "secretaria.firebaseApiKey",
                workingDirectoryProvider = { nestedWorkingDirectory },
                extraSearchRootsProvider = { emptySequence() },
            )

            assertEquals("working-dir-key", value)
        } finally {
            projectRoot.deleteRecursively()
        }
    }

    @Test
    fun readLocalProperty_findsParentOfExtraSearchRoot() {
        val projectRoot = createTempDirectory()
        val unrelatedWorkingDirectory = createTempDirectory()
        val classesDirectory = File(projectRoot, "composeApp/build/classes/kotlin/jvm/main").apply { mkdirs() }
        File(projectRoot, "local.properties").writeText(
            """
                secretaria.firebaseApiKey=code-source-key
            """.trimIndent(),
        )

        try {
            val value = readLocalProperty(
                propertyName = "secretaria.firebaseApiKey",
                workingDirectoryProvider = { unrelatedWorkingDirectory },
                extraSearchRootsProvider = { sequenceOf(classesDirectory) },
            )

            assertEquals("code-source-key", value)
        } finally {
            projectRoot.deleteRecursively()
            unrelatedWorkingDirectory.deleteRecursively()
        }
    }

    @Test
    fun readLocalProperty_returnsNullWhenNotFound() {
        val unrelatedWorkingDirectory = createTempDirectory()

        try {
            val value = readLocalProperty(
                propertyName = "secretaria.firebaseApiKey",
                workingDirectoryProvider = { unrelatedWorkingDirectory },
                extraSearchRootsProvider = { emptySequence() },
            )

            assertNull(value)
        } finally {
            unrelatedWorkingDirectory.deleteRecursively()
        }
    }

    @Test
    fun readNearbyFileText_findsRepoRelativeFileFromExtraSearchRoot() {
        val projectRoot = createTempDirectory()
        val unrelatedWorkingDirectory = createTempDirectory()
        val classesDirectory = File(projectRoot, "composeApp/build/classes/kotlin/jvm/main").apply { mkdirs() }
        val googleServicesFile = File(projectRoot, "androidApp/google-services.json").apply {
            parentFile.mkdirs()
            writeText("""{"project_info":{"project_id":"kotlin-secretaria"}}""")
        }

        try {
            val text = readNearbyFileText(
                relativePath = "androidApp/google-services.json",
                workingDirectoryProvider = { unrelatedWorkingDirectory },
                extraSearchRootsProvider = { sequenceOf(classesDirectory) },
            )

            assertEquals(googleServicesFile.readText(), text)
        } finally {
            projectRoot.deleteRecursively()
            unrelatedWorkingDirectory.deleteRecursively()
        }
    }

    private fun createTempDirectory(): File =
        kotlin.io.path.createTempDirectory().toFile()
}
