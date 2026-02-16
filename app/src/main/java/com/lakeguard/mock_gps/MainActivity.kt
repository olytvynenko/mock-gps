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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

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

                        OutlinedTextField(
                            value = latText,
                            onValueChange = { latText = it },
                            label = { Text("Latitude") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))

                        OutlinedTextField(
                            value = lonText,
                            onValueChange = { lonText = it },
                            label = { Text("Longitude") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))

                        Button(
                            onClick = {
                                val lat = latText.toDoubleOrNull()
                                val lon = lonText.toDoubleOrNull()

                                status = when {
                                    lat == null || lon == null -> "Invalid lat/lon."
                                    lat !in -90.0..90.0 || lon !in -180.0..180.0 ->
                                        "Out of range: lat [-90..90], lon [-180..180]."
                                    else -> {
                                        try {
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
