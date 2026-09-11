package com.example.sgp

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class PlaylistVideoAdapter(
    private val videos: List<PlaylistVideo>,
    private val completedVideoIds: Set<String> = emptySet(),
    private val isOwner: Boolean = false,
    private val onVideoClick: (PlaylistVideo) -> Unit,
    private val onReportClick: (PlaylistVideo) -> Unit = {},
    private val onFeedbackClick: (PlaylistVideo) -> Unit = {}
) : RecyclerView.Adapter<PlaylistVideoAdapter.ViewHolder>() {

    // ---------------------------------------------------------------------
    // Theme state — set from PlaylistActivity.applyTheme() and read on bind.
    // ---------------------------------------------------------------------
    var isDarkMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    // ---- Light palette ----
    private val lightTextPrimary   = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightCardBg        = Color.parseColor("#FFFFFF")
    private val lightCardStroke    = Color.parseColor("#D2C1B6")
    private val lightCheck         = Color.parseColor("#2E9E63")

    // ---- Dark palette ----
    private val darkTextPrimary    = Color.parseColor("#F5F7F8")
    private val darkTextSecondary  = Color.parseColor("#8FA6B8")
    private val darkCardBg         = Color.parseColor("#16232E")
    private val darkCardStroke     = Color.parseColor("#2A3B48")
    private val darkCheck          = Color.parseColor("#4FD8BB")

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrder: TextView = itemView.findViewById(R.id.tvVideoOrder)
        val tvTitle: TextView = itemView.findViewById(R.id.tvVideoTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvVideoDescription)
        val tvDuration: TextView = itemView.findViewById(R.id.tvVideoDuration)
        val ivCompleted: ImageView = itemView.findViewById(R.id.ivVideoCompleted)
        val btnReport: View = itemView.findViewById(R.id.btnReportVideo)
        val btnFeedback: View = itemView.findViewById(R.id.btnFeedbackVideo)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_video_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        val isCompleted = completedVideoIds.contains(video.id)

        // Pick the right palette for the current theme
        val textPrimary   = if (isDarkMode) darkTextPrimary   else lightTextPrimary
        val textSecondary = if (isDarkMode) darkTextSecondary else lightTextSecondary
        val cardBg        = if (isDarkMode) darkCardBg        else lightCardBg
        val cardStroke    = if (isDarkMode) darkCardStroke    else lightCardStroke
        val checkColor    = if (isDarkMode) darkCheck         else lightCheck

        // 1. Recolor the Card Background & Border (FIX for white cards)
        (holder.itemView as? MaterialCardView)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // 2. Recolor the Text
        holder.tvTitle.text = video.title
        holder.tvTitle.setTextColor(textPrimary)

        holder.tvDescription.text = video.description
        holder.tvDescription.setTextColor(textSecondary)

        holder.tvDuration.text = video.duration.ifBlank { "--" }
        holder.tvDuration.setTextColor(textSecondary)

        // 3. Handle the completed checkmark and order number
        if (isCompleted) {
            holder.tvOrder.visibility = View.GONE
            holder.ivCompleted.visibility = View.VISIBLE
            holder.ivCompleted.imageTintList = ColorStateList.valueOf(checkColor)
        } else {
            holder.tvOrder.visibility = View.VISIBLE
            holder.tvOrder.text = "${position + 1}."
            holder.tvOrder.setTextColor(textPrimary)
            holder.ivCompleted.visibility = View.GONE
        }

        // 4. Report + Feedback buttons
        if (isOwner) {
            holder.btnReport.visibility = View.GONE
            holder.btnFeedback.visibility = View.GONE
        } else {
            holder.btnReport.visibility = View.VISIBLE
            holder.btnReport.setOnClickListener { onReportClick(video) }
            holder.btnFeedback.visibility = View.VISIBLE
            holder.btnFeedback.setOnClickListener { onFeedbackClick(video) }
        }

        holder.itemView.setOnClickListener { onVideoClick(video) }
    }

    override fun getItemCount(): Int = videos.size
}