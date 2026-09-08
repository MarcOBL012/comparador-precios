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
            val token = runBlocking {
                when (val result = Clerk.auth.getToken()) {
                    is ClerkResult.Success -> result.value
                    else -> null
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
