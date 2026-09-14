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
    suspend fun scan(
        imageDataUri: String,
        contexto: DeviceContextPayload? = null,
        categoria: String? = null,
    ): ScanResponse =
        mapErrors(
            validationMessage = { detail ->
                "La imagen no es válida. Toma otra foto (JPG/PNG, menos de ~3 MB). ${detail.orEmpty()}".trim()
            },
        ) { api.scan(ScanRequest(imageDataUri, contexto, categoria)) }

    suspend fun prices(identification: Identification, contexto: DeviceContextPayload? = null): PricesResponse =
        mapErrors(
            validationMessage = { "No se pudo buscar este producto: faltan datos para identificarlo." },
        ) { api.prices(PricesRequest(identification, contexto)) }

    private suspend fun <T> mapErrors(validationMessage: (detail: String?) -> String, call: suspend () -> T): T {
        try {
            return call()
        } catch (e: HttpException) {
            val detail = e.response()?.errorBody()?.string()?.take(300)
            throw when (e.code()) {
                400 -> ScanError.Validation(validationMessage(detail))
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
