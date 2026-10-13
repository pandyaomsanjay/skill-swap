package com.example.sgp

import org.json.JSONObject

/** Pure Kotlin (no Android classes except org.json, which is on the platform) so it can be unit-tested on the JVM. */
object ModerationMessages {

    const val WARNINGS_BEFORE_BLOCK = 5

    fun thresholdForStage(stage: Int): Int = when (stage) {
        1 -> 5
        2 -> 3
        3 -> 2
        else -> 5
    }

    fun stageLabel(stage: Int): String = when (stage) {
        1 -> "Stage 1 · 5 warnings → 24-hour ban"
        2 -> "Stage 2 · 3 warnings → 48-hour ban"
        3 -> "Stage 3 · 2 warnings → permanent ban"
        else -> ""
    }

    fun warningTitle(stageWarnings: Int, stage: Int): String {
        val threshold = thresholdForStage(stage)
        return "Warning $stageWarnings of $threshold"
    }

    fun warningBody(stageWarnings: Int, stage: Int, banLevel: Int, isPermanent: Boolean, blockedUntilMillis: Long?, nowMillis: Long): String {
        if (isPermanent || banLevel >= 3) {
            return "Your account has been permanently banned from chat. Contact an administrator."
        }
        if (blockedUntilMillis != null && blockedUntilMillis > nowMillis) {
            val span = blockLabel(blockedUntilMillis - nowMillis)
            return "You have reached $stageWarnings warnings in this stage. Chat sending is restricted for $span."
        }
        val threshold = thresholdForStage(stage)
        return when {
            stageWarnings >= threshold - 1 ->
                "You are close to a chat restriction. $stageWarnings of $threshold warnings in this stage."
            stageWarnings >= 3 ->
                "You have $stageWarnings warnings. Continued violations may restrict your ability to chat."
            else ->
                "Your message may violate our community guidelines. Please communicate respectfully."
        }
    }

    fun categoryLabel(category: String): String = when (category.lowercase()) {
        "abuse" -> "abusive language"
        "threat" -> "threatening language"
        "sexual" -> "unwanted sexual content"
        "spam" -> "spam"
        "clean" -> "no violation"
        else -> "a guideline violation"
    }

    /**
     * Turns a raw ban reason into a short, human label.
     *  - Input like  {"category":"abuse","ruleId":"abuse.term","confidence":"medium"}
     *    becomes     "abusive language"
     *  - Input like  "abuse"   becomes "abusive language"
     *  - Null or blank input returns null so callers can hide the row.
     */
    fun cleanBanReason(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()

        // Fast path: the value is already a plain category string.
        val lower = trimmed.lowercase()
        if (lower in setOf("abuse", "threat", "sexual", "spam", "clean")) {
            return categoryLabel(lower)
        }

        // Try to parse as JSON and pull out the "category" field.
        return try {
            val json = JSONObject(trimmed)
            val cat = json.optString("category", "").ifBlank { null }
            if (cat != null) categoryLabel(cat) else "a guideline violation"
        } catch (_: Exception) {
            // Not JSON, not a known category: show a safe generic label.
            "a guideline violation"
        }
    }

    const val BLOCKED_BODY = "You cannot send messages until your temporary chat restriction expires."
    const val PERMANENT_BAN_BODY =
        "You are permanently banned from chat. You can still read existing conversations, but you cannot send new messages. " +
                "Please contact an administrator if you believe this is a mistake."

    fun blockLabel(remainingMillis: Long): String {
        val minutes = (remainingMillis.coerceAtLeast(0) + 59_999) / 60_000
        return if (minutes >= 60) {
            val hours = (minutes + 59) / 60
            if (hours == 1L) "1 hour" else "$hours hours"
        } else {
            if (minutes == 1L) "1 minute" else "$minutes minutes"
        }
    }

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

    fun banTypeLabel(banLevel: Int, isPermanent: Boolean): String = when {
        isPermanent || banLevel >= 3 -> "Permanent"
        banLevel == 2 -> "48-hour"
        banLevel == 1 -> "24-hour"
        else -> "None"
    }
}