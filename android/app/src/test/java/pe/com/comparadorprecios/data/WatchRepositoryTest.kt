package pe.com.comparadorprecios.data

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

private val WATCH = Watch(
    id = "w1",
    producto = WatchProduct("p1", "Free Tea", "Frutos Rojos", "500ml", "bebidas", "té"),
    precioObjetivo = 2.5,
    estado = "activa",
    creadoEn = "2026-11-03T00:00:00Z",
    actualizadoEn = "2026-11-03T00:00:00Z",
)

/** API que responde con lo que la prueba le indique, incluidos los errores HTTP reales de Retrofit. */
private class ScriptedApi(var failure: Throwable? = null) : WatchApi {
    var lastUpdateBody: JsonObject? = null
    private fun <T> respond(value: T): T = failure?.let { throw it } ?: value
    override suspend fun create(body: CreateWatchRequest) = respond(WATCH)
    override suspend fun list(estado: String?, limit: Int, offset: Int) = respond(WatchPage(listOf(WATCH), 1, limit, offset))
    override suspend fun get(id: String) = respond(WATCH)
    override suspend fun update(id: String, body: JsonObject): Watch { lastUpdateBody = body; return respond(WATCH) }
    override suspend fun delete(id: String) = respond(Unit)
    override suspend fun check(id: String) = respond(CheckResponse(WATCH))
    override suspend fun history(id: String, days: Int) = respond(HistoryResponse(days))
    override suspend fun alerts(unread: Boolean, limit: Int, offset: Int) = respond(AlertPage())
    override suspend fun markAlert(id: Long, body: MarkAlertRequest) =
        respond(AlertView(id, AlertType("objetivo_alcanzado", "Objetivo alcanzado"), "m", "w1", "p", "t", 2.0, "2026-11-03", body.leida))
}

private fun http(code: Int, body: String = "{}", retryAfter: String? = null): HttpException {
    val raw = okhttp3.Response.Builder()
        .request(okhttp3.Request.Builder().url("https://example.test/").build())
        .protocol(okhttp3.Protocol.HTTP_1_1)
        .code(code)
        .message("x")
        .apply { retryAfter?.let { header("Retry-After", it) } }
        .build()
    return HttpException(Response.error<Any>(body.toResponseBody("application/json".toMediaType()), raw))
}

class WatchRepositoryTest {

    private val api = ScriptedApi()
    private val repo = WatchRepository(api)

    private inline fun <reified E : ApiError> failsWith(failure: Throwable, noinline block: suspend (WatchRepository) -> Unit): E {
        api.failure = failure
        var caught: E? = null
        kotlinx.coroutines.runBlocking {
            try {
                block(repo)
                fail("debía lanzar ${E::class.simpleName}")
            } catch (e: ApiError) {
                assertTrue("era ${e::class.simpleName}: ${e.message}", e is E)
                caught = e as E
            }
        }
        return caught!!
    }

    @Test
    fun `una respuesta correcta devuelve el recurso`() = runTest {
        assertEquals("w1", repo.create(ProductPayload("Free Tea", "Frutos Rojos", "500ml", "bebidas", "té"), 2.5).id)
        assertEquals(1, repo.list().total)
        assertEquals(30, repo.history("w1").dias)
    }

    @Test
    fun `sin conexion es un error de red con mensaje para la persona`() {
        val e = failsWith<ApiError.Network>(IOException("timeout")) { it.check("w1") }

        assertTrue(e.message!!.contains("conexión"))
    }

    @Test
    fun `401 es sesion expirada`() {
        failsWith<ApiError.Unauthorized>(http(401, """{"error":"No autenticado.","codigo":"no_autenticado"}""")) { it.list() }
    }

    @Test
    fun `404 es no encontrado y usa el mensaje del servidor`() {
        val e = failsWith<ApiError.NotFound>(http(404, """{"error":"No encontramos ese seguimiento.","codigo":"no_encontrado"}""")) { it.get("x") }

        assertEquals("No encontramos ese seguimiento.", e.message)
    }

    @Test
    fun `409 conserva el codigo para decidir que hacer`() {
        val e = failsWith<ApiError.Conflict>(http(409, """{"error":"Ya sigues este producto.","codigo":"ya_seguido"}""")) {
            it.create(ProductPayload("", "Leche", "", "abarrotes", ""), null)
        }

        assertEquals("ya_seguido", e.codigo)
    }

    @Test
    fun `400 y 422 traen el detalle por campo`() {
        val body = """{"error":"Datos inválidos","codigo":"validacion","detalles":[{"campo":"precioObjetivo","mensaje":"Debe ser mayor a 0"}]}"""

        val e = failsWith<ApiError.Validation>(http(400, body)) { it.setTarget("w1", -1.0) }
        assertEquals("precioObjetivo", e.detalles.single().campo)
        failsWith<ApiError.Validation>(http(422, body)) { it.setStatus("w1", "activa", 1.0) }
    }

    @Test
    fun `429 lee Retry-After y usa 60 s si no viene`() {
        assertEquals(840, failsWith<ApiError.RateLimited>(http(429, """{"error":"Espera"}""", retryAfter = "840")) { it.check("w1") }.retryAfterSeconds)
        assertEquals(60, failsWith<ApiError.RateLimited>(http(429)) { it.check("w1") }.retryAfterSeconds)
    }

    @Test
    fun `502 es un servicio externo caido y 500 un error del servidor`() {
        failsWith<ApiError.Upstream>(http(502, """{"error":"No pudimos consultar las tiendas","codigo":"servicio_externo"}""")) { it.check("w1") }
        val e = failsWith<ApiError.Server>(http(500)) { it.list() }
        assertTrue(e.message!!.contains("500"))
    }

    @Test
    fun `una respuesta con forma inesperada no revienta la app`() {
        failsWith<ApiError.BadResponse>(SerializationException("campo faltante")) { it.list() }
    }

    @Test
    fun `quitar el objetivo envia null explicito y fijarlo envia el numero`() = runTest {
        repo.setTarget("w1", null)
        assertEquals(JsonObject(mapOf("precioObjetivo" to JsonNull)), api.lastUpdateBody)

        repo.setTarget("w1", 2.0)
        assertEquals(JsonObject(mapOf("precioObjetivo" to JsonPrimitive(2.0))), api.lastUpdateBody)
    }

    @Test
    fun `pausar solo envia el estado`() = runTest {
        repo.setStatus("w1", "pausada")

        assertEquals(JsonObject(mapOf("estado" to JsonPrimitive("pausada"))), api.lastUpdateBody)
    }
}
