package pe.com.comparadorprecios.data

import kotlinx.serialization.Serializable

/** Modos de datos del producto (contrato de /api/insights). "auto" lo resuelve el backend por categoría. */
object InsightsMode {
    const val AUTO = "auto"
    const val SALUD = "salud"
    const val TECNOLOGIA = "tecnologia"
    const val CURIOSIDADES = "curiosidades"
}

@Serializable
data class InsightsRequest(
    val identification: Identification,
    val modo: String = InsightsMode.AUTO,
)

@Serializable
data class InsightPoint(
    val emoji: String,
    val texto: String,
)

@Serializable
data class ProductInsights(
    val titular: String,
    /** "verde" | "ambar" | "rojo" (modo salud) o "neutro". */
    val semaforo: String = "neutro",
    val etiquetaSemaforo: String = "",
    val puntos: List<InsightPoint> = emptyList(),
    val consejo: String = "",
    val datoCurioso: String = "",
)

@Serializable
data class InsightsResponse(
    /** El modo realmente usado: con "auto" el backend devuelve el que eligió. */
    val modo: String,
    val insights: ProductInsights,
)

@Serializable
data class NearbyStoresRequest(
    val cadenas: List<String>,
    val lat: Double,
    val lng: Double,
)

@Serializable
data class NearbyStore(
    val cadena: String,
    val nombre: String,
    val direccion: String = "",
    val lat: Double,
    val lng: Double,
    val distanciaKm: Double,
)

@Serializable
data class NearbyStoresResponse(
    val tiendas: List<NearbyStore> = emptyList(),
)
