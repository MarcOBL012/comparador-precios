package pe.com.comparadorprecios.util

import org.junit.Assert.assertEquals
import org.junit.Test
import pe.com.comparadorprecios.data.StoreResult

class PriceSortingTest {

    private fun found(tienda: String, precio: Double) =
        StoreResult(tienda, "encontrado", producto = "$tienda prod", precio = precio, url = "https://x.pe")

    @Test
    fun `encontrados primero ordenados por precio ascendente`() {
        val input = listOf(
            found("Wong", 4.6),
            found("Plaza Vea", 4.5),
        )
        val sorted = PriceSorting.sortForDisplay(input)
        assertEquals(listOf("Plaza Vea", "Wong"), sorted.map { it.tienda })
    }

    @Test
    fun `no_encontrado y error van al final sin omitirse`() {
        val input = listOf(
            StoreResult("Wong", "error", mensaje = "timeout"),
            StoreResult("Plaza Vea", "no_encontrado"),
            found("Tienda Futura", 9.9),
        )
        val sorted = PriceSorting.sortForDisplay(input)
        assertEquals("Tienda Futura", sorted[0].tienda)
        assertEquals("no_encontrado", sorted[1].estado)
        assertEquals("error", sorted[2].estado)
    }

    @Test
    fun `no asume cantidad de tiendas`() {
        assertEquals(0, PriceSorting.sortForDisplay(emptyList()).size)
        val three = listOf(found("C", 3.0), found("A", 1.0), found("B", 2.0))
        assertEquals(listOf("A", "B", "C"), PriceSorting.sortForDisplay(three).map { it.tienda })
    }
}
