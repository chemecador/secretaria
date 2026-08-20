package com.chemecador.secretaria.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class NotePhotosViewModel(
    private val repository: NotePhotosRepository,
    ownerId: String,
    listId: String,
    noteId: String,
) : ViewModel() {
    private val key = NotePhotosKey(ownerId, listId, noteId)
    private val _state = MutableStateFlow(
        NotePhotosState(
            isSupported = repository.isSupported,
            canUpload = repository.canUpload,
        ),
    )
    val state: StateFlow<NotePhotosState> = _state.asStateFlow()

    private var pendingUpload: PreparedNotePhoto? = null
    private var uploadJob: Job? = null
    private var viewerJob: Job? = null

    fun load() {
        if (!repository.isSupported || _state.value.isLoading) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isSupported = repository.isSupported,
                    canUpload = repository.canUpload,
                    isLoading = true,
                    error = null,
                )
            }

            repository.getPhotos(key)
                .onSuccess { photos ->
                    val items = photos
                        .distinctBy(NotePhoto::id)
                        .sortedBy(NotePhoto::createdAt)
                        .take(MAX_PHOTOS_PER_NOTE)
                        .map { photo ->
                            NotePhotoItemState(
                                photo = photo,
                                isThumbnailLoading = true,
                            )
                        }
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasLoaded = true,
                            photos = items,
                            canUpload = repository.canUpload,
                        )
                    }
                    items.forEach { item -> loadThumbnail(item.photo) }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            photos = emptyList(),
                            error = throwable.toNotePhotosError(),
                        )
                    }
                }
        }
    }

    /** Returns true only when the platform picker should actually be launched. */
    fun beginPhotoSelection(): Boolean {
        val current = _state.value
        val error = when {
            !current.isSupported || !current.canUpload -> NotePhotosError.UploadNotAllowed
            current.photos.size >= MAX_PHOTOS_PER_NOTE -> NotePhotosError.NoteLimitReached
            current.uploadState != NotePhotoUploadState.Idle -> return false
            else -> null
        }
        if (error != null) {
            _state.update { it.copy(error = error) }
            return false
        }

        _state.update {
            it.copy(
                uploadState = NotePhotoUploadState.Preparing,
                error = null,
            )
        }
        return true
    }

    fun onPickerResult(result: NotePhotoPickerResult) {
        if (_state.value.uploadState != NotePhotoUploadState.Preparing) return

        when (result) {
            NotePhotoPickerResult.Cancelled -> {
                pendingUpload = null
                _state.update { it.copy(uploadState = NotePhotoUploadState.Idle) }
            }

            is NotePhotoPickerResult.Failed -> {
                pendingUpload = null
                _state.update {
                    it.copy(
                        uploadState = NotePhotoUploadState.Failed(
                            error = result.error,
                            canRetry = false,
                        ),
                    )
                }
            }

            is NotePhotoPickerResult.Selected -> {
                if (_state.value.photos.size >= MAX_PHOTOS_PER_NOTE) {
                    pendingUpload = null
                    _state.update {
                        it.copy(
                            uploadState = NotePhotoUploadState.Failed(
                                error = NotePhotosError.NoteLimitReached,
                                canRetry = false,
                            ),
                        )
                    }
                    return
                }
                pendingUpload = result.photo
                uploadPendingPhoto()
            }
        }
    }

    /** Retries the exact same payload and request id, making server-side retries idempotent. */
    fun retryUpload() {
        val failed = _state.value.uploadState as? NotePhotoUploadState.Failed ?: return
        if (!failed.canRetry || pendingUpload == null) return
        uploadPendingPhoto()
    }

    fun dismissUploadError() {
        if (_state.value.uploadState !is NotePhotoUploadState.Failed) return
        pendingUpload = null
        _state.update { it.copy(uploadState = NotePhotoUploadState.Idle) }
    }

    fun retryThumbnail(photoId: String) {
        val photo = _state.value.photos.firstOrNull { it.photo.id == photoId }?.photo ?: return
        if (_state.value.photos.first { it.photo.id == photoId }.isThumbnailLoading) return
        _state.update { state ->
            state.copy(
                photos = state.photos.map { item ->
                    if (item.photo.id == photoId) {
                        item.copy(isThumbnailLoading = true, thumbnailError = null)
                    } else {
                        item
                    }
                },
            )
        }
        loadThumbnail(photo)
    }

    fun deletePhoto(photoId: String) {
        val current = _state.value
        if (photoId in current.deletingPhotoIds) return
        if (current.photos.none { it.photo.id == photoId }) return

        _state.update {
            it.copy(
                deletingPhotoIds = it.deletingPhotoIds + photoId,
                error = null,
            )
        }
        viewModelScope.launch {
            repository.deletePhoto(key, photoId)
                .onSuccess {
                    _state.update { state ->
                        val viewerPhotoId = state.viewerState.photoOrNull()?.id
                        state.copy(
                            photos = state.photos.filterNot { it.photo.id == photoId },
                            deletingPhotoIds = state.deletingPhotoIds - photoId,
                            viewerState = if (viewerPhotoId == photoId) {
                                NotePhotoViewerState.Closed
                            } else {
                                state.viewerState
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update {
                        it.copy(
                            deletingPhotoIds = it.deletingPhotoIds - photoId,
                            error = throwable.toNotePhotosError(),
                        )
                    }
                }
        }
    }

    fun openPhoto(photoId: String) {
        val photo = _state.value.photos.firstOrNull { it.photo.id == photoId }?.photo ?: return
        viewerJob?.cancel()
        _state.update {
            it.copy(
                viewerState = NotePhotoViewerState.Loading(photo),
                downloadState = NotePhotoDownloadState.Idle,
            )
        }
        viewerJob = viewModelScope.launch {
            repository.getPhotoBytes(photo, fullSize = true)
                .onSuccess { bytes ->
                    _state.update { state ->
                        if (state.viewerState.photoOrNull()?.id != photo.id) {
                            state
                        } else {
                            state.copy(viewerState = NotePhotoViewerState.Ready(photo, bytes))
                        }
                    }
                }
                .onFailure { throwable ->
                    _state.update { state ->
                        if (state.viewerState.photoOrNull()?.id != photo.id) {
                            state
                        } else {
                            state.copy(
                                viewerState = NotePhotoViewerState.Failed(
                                    photo = photo,
                                    error = throwable.toNotePhotosError(),
                                ),
                            )
                        }
                    }
                }
        }
    }

    fun closeViewer() {
        viewerJob?.cancel()
        viewerJob = null
        _state.update {
            it.copy(
                viewerState = NotePhotoViewerState.Closed,
                downloadState = NotePhotoDownloadState.Idle,
            )
        }
    }

    /**
     * Returns true only when the platform saver should actually be launched. Saving reuses the
     * bytes the viewer already holds, so it never costs another download.
     */
    fun beginPhotoDownload(): Boolean {
        val current = _state.value
        if (current.viewerState !is NotePhotoViewerState.Ready) return false
        if (current.downloadState == NotePhotoDownloadState.Saving) return false

        _state.update { it.copy(downloadState = NotePhotoDownloadState.Saving) }
        return true
    }

    fun onDownloadResult(result: NotePhotoDownloadResult) {
        if (_state.value.downloadState != NotePhotoDownloadState.Saving) return

        _state.update {
            it.copy(
                downloadState = when (result) {
                    is NotePhotoDownloadResult.Saved ->
                        NotePhotoDownloadState.Saved(result.location)

                    NotePhotoDownloadResult.Cancelled -> NotePhotoDownloadState.Idle
                    is NotePhotoDownloadResult.Failed ->
                        NotePhotoDownloadState.Failed(result.error)
                },
            )
        }
    }

    fun dismissDownloadFeedback() {
        if (_state.value.downloadState == NotePhotoDownloadState.Saving) return
        _state.update { it.copy(downloadState = NotePhotoDownloadState.Idle) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun uploadPendingPhoto() {
        val prepared = pendingUpload ?: return
        if (uploadJob?.isActive == true) return

        _state.update { it.copy(uploadState = NotePhotoUploadState.Uploading) }
        uploadJob = viewModelScope.launch {
            repository.uploadPhoto(key, prepared)
                .onSuccess { photo ->
                    pendingUpload = null
                    _state.update { state ->
                        val updatedPhotos = (
                            state.photos.filterNot { it.photo.id == photo.id } +
                                NotePhotoItemState(photo = photo, isThumbnailLoading = true)
                            )
                            .sortedBy { it.photo.createdAt }
                            .take(MAX_PHOTOS_PER_NOTE)
                        state.copy(
                            photos = updatedPhotos,
                            uploadState = NotePhotoUploadState.Idle,
                            canUpload = repository.canUpload,
                            error = null,
                        )
                    }
                    loadThumbnail(photo)
                }
                .onFailure { throwable ->
                    val error = throwable.toNotePhotosError()
                    _state.update {
                        it.copy(
                            uploadState = NotePhotoUploadState.Failed(
                                error = error,
                                canRetry = error.isRetryableUploadError(),
                            ),
                        )
                    }
                }
        }
    }

    private fun loadThumbnail(photo: NotePhoto) {
        viewModelScope.launch {
            repository.getPhotoBytes(photo, fullSize = false)
                .onSuccess { bytes ->
                    _state.update { state ->
                        state.copy(
                            photos = state.photos.map { item ->
                                if (item.photo.id == photo.id) {
                                    item.copy(
                                        thumbnailBytes = bytes,
                                        isThumbnailLoading = false,
                                        thumbnailError = null,
                                    )
                                } else {
                                    item
                                }
                            },
                        )
                    }
                }
                .onFailure { throwable ->
                    _state.update { state ->
                        state.copy(
                            photos = state.photos.map { item ->
                                if (item.photo.id == photo.id) {
                                    item.copy(
                                        isThumbnailLoading = false,
                                        thumbnailError = throwable.toNotePhotosError(),
                                    )
                                } else {
                                    item
                                }
                            },
                        )
                    }
                }
        }
    }
}

private fun NotePhotoViewerState.photoOrNull(): NotePhoto? =
    when (this) {
        NotePhotoViewerState.Closed -> null
        is NotePhotoViewerState.Loading -> photo
        is NotePhotoViewerState.Ready -> photo
        is NotePhotoViewerState.Failed -> photo
    }

private fun NotePhotosError.isRetryableUploadError(): Boolean =
    this == NotePhotosError.Network || this is NotePhotosError.Repository
