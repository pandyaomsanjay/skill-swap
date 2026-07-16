package com.example.sgp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration

class AdminTradesActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TradeAdapter
    private val tradeList = mutableListOf<Trade>()
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_trades)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = Firebase.firestore

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TradeAdapter(tradeList) { trade ->
            showTradeOptionsDialog(trade)
        }
        recyclerView.adapter = adapter

        loadTrades()
    }

    private fun loadTrades() {
        listener = db.collection("trades")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this@AdminTradesActivity, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                tradeList.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Trade::class.java)?.let { tradeList.add(it) }
                }
                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    private fun showTradeOptionsDialog(trade: Trade) {
        val options = arrayOf("View Details", "Mark as Completed", "Cancel Trade", "Delete Trade")
        AlertDialog.Builder(this)
            .setTitle("Manage Trade")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewTradeDetails(trade)
                    1 -> updateTradeStatus(trade, "completed")
                    2 -> updateTradeStatus(trade, "cancelled")
                    3 -> deleteTrade(trade)
                }
            }
            .show()
    }

    private fun viewTradeDetails(trade: Trade) {
        val message = """
            Requester: ${trade.requesterName}
            Requester Skill: ${trade.requesterSkill}
            Receiver: ${trade.receiverName}
            Receiver Skill: ${trade.receiverSkill}
            Status: ${trade.status}
            Date: ${java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(trade.timestamp))}
        """.trimIndent()
        AlertDialog.Builder(this)
            .setTitle("Trade Details")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun updateTradeStatus(trade: Trade, status: String) {
        db.collection("trades").document(trade.id)
            .update("status", status)
            .addOnSuccessListener {
                Toast.makeText(this, "Trade status updated to $status", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun deleteTrade(trade: Trade) {
        AlertDialog.Builder(this)
            .setTitle("Delete Trade")
            .setMessage("Are you sure you want to delete this trade? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                db.collection("trades").document(trade.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this, "Trade deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    class TradeAdapter(
        private val trades: List<Trade>,
        private val onItemClick: (Trade) -> Unit
    ) : RecyclerView.Adapter<TradeAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvRequester: TextView = itemView.findViewById(R.id.tvRequester)
            val tvSkills: TextView = itemView.findViewById(R.id.tvSkills)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_trade, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trade = trades[position]
            holder.tvRequester.text = "${trade.requesterName} ⇄ ${trade.receiverName}"
            holder.tvSkills.text = "${trade.requesterSkill} for ${trade.receiverSkill}"
            holder.tvStatus.text = trade.status
            holder.tvStatus.setTextColor(
                when (trade.status) {
                    "pending" -> android.graphics.Color.parseColor("#FF9800")
                    "accepted" -> android.graphics.Color.parseColor("#2196F3")
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