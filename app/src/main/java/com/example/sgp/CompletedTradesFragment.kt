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

/**
 * Completed = {COMPLETED}. Sorted by completedAt client-side.
 */
class CompletedTradesFragment : Fragment() {

    companion object { private const val TAG = "CompletedTrades" }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TradeAdapter
    private lateinit var emptyState: View
    private val tradeList = mutableListOf<Trade>()

    private var regRequester: ListenerRegistration? = null
    private var regReceiver: ListenerRegistration? = null

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

        val merged = mutableMapOf<String, Trade>()
        val statusList = listOf(TradeStatus.COMPLETED.value)

        fun render() {
            tradeList.clear()
            // Prefer completedAt when available; fall back to timestamp
            tradeList.addAll(merged.values.sortedByDescending {
                if (it.completedAt > 0L) it.completedAt else it.timestamp
            })
            adapter.notifyDataSetChanged()
            emptyState.visibility = if (tradeList.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (tradeList.isEmpty()) View.GONE else View.VISIBLE
        }

        regRequester = FirebaseFirestore.getInstance().collection("trades")
            .whereEqualTo("requesterId", email)
            .whereIn("status", statusList)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Requester listener failed", err)
                    return@addSnapshotListener
                }
                merged.entries.removeAll { it.value.requesterId == email }
                snap?.documents?.forEach { d ->
                    d.toObject(Trade::class.java)?.copy(id = d.id)?.let { merged[d.id] = it }
                }
                render()
            }

        regReceiver = FirebaseFirestore.getInstance().collection("trades")
            .whereEqualTo("receiverId", email)
            .whereIn("status", statusList)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e(TAG, "Receiver listener failed", err)
                    return@addSnapshotListener
                }
                merged.entries.removeAll { it.value.receiverId == email }
                snap?.documents?.forEach { d ->
                    d.toObject(Trade::class.java)?.copy(id = d.id)?.let { merged[d.id] = it }
                }
                render()
            }

        return v
    }

    override fun onDestroyView() {
        super.onDestroyView()
        regRequester?.remove(); regReceiver?.remove()
        regRequester = null; regReceiver = null
    }
}