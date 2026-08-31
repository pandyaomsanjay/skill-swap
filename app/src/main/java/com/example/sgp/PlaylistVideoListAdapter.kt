package com.example.sgp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistVideoListAdapter(
    private val videos: List<PlaylistVideo>,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<PlaylistVideoListAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
        val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
        val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
        val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
        val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_video_list, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.tvTitle.text = video.title
        holder.tvDuration.text = video.duration.ifBlank { "--" }
        holder.tvCredits.text = "${video.credits} credits"

        // Optionally load thumbnail if video.videoUrl is available (placeholder for now)
        holder.ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)

        holder.btnDelete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount(): Int = videos.size
}