package com.example.sgp

/**
 * One place for all trade validation rules and their user-facing messages.
 * Both UI (pre-checks) and TradeManager (in-transaction re-checks) call into this.
 *
 * BACKWARD COMPAT: existing constants and functions are unchanged. New
 * constants and functions are appended for the Points / Material negotiation
 * feature. Old callers see no behavioural change.
 */
object TradeValidator {

    // ── User-facing error messages (existing) ──
    const val ERR_NO_INTERNET                = "No internet connection. Please try again."
    const val ERR_SEND_FAILED                = "Unable to send trade request. Please try again."
    const val ERR_UNAVAILABLE                = "This trade request is no longer available."
    const val ERR_SCHEDULE_TAKEN             = "The selected schedule has already been taken."
    const val ERR_ALREADY_FINALIZED          = "This trade has already been finalized."
    const val ERR_CANNOT_MODIFY              = "You cannot modify this trade."
    const val ERR_PAST_TIME                  = "The meeting time cannot be in the past."
    const val ERR_COUNTER_ALREADY            = "You have already submitted a counter-offer."
    const val ERR_SELF_TRADE                 = "You cannot trade with your own resource."
    const val ERR_DUPLICATE                  = "You already have an active trade request for this resource with this user."
    const val ERR_NO_RESOURCE                = "Please select a resource to offer."
    const val ERR_NO_SCHEDULE                = "Please pick a date and time."
    const val ERR_NOT_PARTICIPANT            = "You are not a participant in this trade."
    const val ERR_INVALID_STATE              = "This action is not allowed in the current state."

    // ── User-facing error messages (new — Points / Material feature) ──
    const val ERR_NO_POINTS                  = "Please enter the number of points you want to offer."
    const val ERR_INVALID_EXCHANGE_METHOD    = "Please select an exchange method."
    const val ERR_INSUFFICIENT_POINTS        = "You don't have enough points for this offer."
    const val ERR_NO_COUNTER                 = "Please enter a valid counter-offer."
    const val ERR_COUNTER_POINTS_POSITIVE    = "Counter points must be greater than zero."
    const val ERR_COUNTER_MATERIAL_REQUIRED  = "Please select a resource for your counter-offer."
    const val ERR_INSUFFICIENT_POINTS_AT_COMPLETION =
        "The requester no longer has enough points to complete this trade."

    // ═══════════════════════════════════════════════════════════════
    // Pre-flight validation (client-side, before hitting the network)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Quick pre-flight check on the client before hitting the network.
     * Extends the original behaviour to accept either a MATERIAL offer
     * (existing behaviour) OR a POINTS offer (new).
     */
    fun validateNewTrade(trade: Trade): String? {
        if (trade.requesterId.isBlank()) return "Missing requester information."
        if (trade.receiverId.isBlank()) return "Missing recipient information."
        if (trade.requesterId.equals(trade.receiverId, ignoreCase = true)) return ERR_SELF_TRADE
        if (trade.requestedResourceId.isBlank()) return "Missing requested resource."

        // NEW: validate the exchange side (points vs material) BEFORE the rest
        validateExchangeMethod(trade)?.let { return it }

        if (trade.proposedSchedule <= 0L) return ERR_NO_SCHEDULE
        if (trade.proposedSchedule <= System.currentTimeMillis()) return ERR_PAST_TIME
        return null
    }

    /**
     * NEW: validates the exchange method on the ORIGINAL offer side.
     * Called by both the UI (pre-check) and TradeManager (in-transaction re-check).
     *
     *  • POINTS   → offeredPoints must be > 0
     *  • MATERIAL → offeredResourceId must be non-blank
     *  • anything else → invalid
     */
    fun validateExchangeMethod(trade: Trade): String? {
        return when (trade.exchangeMethod) {
            ExchangeMethod.POINTS.value -> {
                if (trade.offeredPoints <= 0) ERR_NO_POINTS else null
            }
            ExchangeMethod.MATERIAL.value -> {
                if (trade.offeredResourceId.isBlank()) ERR_NO_RESOURCE else null
            }
            else -> ERR_INVALID_EXCHANGE_METHOD
        }
    }

    /**
     * NEW: validates the counter-offer payload before the transaction.
     * Returns null on success, otherwise the user-facing error message.
     */
    fun validateCounterOffer(
        counterExchangeMethod: String,
        counterPoints: Int,
        counterRequestedResourceId: String,
        counterOfferedResourceId: String
    ): String? {
        return when (counterExchangeMethod) {
            ExchangeMethod.POINTS.value ->
                if (counterPoints <= 0) ERR_COUNTER_POINTS_POSITIVE else null

            ExchangeMethod.MATERIAL.value -> when {
                counterRequestedResourceId.isBlank() -> ERR_COUNTER_MATERIAL_REQUIRED
                counterOfferedResourceId.isBlank()   -> ERR_COUNTER_MATERIAL_REQUIRED
                else -> null
            }

            else -> ERR_NO_COUNTER
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // Exception → user-facing message mapping
    // ═══════════════════════════════════════════════════════════════

    /**
     * Maps a low-level exception thrown by TradeManager into the appropriate
     * user-facing message. Never leaks stack traces or Firestore internals.
     */
    fun mapException(e: Throwable): String {
        val raw = e.message.orEmpty()
        return when {
            // ── existing branches ──
            raw.contains("self", ignoreCase = true) -> ERR_SELF_TRADE
            raw.contains("already have", ignoreCase = true) -> ERR_DUPLICATE
            raw.contains("counter-schedule has already", ignoreCase = true)
                    || raw.contains("counter-offer has already", ignoreCase = true) -> ERR_COUNTER_ALREADY
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

            // ── new branches (Points / Material feature) ──
            raw.contains("insufficient credits for this trade", ignoreCase = true) ->
                ERR_INSUFFICIENT_POINTS_AT_COMPLETION
            raw.contains("don't have enough points", ignoreCase = true)
                    || raw.contains("not enough points", ignoreCase = true) ->
                ERR_INSUFFICIENT_POINTS
            raw.contains("Counter points must be positive", ignoreCase = true) ->
                ERR_COUNTER_POINTS_POSITIVE
            raw.contains("counter-offer", ignoreCase = true) ->
                ERR_NO_COUNTER
            raw.contains("points", ignoreCase = true) &&
                    raw.contains("positive", ignoreCase = true) ->
                ERR_NO_POINTS
            raw.contains("exchange method", ignoreCase = true) ->
                ERR_INVALID_EXCHANGE_METHOD

            else -> ERR_SEND_FAILED
        }
    }
}