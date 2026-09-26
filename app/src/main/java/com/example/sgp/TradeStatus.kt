package com.example.sgp

/**
 * Explicit lifecycle for a trade. The state machine is enforced in TradeManager.
 */
enum class TradeStatus(val value: String) {
    PENDING("pending"),
    COUNTER_PROPOSED("counter_proposed"),
    ACCEPTED("accepted"),
    SCHEDULE_FINALIZED("schedule_finalized"),
    COMPLETED("completed"),
    CANCELLED("cancelled"),
    REJECTED("rejected"),
    EXPIRED("expired");

    companion object {
        fun fromString(value: String?): TradeStatus {
            if (value.isNullOrBlank()) return PENDING
            return entries.firstOrNull { it.value.equals(value, ignoreCase = true) } ?: PENDING
        }

        /** Returns true if [from] -> [to] is a legal transition. */
        fun isValidTransition(from: TradeStatus, to: TradeStatus): Boolean = when (from) {
            PENDING -> to in listOf(ACCEPTED, COUNTER_PROPOSED, REJECTED, CANCELLED, EXPIRED)
            COUNTER_PROPOSED -> to in listOf(ACCEPTED, REJECTED, CANCELLED, EXPIRED)
            ACCEPTED -> to in listOf(SCHEDULE_FINALIZED, CANCELLED)
            SCHEDULE_FINALIZED -> to in listOf(COMPLETED, CANCELLED)
            COMPLETED, CANCELLED, REJECTED, EXPIRED -> false // terminal states
        }
    }
}