package tj.zehn.rmbt.ui.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import tj.zehn.rmbt.ui.model.GeoPoint
import kotlin.coroutines.resume

/**
 * The device location for a test, from the fused location provider of Google Play services.
 * Never asks for the permission itself: without a location permission, without Play services or
 * with location turned off it returns null and the test is stored without a point.
 */
internal class FusedLocationSource(context: Context) {

    private val app = context.applicationContext

    @SuppressLint("MissingPermission") // Checked with hasPermission() right before.
    suspend fun current(): GeoPoint? {
        val fine = app.hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        if (!fine && !app.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) return null
        if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(app) != ConnectionResult.SUCCESS) return null

        val request = CurrentLocationRequest.Builder()
            .setPriority(if (fine) Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            // A fix from the last minutes is still where the test runs and comes back at once.
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MS)
            .setDurationMillis(FIX_TIMEOUT_MS)
            .build()
        val cancel = CancellationTokenSource()
        val location = try {
            LocationServices.getFusedLocationProviderClient(app)
                .getCurrentLocation(request, cancel.token)
                .awaitOrNull(cancel)
        } catch (_: SecurityException) {
            null // The permission was revoked between the check and the call.
        }
        return location?.toGeoPoint()
    }

    private fun Location.toGeoPoint() = GeoPoint(
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = if (hasAccuracy()) accuracy else null,
    )

    private companion object {
        const val MAX_FIX_AGE_MS = 2 * 60_000L
        const val FIX_TIMEOUT_MS = 20_000L
    }
}

/**
 * The location permissions to ask for before a test: those the host app declares in its manifest,
 * or none when it already holds one of them (coarse is enough for the map point).
 */
internal fun locationPermissionsToRequest(context: Context): Array<String> {
    val location = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    if (location.any { context.hasPermission(it) }) return emptyArray()
    val declared = runCatching {
        val pm = context.packageManager
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        info.requestedPermissions
    }.getOrNull().orEmpty()
    return location.filter { it in declared }.toTypedArray()
}

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

/** The task's result, or null when it failed or was cancelled; cancelling the coroutine cancels the task. */
private suspend fun <T> Task<T>.awaitOrNull(cancel: CancellationTokenSource): T? =
    suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cancel.cancel() }
        addOnCompleteListener { task -> cont.resume(if (task.isSuccessful) task.result else null) }
    }
