package com.monitoring.parkinsonism

import android.content.Context
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.monitoring.parkinsonism.debug.DebugScreen
import com.monitoring.parkinsonism.presentation.theme.MovementAndSleepMonitoringTheme

class MainActivity : ComponentActivity() {


    // датчики теперь не нужны напрямую – их регистрацию выполняет MonitoringSensorListener
    private lateinit var sensorManager: SensorManager

    private lateinit var sensorEventListener: MonitoringSensorListener

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        sensorEventListener = MonitoringSensorListener(this)

        sensorEventListener.startAllSendingTasks()

        setContent {
            MovementAndSleepMonitoringTheme {
                DebugScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        // sensorManager.unregisterListener(sensorEventListener)
        // ETA ZALUPA LOMAET VSYU RABOTU V FONE, NO BLYA, TOGDA BUDET DATA LEAK

    }
}