package com.chemecador.secretaria.noteslists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal actual fun rememberNotesListsSectionPreferenceStore(): NotesListsSectionPreferenceStore =
    remember { FileNotesListsSectionPreferenceStore() }

private class FileNotesListsSectionPreferenceStore(
    private val path: Path = defaultPath(),
) : NotesListsSectionPreferenceStore {

    override suspend fun load(): NotesListsSection = withContext(Dispatchers.IO) {
        if (!Files.exists(path)) return@withContext NotesListsSection.MINE
        try {
            Files.readString(path).toNotesListsSectionOrDefault()
        } catch (_: Exception) {
            NotesListsSection.MINE
        }
    }

    override suspend fun save(section: NotesListsSection) {
        withContext(Dispatchers.IO) {
            val parent = path.parent
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent)
            }
            val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
            try {
                Files.writeString(tmp, section.name)
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
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            runCatching { Files.deleteIfExists(path) }
        }
    }

    private companion object {
        fun defaultPath(): Path {
            val home = System.getProperty("user.home")
                ?.takeUnless { it.isBlank() }
                ?: System.getProperty("java.io.tmpdir")
                ?: "."
            return Paths.get(home, ".secretaria", "notes-lists-section.txt")
        }
    }
}
