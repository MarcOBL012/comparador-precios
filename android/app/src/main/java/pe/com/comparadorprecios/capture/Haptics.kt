package pe.com.comparadorprecios.capture

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** La ACCIÓN FÍSICA del asistente de captura: un pulso corto que avisa "listo para disparar". */
object Haptics {
    private const val READY_PULSE_MS = 40L

    fun vibrateReady(context: Context) {
        vibratorOf(context).vibrate(VibrationEffect.createOneShot(READY_PULSE_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private fun vibratorOf(context: Context): Vibrator =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
}
