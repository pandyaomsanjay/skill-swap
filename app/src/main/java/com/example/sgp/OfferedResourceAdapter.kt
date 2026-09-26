package com.example.sgp

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class OfferedResourceAdapter(
    private val items: List<Skill>,
    private val onSelect: (Skill) -> Unit
) : RecyclerView.Adapter<OfferedResourceAdapter.VH>() {

    private var selectedId: String? = null

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val card: com.google.android.material.card.MaterialCardView = v.findViewById(R.id.cardOffered)
        val ivThumb: ImageView = v.findViewById(R.id.ivOfferedThumb)
        val tvTitle: TextView = v.findViewById(R.id.tvOfferedTitle)
        val tvType: TextView = v.findViewById(R.id.tvOfferedType)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_offered_resource, parent, false))

    override fun onBindViewHolder(holder: VH, position: Int) {
        val skill = items[position]
        holder.tvTitle.text = skill.title

        val type = if (skill.skillType == "playlist") TradeResourceType.COURSE
        else TradeResourceType.VIDEO
        holder.tvType.text = "${type.emoji} ${type.label}"

        val url = skill.thumbnailUrl ?: skill.videoUrl
        if (!url.isNullOrBlank()) {
            Glide.with(holder.itemView.context).load(url)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(holder.ivThumb)
        } else {
            holder.ivThumb.setImageResource(R.drawable.baseline_videocam_24)
        }

        val isSelected = selectedId == skill.id
        holder.card.strokeWidth = if (isSelected) 4 else 2
        holder.card.strokeColor = if (isSelected)
            ContextCompat.getColor(holder.itemView.context, R.color.brand_navy)
        else
            ContextCompat.getColor(holder.itemView.context, R.color.brand_tan)

        holder.itemView.setOnClickListener {
            selectedId = skill.id
            onSelect(skill)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = items.size
}