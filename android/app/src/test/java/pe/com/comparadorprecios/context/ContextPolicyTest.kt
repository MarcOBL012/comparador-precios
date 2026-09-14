package pe.com.comparadorprecios.context

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextPolicyTest {

    private val wifiFullBattery = DeviceContext(NetworkType.UNMETERED, batteryPercent = 80, charging = false, powerSaveMode = false)

    @Test
    fun `wifi con bateria normal escanea a resolucion completa sin avisos`() {
        val policy = ContextPolicy.decide(wifiFullBattery)

        assertTrue(policy.canScan)
        assertEquals(ContextPolicy.FULL_IMAGE_SIDE_PX, policy.maxImageSidePx)
        assertFalse(policy.saveBattery)
        assertTrue(policy.notices.isEmpty())
    }

    @Test
    fun `sin red no deja escanear y solo muestra el aviso de conexion`() {
        val policy = ContextPolicy.decide(wifiFullBattery.copy(network = NetworkType.NONE, batteryPercent = 5))

        assertFalse(policy.canScan)
        assertEquals(listOf(ContextPolicy.NOTICE_OFFLINE), policy.notices)
    }

    @Test
    fun `datos moviles reducen la resolucion de la foto`() {
        val policy = ContextPolicy.decide(wifiFullBattery.copy(network = NetworkType.METERED))

        assertTrue(policy.canScan)
        assertEquals(ContextPolicy.REDUCED_IMAGE_SIDE_PX, policy.maxImageSidePx)
        assertEquals(listOf(ContextPolicy.NOTICE_METERED), policy.notices)
    }

    @Test
    fun `bateria baja sin cargar activa el ahorro`() {
        val policy = ContextPolicy.decide(wifiFullBattery.copy(batteryPercent = 20))

        assertTrue(policy.saveBattery)
        assertEquals(ContextPolicy.REDUCED_IMAGE_SIDE_PX, policy.maxImageSidePx)
        assertEquals(listOf(ContextPolicy.NOTICE_LOW_BATTERY), policy.notices)
    }

    @Test
    fun `bateria baja pero cargando no activa el ahorro`() {
        val policy = ContextPolicy.decide(wifiFullBattery.copy(batteryPercent = 10, charging = true))

        assertFalse(policy.saveBattery)
    }

    @Test
    fun `el modo ahorro del sistema activa el ahorro aunque haya bateria`() {
        val policy = ContextPolicy.decide(wifiFullBattery.copy(powerSaveMode = true))

        assertTrue(policy.saveBattery)
    }

    @Test
    fun `nivel de bateria desconocido no activa el ahorro`() {
        val policy = ContextPolicy.decide(wifiFullBattery.copy(batteryPercent = null))

        assertFalse(policy.saveBattery)
    }
}
