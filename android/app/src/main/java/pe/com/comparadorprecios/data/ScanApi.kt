package pe.com.comparadorprecios.data

import com.clerk.api.Clerk
import com.clerk.api.network.serialization.ClerkResult
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Único endpoint del backend (Planes 1+2). */
interface ScanApi {
    @POST("api/scan")
    suspend fun scan(@Body request: ScanRequest): ScanResponse
}

object RetrofitProvider {
    val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    fun create(baseUrl: String, debug: Boolean = false): ScanApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BASIC
            else HttpLoggingInterceptor.Level.NONE
        }
        val auth = Interceptor { chain ->
            // Clerk 1.1.5 no expone Session.getToken(): String? ni Clerk.session?.getToken().
            // La API real (verificada contra el .aar en el cache de Gradle vía javap) es
            // Auth.getToken(options): ClerkResult<String, ClerkErrorResponse>, expuesta como
            // Clerk.auth.getToken() — hay que desenvolver el ClerkResult.
            //
            // Verificado por decompilación (javap) del bytecode real de Auth.getToken():
            // cuando NO hay sesión (Clerk.getSession() == null), el método retorna
            // directamente ClerkResult.Companion.apiFailure(
            //   ClerkErrorResponse(errors = emptyList(), meta = null, clerkTraceId = "no-session")
            // ) — es decir, "no hay sesión" es un ClerkResult.Failure, NO un Success(null).
            // Se confirmó además que el literal "no-session" solo aparece en ese único call
            // site dentro de Auth.class (grep sobre todas las clases del .aar), así que
            // `error?.clerkTraceId == "no-session"` es un discriminador fiable y único para
            // ese caso concreto, distinto de cualquier otro Failure real (fallo de red/API
            // con sesión activa, donde clerkTraceId será el trace id real del servidor o
            // el `error` vendrá null si es un fallo de red sin respuesta parseada).
            //
            // - "no hay sesión" → token = null, la request sale sin Authorization y el
            //   backend la 401-ea si de verdad hace falta, disparando el flujo real de
            //   sign-out (ver ScanUiState.Unauthorized / ScanViewModel.signOutAfterUnauthorized).
            // - cualquier OTRO Failure (sí hay sesión, pero falló su refresh/fetch) → se
            //   lanza IOException, que ScanRepository mapea a ScanError.Network (su
            //   catch (e: IOException) ya existente) en vez de enviar la request sin
            //   Authorization y arriesgar un signOut real por un simple hipo de red.
            val token = runBlocking {
                when (val result = Clerk.auth.getToken()) {
                    is ClerkResult.Success -> result.value
                    is ClerkResult.Failure -> {
                        if (result.error?.clerkTraceId == "no-session") {
                            null
                        } else {
                            throw IOException(
                                "No se pudo obtener el token de sesión.", result.throwable
                            )
                        }
                    }
                }
            }
            val request = if (token != null) {
                chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
            } else {
                chain.request()
            }
            chain.proceed(request)
        }
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            // El backend tiene maxDuration = 60s (IA + 2 scrapers en paralelo).
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(auth)
            .addInterceptor(logging)
            .build()
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ScanApi::class.java)
    }
}
