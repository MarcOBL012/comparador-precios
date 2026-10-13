package pe.com.comparadorprecios.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

data class LatLng(val lat: Double, val lng: Double)

/** Separado en interfaz para probar el ViewModel sin GPS. */
fun interface UserLocationProvider {
    /** null = sin permiso, ubicación apagada o no se obtuvo a tiempo. */
    suspend fun current(): LatLng?
}

/**
 * Ubicación con el LocationManager del sistema (vía androidx.core), sin Google Play Services:
 * una dependencia menos y para "tienda más cercana" sobra la precisión de red/GPS del sistema.
 */
class SystemLocationProvider(context: Context) : UserLocationProvider {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(LocationManager::class.java)

    fun hasPermission(): Boolean = hasLocationPermission(appContext)

    @SuppressLint("MissingPermission") // se verifica con hasPermission() justo antes
    override suspend fun current(): LatLng? {
        if (!hasPermission() || manager == null || !LocationManagerCompat.isLocationEnabled(manager)) return null
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { manager.isProviderEnabled(it) }
        // Una ubicación reciente (< 10 min) ya sirve para elegir tienda y evita esperar al GPS.
        val recent = providers
            .mapNotNull { manager.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time < 10 * 60 * 1000 }
            .minByOrNull { it.accuracy }
        if (recent != null) return LatLng(recent.latitude, recent.longitude)

        for (provider in providers) {
            val fresh = withTimeoutOrNull(8_000) { requestOnce(provider) }
            if (fresh != null) return LatLng(fresh.latitude, fresh.longitude)
        }
        return providers.firstNotNullOfOrNull { manager.getLastKnownLocation(it) }
            ?.let { LatLng(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestOnce(provider: String): Location? = suspendCancellableCoroutine { cont ->
        val cancel = CancellationSignal()
        cont.invokeOnCancellation { cancel.cancel() }
        LocationManagerCompat.getCurrentLocation(
            manager!!,
            provider,
            cancel,
            ContextCompat.getMainExecutor(appContext),
        ) { location -> if (cont.isActive) cont.resume(location) }
    }
}

fun hasLocationPermission(context: Context): Boolean =
    listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
