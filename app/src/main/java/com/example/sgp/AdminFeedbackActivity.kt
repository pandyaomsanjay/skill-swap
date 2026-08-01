package com.example.sgp

import android.content.Context
import android.content.ContentValues
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.content.pm.PackageManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AdminFeedbackActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvEmptyState: LinearLayout
    private lateinit var recyclerView: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var adapter: FeedbackAdapter

    private lateinit var tabAllFeedback: MaterialCardView
    private lateinit var tabSuggestions: MaterialCardView
    private lateinit var tabBugReports: MaterialCardView
    private lateinit var tabComplaints: MaterialCardView
    private lateinit var tabFeatureRequests: MaterialCardView
    private lateinit var tvTabAllFeedback: TextView
    private lateinit var tvTabSuggestions: TextView
    private lateinit var tvTabBugReports: TextView
    private lateinit var tvTabComplaints: TextView
    private lateinit var tvTabFeatureRequests: TextView

    // Same chip palette used on the Users/Trades pages, so all admin screens feel identical.
    private val selectedChipBg = Color.parseColor("#F9F3EF")
    private val unselectedChipBg = Color.parseColor("#456882")
    private val selectedChipText = Color.parseColor("#1B3C53")
    private val unselectedChipText = Color.parseColor("#FFFFFF")
    private val unselectedChipStroke = Color.parseColor("#FFFFFF")

    // Dark palette for the options bottom sheet, so it matches the navy app theme
    // instead of the plain light PopupMenu the adapter was showing before.
    private val sheetBg = Color.parseColor("#16263A")
    private val sheetDivider = Color.parseColor("#28405A")
    private val sheetPrimaryText = Color.parseColor("#F5EDE4")
    private val sheetSecondaryText = Color.parseColor("#9FB3C8")
    private val sheetDestructive = Color.parseColor("#FF8A80")

    private var feedbackListener: ListenerRegistration? = null
    private val allFeedback = mutableListOf<Feedback>()
    private var selectedCategory: FeedbackCategory = FeedbackCategory.ALL

    private var pendingExport: (() -> Unit)? = null
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingExport?.invoke()
        } else {
            Toast.makeText(this, "Storage permission is required to export", Toast.LENGTH_SHORT).show()
        }
        pendingExport = null
    }

    private fun runWithStoragePermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            action()
            return
        }
        val permission = android.Manifest.permission.WRITE_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingExport = action
            storagePermissionLauncher.launch(permission)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_feedback)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = Firebase.firestore
        tvEmptyState = findViewById(R.id.tvEmptyState)
        recyclerView = findViewById(R.id.recyclerViewFeedback)
        etSearch = findViewById(R.id.etSearch)

        tabAllFeedback = findViewById(R.id.tabAllFeedback)
        tabSuggestions = findViewById(R.id.tabSuggestions)
        tabBugReports = findViewById(R.id.tabBugReports)
        tabComplaints = findViewById(R.id.tabComplaints)
        tabFeatureRequests = findViewById(R.id.tabFeatureRequests)
        tvTabAllFeedback = findViewById(R.id.tvTabAllFeedback)
        tvTabSuggestions = findViewById(R.id.tvTabSuggestions)
        tvTabBugReports = findViewById(R.id.tvTabBugReports)
        tvTabComplaints = findViewById(R.id.tvTabComplaints)
        tvTabFeatureRequests = findViewById(R.id.tvTabFeatureRequests)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FeedbackAdapter(
            items = mutableListOf(),
            onViewFull = { showFullFeedbackDialog(it) },
            onMoreClick = { feedback, _ -> showFeedbackOptionsMenu(feedback) }
        )
        recyclerView.adapter = adapter

        findViewById<View>(R.id.btnExportPdf).setOnClickListener {
            runWithStoragePermission { exportFeedbackToPdf() }
        }

        setupCategoryTabs()
        setupSearch()
        // Same shared bottom-nav helper the Users and Trades screens use, instead of
        // hand-tinting each icon/card here — one less place for the active-tab logic to drift.
        BottomNav.setup(this, BottomNav.FEEDBACK)
        loadFeedback()
    }

    override fun onDestroy() {
        super.onDestroy()
        feedbackListener?.remove()
    }

    // ─────────────────────── Filtering ───────────────────────

    private fun setupCategoryTabs() {
        tabAllFeedback.setOnClickListener { selectCategoryTab(FeedbackCategory.ALL) }
        tabSuggestions.setOnClickListener { selectCategoryTab(FeedbackCategory.SUGGESTION) }
        tabBugReports.setOnClickListener { selectCategoryTab(FeedbackCategory.BUG_REPORT) }
        tabComplaints.setOnClickListener { selectCategoryTab(FeedbackCategory.COMPLAINT) }
        tabFeatureRequests.setOnClickListener { selectCategoryTab(FeedbackCategory.FEATURE_REQUEST) }
        selectCategoryTab(FeedbackCategory.ALL)
    }

    private fun selectCategoryTab(category: FeedbackCategory) {
        selectedCategory = category
        val tabs = listOf(
            tabAllFeedback to tvTabAllFeedback,
            tabSuggestions to tvTabSuggestions,
            tabBugReports to tvTabBugReports,
            tabComplaints to tvTabComplaints,
            tabFeatureRequests to tvTabFeatureRequests
        )
        val selectedIndex = when (category) {
            FeedbackCategory.ALL -> 0
            FeedbackCategory.SUGGESTION -> 1
            FeedbackCategory.BUG_REPORT -> 2
            FeedbackCategory.COMPLAINT -> 3
            FeedbackCategory.FEATURE_REQUEST -> 4
        }
        val strokeWidthPx = (1 * resources.displayMetrics.density).toInt()
        tabs.forEachIndexed { index, (card, text) ->
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

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                applyFilters()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun applyFilters() {
        val query = etSearch.text?.toString()?.trim()?.lowercase(Locale.getDefault()).orEmpty()

        val filtered = allFeedback.filter { fb ->
            val matchesCategory = selectedCategory == FeedbackCategory.ALL ||
                    fb.category == selectedCategory.firestoreValue
            val matchesQuery = query.isEmpty() ||
                    fb.userName.lowercase(Locale.getDefault()).contains(query) ||
                    fb.id.lowercase(Locale.getDefault()).contains(query) ||
                    fb.title.lowercase(Locale.getDefault()).contains(query) ||
                    fb.message.lowercase(Locale.getDefault()).contains(query)
            matchesCategory && matchesQuery
        }

        adapter.submitList(filtered)
        tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    // ─────────────────────── Data loading ───────────────────────

    private fun loadFeedback() {
        // ASSUMPTION: "feedback" collection with fields matching Feedback data class.
        // Adjust field names in Feedback.kt / here if your schema differs.
        feedbackListener = db.collection("feedback")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                allFeedback.clear()
                snapshot?.documents?.forEach { doc ->
                    val fb = doc.toObject(Feedback::class.java)?.copy(id = doc.id)
                    if (fb != null) allFeedback.add(fb)
                }
                applyFilters()
            }
    }

    // ─────────────────────── Actions ───────────────────────

    /** Built in code, matching showUserOptionsMenu()/showTradeOptionsMenu() in the other admin screens. */
    private fun showFeedbackOptionsMenu(feedback: Feedback) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(this, R.style.DarkBottomSheetDialog)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            background = GradientDrawable().apply {
                val r = dp(20).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(sheetBg)
            }
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        val initial = feedback.userName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        header.addView(MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            radius = dp(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#EAE1DA"))
            addView(TextView(this@AdminFeedbackActivity).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                gravity = Gravity.CENTER
                text = initial
                setTextColor(Color.parseColor("#1B3C53"))
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
            text = feedback.userName.ifBlank { "Unknown user" }
            setTextColor(sheetPrimaryText)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        textCol.addView(TextView(this).apply {
            text = feedback.title.ifBlank { feedback.category.ifBlank { "Feedback" } }
            setTextColor(sheetSecondaryText)
            textSize = 12f
            setPadding(0, dp(2), 0, 0)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
        header.addView(textCol)
        root.addView(header)

        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(sheetDivider)
        })

        fun addRow(emoji: String, label: String, textColor: Int = sheetPrimaryText, action: () -> Unit) {
            val outValue = android.util.TypedValue()
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

        addRow("👁️", "View Full Feedback") { showFullFeedbackDialog(feedback) }
        addRow("👤", "View User Profile") { openUserProfile(feedback) }
        if (feedback.status != FeedbackStatus.READ.firestoreValue) {
            addRow("✅", "Mark as Read") { updateStatus(feedback, FeedbackStatus.READ.firestoreValue) }
        }
        if (feedback.status != FeedbackStatus.RESOLVED.firestoreValue) {
            addRow("🏁", "Mark as Resolved") { updateStatus(feedback, FeedbackStatus.RESOLVED.firestoreValue) }
        }
        addRow("🗑️", "Delete Feedback", sheetDestructive) { confirmDelete(feedback) }

        dialog.setContentView(root)
        dialog.show()
    }

    /** Styled like AdminTradesActivity's viewTradeDetails(): white rounded card, label/value rows, pill Close button. */
    private fun showFullFeedbackDialog(feedback: Feedback) {
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        val root = dialogCard()

        root.addView(dialogTitle(feedback.title.ifBlank { "Feedback Details" }))
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
                layoutParams = LinearLayout.LayoutParams(dp(90), LinearLayout.LayoutParams.WRAP_CONTENT)
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

        addDetailRow("From", feedback.userName.ifBlank { "—" })
        addDetailRow("Category", feedback.category.ifBlank { "—" })
        addDetailRow("Rating", if (feedback.rating > 0) "★".repeat(feedback.rating) + "☆".repeat(5 - feedback.rating) else "—")
        addDetailRow("Status", feedback.status.ifBlank { "new" }.replaceFirstChar { it.uppercase() })
        addDetailRow("Date", if (feedback.timestamp > 0) dateFormat.format(Date(feedback.timestamp)) else "—")

        root.addView(dialogDivider())
        root.addView(TextView(this).apply {
            text = feedback.message.ifBlank { "—" }
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 13.5f
        })

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

        if (feedback.status == FeedbackStatus.NEW.firestoreValue) {
            updateStatus(feedback, FeedbackStatus.READ.firestoreValue, silent = true)
        }
    }

    private fun openUserProfile(feedback: Feedback) {
        if (feedback.userId.isBlank()) {
            Toast.makeText(this, "No user ID on this feedback", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("users").whereEqualTo("email", feedback.userId).limit(1).get()
            .addOnSuccessListener { snapshot ->
                val doc = snapshot.documents.firstOrNull()
                val user = doc?.toObject(User::class.java)
                if (doc == null || user == null) {
                    Toast.makeText(this, "User not found (may have been deleted)", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                if (user.uid.isBlank()) user.uid = doc.id
                showUserProfileDialog(user)
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load user: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    /** Styled with the same dialogCard()/pillButton() helpers used across Users/Trades, instead of duplicated inline drawables. */
    private fun showUserProfileDialog(user: User) {
        val root = dialogCard()

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
        root.addView(avatarContainer)
        root.addView(dialogDivider())

        fun addRow(label: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
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
            root.addView(row)
        }

        addRow("Phone", user.phone.ifBlank { "—" })
        addRow("Location", user.location.ifBlank { "—" })
        addRow("Type", user.userType.ifBlank { "—" })
        addRow("Verified", if (user.isEmailVerified) "Yes" else "No")
        addRow("Blocked", if (user.isBlocked) "Yes" else "No")

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

    private fun updateStatus(feedback: Feedback, status: String, silent: Boolean = false) {
        if (feedback.id.isBlank()) return
        db.collection("feedback").document(feedback.id)
            .update("status", status)
            .addOnSuccessListener {
                if (!silent) {
                    Toast.makeText(this, "Marked as $status", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to update status", Toast.LENGTH_SHORT).show()
            }
    }

    /** Styled like AdminTradesActivity's confirmDeleteTrade(): white card, centered warning, Cancel/Delete pill row. */
    private fun confirmDelete(feedback: Feedback) {
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
            text = "Delete Feedback"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Are you sure you want to delete this feedback? This action cannot be undone."
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
                db.collection("feedback").document(feedback.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this@AdminFeedbackActivity, "Feedback deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@AdminFeedbackActivity, "Failed to delete", Toast.LENGTH_SHORT).show()
                    }
            }
        }
        buttonRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttonRow.addView(btnDelete, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    // ─────────────────────── Themed-dialog helpers (matches AdminTradesActivity / AdminUsersActivity style) ───────────────────────

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
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(12); bottomMargin = dp(12)
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

    // ─────────────────────── Export (Feedback) — Styled Table with Navy Banner ───────────────────────

    private fun exportFeedbackToPdf() {
        db.collection("feedback").get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(this, "No feedback to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                val feedbackDocs = snapshot.documents.mapNotNull {
                    it.toObject(Feedback::class.java)?.copy(id = it.id)
                }
                val fileName = "SKILLSWAP_FEEDBACK.pdf"
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

                val pageWidth = 842
                val pageHeight = 595
                val leftMargin = 24f
                val bottomMargin = pageHeight - 30f
                val rowHeight = 22f
                val headerRowHeight = 24f
                val bannerHeight = 70f

                val headers = listOf("User", "Title", "Category", "Rating", "Status", "Date")
                val tableWidth = pageWidth - (leftMargin * 2)
                val columnWeights = listOf(0.18f, 0.28f, 0.16f, 0.1f, 0.14f, 0.14f)
                val columnWidths = columnWeights.map { it * tableWidth }

                val navy = android.graphics.Color.parseColor("#1B3C53")
                val navyDark = android.graphics.Color.parseColor("#102838")

                val bannerBgPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
                val bannerAccentPaint = Paint().apply { color = navyDark; style = Paint.Style.FILL }
                val titlePaint = Paint().apply {
                    textSize = 22f; isFakeBoldText = true
                    color = android.graphics.Color.parseColor("#F9F3EF"); textAlign = Paint.Align.CENTER
                }
                val subtitlePaint = Paint().apply {
                    textSize = 11f
                    color = android.graphics.Color.parseColor("#CBD8E1")
                    textAlign = Paint.Align.CENTER
                }
                val headerPaint = Paint().apply {
                    textSize = 10f; isFakeBoldText = true; color = android.graphics.Color.parseColor("#F9F3EF")
                }
                val headerBgPaint = Paint().apply { color = navy; style = Paint.Style.FILL }
                val bodyPaint = Paint().apply { textSize = 9f; color = android.graphics.Color.parseColor("#1B3C53") }
                val borderPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#CCCCCC")
                    style = Paint.Style.STROKE; strokeWidth = 0.7f
                }
                val altRowBgPaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#EAF1F5"); style = Paint.Style.FILL
                }
                val footerPaint = Paint().apply {
                    textSize = 8f
                    color = android.graphics.Color.parseColor("#9CA3AF")
                    textAlign = Paint.Align.CENTER
                }

                val pdfDocument = PdfDocument()
                var pageNumber = 1
                var page = pdfDocument.startPage(
                    PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                )
                var canvas = page.canvas
                var y: Float

                fun drawBanner() {
                    canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, bannerBgPaint)
                    canvas.drawRect(0f, bannerHeight, pageWidth.toFloat(), bannerHeight + 4f, bannerAccentPaint)
                    val centerX = pageWidth / 2f
                    canvas.drawText("SkillSwap — Feedback Report", centerX, bannerHeight / 2f, titlePaint)
                    canvas.drawText(
                        "Generated: ${dateFormat.format(Date())}",
                        centerX, bannerHeight / 2f + 20f, subtitlePaint
                    )
                }

                fun drawTableHeader(startY: Float): Float {
                    var x = leftMargin
                    var localY = startY
                    canvas.drawRect(leftMargin, localY, leftMargin + tableWidth, localY + headerRowHeight, headerBgPaint)
                    headers.forEachIndexed { i, header ->
                        canvas.drawText(header, x + 4f, localY + headerRowHeight - 7f, headerPaint)
                        canvas.drawRect(x, localY, x + columnWidths[i], localY + headerRowHeight, borderPaint)
                        x += columnWidths[i]
                    }
                    return localY + headerRowHeight
                }

                fun drawFooter() {
                    canvas.drawText("Page $pageNumber", pageWidth / 2f, pageHeight - 12f, footerPaint)
                }

                fun truncate(text: String, maxWidth: Float, paint: Paint): String {
                    if (paint.measureText(text) <= maxWidth) return text
                    var end = text.length
                    while (end > 0 && paint.measureText(text.substring(0, end) + "…") > maxWidth) end--
                    return text.substring(0, end) + "…"
                }

                fun newPage() {
                    drawFooter()
                    pdfDocument.finishPage(page)
                    pageNumber++
                    page = pdfDocument.startPage(
                        PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    )
                    canvas = page.canvas
                    drawBanner()
                    y = drawTableHeader(bannerHeight + 24f)
                }

                drawBanner()
                y = drawTableHeader(bannerHeight + 24f)

                feedbackDocs.forEachIndexed { rowIndex, fb ->
                    if (y + rowHeight > bottomMargin) newPage()

                    val rowValues = listOf(
                        fb.userName.ifBlank { "—" },
                        fb.title.ifBlank { "—" },
                        fb.category.ifBlank { "—" },
                        if (fb.rating > 0) "${fb.rating}/5" else "—",
                        fb.status.ifBlank { "new" },
                        if (fb.timestamp > 0) dateFormat.format(Date(fb.timestamp)) else "—"
                    )

                    if (rowIndex % 2 == 1) {
                        canvas.drawRect(leftMargin, y, leftMargin + tableWidth, y + rowHeight, altRowBgPaint)
                    }

                    var x = leftMargin
                    rowValues.forEachIndexed { i, value ->
                        val cellPadding = 4f
                        val maxTextWidth = columnWidths[i] - (cellPadding * 2)
                        val displayText = truncate(value, maxTextWidth, bodyPaint)
                        canvas.drawText(displayText, x + cellPadding, y + rowHeight - 7f, bodyPaint)
                        canvas.drawRect(x, y, x + columnWidths[i], y + rowHeight, borderPaint)
                        x += columnWidths[i]
                    }
                    y += rowHeight
                }

                drawFooter()
                pdfDocument.finishPage(page)

                try {
                    val outputStream = openDownloadsOutputStream(fileName, "application/pdf")
                    outputStream?.use { pdfDocument.writeTo(it) }
                    Toast.makeText(this, "Saved to Downloads: $fileName", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                } finally {
                    pdfDocument.close()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load feedback: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun openDownloadsOutputStream(fileName: String, mimeType: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: return null
            contentResolver.openOutputStream(uri)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file)
        }
    }
}