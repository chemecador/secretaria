package com.chemecador.secretaria.notes

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorSpace
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns an untrusted picker URI into the only image representation accepted by the backend.
 * The original bytes are bounded before any decoder sees them and are never uploaded.
 */
internal class AndroidNotePhotoCompressor(
    context: Context,
) {
    private val applicationContext = context.applicationContext

    suspend fun prepare(uri: Uri): Result<PreparedNotePhoto> = withContext(Dispatchers.IO) {
        val inputFile = runCatching {
            File.createTempFile(
                INPUT_FILE_PREFIX,
                INPUT_FILE_SUFFIX,
                applicationContext.cacheDir,
            )
        }.getOrElse { error ->
            return@withContext Result.failure(
                NotePhotosException(NotePhotosError.Repository(error.message), error),
            )
        }

        try {
            copyInputWithLimit(uri, inputFile)
            Result.success(prepareFromFile(inputFile))
        } catch (error: CancellationException) {
            throw error
        } catch (error: PhotoPreparationException) {
            Result.failure(NotePhotosException(error.error, error))
        } catch (error: IOException) {
            Result.failure(NotePhotosException(NotePhotosError.InvalidImage, error))
        } catch (error: OutOfMemoryError) {
            Result.failure(NotePhotosException(NotePhotosError.ImageTooLarge, error))
        } catch (error: Exception) {
            Result.failure(NotePhotosException(NotePhotosError.InvalidImage, error))
        } finally {
            inputFile.delete()
        }
    }

    private fun copyInputWithLimit(uri: Uri, target: File) {
        val input = applicationContext.contentResolver.openInputStream(uri)
            ?: throw PhotoPreparationException(NotePhotosError.InvalidImage)

        input.buffered().use { source ->
            FileOutputStream(target).buffered().use { destination ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var totalBytes = 0L

                while (true) {
                    val read = source.read(buffer)
                    if (read == -1) break
                    totalBytes += read
                    if (totalBytes > MAX_INPUT_BYTES) {
                        throw PhotoPreparationException(NotePhotosError.ImageTooLarge)
                    }
                    destination.write(buffer, 0, read)
                }
            }
        }
    }

    private fun prepareFromFile(inputFile: File): PreparedNotePhoto {
        val bounds = readBounds(inputFile)
        val sourcePixels = bounds.width.toLong() * bounds.height.toLong()
        if (sourcePixels > MAX_SOURCE_PIXELS) {
            throw PhotoPreparationException(NotePhotosError.ImageTooLarge)
        }

        var bitmap = decodeSampled(inputFile, bounds)
        try {
            bitmap = replaceBitmap(bitmap, scaleToMaxSide(bitmap, MAX_OUTPUT_SIDE_PX))
            bitmap = replaceBitmap(bitmap, applyExifOrientation(bitmap, readOrientation(inputFile)))
            bitmap = replaceBitmap(bitmap, flattenOnWhite(bitmap))

            var jpeg = encodeTowardsTarget(bitmap)
            if (jpeg.size > MAX_OUTPUT_BYTES) {
                bitmap = replaceBitmap(bitmap, scaleToMaxSide(bitmap, FALLBACK_OUTPUT_SIDE_PX))
                jpeg = encodeTowardsTarget(bitmap)
            }
            if (jpeg.size > MAX_OUTPUT_BYTES) {
                throw PhotoPreparationException(NotePhotosError.ImageTooLarge)
            }

            return PreparedNotePhoto(
                clientRequestId = UUID.randomUUID().toString(),
                bytes = jpeg,
                width = bitmap.width,
                height = bitmap.height,
            )
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private fun readBounds(file: File): ImageBounds {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)

        val width = options.outWidth
        val height = options.outHeight
        val mimeType = options.outMimeType.orEmpty().lowercase()
        if (width <= 0 || height <= 0 || mimeType.isBlank() || mimeType == GIF_MIME_TYPE) {
            throw PhotoPreparationException(NotePhotosError.InvalidImage)
        }
        return ImageBounds(width, height)
    }

    private fun decodeSampled(file: File, bounds: ImageBounds): Bitmap {
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds)
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inPreferredColorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
            inScaled = false
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
            ?: throw PhotoPreparationException(NotePhotosError.InvalidImage)
    }

    private fun calculateSampleSize(bounds: ImageBounds): Int {
        var sampleSize = 1
        val maxSide = maxOf(bounds.width, bounds.height)
        while (maxSide / (sampleSize * 2) >= MAX_OUTPUT_SIDE_PX) {
            sampleSize *= 2
        }
        return sampleSize
    }

    private fun readOrientation(file: File): Int =
        runCatching {
            ExifInterface(file).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

    private fun applyExifOrientation(source: Bitmap, orientation: Int): Bitmap {
        val flipHorizontally = orientation == ExifInterface.ORIENTATION_FLIP_HORIZONTAL ||
            orientation == ExifInterface.ORIENTATION_FLIP_VERTICAL ||
            orientation == ExifInterface.ORIENTATION_TRANSPOSE ||
            orientation == ExifInterface.ORIENTATION_TRANSVERSE
        val rotationDegrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_TRANSVERSE,
            -> 90f
            ExifInterface.ORIENTATION_ROTATE_180,
            ExifInterface.ORIENTATION_FLIP_VERTICAL,
            -> 180f
            ExifInterface.ORIENTATION_TRANSPOSE,
            ExifInterface.ORIENTATION_ROTATE_270,
            -> 270f
            else -> 0f
        }
        if (!flipHorizontally && rotationDegrees == 0f) return source

        val matrix = Matrix()
        if (flipHorizontally) matrix.setScale(-1f, 1f)
        if (rotationDegrees != 0f) matrix.postRotate(rotationDegrees)

        return Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            matrix,
            true,
        )
    }

    private fun scaleToMaxSide(source: Bitmap, maxSide: Int): Bitmap {
        val currentMaxSide = maxOf(source.width, source.height)
        if (currentMaxSide <= maxSide) return source

        val scale = maxSide.toDouble() / currentMaxSide.toDouble()
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun flattenOnWhite(source: Bitmap): Bitmap {
        val flattened = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(
                source,
                0f,
                0f,
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG),
            )
        }
        flattened.setHasAlpha(false)
        return flattened
    }

    /** Returns the highest useful quality that reaches the target, or quality 60 as fallback. */
    private fun encodeTowardsTarget(bitmap: Bitmap): ByteArray {
        val initial = encode(bitmap, INITIAL_JPEG_QUALITY)
        if (initial.size <= TARGET_OUTPUT_BYTES) return initial

        val minimum = encode(bitmap, MIN_JPEG_QUALITY)
        if (minimum.size > TARGET_OUTPUT_BYTES) return minimum

        var best = minimum
        var low = MIN_JPEG_QUALITY + 1
        var high = INITIAL_JPEG_QUALITY - 1
        while (low <= high) {
            val quality = (low + high) / 2
            val candidate = encode(bitmap, quality)
            if (candidate.size <= TARGET_OUTPUT_BYTES) {
                best = candidate
                low = quality + 1
            } else {
                high = quality - 1
            }
        }
        return best
    }

    private fun encode(bitmap: Bitmap, quality: Int): ByteArray {
        val output = ByteArrayOutputStream(TARGET_OUTPUT_BYTES.toInt())
        val encoded = bitmap.compress(Bitmap.CompressFormat.JPEG, quality, output)
        if (!encoded) throw PhotoPreparationException(NotePhotosError.InvalidImage)
        return output.toByteArray()
    }

    private fun replaceBitmap(current: Bitmap, replacement: Bitmap): Bitmap {
        if (replacement !== current && !current.isRecycled) current.recycle()
        return replacement
    }

    private data class ImageBounds(
        val width: Int,
        val height: Int,
    )

    private class PhotoPreparationException(
        val error: NotePhotosError,
    ) : Exception()

    private companion object {
        const val MAX_INPUT_BYTES = 20L * 1024L * 1024L
        const val MAX_SOURCE_PIXELS = 60_000_000L
        const val MAX_OUTPUT_SIDE_PX = 1600
        const val FALLBACK_OUTPUT_SIDE_PX = 1280
        const val TARGET_OUTPUT_BYTES = 600L * 1024L
        const val MAX_OUTPUT_BYTES = 1024L * 1024L
        const val INITIAL_JPEG_QUALITY = 82
        const val MIN_JPEG_QUALITY = 60
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val GIF_MIME_TYPE = "image/gif"
        const val INPUT_FILE_PREFIX = "note-photo-"
        const val INPUT_FILE_SUFFIX = ".input"
    }
}
