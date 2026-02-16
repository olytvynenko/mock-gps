package com.lakeguard.mock_gps

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val requestFineLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignore */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {

                    var latText by remember { mutableStateOf("48.3794") }
                    var lonText by remember { mutableStateOf("31.1656") }
                    var status by remember {
                        mutableStateOf("Setup: Developer options → Select mock location app → this app.")
                    }
                    var mockingEnabled by remember { mutableStateOf(true) }
                    var selectedLatLng by remember { mutableStateOf(LatLng(48.3794, 31.1656)) }
                    val cameraPositionState = rememberCameraPositionState {
                        position = CameraPosition.fromLatLngZoom(selectedLatLng, 6f)
                    }

                    LaunchedEffect(selectedLatLng) {
                        cameraPositionState.animate(CameraUpdateFactory.newLatLng(selectedLatLng))
                    }

                    val fineGranted = remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(
                                this,
                                Manifest.permission.ACCESS_FINE_LOCATION
                            ) == PackageManager.PERMISSION_GRANTED
                        )
                    }

                    Column(Modifier.padding(16.dp)) {
                        Text("Fake GPS (Mock Location)", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(12.dp))

                        if (!fineGranted.value) {
                            Text(
                                "Location permission not granted. Some emulator images require it.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    requestFineLocation.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    fineGranted.value =
                                        ContextCompat.checkSelfPermission(
                                            this@MainActivity,
                                            Manifest.permission.ACCESS_FINE_LOCATION
                                        ) == PackageManager.PERMISSION_GRANTED
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant location permission")
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        Text("Pick on map", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        GoogleMap(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(240.dp),
                            cameraPositionState = cameraPositionState,
                            onMapClick = { clicked ->
                                if (!mockingEnabled) {
                                    status = "Mocking is disabled. Enable it to pick a map location."
                                    return@GoogleMap
                                }
                                selectedLatLng = clicked
                                latText = formatCoordinate(clicked.latitude)
                                lonText = formatCoordinate(clicked.longitude)
                                status = "Picked from map: ${latText}, ${lonText}"
                            }
                        ) {
                            Marker(
                                state = MarkerState(position = selectedLatLng),
                                title = "Mock location"
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            enabled = mockingEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = lonText,
                            onValueChange = { lonText = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            enabled = mockingEnabled,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Mock location enabled")
                            Switch(
                                checked = mockingEnabled,
                                onCheckedChange = { enabled ->
                                    mockingEnabled = enabled
                                    status = if (enabled) {
                                        "Mocking enabled. Tap \"Set mock location\" to inject coordinates."
                                    } else {
                                        disableMockLocation(lm)
                                        "Mocking disabled. Test providers removed."
                                    }
                                }
                            )
                        }
                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (!mockingEnabled) {
                                    status = "Mocking is disabled. Enable it to inject location."
                                    return@Button
                                }

                                val lat = latText.toDoubleOrNull()
                                val lon = lonText.toDoubleOrNull()

                                status = when {
                                    lat == null || lon == null -> "Invalid lat/lon."
                                    lat !in -90.0..90.0 || lon !in -180.0..180.0 ->
                                        "Out of range: lat [-90..90], lon [-180..180]."
                                    else -> {
                                        try {
                                            selectedLatLng = LatLng(lat, lon)
                                            setMockLocation(lm, lat, lon)
                                            "Mock set: $lat, $lon"
                                        } catch (e: SecurityException) {
                                            Log.e("FakeGPS", "SecurityException", e)
                                            "SecurityException: ensure this app is selected in Developer options → Mock location app."
                                        } catch (e: IllegalArgumentException) {
                                            Log.e("FakeGPS", "IllegalArgumentException", e)
                                            "IllegalArgumentException: ${e.message}"
                                        } catch (t: Throwable) {
                                            Log.e("FakeGPS", "Crash", t)
                                            "Failed: ${t.javaClass.simpleName}: ${t.message}"
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Set mock location") }

                        Spacer(Modifier.height(12.dp))
                        Text(status, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun setMockLocation(lm: LocationManager, lat: Double, lon: Double) {
    // Start with GPS only; NETWORK can be added after GPS works.
    injectProvider(lm, LocationManager.GPS_PROVIDER, lat, lon)
    // If you want, uncomment after GPS works reliably:
    // injectProvider(lm, LocationManager.NETWORK_PROVIDER, lat, lon)
}

private fun disableMockLocation(lm: LocationManager) {
    clearTestProvider(lm, LocationManager.GPS_PROVIDER)
    clearTestProvider(lm, LocationManager.NETWORK_PROVIDER)
}

private fun clearTestProvider(lm: LocationManager, provider: String) {
    runCatching { lm.setTestProviderEnabled(provider, false) }
    runCatching { lm.removeTestProvider(provider) }
}

private fun injectProvider(lm: LocationManager, provider: String, lat: Double, lon: Double) {
    ensureFreshTestProvider(lm, provider)

    val loc = Location(provider).apply {
        latitude = lat
        longitude = lon
        accuracy = 3f
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    lm.setTestProviderLocation(provider, loc)
}

@Suppress("DEPRECATION", "WrongConstant")
private fun ensureFreshTestProvider(lm: LocationManager, provider: String) {
    // Key change: remove then add. This avoids the "provider exists but isn't test provider" crash.
    runCatching { lm.removeTestProvider(provider) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        lm.addTestProvider(provider, buildProviderPropsApi31())
    } else {
        lm.addTestProvider(
            provider,
            false, false, false, false,
            true, true, true,
            LEGACY_POWER_USAGE_LOW,
            LEGACY_ACCURACY_FINE
        )
    }

    lm.setTestProviderEnabled(provider, true)
}

@RequiresApi(Build.VERSION_CODES.S)
private fun buildProviderPropsApi31(): android.location.provider.ProviderProperties {
    return android.location.provider.ProviderProperties.Builder()
        .setPowerUsage(android.location.provider.ProviderProperties.POWER_USAGE_LOW)
        .setAccuracy(android.location.provider.ProviderProperties.ACCURACY_FINE)
        .setHasAltitudeSupport(true)
        .setHasSpeedSupport(true)
        .setHasBearingSupport(true)
        .build()
}

// Legacy constants to avoid referencing ProviderProperties on minSdk < 31
private const val LEGACY_POWER_USAGE_LOW = 1
private const val LEGACY_ACCURACY_FINE = 1

private fun formatCoordinate(value: Double): String {
    return String.format(Locale.US, "%.6f", value)
}
