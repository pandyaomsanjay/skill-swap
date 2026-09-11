package com.example.sgp

import android.content.res.ColorStateList
import android.graphics.Color
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

    // ---------------------------------------------------------------------
    // Theme state — set from the hosting activity (AddSkill / EditPlaylist /
    // wherever this is used) so the row text can flip with the app theme.
    // Defaults to light so the adapter works even before the flag is pushed.
    // ---------------------------------------------------------------------
    var isDarkMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    // ---- Light palette ----
    private val lightTextPrimary   = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightTextAccent    = Color.parseColor("#E58A2A")
    private val lightDeleteTint    = Color.parseColor("#C0392B")

    // ---- Dark palette ----
    private val darkTextPrimary    = Color.parseColor("#F5F7F8")
    private val darkTextSecondary  = Color.parseColor("#8FA6B8")
    private val darkTextAccent     = Color.parseColor("#E8C468")
    private val darkDeleteTint     = Color.parseColor("#FF6961")

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

        val textPrimary   = if (isDarkMode) darkTextPrimary   else lightTextPrimary
        val textSecondary = if (isDarkMode) darkTextSecondary else lightTextSecondary
        val textAccent    = if (isDarkMode) darkTextAccent    else lightTextAccent
        val deleteTint    = if (isDarkMode) darkDeleteTint    else lightDeleteTint

        holder.tvTitle.text = video.title
        holder.tvTitle.setTextColor(textPrimary)

        holder.tvDuration.text = video.duration.ifBlank { "--" }
        holder.tvDuration.setTextColor(textSecondary)

        holder.tvCredits.text = "${video.credits} credits"
        holder.tvCredits.setTextColor(textAccent)

        // Placeholder until thumbnails are wired up
        holder.ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)

        holder.btnDelete.imageTintList = ColorStateList.valueOf(deleteTint)
        holder.btnDelete.setOnClickListener { onDelete(position) }
    }

    override fun getItemCount(): Int = videos.size
}