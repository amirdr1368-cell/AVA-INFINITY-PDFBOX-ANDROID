package com.avainfinity.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Android-side image validation for owner wardrobe media.
 *
 * Bounds are checked before pixel decoding. A sampled decode then confirms that
 * the platform can actually decode the image without allocating its full dimensions.
 */
object AvaWardrobeAndroidImageValidator {
    const val MAX_EDGE = 12_000
    const val MAX_PIXELS = 40_000_000L
    private const val PREVIEW_EDGE = 1_200

    fun isValidImageFile(file: File): Boolean {
        if (!file.isFile || file.length() !in 1L..AvaWardrobeMediaBundleCodec.MAX_ITEM_BYTES) {
            return false
        }

        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)

            val width = bounds.outWidth
            val height = bounds.outHeight
            if (width <= 0 || height <= 0 ||
                width > MAX_EDGE || height > MAX_EDGE ||
                width.toLong() * height.toLong() > MAX_PIXELS
            ) {
                return false
            }

            var sampleSize = 1
            while (width / sampleSize > PREVIEW_EDGE || height / sampleSize > PREVIEW_EDGE) {
                sampleSize *= 2
            }

            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = false
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.RGB_565
            }
            val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return false
            try {
                decoded.width > 0 && decoded.height > 0
            } finally {
                decoded.recycle()
            }
        } catch (_: Exception) {
            false
        }
    }
}
