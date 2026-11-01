package com.example.sgp

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TradeAdapter(
    private val trades: List<Trade>,
    private val currentUserEmail: String,
    private val isDarkMode: Boolean,
    private val onClick: (Trade) -> Unit
) : RecyclerView.Adapter<TradeAdapter.VH>() {

    private val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

    // Light palette
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")

    // Dark palette
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvPartner: TextView = v.findViewById(R.id.tvPartner)
        val tvSkills: TextView = v.findViewById(R.id.tvSkills)
        val tvStatus: TextView = v.findViewById(R.id.tvStatus)
        val tvSchedule: TextView? = v.findViewById(R.id.tvSchedule)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_trade, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val t = trades[position]
        val iAmRequester = t.requesterId.equals(currentUserEmail, true)
        val partner = if (iAmRequester) t.receiverName.ifBlank { t.receiverId }
        else t.requesterName.ifBlank { t.requesterId }

        // Apply theme to root (MaterialCardView or fall back to a drawable)
        if (holder.itemView is com.google.android.material.card.MaterialCardView) {
            val card = holder.itemView as com.google.android.material.card.MaterialCardView
            card.setCardBackgroundColor(if (isDarkMode) darkCardBg else lightCardBg)
            card.strokeColor = if (isDarkMode) darkCardStroke else lightCardStroke
        } else {
            holder.itemView.background = GradientDrawable().apply {
                cornerRadius = 16f * holder.itemView.resources.displayMetrics.density
                setColor(if (isDarkMode) darkCardBg else lightCardBg)
                setStroke(
                    (1 * holder.itemView.resources.displayMetrics.density).toInt(),
                    if (isDarkMode) darkCardStroke else lightCardStroke
                )
            }
        }

        val textPrimary = if (isDarkMode) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDarkMode) darkTextSecondary else lightTextSecondary

        // Partner + exchange-method badge appended inline.
        // Example: "With: Alex Johnson  · 💰 30 pts"
        //          "With: Alex Johnson  · 🎁 Material"
        val methodBadge = when {
            t.isPointsExchange -> "  · 💰 ${t.offeredPoints} pts"
            t.exchangeMethod.isNotBlank() -> "  · 🎁 Material"
            else -> "" // very old records with no exchangeMethod — no badge
        }
        holder.tvPartner.text = "With: $partner$methodBadge"
        holder.tvPartner.setTextColor(textPrimary)

        // Skills line — for points trades show the exchange direction clearly.
        holder.tvSkills.text = buildString {
            if (t.isPointsExchange) {
                append("💰 ${t.offeredPoints} pts")
            } else {
                append("${t.offeredResourceTypeEnum.emoji} ${t.offeredResourceTitle}")
            }
            append("  ⇄  ")
            append("${t.requestedResourceTypeEnum.emoji} ${t.requestedResourceTitle}")
        }
        holder.tvSkills.setTextColor(textPrimary)

        holder.tvStatus.text = t.statusEnum.name.replace('_', ' ')
        holder.tvStatus.setTextColor(when (t.statusEnum) {
            TradeStatus.PENDING -> Color.parseColor("#B8860B")
            TradeStatus.COUNTER_PROPOSED -> Color.parseColor("#7C3AED")
            TradeStatus.ACCEPTED, TradeStatus.SCHEDULE_FINALIZED -> Color.parseColor("#2563EB")
            TradeStatus.COMPLETED -> Color.parseColor("#2E9E63")
            TradeStatus.CANCELLED, TradeStatus.REJECTED -> Color.parseColor("#DC2626")
            TradeStatus.EXPIRED -> Color.parseColor("#9CA3AF")
        })

        holder.tvSchedule?.text = t.effectiveSchedule.takeIf { it > 0L }
            ?.let { "🗓 ${fmt.format(Date(it))}" } ?: "🗓 Not scheduled"
        holder.tvSchedule?.setTextColor(textSecondary)

        holder.itemView.setOnClickListener { onClick(t) }
    }

    override fun getItemCount() = trades.size
}