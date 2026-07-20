package com.pickuppass.android.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import kotlin.math.min

/**
 * Mirrors the web app's client-side compressor: center-crop to a square,
 * resize to a fixed dimension, then step down JPEG quality until the result
 * is under maxBytes. Keeps parent avatar uploads comfortably within the
 * 100KB Storage-rule cap on Firebase's Spark (free) plan.
 */
object ImageCompressor {

    fun compress(
        context: Context,
        uri: Uri,
        targetSize: Int = 400,
        maxBytes: Int = 50 * 1024
    ): ByteArray {
        val original = decodeBitmap(context, uri)
        val squared = centerCropSquare(original)
        val resized = Bitmap.createScaledBitmap(squared, targetSize, targetSize, true)

        var quality = 90
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            resized.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            quality -= 10
        } while (bytes.size > maxBytes && quality >= 30)

        return bytes
    }

    private fun decodeBitmap(context: Context, uri: Uri): Bitmap {
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Could not open image")
        val bitmap = BitmapFactory.decodeStream(input)
        input.close()

        // Respect EXIF orientation so photos taken in portrait don't upload sideways.
        val rotation = context.contentResolver.openInputStream(uri)?.use { stream ->
            val exif = ExifInterface(stream)
            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f

        return if (rotation != 0f) {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    private fun centerCropSquare(bitmap: Bitmap): Bitmap {
        val side = min(bitmap.width, bitmap.height)
        val x = (bitmap.width - side) / 2
        val y = (bitmap.height - side) / 2
        return Bitmap.createBitmap(bitmap, x, y, side, side)
    }
}
