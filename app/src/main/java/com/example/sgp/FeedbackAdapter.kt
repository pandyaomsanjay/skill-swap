package com.example.sgp

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FeedbackAdapter(
    private val items: MutableList<Feedback>,
    private val onViewFull: (Feedback) -> Unit,
    private val onMoreClick: (Feedback, View) -> Unit
) : RecyclerView.Adapter<FeedbackAdapter.ViewHolder>() {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val photo: android.widget.ImageView = itemView.findViewById(R.id.ivUserPhoto)
        val name: TextView = itemView.findViewById(R.id.tvUserName)
        val title: TextView = itemView.findViewById(R.id.tvFeedbackTitle)
        val preview: TextView = itemView.findViewById(R.id.tvPreview)
        val rating: android.widget.RatingBar = itemView.findViewById(R.id.ratingBar)
        val date: TextView = itemView.findViewById(R.id.tvDate)
        val status: TextView = itemView.findViewById(R.id.tvStatus)
        val menuButton: View = itemView.findViewById(R.id.btnMenu)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_feedback_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]

        holder.name.text = item.userName.ifBlank { "Unknown user" }
        holder.title.text = item.title.ifBlank { "(no title)" }
        holder.preview.text = item.message
        holder.rating.rating = item.rating.toFloat()
        holder.date.text = if (item.timestamp > 0) dateFormat.format(Date(item.timestamp)) else "—"

        if (item.userPhotoUrl.isNotBlank()) {
            Glide.with(holder.itemView.context)
                .load(item.userPhotoUrl)
                .centerCrop()
                .into(holder.photo)
        } else {
            Glide.with(holder.itemView.context).clear(holder.photo)
            holder.photo.setImageDrawable(null)
        }

        val (statusLabel, statusColor) = when (item.status) {
            FeedbackStatus.NEW.firestoreValue -> "New" to Color.parseColor("#10142A")
            FeedbackStatus.READ.firestoreValue -> "Read" to Color.parseColor("#8B94C4")
            FeedbackStatus.RESOLVED.firestoreValue -> "Resolved" to Color.parseColor("#4CAF50")
            else -> item.status.ifBlank { "New" } to Color.parseColor("#8B94C4")
        }
        holder.status.text = statusLabel
        holder.status.background.setTint(statusColor)

        holder.itemView.setOnClickListener { onViewFull(item) }

        // Just forwards to the Activity, which shows the dark bottom sheet —
        // same pattern as TradeAdapter.btnMoreOptions in AdminTradesActivity.
        holder.menuButton.setOnClickListener { anchor -> onMoreClick(item, anchor) }
    }

    override fun getItemCount() = items.size

    fun submitList(newItems: List<Feedback>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }
}