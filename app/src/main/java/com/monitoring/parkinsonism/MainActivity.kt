/* While this template provides a good starting point for using Wear Compose, you can always
 * take a look at https://github.com/android/wear-os-samples/tree/main/ComposeStarter and
 * https://github.com/android/wear-os-samples/tree/main/ComposeAdvanced to find the most up to date
 * changes to the libraries and their usages.
 */

package com.monitoring.parkinsonism

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.app.ActivityCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.tooling.preview.devices.WearDevices
import com.monitoring.parkinsonism.MonitoringSensorListener.Companion.LOCATION_PERMISSION_REQUEST_CODE
import com.monitoring.parkinsonism.R
import com.monitoring.parkinsonism.presentation.theme.MovementAndSleepMonitoringTheme
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager

class MainActivity : ComponentActivity() {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var heartRateSensor: Sensor? = null
    private var stepCounterSensor: Sensor? = null
    val permissionsToRequest = mutableListOf<String>()
    private lateinit var locationManager: LocationManager // GPS
    private var locationListener: LocationListener? = null // GPS Listener
    private lateinit var sensorEventListener: MonitoringSensorListener

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setTheme(android.R.style.Theme_DeviceDefault)

        //ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.))

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BODY_SENSORS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(android.Manifest.permission.BODY_SENSORS)
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), LOCATION_PERMISSION_REQUEST_CODE)
        }

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        heartRateSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)
        stepCounterSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager // GPS inicializácia

        // Inicializácia nášho listenera
        sensorEventListener = MonitoringSensorListener(this)
        locationListener = LocationListener { location ->
            // Spracovanie GPS údajov
            val latitude = location.latitude
            val longitude = location.longitude
            println("Aktuálna poloha: Lat: $latitude, Long: $longitude") // Tu môžeš použiť údaje podľa potreby
        }

        // Registrácia senzorov
        accelerometer?.also {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        /*
        heartRateSensor?.also {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        stepCounterSensor?.also {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_FASTEST)
        }*/

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10f, locationListener!!)
        }

        setContent {
            WearApp("Android")
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.also {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
        heartRateSensor?.also {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_FASTEST)
        }
        stepCounterSensor?.also {
            sensorManager.registerListener(sensorEventListener, it, SensorManager.SENSOR_DELAY_FASTEST)
        }

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 5000, 10f, locationListener!!)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(sensorEventListener)
        locationListener?.let { locationManager.removeUpdates(it) } // Odstránenie GPS listenera
    }
}


@Composable
fun WearApp(greetingName: String) {
    MovementAndSleepMonitoringTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentAlignment = Alignment.Center
        ) {
            TimeText()
            Greeting(greetingName = greetingName)
        }
    }
}

@Composable
fun Greeting(greetingName: String) {
    Text(
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        color = MaterialTheme.colors.primary,
        text = stringResource(R.string.hello_world, greetingName)
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun DefaultPreview() {
    WearApp("Preview Android")
}
