package com.example.sgp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.widget.ImageView
import com.bumptech.glide.Glide


enum class TradeTab { ALL, PENDING, ACCEPTED, COMPLETED, CANCELLED }

/** Holds the live-updatable views inside one half of the "View Both User Profiles" dialog. */
private data class ProfileSectionViews(
    val container: View,
    val ivAvatar: ImageView,
    val tvInitial: TextView,
    val tvEmail: TextView,
    val tvBadge: TextView,
    val tvMeta: TextView
)

class AdminTradesActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: TradeAdapter
    private lateinit var emptyState: View
    private lateinit var etSearch: EditText
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    private val allTrades = mutableListOf<Trade>()
    private val displayedTrades = mutableListOf<Trade>()

    private var currentTab = TradeTab.ALL
    private var currentQuery = ""

    private lateinit var tabAll: MaterialCardView
    private lateinit var tabPending: MaterialCardView
    private lateinit var tabAccepted: MaterialCardView
    private lateinit var tabCompleted: MaterialCardView
    private lateinit var tabCancelled: MaterialCardView
    private lateinit var tvTabAll: TextView
    private lateinit var tvTabPending: TextView
    private lateinit var tvTabAccepted: TextView
    private lateinit var tvTabCompleted: TextView
    private lateinit var tvTabCancelled: TextView

    // Same chip palette used on the Users page, so both screens feel identical.
    private val selectedChipBg = Color.parseColor("#F9F3EF")
    private val unselectedChipBg = Color.parseColor("#456882")
    private val selectedChipText = Color.parseColor("#1B3C53")
    private val unselectedChipText = Color.parseColor("#FFFFFF")
    private val unselectedChipStroke = Color.parseColor("#FFFFFF")

    // Dark palette for the options bottom sheet, so it matches the navy app theme
    // instead of sitting on the page as a stark white card.
    private val sheetBg = Color.parseColor("#16263A")
    private val sheetDivider = Color.parseColor("#28405A")
    private val sheetPrimaryText = Color.parseColor("#F5EDE4")
    private val sheetSecondaryText = Color.parseColor("#9FB3C8")
    private val sheetDestructive = Color.parseColor("#FF8A80")

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

        bindViews()
        setupTabs()
        setupSearch()
        BottomNav.setup(this, BottomNav.SWAPS)

        loadTrades()
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        etSearch = findViewById(R.id.etSearch)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = TradeAdapter(displayedTrades) { trade, _ -> showTradeOptionsMenu(trade) }
        recyclerView.adapter = adapter

        tabAll = findViewById(R.id.tabAll)
        tabPending = findViewById(R.id.tabPending)
        tabAccepted = findViewById(R.id.tabAccepted)
        tabCompleted = findViewById(R.id.tabCompleted)
        tabCancelled = findViewById(R.id.tabCancelled)
        tvTabAll = findViewById(R.id.tvTabAll)
        tvTabPending = findViewById(R.id.tvTabPending)
        tvTabAccepted = findViewById(R.id.tvTabAccepted)
        tvTabCompleted = findViewById(R.id.tvTabCompleted)
        tvTabCancelled = findViewById(R.id.tvTabCancelled)
    }

    // ---------- Tabs ----------

    private fun setupTabs() {
        tabAll.setOnClickListener { selectTab(TradeTab.ALL) }
        tabPending.setOnClickListener { selectTab(TradeTab.PENDING) }
        tabAccepted.setOnClickListener { selectTab(TradeTab.ACCEPTED) }
        tabCompleted.setOnClickListener { selectTab(TradeTab.COMPLETED) }
        tabCancelled.setOnClickListener { selectTab(TradeTab.CANCELLED) }
        selectTab(TradeTab.ALL)
    }

    private fun selectTab(tab: TradeTab) {
        currentTab = tab
        val chips = listOf(
            tabAll to tvTabAll,
            tabPending to tvTabPending,
            tabAccepted to tvTabAccepted,
            tabCompleted to tvTabCompleted,
            tabCancelled to tvTabCancelled
        )
        val selectedIndex = when (tab) {
            TradeTab.ALL -> 0
            TradeTab.PENDING -> 1
            TradeTab.ACCEPTED -> 2
            TradeTab.COMPLETED -> 3
            TradeTab.CANCELLED -> 4
        }
        val strokeWidthPx = (1 * resources.displayMetrics.density).toInt()
        chips.forEachIndexed { index, (card, text) ->
            if (index == selectedIndex) {
                card.setCardBackgroundColor(selectedChipBg)
                card.strokeWidth = 0
                text.setTextColor(selectedChipText)
            } else {
                card.setCardBackgroundColor(unselectedChipBg)
                card.strokeWidth = strokeWidthPx
                card.strokeColor = unselectedChipStroke
                text.setTextColor(unselectedChipText)
            }
        }
        applyFilters()
    }

    // ---------- Search ----------

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ---------- Filtering ----------

    private fun applyFilters() {
        val statusFiltered = allTrades.filter { trade ->
            when (currentTab) {
                TradeTab.ALL -> true
                TradeTab.PENDING -> trade.status.equals("pending", ignoreCase = true)
                TradeTab.ACCEPTED -> trade.status.equals("accepted", ignoreCase = true)
                TradeTab.COMPLETED -> trade.status.equals("completed", ignoreCase = true)
                TradeTab.CANCELLED -> trade.status.equals("cancelled", ignoreCase = true) ||
                        trade.status.equals("rejected", ignoreCase = true)
            }
        }

        val fullyFiltered = if (currentQuery.isBlank()) {
            statusFiltered
        } else {
            statusFiltered.filter {
                it.requesterName.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.receiverName.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.requesterSkill.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.receiverSkill.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.id.lowercase(Locale.getDefault()).contains(currentQuery)
            }
        }

        displayedTrades.clear()
        displayedTrades.addAll(fullyFiltered)
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (displayedTrades.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- Data loading ----------

    private fun loadTrades() {
        listener = db.collection("trades")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Load failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                allTrades.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Trade::class.java)?.let {
                        allTrades.add(it.copy(id = doc.id))
                    }
                }
                applyFilters()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    // ---------- Bottom-sheet options menu (built entirely in code, no layout file) ----------

    private fun showTradeOptionsMenu(trade: Trade) {
        val dialog = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            background = GradientDrawable().apply {
                val r = dp(20).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(sheetBg)
            }
        }

        // ---- Header: swap icon + names + swap id ----
        val shortId = if (trade.id.length > 6) trade.id.takeLast(6) else trade.id
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        // Simple initial-based avatar, same look as AdminReportsActivity's bottom-sheet header
        val headerInitial = trade.requesterName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        header.addView(MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            radius = dp(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#28405A"))
            addView(TextView(this@AdminTradesActivity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                text = headerInitial
                setTextColor(sheetPrimaryText)
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
        })
        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        textCol.addView(TextView(this).apply {
            text = "${trade.requesterName} ⇄ ${trade.receiverName}"
            setTextColor(sheetPrimaryText)
            textSize = 16f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(this).apply {
            text = "Swap ID: #$shortId"
            setTextColor(sheetSecondaryText)
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(textCol)
        root.addView(header)

        // ---- Divider ----
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(sheetDivider)
        })

        val isPending = trade.status.equals("pending", ignoreCase = true)
        val isAccepted = trade.status.equals("accepted", ignoreCase = true)

        fun addRow(emoji: String, label: String, textColor: Int = sheetPrimaryText, action: () -> Unit) {
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(20), dp(14), dp(20), dp(14))
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            row.addView(TextView(this).apply {
                text = emoji
                textSize = 18f
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(14) }
            })
            root.addView(row)
        }

        addRow("📋", "View Swap Details") { viewTradeDetails(trade) }
        addRow("👤", "View Both User Profiles") { viewBothProfiles(trade) }
        if (isPending) {
            addRow("✅", "Approve", Color.parseColor("#34D399")) { updateTradeStatus(trade, "accepted") }
            addRow("❌", "Reject", sheetDestructive) { updateTradeStatus(trade, "rejected") }
        }
        if (isAccepted) {
            addRow("🏁", "Mark as Completed") { markCompleted(trade) }
        }
        if (isPending || isAccepted) {
            addRow("🚫", "Cancel Swap") { updateTradeStatus(trade, "cancelled") }
        }
        addRow("💬", "View Chat") { viewChat(trade) }
        addRow("⚠️", "Report Issue") { reportIssue(trade) }
        addRow("🗑️", "Delete Record", sheetDestructive) { confirmDeleteTrade(trade) }

        dialog.setContentView(root)
        dialog.show()
    }

    private fun viewTradeDetails(trade: Trade) {
        val root = dialogCard()

        root.addView(dialogTitle("Trade Details"))
        root.addView(dialogDivider())

        fun addDetailRow(label: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(10) }
            }
            row.addView(TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#456882"))
                textSize = 12.5f
                layoutParams = LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = value
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            root.addView(row)
        }

        addDetailRow("Swap ID", trade.id)
        addDetailRow("Requester", trade.requesterName)
        addDetailRow("Requester Skill", trade.requesterSkill)
        addDetailRow("Receiver", trade.receiverName)
        addDetailRow("Receiver Skill", trade.receiverSkill)
        addDetailRow("Status", trade.status.replaceFirstChar { it.uppercase() })
        addDetailRow("Requested", formatDate(trade.timestamp))
        if (trade.completionTimestamp > 0) {
            addDetailRow("Completed", formatDate(trade.completionTimestamp))
        }
        if (trade.reportReason.isNotBlank()) {
            addDetailRow("Reported Issue", trade.reportReason)
        }

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnOk = pillButton("OK", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
                gravity = Gravity.END
            }
            setPadding(dp(28), dp(10), dp(28), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnOk)

        dialog.show()
    }

    private fun viewBothProfiles(trade: Trade) {
        val root = dialogCard()
        root.addView(dialogTitle("User Profiles"))
        root.addView(dialogDivider())

        val requester = buildProfileSection(trade.requesterName, trade.requesterSkill, "Offering")
        root.addView(requester.container)

        root.addView(dialogDivider())

        val receiver = buildProfileSection(trade.receiverName, trade.receiverSkill, "Offering")
        root.addView(receiver.container)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = pillButton("Close", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(18)
                gravity = Gravity.END
            }
            setPadding(dp(28), dp(10), dp(28), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnClose)

        dialog.show()

        loadUserSummary(trade.requesterId) { user -> applyUserSummary(user, requester) }
        loadUserSummary(trade.receiverId) { user -> applyUserSummary(user, receiver) }
    }

    /** Fetches a lightweight profile summary for the "View Both User Profiles" dialog, by email. */
    private fun loadUserSummary(email: String, onResult: (User?) -> Unit) {
        if (email.isBlank()) {
            onResult(null)
            return
        }
        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { snapshot -> onResult(snapshot.documents.firstOrNull()?.toObject(User::class.java)) }
            .addOnFailureListener { onResult(null) }
    }

    private fun buildProfileSection(name: String, skill: String, skillLabel: String): ProfileSectionViews {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"

        val tvInitial = TextView(this@AdminTradesActivity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            gravity = Gravity.CENTER
            text = initial
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
        }

        val ivAvatar = ImageView(this@AdminTradesActivity).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.CENTER_CROP
            visibility = View.GONE
        }

        val avatar = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            radius = dp(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#EAE1DA"))
            addView(tvInitial)
            addView(ivAvatar)
        }

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(12)
            }
        }
        textCol.addView(TextView(this).apply {
            text = name.ifBlank { "No name set" }
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 15f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        val tvEmail = TextView(this).apply {
            text = "Loading…"
            setTextColor(Color.parseColor("#456882"))
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(tvEmail)

        val tvBadge = TextView(this).apply {
            text = "…"
            textSize = 10f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#9AA7B0"))
            setPadding(dp(10), dp(4), dp(10), dp(4))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.parseColor("#EAF1F5"))
            }
        }

        topRow.addView(avatar)
        topRow.addView(textCol)
        topRow.addView(tvBadge)
        container.addView(topRow)

        container.addView(TextView(this).apply {
            text = "$skillLabel: $skill"
            setTextColor(Color.parseColor("#456882"))
            textSize = 12.5f
            setPadding(0, dp(10), 0, 0)
        })

        val tvMeta = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#9AA7B0"))
            textSize = 11f
            setPadding(0, dp(6), 0, 0)
        }
        container.addView(tvMeta)

        return ProfileSectionViews(container, ivAvatar, tvInitial, tvEmail, tvBadge, tvMeta)
    }

    private fun applyUserSummary(user: User?, views: ProfileSectionViews) {
        if (user == null) {
            views.tvEmail.text = "Profile not found"
            views.tvBadge.visibility = View.GONE
            views.tvMeta.text = ""
            return
        }

        if (user.profileImage.isNotEmpty()) {
            views.tvInitial.visibility = View.GONE
            views.ivAvatar.visibility = View.VISIBLE
            Glide.with(this).load(user.profileImage).into(views.ivAvatar)
        }

        views.tvEmail.text = user.email

        when {
            user.isBlocked -> {
                views.tvBadge.text = "Blocked"
                views.tvBadge.setTextColor(Color.parseColor("#F87171"))
                views.tvBadge.setBackgroundResource(R.drawable.bg_status_blocked)
            }
            user.isEmailVerified -> {
                views.tvBadge.text = "Verified"
                views.tvBadge.setTextColor(Color.parseColor("#34D399"))
                views.tvBadge.setBackgroundResource(R.drawable.bg_status_verified)
            }
            else -> {
                views.tvBadge.text = "Pending"
                views.tvBadge.setTextColor(Color.parseColor("#FBBF24"))
                views.tvBadge.setBackgroundResource(R.drawable.bg_status_pending)
            }
        }

        views.tvMeta.text = "⭐ ${String.format(Locale.getDefault(), "%.1f", user.rating)}   🔄 ${user.completedTrades} swaps"
    }
    private fun markCompleted(trade: Trade) {
        db.collection("trades").document(trade.id)
            .update(
                mapOf(
                    "status" to "completed",
                    "completionTimestamp" to System.currentTimeMillis()
                )
            )
            .addOnSuccessListener {
                Toast.makeText(this, "Marked as completed", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTradeStatus(trade: Trade, status: String) {
        db.collection("trades").document(trade.id)
            .update("status", status)
            .addOnSuccessListener {
                Toast.makeText(this, "Trade status updated to $status", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmDeleteTrade(trade: Trade) {
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "🗑️"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = "Delete Trade"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Are you sure you want to delete this trade? This action cannot be undone."
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(18)
            }
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnDelete = pillButton("Delete", Color.parseColor("#DC2626"), Color.WHITE).apply {
            setOnClickListener {
                dialog.dismiss()
                db.collection("trades").document(trade.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this@AdminTradesActivity, "Trade deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@AdminTradesActivity, "Failed to delete", Toast.LENGTH_SHORT).show()
                    }
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnDelete, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun viewChat(trade: Trade) {
        // Rename the class below once your real chat screen exists.
        try {
            val clazz = Class.forName("com.example.sgp.ChatActivity")
            val intent = Intent(this, clazz).apply {
                putExtra("swapId", trade.id)
            }
            startActivity(intent)
        } catch (e: ClassNotFoundException) {
            Toast.makeText(this, "Chat screen not implemented yet", Toast.LENGTH_SHORT).show()
        }
    }

    private fun reportIssue(trade: Trade) {
        val root = dialogCard()
        root.addView(dialogTitle("Report Issue"))

        val input = EditText(this).apply {
            hint = "Describe the issue"
            setHintTextColor(Color.parseColor("#9AA7B0"))
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14f
            minLines = 3
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EAF1F5"))
                setStroke(dp(1), Color.parseColor("#D2C1B6"))
            }
        }
        root.addView(
            input,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(12)
                bottomMargin = dp(18)
            }
        )

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnSubmit = pillButton("Submit", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            setOnClickListener {
                val reason = input.text.toString().trim()
                if (reason.isEmpty()) {
                    Toast.makeText(this@AdminTradesActivity, "Please describe the issue", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                dialog.dismiss()
                val report = mapOf(
                    "swapId" to trade.id,
                    "reason" to reason,
                    "reportedAt" to System.currentTimeMillis(),
                    "requesterName" to trade.requesterName,
                    "receiverName" to trade.receiverName
                )
                db.collection("reports").add(report)
                    .addOnSuccessListener {
                        db.collection("trades").document(trade.id).update("reportReason", reason)
                        Toast.makeText(this@AdminTradesActivity, "Issue reported", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@AdminTradesActivity, "Failed to submit report", Toast.LENGTH_SHORT).show()
                    }
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnSubmit, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    // ---------- Themed-dialog helpers (shared white rounded card + pill buttons) ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun dialogDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(Color.parseColor("#EAF1F5"))
        }
    }

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
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
    }

    private fun formatDate(millis: Long): String {
        if (millis <= 0) return "-"
        return SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(Date(millis))
    }

    // ---------- Adapter ----------

    class TradeAdapter(
        private val trades: List<Trade>,
        private val onMoreClick: (Trade, View) -> Unit
    ) : RecyclerView.Adapter<TradeAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvAvatarInitial: TextView = itemView.findViewById(R.id.tvAvatarInitial)
            val tvSwapId: TextView = itemView.findViewById(R.id.tvSwapId)
            val tvRequester: TextView = itemView.findViewById(R.id.tvRequester)
            val tvSkills: TextView = itemView.findViewById(R.id.tvSkills)
            val tvRequestedDate: TextView = itemView.findViewById(R.id.tvRequestedDate)
            val llCompletionGroup: View = itemView.findViewById(R.id.llCompletionGroup)
            val tvCompletionDate: TextView = itemView.findViewById(R.id.tvCompletionDate)
            val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
            val btnMoreOptions: View = itemView.findViewById(R.id.btnMoreOptions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_trade, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val trade = trades[position]

            val shortId = if (trade.id.length > 6) trade.id.takeLast(6) else trade.id
            holder.tvSwapId.text = "Swap ID: #$shortId"
            holder.tvAvatarInitial.text = trade.requesterName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.tvRequester.text = "${trade.requesterName} ⇄ ${trade.receiverName}"
            holder.tvSkills.text = "${trade.requesterSkill} for ${trade.receiverSkill}"

            holder.tvRequestedDate.text = "Requested ${formatDate(trade.timestamp)}"

            if (trade.completionTimestamp > 0) {
                holder.llCompletionGroup.visibility = View.VISIBLE
                holder.tvCompletionDate.text = "Completed ${formatDate(trade.completionTimestamp)}"
            } else {
                holder.llCompletionGroup.visibility = View.GONE
            }

            val (label, textColor, bgRes) = statusDisplay(trade.status)
            holder.tvStatusBadge.text = label
            holder.tvStatusBadge.setTextColor(Color.parseColor(textColor))
            holder.tvStatusBadge.setBackgroundResource(bgRes)

            holder.btnMoreOptions.setOnClickListener { onMoreClick(trade, it) }
        }

        private fun statusDisplay(status: String): Triple<String, String, Int> {
            return when (status.lowercase(Locale.getDefault())) {
                "pending" -> Triple("Pending", "#B8860B", R.drawable.bg_status_pending)
                "accepted" -> Triple("Accepted", "#2563EB", R.drawable.bg_status_verified)
                "completed" -> Triple("Completed", "#2E9E63", R.drawable.bg_status_trade_completed)
                "cancelled" -> Triple("Cancelled", "#DC2626", R.drawable.bg_status_trade_cancelled)
                "rejected" -> Triple("Rejected", "#DC2626", R.drawable.bg_status_blocked)
                else -> Triple(status.replaceFirstChar { it.uppercase() }, "#757575", R.drawable.bg_status_pending)
            }
        }

        private fun formatDate(millis: Long): String {
            if (millis <= 0) return "-"
            return SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(millis))
        }

        override fun getItemCount() = trades.size
    }
}