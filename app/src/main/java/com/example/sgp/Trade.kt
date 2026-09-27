package com.example.sgp

import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

/**
 * Complete trade document. Every field required to reconstruct the trade
 * is persisted — no reliance on transient UI state.
 *
 * All additions are backward-compatible: legacy docs without the new
 * fields simply deserialize to safe defaults via the typed accessors.
 *
 * NOTE: `id` is a plain property, NOT @DocumentId. It is set manually
 * wherever a Trade is created or read (e.g. `.copy(id = docRef.id)` on
 * write, `.copy(id = doc.id)` on read). Combining @DocumentId with a
 * document that also stores a literal "id" field causes Firestore to
 * throw at deserialization time, which is why the annotation was removed.
 */
data class Trade(
    var id: String = "",

    // ── Participants ──
    val requesterId: String = "",          // email of the person who initiated
    val requesterName: String = "",
    val receiverId: String = "",           // email of the other person
    val receiverName: String = "",

    // ── Requested (what the requester wants) ──
    val requestedResourceId: String = "",
    val requestedResourceType: String = TradeResourceType.OTHER.name,
    val requestedResourceTitle: String = "",

    // ── Offered (what the requester gives in return) ──
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

    // ── Timestamps ──
    @ServerTimestamp val createdAt: Date? = null,
    @ServerTimestamp val updatedAt: Date? = null,
    val respondedAt: Long = 0L,
    val completedAt: Long = 0L,
    val cancelledAt: Long = 0L,
    val cancellationReason: String = "",

    // ── Live session metadata (only used when a resource type is LIVE_SESSION) ──
    val liveSessionInfo: Map<String, Any>? = null,

    // ── Legacy / compatibility (kept so old docs still parse) ──
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
}