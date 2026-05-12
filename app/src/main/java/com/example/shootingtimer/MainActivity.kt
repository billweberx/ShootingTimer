package com.example.shootingtimer

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
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

fun shareTimerGroup(context: Context, groupName: String, jsonData: String) {
    val sendIntent: Intent = Intent().apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_TEXT, jsonData) // Send the raw JSON text
        putExtra(Intent.EXTRA_SUBJECT, "Timer Group: $groupName")
        type = "text/plain"
    }
    val shareIntent = Intent.createChooser(sendIntent, "Share Timer Group")
    context.startActivity(shareIntent)
}

class TimerState(val id: String = UUID.randomUUID().toString()) {
    var timerName by mutableStateOf("")
    var durationInput by mutableStateOf("10")
    var delayInput by mutableStateOf("5")
    var remainingTime by mutableIntStateOf(0)
    var isRunning by mutableStateOf(false)
    var isInDelayPhase by mutableStateOf(false)
    var isStarted by mutableStateOf(false)

    fun toJsonObject(): JSONObject {
        return JSONObject().apply {
            put("name", timerName)
            put("duration", durationInput)
            put("delay", delayInput)
        }
    }

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

enum class Screen { Main, Settings }
// Updated State Holder
class NavigationState(initialScreen: Screen) {
    var current by mutableStateOf(initialScreen)
}

@Composable
fun rememberNavigationState(initialScreen: Screen = Screen.Main): NavigationState {
    return remember { NavigationState(initialScreen) }
}
@Composable
fun TimerScreen(modifier: Modifier = Modifier) {
    val timers = remember { mutableStateListOf<TimerState>().apply { add(TimerState()) } }
    val toneGenerator = remember { ToneGenerator(AudioManager.STREAM_ALARM, 100) }

    // Using the state holder object instead of a direct 'by remember' variable
    val navState = rememberNavigationState(Screen.Main)

    when (navState.current) {
        Screen.Main -> {
            Column(modifier = modifier.fillMaxSize()) {
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
                        Text("Shooting Timer", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Row {
                            Button(onClick = { timers.add(0, TimerState()) }) { Text("Create") }
                            Spacer(Modifier.width(8.dp))
                            // Reference navState.current - IDE clearly sees this as a read/write
                            IconButton(onClick = { navState.current = Screen.Settings }) {
                                Icon(Icons.Default.Settings, contentDescription = "Settings")
                            }
                        }
                    }
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(timers, key = { it.id }) { timerState ->
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
        }
        Screen.Settings -> {
            SettingsScreen(
                timers = timers,
                onBack = { navState.current = Screen.Main }
            )
        }
    }

    DisposableEffect(Unit) { onDispose { toneGenerator.release() } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(timers: SnapshotStateList<TimerState>, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("TimerGroups", Context.MODE_PRIVATE) }
    // Use LocalClipboardManager and suppress the warning
    @Suppress("DEPRECATION")
    val clipboardManager = LocalClipboardManager.current
    var groupName by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val savedGroups = remember { mutableStateListOf<String>().apply { addAll(prefs.all.keys) } }

    Column(modifier = Modifier
        .fillMaxSize()
        .padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Save/Load Groups", style = MaterialTheme.typography.headlineMedium)

        // Editable Dropdown / Name Box
        Box {
            OutlinedTextField(
                value = groupName,
                onValueChange = { groupName = it },
                label = { Text("Group Name") },
                modifier = Modifier.fillMaxWidth(),
                trailingIcon = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
            )
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                savedGroups.forEach { name ->
                    DropdownMenuItem(
                        text = { Text(name) },
                        onClick = {
                            groupName = name
                            expanded = false
                            // Load Logic
                            val jsonString = prefs.getString(name, null)
                            if (jsonString != null) {
                                val array = JSONArray(jsonString)
                                timers.clear()
                                for (i in 0 until array.length()) {
                                    val obj = array.getJSONObject(i)
                                    val newTimer = TimerState().apply {
                                        timerName = obj.getString("name")
                                        durationInput = obj.getString("duration")
                                        delayInput = obj.getString("delay")
                                    }
                                    timers.add(newTimer)
                                }
                            }
                        }
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(modifier = Modifier.weight(1f), onClick = {
                if (groupName.isNotBlank()) {
                    val array = JSONArray()
                    timers.forEach { array.put(it.toJsonObject()) }
                    prefs.edit().putString(groupName, array.toString()).apply()
                    if (!savedGroups.contains(groupName)) savedGroups.add(groupName)
                }
            }) { Text("Save") }

            Button(modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.Red), onClick = {
                prefs.edit().remove(groupName).apply()
                savedGroups.remove(groupName)
                groupName = ""
            }) { Text("Delete") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    val jsonData = prefs.getString(groupName, "") ?: ""
                    if (jsonData.isNotEmpty()) {
                        shareTimerGroup(context, groupName, jsonData)
                    }
                },
                enabled = groupName.isNotBlank() && savedGroups.contains(groupName)
            ) {
                Text("Share")
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = {
                    // Use the standard getText() which returns AnnotatedString
                    val clipboardData = clipboardManager.getText()
                    val clipboardText = clipboardData?.text // Convert AnnotatedString to String

                    if (!clipboardText.isNullOrBlank() && groupName.isNotBlank()) {
                        try {
                            JSONArray(clipboardText)
                            prefs.edit().putString(groupName, clipboardText).apply()

                            if (!savedGroups.contains(groupName)) {
                                savedGroups.add(groupName)
                            }
                            android.widget.Toast.makeText(context, "Group Imported!", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            android.widget.Toast.makeText(context, "Invalid data in clipboard", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = groupName.isNotBlank()
            ) {
                Text("Import")
            }
        }  // end of Row
        Spacer(modifier = Modifier.weight(1f)) // Pushes the Back button to the bottom
        Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text("Back to Timers")
        }
    }  // end of column
}  // end of SettingsScreen()

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
                    // Time hit zero - Start 300 MS beep
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
            TextField(
                value = timerState.timerName,
                onValueChange = { if (it.length <= 35) timerState.timerName = it },
                placeholder = { Text("Enter Timer Name...", fontSize = 12.sp) },
                modifier = Modifier
                    .fillMaxWidth(0.8f) // Leaves room so it doesn't hit the delete button
                    .align(Alignment.Start),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 12.sp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    unfocusedIndicatorColor = Color.LightGray.copy(alpha = 0.5f)
                )
            )
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
