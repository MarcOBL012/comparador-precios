package pe.com.comparadorprecios.util

import android.graphics.Bitmap
import java.io.ByteArrayOutputStream

/** Genera la miniatura JPEG del historial a partir del bitmap capturado. */
object ThumbnailEncoder {
    fun encode(original: Bitmap): ByteArray {
        val (targetW, targetH) = ThumbnailSpec.compute(original.width, original.height)
        val scaled = if (targetW == original.width && targetH == original.height) {
            original
        } else {
            Bitmap.createScaledBitmap(original, targetW, targetH, true)
        }
        try {
            for (quality in ThumbnailSpec.Qualities) {
                val bytes = jpeg(scaled, quality)
                if (bytes.size <= ThumbnailSpec.MAX_BYTES || quality == ThumbnailSpec.Qualities.last()) {
                    return bytes
                }
            }
            error("unreachable")
        } finally {
            if (scaled !== original) scaled.recycle()
        }
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}
