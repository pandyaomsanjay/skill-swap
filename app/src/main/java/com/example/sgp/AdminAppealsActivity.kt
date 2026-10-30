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

class AdminAppealsActivity : BaseActivity() {

    private lateinit var adapter: Adapter
    private lateinit var emptyState: View
    private lateinit var progressBar: View
    private lateinit var tabPending: MaterialCardView
    private lateinit var tabDecided: MaterialCardView
    private lateinit var tvTabPending: TextView
    private lateinit var tvTabDecided: TextView

    private val items = mutableListOf<AppealListItem>()
    private var showDecided = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_appeals)

        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish(); return
        }

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { load() }
        tabPending = findViewById(R.id.tabPending)
        tabDecided = findViewById(R.id.tabDecided)
        tvTabPending = findViewById(R.id.tvTabPending)
        tvTabDecided = findViewById(R.id.tvTabDecided)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)
        tabPending.setOnClickListener { showDecided = false; styleChips(); load() }
        tabDecided.setOnClickListener { showDecided = true; styleChips(); load() }

        val rv = findViewById<RecyclerView>(R.id.recyclerView)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = Adapter(items) { a ->
            startActivity(Intent(this, AdminAppealDetailActivity::class.java).putExtra("appealId", a.id))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        }
        rv.adapter = adapter
        styleChips()
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun styleChips() {
        val onBg = Color.parseColor("#F9F3EF"); val onText = Color.parseColor("#1B3C53")
        val offBg = Color.parseColor("#456882"); val offText = Color.WHITE
        tabPending.setCardBackgroundColor(if (showDecided) offBg else onBg)
        tvTabPending.setTextColor(if (showDecided) offText else onText)
        tabDecided.setCardBackgroundColor(if (showDecided) onBg else offBg)
        tvTabDecided.setTextColor(if (showDecided) onText else offText)
    }

    private fun load() {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        lifecycleScope.launch {
            val res = AppealClient.adminList(showDecided)
            progressBar.visibility = View.GONE
            val list = res.value
            if (list == null) {
                Toast.makeText(this@AdminAppealsActivity, AppealClient.errorText(res.error), Toast.LENGTH_LONG).show()
                return@launch
            }
            items.clear(); items.addAll(list)
            adapter.notifyDataSetChanged()
            emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    class Adapter(
        private val items: List<AppealListItem>,
        private val onClick: (AppealListItem) -> Unit
    ) : RecyclerView.Adapter<Adapter.VH>() {

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
            val a = items[position]
            h.tvRequester.text = a.username.ifBlank { a.email.ifBlank { a.uid } }
            h.tvOther.text = "Flagged for: ${AppealClient.categoryLabel(a.category)}" +
                    if (a.evidenceAvailable) "" else " · Evidence unavailable"
            h.tvDesc.text = a.note.ifBlank { "No explanation provided." }
            val (label, fg, bg) = when (a.status) {
                "approved" -> Triple("Approved", "#2D8577", "#DCEEE8")
                "rejected" -> Triple("Rejected", "#C0392B", "#F6DEDE")
                else -> Triple("Pending", "#B8860B", "#F3E7CE")
            }
            h.tvBadge.text = label
            h.tvBadge.setTextColor(Color.parseColor(fg))
            h.tvBadge.setBackgroundColor(Color.parseColor(bg))
            h.tvCreated.text = if (a.status == "pending")
                "Submitted: " + (a.submittedMillis?.let { fmt.format(Date(it)) } ?: "—")
            else "Decided: " + (a.decidedMillis?.let { fmt.format(Date(it)) } ?: "—") +
                    " · Push: " + (a.notifyStatus ?: "—")
            h.itemView.setOnClickListener { onClick(a) }
        }

        override fun getItemCount() = items.size
    }
}