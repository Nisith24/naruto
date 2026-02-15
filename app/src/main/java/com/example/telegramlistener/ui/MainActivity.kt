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
        var status by remember { mutableStateOf("Ready") }
        var isConfigExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            val (token, chat) = repository.getConfig()
            botToken = token
            chatId = chat
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
                "Remote Control Service",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Status Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Status", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(status, style = MaterialTheme.typography.bodyMedium)
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
                        status = "Service Active 🟢"
                    },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Text("Start")
                }

                Button(
                    onClick = {
                        stopService(Intent(applicationContext, MonitorService::class.java))
                        status = "Service Stopped 🔴"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text("Stop")
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
                                    status = "Found ID: $id"
                                } else {
                                    status = "ID Check Failed"
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
                            status = "Config Saved ✅"
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
}

