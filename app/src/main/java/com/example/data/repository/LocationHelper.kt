package com.example.data.repository

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

data class ApproximateLocation(
    val areaLabel: String,
    val latitude: Double,
    val longitude: Double,
    val permissionGranted: Boolean
)

class LocationHelper(private val context: Context) {

    fun hasCoarseLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun fetchApproximateLocation(): ApproximateLocation {
        if (!hasCoarseLocationPermission()) {
            return ApproximateLocation(
                areaLabel = "Indiranagar Metro Zone, Bengaluru",
                latitude = 12.978,
                longitude = 77.641,
                permissionGranted = false
            )
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val rawLocation = suspendCancellableCoroutine { cont ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, null)
                .addOnSuccessListener { location -> cont.resume(location) }
                .addOnFailureListener { cont.resume(null) }
        }
        return if (rawLocation != null) {
            // Privacy snap: round to 3 decimal places (~110m grid) so exact address is never stored or exposed.
            val snappedLat = round(rawLocation.latitude * 1000.0) / 1000.0
            val snappedLng = round(rawLocation.longitude * 1000.0) / 1000.0
            ApproximateLocation(
                areaLabel = "Approx. Grid (${snappedLat}, ${snappedLng})",
                latitude = snappedLat,
                longitude = snappedLng,
                permissionGranted = true
            )
        } else {
            ApproximateLocation(
                areaLabel = "Indiranagar Metro Zone, Bengaluru",
                latitude = 12.978,
                longitude = 77.641,
                permissionGranted = true
            )
        }
    }

    companion object {
        fun approximateDistanceMeters(
            lat1: Double,
            lng1: Double,
            lat2: Double,
            lng2: Double
        ): Int {
            val r = 6371000.0
            val dLat = Math.toRadians(lat2 - lat1)
            val dLng = Math.toRadians(lng2 - lng1)
            val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLng / 2) * sin(dLng / 2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val rawMeters = (r * c).toInt()
            // Snap to nearest 50m band to avoid revealing exact user coordinates
            return ((rawMeters + 25) / 50 * 50).coerceAtLeast(100)
        }
    }
}
