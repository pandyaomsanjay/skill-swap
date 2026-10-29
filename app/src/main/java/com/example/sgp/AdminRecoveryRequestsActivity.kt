package com.example.sgp

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminRecoveryRequestsActivity : BaseActivity() {

    private lateinit var adapter: RequestAdapter
    private lateinit var emptyState: View
    private lateinit var progressBar: View
    private lateinit var tabOpen: MaterialCardView
    private lateinit var tabClosed: MaterialCardView
    private lateinit var tvTabOpen: TextView
    private lateinit var tvTabClosed: TextView

    private val items = mutableListOf<RecoveryRequest>()
    private var showClosed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_recovery_requests)

        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { load() }
        tabOpen = findViewById(R.id.tabOpen)
        tabClosed = findViewById(R.id.tabClosed)
        tvTabOpen = findViewById(R.id.tvTabOpen)
        tvTabClosed = findViewById(R.id.tvTabClosed)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        tabOpen.setOnClickListener { showClosed = false; styleChips(); load() }
        tabClosed.setOnClickListener { showClosed = true; styleChips(); load() }

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = RequestAdapter(items) { r ->
            startActivity(Intent(this, AdminRecoveryChatActivity::class.java).putExtra("requestId", r.id))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        }
        rv.adapter = adapter
        styleChips()
    }

    override fun onResume() {
        super.onResume()
        if (::progressBar.isInitialized) load() // refresh after returning from a review / End Chat
    }

    private fun styleChips() {
        val onBg = Color.parseColor("#F9F3EF"); val onText = Color.parseColor("#1B3C53")
        val offBg = Color.parseColor("#456882"); val offText = Color.WHITE
        tabOpen.setCardBackgroundColor(if (showClosed) offBg else onBg)
        tvTabOpen.setTextColor(if (showClosed) offText else onText)
        tabClosed.setCardBackgroundColor(if (showClosed) onBg else offBg)
        tvTabClosed.setTextColor(if (showClosed) onText else offText)
    }

    private fun load() {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        lifecycleScope.launch {
            val res = RecoveryClient.adminList(showClosed)
            progressBar.visibility = View.GONE
            val list = res.value
            if (list == null) {
                Toast.makeText(this@AdminRecoveryRequestsActivity, RecoveryClient.errorText(res.error), Toast.LENGTH_LONG).show()
                return@launch
            }
            items.clear(); items.addAll(list)
            adapter.notifyDataSetChanged()
            emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    class RequestAdapter(
        private val items: List<RecoveryRequest>,
        private val onClick: (RecoveryRequest) -> Unit
    ) : RecyclerView.Adapter<RequestAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvRequester: TextView = v.findViewById(R.id.tvRequester)
            val tvBadge: TextView = v.findViewById(R.id.tvBadge)
            val tvOther: TextView = v.findViewById(R.id.tvOther)
            val tvDesc: TextView = v.findViewById(R.id.tvDesc)
            val tvCreated: TextView = v.findViewById(R.id.tvCreated)
        }

        private val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            VH(LayoutInflater.from(parent.context).inflate(R.layout.item_recovery_request, parent, false))

        override fun onBindViewHolder(h: VH, position: Int) {
            val r = items[position]
            h.tvRequester.text = r.requesterEmail
            h.tvOther.text = "Chat with: ${r.otherEmail}"
            h.tvDesc.text = r.description
            h.tvCreated.text = "Submitted: " + fmt.format(Date(r.createdMillis))
            val (label, fg, bg) = when (r.status) {
                "pending" -> Triple("Pending", "#B8860B", "#F3E7CE")
                "active" -> Triple("Access active", "#2D8577", "#DCEEE8")
                "rejected" -> Triple("Rejected", "#C0392B", "#F6DEDE")
                else -> Triple("Ended", "#456882", "#EAE1DA")
            }
            h.tvBadge.text = label
            h.tvBadge.setTextColor(Color.parseColor(fg))
            h.tvBadge.setBackgroundColor(Color.parseColor(bg))
            h.itemView.setOnClickListener { onClick(r) }
        }

        override fun getItemCount() = items.size
    }
}