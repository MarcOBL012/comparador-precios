package pe.com.comparadorprecios.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.HttpException
import java.io.IOException

/** Errores de la API de seguimientos, tipados para que la pantalla decida qué mostrar sin leer texto. */
sealed class ApiError(message: String, cause: Throwable? = null) : RuntimeException(message, cause) {
    /** Sin conexión, timeout o la respuesta no llegó. */
    class Network(message: String, cause: Throwable? = null) : ApiError(message, cause)
    class Unauthorized(message: String) : ApiError(message)
    class NotFound(message: String) : ApiError(message)
    /** 409. `codigo`: ya_seguido, limite_seguimientos, seguimiento_pausado, transicion_invalida. */
    class Conflict(message: String, val codigo: String) : ApiError(message)
    /** 400 (mal formada) y 422 (rompe una regla de negocio). */
    class Validation(message: String, val detalles: List<FieldIssue>) : ApiError(message)
    class RateLimited(message: String, val retryAfterSeconds: Int) : ApiError(message)
    /** 502: las tiendas no respondieron. */
    class Upstream(message: String) : ApiError(message)
    class Server(message: String) : ApiError(message)
    /** La respuesta llegó pero no tiene la forma esperada. */
    class BadResponse(message: String, cause: Throwable? = null) : ApiError(message, cause)
}

class WatchRepository(private val api: WatchApi) {

    suspend fun create(producto: ProductPayload, precioObjetivo: Double?): Watch =
        call { api.create(CreateWatchRequest(producto, precioObjetivo)) }

    suspend fun list(estado: String? = null, limit: Int = 50, offset: Int = 0): WatchPage =
        call { api.list(estado, limit, offset) }

    suspend fun get(id: String): Watch = call { api.get(id) }

    /** `null` quita el objetivo (solo avisará mínimos históricos); un número lo fija. */
    suspend fun setTarget(id: String, precioObjetivo: Double?): Watch =
        call { api.update(id, JsonObject(mapOf("precioObjetivo" to (precioObjetivo?.let(::JsonPrimitive) ?: JsonNull)))) }

    suspend fun setStatus(id: String, estado: String, precioObjetivo: Double? = null): Watch =
        call {
            val body = buildMap<String, kotlinx.serialization.json.JsonElement> {
                put("estado", JsonPrimitive(estado))
                if (precioObjetivo != null) put("precioObjetivo", JsonPrimitive(precioObjetivo))
            }
            api.update(id, JsonObject(body))
        }

    suspend fun delete(id: String) = call { api.delete(id) }

    suspend fun check(id: String): CheckResponse = call { api.check(id) }

    suspend fun history(id: String, days: Int = 30): HistoryResponse = call { api.history(id, days) }

    suspend fun alerts(unread: Boolean = false, limit: Int = 50, offset: Int = 0): AlertPage =
        call { api.alerts(unread, limit, offset) }

    suspend fun markAlert(id: Long, read: Boolean): AlertView = call { api.markAlert(id, MarkAlertRequest(read)) }

    private suspend fun <T> call(block: suspend () -> T): T {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            throw mapHttp(e)
        } catch (e: IOException) {
            throw ApiError.Network("Sin conexión o el servidor tardó demasiado. Revisa tu internet y reintenta.", e)
        } catch (e: SerializationException) {
            throw ApiError.BadResponse("El servidor respondió algo inesperado. Reintenta.", e)
        }
    }

    private fun mapHttp(e: HttpException): ApiError {
        val body = runCatching {
            e.response()?.errorBody()?.string()?.let { RetrofitProvider.json.decodeFromString<ApiErrorBody>(it) }
        }.getOrNull()
        val message = body?.error?.takeIf { it.isNotBlank() }
        return when (e.code()) {
            400, 422 -> ApiError.Validation(message ?: "Revisa los datos e inténtalo de nuevo.", body?.detalles.orEmpty())
            401 -> ApiError.Unauthorized(message ?: "Tu sesión expiró. Inicia sesión de nuevo.")
            404 -> ApiError.NotFound(message ?: "No encontramos eso. Quizá ya fue eliminado.")
            409 -> ApiError.Conflict(message ?: "Esa acción choca con el estado actual.", body?.codigo.orEmpty())
            429 -> ApiError.RateLimited(
                message ?: "Demasiadas consultas. Espera un momento.",
                e.response()?.headers()?.get("Retry-After")?.toIntOrNull() ?: 60,
            )
            502 -> ApiError.Upstream(message ?: "No pudimos consultar las tiendas ahora. Intenta en unos minutos.")
            else -> ApiError.Server("Error del servidor (${e.code()}). Reintenta en unos segundos.")
        }
    }
}
