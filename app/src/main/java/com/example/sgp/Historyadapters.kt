package com.example.sgp

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ---------------------------------------------------------------------
// 1. Palette (Matches ExploreActivity dark mode exactly)
// ---------------------------------------------------------------------
data class HistoryPalette(
    val screenBg: Int, val cardBg: Int, val cardStroke: Int, val textPrimary: Int,
    val textSecondary: Int, val headerBg: Int, val accent: Int, val onAccent: Int,
    val positive: Int, val warning: Int, val negative: Int
)

fun historyPalette(isDark: Boolean): HistoryPalette =
    if (isDark) {
        HistoryPalette(
            screenBg = Color.parseColor("#0F1B24"),
            cardBg = Color.parseColor("#16232E"),
            cardStroke = Color.parseColor("#2A3B48"),
            textPrimary = Color.parseColor("#F5F7F8"),
            textSecondary = Color.parseColor("#8FA6B8"),
            headerBg = Color.parseColor("#0B1620"),
            accent = Color.parseColor("#3E6A8A"),
            onAccent = Color.WHITE,
            positive = Color.parseColor("#66BB6A"),
            warning = Color.parseColor("#FFB74D"),
            negative = Color.parseColor("#EF5350")
        )
    } else {
        HistoryPalette(
            screenBg = Color.parseColor("#EAF1F5"),
            cardBg = Color.parseColor("#FFFFFF"),
            cardStroke = Color.parseColor("#D2C1B6"),
            textPrimary = Color.parseColor("#1B3C53"),
            textSecondary = Color.parseColor("#456882"),
            headerBg = Color.parseColor("#1B3C53"),
            accent = Color.parseColor("#1B3C53"),
            onAccent = Color.parseColor("#F9F3EF"),
            positive = Color.parseColor("#2E7D32"),
            warning = Color.parseColor("#B26A00"),
            negative = Color.parseColor("#C62828")
        )
    }

// ---------------------------------------------------------------------
// 2. Swap State Helper
// ---------------------------------------------------------------------
enum class SwapState { COMPLETED, PENDING, CANCELLED }

fun Trade.swapState(): SwapState {
    val s = status.trim().lowercase()
    return when {
        s.contains("complet") || s.contains("done") || s.contains("finish") -> SwapState.COMPLETED
        s.contains("cancel") || s.contains("reject") || s.contains("declin") -> SwapState.CANCELLED
        else -> SwapState.PENDING
    }
}

// ---------------------------------------------------------------------
// 3. Swaps Adapter
// ---------------------------------------------------------------------
class SwapHistoryAdapter(private val onClick: (Trade) -> Unit) : RecyclerView.Adapter<SwapHistoryAdapter.VH>() {

    private var items: List<Trade> = emptyList()
    private var p: HistoryPalette = historyPalette(false)
    private var userId: String = ""
    private val fmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

    fun submit(list: List<Trade>, palette: HistoryPalette, currentUserId: String) {
        items = list; p = palette; userId = currentUserId
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val icon: ImageView = view.findViewById(R.id.ivHsIcon)
        val partner: TextView = view.findViewById(R.id.tvHsPartner)
        val detail: TextView = view.findViewById(R.id.tvHsDetail)
        val time: TextView = view.findViewById(R.id.tvHsTime)
        val rating: TextView = view.findViewById(R.id.tvHsRating)
        val status: TextView = view.findViewById(R.id.tvHsStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history_swap, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = items[position]
        val iAmRequester = t.requesterId == userId
        val other = (if (iAmRequester) t.receiverName else t.requesterName).ifBlank { "Unknown user" }
        val mine = if (iAmRequester) t.requesterSkill else t.receiverSkill
        val theirs = if (iAmRequester) t.receiverSkill else t.requesterSkill

        h.card.setCardBackgroundColor(p.cardBg)
        h.card.strokeColor = p.cardStroke
        h.icon.imageTintList = ColorStateList.valueOf(p.textSecondary)
        h.icon.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ColorUtils.setAlphaComponent(p.textSecondary, 38)) }

        h.partner.text = other
        h.partner.setTextColor(p.textPrimary)
        h.detail.text = "$mine ↔ $theirs"
        h.detail.setTextColor(p.textSecondary)
        h.time.text = if (t.timestamp > 0) fmt.format(Date(t.timestamp)) else ""
        h.time.setTextColor(p.textSecondary)

        if (t.rating > 0f) {
            h.rating.visibility = View.VISIBLE
            h.rating.text = "★ ${String.format(Locale.getDefault(), "%.1f", t.rating.toDouble())}"
            h.rating.setTextColor(p.warning)
        } else h.rating.visibility = View.GONE

        val state = t.swapState()
        val (label, color) = when (state) {
            SwapState.COMPLETED -> "Completed" to p.positive
            SwapState.CANCELLED -> "Cancelled" to p.negative
            SwapState.PENDING -> "Pending" to p.warning
        }
        h.status.text = label
        h.status.setTextColor(color)
        h.status.background = GradientDrawable().apply { cornerRadius = 100f; setColor(ColorUtils.setAlphaComponent(color, 38)) }

        h.card.setOnClickListener { onClick(t) }
    }
}

// ---------------------------------------------------------------------
// 4. Points Adapter
// ---------------------------------------------------------------------
class PointsHistoryAdapter(
    private val onClick: (PointTransaction) -> Unit
) : RecyclerView.Adapter<PointsHistoryAdapter.VH>() {

    private var items: List<PointTransaction> = emptyList()
    private var p: HistoryPalette = historyPalette(false)
    private val fmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())

    fun submit(list: List<PointTransaction>, palette: HistoryPalette) {
        items = list; p = palette
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view as MaterialCardView
        val badge: TextView = view.findViewById(R.id.tvTxBadge)
        val title: TextView = view.findViewById(R.id.tvTxTitle)
        val date: TextView = view.findViewById(R.id.tvTxDate)
        val amount: TextView = view.findViewById(R.id.tvTxAmount)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_history_transaction, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, position: Int) {
        val t = items[position]
        val earned = t.amount >= 0
        val color = if (earned) p.positive else p.negative

        h.card.setCardBackgroundColor(p.cardBg)
        h.card.strokeColor = p.cardStroke

        h.badge.text = if (earned) "↑" else "↓"
        h.badge.setTextColor(color)
        h.badge.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(ColorUtils.setAlphaComponent(color, 38)) }

        h.title.text = t.title.ifBlank { if (earned) "Points earned" else "Points spent" }
        h.title.setTextColor(p.textPrimary)

        h.date.text = if (t.timestamp > 0) fmt.format(Date(t.timestamp)) else ""
        h.date.setTextColor(p.textSecondary)

        h.amount.text = if (earned) "+${t.amount}" else "${t.amount}"
        h.amount.setTextColor(color)

        // CLICK LISTENER ADDED HERE
        h.card.setOnClickListener { onClick(t) }
    }
}