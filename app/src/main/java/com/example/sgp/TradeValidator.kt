package com.example.sgp

/**
 * One place for all trade validation rules and their user-facing messages.
 * Both UI (pre-checks) and TradeManager (in-transaction re-checks) call into this.
 */
object TradeValidator {

    // ── User-facing error messages ──
    const val ERR_NO_INTERNET = "No internet connection. Please try again."
    const val ERR_SEND_FAILED = "Unable to send trade request. Please try again."
    const val ERR_UNAVAILABLE = "This trade request is no longer available."
    const val ERR_SCHEDULE_TAKEN = "The selected schedule has already been taken."
    const val ERR_ALREADY_FINALIZED = "This trade has already been finalized."
    const val ERR_CANNOT_MODIFY = "You cannot modify this trade."
    const val ERR_PAST_TIME = "The meeting time cannot be in the past."
    const val ERR_COUNTER_ALREADY = "You have already submitted a counter-schedule."
    const val ERR_SELF_TRADE = "You cannot trade with your own resource."
    const val ERR_DUPLICATE = "You already have an active trade request for this resource with this user."
    const val ERR_NO_RESOURCE = "Please select a resource to offer."
    const val ERR_NO_SCHEDULE = "Please pick a date and time."
    const val ERR_NOT_PARTICIPANT = "You are not a participant in this trade."
    const val ERR_INVALID_STATE = "This action is not allowed in the current state."

    /** Quick pre-flight check on the client before hitting the network. */
    fun validateNewTrade(trade: Trade): String? {
        if (trade.requesterId.isBlank()) return "Missing requester information."
        if (trade.receiverId.isBlank()) return "Missing recipient information."
        if (trade.requesterId.equals(trade.receiverId, ignoreCase = true)) return ERR_SELF_TRADE
        if (trade.requestedResourceId.isBlank()) return "Missing requested resource."
        if (trade.offeredResourceId.isBlank()) return ERR_NO_RESOURCE
        if (trade.proposedSchedule <= 0L) return ERR_NO_SCHEDULE
        if (trade.proposedSchedule <= System.currentTimeMillis()) return ERR_PAST_TIME
        return null
    }

    /**
     * Maps a low-level exception thrown by TradeManager into the appropriate
     * user-facing message. Never leaks stack traces or Firestore internals.
     */
    fun mapException(e: Throwable): String {
        val raw = e.message.orEmpty()
        return when {
            raw.contains("self", ignoreCase = true) -> ERR_SELF_TRADE
            raw.contains("already have", ignoreCase = true) -> ERR_DUPLICATE
            raw.contains("counter-schedule has already", ignoreCase = true) -> ERR_COUNTER_ALREADY
            raw.contains("future time", ignoreCase = true)
                    || raw.contains("already passed", ignoreCase = true) -> ERR_PAST_TIME
            raw.contains("conflict", ignoreCase = true)
                    || raw.contains("already have a trade at that time", ignoreCase = true) -> ERR_SCHEDULE_TAKEN
            raw.contains("Only the receiver", ignoreCase = true)
                    || raw.contains("Only the requester", ignoreCase = true)
                    || raw.contains("not part of this trade", ignoreCase = true)
                    || raw.contains("not a participant", ignoreCase = true) -> ERR_CANNOT_MODIFY
            raw.contains("can no longer be", ignoreCase = true)
                    || raw.contains("No counter", ignoreCase = true) -> ERR_UNAVAILABLE
            raw.contains("finalized", ignoreCase = true) -> ERR_ALREADY_FINALIZED
            raw.contains("network", ignoreCase = true)
                    || raw.contains("UNAVAILABLE", ignoreCase = true) -> ERR_NO_INTERNET
            else -> ERR_SEND_FAILED
        }
    }
}