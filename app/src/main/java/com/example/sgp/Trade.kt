package com.example.sgp

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Trade document.
 *
 * Every field required to reconstruct the trade is persisted — no reliance
 * on transient UI state.
 *
 * BACKWARD COMPAT: every field added for the points/material upgrade has a
 * default that makes an OLD document (written before this feature existed)
 * decode to "MATERIAL" exchange with no counter and no final agreement.
 * Old completed trades therefore skip the new point-transfer path entirely.
 *
 * `id` is a plain property, NOT @DocumentId — it is set manually wherever
 * a Trade is created or read.
 */
data class Trade(
    var id: String = "",

    // ── Participants ──
    val requesterId: String = "",          // email of the person who initiated
    val requesterName: String = "",
    val receiverId: String = "",           // email of the other person
    val receiverName: String = "",

    // ── Requested resource (what the requester wants) ──
    val requestedResourceId: String = "",
    val requestedResourceType: String = TradeResourceType.OTHER.name,
    val requestedResourceTitle: String = "",

    // ═══ NEW: exchange method ═══
    // "POINTS" | "MATERIAL". Defaults to MATERIAL so old docs decode cleanly.
    val exchangeMethod: String = ExchangeMethod.MATERIAL.value,
    // Only meaningful when exchangeMethod == "POINTS"
    val offeredPoints: Int = 0,

    // ── Offered resource (what the requester gives back) ──
    // Populated only when exchangeMethod == "MATERIAL"
    val offeredResourceId: String = "",
    val offeredResourceType: String = TradeResourceType.OTHER.name,
    val offeredResourceTitle: String = "",

    // ── Message + status ──
    val message: String = "",
    val status: String = TradeStatus.PENDING.value,

    // ── Schedule (millis since epoch) ──
    val proposedSchedule: Long = 0L,
    val counterSchedule: Long = 0L,
    val finalizedSchedule: Long = 0L,
    val counterMessage: String = "",

    // ═══ NEW: counter offer (from User B) ═══
    // "" = no counter. Otherwise "POINTS" | "MATERIAL".
    val counterExchangeMethod: String = "",
    // User B's requested points from User A (counterExchangeMethod == "POINTS")
    val counterOfferedPoints: Int = 0,
    // User B's requested resource from User A (counterExchangeMethod == "MATERIAL")
    val counterRequestedResourceId: String = "",
    val counterRequestedResourceType: String = "",
    val counterRequestedResourceTitle: String = "",
    // User B's offer back (usually same as original requested; may differ)
    val counterOfferedResourceId: String = "",
    val counterOfferedResourceType: String = "",
    val counterOfferedResourceTitle: String = "",

    // ═══ NEW: final agreement ═══
    // "" until accepted; then "POINTS" | "MATERIAL".
    val finalExchangeMethod: String = "",
    // Points to transfer at completion (only when finalExchangeMethod == "POINTS")
    val finalPoints: Int = 0,

    // ── Timestamps ──
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
    val respondedAt: Long = 0L,
    val completedAt: Long = 0L,
    val cancelledAt: Long = 0L,
    val cancellationReason: String = "",

    // ── Live session metadata ──
    val liveSessionInfo: Map<String, Any>? = null,

    // ── Legacy / compatibility fields (kept so old screens keep working) ──
    val requesterSkill: String = "",
    val receiverSkill: String = "",
    val timestamp: Long = 0L,
    val completionTimestamp: Long = 0L,
    val reportReason: String = "",
    val uploaderName: String = "",
    val uploaderAvatar: Int = R.drawable.ic_default_profile,
    val skillOffered: String = "",
    val skillRequested: String = "",
    val rating: Float = 0f,
    val videoUrl: String = "",
    val isActive: Boolean = true
) {

    // ═════════════════════════════════════════════════════════════
    // TYPED ACCESSORS — safe with legacy or partial documents
    // ═════════════════════════════════════════════════════════════

    /** Typed status — falls back to PENDING if the field is missing or unknown. */
    val statusEnum: TradeStatus
        get() = TradeStatus.fromString(status)

    /** Typed requested-resource type — falls back to OTHER if unknown. */
    val requestedResourceTypeEnum: TradeResourceType
        get() = TradeResourceType.fromString(requestedResourceType)

    /** Typed offered-resource type — falls back to OTHER if unknown. */
    val offeredResourceTypeEnum: TradeResourceType
        get() = TradeResourceType.fromString(offeredResourceType)

    /** Typed live-session info — always returns a valid instance (never null). */
    val liveSessionInfoTyped: LiveSessionInfo
        get() = LiveSessionInfo.fromMap(liveSessionInfo)

    /**
     * The best available schedule at this moment.
     * Priority: finalized > counter > proposed.
     * Returns 0L if none has been set — callers must check before use.
     */
    val effectiveSchedule: Long
        get() = when {
            finalizedSchedule > 0L -> finalizedSchedule
            counterSchedule > 0L -> counterSchedule
            else -> proposedSchedule
        }

    /**
     * True if meeting details (link / address) should be visible right now.
     * Only true once the schedule has been finalized or the trade is complete.
     */
    val shouldExposeMeetingDetails: Boolean
        get() = statusEnum == TradeStatus.SCHEDULE_FINALIZED
                || statusEnum == TradeStatus.COMPLETED

    /** True if the given email is one of the two participants. */
    fun hasParticipant(email: String): Boolean =
        email.equals(requesterId, ignoreCase = true) ||
                email.equals(receiverId, ignoreCase = true)

    /** True if [email] is the person who started the trade. */
    fun isRequester(email: String): Boolean =
        email.equals(requesterId, ignoreCase = true)

    /** True if [email] is the person receiving the trade request. */
    fun isReceiver(email: String): Boolean =
        email.equals(receiverId, ignoreCase = true)

    /** Other-party email relative to [me]. Returns "" if [me] isn't a participant. */
    fun otherParty(me: String): String = when {
        isRequester(me) -> receiverId
        isReceiver(me) -> requesterId
        else -> ""
    }

    // ═════════════════════════════════════════════════════════════
    // NEW ACCESSORS — Points / Material negotiation
    // ═════════════════════════════════════════════════════════════

    /** True if the requester offered points instead of a resource. */
    val isPointsExchange: Boolean
        get() = exchangeMethod.equals(ExchangeMethod.POINTS.value, ignoreCase = true)

    /** True if the requester offered a resource (default). */
    val isMaterialExchange: Boolean
        get() = !isPointsExchange

    /** True if the receiver has submitted a counter offer. */
    val hasCounterOffer: Boolean
        get() = counterExchangeMethod.isNotBlank()

    /** True if the counter was a points counter. */
    val isCounterPoints: Boolean
        get() = counterExchangeMethod.equals(ExchangeMethod.POINTS.value, ignoreCase = true)

    /** True if the counter was a material counter. */
    val isCounterMaterial: Boolean
        get() = counterExchangeMethod.equals(ExchangeMethod.MATERIAL.value, ignoreCase = true)

    /** True once finalExchangeMethod has been set (accept happened). */
    val isFinalized: Boolean
        get() = finalExchangeMethod.isNotBlank()

    /** Points to actually move at completion. 0 for material or legacy trades. */
    val agreedPoints: Int
        get() = if (finalExchangeMethod.equals(ExchangeMethod.POINTS.value, ignoreCase = true))
            finalPoints else 0

    /** What User A is offering — for display. */
    fun offerDisplay(): String = if (isPointsExchange) {
        "$offeredPoints points"
    } else {
        val type = TradeResourceType.fromString(offeredResourceType)
        "${type.emoji} ${offeredResourceTitle.ifBlank { "Untitled" }}"
    }

    /** What User B is counter-offering — for display. */
    fun counterDisplay(): String = when {
        !hasCounterOffer -> "—"
        isCounterPoints -> "$counterOfferedPoints points"
        else -> {
            val type = TradeResourceType.fromString(counterRequestedResourceType)
            "${type.emoji} ${counterRequestedResourceTitle.ifBlank { "Untitled" }}"
        }
    }

    /** The final agreed exchange — for display. */
    fun finalDisplay(): String = when (finalExchangeMethod) {
        ExchangeMethod.POINTS.value -> "$finalPoints points"
        ExchangeMethod.MATERIAL.value ->
            if (hasCounterOffer && isCounterMaterial) "Material (counter agreement)"
            else offerDisplay()
        else -> "—"
    }
}