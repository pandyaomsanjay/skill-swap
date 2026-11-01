package com.example.sgp

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules two push reminders per finalized trade:
 *   • 24h before the meeting
 *   • 1h  before the meeting
 *
 * Uses WorkManager so reminders survive app close.
 * Work is deduped by unique name — re-scheduling the same trade
 * replaces the previous work, so no duplicate reminders.
 *
 * POINTS trades fix: for trades where the requester offered points
 * instead of a resource, `offeredResourceTitle` is empty — the reminder
 * body used to read "...Requested: X • Offered: " with a trailing blank.
 * We now substitute "{N} points" in that case.
 */
object MeetingReminderScheduler {

    private const val TAG = "MeetingReminder"
    private const val TWENTY_FOUR_HOURS_MS = 24L * 60 * 60 * 1000
    private const val ONE_HOUR_MS = 60L * 60 * 1000

    fun scheduleForTrade(context: Context, trade: Trade) {
        val meetingAt = trade.finalizedSchedule
        if (meetingAt <= 0L) return

        val now = System.currentTimeMillis()
        val for24h = meetingAt - TWENTY_FOUR_HOURS_MS
        val for1h = meetingAt - ONE_HOUR_MS

        if (for24h > now) enqueue(context, trade, delayMs = for24h - now, window = "24h")
        if (for1h > now) enqueue(context, trade, delayMs = for1h - now, window = "1h")
    }

    fun cancelForTrade(context: Context, tradeId: String) {
        val wm = WorkManager.getInstance(context)
        wm.cancelUniqueWork("trade_reminder_${tradeId}_24h")
        wm.cancelUniqueWork("trade_reminder_${tradeId}_1h")
    }

    private fun enqueue(context: Context, trade: Trade, delayMs: Long, window: String) {
        // FIX: for POINTS trades, offeredResourceTitle is empty. Substitute
        // the point count so the reminder reads "Offered: 30 points" instead
        // of "Offered: ".
        val offeredDisplay =
            if (trade.isPointsExchange) "${trade.offeredPoints} points"
            else trade.offeredResourceTitle

        val input = Data.Builder()
            .putString(MeetingReminderWorker.KEY_TRADE_ID, trade.id)
            .putString(MeetingReminderWorker.KEY_REQUESTER_ID, trade.requesterId)
            .putString(MeetingReminderWorker.KEY_REQUESTER_NAME, trade.requesterName)
            .putString(MeetingReminderWorker.KEY_RECEIVER_ID, trade.receiverId)
            .putString(MeetingReminderWorker.KEY_RECEIVER_NAME, trade.receiverName)
            .putString(MeetingReminderWorker.KEY_REQUESTED_TITLE, trade.requestedResourceTitle)
            .putString(MeetingReminderWorker.KEY_OFFERED_TITLE, offeredDisplay)
            .putLong(MeetingReminderWorker.KEY_SCHEDULE, trade.finalizedSchedule)
            .putString(MeetingReminderWorker.KEY_WINDOW, window)
            .build()

        val request = OneTimeWorkRequestBuilder<MeetingReminderWorker>()
            .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
            .setInputData(input)
            .addTag("trade_reminder")
            .addTag("trade_reminder_${trade.id}")
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "trade_reminder_${trade.id}_$window",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }
}