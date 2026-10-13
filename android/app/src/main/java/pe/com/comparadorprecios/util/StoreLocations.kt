package pe.com.comparadorprecios.util

import pe.com.comparadorprecios.data.StoreResult
import java.util.Locale
import kotlin.math.roundToInt

object StoreLocations {
    /** Cadenas con tiendas físicas consultables (VTEX, ver lib/stores/nearbyStores.ts en el backend). */
    val CHAINS_WITH_STORES = setOf("Plaza Vea", "Promart", "Oechsle", "Wong", "Metro")

    private const val WALKING_KMH = 4.8
    private const val MAX_WALKING_KM = 2.5

    /** Solo vale la pena buscar la tienda física de las cadenas donde el producto SÍ se encontró. */
    fun chainsToLocate(tiendas: List<StoreResult>): List<String> =
        tiendas.filter { it.isFound && it.tienda in CHAINS_WITH_STORES }.map { it.tienda }.distinct()

    /** "800 m" en vez de "0.8 km": los números concretos y chicos se sienten más cerca. */
    fun formatDistance(km: Double): String {
        if (km >= 1) return String.format(Locale.US, "%.1f km", km)
        val meters = ((km * 1000 / 50).roundToInt() * 50).coerceAtLeast(50)
        return "$meters m"
    }

    /** Minutos caminando, solo si es razonable ir a pie; más lejos no se promete un tiempo. */
    fun walkingMinutes(km: Double): Int? =
        if (km > MAX_WALKING_KM) null else (km / WALKING_KMH * 60).roundToInt().coerceAtLeast(1)

    /** Abre Google Maps (o la app de mapas que haya) con indicaciones hasta la tienda. */
    fun directionsUrl(lat: Double, lng: Double): String =
        "https://www.google.com/maps/dir/?api=1&destination=$lat,$lng"
}
