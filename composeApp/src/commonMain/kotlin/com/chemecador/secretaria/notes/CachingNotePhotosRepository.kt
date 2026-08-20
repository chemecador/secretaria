package com.chemecador.secretaria.notes

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps downloaded photo bytes in memory so reopening the viewer costs nothing.
 *
 * Every Storage read is billed twice over: the object download itself, plus the two
 * `firestore.get()` calls the Storage rules make to authorise it. A photo is immutable once
 * uploaded — its id is derived from the payload — so a hit is always valid, and deleting the photo
 * is the only thing that can invalidate an entry.
 */
class CachingNotePhotosRepository(
    private val delegate: NotePhotosRepository,
    private val maxBytes: Long = DEFAULT_MAX_BYTES,
) : NotePhotosRepository {

    private val mutex = Mutex()

    /** Insertion order is the LRU order: a hit is reinserted so the oldest entry stays first. */
    private val entries = LinkedHashMap<String, ByteArray>()
    private var cachedBytes = 0L

    override val isSupported: Boolean
        get() = delegate.isSupported

    override val canUpload: Boolean
        get() = delegate.canUpload

    override suspend fun getPhotos(key: NotePhotosKey): Result<List<NotePhoto>> =
        delegate.getPhotos(key)

    override suspend fun getPhotoBytes(photo: NotePhoto, fullSize: Boolean): Result<ByteArray> {
        val cacheKey = cacheKey(photo.id, fullSize)
        read(cacheKey)?.let { cached -> return Result.success(cached) }

        return delegate.getPhotoBytes(photo, fullSize)
            .onSuccess { bytes -> write(cacheKey, bytes) }
    }

    override suspend fun uploadPhoto(
        key: NotePhotosKey,
        prepared: PreparedNotePhoto,
    ): Result<NotePhoto> = delegate.uploadPhoto(key, prepared)

    override suspend fun deletePhoto(key: NotePhotosKey, photoId: String): Result<Unit> =
        delegate.deletePhoto(key, photoId)
            .onSuccess { evict(photoId) }

    private suspend fun read(cacheKey: String): ByteArray? = mutex.withLock {
        val bytes = entries.remove(cacheKey) ?: return@withLock null
        entries[cacheKey] = bytes
        bytes
    }

    private suspend fun write(cacheKey: String, bytes: ByteArray) {
        if (bytes.size.toLong() > maxBytes) return

        mutex.withLock {
            forget(cacheKey)
            entries[cacheKey] = bytes
            cachedBytes += bytes.size

            while (cachedBytes > maxBytes) {
                val oldest = entries.keys.firstOrNull() ?: break
                forget(oldest)
            }
        }
    }

    private suspend fun evict(photoId: String) = mutex.withLock {
        forget(cacheKey(photoId, fullSize = true))
        forget(cacheKey(photoId, fullSize = false))
    }

    private fun forget(cacheKey: String) {
        entries.remove(cacheKey)?.let { removed -> cachedBytes -= removed.size }
    }

    private fun cacheKey(photoId: String, fullSize: Boolean): String =
        if (fullSize) "$photoId:full" else "$photoId:thumb"

    private companion object {
        /** Originals are capped at 1 MiB server-side, so this holds a comfortable handful. */
        const val DEFAULT_MAX_BYTES = 8L * 1024 * 1024
    }
}
