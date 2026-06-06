package com.example.model

/**
 * Represents a session signature for direct WebRTC calling.
 * Statuses: "ringing", "accepted", "rejected", "ended"
 * Types: "audio", "video"
 */
data class Call(
    val callId: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val receiverId: String = "",
    val callType: String = "video",
    val callStatus: String = "ringing",
    val offerSdp: String? = null,
    val answerSdp: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
