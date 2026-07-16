package com.example.sgp

data class Report(
        val id: String = "",
        val reporterId: String = "",
        val reportedUserId: String = "",
        val reason: String = "",
        val description: String = "",
        val status: String = "pending", // pending, resolved, dismissed
        val timestamp: Long = 0L
)
