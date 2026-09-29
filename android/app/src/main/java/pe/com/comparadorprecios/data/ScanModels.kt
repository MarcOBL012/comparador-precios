package pe.com.comparadorprecios.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Contexto del dispositivo que el backend usa para decidir cuánto buscar (ver lib/searchStores.ts). */
@Serializable
data class DeviceContextPayload(
    val ahorroBateria: Boolean,
)

/** Request — el backend exige data URI completa con 1 de 4 tipos y base64 sin saltos. */
@Serializable
data class ScanRequest(
    val image: String,
    val contexto: DeviceContextPayload? = null,
    /** Categoría marcada antes de la foto; null = que la IA decida sola. */
    val categoria: String? = null,
)

/** Identificación visual devuelta por el backend (Plan 1). */
@Serializable
data class Identification(
    val marca: String,
    val nombre: String,
    val presentacion: String,
    val categoria: String,
    val confianza: Double,
    // Escaneos guardados antes de que el backend devolviera el tipo no lo traen.
    val tipo: String = "",
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
    /**
     * "web" = precio visto en Google Shopping. "busqueda_web" = último recurso: un link de una
     * búsqueda normal, SIN precio confirmado (ver lib/stores/googleSearch.ts en el backend).
     * null = consultado directo a la tienda.
     */
    val fuente: String? = null,
) {
    val isFound: Boolean get() = estado == "encontrado"
    val isFromWeb: Boolean get() = fuente == "web"
    val isFromWebSearchFallback: Boolean get() = fuente == "busqueda_web"
}

@Serializable
data class ScanResponse(
    val identification: Identification,
    // Confianza < 0.5 → el backend devuelve [] (no se buscaron tiendas).
    val tiendas: List<StoreResult> = emptyList(),
    val busquedaWeb: String = WEB_SEARCH_NOT_APPLICABLE,
)

@Serializable
data class PricesRequest(
    val identification: Identification,
    val contexto: DeviceContextPayload? = null,
)

@Serializable
data class PricesResponse(
    val tiendas: List<StoreResult> = emptyList(),
    val busquedaWeb: String = WEB_SEARCH_NOT_APPLICABLE,
)

@Serializable
data class ErrorBody(
    val error: String = "",
)

const val WEB_SEARCH_NOT_APPLICABLE = "no_aplica"
const val WEB_SEARCH_SKIPPED_FOR_BATTERY = "omitida_por_bateria"

/** Umbral del backend (CONFIDENCE_THRESHOLD = 0.5 en lib/scanHandler.ts). */
const val LOW_CONFIDENCE_THRESHOLD = 0.5

fun Identification.isLowConfidence(): Boolean = confianza < LOW_CONFIDENCE_THRESHOLD

val Identification.title: String get() = "$marca $nombre $presentacion".trim()
