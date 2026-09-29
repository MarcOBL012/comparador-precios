package pe.com.comparadorprecios.capture

import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureAdvisorTest {

    @Test
    fun `firme y con buena luz queda listo sin flash`() {
        val action = CaptureAdvisor.decide(CaptureConditions(Steadiness.STEADY, Lighting.BRIGHT))

        assertEquals(CaptureAction.READY_BRIGHT, action)
    }

    @Test
    fun `firme y con poca luz queda listo con flash`() {
        val action = CaptureAdvisor.decide(CaptureConditions(Steadiness.STEADY, Lighting.LOW))

        assertEquals(CaptureAction.READY_DARK, action)
    }

    @Test
    fun `temblando con poca luz espera firmeza en vez de prender el flash`() {
        val action = CaptureAdvisor.decide(CaptureConditions(Steadiness.SHAKING, Lighting.LOW))

        assertEquals(CaptureAction.WAIT_STEADY, action)
    }

    @Test
    fun `temblando con buena luz tambien espera firmeza`() {
        val action = CaptureAdvisor.decide(CaptureConditions(Steadiness.SHAKING, Lighting.BRIGHT))

        assertEquals(CaptureAction.WAIT_STEADY, action)
    }
}
