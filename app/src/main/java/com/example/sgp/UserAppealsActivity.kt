package com.example.sgp

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The signed-in user's OWN appeals only (the server filters by the verified uid). */
class UserAppealsActivity : BaseActivity() {

    private var dark = false
    private val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_appeals)
        if (FirebaseAuth.getInstance().currentUser == null) { finish(); return }

        dark = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        findViewById<View>(R.id.rootAppeals).setBackgroundColor(Color.parseColor(if (dark) "#0F1B24" else "#EAF1F5"))
        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val progress = findViewById<View>(R.id.progressBar)
        progress.visibility = View.VISIBLE
        lifecycleScope.launch {
            val res = AppealClient.userMine()
            progress.visibility = View.GONE
            val data = res.value
            if (data == null) {
                Toast.makeText(this@UserAppealsActivity, R.string.appeals_load_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            render(data)
        }
    }

    private fun render(data: UserAppealsResult) {
        val text = if (dark) "#F5F7F8" else "#1B3C53"
        val sub = if (dark) "#8FA6B8" else "#456882"
        val cardBg = Color.parseColor(if (dark) "#16232E" else "#FFFFFF")
        val stroke = Color.parseColor(if (dark) "#2A3B48" else "#D2C1B6")

        // current restriction (server state)
        val m = data.moderation
        val until = m.blockedUntilMillis
        findViewById<MaterialCardView>(R.id.cardRestriction).apply { setCardBackgroundColor(cardBg); strokeColor = stroke }
        findViewById<TextView>(R.id.tvRestriction).apply {
            setTextColor(Color.parseColor(text))
            this.text = when {
                m.isPermanent || m.banLevel >= 3 -> getString(R.string.appeals_restriction_perm)
                until != null && until > ModerationClient.now() ->
                    getString(R.string.appeals_restriction_temp, ModerationMessages.formatRemaining(until - ModerationClient.now()))
                else -> getString(R.string.appeals_restriction_none)
            }
        }

        val list = findViewById<LinearLayout>(R.id.listAppeals)
        list.removeAllViews()
        findViewById<TextView>(R.id.tvEmpty).apply {
            visibility = if (data.appeals.isEmpty()) View.VISIBLE else View.GONE
            setTextColor(Color.parseColor(sub))
        }
        val highlight = intent.getStringExtra("appealId")
        val inflater = LayoutInflater.from(this)
        for (a in data.appeals) {
            val card = inflater.inflate(R.layout.item_user_appeal, list, false) as MaterialCardView
            card.setCardBackgroundColor(cardBg)
            card.strokeColor = stroke
            if (a.id == highlight) {
                card.strokeColor = Color.parseColor("#B8860B")
                card.strokeWidth = (3 * resources.displayMetrics.density).toInt()
            }
            card.findViewById<TextView>(R.id.tvCategory).apply {
                setTextColor(Color.parseColor(text))
                this.text = getString(R.string.appeal_category_label, AppealClient.categoryLabel(a.category))
            }
            val (labelRes, fg, bg) = when (a.status) {
                "approved" -> Triple(R.string.appeal_status_approved, "#2D8577", "#DCEEE8")
                "rejected" -> Triple(R.string.appeal_status_rejected, "#C0392B", "#F6DEDE")
                else -> Triple(R.string.appeal_status_pending, "#B8860B", "#F3E7CE")
            }
            card.findViewById<TextView>(R.id.tvStatus).apply {
                setText(labelRes); setTextColor(Color.parseColor(fg)); setBackgroundColor(Color.parseColor(bg))
            }
            card.findViewById<TextView>(R.id.tvNote).apply {
                setTextColor(Color.parseColor(sub)); this.text = a.note; visibility = if (a.note.isBlank()) View.GONE else View.VISIBLE
            }
            card.findViewById<TextView>(R.id.tvDecision).apply {
                setTextColor(Color.parseColor(text))
                if (a.status == "pending" || a.decisionReason.isNullOrBlank()) visibility = View.GONE
                else { visibility = View.VISIBLE; this.text = getString(R.string.appeal_reason_label, a.decisionReason) }
            }
            card.findViewById<TextView>(R.id.tvDates).apply {
                setTextColor(Color.parseColor(sub))
                val s = a.submittedMillis?.let { getString(R.string.appeal_submitted_label, fmt.format(Date(it))) } ?: ""
                val d = a.decidedMillis?.let { "\n" + getString(R.string.appeal_decided_label, fmt.format(Date(it))) } ?: ""
                this.text = s + d
            }
            list.addView(card)
        }
    }
}