package pe.com.comparadorprecios.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.com.comparadorprecios.data.StoreResult

class PriceInsightsTest {

    private fun found(tienda: String, precio: Double) = StoreResult(tienda, "encontrado", "prod", precio, "https://x")

    private val tiendas = listOf(found("Oechsle", 83.0), StoreResult("Wong", "no_encontrado"), found("Promart", 49.0), found("Plaza Vea", 56.0))

    @Test
    fun `resume mejor tienda, ahorro frente a la mas cara y cantidad de ofertas`() {
        val summary = PriceInsights.summarize(tiendas)!!

        assertEquals("Promart", summary.bestStore)
        assertEquals(49.0, summary.bestPrice, 0.0)
        assertEquals(34.0, summary.savings, 0.001)
        assertEquals(3, summary.offersCount)
    }

    @Test
    fun `sin ofertas no hay resumen`() {
        assertNull(PriceInsights.summarize(listOf(StoreResult("Wong", "no_encontrado"))))
    }

    @Test
    fun `extraOverBest da la diferencia solo a las que no son la mejor`() {
        val summary = PriceInsights.summarize(tiendas)

        assertEquals(7.0, PriceInsights.extraOverBest(found("Plaza Vea", 56.0), summary)!!, 0.001)
        assertNull(PriceInsights.extraOverBest(found("Promart", 49.0), summary))
        assertNull(PriceInsights.extraOverBest(StoreResult("Wong", "no_encontrado"), summary))
    }
}
