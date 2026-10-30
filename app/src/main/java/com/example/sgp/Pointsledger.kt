package com.example.sgp

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

/**
 * One row in the points history. Stored in Firestore collection "transactions".
 * amount > 0  => points earned
 * amount < 0  => points spent
 */
data class PointTransaction(
    val id: String = "",
    val userId: String = "",      // user's email (same id Home uses for trades)
    val amount: Int = 0,
    val title: String = "",
    val type: String = "",        // "EARNED" | "SPENT" | "BONUS"
    val tradeId: String = "",
    val timestamp: Long = 0L
)

object PointsLedger {

    private const val TAG = "PointsLedger"

    /**
     * Call this every time a user's points change.
     *
     * If tradeId is given, the document id is deterministic, so calling this twice
     * for the same trade (e.g. the user taps "Complete" twice) will NOT create duplicates.
     * Leave tradeId blank for things that can repeat (each call creates a new row).
     *
     * Results are logged under the tag "PointsLedger" (filter Logcat by it).
     */
    fun record(
        userId: String,
        amount: Int,
        title: String,
        tradeId: String = "",
        type: String = if (amount >= 0) "EARNED" else "SPENT"
    ) {
        Log.d(TAG, "record() called: user=$userId amount=$amount title=$title tradeId=$tradeId")

        if (userId.isBlank() || amount == 0) {
            Log.w(TAG, "Skipped: userId blank or amount is 0")
            return
        }

        val data = hashMapOf(
            "userId" to userId,
            "amount" to amount,
            "title" to title,
            "type" to type,
            "tradeId" to tradeId,
            "timestamp" to System.currentTimeMillis()
        )

        val col = FirebaseFirestore.getInstance().collection("transactions")
        val task = if (tradeId.isNotBlank()) {
            col.document("${tradeId}_${userId}_$type").set(data)
        } else {
            col.add(data)
        }

        task.addOnSuccessListener { Log.d(TAG, "SAVED: $title ($amount)") }
            .addOnFailureListener { Log.e(TAG, "FAILED: ${it.message}", it) }
    }

    /** Shortcut for spending points, e.g. PointsLedger.recordSpent(email, 10, "Bought playlist: Jazz") */
    fun recordSpent(userId: String, points: Int, title: String, refId: String = "") =
        record(userId, -kotlin.math.abs(points), title, refId, "SPENT")

    /** Shortcut for earning points. */
    fun recordEarned(userId: String, points: Int, title: String, refId: String = "") =
        record(userId, kotlin.math.abs(points), title, refId, "EARNED")
}