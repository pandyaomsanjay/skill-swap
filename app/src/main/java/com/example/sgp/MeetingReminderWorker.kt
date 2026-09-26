package com.example.sgp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MeetingReminderWorker(
    context: Context,
    params: WorkerParameters
) : Worker(context, params) {

    companion object {
        const val KEY_TRADE_ID = "tradeId"
        const val KEY_REQUESTER_ID = "requesterId"
        const val KEY_REQUESTER_NAME = "requesterName"
        const val KEY_RECEIVER_ID = "receiverId"
        const val KEY_RECEIVER_NAME = "receiverName"
        const val KEY_REQUESTED_TITLE = "requestedTitle"
        const val KEY_OFFERED_TITLE = "offeredTitle"
        const val KEY_SCHEDULE = "schedule"
        const val KEY_WINDOW = "window"

        private const val CHANNEL_ID = "trade_reminders"
        private const val CHANNEL_NAME = "Trade Reminders"
    }

    private val db = FirebaseFirestore.getInstance()

    override fun doWork(): Result {
        val tradeId = inputData.getString(KEY_TRADE_ID) ?: return Result.failure()
        val requesterId = inputData.getString(KEY_REQUESTER_ID).orEmpty()
        val requesterName = inputData.getString(KEY_REQUESTER_NAME).orEmpty()
        val receiverId = inputData.getString(KEY_RECEIVER_ID).orEmpty()
        val receiverName = inputData.getString(KEY_RECEIVER_NAME).orEmpty()
        val requestedTitle = inputData.getString(KEY_REQUESTED_TITLE).orEmpty()
        val offeredTitle = inputData.getString(KEY_OFFERED_TITLE).orEmpty()
        val schedule = inputData.getLong(KEY_SCHEDULE, 0L)
        val window = inputData.getString(KEY_WINDOW) ?: "1h"

        if (schedule <= 0L || schedule <= System.currentTimeMillis()) return Result.success()

        // Confirm the trade is still active before firing reminders
        return try {
            val snap = Tasks.await(db.collection("trades").document(tradeId).get())
            val status = snap.getString("status").orEmpty()
            if (status != TradeStatus.SCHEDULE_FINALIZED.value &&
                status != TradeStatus.ACCEPTED.value) {
                return Result.success()
            }

            val dateFmt = SimpleDateFormat("d MMM yyyy 'at' h:mm a", Locale.getDefault())
            val whenLabel = dateFmt.format(Date(schedule))
            val windowLabel = if (window == "24h") "tomorrow" else "in 1 hour"

            // Notify requester
            if (requesterId.isNotBlank()) {
                postSystemNotification(
                    userId = requesterId,
                    title = "🔔 SkillSwap Reminder",
                    body = "Your skill trade with ${receiverName.ifBlank { "your partner" }} is $windowLabel — $whenLabel."
                )
                writeFirestoreNotification(
                    userId = requesterId,
                    title = "Trade Reminder",
                    message = "Session $windowLabel: $whenLabel. Requested: $requestedTitle • Offered: $offeredTitle"
                )
            }
            // Notify receiver
            if (receiverId.isNotBlank()) {
                postSystemNotification(
                    userId = receiverId,
                    title = "🔔 SkillSwap Reminder",
                    body = "Your skill trade with ${requesterName.ifBlank { "your partner" }} is $windowLabel — $whenLabel."
                )
                writeFirestoreNotification(
                    userId = receiverId,
                    title = "Trade Reminder",
                    message = "Session $windowLabel: $whenLabel. Requested: $requestedTitle • Offered: $offeredTitle"
                )
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private fun postSystemNotification(userId: String, title: String, body: String) {
        val ctx = applicationContext
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(ch)
        }

        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_notifications_24)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        // Use a stable id so re-scheduling doesn't stack duplicate notifications
        nm.notify((userId + title).hashCode(), notif)
    }

    private fun writeFirestoreNotification(userId: String, title: String, message: String) {
        val payload = hashMapOf<String, Any>(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "type" to "trade_reminder",
            "timestamp" to System.currentTimeMillis(),
            "read" to false
        )
        db.collection("notifications").add(payload)
    }
}