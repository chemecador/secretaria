package com.chemecador.secretaria.notes

import android.util.Base64
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import kotlin.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await

class FirebaseNotePhotosRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val functions: FirebaseFunctions = FirebaseFunctions.getInstance(FUNCTIONS_REGION),
    private val storage: FirebaseStorage = FirebaseStorage.getInstance(),
) : NotePhotosRepository {

    override val isSupported: Boolean
        get() = auth.currentUser?.let { user -> !user.isAnonymous } == true

    override val canUpload: Boolean
        get() = isSupported

    override suspend fun getPhotos(key: NotePhotosKey): Result<List<NotePhoto>> = firebaseResult {
        photosCollection(key)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .get()
            .await()
            .documents
            .mapNotNull(DocumentSnapshot::toReadyNotePhoto)
    }

    override suspend fun getPhotoBytes(
        photo: NotePhoto,
        fullSize: Boolean,
    ): Result<ByteArray> = firebaseResult {
        val hasDedicatedThumbnail = photo.thumbnailPath.isNotBlank() &&
            photo.thumbnailPath != photo.storagePath
        val useOriginal = fullSize || !hasDedicatedThumbnail
        val objectPath = if (useOriginal) photo.storagePath else photo.thumbnailPath
        val maxBytes = if (useOriginal) {
            MAX_ORIGINAL_DOWNLOAD_BYTES
        } else {
            MAX_THUMBNAIL_DOWNLOAD_BYTES
        }
        storage.reference.child(objectPath).getBytes(maxBytes).await()
    }

    override suspend fun uploadPhoto(
        key: NotePhotosKey,
        prepared: PreparedNotePhoto,
    ): Result<NotePhoto> = firebaseResult {
        if (!canUpload) throw NotePhotosException(NotePhotosError.UploadNotAllowed)
        if (prepared.contentType != JPEG_CONTENT_TYPE || prepared.bytes.size > MAX_UPLOAD_BYTES) {
            throw NotePhotosException(NotePhotosError.ImageTooLarge)
        }

        val response = functions.getHttpsCallable(UPLOAD_FUNCTION)
            .call(
                mapOf(
                    FIELD_OWNER_ID to key.ownerId,
                    FIELD_LIST_ID to key.listId,
                    FIELD_NOTE_ID to key.noteId,
                    FIELD_REQUEST_ID to prepared.clientRequestId,
                    FIELD_JPEG_BASE64 to Base64.encodeToString(prepared.bytes, Base64.NO_WRAP),
                ),
            )
            .await()
            .data
            .asStringKeyMap()

        response.toNotePhoto(auth.currentUser?.uid.orEmpty())
    }

    override suspend fun deletePhoto(
        key: NotePhotosKey,
        photoId: String,
    ): Result<Unit> = firebaseResult {
        functions.getHttpsCallable(DELETE_FUNCTION)
            .call(
                mapOf(
                    FIELD_OWNER_ID to key.ownerId,
                    FIELD_LIST_ID to key.listId,
                    FIELD_NOTE_ID to key.noteId,
                    FIELD_PHOTO_ID to photoId,
                ),
            )
            .await()
        Unit
    }

    private fun photosCollection(key: NotePhotosKey) =
        firestore.collection(USERS_COLLECTION)
            .document(key.ownerId)
            .collection(NOTES_LIST_COLLECTION)
            .document(key.listId)
            .collection(NOTES_COLLECTION)
            .document(key.noteId)
            .collection(PHOTOS_COLLECTION)

    private suspend inline fun <T> firebaseResult(crossinline block: suspend () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            Result.failure(error.toNotePhotosException())
        }

    private companion object {
        const val FUNCTIONS_REGION = "europe-west1"
        const val UPLOAD_FUNCTION = "uploadNotePhoto"
        const val DELETE_FUNCTION = "deleteNotePhoto"

        const val USERS_COLLECTION = "users"
        const val NOTES_LIST_COLLECTION = "noteslist"
        const val NOTES_COLLECTION = "notes"
        const val PHOTOS_COLLECTION = "photos"

        const val FIELD_OWNER_ID = "ownerId"
        const val FIELD_LIST_ID = "listId"
        const val FIELD_NOTE_ID = "noteId"
        const val FIELD_REQUEST_ID = "requestId"
        const val FIELD_JPEG_BASE64 = "jpegBase64"
        const val FIELD_PHOTO_ID = "photoId"
        const val FIELD_CREATED_AT = "createdAt"

        const val JPEG_CONTENT_TYPE = "image/jpeg"
        const val MAX_UPLOAD_BYTES = 1024 * 1024
        const val MAX_ORIGINAL_DOWNLOAD_BYTES = 1024L * 1024L
        const val MAX_THUMBNAIL_DOWNLOAD_BYTES = 512L * 1024L
    }
}

private fun DocumentSnapshot.toReadyNotePhoto(): NotePhoto? {
    val status = getString("status")
    if (status != null && status != "ready") return null

    val storagePath = getString("storagePath") ?: return null
    val width = getLong("width")?.toInt()?.takeIf { it > 0 } ?: return null
    val height = getLong("height")?.toInt()?.takeIf { it > 0 } ?: return null
    val sizeBytes = (getLong("byteSize") ?: getLong("sizeBytes"))
        ?.takeIf { it > 0 }
        ?: return null
    val createdAt = getTimestamp("createdAt")?.let { timestamp ->
        Instant.fromEpochSeconds(timestamp.seconds, timestamp.nanoseconds)
    } ?: return null

    return NotePhoto(
        id = id,
        storagePath = storagePath,
        thumbnailPath = getString("thumbnailPath").orEmpty().ifBlank { storagePath },
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        uploadedBy = getString("uploaderId").orEmpty(),
        createdAt = createdAt,
    )
}

private fun Any?.asStringKeyMap(): Map<String, Any?> =
    (this as? Map<*, *>)
        ?.entries
        ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
        ?.toMap()
        ?: throw NotePhotosException(NotePhotosError.Repository(null))

private fun Map<String, Any?>.toNotePhoto(currentUserId: String): NotePhoto {
    val photoId = string("photoId")
    val storagePath = string("storagePath")
    val width = positiveInt("width")
    val height = positiveInt("height")
    val sizeBytes = positiveLong("byteSize")
    val createdAtEpochMs = positiveLong("createdAtEpochMs")

    return NotePhoto(
        id = photoId,
        storagePath = storagePath,
        thumbnailPath = (get("thumbnailPath") as? String).orEmpty().ifBlank { storagePath },
        width = width,
        height = height,
        sizeBytes = sizeBytes,
        uploadedBy = (get("uploaderId") as? String).orEmpty().ifBlank { currentUserId },
        createdAt = Instant.fromEpochMilliseconds(createdAtEpochMs),
    )
}

private fun Map<String, Any?>.string(key: String): String =
    (get(key) as? String)?.takeIf(String::isNotBlank)
        ?: throw NotePhotosException(NotePhotosError.Repository(null))

private fun Map<String, Any?>.positiveInt(key: String): Int =
    number(key).toInt().takeIf { it > 0 }
        ?: throw NotePhotosException(NotePhotosError.Repository(null))

private fun Map<String, Any?>.positiveLong(key: String): Long =
    number(key).toLong().takeIf { it > 0L }
        ?: throw NotePhotosException(NotePhotosError.Repository(null))

private fun Map<String, Any?>.number(key: String): Number =
    get(key) as? Number
        ?: throw NotePhotosException(NotePhotosError.Repository(null))

private fun Throwable.toNotePhotosException(): NotePhotosException {
    if (this is NotePhotosException) return this

    val mapped = when (this) {
        is FirebaseFunctionsException -> toNotePhotosError()
        is FirebaseFirestoreException -> when (code) {
            FirebaseFirestoreException.Code.PERMISSION_DENIED -> NotePhotosError.PermissionDenied
            FirebaseFirestoreException.Code.NOT_FOUND -> NotePhotosError.NotFound
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            -> NotePhotosError.Network
            else -> NotePhotosError.Repository(message)
        }
        is StorageException -> when (errorCode) {
            StorageException.ERROR_NOT_AUTHENTICATED -> NotePhotosError.UploadNotAllowed
            StorageException.ERROR_NOT_AUTHORIZED -> NotePhotosError.PermissionDenied
            StorageException.ERROR_OBJECT_NOT_FOUND -> NotePhotosError.NotFound
            StorageException.ERROR_RETRY_LIMIT_EXCEEDED -> NotePhotosError.Network
            StorageException.ERROR_QUOTA_EXCEEDED -> NotePhotosError.AccountStorageLimitReached
            else -> NotePhotosError.Repository(message)
        }
        is FirebaseNetworkException -> NotePhotosError.Network
        else -> NotePhotosError.Repository(message)
    }
    return NotePhotosException(mapped, this)
}

private fun FirebaseFunctionsException.toNotePhotosError(): NotePhotosError {
    val reason = (details as? Map<*, *>)?.get("reason") as? String
    return when (code) {
        FirebaseFunctionsException.Code.UNAUTHENTICATED -> NotePhotosError.UploadNotAllowed
        FirebaseFunctionsException.Code.PERMISSION_DENIED -> NotePhotosError.PermissionDenied
        FirebaseFunctionsException.Code.NOT_FOUND -> NotePhotosError.NotFound
        FirebaseFunctionsException.Code.INVALID_ARGUMENT -> when (reason) {
            "TOO_LARGE" -> NotePhotosError.ImageTooLarge
            "PAYLOAD_MISMATCH" -> NotePhotosError.UploadSessionExpired
            else -> NotePhotosError.InvalidImage
        }
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> when (reason) {
            "NOTE_LIMIT" -> NotePhotosError.NoteLimitReached
            "ACCOUNT_COUNT" -> NotePhotosError.AccountPhotoLimitReached
            "ACCOUNT_BYTES" -> NotePhotosError.AccountStorageLimitReached
            "DAILY_LIMIT" -> NotePhotosError.DailyUploadLimitReached
            "MONTHLY_LIMIT" -> NotePhotosError.MonthlyUploadLimitReached
            "CALL_LIMIT" -> NotePhotosError.DailyOperationLimitReached
            "GLOBAL_LIMIT" -> NotePhotosError.GlobalLimitReached
            "RETRY_LIMIT" -> NotePhotosError.UploadSessionExpired
            else -> NotePhotosError.Repository(message)
        }
        FirebaseFunctionsException.Code.ABORTED,
        FirebaseFunctionsException.Code.UNAVAILABLE,
        FirebaseFunctionsException.Code.DEADLINE_EXCEEDED,
        -> NotePhotosError.Network
        else -> NotePhotosError.Repository(message)
    }
}
