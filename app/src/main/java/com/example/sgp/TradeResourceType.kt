package com.example.sgp

/**
 * All resource types that can be exchanged in a trade.
 * Replaces the old string-only "requesterSkill / receiverSkill" approach.
 */
enum class TradeResourceType(val label: String, val emoji: String) {
    VIDEO("Video", "📹"),
    NOTES("Notes", "📄"),
    LIVE_SESSION("Live Teaching", "👨‍🏫"),
    DOCUMENT("Document", "📑"),
    PDF("PDF", "📑"),
    COURSE("Course", "🎓"),
    OTHER("Other", "📚");

    companion object {
        fun fromString(value: String?): TradeResourceType {
            if (value.isNullOrBlank()) return OTHER
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: OTHER
        }
    }
}