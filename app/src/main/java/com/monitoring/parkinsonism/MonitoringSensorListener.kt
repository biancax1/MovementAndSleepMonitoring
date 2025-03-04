package com.monitoring.parkinsonism

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.monitoring.parkinsonism.debug.DebugData
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*
import kotlin.math.*
class MonitoringSensorListener(private val context: Context) : SensorEventListener {


    //SensorManager
    private val sensorManager: SensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // fusedLocationClient for GPS
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    // DataClient for Wearable
    private val dataClient: DataClient = Wearable.getDataClient(context)

    private var isSleeping: Boolean = false
    private var sleepStartTime: Long = 0
    private var sleepEndTime: Long = 0
    private var totalSleepTime: Long = 0
    private var totalSleepTimeInADay: Long = 0
    private var dayInAYear: Int = LocalDate.now().dayOfYear
    private var offFreezingState: Boolean = false
    private var totalStepsInDay: Int = 0
    private var isStepCounterInitialized: Boolean = false
    private var initialStepCount: Float = 0f
    private var isActive: Boolean = false
    private var activeStartTime: Long = 0
    private var totalActiveTimeToday: Long = 0
    private val tremorBufferX = mutableListOf<Float>()
    private val tremorBufferY = mutableListOf<Float>()
    private val tremorBufferZ = mutableListOf<Float>()
    private val tremorBufferTime = mutableListOf<Long>()
    private val tremorWindowMs = 3000 // 3sec
    private var tremorIsOccuring: Long = 0

    private var lastGpsLocation: Sensor? = null  // CHECK LOCATION TYPE NIGGA
    private var lastGpsLocationObj: android.location.Location? = null
    private var lastGpsTimestamp: Long = 0
    private var SpeedGate: Long = 0

    private var heartRate: Float = 0f

    private var lastUpdateTime: Long = 0

    init {
        logAvailableSensors()
        registerSensorsAutomatically()
        DebugData.systemStatus = "Датчики зарегистрированы автоматически"
        startAllSendingTasks()
        updateCurrentTime()
    }

    private fun logAvailableSensors() {
        val sensorTypes = listOf(
            Sensor.TYPE_ACCELEROMETER,
            Sensor.TYPE_STEP_COUNTER,
            Sensor.TYPE_AMBIENT_TEMPERATURE,
            Sensor.TYPE_HEART_RATE
        )
        sensorTypes.forEach { type ->
            val sensorsList: List<Sensor> = sensorManager.getSensorList(type)
            DebugData.appendLog("Для типа $type найдено датчиков: ${sensorsList.size}")
            sensorsList.forEach { sensor ->
                val info = "Датчик: ${sensor.name} | Тип: ${sensor.type} | Производитель: ${sensor.vendor}"
                DebugData.appendLog(info)
                android.util.Log.d("SensorsList", info)
            }
        }
    }

    fun findSensorByName(sensorType: Int, partialName: String): Sensor? {
        val sensors: List<Sensor> = sensorManager.getSensorList(sensorType)
        val lowerPartial = partialName.lowercase(Locale.getDefault())
        val found = sensors.firstOrNull { sensor ->
            sensor.name.lowercase(Locale.getDefault()).contains(lowerPartial)
        }
        if (found != null) {
            DebugData.appendLog("Найден датчик: ${found.name} для типа $sensorType, поиск: \"$partialName\"")
        } else {
            DebugData.appendLog("Датчик с именем, содержащим \"$partialName\", для типа $sensorType не найден.")
        }
        return found
    }

    fun registerSensorsAutomatically() {

        val accelerometerSensor = findSensorByName(Sensor.TYPE_ACCELEROMETER, "accel")
            ?: findSensorByName(Sensor.TYPE_ACCELEROMETER, "accelerometer")
        if (accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_NORMAL)
        } else {
            Toast.makeText(context, "Акселерометр не найден", Toast.LENGTH_SHORT).show()
            DebugData.appendLog("Акселерометр не найден")
        }

        val stepSensor = findSensorByName(Sensor.TYPE_STEP_COUNTER, "step")
            ?: findSensorByName(Sensor.TYPE_STEP_COUNTER, "counter")
        if (stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Toast.makeText(context, "Датчик шагов не найден", Toast.LENGTH_SHORT).show()
            DebugData.appendLog("Датчик шагов не найден")
        }

        val tempSensor = findSensorByName(Sensor.TYPE_AMBIENT_TEMPERATURE, "temp")
            ?: findSensorByName(Sensor.TYPE_AMBIENT_TEMPERATURE, "ambient")
        if (tempSensor != null) {
            sensorManager.registerListener(this, tempSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Toast.makeText(context, "Температурный датчик не найден", Toast.LENGTH_SHORT).show()
            DebugData.appendLog("Температурный датчик не найден")
        }

        val heartRateSensor = findSensorByName(Sensor.TYPE_HEART_RATE, "heart")
            ?: findSensorByName(Sensor.TYPE_HEART_RATE, "hr")
        if (heartRateSensor != null) {
            sensorManager.registerListener(this, heartRateSensor, SensorManager.SENSOR_DELAY_FASTEST)
        } else {
            Toast.makeText(context, "Датчик ЧСС не найден", Toast.LENGTH_SHORT).show()
            DebugData.appendLog("Датчик ЧСС не найден")
        }
    }

    private fun updateCurrentTime() {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        DebugData.currentTime = sdf.format(Date())
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            if (it.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                handleStepCounterData(it)
            } else {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastUpdateTime >= 100) {
                    lastUpdateTime = currentTime
                    when (it.sensor.type) {
                        Sensor.TYPE_ACCELEROMETER -> {
                            handleAccelerometerData(it)
                            detectTremorWithFFT(it)
                        }
                        Sensor.TYPE_HEART_RATE -> handleHeartRateData(it)
                        Sensor.TYPE_AMBIENT_TEMPERATURE -> handleTemperatureData(it)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // too lazy for this shit
    }

    // Temp
    private var lastTemperature: Float? = null
    private fun handleTemperatureData(event: SensorEvent) {
        val currentTemperature = event.values[0]
        DebugData.appendLog("Температура: $currentTemperature")
        lastTemperature = currentTemperature
    }

    // Accel
    private fun handleAccelerometerData(event: SensorEvent) {
        val (x, y, z) = event.values
        DebugData.accelX = x
        DebugData.accelY = y
        DebugData.accelZ = z
        DebugData.appendLog("Акселерометр: x=$x, y=$y, z=$z")

        val accelerationMagnitude = sqrt(x*x + y*y + z*z.toDouble())
        // IF threshold == 0,  isMoving always true
        val motionThreshold = 2.0
        val isMoving = accelerationMagnitude > motionThreshold

        val currentDay = LocalDate.now().dayOfYear
        if (dayInAYear != currentDay) {
            dayInAYear = currentDay
            totalActiveTimeToday = 0L
        }
        if (isMoving) {
            if (!isActive) {
                isActive = true
                activeStartTime = System.currentTimeMillis()
                DebugData.appendLog("Пользователь начал движение")
            }
        } else {
            if (isActive) {
                val activeEndTime = System.currentTimeMillis()
                totalActiveTimeToday += activeEndTime - activeStartTime
                isActive = false
                DebugData.appendLog("Пользователь остановился")
            }
        }
    }

    // heartrate
    private fun handleHeartRateData(event: SensorEvent) {
        heartRate = event.values[0]
        DebugData.heartRate = heartRate
        DebugData.appendLog("ЧСС: $heartRate BPM")
        // Дополнительная логика обработки сна и off-freezing может быть добавлена здесь
    }

    // step
    private fun handleStepCounterData(event: SensorEvent) {
        val currentStepCount = event.values[0]
        val currentDay = LocalDate.now().dayOfYear
        if (dayInAYear != currentDay) {
            dayInAYear = currentDay
            initialStepCount = currentStepCount
            totalStepsInDay = 0
        }
        if (!isStepCounterInitialized) {
            initialStepCount = currentStepCount
            isStepCounterInitialized = true
        }
        totalStepsInDay = (currentStepCount - initialStepCount).toInt()
        DebugData.stepsCount = totalStepsInDay
        DebugData.appendLog("Шагов за день: $totalStepsInDay")
    }

    // speed based on gps
    fun updateWalkingSpeed() {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED) {
            DebugData.appendLog("Нет разрешения для ACCESS_FINE_LOCATION")
            return
        }
        fusedLocationClient.lastLocation.addOnSuccessListener { currentLocation ->
            if (currentLocation == null) {
                DebugData.appendLog("GPS: координаты не получены")
                return@addOnSuccessListener
            }
            if (lastGpsLocationObj != null && lastGpsTimestamp != 0L) {
                val timeDeltaMillis = currentLocation.time - lastGpsTimestamp
                if (timeDeltaMillis > 0) {
                    val distanceMeters = lastGpsLocationObj!!.distanceTo(currentLocation)
                    val timeDeltaSeconds = timeDeltaMillis / 1000.0
                    val speedMs = distanceMeters / timeDeltaSeconds
                    val speedKmH = speedMs * 3.6
                    SpeedGate = speedKmH.toLong()
                    DebugData.walkingSpeed = SpeedGate
                    DebugData.appendLog("Скорость: $speedKmH км/ч")
                }
            }
            lastGpsLocationObj = currentLocation
            lastGpsTimestamp = currentLocation.time
        }
    }

    // tremor shitty detection
    private fun detectTremorWithFFT(event: SensorEvent) {
        val (x, y, z) = event.values
        val currentTime = System.currentTimeMillis()
        tremorBufferX.add(x)
        tremorBufferY.add(y)
        tremorBufferZ.add(z)
        tremorBufferTime.add(currentTime)
        while (tremorBufferTime.isNotEmpty() && currentTime - tremorBufferTime.first() > tremorWindowMs) {
            tremorBufferX.removeAt(0)
            tremorBufferY.removeAt(0)
            tremorBufferZ.removeAt(0)
            tremorBufferTime.removeAt(0)
        }
        if (tremorBufferX.size >= 100) {
            val avgX = tremorBufferX.average()
            tremorIsOccuring = if (avgX > 5.0) 1 else 0
            DebugData.tremorDetected = (tremorIsOccuring == 1L)
            DebugData.appendLog("Тремор: " + if (DebugData.tremorDetected) "Обнаружен" else "Не обнаружен")
        }
    }

    // FFT
    fun fft(data: FloatArray): DoubleArray {
        val n = data.size
        val real = data.copyOf()
        val imag = FloatArray(n) { 0f }
        var m = 0
        while ((1 shl m) < n) m++
        for (i in 0 until n) {
            val j = Integer.reverse(i) ushr (32 - m)
            if (i < j) {
                val temp = real[i]
                real[i] = real[j]
                real[j] = temp
            }
        }
        var step = 1
        while (step < n) {
            val jump = step shl 1
            val delta = (-2.0 * Math.PI / jump).toFloat()
            for (i in 0 until step) {
                val wRe = cos(i * delta)
                val wIm = sin(i * delta)
                var k = i
                while (k < n) {
                    val l = k + step
                    val tRe = real[l] * wRe - imag[l] * wIm
                    val tIm = real[l] * wIm + imag[l] * wRe
                    real[l] = real[k] - tRe
                    imag[l] = imag[k] - tIm
                    real[k] += tRe
                    imag[k] += tIm
                    k += jump
                }
            }
            step = jump
        }
        return DoubleArray(n / 2) { i ->
            sqrt((real[i]*real[i] + imag[i]*imag[i]).toDouble())
        }
    }

    private fun abs(value: Float): Float {
        return if (value < 0) -value else value
    }

    // Send DATA
    private fun sendMessage(x: Float, y: Float, z: Float) {
        val dataMapRequest = PutDataMapRequest.create("/accelerometer_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putFloat("x", x)
        dataMap.putFloat("y", y)
        dataMap.putFloat("z", z)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener {
            Log.d("dataLayer", "Данные акселерометра отправлены успешно")
        }.addOnFailureListener {
            Log.d("dataLayer", "Отправка данных акселерометра не удалась")
        }
    }

    private fun sendStepsMessage(){
        val dataMapRequest = PutDataMapRequest.create("/steps_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putInt("totalStepsInDay", totalStepsInDay)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener {
            Log.d("dataLayer", "Шаги отправлены успешно")
        }.addOnFailureListener{
            Log.d("dataLayer", "Отправка шагов не удалась")
        }
    }

    private fun sendActivityMessage(){
        val dataMapRequest = PutDataMapRequest.create("/activity_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putBoolean("isActive", isActive)
        dataMap.putLong("timeStamp", timeStamp)
        dataMap.putLong("totalActiveTimeToday", totalActiveTimeToday)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener {
            Log.d("dataLayer", "Данные активности отправлены успешно")
        }.addOnFailureListener{
            Log.d("dataLayer", "Отправка данных активности не удалась")
        }
    }

    private fun sendAGaitMessage(){
        val dataMapRequest = PutDataMapRequest.create("/gait_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putLong("SpeedGate", SpeedGate)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener {
            Log.d("dataLayer", "Скорость отправлена успешно")
        }.addOnFailureListener {
            Log.d("dataLayer", "Отправка скорости не удалась")
        }
    }

    private fun sendATremorMessage(){
        val dataMapRequest = PutDataMapRequest.create("/tremor_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putLong("tremorIsOccuring", tremorIsOccuring)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener {
            Log.d("dataLayer", "Данные тремора отправлены успешно")
        }.addOnFailureListener {
            Log.d("dataLayer", "Отправка данных тремора не удалась")
        }
    }

    // Periodic tasks
    fun startAllSendingTasks() {
        val speedTimer = Timer()
        speedTimer.schedule(object : TimerTask() {
            override fun run() {
                updateCurrentTime()
                updateWalkingSpeed()
                // Если нужно, вызвать отправку данных
            }
        }, 0, 15000) // каждые 15 секунд

        val stepsTimer = Timer()
        stepsTimer.schedule(object : TimerTask() {
            override fun run() {
                sendStepsMessage()
            }
        }, 0, 1000) // каждую секунду
    }

    // KOSTYL''
    private fun isMoving(): Boolean {
        val accelTotal = sqrt(DebugData.accelX * DebugData.accelX +
                DebugData.accelY * DebugData.accelY +
                DebugData.accelZ * DebugData.accelZ)
        return accelTotal > 2.0
    }
}