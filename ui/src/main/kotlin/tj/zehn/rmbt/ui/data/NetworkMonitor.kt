package tj.zehn.rmbt.ui.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tj.zehn.rmbt.ui.model.NetworkKind

/** What the start screen shows about the current connection. */
internal data class NetworkState(
    val connected: Boolean,
    val kind: NetworkKind?,
    /**
     * Wi-Fi SSID (only when the host app holds the location permission) or mobile operator name;
     * null when unknown and for Ethernet.
     */
    val name: String?,
    /** Android signal level 0..4, or null when unknown. */
    val signalLevel: Int?,
) {
    /** RMBT network type code sent with the test (see RmbtConfig.networkType). */
    val rmbtNetworkType: Int
        get() = when (kind) {
            NetworkKind.WIFI -> 99
            NetworkKind.ETHERNET -> 106
            NetworkKind.CELLULAR -> 105
            NetworkKind.OTHER, null -> 98
        }

    companion object {
        val OFFLINE = NetworkState(connected = false, kind = null, name = null, signalLevel = null)
    }
}

/**
 * The default network and its signal level, read without runtime permissions: Wi-Fi level from the
 * network capabilities (Android 10+), mobile level from the telephony service (Android 9+).
 */
internal class NetworkMonitor(context: Context) {

    private val app = context.applicationContext
    private val connectivity = app.getSystemService(ConnectivityManager::class.java)
    private val telephony = app.getSystemService(TelephonyManager::class.java)

    fun states(): Flow<NetworkState> = callbackFlow {
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(current())
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                trySend(read(capabilities))
            }

            override fun onLost(network: Network) {
                trySend(current())
            }
        }
        trySend(current())
        connectivity.registerDefaultNetworkCallback(callback)
        // The mobile signal level has no callback without READ_PHONE_STATE; re-read it now and then.
        val poll = launch {
            while (isActive) {
                delay(SIGNAL_POLL_MS)
                trySend(current())
            }
        }
        awaitClose {
            poll.cancel()
            connectivity.unregisterNetworkCallback(callback)
        }
    }.distinctUntilChanged()

    fun current(): NetworkState {
        val capabilities = connectivity.activeNetwork?.let { connectivity.getNetworkCapabilities(it) }
        return capabilities?.let { read(it) } ?: NetworkState.OFFLINE
    }

    private fun read(capabilities: NetworkCapabilities): NetworkState {
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return NetworkState.OFFLINE
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ->
                NetworkState(true, NetworkKind.WIFI, wifiName(), wifiLevel(capabilities))
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ->
                NetworkState(true, NetworkKind.CELLULAR, operatorName(), mobileLevel())
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ->
                NetworkState(true, NetworkKind.ETHERNET, null, null)
            else -> NetworkState(true, NetworkKind.OTHER, null, null)
        }
    }

    @Suppress("DEPRECATION")
    private fun wifiLevel(capabilities: NetworkCapabilities): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rssi = capabilities.signalStrength
        if (rssi == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED) return null
        return WifiManager.calculateSignalLevel(rssi, SIGNAL_LEVELS)
    }

    private fun mobileLevel(): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching { telephony?.signalStrength?.level }.getOrNull()
    }

    /** The SSID is hidden ("<unknown ssid>") unless the app may read the location. */
    @Suppress("DEPRECATION")
    private fun wifiName(): String? {
        val granted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val ssid = runCatching { app.getSystemService(WifiManager::class.java)?.connectionInfo?.ssid }.getOrNull()
        return ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }
    }

    private fun operatorName(): String? = telephony?.networkOperatorName?.takeIf { it.isNotBlank() }

    private companion object {
        const val SIGNAL_POLL_MS = 5_000L
        const val SIGNAL_LEVELS = 5
        const val UNKNOWN_SSID = "<unknown ssid>"
    }
}
