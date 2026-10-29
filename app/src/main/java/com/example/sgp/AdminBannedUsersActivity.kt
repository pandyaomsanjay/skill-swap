package com.example.sgp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminBannedUsersActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: BannedUserAdapter
    private lateinit var emptyState: View
    private lateinit var progressBar: View

    private lateinit var tabPermanent: MaterialCardView
    private lateinit var tabIncludeTemp: MaterialCardView
    private lateinit var tvTabPermanent: TextView
    private lateinit var tvTabIncludeTemp: TextView

    private val items = mutableListOf<BannedUser>()
    private var includeTemporary = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_banned_users)

        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        findViewById<View>(R.id.ivBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnRefresh).setOnClickListener { load() }
        findViewById<View>(R.id.btnRecovery).setOnClickListener {
            startActivity(Intent(this, AdminRecoveryRequestsActivity::class.java))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        }

        tabPermanent = findViewById(R.id.tabPermanent)
        tabIncludeTemp = findViewById(R.id.tabIncludeTemp)
        tvTabPermanent = findViewById(R.id.tvTabPermanent)
        tvTabIncludeTemp = findViewById(R.id.tvTabIncludeTemp)

        tabPermanent.setOnClickListener { setIncludeTemporary(false) }
        tabIncludeTemp.setOnClickListener { setIncludeTemporary(true) }

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = BannedUserAdapter(items) { user -> confirmUnban(user) }
        recyclerView.adapter = adapter

        setIncludeTemporary(false)
    }

    private fun setIncludeTemporary(include: Boolean) {
        includeTemporary = include
        styleChips()
        load()
    }

    private fun styleChips() {
        val onBg = Color.parseColor("#F9F3EF")
        val onText = Color.parseColor("#1B3C53")
        val offBg = Color.parseColor("#456882")
        val offText = Color.parseColor("#FFFFFF")

        if (includeTemporary) {
            tabPermanent.setCardBackgroundColor(offBg)
            tvTabPermanent.setTextColor(offText)
            tabIncludeTemp.setCardBackgroundColor(onBg)
            tvTabIncludeTemp.setTextColor(onText)
        } else {
            tabPermanent.setCardBackgroundColor(onBg)
            tvTabPermanent.setTextColor(onText)
            tabIncludeTemp.setCardBackgroundColor(offBg)
            tvTabIncludeTemp.setTextColor(offText)
        }
    }

    private fun load() {
        progressBar.visibility = View.VISIBLE
        emptyState.visibility = View.GONE
        lifecycleScope.launch {
            val list = ModerationClient.fetchBannedUsers(includeTemporary)
            progressBar.visibility = View.GONE
            if (list == null) {
                Toast.makeText(this@AdminBannedUsersActivity, "Couldn't load banned users.", Toast.LENGTH_LONG).show()
                return@launch
            }
            items.clear()
            items.addAll(list)
            adapter.notifyDataSetChanged()
            emptyState.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun confirmUnban(user: BannedUser) {
        val banType = ModerationMessages.banTypeLabel(user.banLevel, user.isPermanent)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }

        root.addView(TextView(this).apply {
            text = "Unban user?"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        })

        root.addView(TextView(this).apply {
            text = "This user is currently banned ($banType).\n\n" +
                    "Removing the ban lets them chat again immediately. " +
                    "The ladder restarts at stage 1 with 0 warnings in the current stage. " +
                    "Total warning count and ban history are preserved. " +
                    "This action is recorded in the audit log."
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            setPadding(0, dp(8), 0, dp(18))
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnUnban = pillButton("Unban", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            setOnClickListener { dialog.dismiss(); doUnban(user) }
        }
        row.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        row.addView(btnUnban, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(row)

        dialog.show()
    }

    private fun doUnban(user: BannedUser) {
        progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            val ok = ModerationClient.unbanUser(user.uid, "unbanned from admin screen")
            progressBar.visibility = View.GONE
            if (ok) {
                Toast.makeText(this@AdminBannedUsersActivity, "User unbanned", Toast.LENGTH_SHORT).show()
                load()
            } else {
                Toast.makeText(this@AdminBannedUsersActivity, "Unban failed. Please try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView = TextView(this).apply {
        this.text = text
        setTextColor(textColor)
        textSize = 14f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        setPadding(0, dp(12), 0, dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(bgColor)
        }
        isClickable = true
        isFocusable = true
    }

    // ------------------------------------------------------------------
    class BannedUserAdapter(
        private val items: List<BannedUser>,
        private val onUnbanClick: (BannedUser) -> Unit
    ) : RecyclerView.Adapter<BannedUserAdapter.VH>() {

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
            val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
            val tvUid: TextView = itemView.findViewById(R.id.tvUid)
            val tvBanBadge: TextView = itemView.findViewById(R.id.tvBanBadge)
            val tvWarnings: TextView = itemView.findViewById(R.id.tvWarnings)
            val tvStage: TextView = itemView.findViewById(R.id.tvStage)
            val tvBanStart: TextView = itemView.findViewById(R.id.tvBanStart)
            val tvBanEnds: TextView = itemView.findViewById(R.id.tvBanEnds)
            val tvReason: TextView = itemView.findViewById(R.id.tvReason)
            val btnUnban: View = itemView.findViewById(R.id.btnUnban)
        }

        private val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_banned_user, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val u = items[position]
            val display = u.username.ifBlank { u.email.ifBlank { u.uid } }
            holder.tvInitial.text = display.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.tvUsername.text = display
            holder.tvUid.text = u.uid

            val label = ModerationMessages.banTypeLabel(u.banLevel, u.isPermanent)
            holder.tvBanBadge.text = label
            when {
                u.isPermanent || u.banLevel >= 3 -> {
                    holder.tvBanBadge.setTextColor(Color.parseColor("#C0392B"))
                    holder.tvBanBadge.setBackgroundColor(Color.parseColor("#F6DEDE"))
                }
                u.banLevel == 2 -> {
                    holder.tvBanBadge.setTextColor(Color.parseColor("#B8860B"))
                    holder.tvBanBadge.setBackgroundColor(Color.parseColor("#F3E7CE"))
                }
                else -> {
                    holder.tvBanBadge.setTextColor(Color.parseColor("#2D8577"))
                    holder.tvBanBadge.setBackgroundColor(Color.parseColor("#DCEEE8"))
                }
            }

            holder.tvWarnings.text = "Total warnings: ${u.warningCount}"
            holder.tvStage.text = "Stage ${u.stage()}"

            holder.tvBanStart.text = "Started: " + (u.blockStartedMillis?.let { fmt.format(Date(it)) } ?: "—")
            holder.tvBanEnds.text = if (u.isPermanent || u.banLevel >= 3) {
                "Ends: never (admin action required)"
            } else {
                "Ends: " + (u.blockedUntilMillis?.let { fmt.format(Date(it)) } ?: "—")
            }
            holder.tvReason.text = "Reason: " + (ModerationMessages.cleanBanReason(u.reason) ?: "—")

            // Admins can unban at ANY stage (temporary or permanent).
            holder.btnUnban.isEnabled = true
            holder.btnUnban.alpha = 1f
            holder.btnUnban.setOnClickListener { onUnbanClick(u) }
        }

        override fun getItemCount() = items.size
    }
}