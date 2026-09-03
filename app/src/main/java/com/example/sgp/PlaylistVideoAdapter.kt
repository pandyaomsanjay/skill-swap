package com.example.sgp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistVideoAdapter(
    private val videos: List<PlaylistVideo>,
    // FEATURE 2: ids of videos this learner has completed. Defaults to empty so
    // existing call sites without progress data still compile.
    private val completedVideoIds: Set<String> = emptySet(),
    // Per-video report: hides the report icon for the playlist owner viewing
    // their own upload. Defaults to false so existing call sites still compile.
    private val isOwner: Boolean = false,
    private val onVideoClick: (PlaylistVideo) -> Unit,
    // Called when the report flag on a row is tapped. Defaults to a no-op so
    // existing call sites without a handler still compile.
    private val onReportClick: (PlaylistVideo) -> Unit = {},
    // Called when the feedback flag on a row is tapped. Defaults to a no-op so
    // existing call sites without a handler still compile.
    private val onFeedbackClick: (PlaylistVideo) -> Unit = {}
) : RecyclerView.Adapter<PlaylistVideoAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrder: TextView = itemView.findViewById(R.id.tvVideoOrder)
        val tvTitle: TextView = itemView.findViewById(R.id.tvVideoTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvVideoDescription)
        val tvDuration: TextView = itemView.findViewById(R.id.tvVideoDuration)
        // FEATURE 2: new checkmark icon, added to item_playlist_video_row.xml — see below.
        val ivCompleted: ImageView = itemView.findViewById(R.id.ivVideoCompleted)
        val btnReport: View = itemView.findViewById(R.id.btnReportVideo)
        // New: feedback icon, add to item_playlist_video_row.xml next to btnReportVideo.
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

        holder.tvTitle.text = video.title
        holder.tvDescription.text = video.description
        holder.tvDuration.text = video.duration.ifBlank { "--" }

        // FEATURE 2: swap the leading order number for a checkmark once completed,
        // so the learner can see exactly which videos are done.
        if (isCompleted) {
            holder.tvOrder.visibility = View.GONE
            holder.ivCompleted.visibility = View.VISIBLE
        } else {
            holder.tvOrder.visibility = View.VISIBLE
            holder.tvOrder.text = "${position + 1}."
            holder.ivCompleted.visibility = View.GONE
        }

        // Report + Feedback: available on every video except for the playlist
        // owner viewing their own upload.
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