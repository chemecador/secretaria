package com.chemecador.secretaria.notes

import kotlin.time.Instant

const val MAX_PHOTOS_PER_NOTE = 3

/** Identifies the note that owns the photos, including notes inside another user's shared list. */
data class NotePhotosKey(
    val ownerId: String,
    val listId: String,
    val noteId: String,
)

/** Server-owned metadata. The binary content always stays in Cloud Storage. */
data class NotePhoto(
    val id: String,
    val storagePath: String,
    val thumbnailPath: String,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val uploadedBy: String,
    val createdAt: Instant,
)

/** Sanitised JPEG produced on-device before requesting an upload. */
data class PreparedNotePhoto(
    val clientRequestId: String,
    val bytes: ByteArray,
    val width: Int,
    val height: Int,
    val contentType: String = "image/jpeg",
) {
    init {
        require(clientRequestId.isNotBlank())
        require(bytes.isNotEmpty())
        require(width > 0)
        require(height > 0)
        require(contentType == "image/jpeg")
    }

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is PreparedNotePhoto &&
            clientRequestId == other.clientRequestId &&
            bytes.contentEquals(other.bytes) &&
            width == other.width &&
            height == other.height &&
            contentType == other.contentType

    override fun hashCode(): Int {
        var result = clientRequestId.hashCode()
        result = 31 * result + bytes.contentHashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + contentType.hashCode()
        return result
    }
}

sealed interface NotePhotoPickerResult {
    data class Selected(val photo: PreparedNotePhoto) : NotePhotoPickerResult
    data class Failed(val error: NotePhotosError) : NotePhotoPickerResult
    data object Cancelled : NotePhotoPickerResult
}

sealed interface NotePhotoDownloadResult {
    data object Saved : NotePhotoDownloadResult
    data object Cancelled : NotePhotoDownloadResult
    data class Failed(val error: NotePhotosError) : NotePhotoDownloadResult
}

sealed interface NotePhotosError {
    data object Unsupported : NotePhotosError
    data object UploadNotAllowed : NotePhotosError
    data object NoteLimitReached : NotePhotosError
    data object AccountPhotoLimitReached : NotePhotosError
    data object AccountStorageLimitReached : NotePhotosError
    data object DailyUploadLimitReached : NotePhotosError
    data object MonthlyUploadLimitReached : NotePhotosError
    data object DailyOperationLimitReached : NotePhotosError
    data object GlobalLimitReached : NotePhotosError
    data object UploadSessionExpired : NotePhotosError
    data object InvalidImage : NotePhotosError
    data object ImageTooLarge : NotePhotosError
    data object PermissionDenied : NotePhotosError
    data object NotFound : NotePhotosError
    data object Network : NotePhotosError
    data class Repository(val message: String?) : NotePhotosError
}

class NotePhotosException(
    val error: NotePhotosError,
    cause: Throwable? = null,
) : Exception((error as? NotePhotosError.Repository)?.message, cause)

internal fun Throwable.toNotePhotosError(): NotePhotosError =
    (this as? NotePhotosException)?.error ?: NotePhotosError.Repository(message)
