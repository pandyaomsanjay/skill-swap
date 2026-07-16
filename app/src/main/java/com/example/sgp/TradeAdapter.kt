package com.example.sgp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TradeAdapter(
    private val trades: List<Trade>,
    private val onTradeClick: (Trade) -> Unit
) : RecyclerView.Adapter<TradeAdapter.TradeViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TradeViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_trade_video, parent, false)
        return TradeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TradeViewHolder, position: Int) {
        holder.bind(trades[position])
    }

    override fun getItemCount() = trades.size

    inner class TradeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
        private val tvName: TextView = itemView.findViewById(R.id.tvName)
        private val tvSkills: TextView = itemView.findViewById(R.id.tvSkills)
        private val ratingBar: RatingBar = itemView.findViewById(R.id.ratingBar)
        private val btnTrade: Button = itemView.findViewById(R.id.btnTrade)
        // Video placeholder – can be an ImageView or a VideoView
        private val ivVideoThumb: ImageView = itemView.findViewById(R.id.ivVideoThumb)

        fun bind(trade: Trade) {
            ivAvatar.setImageResource(trade.uploaderAvatar)
            tvName.text = trade.uploaderName
            tvSkills.text = "${trade.skillOffered} ↔ ${trade.skillRequested}"
            ratingBar.rating = trade.rating
            // For demo, set a placeholder drawable; in real app load thumbnail from videoUrl
            ivVideoThumb.setImageResource(R.drawable.baseline_videocam_24)

            btnTrade.setOnClickListener {
                onTradeClick(trade)
            }

            itemView.setOnClickListener {
                // Navigate to trade detail (optional)
            }
        }
    }
}
