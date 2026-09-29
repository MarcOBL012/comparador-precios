package pe.com.comparadorprecios.capture

/** Qué tan firme está el teléfono ahora mismo, según la varianza reciente del acelerómetro. */
enum class Steadiness { STEADY, SHAKING }

/** Nivel de luz ambiental medido por el sensor de luz (lux). */
enum class Lighting { BRIGHT, LOW }

/** Lo que perciben los dos sensores de entrada en este instante. */
data class CaptureConditions(
    val steadiness: Steadiness,
    val lighting: Lighting,
)

/** Qué debe hacer el asistente de captura dado el par (firmeza, luz). */
enum class CaptureAction { WAIT_STEADY, READY_DARK, READY_BRIGHT }

/**
 * La etapa DECISIÓN del asistente de captura (Taller 2): combina firmeza + luz para decidir si
 * conviene prender el flash y si ya se puede avisar que está listo para disparar.
 *
 * Las dos entradas participan juntas, no por separado: con poca luz, si el teléfono tiembla NO se
 * prende el flash (saldría borroso igual, el flash no arregla el movimiento) — primero hay que
 * estar firme. Solo cuando ambas condiciones son favorables se avisa "listo".
 *
 * Sin dependencias de Android para poder probarla con JVM puro (`:app:testDebugUnitTest`).
 */
object CaptureAdvisor {
    fun decide(conditions: CaptureConditions): CaptureAction = when {
        conditions.steadiness == Steadiness.SHAKING -> CaptureAction.WAIT_STEADY
        conditions.lighting == Lighting.LOW -> CaptureAction.READY_DARK
        else -> CaptureAction.READY_BRIGHT
    }
}
