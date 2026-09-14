package pe.com.comparadorprecios.context

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Observa red, batería y ahorro de energía; emite un [DeviceContext] cada vez que algo cambia. */
class DeviceContextMonitor(context: Context) {

    private val appContext = context.applicationContext
    private val connectivity = appContext.getSystemService(ConnectivityManager::class.java)
    private val power = appContext.getSystemService(PowerManager::class.java)

    fun current(): DeviceContext {
        val battery = ContextCompat.registerReceiver(
            appContext,
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        return DeviceContext(
            network = networkTypeOf(connectivity.getNetworkCapabilities(connectivity.activeNetwork)),
            batteryPercent = battery?.let(::batteryPercentOf),
            charging = battery?.let(::isChargingOf) ?: false,
            powerSaveMode = power.isPowerSaveMode,
        )
    }

    fun observe(): Flow<DeviceContext> = callbackFlow {
        var snapshot = current()
        val lock = Any()
        // La red avisa desde un hilo propio y la batería desde el principal: se serializa la actualización.
        fun update(change: (DeviceContext) -> DeviceContext) = synchronized(lock) {
            snapshot = change(snapshot)
            trySend(snapshot)
        }
        trySend(snapshot)

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                update { it.copy(network = networkTypeOf(capabilities)) }
            }

            override fun onLost(network: Network) {
                update { it.copy(network = NetworkType.NONE) }
            }
        }
        connectivity.registerDefaultNetworkCallback(networkCallback)

        val batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                update {
                    when (intent.action) {
                        Intent.ACTION_BATTERY_CHANGED -> it.copy(
                            batteryPercent = batteryPercentOf(intent),
                            charging = isChargingOf(intent),
                        )
                        else -> it.copy(powerSaveMode = power.isPowerSaveMode)
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            appContext,
            batteryReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        awaitClose {
            connectivity.unregisterNetworkCallback(networkCallback)
            appContext.unregisterReceiver(batteryReceiver)
        }
    }.distinctUntilChanged()

    private fun networkTypeOf(capabilities: NetworkCapabilities?): NetworkType = when {
        capabilities == null || !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) -> NetworkType.NONE
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) -> NetworkType.UNMETERED
        else -> NetworkType.METERED
    }

    private fun batteryPercentOf(intent: Intent): Int? {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        return if (level >= 0 && scale > 0) level * 100 / scale else null
    }

    private fun isChargingOf(intent: Intent): Boolean {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }
}
