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
    /** Intereses del perfil ("Bajar azúcar", "Gaming"…) para personalizar los consejos. */
    val perfil: List<String> = emptyList(),
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
    /** 0-100 para el anillo de puntaje; null en curiosidades. */
    val puntaje: Double? = null,
    /** Frase dirigida al perfil de la persona; vacía si no configuró perfil. */
    val paraTi: String = "",
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

@Serializable
data class GeoPoint(val lat: Double, val lng: Double)

@Serializable
data class RouteRequest(
    val origen: GeoPoint,
    val destino: GeoPoint,
)

@Serializable
data class StoreRoute(
    /** "pie" | "auto": el backend decide según la distancia. */
    val modo: String,
    val distanciaKm: Double,
    val minutos: Int,
    /** Puntos [lat, lng] en orden, para dibujar la línea en el mapa. */
    val puntos: List<List<Double>> = emptyList(),
) {
    val walking: Boolean get() = modo == "pie"
}

@Serializable
data class RouteResponse(
    /** null = no se encontró camino; el mapa muestra igual las dos posiciones. */
    val ruta: StoreRoute? = null,
)
