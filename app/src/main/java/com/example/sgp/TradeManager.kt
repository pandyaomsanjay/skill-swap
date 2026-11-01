package com.example.sgp

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.tasks.await

/**
 * Single source of truth for trade mutations, availability checks,
 * state transitions, notifications, and meeting-reminder scheduling.
 *
 * All writes that must be atomic are wrapped in Firestore transactions.
 *
 * Points / Material upgrade:
 *   • createTrade validates the exchange method and (softly) pre-checks balance
 *   • acceptTrade / acceptCounterSchedule set finalExchangeMethod + finalPoints
 *   • counterTrade replaces counterSchedule (schedule-only) with a full
 *     points-or-material counter
 *   • updateStatus transfers points atomically inside the completion transaction
 *   • updateStatus also cancels meeting reminders on completion (bug fix)
 *
 * Backward compat: legacy trades with no exchangeMethod field decode to
 * MATERIAL and skip the point-transfer path entirely.
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
     *   • POINTS offers that exceed the requester's current balance
     */
    suspend fun createTrade(trade: Trade): Result<String> = runCatching {
        require(trade.requesterId.isNotBlank()) { "Missing requester" }
        require(trade.receiverId.isNotBlank()) { "Missing receiver" }
        require(!trade.requesterId.equals(trade.receiverId, ignoreCase = true)) {
            "You cannot trade with your own resource"
        }
        require(trade.requestedResourceId.isNotBlank()) { "Missing requested resource" }
        require(trade.proposedSchedule > 0L) { "Please pick a date and time" }
        require(trade.proposedSchedule > System.currentTimeMillis()) {
            "Scheduled time must be in the future"
        }
        require(isUserAvailable(trade.requesterId, trade.proposedSchedule)) {
            "You already have another trade at that time"
        }

        // Validate the exchange side (POINTS vs MATERIAL)
        TradeValidator.validateExchangeMethod(trade)?.let { throw IllegalStateException(it) }

        // Soft balance pre-check for POINTS offers. The authoritative check
        // still happens at completion time inside the transaction.
        if (trade.exchangeMethod == ExchangeMethod.POINTS.value && trade.offeredPoints > 0) {
            val requesterSnap = db.collection("users")
                .whereEqualTo("email", trade.requesterId)
                .limit(1)
                .get()
                .await()
            val credits = requesterSnap.documents.firstOrNull()
                ?.getLong("credits")?.toInt() ?: 0
            require(credits >= trade.offeredPoints) {
                "You don't have enough points for this offer (you have $credits)"
            }
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

        // Legacy mirrors: keep admin screens and old adapters working.
        val legacyOfferedText =
            if (trade.exchangeMethod == ExchangeMethod.POINTS.value)
                "${trade.offeredPoints} points"
            else trade.offeredResourceTitle

        val payload = trade.copy(
            id = docRef.id,
            status = TradeStatus.PENDING.value,
            createdAt = java.util.Date(now),
            updatedAt = java.util.Date(now),
            requesterSkill = legacyOfferedText,
            receiverSkill = trade.requestedResourceTitle,
            skillOffered = legacyOfferedText,
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
    // ACCEPT (finalizes the ORIGINAL offer's exchange method)
    // ═════════════════════════════════════════════════════════════

    /**
     * Receiver accepts the trade. Finalizes the schedule AND the exchange
     * method/points based on the requester's original offer.
     * Notifies the requester.
     *
     * Caller should follow with scheduleRemindersAfterFinalize(context, tradeId).
     */
    suspend fun acceptTrade(tradeId: String, currentUserEmail: String): Result<Unit> = runCatching {
        val snap = db.collection("trades").document(tradeId).get().await()
        require(snap.exists()) { "Trade not found" }
        val preTrade = snap.toObject(Trade::class.java)
            ?: throw IllegalStateException("Invalid trade")
        val schedule = if (preTrade.counterSchedule > 0L)
            preTrade.counterSchedule else preTrade.proposedSchedule
        require(schedule > System.currentTimeMillis()) { "Scheduled time has already passed" }
        require(isUserAvailable(preTrade.requesterId, schedule)) {
            "The requester has a conflict at that time"
        }
        require(isUserAvailable(preTrade.receiverId, schedule)) {
            "You already have a trade at that time"
        }

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

            // NEW: finalize the exchange based on the ORIGINAL offer
            val finalMethod = when {
                trade.isPointsExchange -> ExchangeMethod.POINTS.value
                else -> ExchangeMethod.MATERIAL.value
            }
            val finalPts = if (trade.isPointsExchange) trade.offeredPoints else 0

            val now = System.currentTimeMillis()
            tx.update(
                ref,
                mapOf(
                    "status" to TradeStatus.SCHEDULE_FINALIZED.value,
                    "finalizedSchedule" to schedule,
                    "finalExchangeMethod" to finalMethod,
                    "finalPoints" to finalPts,
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
    // COUNTER (points OR material, plus optional schedule)
    // ═════════════════════════════════════════════════════════════

    /**
     * Receiver proposes a counter-offer. Can change:
     *   • the exchange method (POINTS or MATERIAL)
     *   • the points (if POINTS)
     *   • the resources on either side (if MATERIAL)
     *   • the schedule (if `newSchedule > 0`)
     *   • an optional message
     *
     * Exactly ONE counter is allowed per trade.
     */
    suspend fun counterTrade(
        tradeId: String,
        currentUserEmail: String,
        newSchedule: Long,
        counterExchangeMethod: String,
        counterPoints: Int = 0,
        counterRequestedResourceId: String = "",
        counterRequestedResourceType: String = "",
        counterRequestedResourceTitle: String = "",
        counterOfferedResourceId: String = "",
        counterOfferedResourceType: String = "",
        counterOfferedResourceTitle: String = "",
        message: String = ""
    ): Result<Unit> = runCatching {
        // Pre-validate the counter payload
        TradeValidator.validateCounterOffer(
            counterExchangeMethod, counterPoints,
            counterRequestedResourceId, counterOfferedResourceId
        )?.let { throw IllegalArgumentException(it) }

        if (newSchedule > 0) {
            require(newSchedule > System.currentTimeMillis()) {
                "Counter schedule must be in the future"
            }
            require(isUserAvailable(currentUserEmail, newSchedule)) {
                "You already have a trade at that time"
            }
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
            require(trade.counterSchedule == 0L && trade.counterExchangeMethod.isBlank()) {
                "A counter-offer has already been sent"
            }

            requesterEmail = trade.requesterId
            receiverName = trade.receiverName

            val now = System.currentTimeMillis()
            val updates = mutableMapOf<String, Any>(
                "status" to TradeStatus.COUNTER_PROPOSED.value,
                "respondedAt" to now,
                "updatedAt" to Timestamp(now / 1000, 0),
                "counterExchangeMethod" to counterExchangeMethod
            )
            if (newSchedule > 0) updates["counterSchedule"] = newSchedule
            if (message.isNotBlank()) updates["counterMessage"] = message

            if (counterExchangeMethod == ExchangeMethod.POINTS.value) {
                updates["counterOfferedPoints"] = counterPoints
            } else {
                updates["counterRequestedResourceId"] = counterRequestedResourceId
                updates["counterRequestedResourceType"] = counterRequestedResourceType
                updates["counterRequestedResourceTitle"] = counterRequestedResourceTitle
                updates["counterOfferedResourceId"] = counterOfferedResourceId
                updates["counterOfferedResourceType"] = counterOfferedResourceType
                updates["counterOfferedResourceTitle"] = counterOfferedResourceTitle
            }

            tx.update(ref, updates)
        }.await()

        sendTradeNotification(
            userId = requesterEmail,
            title = "Counter Offer Received",
            message = "${receiverName.ifBlank { "The receiver" }} proposed a counter offer.",
            type = "trade_counter",
            tradeId = tradeId
        )
    }

    /**
     * @deprecated Kept for backward compat with any external caller.
     * Use [counterTrade] which supports points/material in addition to
     * schedule changes. This wrapper delegates to it with the original
     * offer's exchange method so the counter is still a valid round-trip.
     */
    @Deprecated("Use counterTrade()")
    suspend fun counterSchedule(
        tradeId: String,
        newSchedule: Long,
        optionalMessage: String,
        currentUserEmail: String
    ): Result<Unit> = runCatching {
        val trade = db.collection("trades").document(tradeId).get().await()
            .toObject(Trade::class.java) ?: throw IllegalStateException("Trade not found")
        counterTrade(
            tradeId = tradeId,
            currentUserEmail = currentUserEmail,
            newSchedule = newSchedule,
            counterExchangeMethod = trade.exchangeMethod,
            counterPoints = trade.offeredPoints,
            counterRequestedResourceId = trade.requestedResourceId,
            counterRequestedResourceType = trade.requestedResourceType,
            counterRequestedResourceTitle = trade.requestedResourceTitle,
            counterOfferedResourceId = trade.offeredResourceId,
            counterOfferedResourceType = trade.offeredResourceType,
            counterOfferedResourceTitle = trade.offeredResourceTitle,
            message = optionalMessage
        )
    }

    // ═════════════════════════════════════════════════════════════
    // ACCEPT COUNTER (finalizes the COUNTER's exchange method)
    // ═════════════════════════════════════════════════════════════

    /**
     * Requester accepts the receiver's counter-offer. Finalizes the schedule
     * AND the exchange method/points based on the COUNTER.
     * Caller should follow with scheduleRemindersAfterFinalize(context, tradeId).
     */
    suspend fun acceptCounterSchedule(
        tradeId: String,
        currentUserEmail: String
    ): Result<Unit> = runCatching {
        val snap = db.collection("trades").document(tradeId).get().await()
        require(snap.exists()) { "Trade not found" }
        val preTrade = snap.toObject(Trade::class.java)
            ?: throw IllegalStateException("Invalid trade")
        val schedule = if (preTrade.counterSchedule > 0L)
            preTrade.counterSchedule else preTrade.proposedSchedule
        require(schedule > System.currentTimeMillis()) { "Counter time has already passed" }
        require(isUserAvailable(preTrade.requesterId, schedule)) {
            "You have a conflict at that time"
        }
        require(isUserAvailable(preTrade.receiverId, schedule)) {
            "The receiver has a conflict at that time"
        }

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

            // NEW: finalize based on the COUNTER's exchange method
            val finalMethod = if (trade.isCounterPoints)
                ExchangeMethod.POINTS.value else ExchangeMethod.MATERIAL.value
            val finalPts = if (trade.isCounterPoints) trade.counterOfferedPoints else 0

            val now = System.currentTimeMillis()
            tx.update(
                ref,
                mapOf(
                    "status" to TradeStatus.SCHEDULE_FINALIZED.value,
                    "finalizedSchedule" to schedule,
                    "finalExchangeMethod" to finalMethod,
                    "finalPoints" to finalPts,
                    "respondedAt" to now,
                    "updatedAt" to Timestamp(now / 1000, 0)
                )
            )
        }.await()

        sendTradeNotification(
            userId = receiverEmail,
            title = "Trade Scheduled",
            message = "Your skill trade is confirmed. The counter-offer was accepted.",
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

    /**
     * Atomic state transition. On COMPLETED with agreedPoints > 0, transfers
     * the points in the SAME transaction as the status update — insufficient
     * balance aborts the whole thing (the trade stays SCHEDULE_FINALIZED).
     * Cancels the WorkManager meeting reminders on completion.
     */
    private suspend fun updateStatus(
        tradeId: String,
        currentUserEmail: String,
        newStatus: TradeStatus,
        reason: String,
        allowFrom: List<TradeStatus>,
        notifyCounterpart: Boolean,
        isCompletion: Boolean = false
    ): Result<Unit> = runCatching {

        // ── Pre-resolve user refs when we might transfer points ──
        var requesterRef: DocumentReference? = null
        var receiverRef: DocumentReference? = null
        if (isCompletion) {
            val pre = db.collection("trades").document(tradeId).get().await()
                .toObject(Trade::class.java)
            if (pre != null && pre.agreedPoints > 0) {
                requesterRef = db.collection("users")
                    .whereEqualTo("email", pre.requesterId).limit(1).get().await()
                    .documents.firstOrNull()?.reference
                receiverRef = db.collection("users")
                    .whereEqualTo("email", pre.receiverId).limit(1).get().await()
                    .documents.firstOrNull()?.reference
                if (requesterRef == null || receiverRef == null) {
                    throw IllegalStateException(
                        "Unable to resolve user references for point transfer"
                    )
                }
            }
        }

        var counterpartEmail = ""
        var actorName = ""
        var transferApplied = false
        var transferPoints = 0

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

            // ── Atomic point transfer (only on completion) ──
            if (newStatus == TradeStatus.COMPLETED && trade.agreedPoints > 0) {
                val rr = requesterRef ?: throw IllegalStateException("Requester ref missing")
                val rc = receiverRef ?: throw IllegalStateException("Receiver ref missing")

                // All reads BEFORE any writes
                val requesterCredits = tx.get(rr).getLong("credits")?.toInt() ?: 0
                if (requesterCredits < trade.agreedPoints) {
                    throw IllegalStateException(
                        "Requester has insufficient credits for this trade"
                    )
                }
                val receiverCredits = tx.get(rc).getLong("credits")?.toInt() ?: 0

                // Writes
                tx.update(rr, "credits", requesterCredits - trade.agreedPoints)
                tx.update(rc, "credits", receiverCredits + trade.agreedPoints)

                transferApplied = true
                transferPoints = trade.agreedPoints
            }

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
                else -> { /* no extra fields */ }
            }

            tx.update(ref, update)
        }.await()

        // ── Post-transaction side-effects (best-effort) ──

        if (transferApplied) {
            try {
                val trade = db.collection("trades").document(tradeId).get().await()
                    .toObject(Trade::class.java)
                if (trade != null) {
                    PointsLedger.recordSpent(
                        userId = trade.requesterId,
                        points = transferPoints,
                        title = "Paid ${transferPoints} pts for: ${trade.requestedResourceTitle}",
                        refId = tradeId
                    )
                    PointsLedger.recordEarned(
                        userId = trade.receiverId,
                        points = transferPoints,
                        title = "Received ${transferPoints} pts for: ${trade.requestedResourceTitle}",
                        refId = tradeId
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Ledger write failed for trade $tradeId", e)
            }
        }

        if (isCompletion) {
            // Bump counters (unchanged existing behaviour)
            try {
                val trade = db.collection("trades").document(tradeId).get().await()
                    .toObject(Trade::class.java)
                if (trade != null) {
                    listOf(trade.requesterId, trade.receiverId).forEach { email ->
                        db.collection("users")
                            .whereEqualTo("email", email).limit(1).get().await()
                            .documents.firstOrNull()?.reference
                            ?.update("completedTrades", FieldValue.increment(1))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to bump completedTrades for $tradeId", e)
            }

            // NEW: cancel meeting reminders on completion (fixes existing bug)
            cancelRemindersForTrade(tradeId)
        }

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
                    "The skill trade has been cancelled." +
                            if (reason.isNotBlank()) " Reason: $reason" else ""
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
                .get().await()
            val asReceiver = db.collection("trades")
                .whereEqualTo("receiverId", userId)
                .whereIn("status", activeStatuses)
                .get().await()

            val all = (asRequester.documents + asReceiver.documents).mapNotNull {
                it.toObject(Trade::class.java)
            }

            all.none { t ->
                val s = t.effectiveSchedule
                s > 0L && kotlin.math.abs(s - timestamp) < window
            }
        } catch (e: Exception) {
            Log.e(TAG, "isUserAvailable failed — defaulting to available", e)
            true // fail-open
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
                onUpdate(snap?.toObject(Trade::class.java)?.copy(id = tradeId))
            }

    // ═════════════════════════════════════════════════════════════
    // REMINDERS
    // ═════════════════════════════════════════════════════════════

    /**
     * Schedules the 24h and 1h WorkManager reminders for a trade
     * whose schedule has just been finalized.
     */
    suspend fun scheduleRemindersAfterFinalize(context: android.content.Context, tradeId: String) {
        try {
            val doc = db.collection("trades").document(tradeId).get().await()
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
    fun cancelReminders(context: android.content.Context, tradeId: String) {
        try {
            MeetingReminderScheduler.cancelForTrade(context.applicationContext, tradeId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel reminders for $tradeId", e)
        }
    }

    /**
     * Context-free reminder cancellation for internal use (e.g. on completion).
     * Uses the AppContext singleton; if it wasn't initialized, this is a no-op
     * (the reminder worker checks trade status and skips non-finalized trades
     * anyway, so no notification is ever shown).
     */
    private fun cancelRemindersForTrade(tradeId: String) {
        try {
            val ctx = MyApp.getInstance()?.applicationContext
            if (ctx != null) {
                MeetingReminderScheduler.cancelForTrade(ctx, tradeId)
                Log.d(TAG, "Reminders cancelled for trade $tradeId")
            } else {
                Log.w(TAG, "MyApp instance not initialized — skipping reminder cancel for $tradeId")
            }
        } catch (e: Exception) {
            Log.e(TAG, "cancelRemindersForTrade failed for $tradeId", e)
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