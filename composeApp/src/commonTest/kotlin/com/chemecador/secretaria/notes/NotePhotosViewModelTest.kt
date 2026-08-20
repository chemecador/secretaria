package com.chemecador.secretaria.notes

import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain

@OptIn(ExperimentalCoroutinesApi::class)
class NotePhotosViewModelTest {
    private lateinit var dispatcher: TestDispatcher

    @BeforeTest
    fun setUp() {
        dispatcher = StandardTestDispatcher()
        Dispatchers.setMain(dispatcher)
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun load_readsMetadataAndThumbnailForSharedOwnerPath() = runTest(dispatcher) {
        val photo = photo("photo-1")
        val repository = RecordingRepository().apply {
            photosResult = Result.success(listOf(photo))
            bytesHandler = { _, fullSize ->
                assertFalse(fullSize)
                Result.success(byteArrayOf(1, 2, 3))
            }
        }
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertEquals(listOf(KEY), repository.loadedKeys)
        assertEquals(1, viewModel.state.value.photos.size)
        assertContentEquals(
            byteArrayOf(1, 2, 3),
            viewModel.state.value.photos.single().thumbnailBytes,
        )
        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.hasLoaded)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun load_failureLeavesTheCountUnconfirmed() = runTest(dispatcher) {
        val repository = RecordingRepository().apply {
            photosResult = Result.failure(NotePhotosException(NotePhotosError.Network))
        }
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.hasLoaded)
        assertTrue(viewModel.state.value.photos.isEmpty())
        assertEquals(NotePhotosError.Network, viewModel.state.value.error)
    }

    @Test
    fun load_keepsAThumbnailFailureLocalToItsPhoto() = runTest(dispatcher) {
        val first = photo("photo-1")
        val second = photo("photo-2")
        val repository = RecordingRepository().apply {
            photosResult = Result.success(listOf(first, second))
            bytesHandler = { item, _ ->
                if (item.id == first.id) {
                    Result.failure(NotePhotosException(NotePhotosError.Network))
                } else {
                    Result.success(byteArrayOf(9))
                }
            }
        }
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        val firstState = viewModel.state.value.photos.first { it.photo.id == first.id }
        val secondState = viewModel.state.value.photos.first { it.photo.id == second.id }
        assertEquals(NotePhotosError.Network, firstState.thumbnailError)
        assertNull(firstState.thumbnailBytes)
        assertContentEquals(byteArrayOf(9), secondState.thumbnailBytes)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun cancelledPicker_returnsToIdleWithoutUploading() {
        val repository = RecordingRepository()
        val viewModel = buildViewModel(repository)

        assertTrue(viewModel.beginPhotoSelection())
        assertEquals(NotePhotoUploadState.Preparing, viewModel.state.value.uploadState)

        viewModel.onPickerResult(NotePhotoPickerResult.Cancelled)

        assertEquals(NotePhotoUploadState.Idle, viewModel.state.value.uploadState)
        assertTrue(repository.uploads.isEmpty())
    }

    @Test
    fun pickerFailure_isTypedAndCannotRetry() {
        val viewModel = buildViewModel(RecordingRepository())

        viewModel.beginPhotoSelection()
        viewModel.onPickerResult(NotePhotoPickerResult.Failed(NotePhotosError.InvalidImage))

        assertEquals(
            NotePhotoUploadState.Failed(NotePhotosError.InvalidImage, canRetry = false),
            viewModel.state.value.uploadState,
        )
    }

    @Test
    fun uploadSuccess_usesFullKeyAndLoadsNewThumbnail() = runTest(dispatcher) {
        val uploaded = photo("server-photo")
        val repository = RecordingRepository().apply {
            uploadResults += Result.success(uploaded)
            bytesHandler = { _, fullSize ->
                assertFalse(fullSize)
                Result.success(byteArrayOf(4, 5))
            }
        }
        val viewModel = buildViewModel(repository)
        val prepared = prepared("request-1")

        viewModel.beginPhotoSelection()
        viewModel.onPickerResult(NotePhotoPickerResult.Selected(prepared))
        advanceUntilIdle()

        assertEquals(listOf(KEY), repository.uploads.map { it.first })
        assertEquals(prepared, repository.uploads.single().second)
        assertEquals(NotePhotoUploadState.Idle, viewModel.state.value.uploadState)
        assertEquals(uploaded, viewModel.state.value.photos.single().photo)
        assertContentEquals(
            byteArrayOf(4, 5),
            viewModel.state.value.photos.single().thumbnailBytes,
        )
    }

    @Test
    fun retryUpload_reusesTheSamePreparedPayloadAndRequestId() = runTest(dispatcher) {
        val uploaded = photo("server-photo")
        val repository = RecordingRepository().apply {
            uploadResults += Result.failure(NotePhotosException(NotePhotosError.Network))
            uploadResults += Result.success(uploaded)
        }
        val viewModel = buildViewModel(repository)
        val prepared = prepared("stable-request-id")

        viewModel.beginPhotoSelection()
        viewModel.onPickerResult(NotePhotoPickerResult.Selected(prepared))
        advanceUntilIdle()

        assertEquals(
            NotePhotoUploadState.Failed(NotePhotosError.Network, canRetry = true),
            viewModel.state.value.uploadState,
        )

        viewModel.retryUpload()
        advanceUntilIdle()

        assertEquals(2, repository.uploads.size)
        assertTrue(repository.uploads[0].second === repository.uploads[1].second)
        assertEquals(
            listOf("stable-request-id", "stable-request-id"),
            repository.uploads.map { it.second.clientRequestId },
        )
        assertEquals(NotePhotoUploadState.Idle, viewModel.state.value.uploadState)
    }

    @Test
    fun expiredUploadSession_requiresSelectingThePhotoAgain() = runTest(dispatcher) {
        val repository = RecordingRepository().apply {
            uploadResults += Result.failure(
                NotePhotosException(NotePhotosError.UploadSessionExpired),
            )
        }
        val viewModel = buildViewModel(repository)

        viewModel.beginPhotoSelection()
        viewModel.onPickerResult(NotePhotoPickerResult.Selected(prepared("expired-request")))
        advanceUntilIdle()

        assertEquals(
            NotePhotoUploadState.Failed(
                NotePhotosError.UploadSessionExpired,
                canRetry = false,
            ),
            viewModel.state.value.uploadState,
        )
        viewModel.retryUpload()
        advanceUntilIdle()
        assertEquals(1, repository.uploads.size)
    }

    @Test
    fun noteLimit_preventsOpeningPickerOrStartingAFourthUpload() = runTest(dispatcher) {
        val repository = RecordingRepository().apply {
            photosResult = Result.success(
                listOf(photo("1"), photo("2"), photo("3")),
            )
        }
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        assertFalse(viewModel.beginPhotoSelection())
        assertEquals(NotePhotosError.NoteLimitReached, viewModel.state.value.error)
        assertFalse(viewModel.state.value.canAddPhoto)
    }

    @Test
    fun deletePhoto_removesOnlyTheRequestedItemAndUsesFullKey() = runTest(dispatcher) {
        val repository = RecordingRepository().apply {
            photosResult = Result.success(listOf(photo("1"), photo("2")))
        }
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.deletePhoto("1")
        advanceUntilIdle()

        assertEquals(listOf(KEY to "1"), repository.deletions)
        assertEquals(listOf("2"), viewModel.state.value.photos.map { it.photo.id })
    }

    @Test
    fun openPhoto_requestsFullSizeBytesAndCanBeClosed() = runTest(dispatcher) {
        val photo = photo("1")
        val repository = RecordingRepository().apply {
            photosResult = Result.success(listOf(photo))
            bytesHandler = { _, fullSize ->
                Result.success(if (fullSize) byteArrayOf(8, 8) else byteArrayOf(1))
            }
        }
        val viewModel = buildViewModel(repository)
        viewModel.load()
        advanceUntilIdle()

        viewModel.openPhoto(photo.id)
        advanceUntilIdle()

        val ready = assertIs<NotePhotoViewerState.Ready>(viewModel.state.value.viewerState)
        assertContentEquals(byteArrayOf(8, 8), ready.bytes)
        assertTrue(repository.byteRequests.last().second)

        viewModel.closeViewer()
        assertEquals(NotePhotoViewerState.Closed, viewModel.state.value.viewerState)
    }

    @Test
    fun beginPhotoDownload_needsTheFullSizeBytesOnScreen() = runTest(dispatcher) {
        val photo = photo("1")
        val viewModel = buildViewModel(readyViewerRepository(photo))
        viewModel.load()
        advanceUntilIdle()

        assertFalse(viewModel.beginPhotoDownload())
        assertEquals(NotePhotoDownloadState.Idle, viewModel.state.value.downloadState)

        viewModel.openPhoto(photo.id)
        advanceUntilIdle()

        assertTrue(viewModel.beginPhotoDownload())
        assertEquals(NotePhotoDownloadState.Saving, viewModel.state.value.downloadState)
        assertFalse(viewModel.beginPhotoDownload())
    }

    @Test
    fun downloadResult_reportsSuccessFailureAndCancellation() = runTest(dispatcher) {
        val photo = photo("1")
        val viewModel = buildViewModel(readyViewerRepository(photo))
        viewModel.load()
        advanceUntilIdle()
        viewModel.openPhoto(photo.id)
        advanceUntilIdle()

        viewModel.beginPhotoDownload()
        viewModel.onDownloadResult(NotePhotoDownloadResult.Saved(SAVED_LOCATION))
        // The location travels through so the snackbar can offer to open the saved file.
        assertEquals(
            NotePhotoDownloadState.Saved(SAVED_LOCATION),
            viewModel.state.value.downloadState,
        )

        // A result that arrives without a save in flight is ignored.
        viewModel.onDownloadResult(NotePhotoDownloadResult.Cancelled)
        assertEquals(
            NotePhotoDownloadState.Saved(SAVED_LOCATION),
            viewModel.state.value.downloadState,
        )

        viewModel.dismissDownloadFeedback()
        assertEquals(NotePhotoDownloadState.Idle, viewModel.state.value.downloadState)

        viewModel.beginPhotoDownload()
        viewModel.onDownloadResult(NotePhotoDownloadResult.Cancelled)
        assertEquals(NotePhotoDownloadState.Idle, viewModel.state.value.downloadState)

        viewModel.beginPhotoDownload()
        viewModel.onDownloadResult(
            NotePhotoDownloadResult.Failed(NotePhotosError.Network),
        )
        assertEquals(
            NotePhotoDownloadState.Failed(NotePhotosError.Network),
            viewModel.state.value.downloadState,
        )
    }

    @Test
    fun closingTheViewer_dropsStaleDownloadFeedback() = runTest(dispatcher) {
        val photo = photo("1")
        val viewModel = buildViewModel(readyViewerRepository(photo))
        viewModel.load()
        advanceUntilIdle()
        viewModel.openPhoto(photo.id)
        advanceUntilIdle()

        viewModel.beginPhotoDownload()
        viewModel.onDownloadResult(NotePhotoDownloadResult.Saved(SAVED_LOCATION))
        viewModel.closeViewer()

        assertEquals(NotePhotoDownloadState.Idle, viewModel.state.value.downloadState)
    }

    private fun readyViewerRepository(photo: NotePhoto): RecordingRepository =
        RecordingRepository().apply {
            photosResult = Result.success(listOf(photo))
            bytesHandler = { _, fullSize ->
                Result.success(if (fullSize) byteArrayOf(8, 8) else byteArrayOf(1))
            }
        }

    @Test
    fun noopRepository_isSafeOnUnsupportedTargets() = runTest(dispatcher) {
        val repository = NoopNotePhotosRepository()
        val viewModel = buildViewModel(repository)

        viewModel.load()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isSupported)
        assertTrue(viewModel.state.value.photos.isEmpty())
        assertFalse(viewModel.beginPhotoSelection())
        assertEquals(NotePhotosError.UploadNotAllowed, viewModel.state.value.error)
    }

    private fun buildViewModel(repository: NotePhotosRepository): NotePhotosViewModel =
        NotePhotosViewModel(
            repository = repository,
            ownerId = KEY.ownerId,
            listId = KEY.listId,
            noteId = KEY.noteId,
        )

    private class RecordingRepository : NotePhotosRepository {
        override var isSupported: Boolean = true
        override var canUpload: Boolean = true
        var photosResult: Result<List<NotePhoto>> = Result.success(emptyList())
        var bytesHandler: (NotePhoto, Boolean) -> Result<ByteArray> = { _, _ ->
            Result.success(byteArrayOf(1))
        }
        val uploadResults = mutableListOf<Result<NotePhoto>>()
        val loadedKeys = mutableListOf<NotePhotosKey>()
        val byteRequests = mutableListOf<Pair<NotePhoto, Boolean>>()
        val uploads = mutableListOf<Pair<NotePhotosKey, PreparedNotePhoto>>()
        val deletions = mutableListOf<Pair<NotePhotosKey, String>>()

        override suspend fun getPhotos(key: NotePhotosKey): Result<List<NotePhoto>> {
            loadedKeys += key
            return photosResult
        }

        override suspend fun getPhotoBytes(
            photo: NotePhoto,
            fullSize: Boolean,
        ): Result<ByteArray> {
            byteRequests += photo to fullSize
            return bytesHandler(photo, fullSize)
        }

        override suspend fun uploadPhoto(
            key: NotePhotosKey,
            prepared: PreparedNotePhoto,
        ): Result<NotePhoto> {
            uploads += key to prepared
            return if (uploadResults.isEmpty()) {
                Result.success(photo("uploaded"))
            } else {
                uploadResults.removeAt(0)
            }
        }

        override suspend fun deletePhoto(key: NotePhotosKey, photoId: String): Result<Unit> {
            deletions += key to photoId
            return Result.success(Unit)
        }
    }

    private companion object {
        /** Shaped like the Android content Uri the real controller hands back. */
        const val SAVED_LOCATION = "content://media/external/images/media/42"

        val KEY = NotePhotosKey(
            ownerId = "shared-owner",
            listId = "shared-list",
            noteId = "note-1",
        )

        fun photo(id: String) = NotePhoto(
            id = id,
            storagePath = "note-photos/${KEY.ownerId}/${KEY.listId}/${KEY.noteId}/$id/original.jpg",
            thumbnailPath = "note-photos/${KEY.ownerId}/${KEY.listId}/${KEY.noteId}/$id/thumbnail.jpg",
            width = 1200,
            height = 800,
            sizeBytes = 300_000,
            uploadedBy = "user-1",
            createdAt = Instant.parse("2026-08-19T10:00:00Z"),
        )

        fun prepared(requestId: String) = PreparedNotePhoto(
            clientRequestId = requestId,
            bytes = byteArrayOf(1, 2, 3),
            width = 1200,
            height = 800,
        )
    }
}
