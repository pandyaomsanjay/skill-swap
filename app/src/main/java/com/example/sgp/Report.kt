package com.example.sgp

data class Report(
    val id: String = "",
    val reporterId: String = "",
    val reportedUserId: String = "",
    val skillId: String = "",   // ← new: links the report to the reported skill/video
    val reason: String = "",
    val description: String = "",
    val status: String = "",
    val timestamp: Long = 0L
)
