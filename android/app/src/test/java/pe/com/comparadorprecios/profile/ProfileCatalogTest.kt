package pe.com.comparadorprecios.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pe.com.comparadorprecios.data.InsightsMode

class ProfileCatalogTest {

    private val selected = setOf("Bajar azúcar", "Deportista", "Gaming", "Historia")

    @Test
    fun `en modo salud solo manda los intereses de salud`() {
        assertEquals(
            listOf("Bajar azúcar", "Deportista"),
            ProfileCatalog.interestsFor(InsightsMode.SALUD, selected),
        )
    }

    @Test
    fun `en modo tecnologia solo manda los de tecnologia`() {
        assertEquals(listOf("Gaming"), ProfileCatalog.interestsFor(InsightsMode.TECNOLOGIA, selected))
    }

    @Test
    fun `en automatico manda todos los elegidos y el backend decide el modo`() {
        assertEquals(
            setOf("Bajar azúcar", "Deportista", "Gaming", "Historia"),
            ProfileCatalog.interestsFor(InsightsMode.AUTO, selected).toSet(),
        )
    }

    @Test
    fun `ignora etiquetas que ya no estan en el catalogo`() {
        assertEquals(emptyList<String>(), ProfileCatalog.interestsFor(InsightsMode.AUTO, setOf("Etiqueta vieja")))
    }

    @Test
    fun `las etiquetas son unicas y caben en el limite del backend`() {
        val labels = ProfileCatalog.GROUPS.flatMap { g -> g.interests.map { it.label } }
        assertEquals(labels.size, labels.toSet().size)
        assertTrue("el backend recorta etiquetas de más de 40 caracteres", labels.all { it.length <= 40 })
    }
}
