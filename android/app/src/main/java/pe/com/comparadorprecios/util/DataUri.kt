package pe.com.comparadorprecios.util

/**
 * Lógica pura (JVM, sin imports de Android) del contrato de imagen.
 * La codificación Base64 en producción usa `android.util.Base64.NO_WRAP`
 * (ver [ImageEncoding]); `java.util.Base64.getEncoder()` es su equivalente
 * sin saltos de línea y es lo que los tests usan para verificar el formato.
 */
object DataUri {
    const val MAX_DATA_URI_LENGTH = 4_000_000

    private val PATTERN =
        Regex("""^data:image/(jpeg|png|gif|webp);base64,([A-Za-z0-9+/]+={0,2})$""")

    fun build(base64NoWrap: String, mimeType: String = "image/jpeg"): String =
        "data:$mimeType;base64,$base64NoWrap"

    fun isValid(dataUri: String): Boolean = PATTERN.matches(dataUri)

    fun exceedsLimit(dataUri: String): Boolean = dataUri.length > MAX_DATA_URI_LENGTH
}
