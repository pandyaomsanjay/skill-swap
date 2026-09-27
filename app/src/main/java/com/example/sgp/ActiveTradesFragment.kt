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
 * Active = {PENDING, COUNTER_PROPOSED, ACCEPTED, SCHEDULE_FINALIZED}.
 * Merges requester + receiver streams (Firestore can't OR two different fields).
 * No orderBy anywhere — sorted client-side.
 */
class ActiveTradesFragment : Fragment() {

    companion object { private const val TAG = "ActiveTrades" }

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TradeAdapter
    private lateinit var emptyState: View
    private val tradeList = mutableListOf<Trade>()

    private var regRequester: ListenerRegistration? = null
    private var regReceiver: ListenerRegistration? = null

    private val activeStatuses = listOf(
        TradeStatus.PENDING.value,
        TradeStatus.COUNTER_PROPOSED.value,
        TradeStatus.ACCEPTED.value,
        TradeStatus.SCHEDULE_FINALIZED.value
    )

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, b: Bundle?): View {
        val v = inflater.inflate(R.layout.fragment_trades_list, c, false)
        recyclerView = v.findViewById(R.id.recyclerView)
        emptyState = v.findViewById(R.id.emptyState)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Extract dark mode state passed from MyTradesActivity
        val isDarkMode = arguments?.getBoolean("isDarkMode") ?: false

        val email = requireContext()
            .getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
            .getString("user_email", "").orEmpty()

        // Pass isDarkMode to the adapter
        adapter = TradeAdapter(tradeList, email, isDarkMode) { trade ->
            TradeDetailActivity.start(requireContext(), trade.id)
        }
        recyclerView.adapter = adapter

        if (email.isBlank()) {
            emptyState.visibility = View.VISIBLE
            return v
        }

        // ── Merge both sides into one map (dedupes by tradeId) ──
        val merged = mutableMapOf<String, Trade>()

        fun render() {
            tradeList.clear()
            tradeList.addAll(merged.values.sortedByDescending { it.timestamp })
            adapter.notifyDataSetChanged()
            emptyState.visibility = if (tradeList.isEmpty()) View.VISIBLE else View.GONE
            recyclerView.visibility = if (tradeList.isEmpty()) View.GONE else View.VISIBLE
        }

        regRequester = FirebaseFirestore.getInstance().collection("trades")
            .whereEqualTo("requesterId", email)
            .whereIn("status", activeStatuses)
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
            .whereIn("status", activeStatuses)
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