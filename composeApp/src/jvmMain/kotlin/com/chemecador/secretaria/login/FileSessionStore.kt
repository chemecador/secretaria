package com.chemecador.secretaria.login

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * JVM/Desktop [SessionStore] backed by a JSON file under the user's home directory.
 *
 * Location defaults to `<user.home>/.secretaria/session.json`. On POSIX filesystems we
 * tighten permissions to `rw-------` when possible; on Windows we rely on the user's
 * default ACL (acceptable for v1, mirrors what platform-native desktop apps do).
 */
internal class FileSessionStore(
    private val path: Path = defaultPath(),
) : SessionStore {

    override suspend fun load(): PersistedAuthSession? = withContext(Dispatchers.IO) {
        if (!Files.exists(path)) return@withContext null
        val json = try {
            Files.readString(path)
        } catch (_: Exception) {
            return@withContext null
        }
        decodePersistedAuthSession(json)
    }

    override suspend fun save(session: PersistedAuthSession) {
        val json = session.encodeToJsonString()
        withContext(Dispatchers.IO) {
            val parent = path.parent
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent)
            }
            val tmp = path.resolveSibling(path.fileName.toString() + ".tmp")
            try {
                Files.writeString(tmp, json)
                try {
                    Files.setPosixFilePermissions(
                        tmp,
                        PosixFilePermissions.fromString("rw-------"),
                    )
                } catch (_: UnsupportedOperationException) {
                    // Non-POSIX filesystem (e.g. Windows); skip permission tightening.
                } catch (_: Exception) {
                    // Permissions are best-effort; continue with a readable file.
                }
                Files.move(
                    tmp,
                    path,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE,
                )
            } catch (_: Exception) {
                // Best effort; leave any stale tmp behind silently.
                try {
                    Files.deleteIfExists(tmp)
                } catch (_: Exception) {
                    // ignore
                }
            }
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            try {
                Files.deleteIfExists(path)
            } catch (_: Exception) {
                // ignore — nothing we can do, caller already cleared memory.
            }
        }
    }

    private companion object {
        fun defaultPath(): Path {
            val home = System.getProperty("user.home")
                ?.takeUnless { it.isBlank() }
                ?: System.getProperty("java.io.tmpdir")
                ?: "."
            return Paths.get(home, ".secretaria", "session.json")
        }
    }
}
