package com.chemecador.secretaria.notes

data class NotePhotoItemState(
    val photo: NotePhoto,
    val thumbnailBytes: ByteArray? = null,
    val isThumbnailLoading: Boolean = false,
    val thumbnailError: NotePhotosError? = null,
)

sealed interface NotePhotoUploadState {
    data object Idle : NotePhotoUploadState
    data object Preparing : NotePhotoUploadState
    data object Uploading : NotePhotoUploadState
    data class Failed(
        val error: NotePhotosError,
        val canRetry: Boolean,
    ) : NotePhotoUploadState
}

sealed interface NotePhotoViewerState {
    data object Closed : NotePhotoViewerState
    data class Loading(val photo: NotePhoto) : NotePhotoViewerState
    data class Ready(
        val photo: NotePhoto,
        val bytes: ByteArray,
    ) : NotePhotoViewerState

    data class Failed(
        val photo: NotePhoto,
        val error: NotePhotosError,
    ) : NotePhotoViewerState
}

data class NotePhotosState(
    val isSupported: Boolean = false,
    val canUpload: Boolean = false,
    val isLoading: Boolean = false,
    val photos: List<NotePhotoItemState> = emptyList(),
    val uploadState: NotePhotoUploadState = NotePhotoUploadState.Idle,
    val deletingPhotoIds: Set<String> = emptySet(),
    val viewerState: NotePhotoViewerState = NotePhotoViewerState.Closed,
    val error: NotePhotosError? = null,
) {
    val canAddPhoto: Boolean
        get() = isSupported &&
            canUpload &&
            photos.size < MAX_PHOTOS_PER_NOTE &&
            uploadState == NotePhotoUploadState.Idle
}
