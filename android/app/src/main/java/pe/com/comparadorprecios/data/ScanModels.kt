package pe.com.comparadorprecios.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request — el backend exige data URI completa con 1 de 4 tipos y base64 sin saltos. */
@Serializable
data class ScanRequest(
    val image: String,
)

/** Identificación visual devuelta por el backend (Plan 1). */
@Serializable
data class Identification(
    val marca: String,
    val nombre: String,
    val presentacion: String,
    val categoria: String,
    val confianza: Double,
)

/**
 * Resultado por tienda. `estado` es exactamente uno de
 * "encontrado" | "no_encontrado" | "error" (contrato backend, Plan 2).
 */
@Serializable
data class StoreResult(
    val tienda: String,
    val estado: String,
    val producto: String? = null,
    val precio: Double? = null,
    val url: String? = null,
    val mensaje: String? = null,
) {
    val isFound: Boolean get() = estado == "encontrado"
}

@Serializable
data class ScanResponse(
    val identification: Identification,
    // Confianza < 0.5 → el backend devuelve [] (no se buscaron tiendas).
    val tiendas: List<StoreResult> = emptyList(),
)

@Serializable
data class ErrorBody(
    val error: String = "",
)

/** Umbral del backend (CONFIDENCE_THRESHOLD = 0.5 en lib/scanHandler.ts). */
const val LOW_CONFIDENCE_THRESHOLD = 0.5

fun Identification.isLowConfidence(): Boolean = confianza < LOW_CONFIDENCE_THRESHOLD
