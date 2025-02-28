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
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import java.time.LocalDate
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import java.time.ZoneId
import java.util.Timer
import java.util.TimerTask
import org.jtransforms.fft.DoubleFFT_1D
import kotlin.math.*

class MonitoringSensorListener(private val context: Context) : SensorEventListener {

   // SENZORY
    private val sensorManager: SensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepCounterSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
    private val temperatureSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
    private val accelerometerSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val heartRateSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE)

    // PREMENNÉ PRE MONITORING SPÁNKU
    private var heartRate = 0f
    private var isStationary = false
    private var isSleeping = false
    private var lastTemperature: Float? = null // Uchováva poslednú známu teplotu
    // *
    private var sleepStartTime: Long = 0
    // *
    private var sleepEndTime: Long = 0
    private var totalSleepTime: Long = 0
    // *
    private var totalSleepTimeInADay: Long = 0
    private var dayInAYear = LocalDate.now().dayOfYear

    // PREMENNE PRE MONITORING  OFF-FREEZINGU
    // *
    private var offFreezingState: Boolean = false;   //aktivita
    private val dataClient: DataClient = Wearable.getDataClient(context)
    private var lastUpdateTime: Long = 0
    // kroky

    // PREMENNE PRE MONITORING POCTU KROKOV
    // **
    private var totalStepsInDay: Int = 0
    private var isStepCounterInitialized = false
    private var initialStepCount = 0f

    // PREMENNE PRE MONITORING POCTU AKTIVITY
    // **
    private var isActive: Boolean = false
    private var activeStartTime: Long = 0

    // PREMENNE PRE MONITORING TREMORU
    private val tremorData = mutableListOf<Pair<Long, Double>>() // Ukladá čas a amplitúdy pohybu
    private val tremorBufferX = mutableListOf<Float>()
    private val tremorBufferY = mutableListOf<Float>()
    private val tremorBufferZ = mutableListOf<Float>()
    private val tremorBufferTime = mutableListOf<Long>()
    private val tremorWindowMs = 3000 // 3 sekundy

    // PREMENNE PRE MONITORING RYCHLOSTI CHODZE
    // *
    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    //*
    private var totalActiveTimeToday: Long = 0  // vypis v hh:mm:ss
    private var SpeedGate: Long = 0 // toto je ciselny graf
    private var tremorIsOccuring: Long = 0 // toto je "boolean" graf, ktory nie je boolean ale ciselny



    private var lastGpsLocation: Location? = null
    private var lastGpsTimestamp: Long = 0
    private var lastStepCount: Int = 0
    private var stepLength: Double = 0.75


    init {
        startAllSedingTasks()
        accelerometerSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            Toast.makeText(context, "Accelerometer Sensor not available", Toast.LENGTH_SHORT).show()
        }

        heartRateSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            Toast.makeText(context, "Heart Rate Sensor not available", Toast.LENGTH_SHORT).show()
        }
        stepCounterSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            Toast.makeText(context, "Step Counter Sensor not available", Toast.LENGTH_SHORT).show()
        }
        temperatureSensor?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        } ?: run {
            Toast.makeText(context, "Temperature Sensor not available", Toast.LENGTH_SHORT).show()
        }
    }

    // TATO FUNKCIA JE TU KVOLI GPS
    private fun checkLocationPermission() {
        if (ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }


    override fun onSensorChanged(event: SensorEvent?) {
        event?.let {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastUpdateTime >= 5000) {
                lastUpdateTime = currentTime
                when (it.sensor.type) {
                    Sensor.TYPE_ACCELEROMETER -> {
                        //detectTremorWithFFT(it)
                        handleAccelerometerData(it)
                        detectTremorWithFFT(it)
                    }
                    Sensor.TYPE_HEART_RATE -> handleHeartRateData(it)
                    Sensor.TYPE_STEP_COUNTER -> handleStepCounterData(it)
                    Sensor.TYPE_AMBIENT_TEMPERATURE -> handleTemperatureData(it)
                }
            }
        }
    }

    private fun handleTemperatureData(event: SensorEvent) {
        val currentTemperature = event.values[0]
        if (lastTemperature == null) {
            lastTemperature = currentTemperature
        } else {
            lastTemperature = currentTemperature
        }
    }

    fun startAllSedingTasks() {
        val StepsTimer = Timer()
        StepsTimer.schedule(object : TimerTask() {
            override fun run() {
                sendStepsMessage()
            }
        }, 0, 1000)

        val ActivityTimer = Timer()
        ActivityTimer.schedule(object : TimerTask() {
            override fun run() {
                sendActivityMessage()
            }
        }, 0, 1000)

        val GaitTimer = Timer()
        GaitTimer.schedule(object : TimerTask() {
            override fun run() {
                sendAGaitMessage()
            }
        }, 0, 5000)

        val TremorTimer = Timer()
        TremorTimer.schedule(object : TimerTask() {
            override fun run() {
                sendATremorMessage()
            }
        }, 0, 5000)

    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Voliteľná implementácia
    }

    private fun sendMessage(x:Float,y:Float,z:Float) {
        val dataMapRequest = PutDataMapRequest.create("/accelerometer_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putFloat("x", x)
        dataMap.putFloat("y", y)
        dataMap.putFloat("z", z)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener { Log.d("dataLayer", "Poslanie bolo uspesne") }.addOnFailureListener{Log.d("dataLayer", "Poslanie zluhalo")}
    }

    private fun sendStepsMessage(){
        val dataMapRequest = PutDataMapRequest.create("/steps_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putInt("totalStepsInDay", totalStepsInDay)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener { Log.d("dataLayer", "Poslanie bolo uspesne") }.addOnFailureListener{Log.d("dataLayer", "Poslanie zluhalo")}
    }

    private fun sendActivityMessage(){
        val dataMapRequest = PutDataMapRequest.create("/activity_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putBoolean("isActive", isActive)
        dataMap.putLong("timeStamp", timeStamp)
        dataMap.putLong("totalActiveTimeToday", totalActiveTimeToday)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener { Log.d("dataLayer", "Poslanie bolo uspesne") }.addOnFailureListener{Log.d("dataLayer", "Poslanie zluhalo")}
    }

    private fun sendASleepMessage(){
        val dataMapRequest = PutDataMapRequest.create("/sleep_data")
        val dataMap = dataMapRequest.dataMap
        dataMap.putLong("sleepStartTime", sleepStartTime)
        dataMap.putLong("sleepEndTime", sleepEndTime)
        dataMap.putLong("totalSleepTimeInADay", totalSleepTimeInADay)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener { Log.d("dataLayer", "Poslanie bolo uspesne") }.addOnFailureListener{Log.d("dataLayer", "Poslanie zluhalo")}
    }

    private fun sendAGaitMessage(){
        val dataMapRequest = PutDataMapRequest.create("/gait_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putLong("SpeedGate", SpeedGate)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener { Log.d("dataLayer", "Poslanie bolo uspesne") }.addOnFailureListener{Log.d("dataLayer", "Poslanie zluhalo")}
    }

    private fun sendATremorMessage(){
        val dataMapRequest = PutDataMapRequest.create("/tremor_data")
        val dataMap = dataMapRequest.dataMap
        val timeStamp = System.currentTimeMillis()
        dataMap.putLong("tremorIsOccuring", tremorIsOccuring)
        dataMap.putLong("timeStamp", timeStamp)
        val dataRequest = dataMapRequest.asPutDataRequest().setUrgent()
        dataClient.putDataItem(dataRequest).addOnSuccessListener { Log.d("dataLayer", "Poslanie bolo uspesne") }.addOnFailureListener{Log.d("dataLayer", "Poslanie zluhalo")}
    }

    private fun handleAccelerometerData(event: SensorEvent) {
        val (x, y, z) = event.values // Zrýchlenie v osiach X, Y, Z (m/s²)

        Toast.makeText(context, "X: $x Y: $y Z: $z", Toast.LENGTH_SHORT).show()
        sendMessage(x, y, z)

        // Vypočítame veľkosť zrýchlenia ako vektorovú dĺžku
        val accelerationMagnitude = Math.sqrt((x * x + y * y + z * z).toDouble())

        // Hranica pre detekciu pohybu (môžeš experimentovať)
        val motionThreshold = 2.0
        val isMoving = accelerationMagnitude > motionThreshold
        isStationary = !isMoving

        // Skontrolujeme, či prešiel nový deň a resetujeme aktívny čas
        val currentDay = LocalDate.now().dayOfYear
        if (dayInAYear != currentDay) {
            dayInAYear = currentDay
            totalActiveTimeToday = 0 // Reset denného aktívneho času
        }

        if (isMoving) {
            if (!isActive) {
                isActive = true
                activeStartTime = System.currentTimeMillis()
                Log.d("ActivityStatus", "User started moving")
            }
        } else {
            if (isActive) {
                val activeEndTime = System.currentTimeMillis()
                totalActiveTimeToday += activeEndTime - activeStartTime
                isActive = false
                Log.d("ActivityStatus", "User stopped moving")
            }
        }
    }

    private fun handleHeartRateData(event: SensorEvent) {
        heartRate = event.values[0]
        Toast.makeText(context, "heartRate: $heartRate", Toast.LENGTH_SHORT).show()
        val currentTime = System.currentTimeMillis()
        val currentDate = LocalDate.now()

        // Ak je pacient stále v stave spánku
        if (isSleeping) {
            // Kontrola, či nastala polnoc
            if (currentDate.dayOfYear != dayInAYear) {
                // Vypočítame čas spánku od zaspatia do polnoci
                val endOfDay = currentDate
                    .atTime(23, 59, 59)
                    .atZone(ZoneId.systemDefault())
                    .toEpochSecond() * 1000
                totalSleepTimeInADay += endOfDay - sleepStartTime

                // Reset na nový deň
                dayInAYear = currentDate.dayOfYear
                totalSleepTimeInADay = 0
                sleepStartTime = currentTime
            } else {
                // Ak nie je polnoc, normálne započítame čas spánku
                totalSleepTimeInADay += currentTime - sleepStartTime
                sleepStartTime = currentTime // Posunieme začiatok spánku
            }
        }

        val isTemperatureDrop = lastTemperature?.let { it - event.values[0] >= 0.2 } ?: false
        // spí
        if (isStationary && heartRate in 40f..70f && isTemperatureDrop) {
            Toast.makeText(context, "User is likely sleeping (HR: $heartRate BPM)", Toast.LENGTH_SHORT).show()
            if (!isSleeping) {
                isSleeping = true
                sleepStartTime = currentTime
            }
        } else {
            // prebudí sa
            if (isSleeping) {
                sleepEndTime = currentTime
                totalSleepTime = sleepEndTime - sleepStartTime
                // Pridáme čas spánku len v prípade, že nebol zahrnutý priebežne
                if (currentDate.dayOfYear == dayInAYear) {
                    totalSleepTimeInADay += totalSleepTime
                }
                isSleeping = false
                sendASleepMessage()
            }
        }

        // má off-freezing
        if (isStationary && heartRate > 70f) {
            Toast.makeText(context, "User is likely off-freezed (HR: $heartRate BPM)", Toast.LENGTH_SHORT).show()
            offFreezingState = true;
        } else{
            offFreezingState = false;
        }
    }

    private fun handleStepCounterData(event: SensorEvent) {
        val currentStepCount = event.values[0]
        val currentDay = LocalDate.now().dayOfYear

        // polnoc
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

        Toast.makeText(context, "Total Steps Today: $totalStepsInDay", Toast.LENGTH_SHORT).show()
    }

    fun getTotalStepsInDay(): Int {
        return totalStepsInDay
    }

    fun getTotalActiveTimeToday(): Long {
        return totalActiveTimeToday
    }

    fun isUserActive(): Boolean {
        return isActive
    }

    // **************
    // **  TREMOR  **
    // **************
    private fun detectTremorWithFFT(event: SensorEvent) {
        val (x, y, z) = event.values
        val currentTime = System.currentTimeMillis()

        // Uloženie do buffera
        tremorBufferX.add(x)
        tremorBufferY.add(y)
        tremorBufferZ.add(z)
        tremorBufferTime.add(currentTime)

        // Odstránenie starých dát (viac ako 2 sekundy)
        while (tremorBufferTime.isNotEmpty() && currentTime - tremorBufferTime.first() > tremorWindowMs) {
            tremorBufferX.removeAt(0)
            tremorBufferY.removeAt(0)
            tremorBufferZ.removeAt(0)
            tremorBufferTime.removeAt(0)
        }

        // Máme dostatok dát na FFT analýzu?
        val samplingRate = tremorBufferX.size / (tremorWindowMs / 1000f) // Odvodíme vzorkovaciu frekvenciu
        if (tremorBufferX.size >= 1024) { // Potrebujeme aspoň 1024 vzoriek
            val tremorPower = computeTremorPower(
                tremorBufferX.toFloatArray(),
                tremorBufferY.toFloatArray(),
                tremorBufferZ.toFloatArray(),
                samplingRate
            )

            // Ak výkon v pásme 3-7 Hz je väčší ako 0, tremor sa deteguje
            tremorIsOccuring = if (tremorPower > 0) 1 else 0
            Log.d("TremorDetection", "Tremor Power: $tremorPower | Tremor Occurring: $tremorIsOccuring")
            sendATremorMessage()
        }
    }

    // Funckia na vypocet vykonu tremoru pozitim FFT a PSD
    //FFT sa pocita pomocou funkcie fft, ale je mozne pouzit aj kniznicu JTransforms - mozno bude vypocet rychlejsi?
    fun computeTremorPower(x: FloatArray, y: FloatArray, z: FloatArray, samplingRate: Float): Double {

        val n = x.size
        if (n == 0) return 0.0  // Return 0 if no data

        // vypocitame vysledne zrychlenie z troch osi x, y, a z;
        val r = FloatArray(n) { sqrt(x[it].pow(2) + y[it].pow(2) + z[it].pow(2)) }

        // Normalizacia signalu s nulovou strednou hodnotou (moze vylepsit analyzu)
        val meanR = r.average().toFloat()
        val normalizedR = r.map { it - meanR }.toFloatArray()

        // Vypocet FFT pomocou takzvanej Welchovej metody
        val psd = welchPSD(normalizedR, samplingRate)

        // výber frekvencneho pasma tremoru
        return computeBandPower(psd, samplingRate, 3f, 7f)
    }

    // vypocet PSD Welchovou metodou
    private fun welchPSD(signal: FloatArray, fs: Float, windowSize: Int = 256, overlap: Int = 128): DoubleArray {
        val step = windowSize - overlap
        val numSegments = (signal.size - overlap) / step
        val psd = DoubleArray(windowSize / 2) { 0.0 }

        for (i in 0 until numSegments) {
            val segment = signal.copyOfRange(i * step, i * step + windowSize)
            val windowedSegment = applyHannWindow(segment) //na zlepsenie vysledkov sa aplikuje na segmenty okno, napr. Hann
            val fftResult = fft(windowedSegment)

            for (j in psd.indices) {
                psd[j] += (fftResult[j].pow(2)) / numSegments  // Primerna PSD
            }
        }

        return psd
    }

    // Vypocet vykonu vo vybranom frekvencnom pasme
    private fun computeBandPower(psd: DoubleArray, fs: Float, fLow: Float, fHigh: Float): Double {
        val freqResolution = fs / (2 * psd.size)
        val lowIndex = (fLow / freqResolution).toInt()
        val highIndex = (fHigh / freqResolution).toInt()
        return psd.slice(lowIndex..highIndex).sum()
    }

    // Aplikacia Hannovho okna na vylepsinie spektralneho odhadu PSD
    private fun applyHannWindow(data: FloatArray): FloatArray {
        val n = data.size
        return FloatArray(n) { i -> data[i] * (0.5f - 0.5f * cos(2 * Math.PI * i / (n - 1)).toFloat()) }
    }

    // Implementacia FFT na vypocet amplitudoveho spektra signalu
    private fun fft(data: FloatArray): DoubleArray {
        val n = data.size
        val real = data.copyOf()
        val imag = FloatArray(n)

        var m = 0
        while (1 shl m < n) m++

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
            val sine = sin(delta)
            val cosine = cos(delta)
            for (i in 0 until step) {
                var k = i
                val re = cos(i * delta)
                val im = sin(i * delta)
                while (k < n) {
                    val l = k + step
                    val tRe = real[l] * re - imag[l] * im
                    val tIm = real[l] * im + imag[l] * re
                    real[l] = real[k] - tRe
                    imag[l] = imag[k] - tIm
                    real[k] += tRe
                    imag[k] += tIm
                    k += jump
                }
            }
            step = jump
        }
        return DoubleArray(n / 2) { sqrt(real[it].pow(2) + imag[it].pow(2)).toDouble() }
    }

}
