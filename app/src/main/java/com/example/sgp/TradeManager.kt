package com.example.sgp

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for trade mutations, availability checks,
 * state transitions, notifications, and meeting-reminder scheduling.
 *
 * All writes that must be atomic are wrapped in Firestore transactions.
 */
object TradeManager {

    private const val TAG = "TradeManager"

    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // ═════════════════════════════════════════════════════════════
    // CREATE
    // ═════════════════════════════════════════════════════════════

    /**
     * Creates a new PENDING trade. Rejects:
     *   • self-trades
     *   • missing fields
     *   • schedules in the past
     *   • schedules the requester already has a conflict with
     *   • duplicate active requests between the same pair for the same resource
     */
    suspend fun createTrade(trade: Trade): Result<String> = runCatching {
        require(trade.requesterId.isNotBlank()) { "Missing requester" }
        require(trade.receiverId.isNotBlank()) { "Missing receiver" }
        require(!trade.requesterId.equals(trade.receiverId, ignoreCase = true)) {
            "You cannot trade with your own resource"
        }
        require(trade.requestedResourceId.isNotBlank()) { "Missing requested resource" }
        require(trade.offeredResourceId.isNotBlank()) { "Missing offered resource" }
        require(trade.proposedSchedule > 0L) { "Please pick a date and time" }
        require(trade.proposedSchedule > System.currentTimeMillis()) {
            "Scheduled time must be in the future"
        }
        require(isUserAvailable(trade.requesterId, trade.proposedSchedule)) {
            "You already have another trade at that time"
        }

        // ── Duplicate active-request guard ──
        val existing = db.collection("trades")
            .whereEqualTo("requesterId", trade.requesterId)
            .whereEqualTo("receiverId", trade.receiverId)
            .whereEqualTo("requestedResourceId", trade.requestedResourceId)
            .whereIn(
                "status",
                listOf(
                    TradeStatus.PENDING.value,
                    TradeStatus.COUNTER_PROPOSED.value,
                    TradeStatus.ACCEPTED.value,
                    TradeStatus.SCHEDULE_FINALIZED.value
                )
            )
            .limit(1)
            .get()
            .await()

        if (!existing.isEmpty) {
            throw IllegalStateException(
                "You already have an active trade request for this resource with this user"
            )
        }

        val docRef = db.collection("trades").document()
        val now = System.currentTimeMillis()

        val payload = trade.copy(
            id = docRef.id,
            status = TradeStatus.PENDING.value,
            createdAt = java.util.Date(now),
            updatedAt = java.util.Date(now),

            // Legacy mirrors so existing admin screens & adapters still work
            requesterSkill = trade.offeredResourceTitle,
            receiverSkill = trade.requestedResourceTitle,
            skillOffered = trade.offeredResourceTitle,
            skillRequested = trade.requestedResourceTitle,
            timestamp = now,
            isActive = true
        )

        docRef.set(payload).await()

        sendTradeNotification(
            userId = trade.receiverId,
            title = "New Skill Trade Request",
            message = "${trade.requesterName.ifBlank { "Someone" }} wants to exchange resources with you.",
            type = "trade_request",
            tradeId = docRef.id
        )

        docRef.id
    }

    // ═════════════════════════════════════════════════════════════
    // ACCEPT (finalizes the proposed schedule)
    // ═════════════════════════════════════════════════════════════

    /**
     * Receiver accepts the trade. Finalizes the proposed schedule.
     * Notifies the requester.
     *
     * Caller should follow with scheduleRemindersAfterFinalize(context, tradeId).
     */
    suspend fun acceptTrade(tradeId: String, currentUserEmail: String): Result<Unit> = runCatching {
        val snap = db.collection("trades").document(tradeId).get().await()
        require(snap.exists()) { "Trade not found" }
        val preTrade = snap.toObject(Trade::class.java) ?: throw IllegalStateException("Invalid trade")
        val schedule = if (preTrade.counterSchedule > 0L) preTrade.counterSchedule else preTrade.proposedSchedule
        require(schedule > System.currentTimeMillis()) { "Scheduled time has already passed" }
        require(isUserAvailable(preTrade.requesterId, schedule)) { "The requester has a conflict at that time" }
        require(isUserAvailable(preTrade.receiverId, schedule)) { "You already have a trade at that time" }

        var requesterEmail = ""
        var receiverName = ""

        db.runTransaction { tx ->
            val ref = db.collection("trades").document(tradeId)
            val txSnap = tx.get(ref)
            require(txSnap.exists()) { "Trade not found" }

            val trade = txSnap.toObject(Trade::class.java)
                ?: throw IllegalStateException("Invalid trade")

            require(trade.receiverId.equals(currentUserEmail, ignoreCase = true)) {
                "Only the receiver can accept"
            }
            require(TradeStatus.isValidTransition(trade.statusEnum, TradeStatus.ACCEPTED)) {
                "Trade can no longer be accepted (status=${trade.status})"
            }

            requesterEmail = trade.requesterId
            receiverName = trade.receiverName

            val now = System.currentTimeMillis()
            tx.update(
                ref,
                mapOf(
                    "status" to TradeStatus.SCHEDULE_FINALIZED.value,
                    "finalizedSchedule" to schedule,
                    "respondedAt" to now,
                    "updatedAt" to Timestamp(now / 1000, 0)
                )
            )
        }.await()

        sendTradeNotification(
            userId = requesterEmail,
            title = "Trade Accepted",
            message = "Your trade request has been accepted by ${receiverName.ifBlank { "your partner" }}.",
            type = "trade_accepted",
            tradeId = tradeId
        )
    }

    // ═════════════════════════════════════════════════════════════
    // COUNTER SCHEDULE
    // ═════════════════════════════════════════════════════════════

    /**
     * Receiver proposes ONE counter-schedule. Fails if a counter already exists.
     */
    suspend fun counterSchedule(
        tradeId: String,
        newSchedule: Long,
        optionalMessage: String,
        currentUserEmail: String
    ): Result<Unit> = runCatching {
        require(newSchedule > System.currentTimeMillis()) {
            "Please choose a future time"
        }
        require(isUserAvailable(currentUserEmail, newSchedule)) {
            "You already have a trade at that time"
        }

        var requesterEmail = ""
        var receiverName = ""

        db.runTransaction { tx ->
            val ref = db.collection("trades").document(tradeId)
            val snap = tx.get(ref)
            require(snap.exists()) { "Trade not found" }

            val trade = snap.toObject(Trade::class.java)
                ?: throw IllegalStateException("Invalid trade")

            require(trade.receiverId.equals(currentUserEmail, ignoreCase = true)) {
                "Only the receiver can counter"
            }
            require(trade.statusEnum == TradeStatus.PENDING) {
                "Counter is only allowed while the trade is pending"
            }
            require(trade.counterSchedule == 0L) {
                "A counter-schedule has already been sent"
            }

            requesterEmail = trade.requesterId
            receiverName = trade.receiverName

            val now = System.currentTimeMillis()
            tx.update(
                ref,
                mapOf(
                    "status" to TradeStatus.COUNTER_PROPOSED.value,
                    "counterSchedule" to newSchedule,
                    "counterMessage" to optionalMessage,
                    "respondedAt" to now,
                    "updatedAt" to Timestamp(now / 1000, 0)
                )
            )
        }.await()

        sendTradeNotification(
            userId = requesterEmail,
            title = "Counter Schedule Received",
            message = "${receiverName.ifBlank { "The receiver" }} proposed a different meeting time.",
            type = "trade_counter",
            tradeId = tradeId
        )
    }

    /**
     * Requester accepts the receiver's counter-schedule.
     * Caller should follow with scheduleRemindersAfterFinalize(context, tradeId).
     */
    suspend fun acceptCounterSchedule(
        tradeId: String,
        currentUserEmail: String
    ): Result<Unit> = runCatching {
        val snap = db.collection("trades").document(tradeId).get().await()
        require(snap.exists()) { "Trade not found" }
        val preTrade = snap.toObject(Trade::class.java) ?: throw IllegalStateException("Invalid trade")
        require(preTrade.counterSchedule > System.currentTimeMillis()) { "Counter time has already passed" }
        require(isUserAvailable(preTrade.requesterId, preTrade.counterSchedule)) { "You have a conflict at that time" }
        require(isUserAvailable(preTrade.receiverId, preTrade.counterSchedule)) { "The receiver has a conflict at that time" }

        var receiverEmail = ""

        db.runTransaction { tx ->
            val ref = db.collection("trades").document(tradeId)
            val txSnap = tx.get(ref)
            require(txSnap.exists()) { "Trade not found" }

            val trade = txSnap.toObject(Trade::class.java)
                ?: throw IllegalStateException("Invalid trade")

            require(trade.requesterId.equals(currentUserEmail, ignoreCase = true)) {
                "Only the requester can accept the counter"
            }
            require(trade.statusEnum == TradeStatus.COUNTER_PROPOSED) {
                "No counter-schedule to accept"
            }

            receiverEmail = trade.receiverId

            val now = System.currentTimeMillis()
            tx.update(
                ref,
                mapOf(
                    "status" to TradeStatus.SCHEDULE_FINALIZED.value,
                    "finalizedSchedule" to trade.counterSchedule,
                    "respondedAt" to now,
                    "updatedAt" to Timestamp(now / 1000, 0)
                )
            )
        }.await()

        sendTradeNotification(
            userId = receiverEmail,
            title = "Trade Scheduled",
            message = "Your skill trade is confirmed. The schedule has been finalized.",
            type = "trade_finalized",
            tradeId = tradeId
        )
    }

    // ═════════════════════════════════════════════════════════════
    // REJECT / CANCEL / COMPLETE
    // ═════════════════════════════════════════════════════════════

    suspend fun rejectTrade(
        tradeId: String,
        currentUserEmail: String,
        reason: String = ""
    ): Result<Unit> = updateStatus(
        tradeId = tradeId,
        currentUserEmail = currentUserEmail,
        newStatus = TradeStatus.REJECTED,
        reason = reason,
        allowFrom = listOf(TradeStatus.PENDING, TradeStatus.COUNTER_PROPOSED),
        notifyCounterpart = true
    )

    suspend fun cancelTrade(
        tradeId: String,
        currentUserEmail: String,
        reason: String = ""
    ): Result<Unit> = updateStatus(
        tradeId = tradeId,
        currentUserEmail = currentUserEmail,
        newStatus = TradeStatus.CANCELLED,
        reason = reason,
        allowFrom = listOf(
            TradeStatus.PENDING,
            TradeStatus.COUNTER_PROPOSED,
            TradeStatus.ACCEPTED,
            TradeStatus.SCHEDULE_FINALIZED
        ),
        notifyCounterpart = true
    )

    suspend fun completeTrade(
        tradeId: String,
        currentUserEmail: String
    ): Result<Unit> = updateStatus(
        tradeId = tradeId,
        currentUserEmail = currentUserEmail,
        newStatus = TradeStatus.COMPLETED,
        reason = "",
        allowFrom = listOf(TradeStatus.SCHEDULE_FINALIZED, TradeStatus.ACCEPTED),
        notifyCounterpart = true,
        isCompletion = true
    )

    private suspend fun updateStatus(
        tradeId: String,
        currentUserEmail: String,
        newStatus: TradeStatus,
        reason: String,
        allowFrom: List<TradeStatus>,
        notifyCounterpart: Boolean,
        isCompletion: Boolean = false
    ): Result<Unit> = runCatching {
        var counterpartEmail = ""
        var actorName = ""

        db.runTransaction { tx ->
            val ref = db.collection("trades").document(tradeId)
            val snap = tx.get(ref)
            require(snap.exists()) { "Trade not found" }

            val trade = snap.toObject(Trade::class.java)
                ?: throw IllegalStateException("Invalid trade")

            require(trade.hasParticipant(currentUserEmail)) {
                "You are not part of this trade"
            }
            require(trade.statusEnum in allowFrom) {
                "Trade can no longer be ${newStatus.value}"
            }

            val isRequester = trade.requesterId.equals(currentUserEmail, ignoreCase = true)
            counterpartEmail = if (isRequester) trade.receiverId else trade.requesterId
            actorName = if (isRequester) trade.requesterName else trade.receiverName

            val now = System.currentTimeMillis()
            val update = hashMapOf<String, Any>(
                "status" to newStatus.value,
                "updatedAt" to Timestamp(now / 1000, 0)
            )

            when (newStatus) {
                TradeStatus.COMPLETED -> {
                    update["completedAt"] = now
                    update["completionTimestamp"] = now
                    update["isActive"] = false
                }
                TradeStatus.CANCELLED -> {
                    update["cancelledAt"] = now
                    update["cancellationReason"] = reason
                    update["isActive"] = false
                }
                TradeStatus.REJECTED -> {
                    update["respondedAt"] = now
                    update["cancellationReason"] = reason
                    update["isActive"] = false
                }
                else -> { /* PENDING / COUNTER / etc — no extra fields */ }
            }

            tx.update(ref, update)
        }.await()

        if (notifyCounterpart) {
            val title = when (newStatus) {
                TradeStatus.REJECTED -> "Trade Cancelled"
                TradeStatus.CANCELLED -> "Trade Cancelled"
                TradeStatus.COMPLETED -> "Trade Completed"
                else -> "Trade Updated"
            }
            val body = when (newStatus) {
                TradeStatus.COMPLETED ->
                    "Your skill exchange with ${actorName.ifBlank { "your partner" }} has been successfully completed."
                TradeStatus.CANCELLED ->
                    "The skill trade has been cancelled." + if (reason.isNotBlank()) " Reason: $reason" else ""
                TradeStatus.REJECTED ->
                    "The skill trade has been cancelled."
                else -> "Trade status updated."
            }
            sendTradeNotification(
                userId = counterpartEmail,
                title = title,
                message = body,
                type = "trade_${newStatus.value}",
                tradeId = tradeId
            )
        }

        // ── On completion, bump both users' completedTrades counters ──
        if (isCompletion) {
            try {
                val trade = db.collection("trades").document(tradeId).get().await()
                    .toObject(Trade::class.java)
                if (trade != null) {
                    listOf(trade.requesterId, trade.receiverId).forEach { email ->
                        db.collection("users")
                            .whereEqualTo("email", email)
                            .limit(1)
                            .get()
                            .await()
                            .documents
                            .firstOrNull()
                            ?.reference
                            ?.update("completedTrades", FieldValue.increment(1))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to increment completedTrades", e)
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    // AVAILABILITY
    // ═════════════════════════════════════════════════════════════

    /**
     * Non-throwing availability check.
     * A user is "unavailable" if any ACTIVE trade they're part of has an
     * effective schedule within ±30 min of [timestamp].
     */
    suspend fun isUserAvailable(userId: String, timestamp: Long): Boolean {
        if (userId.isBlank() || timestamp <= 0L) return true
        val window = 30L * 60 * 1000

        val activeStatuses = listOf(
            TradeStatus.PENDING.value,
            TradeStatus.COUNTER_PROPOSED.value,
            TradeStatus.ACCEPTED.value,
            TradeStatus.SCHEDULE_FINALIZED.value
        )

        return try {
            val asRequester = db.collection("trades")
                .whereEqualTo("requesterId", userId)
                .whereIn("status", activeStatuses)
                .get()
                .await()

            val asReceiver = db.collection("trades")
                .whereEqualTo("receiverId", userId)
                .whereIn("status", activeStatuses)
                .get()
                .await()

            val all = (asRequester.documents + asReceiver.documents).mapNotNull {
                it.toObject(Trade::class.java)
            }

            all.none { t ->
                val s = t.effectiveSchedule
                s > 0L && kotlin.math.abs(s - timestamp) < window
            }
        } catch (e: Exception) {
            Log.e(TAG, "isUserAvailable failed — defaulting to available", e)
            true // fail-open so a transient Firestore issue doesn't block trades
        }
    }

    // ═════════════════════════════════════════════════════════════
    // LIVE LISTENERS
    // ═════════════════════════════════════════════════════════════

    fun listenToTrade(
        tradeId: String,
        onUpdate: (Trade?) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration =
        db.collection("trades").document(tradeId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                // @DocumentId was removed from Trade.kt, so id must be set manually here.
                onUpdate(snap?.toObject(Trade::class.java)?.copy(id = tradeId))
            }

    // ═════════════════════════════════════════════════════════════
    // REMINDERS
    // ═════════════════════════════════════════════════════════════

    /**
     * Schedules the 24h and 1h WorkManager reminders for a trade
     * whose schedule has just been finalized.
     * Should be called from the Activity right after acceptTrade() or
     * acceptCounterSchedule() succeeds.
     */
    suspend fun scheduleRemindersAfterFinalize(context: Context, tradeId: String) {
        try {
            val doc = db.collection("trades").document(tradeId).get().await()
            // @DocumentId was removed from Trade.kt, so id must be set manually here.
            val trade = doc.toObject(Trade::class.java)?.copy(id = tradeId)
            if (trade == null) {
                Log.w(TAG, "Cannot schedule reminders — trade not found: $tradeId")
                return
            }
            MeetingReminderScheduler.scheduleForTrade(context.applicationContext, trade)
            Log.d(TAG, "Reminders scheduled for trade $tradeId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule reminders for $tradeId", e)
        }
    }

    /** Cancels pending reminders (call on cancel/reject). */
    fun cancelReminders(context: Context, tradeId: String) {
        try {
            MeetingReminderScheduler.cancelForTrade(context.applicationContext, tradeId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel reminders for $tradeId", e)
        }
    }

    // ═════════════════════════════════════════════════════════════
    // NOTIFICATIONS
    // ═════════════════════════════════════════════════════════════

    private fun sendTradeNotification(
        userId: String,
        title: String,
        message: String,
        type: String,
        tradeId: String
    ) {
        if (userId.isBlank()) return
        val payload = hashMapOf<String, Any>(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "type" to type,
            "tradeId" to tradeId,
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )
        db.collection("notifications").add(payload)
            .addOnFailureListener { e -> Log.e(TAG, "Notification write failed", e) }
    }
}