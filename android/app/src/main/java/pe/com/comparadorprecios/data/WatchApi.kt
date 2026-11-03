package pe.com.comparadorprecios.data

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Los 9 endpoints de la API propia que consume la app (el décimo, /internal/check-due, es solo del cron). */
interface WatchApi {
    @POST("api/v1/watches")
    suspend fun create(@Body body: CreateWatchRequest): Watch

    @GET("api/v1/watches")
    suspend fun list(
        @Query("estado") estado: String?,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): WatchPage

    @GET("api/v1/watches/{id}")
    suspend fun get(@Path("id") id: String): Watch

    /** JsonObject (no una data class) para poder enviar `precioObjetivo: null` y así QUITAR el objetivo. */
    @PATCH("api/v1/watches/{id}")
    suspend fun update(@Path("id") id: String, @Body body: JsonObject): Watch

    @DELETE("api/v1/watches/{id}")
    suspend fun delete(@Path("id") id: String)

    @POST("api/v1/watches/{id}/check")
    suspend fun check(@Path("id") id: String): CheckResponse

    @GET("api/v1/watches/{id}/history")
    suspend fun history(@Path("id") id: String, @Query("days") days: Int): HistoryResponse

    @GET("api/v1/alerts")
    suspend fun alerts(
        @Query("unread") unread: Boolean,
        @Query("limit") limit: Int,
        @Query("offset") offset: Int,
    ): AlertPage

    @PATCH("api/v1/alerts/{id}")
    suspend fun markAlert(@Path("id") id: Long, @Body body: MarkAlertRequest): AlertView
}
