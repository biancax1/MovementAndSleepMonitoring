package com.monitoring.parkinsonism.debug

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object DebugData {
    var currentTime by mutableStateOf("")
    var systemStatus by mutableStateOf("Инициализация")


    var accelX by mutableStateOf(0f)
    var accelY by mutableStateOf(0f)
    var accelZ by mutableStateOf(0f)

    var stepsCount by mutableStateOf(0)
    var walkingSpeed by mutableStateOf(0L)
    var heartRate by mutableStateOf(0f)
    var tremorDetected by mutableStateOf(false)

    //
    private val _logs: MutableList<String> = mutableListOf()
    val logs: List<String>
        get() = _logs.toList()

    fun appendLog(message: String) {
        val entry = "${System.currentTimeMillis()} : $message"
        _logs.add(entry)
    }
}