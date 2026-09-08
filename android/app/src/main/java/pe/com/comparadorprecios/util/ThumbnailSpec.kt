package pe.com.comparadorprecios.util

/** Matemática de la miniatura del historial (spec: lado largo 256px, máx 200 KB). */
object ThumbnailSpec {
    const val LONG_SIDE_PX = 256
    const val MAX_BYTES = 200_000
    val Qualities = intArrayOf(85, 70, 55, 40)

    /** Devuelve (ancho, alto) destino preservando el aspecto. */
    fun compute(width: Int, height: Int): Pair<Int, Int> {
        val longest = maxOf(width, height)
        if (longest <= LONG_SIDE_PX) return width to height
        val ratio = LONG_SIDE_PX.toFloat() / longest
        return (width * ratio).toInt() to (height * ratio).toInt()
    }
}
