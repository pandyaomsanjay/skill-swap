package com.example.sgp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class UserTab { ALL, VERIFIED, BLOCKED, REPORTED }

class AdminUsersActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserAdapter
    private lateinit var emptyState: View
    private lateinit var etSearch: EditText
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null
    private var tradesListener: ListenerRegistration? = null

    private val allUsers = mutableListOf<User>()
    private val displayedUsers = mutableListOf<User>()
    private val swapCounts = mutableMapOf<String, Int>() // key = user email, value = completed swap count

    private var currentTab = UserTab.ALL
    private var currentQuery = ""

    private lateinit var tabAll: MaterialCardView
    private lateinit var tabVerified: MaterialCardView
    private lateinit var tabBlocked: MaterialCardView
    private lateinit var tabReported: MaterialCardView
    private lateinit var tvTabAll: TextView
    private lateinit var tvTabVerified: TextView
    private lateinit var tvTabBlocked: TextView
    private lateinit var tvTabReported: TextView

    // Same chip palette used on the Trades page, so both screens feel identical.
    private val selectedChipBg = Color.parseColor("#F9F3EF")
    private val unselectedChipBg = Color.parseColor("#456882")
    private val selectedChipText = Color.parseColor("#1B3C53")
    private val unselectedChipText = Color.parseColor("#FFFFFF")
    private val unselectedChipStroke = Color.parseColor("#FFFFFF")

    // Dark palette for the options bottom sheet, matching the navy app theme
    private val sheetBg = Color.parseColor("#16263A")
    private val sheetDivider = Color.parseColor("#28405A")
    private val sheetPrimaryText = Color.parseColor("#F5EDE4")
    private val sheetSecondaryText = Color.parseColor("#9FB3C8")
    private val sheetDestructive = Color.parseColor("#FF8A80")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_users)

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
        BottomNav.setup(this, BottomNav.USERS)

        loadUsers()
        loadSwapCountsRealtime()
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        etSearch = findViewById(R.id.etSearch)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UserAdapter(displayedUsers, swapCounts) { user, _ ->
            showUserOptionsMenu(user)
        }
        recyclerView.adapter = adapter

        tabAll = findViewById(R.id.tabAll)
        tabVerified = findViewById(R.id.tabVerified)
        tabBlocked = findViewById(R.id.tabBlocked)
        tabReported = findViewById(R.id.tabReported)
        tvTabAll = findViewById(R.id.tvTabAll)
        tvTabVerified = findViewById(R.id.tvTabVerified)
        tvTabBlocked = findViewById(R.id.tvTabBlocked)
        tvTabReported = findViewById(R.id.tvTabReported)
    }

    // ---------- Tabs ----------

    private fun setupTabs() {
        tabAll.setOnClickListener { selectTab(UserTab.ALL) }
        tabVerified.setOnClickListener { selectTab(UserTab.VERIFIED) }
        tabBlocked.setOnClickListener { selectTab(UserTab.BLOCKED) }
        tabReported.setOnClickListener { selectTab(UserTab.REPORTED) }
        selectTab(UserTab.ALL)
    }

    private fun selectTab(tab: UserTab) {
        currentTab = tab
        val chips = listOf(
            tabAll to tvTabAll,
            tabVerified to tvTabVerified,
            tabBlocked to tvTabBlocked,
            tabReported to tvTabReported
        )
        val selectedIndex = when (tab) {
            UserTab.ALL -> 0
            UserTab.VERIFIED -> 1
            UserTab.BLOCKED -> 2
            UserTab.REPORTED -> 3
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

    private fun applyFilters() {
        val filtered = allUsers.filter { user ->
            val matchesTab = when (currentTab) {
                UserTab.ALL -> true
                UserTab.VERIFIED -> !user.isBlocked && user.isEmailVerified
                UserTab.BLOCKED -> user.isBlocked
                UserTab.REPORTED -> user.isReported
            }
            val matchesQuery = currentQuery.isBlank() ||
                    user.name.lowercase(Locale.getDefault()).contains(currentQuery) ||
                    user.email.lowercase(Locale.getDefault()).contains(currentQuery)
            matchesTab && matchesQuery
        }
        displayedUsers.clear()
        displayedUsers.addAll(filtered)
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (displayedUsers.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------- Data loading ----------

    private fun loadUsers() {
        listener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Load failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                allUsers.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        val user = doc.toObject(User::class.java)
                        if (user != null) {
                            if (user.uid.isBlank()) user.uid = doc.id
                            allUsers.add(user)
                        }
                    } catch (_: Exception) {
                        // skip malformed doc
                    }
                }
                applyFilters()
            }
    }

    private fun loadSwapCountsRealtime() {
        tradesListener = db.collection("trades")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener

                val counts = mutableMapOf<String, Int>()
                snapshot?.documents?.forEach { doc ->
                    val status = (doc.getString("status") ?: "").lowercase(Locale.getDefault())
                    if (status == "completed") {
                        val requesterId = doc.getString("requesterId")
                        val receiverId = doc.getString("receiverId")
                        if (!requesterId.isNullOrBlank()) {
                            counts[requesterId] = (counts[requesterId] ?: 0) + 1
                        }
                        if (!receiverId.isNullOrBlank()) {
                            counts[receiverId] = (counts[receiverId] ?: 0) + 1
                        }
                    }
                }

                swapCounts.clear()
                swapCounts.putAll(counts)
                adapter.notifyDataSetChanged()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        tradesListener?.remove()
    }

    // ---------- Bottom-sheet options menu ----------

    private fun showUserOptionsMenu(user: User) {
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

        // ---- Header: avatar + name + email ----
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        val initial = user.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatar = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            radius = dp(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#EAE1DA"))
        }
        if (user.profileImage.isNotEmpty()) {
            val iv = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            avatar.addView(iv)
            Glide.with(this).load(user.profileImage).into(iv)
        } else {
            avatar.addView(TextView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                text = initial
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 16f
                setTypeface(typeface, Typeface.BOLD)
            })
        }
        header.addView(avatar)

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        textCol.addView(TextView(this).apply {
            text = user.name.ifBlank { "No name set" }
            setTextColor(sheetPrimaryText)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(this).apply {
            text = user.email
            setTextColor(sheetSecondaryText)
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        header.addView(textCol)
        root.addView(header)

        root.addView(sheetDividerLine())

        fun addRow(emoji: String, label: String, textColor: Int = sheetPrimaryText, action: () -> Unit) {
            val outValue = TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
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
                gravity = Gravity.CENTER
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

        addRow("👁️", "View Profile") { viewProfile(user) }
        addRow("✏️", "Edit User") { editUser(user) }
        addRow(
            if (user.isEmailVerified) "❎" else "✅",
            if (user.isEmailVerified) "Unverify" else "Verify"
        ) { toggleVerified(user) }
        addRow(
            if (user.isBlocked) "🔓" else "🚫",
            if (user.isBlocked) "Unblock" else "Block",
            if (user.isBlocked) sheetPrimaryText else sheetDestructive
        ) { toggleBlocked(user) }

        addRow("🧩", "View Skills") { viewSkills(user) }
        addRow("🔄", "Swap History") { viewSwapHistory(user) }
        addRow("🗑️", "Delete Account", sheetDestructive) { confirmDelete(user) }

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    // ---------- View profile ----------

    private fun viewProfile(user: User) {
        val root = dialogCard()
        val scroll = ScrollView(this)

        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        val avatarContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        val initial = user.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        val avatar = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64))
            radius = dp(32).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#EAE1DA"))
        }
        if (user.profileImage.isNotEmpty()) {
            val iv = ImageView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                scaleType = ImageView.ScaleType.CENTER_CROP
            }
            avatar.addView(iv)
            Glide.with(this).load(user.profileImage).into(iv)
        } else {
            avatar.addView(TextView(this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                text = initial
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 22f
                setTypeface(typeface, Typeface.BOLD)
            })
        }
        avatarContainer.addView(avatar)

        avatarContainer.addView(TextView(this).apply {
            text = user.name.ifBlank { "No name set" }
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        })
        avatarContainer.addView(TextView(this).apply {
            text = user.email
            setTextColor(Color.parseColor("#456882"))
            textSize = 12.5f
            gravity = Gravity.CENTER
        })

        val (statusLabel, statusColor, statusBg) = when {
            user.isBlocked -> Triple("Blocked", "#F87171", R.drawable.bg_status_blocked)
            user.isEmailVerified -> Triple("Verified", "#34D399", R.drawable.bg_status_verified)
            else -> Triple("Pending", "#FBBF24", R.drawable.bg_status_pending)
        }
        avatarContainer.addView(TextView(this).apply {
            text = statusLabel
            setTextColor(Color.parseColor(statusColor))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(statusBg)
            setPadding(dp(12), dp(4), dp(12), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        })

        content.addView(
            avatarContainer,
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(12)
            }
        )
        content.addView(dividerLine())

        val statsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        fun statCell(label: String, value: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@AdminUsersActivity).apply {
                    text = value
                    setTextColor(Color.parseColor("#1B3C53"))
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
                addView(TextView(this@AdminUsersActivity).apply {
                    text = label
                    setTextColor(Color.parseColor("#456882"))
                    textSize = 11f
                    gravity = Gravity.CENTER
                })
            }
        }
        statsRow.addView(statCell("Rating", String.format(Locale.getDefault(), "%.1f", user.rating)))
        statsRow.addView(statCell("Swaps", (swapCounts[user.email] ?: user.completedTrades).toString()))
        statsRow.addView(statCell("Credits", user.credits.toString()))
        content.addView(statsRow, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(12); bottomMargin = dp(12)
        })
        content.addView(dividerLine())

        val joined = if (user.joinedDate > 0) {
            SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(user.joinedDate))
        } else "Unknown"

        fun addDetailRow(label: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            }
            row.addView(TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#456882"))
                textSize = 12.5f
                layoutParams = LinearLayout.LayoutParams(dp(100), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = value
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 12.5f
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            content.addView(row)
        }

        addDetailRow("Phone", user.phone.ifBlank { "—" })
        addDetailRow("Location", user.location.ifBlank { "—" })
        addDetailRow("Type", user.userType.ifBlank { "—" })
        addDetailRow("Reported", if (user.isReported) "Yes" else "No")
        addDetailRow("Joined", joined)

        scroll.addView(content)
        root.addView(scroll)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = pillButton("Close", Color.parseColor("#1B3C53"), Color.WHITE).apply {
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
        root.addView(btnClose)

        dialog.show()
    }

    // ---------- Edit user ----------

    private fun editUser(user: User) {
        if (user.uid.isBlank()) {
            Toast.makeText(this, "This user has no uid on record, can't edit", Toast.LENGTH_SHORT).show()
            return
        }

        val root = dialogCard()
        root.addView(dialogTitle("Edit User"))
        root.addView(dividerLine())

        val etName = styledInput("Name", user.name)
        val etPhone = styledInput("Phone", user.phone)
        val etLocation = styledInput("Location", user.location)
        val etCredits = styledInput("Credits", user.credits.toString(), numeric = true)

        listOf(etName, etPhone, etLocation, etCredits).forEach { field ->
            root.addView(
                field,
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(12)
                }
            )
        }

        val dialog = AlertDialog.Builder(this).setView(ScrollView(this).apply { addView(root) }).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(18)
            }
        }
        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnSave = pillButton("Save", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            setOnClickListener {
                val newName = etName.text.toString().trim()
                val newPhone = etPhone.text.toString().trim()
                val newLocation = etLocation.text.toString().trim()
                val newCredits = etCredits.text.toString().trim().toLongOrNull()

                if (newName.isEmpty()) {
                    Toast.makeText(this@AdminUsersActivity, "Name can't be empty", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newCredits == null) {
                    Toast.makeText(this@AdminUsersActivity, "Credits must be a number", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val updates = hashMapOf<String, Any>(
                    "name" to newName,
                    "phone" to newPhone,
                    "location" to newLocation,
                    "credits" to newCredits
                )

                db.collection("users").document(user.uid)
                    .update(updates)
                    .addOnSuccessListener {
                        Toast.makeText(this@AdminUsersActivity, "User updated", Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@AdminUsersActivity, "Update failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
        }
        buttonRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttonRow.addView(btnSave, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    // ---------- View skills ----------

    private fun viewSkills(user: User) {
        val root = dialogCard()
        root.addView(dialogTitle("${user.name.ifBlank { user.email }}'s Skills"))
        root.addView(dividerLine())

        fun addSection(label: String, value: String) {
            root.addView(TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#456882"))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(0, dp(10), 0, dp(4))
            })
            root.addView(TextView(this).apply {
                text = value.ifBlank { "—" }
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 13.5f
            })
        }

        addSection("Can Teach", user.skillsTeach)
        addSection("Wants to Learn", user.skillsLearn)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = pillButton("Close", Color.parseColor("#1B3C53"), Color.WHITE).apply {
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
        root.addView(btnClose)

        dialog.show()
    }

    // ---------- Swap history ----------

    private fun viewSwapHistory(user: User) {
        val root = dialogCard()
        root.addView(dialogTitle("${user.name.ifBlank { user.email }}'s Swap History"))
        root.addView(dividerLine())

        val listContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val loadingText = TextView(this).apply {
            text = "Loading…"
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
        }
        listContainer.addView(loadingText)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(360))
            addView(listContainer)
        }
        root.addView(scroll)

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnClose = pillButton("Close", Color.parseColor("#1B3C53"), Color.WHITE).apply {
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
        root.addView(btnClose)

        dialog.show()

        val email = user.email
        val asRequester = db.collection("trades").whereEqualTo("requesterId", email).get()
        val asReceiver = db.collection("trades").whereEqualTo("receiverId", email).get()

        Tasks.whenAllComplete(asRequester, asReceiver)
            .addOnCompleteListener {
                val items = mutableListOf<Map<String, Any?>>()
                (asRequester.result?.documents ?: emptyList()).forEach { doc -> items.add(doc.data ?: emptyMap()) }
                (asReceiver.result?.documents ?: emptyList()).forEach { doc -> items.add(doc.data ?: emptyMap()) }

                listContainer.removeAllViews()

                if (items.isEmpty()) {
                    listContainer.addView(TextView(this).apply {
                        text = "No swaps yet"
                        setTextColor(Color.parseColor("#9AA7B0"))
                        textSize = 13f
                        gravity = Gravity.CENTER
                        setPadding(0, dp(24), 0, dp(24))
                    })
                    return@addOnCompleteListener
                }

                items.forEachIndexed { index, trade ->
                    listContainer.addView(buildSwapHistoryRow(trade, email))
                    if (index != items.lastIndex) listContainer.addView(dividerLine())
                }
            }
    }

    private fun buildSwapHistoryRow(trade: Map<String, Any?>, currentUserEmail: String): View {
        val requesterId = trade["requesterId"] as? String ?: ""
        val receiverId = trade["receiverId"] as? String ?: ""
        val otherParty = if (requesterId == currentUserEmail) receiverId else requesterId
        val status = (trade["status"] as? String)?.ifBlank { "Pending" } ?: "Pending"
        val skillOffered = trade["skillOffered"] as? String ?: "—"
        val skillWanted = trade["skillWanted"] as? String ?: "—"
        val timestamp = (trade["timestamp"] as? Long) ?: (trade["createdDate"] as? Long) ?: 0L

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(10), 0, dp(10))
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        topRow.addView(TextView(this).apply {
            text = "With: ${otherParty.ifBlank { "Unknown" }}"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 13.5f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })

        val statusColor = when (status.lowercase(Locale.getDefault())) {
            "completed" -> "#34D399"
            "cancelled", "rejected" -> "#F87171"
            else -> "#FBBF24"
        }
        topRow.addView(TextView(this).apply {
            text = status.replaceFirstChar { it.uppercase() }
            setTextColor(Color.parseColor(statusColor))
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        })
        row.addView(topRow)

        row.addView(TextView(this).apply {
            text = "Offered: $skillOffered · Wanted: $skillWanted"
            setTextColor(Color.parseColor("#456882"))
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        })

        row.addView(TextView(this).apply {
            text = if (timestamp > 0) {
                SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(timestamp))
            } else "Date unknown"
            setTextColor(Color.parseColor("#9AA7B0"))
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })

        return row
    }

    // ---------- Verify / block / suspend ----------

    private fun toggleVerified(user: User) {
        updateUserField(user, "isEmailVerified", !user.isEmailVerified, "Verification updated")
    }

    private fun toggleBlocked(user: User) {
        updateUserField(user, "isBlocked", !user.isBlocked, "Block status updated")
    }

    // ---------- Delete account (cascading Firestore delete) ----------

    private fun confirmDelete(user: User) {
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
            text = "Delete Account"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Delete all Firestore data for ${user.name.ifBlank { user.email }} " +
                    "(skills, trades, reports, ratings, videos)? " +
                    "Cannot be undone. Auth record is unaffected."
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
                deleteUserDocument(user)
            }
        }
        buttonRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttonRow.addView(btnDelete, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun deleteUserDocument(user: User) {
        if (user.uid.isBlank()) {
            Toast.makeText(this, "This user has no uid on record, can't delete", Toast.LENGTH_SHORT).show()
            return
        }

        val progressRoot = dialogCard()
        progressRoot.addView(TextView(this).apply {
            text = "Deleting user and related data…"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14f
            gravity = Gravity.CENTER
        })
        val progress = AlertDialog.Builder(this)
            .setView(progressRoot)
            .setCancelable(false)
            .create()
        progress.window?.setBackgroundDrawableResource(android.R.color.transparent)
        progress.show()

        val uid = user.uid
        val email = user.email

        val cleanupTasks = mutableListOf<Task<*>>()

        val skillsTask = db.collection("skills").whereEqualTo("userId", email).get()
            .continueWithTask { task ->
                val batch = db.batch()
                task.result?.documents?.forEach { doc -> batch.delete(doc.reference) }
                batch.commit()
            }
        cleanupTasks.add(skillsTask)

        val tradesAsRequester = db.collection("trades").whereEqualTo("requesterId", email).get()
            .continueWithTask { task ->
                val batch = db.batch()
                task.result?.documents?.forEach { doc -> batch.delete(doc.reference) }
                batch.commit()
            }
        val tradesAsReceiver = db.collection("trades").whereEqualTo("receiverId", email).get()
            .continueWithTask { task ->
                val batch = db.batch()
                task.result?.documents?.forEach { doc -> batch.delete(doc.reference) }
                batch.commit()
            }
        cleanupTasks.add(tradesAsRequester)
        cleanupTasks.add(tradesAsReceiver)

        val reportsFiled = db.collection("reports").whereEqualTo("reporterId", email).get()
            .continueWithTask { task ->
                val batch = db.batch()
                task.result?.documents?.forEach { doc -> batch.delete(doc.reference) }
                batch.commit()
            }
        cleanupTasks.add(reportsFiled)

        val ratingDelete = db.collection("ratings").document(uid).delete()
        cleanupTasks.add(ratingDelete)

        val videosDelete = db.collection("videos").document(uid).collection("userVideos").get()
            .continueWithTask { task ->
                val batch = db.batch()
                task.result?.documents?.forEach { doc -> batch.delete(doc.reference) }
                batch.commit()
            }
        cleanupTasks.add(videosDelete)

        Tasks.whenAllComplete(cleanupTasks)
            .addOnCompleteListener {
                db.collection("users").document(uid)
                    .delete()
                    .addOnSuccessListener {
                        progress.dismiss()
                        Toast.makeText(this, "User and related Firestore data deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener { e ->
                        progress.dismiss()
                        Toast.makeText(this, "Related data cleaned, but user doc delete failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
    }

    private fun updateUserField(user: User, field: String, value: Any, successMessage: String) {
        if (user.uid.isBlank()) {
            Toast.makeText(this, "This user has no uid on record, can't update", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("users").document(user.uid)
            .update(field, value)
            .addOnSuccessListener {
                Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to update: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ---------- Themed-dialog helpers ----------

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

    private fun dividerLine(): View {
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

    private fun sheetDividerLine(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(sheetDivider)
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

    private fun styledInput(
        hint: String,
        initialText: String,
        numeric: Boolean = false,
        multiline: Boolean = false
    ): EditText {
        return EditText(this).apply {
            this.hint = hint
            setText(initialText)
            setHintTextColor(Color.parseColor("#9AA7B0"))
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 14f
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER
            if (multiline) {
                minLines = 3
                gravity = Gravity.TOP or Gravity.START
                inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(Color.parseColor("#EAF1F5"))
                setStroke(dp(1), Color.parseColor("#D2C1B6"))
            }
        }
    }

    // ---------- Adapter ----------
    class UserAdapter(
        private val users: List<User>,
        private val swapCounts: Map<String, Int>,
        private val onMoreClick: (User, View) -> Unit
    ) : RecyclerView.Adapter<UserAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvInitial: TextView = itemView.findViewById(R.id.tvInitial)
            val ivAvatar: ImageView = itemView.findViewById(R.id.ivAvatar)
            val tvName: TextView = itemView.findViewById(R.id.tvName)
            val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
            val tvStatusBadge: TextView = itemView.findViewById(R.id.tvStatusBadge)
            val tvJoinDate: TextView = itemView.findViewById(R.id.tvJoinDate)
            val tvRating: TextView = itemView.findViewById(R.id.tvRating)
            val tvSwapsCompleted: TextView = itemView.findViewById(R.id.tvSwapsCompleted)
            val btnMoreOptions: View = itemView.findViewById(R.id.btnMoreOptions)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_user, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val user = users[position]

            val initial = user.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            holder.tvInitial.text = initial
            holder.tvName.text = if (user.name.isBlank()) "No name set" else user.name
            holder.tvEmail.text = user.email

            when {
                user.isBlocked -> {
                    holder.tvStatusBadge.text = "Blocked"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#F87171"))
                    holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_blocked)
                }
                user.isEmailVerified -> {
                    holder.tvStatusBadge.text = "Verified"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#34D399"))
                    holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_verified)
                }
                else -> {
                    holder.tvStatusBadge.text = "Pending"
                    holder.tvStatusBadge.setTextColor(Color.parseColor("#FBBF24"))
                    holder.tvStatusBadge.setBackgroundResource(R.drawable.bg_status_pending)
                }
            }

            holder.tvJoinDate.text = if (user.joinedDate > 0) {
                "Joined ${SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(user.joinedDate))}"
            } else {
                "Joined --"
            }
            holder.tvRating.text = String.format(Locale.getDefault(), "%.1f", user.rating)

            val liveCount = swapCounts[user.email] ?: user.completedTrades
            holder.tvSwapsCompleted.text = "$liveCount swaps"

            if (user.profileImage.isNotEmpty()) {
                holder.ivAvatar.visibility = View.VISIBLE
                holder.tvInitial.visibility = View.GONE
                Glide.with(holder.itemView.context)
                    .load(user.profileImage)
                    .into(holder.ivAvatar)
            } else {
                holder.ivAvatar.visibility = View.GONE
                holder.tvInitial.visibility = View.VISIBLE
            }

            holder.btnMoreOptions.setOnClickListener { onMoreClick(user, it) }
        }

        override fun getItemCount() = users.size
    }
}