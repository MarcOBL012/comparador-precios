package pe.com.comparadorprecios.shopping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.com.comparadorprecios.data.StoreResult

class ShoppingBasketTest {

    private fun found(tienda: String, precio: Double, fuente: String? = null) =
        StoreResult(tienda, "encontrado", "prod", precio, "https://x", fuente = fuente)

    private val leche = BasketLine(
        "Leche Gloria",
        quantity = 3,
        tiendas = listOf(found("Plaza Vea", 4.5), found("Wong", 4.2), StoreResult("Metro", "no_encontrado")),
    )
    // Wong: 4.2 × 3 + 6.5 = 19.10 · Plaza Vea: 4.5 × 3 + 6.0 = 19.50
    private val arroz = BasketLine("Arroz Costeño", quantity = 1, tiendas = listOf(found("Plaza Vea", 6.0), found("Wong", 6.5)))

    @Test
    fun `suma precio por cantidad en cada tienda`() {
        val wong = ShoppingBasket.compare(listOf(leche, arroz)).first { it.tienda == "Wong" }

        assertEquals(19.1, wong.total, 0.001)
        assertEquals(2, wong.foundCount)
        assertTrue(wong.missing.isEmpty())
    }

    @Test
    fun `entre tiendas con los mismos productos gana la mas barata`() {
        val baskets = ShoppingBasket.compare(listOf(leche, arroz))

        assertEquals(listOf("Wong", "Plaza Vea"), baskets.map { it.tienda })
    }

    @Test
    fun `una tienda barata a la que le faltan productos queda detras de una completa`() {
        val cafe = BasketLine("Café", quantity = 1, tiendas = listOf(found("Plaza Vea", 20.0), found("Tambo", 1.0)))
        val baskets = ShoppingBasket.compare(listOf(cafe, arroz))

        assertEquals("Plaza Vea", baskets.first().tienda)
        assertEquals(listOf("Arroz Costeño"), baskets.first { it.tienda == "Tambo" }.missing)
    }

    @Test
    fun `ignora tiendas sin precio y marca las que solo vienen de la web`() {
        val mouse = BasketLine("Mouse", 1, listOf(found("Sodimac", 51.5, fuente = "web"), StoreResult("Oechsle", "error")))

        val baskets = ShoppingBasket.compare(listOf(mouse))

        assertEquals(listOf("Sodimac"), baskets.map { it.tienda })
        assertTrue(baskets.first().fromWeb)
    }
}
