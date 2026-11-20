package com.avainfinity.android

/**
 * Detects image formats from file bytes, never from a provider-supplied MIME type.
 * Android decoding and dimension checks are performed by the caller.
 */
object AvaWardrobeImageFormat {
    data class Detected(val mimeType: String, val extension: String)

    fun detect(header: ByteArray): Detected? {
        if (header.size >= 8 &&
            header[0] == 0x89.toByte() && header[1] == 0x50.toByte() &&
            header[2] == 0x4E.toByte() && header[3] == 0x47.toByte() &&
            header[4] == 0x0D.toByte() && header[5] == 0x0A.toByte() &&
            header[6] == 0x1A.toByte() && header[7] == 0x0A.toByte()
        ) return Detected("image/png", ".png")

        if (header.size >= 3 &&
            header[0] == 0xFF.toByte() && header[1] == 0xD8.toByte() &&
            header[2] == 0xFF.toByte()
        ) return Detected("image/jpeg", ".jpg")

        if (header.size >= 6) {
            val gif = String(header, 0, 6, Charsets.US_ASCII)
            if (gif == "GIF87a" || gif == "GIF89a") return Detected("image/gif", ".gif")
        }

        if (header.size >= 12 &&
            String(header, 0, 4, Charsets.US_ASCII) == "RIFF" &&
            String(header, 8, 4, Charsets.US_ASCII) == "WEBP"
        ) return Detected("image/webp", ".webp")

        return null
    }
}
