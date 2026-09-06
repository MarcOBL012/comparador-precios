package pe.com.comparadorprecios.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanModelsTest {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Test
    fun `parsea respuesta 200 con tiendas`() {
        val body = """
            {
              "identification": {"marca":"Gloria","nombre":"Leche evaporada","presentacion":"400g","categoria":"abarrotes","confianza":0.92},
              "tiendas": [
                {"tienda":"Plaza Vea","estado":"encontrado","producto":"Leche Evaporada Gloria 400g","precio":4.5,"url":"https://www.plazavea.com.pe/p/1"},
                {"tienda":"Wong","estado":"no_encontrado"}
              ]
            }
        """.trimIndent()
        val res = json.decodeFromString<ScanResponse>(body)
        assertEquals("Gloria", res.identification.marca)
        assertEquals(2, res.tiendas.size)
        assertTrue(res.tiendas[0].isFound)
        assertEquals(4.5, res.tiendas[0].precio!!, 0.0)
        assertNull(res.tiendas[1].precio)
    }

    @Test
    fun `baja confianza trae tiendas vacia`() {
        val body = """
            {
              "identification": {"marca":"","nombre":"","presentacion":"","categoria":"","confianza":0.2},
              "tiendas": []
            }
        """.trimIndent()
        val res = json.decodeFromString<ScanResponse>(body)
        assertTrue(res.identification.isLowConfidence())
        assertTrue(res.tiendas.isEmpty())
    }

    @Test
    fun `error trae mensaje sin producto-precio-url`() {
        val body = """
            {
              "identification": {"marca":"Gloria","nombre":"Leche evaporada","presentacion":"400g","categoria":"abarrotes","confianza":0.9},
              "tiendas": [{"tienda":"Plaza Vea","estado":"error","mensaje":"timeout de red"}]
            }
        """.trimIndent()
        val res = json.decodeFromString<ScanResponse>(body)
        assertEquals("error", res.tiendas[0].estado)
        assertEquals("timeout de red", res.tiendas[0].mensaje)
        assertNull(res.tiendas[0].producto)
    }

    @Test
    fun `umbral de baja confianza es 0 punto 5`() {
        assertTrue(Identification("G", "L", "400g", "abarrotes", 0.49).isLowConfidence())
        assertTrue(!Identification("G", "L", "400g", "abarrotes", 0.5).isLowConfidence())
    }
}
