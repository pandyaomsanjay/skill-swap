package com.example.sgp

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class SentTradesFragment : Fragment() {

    companion object { private const val TAG = "SentTrades" }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TradeAdapter
    private lateinit var emptyState: View
    private val tradeList = mutableListOf<Trade>()
    private var listener: ListenerRegistration? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, b: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_trades_list, c, false)
        recyclerView = v.findViewById(R.id.recyclerView)
        emptyState = v.findViewById(R.id.emptyState)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        val email = requireContext()
            .getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
            .getString("user_email", "").orEmpty()

        adapter = TradeAdapter(tradeList, email) { trade ->
            TradeDetailActivity.start(requireContext(), trade.id)
        }
        recyclerView.adapter = adapter

        if (email.isBlank()) {
            emptyState.visibility = View.VISIBLE
            return v
        }

        listener = FirebaseFirestore.getInstance().collection("trades")
            .whereEqualTo("requesterId", email)
            .whereIn("status", listOf(
                TradeStatus.PENDING.value,
                TradeStatus.COUNTER_PROPOSED.value
            ))
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Sent listener failed", err)
                    return@addSnapshotListener
                }
                tradeList.clear()
                snap?.documents?.forEach { d ->
                    d.toObject(Trade::class.java)?.copy(id = d.id)?.let { tradeList.add(it) }
                }
                tradeList.sortByDescending { it.timestamp }
                adapter.notifyDataSetChanged()
                emptyState.visibility = if (tradeList.isEmpty()) View.VISIBLE else View.GONE
                recyclerView.visibility = if (tradeList.isEmpty()) View.GONE else View.VISIBLE
            }
        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        listener?.remove(); listener = null
    }
}