package com.chemecador.secretaria.notes

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Two permission-free paths, picked by API level. From Android 10 the photo lands straight in
 * `Pictures/Secretaria` through MediaStore; below that MediaStore would demand
 * `WRITE_EXTERNAL_STORAGE`, so the system document picker is used instead and the user chooses
 * where the file goes.
 */
@Composable
actual fun rememberNotePhotoDownloadController(
    onResult: (NotePhotoDownloadResult) -> Unit,
): NotePhotoDownloadController? {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val coroutineScope = rememberCoroutineScope()
    val session = remember { NotePhotoDownloadSession() }

    DisposableEffect(session) {
        onDispose {
            if (session.isActive) {
                session.finish()
                currentOnResult(NotePhotoDownloadResult.Cancelled)
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(JPEG_MIME_TYPE),
    ) { uri ->
        if (!session.isActive) return@rememberLauncherForActivityResult
        val bytes = session.pendingBytes
        if (uri == null || bytes == null) {
            session.finish()
            currentOnResult(NotePhotoDownloadResult.Cancelled)
            return@rememberLauncherForActivityResult
        }

        coroutineScope.launch {
            try {
                val saved = runCatching {
                    withContext(Dispatchers.IO) {
                        context.writeBytes(uri, bytes)
                    }
                }
                if (!session.isActive) return@launch
                session.finish()
                currentOnResult(saved.toDownloadResult())
            } catch (error: CancellationException) {
                if (session.isActive) {
                    session.finish()
                    currentOnResult(NotePhotoDownloadResult.Cancelled)
                }
                throw error
            }
        }
    }

    return remember(documentPicker, context) {
        object : NotePhotoDownloadController {
            override fun save(photo: NotePhoto, bytes: ByteArray) {
                if (session.isActive) return
                session.start(bytes)
                val fileName = notePhotoFileName()

                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    runCatching { documentPicker.launch(fileName) }
                        .onFailure { error ->
                            session.finish()
                            currentOnResult(
                                NotePhotoDownloadResult.Failed(
                                    NotePhotosError.Repository(error.message),
                                ),
                            )
                        }
                    return
                }

                coroutineScope.launch {
                    try {
                        val saved = runCatching {
                            withContext(Dispatchers.IO) {
                                context.saveToPictures(fileName, bytes)
                            }
                        }
                        if (!session.isActive) return@launch
                        session.finish()
                        currentOnResult(saved.toDownloadResult())
                    } catch (error: CancellationException) {
                        if (session.isActive) {
                            session.finish()
                            currentOnResult(NotePhotoDownloadResult.Cancelled)
                        }
                        throw error
                    }
                }
            }
        }
    }
}

private class NotePhotoDownloadSession {
    var isActive: Boolean = false
        private set
    var pendingBytes: ByteArray? = null
        private set

    fun start(bytes: ByteArray) {
        isActive = true
        pendingBytes = bytes
    }

    fun finish() {
        isActive = false
        pendingBytes = null
    }
}

/** Publishes the file only once the bytes are on disk, so no half-written image is ever scanned. */
private fun Context.saveToPictures(fileName: String, bytes: ByteArray) {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, JPEG_MIME_TYPE)
        put(
            MediaStore.Images.Media.RELATIVE_PATH,
            "${Environment.DIRECTORY_PICTURES}/$ALBUM_NAME",
        )
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("MediaStore rejected the new image")

    try {
        writeBytes(uri, bytes)
        contentResolver.update(
            uri,
            ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
            null,
            null,
        )
    } catch (error: Throwable) {
        runCatching { contentResolver.delete(uri, null, null) }
        throw error
    }
}

private fun Context.writeBytes(uri: Uri, bytes: ByteArray) {
    contentResolver.openOutputStream(uri)?.use { output -> output.write(bytes) }
        ?: error("Could not open $uri for writing")
}

private fun Result<Unit>.toDownloadResult(): NotePhotoDownloadResult =
    fold(
        onSuccess = { NotePhotoDownloadResult.Saved },
        onFailure = { error ->
            NotePhotoDownloadResult.Failed(NotePhotosError.Repository(error.message))
        },
    )

private fun notePhotoFileName(): String {
    val timestamp = SimpleDateFormat(FILE_NAME_PATTERN, Locale.US).format(Date())
    return "Secretaria_$timestamp.jpg"
}

private const val JPEG_MIME_TYPE = "image/jpeg"
private const val ALBUM_NAME = "Secretaria"
private const val FILE_NAME_PATTERN = "yyyyMMdd_HHmmss"
