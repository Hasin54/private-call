package com.example.model

/**
 * Represents gathered ICE candidate options for establishing peer connections.
 */
data class IceCandidateModel(
    val sdpMid: String = "",
    val sdpMLineIndex: Int = 0,
    val sdp: String = "",
    val senderId: String = ""
)
