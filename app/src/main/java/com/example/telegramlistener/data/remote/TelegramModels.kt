package com.example.telegramlistener.data.remote

data class TelegramResponse<T>(
    val ok: Boolean,
    val result: T?,
    val description: String? = null
)

data class Update(
    val update_id: Long,
    val message: Message? = null,
    val edited_message: Message? = null,
    val callback_query: CallbackQuery? = null
)

data class CallbackQuery(
    val id: String,
    val from: User,
    val message: Message? = null,
    val data: String? = null
)

data class User(
    val id: Long,
    val is_bot: Boolean,
    val first_name: String
)

data class Message(
    val message_id: Long,
    val chat: Chat,
    val text: String? = null,
    val message_thread_id: Int? = null
)

data class Chat(
    val id: Long,
    val type: String
)

data class ForumTopicResponse(
    val ok: Boolean,
    val result: ForumTopic?
)

data class ForumTopic(
    val message_thread_id: Int,
    val name: String
)

data class InlineKeyboardMarkup(
    val inline_keyboard: List<List<InlineKeyboardButton>>
)

data class InlineKeyboardButton(
    val text: String,
    val callback_data: String? = null,
    val url: String? = null
)
