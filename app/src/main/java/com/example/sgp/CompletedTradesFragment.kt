package com.example.sgp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration

class CompletedTradesFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TradeAdapter
    private val tradeList = mutableListOf<Trade>()
    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserId: String
    private var listener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_trades_list, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = TradeAdapter(tradeList) { _ ->
            Toast.makeText(requireContext(), "Trade clicked", Toast.LENGTH_SHORT).show()
        }
        recyclerView.adapter = adapter

        val prefs = requireContext().getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        currentUserId = prefs.getString("user_email", "") ?: ""

        db = Firebase.firestore
        loadTrades()
        return view
    }

    private fun loadTrades() {
        listener = db.collection("trades")
            .whereIn("status", listOf("completed", "cancelled"))
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(requireContext(), error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                tradeList.clear()
                snapshot?.documents?.forEach { doc ->
                    val trade = doc.toObject(Trade::class.java)
                    if (trade != null && (trade.requesterId == currentUserId || trade.receiverId == currentUserId)) {
                        tradeList.add(trade)
                    }
                }
                // also get trades where user is receiver
                db.collection("trades")
                    .whereIn("status", listOf("completed", "cancelled"))
                    .whereEqualTo("receiverId", currentUserId)
                    .get()
                    .addOnSuccessListener { receiverSnapshot ->
                        receiverSnapshot.documents.forEach { doc ->
                            val trade = doc.toObject(Trade::class.java)
                            if (trade != null && !tradeList.contains(trade)) {
                                tradeList.add(trade)
                            }
                        }
                        tradeList.sortByDescending { it.timestamp }
                        adapter.notifyDataSetChanged()
                    }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove()
    }

    inner class TradeAdapter(
        private val trades: List<Trade>,
        private val onItemClick: (Trade) -> Unit
    ) : RecyclerView.Adapter<TradeAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvPartner: TextView = itemView.findViewById(R.id.tvPartner)
            val tvSkills: TextView = itemView.findViewById(R.id.tvSkills)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_trade, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trade = trades[position]
            val partnerName = if (trade.requesterId == currentUserId) trade.receiverName else trade.requesterName
            holder.tvPartner.text = "With: $partnerName"
            holder.tvSkills.text = "${trade.requesterSkill} ⇄ ${trade.receiverSkill}"
            holder.tvStatus.text = trade.status
            holder.tvStatus.setTextColor(
                when (trade.status) {
                    "completed" -> android.graphics.Color.parseColor("#4CAF50")
                    "cancelled" -> android.graphics.Color.parseColor("#F44336")
                    else -> android.graphics.Color.parseColor("#757575")
                }
            )
            holder.itemView.setOnClickListener { onItemClick(trade) }
        }

        override fun getItemCount() = trades.size
    }
}