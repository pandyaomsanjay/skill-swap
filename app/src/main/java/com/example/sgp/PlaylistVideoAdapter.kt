package com.example.sgp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class PlaylistVideoAdapter(
    private val videos: List<PlaylistVideo>,
    private val onVideoClick: (PlaylistVideo) -> Unit
) : RecyclerView.Adapter<PlaylistVideoAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvOrder: TextView = itemView.findViewById(R.id.tvVideoOrder)
        val tvTitle: TextView = itemView.findViewById(R.id.tvVideoTitle)
        val tvDescription: TextView = itemView.findViewById(R.id.tvVideoDescription)
        val tvDuration: TextView = itemView.findViewById(R.id.tvVideoDuration)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_playlist_video_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val video = videos[position]
        holder.tvOrder.text = "${position + 1}."
        holder.tvTitle.text = video.title
        holder.tvDescription.text = video.description
        holder.tvDuration.text = video.duration.ifBlank { "--" }

        holder.itemView.setOnClickListener { onVideoClick(video) }
    }

    override fun getItemCount(): Int = videos.size
}
