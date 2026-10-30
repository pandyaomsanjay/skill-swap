package com.example.sgp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Opened with "appealId" (appeal review + decision) or with "uid" (read-only warning history).
 * Shows decrypted evidence, so screenshots/recording are blocked on this screen.
 */
class AdminAppealDetailActivity : BaseActivity() {

    private var appealId: String? = null
    private var uid: String? = null
    private var detail: AppealDetail? = null
    private var busy = false
    private val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(R.layout.activity_admin_appeal_detail)

        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        appealId = intent.getStringExtra("appealId")
        uid = intent.getStringExtra("uid")
        if (appealId.isNullOrEmpty() && uid.isNullOrEmpty()) { finish(); return }

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { load() }
        findViewById<View>(R.id.btnApprove).setOnClickListener { showDecisionDialog(approve = true) }
        findViewById<View>(R.id.btnReject).setOnClickListener { showDecisionDialog(approve = false) }
        findViewById<View>(R.id.btnResend).setOnClickListener { resend() }
        load()
    }

    private fun load() {
        findViewById<View>(R.id.progressBar).visibility = View.VISIBLE
        lifecycleScope.launch {
            val res = if (!appealId.isNullOrEmpty()) AppealClient.adminDetail(appealId!!)
            else AppealClient.adminUserWarnings(uid!!)
            findViewById<View>(R.id.progressBar).visibility = View.GONE
            val d = res.value
            if (d == null) {
                Toast.makeText(this@AdminAppealDetailActivity, AppealClient.errorText(res.error), Toast.LENGTH_LONG).show()
                return@launch
            }
            render(d)
        }
    }

    private fun render(d: AppealDetail) {
        detail = d
        val display = d.username.ifBlank { d.email.ifBlank { d.uid } }
        findViewById<TextView>(R.id.tvUser).text =
            display + if (d.email.isNotBlank() && d.username.isNotBlank()) "\n${d.email}" else ""

        val m = d.moderation
        val until = m.blockedUntilMillis
        val banText = when {
            m.isPermanent || m.banLevel >= 3 -> "Permanent ban"
            until != null && until > ModerationClient.now() ->
                "${ModerationMessages.banTypeLabel(m.banLevel, false)} ban until ${fmt.format(Date(until))}"
            else -> "No active ban"
        }
        findViewById<TextView>(R.id.tvState).text =
            "Total warnings: ${m.warningCount} · Stage ${m.stage()} (${m.stageWarnings} in stage)\n$banText"

        // ---- appeal card + actions
        val ap = d.appeal
        val cardAppeal = findViewById<View>(R.id.cardAppeal)
        val layoutDecide = findViewById<View>(R.id.layoutDecide)
        val btnResend = findViewById<View>(R.id.btnResend)
        if (ap == null) {
            cardAppeal.visibility = View.GONE
            layoutDecide.visibility = View.GONE
            btnResend.visibility = View.GONE
        } else {
            cardAppeal.visibility = View.VISIBLE
            val (label, color) = when (ap.status) {
                "approved" -> "Appeal approved" to "#2D8577"
                "rejected" -> "Appeal rejected" to "#C0392B"
                else -> "Appeal pending review" to "#B8860B"
            }
            findViewById<TextView>(R.id.tvAppealStatus).apply { text = label; setTextColor(Color.parseColor(color)) }
            findViewById<TextView>(R.id.tvAppealNote).text =
                "User's explanation: " + ap.note.ifBlank { "none provided" }
            findViewById<TextView>(R.id.tvAppealDecision).text =
                if (ap.status == "pending") "Review the disputed warning (highlighted in gold) before deciding."
                else "Decision: ${ap.decisionReason ?: "—"}\n" +
                        "Decided: ${ap.decidedMillis?.let { fmt.format(Date(it)) } ?: "—"} · Push: ${ap.notifyStatus ?: "—"}"
            layoutDecide.visibility = if (ap.status == "pending") View.VISIBLE else View.GONE
            btnResend.visibility =
                if (ap.status != "pending" && ap.notifyStatus in listOf("failed", "skipped", "pending")) View.VISIBLE else View.GONE
        }

        // ---- history
        val list = findViewById<LinearLayout>(R.id.listHistory)
        list.removeAllViews()
        findViewById<View>(R.id.tvEmptyHistory).visibility = if (d.history.isEmpty()) View.VISIBLE else View.GONE
        val inflater = LayoutInflater.from(this)
        for (r in d.history.reversed()) {
            val card = inflater.inflate(R.layout.item_warning_evidence, list, false) as MaterialCardView
            bindWarning(card, r, disputed = r.eventId == d.eventId)
            list.addView(card)
        }
    }

    private fun bindWarning(card: MaterialCardView, r: WarningRecord, disputed: Boolean) {
        val title = "Warning #" + (r.warningNumber?.toString() ?: "?") +
                (r.stage?.let { " · Stage $it" } ?: "") +
                (if (r.stage != null && r.stageWarningNo != null) " (${r.stageWarningNo} in stage)" else "")
        card.findViewById<TextView>(R.id.tvWarnTitle).text = if (disputed) "⚖️ $title" else title

        val (label, fg, bg) = when (r.eventStatus) {
            "overturned" -> Triple("Overturned", "#2D8577", "#DCEEE8")
            "confirmed" -> Triple("Confirmed", "#C0392B", "#F6DEDE")
            "appealed" -> Triple("Appealed", "#B8860B", "#F3E7CE")
            else -> Triple("Active", "#456882", "#EAE1DA")
        }
        card.findViewById<TextView>(R.id.tvEventBadge).apply {
            text = label; setTextColor(Color.parseColor(fg)); setBackgroundColor(Color.parseColor(bg))
        }
        card.findViewById<TextView>(R.id.tvWhen).text = fmt.format(Date(r.createdMillis))

        val tvOriginal = card.findViewById<TextView>(R.id.tvOriginal)
        val tvMatched = card.findViewById<TextView>(R.id.tvMatched)
        val original = r.originalMessage
        if (r.evidenceAvailable && original != null) {
            tvOriginal.text = AppealClient.highlight(original, r.spans)
            tvOriginal.setTypeface(null, Typeface.NORMAL)
            tvMatched.visibility = View.VISIBLE
            tvMatched.text = when {
                r.matchedText.isNullOrBlank() -> "Matched phrase: not recorded"
                r.spans.isEmpty() -> "Matched: “${r.matchedText}” (could not be located in the text)"
                else -> "Matched: “${r.matchedText}”"
            }
        } else {
            tvOriginal.text = "Evidence unavailable"
            tvOriginal.setTypeface(null, Typeface.ITALIC)
            tvMatched.visibility = View.GONE
        }
        card.findViewById<TextView>(R.id.tvRule).text =
            "Category: ${AppealClient.categoryLabel(r.category)} · Rule: ${r.ruleId.ifBlank { "—" }}"

        val tvBan = card.findViewById<TextView>(R.id.tvBan)
        if (r.triggeredBan) {
            val b = r.ban
            tvBan.visibility = View.VISIBLE
            tvBan.text = if (b == null) "Triggered a chat ban (ban record unavailable)"
            else "Triggered ${ModerationMessages.banTypeLabel(b.level, b.permanent)} ban" +
                    (b.startedMillis?.let { " · ${fmt.format(Date(it))}" } ?: "") +
                    (if (b.permanent) " · never expires" else b.expiresMillis?.let { " → ${fmt.format(Date(it))}" } ?: "") +
                    (b.endedReason?.let { " · ended: $it" } ?: "")
        } else tvBan.visibility = View.GONE

        val tvAppeal = card.findViewById<TextView>(R.id.tvAppeal)
        val ap = r.appeal
        if (ap != null) {
            tvAppeal.visibility = View.VISIBLE
            tvAppeal.text = "Appeal: ${ap.status}" + (ap.decisionReason?.let { " — $it" } ?: "")
        } else tvAppeal.visibility = View.GONE

        if (disputed) {
            card.strokeColor = Color.parseColor("#B8860B")
            card.strokeWidth = (3 * resources.displayMetrics.density).toInt()
        }
    }

    // ------------------------------------------------------------------ decision
    private fun showDecisionDialog(approve: Boolean) {
        if (busy) return
        val dp = { v: Int -> (v * resources.displayMetrics.density).toInt() }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply { cornerRadius = dp(20).toFloat(); setColor(Color.WHITE) }
        }
        root.addView(TextView(this).apply {
            text = if (approve) "Approve appeal?" else "Reject appeal?"
            setTextColor(Color.parseColor("#1B3C53")); textSize = 17f; setTypeface(typeface, Typeface.BOLD)
        })
        root.addView(TextView(this).apply {
            text = if (approve)
                "The disputed warning is marked overturned and the user's warnings and ban are recalculated from the remaining valid warnings. " +
                        "Original evidence is kept. The user gets a push notification."
            else
                "The warning stays valid and any ban stays as it is. Original evidence is kept. The user gets a push notification that includes your reason."
            setTextColor(Color.parseColor("#456882")); textSize = 13f; setPadding(0, dp(8), 0, dp(12))
        })
        val input = EditText(this).apply {
            hint = "Decision reason (required, no private message content)"
            setTextColor(Color.parseColor("#1B3C53")); setHintTextColor(Color.parseColor("#456882"))
            minLines = 2; maxLines = 4
            filters = arrayOf(android.text.InputFilter.LengthFilter(500))
        }
        root.addView(input)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fun pill(text: String, bg: Int, fg: Int) = TextView(this).apply {
            this.text = text; setTextColor(fg); textSize = 14f; setTypeface(typeface, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER; setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply { cornerRadius = dp(24).toFloat(); setColor(bg) }
            isClickable = true; isFocusable = true
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, dp(16), 0, 0) }
        row.addView(pill("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(pill(
            if (approve) "Confirm approval" else "Confirm rejection",
            Color.parseColor(if (approve) "#1B3C53" else "#DC2626"), Color.WHITE
        ).apply {
            setOnClickListener {
                val reason = input.text.toString().trim()
                if (reason.length < 3) {
                    Toast.makeText(this@AdminAppealDetailActivity, "Enter a decision reason.", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                decide(approve, reason)
            }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)
        dialog.show()
    }

    private fun setBusy(b: Boolean) {
        busy = b
        findViewById<View>(R.id.progressBar).visibility = if (b) View.VISIBLE else View.GONE
        findViewById<View>(R.id.btnApprove).isEnabled = !b
        findViewById<View>(R.id.btnReject).isEnabled = !b
    }

    private fun decide(approve: Boolean, reason: String) {
        val id = appealId ?: return
        setBusy(true)
        lifecycleScope.launch {
            val res = AppealClient.adminDecide(id, approve, reason)
            setBusy(false)
            if (res.ok) {
                val push = when (res.value) {
                    "sent" -> "User notified."
                    "skipped" -> "Saved. The user has push turned off or no device."
                    "failed" -> "Saved. Push failed; use Resend notification."
                    else -> "Saved."
                }
                Toast.makeText(this@AdminAppealDetailActivity,
                    (if (approve) "Appeal approved. " else "Appeal rejected. ") + push, Toast.LENGTH_LONG).show()
                load()
            } else {
                Toast.makeText(this@AdminAppealDetailActivity, AppealClient.errorText(res.error), Toast.LENGTH_LONG).show()
                load()
            }
        }
    }

    private fun resend() {
        val id = appealId ?: return
        lifecycleScope.launch {
            val res = AppealClient.adminResend(id)
            Toast.makeText(this@AdminAppealDetailActivity,
                if (res.ok) "Notification: ${res.value}" else AppealClient.errorText(res.error), Toast.LENGTH_LONG).show()
            load()
        }
    }
}