package pe.com.comparadorprecios.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.com.comparadorprecios.data.StoreResult

class StoreLocationsTest {

    @Test
    fun `solo busca tienda fisica de cadenas VTEX donde el producto se encontro`() {
        val tiendas = listOf(
            StoreResult("Plaza Vea", "encontrado", "Leche", 4.5),
            StoreResult("Wong", "no_encontrado"),
            StoreResult("Falabella", "encontrado", "Leche", 5.0, fuente = "web"),
            StoreResult("Oechsle", "encontrado", "Leche", 4.9),
        )

        assertEquals(listOf("Plaza Vea", "Oechsle"), StoreLocations.chainsToLocate(tiendas))
    }

    @Test
    fun `menos de un km se muestra en metros redondeados a 50`() {
        assertEquals("250 m", StoreLocations.formatDistance(0.238))
        assertEquals("50 m", StoreLocations.formatDistance(0.01))
        assertEquals("1.2 km", StoreLocations.formatDistance(1.2245))
    }

    @Test
    fun `solo promete minutos caminando si la tienda esta a una distancia caminable`() {
        assertEquals(3, StoreLocations.walkingMinutes(0.238))
        assertNull(StoreLocations.walkingMinutes(4.0))
    }

    @Test
    fun `el enlace de como llegar abre indicaciones a las coordenadas de la tienda`() {
        assertEquals(
            "https://www.google.com/maps/dir/?api=1&destination=-12.04855,-77.04271",
            StoreLocations.directionsUrl(-12.04855, -77.04271),
        )
    }
}
