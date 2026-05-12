package com.example.shootingtimer

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.shootingtimer.ui.theme.ShootingTimerTheme
import kotlinx.coroutines.delay
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShootingTimerTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    TimerScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

class TimerState(val id: String = UUID.randomUUID().toString()) {
    var durationInput by mutableStateOf("10")
    var delayInput by mutableStateOf("5")
    var remainingTime by mutableIntStateOf(0)
    var isRunning by mutableStateOf(false)
    var isInDelayPhase by mutableStateOf(false)
    var isStarted by mutableStateOf(false)

    fun start() {
        if (!isStarted) {
            val dly = delayInput.toIntOrNull() ?: 0
            if (dly > 0) {
                remainingTime = dly
                isInDelayPhase = true
            } else {
                remainingTime = durationInput.toIntOrNull() ?: 0
                isInDelayPhase = false
            }
            isStarted = true
        }
        isRunning = true
    }

    fun stop() {
        isRunning = false
    }

    fun reset() {
        isRunning = false
        isStarted = false
        isInDelayPhase = false
        remainingTime = durationInput.toIntOrNull() ?: 0
    }
}

@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val timers = remember { mutableStateListOf<TimerState>().apply { add(TimerState()) } }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }

    Column(modifier = modifier.fillMaxSize()) {
        // Non-scrollable Header
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primaryContainer,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Shooting Timer",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Button(onClick = { timers.add(0, TimerState()) }) {
                    Text("Create Timer")
                }
            }
        }

        // Scrollable List of Timers
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            items(timers, key = { it.id }) { timerState ->
                // Ensure each timer block takes about half the screen height
                Box(modifier = Modifier.fillParentMaxHeight(0.5f)) {
                    TimerBlock(
                        timerState = timerState,
                        toneGenerator = toneGenerator,
                        onDelete = { timers.remove(timerState) }
                    )
                }
                HorizontalDivider()
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator.release()
        }
    }
}

@Composable
fun TimerBlock(
    timerState: TimerState,
    toneGenerator: ToneGenerator,
    onDelete: () -> Unit
) {
    // Countdown logic optimized to prevent pauses between phases
    LaunchedEffect(timerState.isRunning) {
        if (timerState.isRunning) {
            while (timerState.isRunning) {
                if (timerState.remainingTime > 0) {
                    delay(1000)
                    if (timerState.isRunning) {
                        timerState.remainingTime -= 1
                    }
                } else {
                    // Time hit zero - Start 2-second beep
                    toneGenerator.startTone(ToneGenerator.TONE_DTMF_0, 300)
                    
                    if (timerState.isInDelayPhase) {
                        // Switch phase immediately without adding extra delay
                        timerState.isInDelayPhase = false
                        timerState.remainingTime = timerState.durationInput.toIntOrNull() ?: 0
                    } else {
                        // Timer finished
                        timerState.isRunning = false
                        timerState.isStarted = false
                        break
                    }
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        IconButton(
            onClick = onDelete,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.Red)
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (timerState.isInDelayPhase) "DELAY: ${timerState.remainingTime}" else "TIME: ${timerState.remainingTime}",
                fontSize = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (timerState.isInDelayPhase) Color.Red else MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = timerState.durationInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) timerState.durationInput = it },
                    label = { Text("Duration (s)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = timerState.delayInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) timerState.delayInput = it },
                    label = { Text("Delay (s)") },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { timerState.start() },
                    modifier = Modifier
                        .weight(2f)
                        .height(64.dp),
                    enabled = !timerState.isRunning
                ) {
                    Text("START", fontSize = 20.sp)
                }
                Button(
                    onClick = { timerState.stop() },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp),
                    enabled = timerState.isRunning
                ) {
                    // Font size reduced to prevent wrapping
                    Text("STOP", fontSize = 10.sp, maxLines = 1)
                }
                Button(
                    onClick = { timerState.reset() },
                    modifier = Modifier
                        .weight(1f)
                        .height(64.dp)
                ) {
                    // Font size reduced to prevent wrapping
                    Text("RESET", fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }
}
