package com.example.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.SuccessGreen
import com.example.ui.theme.ZulvazaNavy
import com.example.ui.theme.ZulvazaOrange
import com.example.util.LocationHelper
import com.example.util.ReverseGeocodeResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun LocationGpsSection(
    latitude: Double?,
    longitude: Double?,
    onCoordinatesChanged: (Double, Double) -> Unit,
    onAddressGeocoded: (ReverseGeocodeResult) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var locating by remember { mutableStateOf(false) }

    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun fetchCurrentGps() {
        locating = true
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    onCoordinatesChanged(lat, lng)
                    scope.launch {
                        val geo = LocationHelper.reverseGeocode(context, lat, lng)
                        onAddressGeocoded(geo)
                        locating = false
                    }
                } else {
                    fusedClient.lastLocation.addOnSuccessListener { last ->
                        if (last != null) {
                            onCoordinatesChanged(last.latitude, last.longitude)
                            scope.launch {
                                val geo = LocationHelper.reverseGeocode(context, last.latitude, last.longitude)
                                onAddressGeocoded(geo)
                                locating = false
                            }
                        } else {
                            locating = false
                            onError("Could not acquire GPS fix. Enter your address details manually below.")
                        }
                    }.addOnFailureListener {
                        locating = false
                        onError("GPS unavailable. Enter your address details manually below.")
                    }
                }
            }
            .addOnFailureListener { e ->
                locating = false
                onError(e.message ?: "Unable to read device GPS")
            }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            fetchCurrentGps()
        } else {
            onError("Location permission denied. You can enter your address manually below.")
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Button(
            onClick = {
                val hasFine = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                val hasCoarse = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
                if (hasFine || hasCoarse) {
                    fetchCurrentGps()
                } else {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                }
            },
            enabled = !locating,
            colors = ButtonDefaults.buttonColors(
                containerColor = ZulvazaNavy,
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("use_current_location_button")
        ) {
            if (locating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = Color.White
                )
            } else {
                Icon(Icons.Default.MyLocation, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(if (locating) "Detecting GPS location..." else "Use My Current Location")
        }

        Spacer(modifier = Modifier.height(10.dp))

        val hasCoords = latitude != null && longitude != null
        Surface(
            color = if (hasCoords) SuccessGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(
                1.dp,
                if (hasCoords) SuccessGreen.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (hasCoords) Icons.Default.CheckCircle else Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = if (hasCoords) SuccessGreen else ZulvazaOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasCoords) "GPS Coordinates Attached" else "Native GPS Location (Optional)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (hasCoords) {
                            String.format(Locale.US, "Lat %.5f, Lng %.5f", latitude, longitude)
                        } else {
                            "Tap above to auto-fill address & GPS coordinates, or type address fields below."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
