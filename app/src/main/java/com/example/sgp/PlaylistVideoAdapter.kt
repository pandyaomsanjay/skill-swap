package com.example.sgp

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class PlaylistVideoAdapter(
    private val videos: List<PlaylistVideo>,
    private val completedVideoIds: Set<String> = emptySet(),
    private val isOwner: Boolean = false,
    private val onVideoClick: (PlaylistVideo) -> Unit,
    private val onReportClick: (PlaylistVideo) -> Unit = {},
    private val onFeedbackClick: (PlaylistVideo) -> Unit = {},
    private val onBuyClick: (PlaylistVideo) -> Unit = {}
) : RecyclerView.Adapter<PlaylistVideoAdapter.ViewHolder>() {

    var isDarkMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** True when the user is the owner, admin, or has bought the full playlist. */
    var fullPlaylistOwned: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** IDs of individually purchased videos in this playlist. */
    var ownedVideoIds: Set<String> = emptySet()
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    // Light palette
    private val lightTextPrimary   = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightCardBg        = Color.parseColor("#FFFFFF")
    private val lightCardStroke    = Color.parseColor("#D2C1B6")
    private val lightCheck         = Color.parseColor("#2E9E63")
    private val lightBuyBg         = Color.parseColor("#7C3AED")
    private val lightWatchBg       = Color.parseColor("#2E9E63")
    private val lightPriceAccent   = Color.parseColor("#E58A2A")

    // Dark palette
    private val darkTextPrimary    = Color.parseColor("#F5F7F8")
    private val darkTextSecondary  = Color.parseColor("#8FA6B8")
    private val darkCardBg         = Color.parseColor("#16232E")
    private val darkCardStroke     = Color.parseColor("#2A3B48")
    private val darkCheck          = Color.parseColor("#4FD8BB")
    private val darkBuyBg          = Color.parseColor("#8B5CF6")
    private val darkWatchBg        = Color.parseColor("#3EA17A")
    private val darkPriceAccent    = Color.parseColor("#E8C468")

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrder: TextView = itemView.findViewById(R.id.tvVideoOrder)
        val tvTitle: TextView = itemView.findViewById(R.id.tvVideoTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvVideoDescription)
        val tvDuration: TextView = itemView.findViewById(R.id.tvVideoDuration)
        val ivCompleted: ImageView = itemView.findViewById(R.id.ivVideoCompleted)
        val btnReport: View = itemView.findViewById(R.id.btnReportVideo)
        val btnFeedback: View = itemView.findViewById(R.id.btnFeedbackVideo)
        val tvAccessStatus: TextView = itemView.findViewById(R.id.tvVideoAccessStatus)
        val btnBuyWatch: MaterialButton = itemView.findViewById(R.id.btnBuyWatch)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_video_row, parent, false)
        return ViewHolder(view)
    }

    /** Combines every ownership rule into one check used for display + clicks. */
    private fun canWatch(video: PlaylistVideo): Boolean {
        if (isOwner) return true
        if (fullPlaylistOwned) return true
        if (video.id in ownedVideoIds) return true
        if (video.credits <= 0) return true
        return false
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        val isCompleted = completedVideoIds.contains(video.id)

        val textPrimary   = if (isDarkMode) darkTextPrimary   else lightTextPrimary
        val textSecondary = if (isDarkMode) darkTextSecondary else lightTextSecondary
        val cardBg        = if (isDarkMode) darkCardBg        else lightCardBg
        val cardStroke    = if (isDarkMode) darkCardStroke    else lightCardStroke
        val checkColor    = if (isDarkMode) darkCheck         else lightCheck
        val buyBg         = if (isDarkMode) darkBuyBg         else lightBuyBg
        val watchBg       = if (isDarkMode) darkWatchBg       else lightWatchBg
        val priceAccent   = if (isDarkMode) darkPriceAccent   else lightPriceAccent

        // Card background / stroke
        (holder.itemView as? MaterialCardView)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Core info
        holder.tvTitle.text = video.title
        holder.tvTitle.setTextColor(textPrimary)

        holder.tvDescription.text = video.description
        holder.tvDescription.setTextColor(textSecondary)

        holder.tvDuration.text = video.duration.ifBlank { "--" }
        holder.tvDuration.setTextColor(textSecondary)

        // Order number / checkmark
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

        // Report / feedback hidden for owner
        if (isOwner) {
            holder.btnReport.visibility = View.GONE
            holder.btnFeedback.visibility = View.GONE
        } else {
            holder.btnReport.visibility = View.VISIBLE
            holder.btnReport.setOnClickListener { onReportClick(video) }
            holder.btnFeedback.visibility = View.VISIBLE
            holder.btnFeedback.setOnClickListener { onFeedbackClick(video) }
        }

        // ── Access row: status + Buy/Watch button ──
        val watchable = canWatch(video)

        when {
            isOwner -> {
                holder.tvAccessStatus.text = "✓ Your upload"
                holder.tvAccessStatus.setTextColor(checkColor)
            }
            fullPlaylistOwned -> {
                holder.tvAccessStatus.text = "✓ Owned (Playlist)"
                holder.tvAccessStatus.setTextColor(checkColor)
            }
            video.id in ownedVideoIds -> {
                holder.tvAccessStatus.text = "✓ Owned (Video)"
                holder.tvAccessStatus.setTextColor(checkColor)
            }
            video.credits <= 0 -> {
                holder.tvAccessStatus.text = "Free"
                holder.tvAccessStatus.setTextColor(checkColor)
            }
            else -> {
                holder.tvAccessStatus.text = "${video.credits} credits"
                holder.tvAccessStatus.setTextColor(priceAccent)
            }
        }

        if (watchable) {
            holder.btnBuyWatch.text = "▶ Watch"
            holder.btnBuyWatch.backgroundTintList = ColorStateList.valueOf(watchBg)
            holder.btnBuyWatch.setOnClickListener { onVideoClick(video) }
        } else {
            holder.btnBuyWatch.text = "🔓 Buy for ${video.credits}"
            holder.btnBuyWatch.backgroundTintList = ColorStateList.valueOf(buyBg)
            holder.btnBuyWatch.setOnClickListener { onBuyClick(video) }
        }

        // Row click mirrors the button action so any part of the row works.
        holder.itemView.setOnClickListener {
            if (watchable) onVideoClick(video) else onBuyClick(video)
        }
    }

    override fun getItemCount(): Int = videos.size
}
