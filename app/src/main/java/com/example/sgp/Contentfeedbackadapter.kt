package com.example.sgp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Row renderer for the "Content" tab in AdminFeedbackActivity — sourced from
 * the content_feedback collection (ratings/comments left on a video or
 * playlist), separate from the general-app Feedback rows this screen already
 * shows. Delete-only, no status workflow, per the task spec.
 *
 * Restyled to match the general Feedback row look (avatar, name, comment,
 * stars + meta) instead of the earlier flat card.
 */
class ContentFeedbackAdapter(
    private var items: MutableList<ContentFeedback>,
    private val skillTitleCache: Map<String, Pair<String, String>>, // skillId -> (title, uploaderName)
    private val onDelete: (ContentFeedback) -> Unit
) : RecyclerView.Adapter<ContentFeedbackAdapter.ViewHolder>() {

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
        val tvReporterName: TextView = itemView.findViewById(R.id.tvReporterName)
        val tvTarget: TextView = itemView.findViewById(R.id.tvTarget)
        val tvStars: TextView = itemView.findViewById(R.id.tvStars)
        val tvComment: TextView = itemView.findViewById(R.id.tvComment)
        val tvMeta: TextView = itemView.findViewById(R.id.tvMeta)
        val btnDelete: View = itemView.findViewById(R.id.btnDelete)
    }

    fun submitList(newItems: List<ContentFeedback>) {
        items = newItems.toMutableList()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_content_feedback, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cf = items[position]
        val (skillTitle, uploaderName) = skillTitleCache[cf.skillId] ?: ("Deleted content" to "")

        val displayName = cf.reporterName.ifBlank { cf.reporterId }
        holder.tvInitial.text = displayName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        holder.tvReporterName.text = displayName.ifBlank { "Unknown user" }

        val targetLabel = if (cf.videoId.isNotBlank()) "a video in \"$skillTitle\"" else "\"$skillTitle\""
        holder.tvTarget.text = "on $targetLabel"

        holder.tvComment.text = cf.comment.ifBlank { "(no comment)" }

        holder.tvStars.text = if (cf.rating > 0) "★".repeat(cf.rating) + "☆".repeat(5 - cf.rating) else "N/A"

        val dateStr = if (cf.timestamp > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(cf.timestamp))
        } else "—"
        holder.tvMeta.text = "by ${uploaderName.ifBlank { "Unknown" }} · $dateStr"

        holder.btnDelete.setOnClickListener { onDelete(cf) }
    }

    override fun getItemCount() = items.size
}