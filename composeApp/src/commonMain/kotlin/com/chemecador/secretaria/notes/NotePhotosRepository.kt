package com.chemecador.secretaria.notes

interface NotePhotosRepository {
    val isSupported: Boolean
    val canUpload: Boolean

    suspend fun getPhotos(key: NotePhotosKey): Result<List<NotePhoto>>
    suspend fun getPhotoBytes(photo: NotePhoto, fullSize: Boolean): Result<ByteArray>
    suspend fun uploadPhoto(key: NotePhotosKey, prepared: PreparedNotePhoto): Result<NotePhoto>
    suspend fun deletePhoto(key: NotePhotosKey, photoId: String): Result<Unit>
}

class NoopNotePhotosRepository : NotePhotosRepository {
    override val isSupported: Boolean = false
    override val canUpload: Boolean = false

    override suspend fun getPhotos(key: NotePhotosKey): Result<List<NotePhoto>> =
        Result.success(emptyList())

    override suspend fun getPhotoBytes(photo: NotePhoto, fullSize: Boolean): Result<ByteArray> =
        unsupported()

    override suspend fun uploadPhoto(
        key: NotePhotosKey,
        prepared: PreparedNotePhoto,
    ): Result<NotePhoto> = unsupported()

    override suspend fun deletePhoto(key: NotePhotosKey, photoId: String): Result<Unit> =
        unsupported()

    private fun <T> unsupported(): Result<T> =
        Result.failure(NotePhotosException(NotePhotosError.Unsupported))
}
