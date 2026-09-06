package pe.com.comparadorprecios.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class DataUriTest {

    @Test
    fun `build usa el formato exacto del backend`() {
        val uri = DataUri.build("ABC123", "image/jpeg")
        assertTrue(DataUri.isValid(uri))
    }

    @Test
    fun `rechaza tipos no soportados como tiff`() {
        assertFalse(DataUri.isValid("data:image/tiff;base64,ABC123"))
    }

    @Test
    fun `rechaza base64 con saltos de linea (Base64_DEFAULT romperia el contrato)`() {
        // Simula lo que haría Base64.DEFAULT: \n cada 76 chars.
        val withWraps = "A".repeat(76) + "\n" + "B".repeat(76)
        val uri = DataUri.build(withWraps)
        assertFalse(DataUri.isValid(uri))
    }

    @Test
    fun `el encoder sin wraps nunca genera saltos de linea`() {
        val bytes = ByteArray(1024) { it.toByte() }
        val encoded = Base64.getEncoder().encodeToString(bytes)
        assertFalse(encoded.contains("\n"))
        assertTrue(DataUri.isValid(DataUri.build(encoded)))
    }

    @Test
    fun `exceedsLimit respeta los 4M de caracteres`() {
        assertFalse(DataUri.exceedsLimit(DataUri.build("ABC")))
        assertTrue(DataUri.exceedsLimit("x".repeat(DataUri.MAX_DATA_URI_LENGTH + 1)))
    }
}
