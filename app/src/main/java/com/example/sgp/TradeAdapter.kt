package com.example.sgp

import android.graphics.Color
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
    private val onClick: (Trade) -> Unit
) : RecyclerView.Adapter<TradeAdapter.VH>() {

    private val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.getDefault())

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

        holder.tvPartner.text = "With: $partner"
        holder.tvSkills.text =
            "${t.offeredResourceTypeEnum.emoji} ${t.offeredResourceTitle}  ⇄  ${t.requestedResourceTypeEnum.emoji} ${t.requestedResourceTitle}"

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

        holder.itemView.setOnClickListener { onClick(t) }
    }

    override fun getItemCount() = trades.size
}