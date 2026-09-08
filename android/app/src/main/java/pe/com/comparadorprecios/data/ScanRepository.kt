package pe.com.comparadorprecios.data

import retrofit2.HttpException
import java.io.IOException

/** Errores de dominio mapeados desde HTTP — mensajes accionables en español. */
sealed class ScanError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    class Validation(message: String) : ScanError(message)
    class IdentificationFailed(message: String) : ScanError(message)
    class Unauthorized(message: String) : ScanError(message)
    class Server(message: String) : ScanError(message)
    class Network(message: String, cause: Throwable? = null) : ScanError(message, cause)
}

class ScanRepository(private val api: ScanApi) {
    suspend fun scan(imageDataUri: String): ScanResponse {
        try {
            return api.scan(ScanRequest(imageDataUri))
        } catch (e: HttpException) {
            val detail = e.response()?.errorBody()?.string()?.take(300)
            throw when (e.code()) {
                400 -> ScanError.Validation(
                    "La imagen no es válida. Toma otra foto (JPG/PNG, menos de ~3 MB). ${detail.orEmpty()}".trim()
                )
                401 -> ScanError.Unauthorized("Tu sesión expiró. Inicia sesión de nuevo.")
                502 -> ScanError.IdentificationFailed(
                    "No se pudo identificar el producto. Reintenta con mejor luz y el empaque visible."
                )
                else -> ScanError.Server("Error del servidor (${e.code()}). Reintenta en unos segundos.")
            }
        } catch (e: IOException) {
            throw ScanError.Network("Sin conexión o el servidor tardó demasiado. Revisa tu internet y reintenta.", e)
        }
    }
}
