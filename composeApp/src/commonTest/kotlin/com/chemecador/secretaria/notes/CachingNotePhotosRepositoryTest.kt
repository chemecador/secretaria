package com.chemecador.secretaria.notes

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.test.runTest

class CachingNotePhotosRepositoryTest {

    @Test
    fun secondRead_isServedFromMemory() = runTest {
        val delegate = CountingRepository()
        val repository = CachingNotePhotosRepository(delegate)
        val photo = photo("1")

        val first = repository.getPhotoBytes(photo, fullSize = true).getOrThrow()
        val second = repository.getPhotoBytes(photo, fullSize = true).getOrThrow()

        assertContentEquals(first, second)
        assertEquals(1, delegate.byteRequests.size)
    }

    @Test
    fun thumbnailAndOriginal_areCachedApart() = runTest {
        val delegate = CountingRepository()
        val repository = CachingNotePhotosRepository(delegate)
        val photo = photo("1")

        repository.getPhotoBytes(photo, fullSize = false)
        repository.getPhotoBytes(photo, fullSize = true)
        repository.getPhotoBytes(photo, fullSize = false)
        repository.getPhotoBytes(photo, fullSize = true)

        assertEquals(
            listOf("1" to false, "1" to true),
            delegate.byteRequests,
        )
    }

    @Test
    fun deletingAPhoto_dropsBothOfItsEntries() = runTest {
        val delegate = CountingRepository()
        val repository = CachingNotePhotosRepository(delegate)
        val photo = photo("1")
        val other = photo("2")

        repository.getPhotoBytes(photo, fullSize = true)
        repository.getPhotoBytes(photo, fullSize = false)
        repository.getPhotoBytes(other, fullSize = true)
        repository.deletePhoto(KEY, photo.id)
        delegate.byteRequests.clear()

        repository.getPhotoBytes(photo, fullSize = true)
        repository.getPhotoBytes(photo, fullSize = false)
        repository.getPhotoBytes(other, fullSize = true)

        assertEquals(
            listOf("1" to true, "1" to false),
            delegate.byteRequests,
        )
    }

    @Test
    fun failedRead_isNotRemembered() = runTest {
        val delegate = CountingRepository().apply {
            result = Result.failure(NotePhotosException(NotePhotosError.Network))
        }
        val repository = CachingNotePhotosRepository(delegate)
        val photo = photo("1")

        assertTrue(repository.getPhotoBytes(photo, fullSize = true).isFailure)
        assertTrue(repository.getPhotoBytes(photo, fullSize = true).isFailure)

        assertEquals(2, delegate.byteRequests.size)
    }

    @Test
    fun theOldestEntryGoesFirstWhenTheBudgetIsSpent() = runTest {
        val delegate = CountingRepository().apply { result = Result.success(ByteArray(40)) }
        // Room for exactly two entries.
        val repository = CachingNotePhotosRepository(delegate, maxBytes = 100)

        repository.getPhotoBytes(photo("1"), fullSize = true)
        repository.getPhotoBytes(photo("2"), fullSize = true)
        // Touching "1" makes "2" the oldest.
        repository.getPhotoBytes(photo("1"), fullSize = true)
        repository.getPhotoBytes(photo("3"), fullSize = true)
        delegate.byteRequests.clear()

        repository.getPhotoBytes(photo("1"), fullSize = true)
        repository.getPhotoBytes(photo("3"), fullSize = true)
        repository.getPhotoBytes(photo("2"), fullSize = true)

        assertEquals(listOf("2" to true), delegate.byteRequests)
    }

    @Test
    fun anEntryLargerThanTheBudget_isNeverKept() = runTest {
        val delegate = CountingRepository().apply { result = Result.success(ByteArray(200)) }
        val repository = CachingNotePhotosRepository(delegate, maxBytes = 100)
        val photo = photo("1")

        repository.getPhotoBytes(photo, fullSize = true)
        repository.getPhotoBytes(photo, fullSize = true)

        assertEquals(2, delegate.byteRequests.size)
    }

    private class CountingRepository : NotePhotosRepository {
        override val isSupported: Boolean = true
        override val canUpload: Boolean = true
        var result: Result<ByteArray> = Result.success(byteArrayOf(1, 2, 3))
        val byteRequests = mutableListOf<Pair<String, Boolean>>()

        override suspend fun getPhotos(key: NotePhotosKey): Result<List<NotePhoto>> =
            Result.success(emptyList())

        override suspend fun getPhotoBytes(
            photo: NotePhoto,
            fullSize: Boolean,
        ): Result<ByteArray> {
            byteRequests += photo.id to fullSize
            return result
        }

        override suspend fun uploadPhoto(
            key: NotePhotosKey,
            prepared: PreparedNotePhoto,
        ): Result<NotePhoto> = Result.failure(NotePhotosException(NotePhotosError.Unsupported))

        override suspend fun deletePhoto(key: NotePhotosKey, photoId: String): Result<Unit> =
            Result.success(Unit)
    }

    private companion object {
        val KEY = NotePhotosKey(
            ownerId = "owner",
            listId = "list-1",
            noteId = "note-1",
        )

        fun photo(id: String) = NotePhoto(
            id = id,
            storagePath = "note-images/owner/list-1/note-1/$id/original.jpg",
            thumbnailPath = "note-images/owner/list-1/note-1/$id/thumbnail.jpg",
            width = 1200,
            height = 800,
            sizeBytes = 300_000,
            uploadedBy = "owner",
            createdAt = Instant.parse("2026-08-20T10:00:00Z"),
        )
    }
}
