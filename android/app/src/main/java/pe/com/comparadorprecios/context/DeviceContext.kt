package pe.com.comparadorprecios.context

enum class NetworkType { NONE, UNMETERED, METERED }

/** Lo que el celular sabe de su entorno en este momento. */
data class DeviceContext(
    val network: NetworkType,
    val batteryPercent: Int?,
    val charging: Boolean,
    val powerSaveMode: Boolean,
)

/** Cómo debe comportarse el escaneo dado el contexto. */
data class ScanPolicy(
    val canScan: Boolean,
    val maxImageSidePx: Int,
    val saveBattery: Boolean,
    val notices: List<String>,
)

/**
 * La etapa DECISIÓN del pipeline aplicada al entorno del dispositivo.
 * Sin dependencias de Android para poder probar cada regla por separado.
 */
object ContextPolicy {
    const val FULL_IMAGE_SIDE_PX = 1600
    // Gemini sigue leyendo marca y nombre del empaque a 1024 px; la foto pesa ~60% menos.
    const val REDUCED_IMAGE_SIDE_PX = 1024
    const val LOW_BATTERY_PERCENT = 20

    const val NOTICE_OFFLINE =
        "Sin conexión: conéctate a internet para escanear. Tu historial y tu lista siguen disponibles."
    const val NOTICE_METERED =
        "Datos móviles: la foto se envía en menor resolución para gastar menos datos."
    const val NOTICE_LOW_BATTERY =
        "Batería baja: se busca solo en las tiendas principales para responder más rápido."

    fun decide(context: DeviceContext): ScanPolicy {
        val lowBattery = context.powerSaveMode ||
            (!context.charging && context.batteryPercent != null && context.batteryPercent <= LOW_BATTERY_PERCENT)
        val offline = context.network == NetworkType.NONE
        val metered = context.network == NetworkType.METERED

        val notices = buildList {
            if (offline) add(NOTICE_OFFLINE)
            if (!offline && metered) add(NOTICE_METERED)
            if (!offline && lowBattery) add(NOTICE_LOW_BATTERY)
        }
        return ScanPolicy(
            canScan = !offline,
            maxImageSidePx = if (metered || lowBattery) REDUCED_IMAGE_SIDE_PX else FULL_IMAGE_SIDE_PX,
            saveBattery = lowBattery,
            notices = notices,
        )
    }
}
