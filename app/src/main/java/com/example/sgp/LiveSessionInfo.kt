package com.example.sgp

/**
 * Extra data required when a trade resource is a LIVE_SESSION.
 * Stored inside the Trade doc as a nested map only when relevant.
 * Never exposed to the counterparty until the trade is finalized.
 */
data class LiveSessionInfo(
    val sessionTitle: String = "",
    val description: String = "",
    val durationMinutes: Int = 60,
    val mode: String = "online",        // "online" | "offline"
    val platform: String = "",          // "Google Meet", "Zoom", "In-person", etc.
    val meetingLink: String = "",       // Hidden until finalized
    val physicalLocation: String = ""   // For offline sessions
) {
    fun toMap(): Map<String, Any> = mapOf(
        "sessionTitle" to sessionTitle,
        "description" to description,
        "durationMinutes" to durationMinutes,
        "mode" to mode,
        "platform" to platform,
        "meetingLink" to meetingLink,
        "physicalLocation" to physicalLocation
    )

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any>?): LiveSessionInfo {
            if (map == null) return LiveSessionInfo()
            return LiveSessionInfo(
                sessionTitle = map["sessionTitle"] as? String ?: "",
                description = map["description"] as? String ?: "",
                durationMinutes = (map["durationMinutes"] as? Number)?.toInt() ?: 60,
                mode = map["mode"] as? String ?: "online",
                platform = map["platform"] as? String ?: "",
                meetingLink = map["meetingLink"] as? String ?: "",
                physicalLocation = map["physicalLocation"] as? String ?: ""
            )
        }
    }
}