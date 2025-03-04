package com.monitoring.parkinsonism.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun DebugScreen() {
    var showLogs by remember { mutableStateOf(false) }

    LaunchedEffect(key1 = true) {
        while (true) {
            delay(250L)
        }
    }

    Scaffold {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF121212))
                .padding(8.dp)
        ) {

            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                item {
                    Text(
                        text = "Акселерометр:",
                        color = Color.Cyan,
                        modifier = Modifier.padding(bottom = 4.dp),
                        fontSize = 16.sp
                    )
                }
                item {
                    Text(
                        text = "X = ${DebugData.accelX}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                item {
                    Text(
                        text = "Y = ${DebugData.accelY}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                item {
                    Text(
                        text = "Z = ${DebugData.accelZ}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    Text(
                        text = "Шагов: ${DebugData.stepsCount}",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                item {
                    Text(
                        text = "Скорость: ${DebugData.walkingSpeed} км/ч",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                item {
                    Text(
                        text = "ЧСС: ${DebugData.heartRate} BPM",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
                item {
                    Text(
                        text = "Тремор: " + if (DebugData.tremorDetected) "Обнаружен" else "Не обнаружен",
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { showLogs = true },
                modifier = Modifier
                    .width(150.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                Text(text = "Показать Логи", fontSize = 12.sp)
            }
        }
    }


    if (showLogs) {
        AlertDialog(
            onDismissRequest = { showLogs = false },
            title = { Text(text = "Логи", color = Color.White, fontSize = 16.sp) },
            text = {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF333333))
                        .fillMaxWidth()
                        .padding(8.dp)
                ) {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(DebugData.logs) { logEntry ->
                            Text(
                                text = logEntry,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLogs = false },
                    modifier = Modifier.width(100.dp)
                ) {
                    Text(text = "Закрыть", fontSize = 12.sp)
                }
            }
        )
    }
}