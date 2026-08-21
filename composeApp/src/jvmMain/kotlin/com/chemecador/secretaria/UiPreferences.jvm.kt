package com.chemecador.secretaria

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberUiPreferences(): UiPreferences = remember { FileUiPreferences() }

private class FileUiPreferences(
    private val path: Path = defaultPath(),
) : UiPreferences {

    /** Cada escritura es leer-modificar-escribir sobre el mismo fichero, asi que se serializan. */
    private val mutex = Mutex()

    override suspend fun getString(key: String): String? = withContext(Dispatchers.IO) {
        mutex.withLock { read().getProperty(key) }
    }

    override suspend fun putString(key: String, value: String) {
        edit { it.setProperty(key, value) }
    }

    override suspend fun remove(key: String) {
        edit { it.remove(key) }
    }

    private suspend fun edit(block: (Properties) -> Unit) {
        withContext(Dispatchers.IO) {
            mutex.withLock {
                val properties = read()
                block(properties)
                write(properties)
            }
        }
    }

    private fun read(): Properties {
        val properties = Properties()
        if (!Files.exists(path)) return properties
        try {
            Files.newInputStream(path).use(properties::load)
        } catch (_: Exception) {
            return Properties()
        }
        return properties
    }

    private fun write(properties: Properties) {
        val parent = path.parent
        if (parent != null && !Files.exists(parent)) {
            runCatching { Files.createDirectories(parent) }
        }
        val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
        try {
            Files.newOutputStream(tmp).use { properties.store(it, null) }
            Files.move(
                tmp,
                path,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: Exception) {
            runCatching { Files.deleteIfExists(tmp) }
        }
    }

    private companion object {
        fun defaultPath(): Path {
            val home = System.getProperty("user.home")
                ?.takeUnless { it.isBlank() }
                ?: System.getProperty("java.io.tmpdir")
                ?: "."
            return Paths.get(home, ".secretaria", "ui-preferences.properties")
        }
    }
}
