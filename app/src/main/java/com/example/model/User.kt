package com.example.model

/**
 * Represent a user in the Global Call communication network.
 */
data class User(
    val userId: String = "",
    val email: String = "",
    val name: String = "",
    val phone: String = "",
    val avatarUrl: String = "",
    val status: String = "Feeling happy", // e.g., "Available", "Busy"
    val isOnline: Boolean = false,
    val fcmToken: String = "",
    val lastSeen: Long = 0L
)
