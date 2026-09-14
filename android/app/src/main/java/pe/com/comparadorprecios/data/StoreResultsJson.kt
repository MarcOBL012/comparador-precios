package pe.com.comparadorprecios.data

import kotlinx.serialization.builtins.ListSerializer

/** Cómo se guardan los resultados por tienda en Room (historial, revisiones, lista). */
object StoreResultsJson {
    private val serializer = ListSerializer(StoreResult.serializer())

    fun encode(tiendas: List<StoreResult>): String = RetrofitProvider.json.encodeToString(serializer, tiendas)

    fun decode(json: String): List<StoreResult> = RetrofitProvider.json.decodeFromString(serializer, json)
}
