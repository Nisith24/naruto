package com.example.telegramlistener.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Body

interface TelegramApi {
    @POST("/bot{token}/sendMessage")
    suspend fun sendMessage(
        @Path("token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Query("text") text: String,
        @Query("parse_mode") parseMode: String? = "Markdown",
        @Query("message_thread_id") threadId: Int? = null,
        @Body replyMarkup: InlineKeyboardMarkup? = null
    ): Response<TelegramResponse<Message>>
    
    @POST("/bot{token}/answerCallbackQuery")
    suspend fun answerCallbackQuery(
        @Path("token", encoded = true) token: String,
        @Query("callback_query_id") queryId: String,
        @Query("text") text: String? = null,
        @Query("show_alert") showAlert: Boolean? = null
    ): Response<TelegramResponse<Boolean>>

    @POST("/bot{token}/editMessageText")
    suspend fun editMessageText(
        @Path("token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Query("message_id") messageId: Long,
        @Query("text") text: String,
        @Query("parse_mode") parseMode: String? = "Markdown",
        @Body replyMarkup: InlineKeyboardMarkup? = null
    ): Response<TelegramResponse<Message>>

    @POST("/bot{token}/createForumTopic")
    suspend fun createForumTopic(
        @Path("token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Query("name") name: String
    ): Response<ForumTopicResponse>

    @GET("/bot{token}/getUpdates")
    suspend fun getUpdates(
        @Path("token", encoded = true) token: String,
        @Query("offset") offset: Long? = null,
        @Query("timeout") timeout: Int? = null
    ): Response<TelegramResponse<List<Update>>>

    @retrofit2.http.Multipart
    @POST("/bot{token}/sendPhoto")
    suspend fun sendPhoto(
        @Path("token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @retrofit2.http.Part photo: okhttp3.MultipartBody.Part,
        @Query("caption") caption: String? = null,
        @Query("message_thread_id") threadId: Int? = null
    ): Response<TelegramResponse<Message>>

    @retrofit2.http.Multipart
    @POST("/bot{token}/sendDocument")
    suspend fun sendDocument(
        @Path("token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @retrofit2.http.Part document: okhttp3.MultipartBody.Part,
        @Query("caption") caption: String? = null,
        @Query("message_thread_id") threadId: Int? = null
    ): Response<TelegramResponse<Message>>

    @POST("/bot{token}/deleteForumTopic")
    suspend fun deleteForumTopic(
        @Path("token", encoded = true) token: String,
        @Query("chat_id") chatId: String,
        @Query("message_thread_id") threadId: Int
    ): Response<TelegramResponse<Boolean>>
}
