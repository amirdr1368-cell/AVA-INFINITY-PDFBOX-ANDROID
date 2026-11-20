package com.avainfinity.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvaWardrobeImageFormatTest {
    @Test fun detectsPngFromSignature() {
        assertEquals("image/png", AvaWardrobeImageFormat.detect(
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        )?.mimeType)
    }

    @Test fun detectsJpegFromSignature() {
        assertEquals(".jpg", AvaWardrobeImageFormat.detect(
            byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        )?.extension)
    }

    @Test fun detectsBothGifSignatures() {
        for (signature in listOf("GIF87a", "GIF89a")) {
            assertEquals("image/gif", AvaWardrobeImageFormat.detect(signature.toByteArray(Charsets.US_ASCII))?.mimeType)
        }
    }

    @Test fun detectsWebpOnlyWithRiffAndWebpMarkers() {
        assertEquals(".webp", AvaWardrobeImageFormat.detect("RIFF0000WEBP".toByteArray(Charsets.US_ASCII))?.extension)
        assertNull(AvaWardrobeImageFormat.detect("RIFF0000NOPE".toByteArray(Charsets.US_ASCII)))
    }

    @Test fun rejectsUnknownAndTruncatedHeaders() {
        assertNull(AvaWardrobeImageFormat.detect(byteArrayOf(1, 2, 3)))
        assertNull(AvaWardrobeImageFormat.detect("not-an-image".toByteArray(Charsets.US_ASCII)))
    }
}
