package com.example.model

/**
 * Represents a secure message in a one-to-one room.
 * Message Types: "text", "audio_call_log", "video_call_log"
 */
data class Message(
    val messageId: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val receiverId: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val messageType: String = "text"
)
