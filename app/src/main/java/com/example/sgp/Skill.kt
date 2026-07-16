// Skill.kt
package com.example.sgp

data class Skill(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val duration: String = "",
    val credits: Int = 0,               // For single video skills; for playlist, this is the total? Not used directly.
    val timestamp: Long = 0,
    val videoUrl: String? = null,       // Only for single video
    val skillType: String = "single",   // "single" or "playlist"
    val videos: List<PlaylistVideo>? = null // Only for playlist
)

data class PlaylistVideo(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val videoUrl: String = "",
    val credits: Int = 0
)