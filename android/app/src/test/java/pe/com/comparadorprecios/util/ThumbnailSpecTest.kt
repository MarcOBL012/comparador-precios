package pe.com.comparadorprecios.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ThumbnailSpecTest {

    @Test
    fun `reduce el lado largo a 256 preservando aspecto`() {
        assertEquals(256 to 192, ThumbnailSpec.compute(1600, 1200))
        assertEquals(192 to 256, ThumbnailSpec.compute(1200, 1600))
    }

    @Test
    fun `no escala imagenes ya pequenas`() {
        assertEquals(200 to 100, ThumbnailSpec.compute(200, 100))
    }

    @Test
    fun `imagen cuadrada queda 256x256`() {
        assertEquals(256 to 256, ThumbnailSpec.compute(2000, 2000))
    }
}
