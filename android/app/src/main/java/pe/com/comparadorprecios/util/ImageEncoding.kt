package pe.com.comparadorprecios.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Codificación de imagen según el contrato del backend (HANDOFF-PLAN3.md):
 * - Data URI `data:image/<jpeg|png|gif|webp>;base64,<...>`
 * - Base64 **NO_WRAP** (DEFAULT inserta \n cada 76 chars y el regex del backend lo rechaza → 400).
 * - Máximo 4_000_000 caracteres en el string completo.
 */
object ImageEncoding {

    /** Límite del backend (ver [DataUri.MAX_DATA_URI_LENGTH]). */
    const val MAX_DATA_URI_LENGTH = DataUri.MAX_DATA_URI_LENGTH

    fun toDataUri(jpegBytes: ByteArray, mimeType: String = "image/jpeg"): String {
        // NO_WRAP es obligatorio — ver HANDOFF (DEFAULT inserta \n cada 76 chars → 400).
        val base64 = Base64.encodeToString(jpegBytes, Base64.NO_WRAP)
        return DataUri.build(base64, mimeType)
    }

    fun isValidDataUri(dataUri: String): Boolean = DataUri.isValid(dataUri)

    fun exceedsLimit(dataUri: String): Boolean = DataUri.exceedsLimit(dataUri)

    /**
     * Comprime un JPEG bajando calidad hasta entrar en el límite.
     * Devuelve los bytes comprimidos (ya listos para [toDataUri]).
     */
    fun compressToLimit(
        original: Bitmap,
        mimeType: String = "image/jpeg",
        maxSidePx: Int = 1600,
    ): ByteArray {
        val scaled = scaleDown(original, maxSidePx)
        var quality = 85
        var bytes = jpeg(scaled, quality)
        var dataUri = toDataUri(bytes, mimeType)
        while (exceedsLimit(dataUri) && quality > 20) {
            quality -= 15
            bytes = jpeg(scaled, quality)
            dataUri = toDataUri(bytes, mimeType)
        }
        if (scaled !== original) scaled.recycle()
        return bytes
    }

    private fun scaleDown(src: Bitmap, maxSide: Int): Bitmap {
        val longest = maxOf(src.width, src.height)
        if (longest <= maxSide) return src
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(
            src,
            (src.width * ratio).toInt(),
            (src.height * ratio).toInt(),
            true,
        )
    }

    private fun jpeg(bitmap: Bitmap, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray): Bitmap? =
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
