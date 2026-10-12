package com.example.sgp

/** Pure Kotlin (no Android classes) so it can be unit-tested on the JVM. All user-facing moderation copy lives here. */
object ModerationMessages {
    const val WARNINGS_BEFORE_BLOCK = 5

    fun warningTitle(count: Int): String =
        if (count >= WARNINGS_BEFORE_BLOCK) "Chat restricted" else "Warning $count of $WARNINGS_BEFORE_BLOCK"

    fun warningBody(count: Int, blockedUntilMillis: Long?, nowMillis: Long): String = when {
        count >= WARNINGS_BEFORE_BLOCK -> {
            val span = if (blockedUntilMillis != null) blockLabel(blockedUntilMillis - nowMillis) else "24 hours"
            "You have received $count warnings. Chat sending is restricted for $span."
        }
        count >= 3 ->
            "You have received $count warnings. Continued violations may temporarily restrict your ability to chat."
        else ->
            "Your message may violate our community guidelines. Please communicate respectfully."
    }

    fun categoryLabel(category: String): String = when (category) {
        "abuse" -> "abusive language"
        "threat" -> "threatening language"
        "sexual" -> "unwanted sexual content"
        "spam" -> "spam"
        else -> "a guideline violation"
    }

    const val BLOCKED_BODY = "You cannot send messages until your temporary chat restriction expires."

    /** "24 hours", "3 hours", "45 minutes" (rounded up). */
    fun blockLabel(remainingMillis: Long): String {
        val minutes = (remainingMillis.coerceAtLeast(0) + 59_999) / 60_000
        return if (minutes >= 60) {
            val hours = (minutes + 59) / 60
            if (hours == 1L) "1 hour" else "$hours hours"
        } else {
            if (minutes == 1L) "1 minute" else "$minutes minutes"
        }
    }

    /** Live countdown text, e.g. "23h 59m" or "4m 05s". Display only; the server decides. */
    fun formatRemaining(remainingMillis: Long): String {
        val total = (remainingMillis.coerceAtLeast(0) + 999) / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> "${h}h ${m.toString().padStart(2, '0')}m"
            m > 0 -> "${m}m ${s.toString().padStart(2, '0')}s"
            else -> "${s}s"
        }
    }
}