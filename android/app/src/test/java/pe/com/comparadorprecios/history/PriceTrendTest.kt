package pe.com.comparadorprecios.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pe.com.comparadorprecios.data.StoreResult

class PriceTrendTest {

    private fun found(tienda: String, precio: Double) = StoreResult(tienda, "encontrado", "prod", precio, "https://x")

    @Test
    fun `compara el precio de cada tienda y ordena por el mas barato de hoy`() {
        val before = listOf(found("Plaza Vea", 56.0), found("Promart", 49.0))
        val now = listOf(found("Plaza Vea", 45.0), found("Promart", 52.0))

        val changes = PriceTrend.compare(before, now)

        assertEquals(listOf("Plaza Vea", "Promart"), changes.map { it.tienda })
        assertEquals(-11.0, changes[0].difference!!, 0.001)
        assertEquals(3.0, changes[1].difference!!, 0.001)
    }

    @Test
    fun `detecta tiendas que ya no lo tienen y tiendas nuevas`() {
        val before = listOf(found("Wong", 4.6))
        val now = listOf(StoreResult("Wong", "no_encontrado"), found("Metro", 4.5))

        val changes = PriceTrend.compare(before, now).associateBy { it.tienda }

        assertNull(changes.getValue("Wong").now)
        assertNull(changes.getValue("Metro").before)
        assertNull(changes.getValue("Metro").difference)
    }

    @Test
    fun `bestPrice ignora las tiendas sin resultado`() {
        assertEquals(4.5, PriceTrend.bestPrice(listOf(StoreResult("Wong", "error"), found("Metro", 4.5)))!!, 0.0)
        assertNull(PriceTrend.bestPrice(listOf(StoreResult("Wong", "no_encontrado"))))
    }
}
