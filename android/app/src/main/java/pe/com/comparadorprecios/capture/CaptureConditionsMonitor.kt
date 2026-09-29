package pe.com.comparadorprecios.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.sqrt

/**
 * La etapa PROCESAMIENTO del asistente de captura: lee acelerómetro y sensor de luz en tiempo
 * real y los fusiona en [CaptureConditions].
 *
 * La firmeza NO se mide con el valor instantáneo del acelerómetro (siempre trae ~9.8 m/s² de la
 * gravedad aunque el teléfono esté perfectamente quieto): se mide la varianza de la magnitud en
 * una ventana móvil corta, que sí sube cuando la mano tiembla o el teléfono se mueve.
 */
class CaptureConditionsMonitor(context: Context) {
    private val sensorManager = context.applicationContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    /** Sin ambos sensores no hay decisión conjunta posible; la UI debe evitar mostrar el asistente. */
    val hasBothSensors: Boolean get() = accelerometer != null && lightSensor != null

    fun observe(): Flow<CaptureConditions> = callbackFlow {
        val window = ArrayDeque<Float>()
        var lighting = Lighting.BRIGHT

        fun steadinessFromWindow(): Steadiness {
            if (window.size < WINDOW_SIZE) return Steadiness.STEADY
            val mean = window.average()
            val variance = window.sumOf { (it - mean) * (it - mean) } / window.size
            return if (variance > SHAKE_VARIANCE_THRESHOLD) Steadiness.SHAKING else Steadiness.STEADY
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        val (x, y, z) = event.values
                        if (window.size == WINDOW_SIZE) window.removeFirst()
                        window.addLast(sqrt(x * x + y * y + z * z))
                        trySend(CaptureConditions(steadinessFromWindow(), lighting))
                    }
                    Sensor.TYPE_LIGHT -> {
                        lighting = if (event.values[0] < LOW_LUX_THRESHOLD) Lighting.LOW else Lighting.BRIGHT
                        trySend(CaptureConditions(steadinessFromWindow(), lighting))
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) = Unit
        }

        accelerometer?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }
        lightSensor?.let { sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI) }

        awaitClose { sensorManager.unregisterListener(listener) }
    }.distinctUntilChanged()

    companion object {
        // ~12 muestras a SENSOR_DELAY_UI (60 ms) ≈ 0.7 s de historial: suficiente para distinguir
        // temblor de mano de un pulso momentáneo, sin sentirse lento en la demo.
        private const val WINDOW_SIZE = 12
        private const val SHAKE_VARIANCE_THRESHOLD = 1.2f
        // Lux de un cuarto con poca luz/atardecer; interior bien iluminado ronda 100-300 lux.
        private const val LOW_LUX_THRESHOLD = 30f
    }
}
