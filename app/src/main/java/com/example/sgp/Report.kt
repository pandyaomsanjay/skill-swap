package com.example.sgp

data class Report(
    val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val skillId: String = "",
    val reason: String = "",
    val description: String = "",
    val status: String = "",
    val timestamp: Long = 0L,
    val type: String = "video"   // "video" | "playlist" | "user" — old docs without this field decode as "video"
)