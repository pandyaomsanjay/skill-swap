package com.example.sgp

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class AdminSkillsActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SkillAdapter
    private lateinit var etSearch: EditText
    private lateinit var categoryTabsContainer: LinearLayout
    private val allSkills = mutableListOf<Skill>()
    private val displayedSkills = mutableListOf<Skill>()
    private var currentQuery = ""
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    // Category chips shown above the list. "All" is always first; add/remove
    // entries here to match whatever categories skills are posted under.
    private val categories = listOf(
        "All", "Technology", "Arts", "Sports", "Home", "Education", "Lifestyle"
    )
    private var selectedCategory: String = "All"
    private val categoryChipViews = mutableListOf<Pair<MaterialCardView, TextView>>()

    // Same chip palette used on the Users/Trades/Feedback pages, so all admin
    // screens feel identical.
    private val selectedChipBg = Color.parseColor("#F9F3EF")
    private val unselectedChipBg = Color.parseColor("#456882")
    private val selectedChipText = Color.parseColor("#1B3C53")
    private val unselectedChipText = Color.parseColor("#FFFFFF")
    private val unselectedChipStroke = Color.parseColor("#FFFFFF")

    // Dark palette for the options bottom sheet, matching the navy app theme
    // used by the Users/Trades/Feedback options menus.
    private val sheetBg = Color.parseColor("#16263A")
    private val sheetDivider = Color.parseColor("#28405A")
    private val sheetPrimaryText = Color.parseColor("#F5EDE4")
    private val sheetSecondaryText = Color.parseColor("#9FB3C8")
    private val sheetDestructive = Color.parseColor("#FF8A80")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_skills)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = Firebase.firestore

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        etSearch = findViewById(R.id.etSearch)
        categoryTabsContainer = findViewById(R.id.categoryTabsContainer)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SkillAdapter(displayedSkills) { skill ->
            showSkillOptionsDialog(skill)
        }
        recyclerView.adapter = adapter

        setupCategoryTabs()
        setupSearch()
        loadSkills()
    }

    // ─────────────────────── Filtering ───────────────────────

    /** Builds one chip per entry in `categories` and adds it to categoryTabsContainer. */
    private fun setupCategoryTabs() {
        categoryTabsContainer.removeAllViews()
        categoryChipViews.clear()

        categories.forEachIndexed { index, category ->
            val chipText = TextView(this).apply {
                text = category
                textSize = 13f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(18), dp(9), dp(18), dp(9))
            }
            val chip = MaterialCardView(this).apply {
                radius = dp(18).toFloat()
                cardElevation = 0f
                strokeWidth = dp(1)
                isClickable = true
                isFocusable = true
                foreground = rippleForeground()
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dp(8)
                    if (index == 0) marginStart = 0
                }
                addView(chipText)
                setOnClickListener { selectCategoryTab(category) }
            }
            categoryTabsContainer.addView(chip)
            categoryChipViews.add(chip to chipText)
        }

        selectCategoryTab(selectedCategory)
    }

    private fun selectCategoryTab(category: String) {
        selectedCategory = category
        categories.forEachIndexed { index, cat ->
            val (chip, text) = categoryChipViews[index]
            if (cat == category) {
                chip.setCardBackgroundColor(selectedChipBg)
                chip.strokeWidth = 0
                text.setTextColor(selectedChipText)
            } else {
                chip.setCardBackgroundColor(unselectedChipBg)
                chip.strokeWidth = dp(1)
                chip.strokeColor = unselectedChipStroke
                text.setTextColor(unselectedChipText)
            }
        }
        applyFilters()
    }

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
        val filtered = allSkills.filter { skill ->
            val matchesCategory = selectedCategory == "All" ||
                    skill.category.equals(selectedCategory, ignoreCase = true)
            val matchesQuery = currentQuery.isBlank() ||
                    skill.title.lowercase(Locale.getDefault()).contains(currentQuery) ||
                    skill.category.lowercase(Locale.getDefault()).contains(currentQuery) ||
                    skill.userName.lowercase(Locale.getDefault()).contains(currentQuery)
            matchesCategory && matchesQuery
        }
        displayedSkills.clear()
        displayedSkills.addAll(filtered)
        adapter.notifyDataSetChanged()
    }

    private fun loadSkills() {
        listener = db.collection("skills")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this@AdminSkillsActivity, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                allSkills.clear()
                snapshot?.documents?.forEach { doc ->
                    doc.toObject(Skill::class.java)?.let { allSkills.add(it) }
                }
                applyFilters()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
    }

    /** Built in code, matching showUserOptionsMenu()/showTradeOptionsMenu() in the other admin screens. */
    private fun showSkillOptionsDialog(skill: Skill) {
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

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }
        header.addView(TextView(this).apply {
            text = skill.title.ifBlank { "Skill" }
            setTextColor(sheetPrimaryText)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        })
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

        addRow("📋", "View Details") { viewSkillDetails(skill) }
        addRow("👤", "View User Profile") { openUserProfile(skill) }
        addRow("🗑️", "Delete Skill", sheetDestructive) { deleteSkill(skill) }

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    /** Styled like AdminTradesActivity's viewTradeDetails(): white rounded card, label/value rows, pill Close button. */
    private fun viewSkillDetails(skill: Skill) {
        val root = dialogCard()
        root.addView(dialogTitle("Skill Details"))
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

        addDetailRow("Title", skill.title.ifBlank { "—" })
        addDetailRow("Category", skill.category.ifBlank { "—" })
        addDetailRow("Posted by", skill.userName.ifBlank { "—" })

        root.addView(dialogDivider())
        root.addView(TextView(this).apply {
            text = skill.description.ifBlank { "—" }
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
    }

    /** ASSUMPTION: Skill.userId stores the poster's email, matching the pattern used by Feedback.userId.
     *  If your Skill data class uses a different field name for this, swap it below. */
    private fun openUserProfile(skill: Skill) {
        if (skill.userId.isBlank()) {
            Toast.makeText(this, "No user ID on this skill", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("users").whereEqualTo("email", skill.userId).limit(1).get()
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

    /** Styled with the same dialogCard()/pillButton() helpers used across Users/Trades/Feedback. */
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

    /** Styled like AdminTradesActivity's confirmDeleteTrade(): white card, centered warning, Cancel/Delete pill row. */
    private fun deleteSkill(skill: Skill) {
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
            text = "Delete Skill"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Are you sure you want to delete this skill? This action cannot be undone."
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
                db.collection("skills").document(skill.id).delete()
                    .addOnSuccessListener { Toast.makeText(this@AdminSkillsActivity, "Deleted", Toast.LENGTH_SHORT).show() }
                    .addOnFailureListener { Toast.makeText(this@AdminSkillsActivity, "Failed", Toast.LENGTH_SHORT).show() }
            }
        }
        buttonRow.addView(btnCancel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttonRow.addView(btnDelete, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    // ─────────────────────── Themed-dialog helpers (matches other admin screens' style) ───────────────────────

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Simple ripple foreground for chip touch feedback, resolved from the current theme. */
    private fun rippleForeground(): android.graphics.drawable.Drawable? {
        val outValue = android.util.TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return try {
            androidx.core.content.ContextCompat.getDrawable(this, outValue.resourceId)
        } catch (e: Exception) {
            null
        }
    }

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

    class SkillAdapter(
        private val skills: List<Skill>,
        private val onItemClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<SkillAdapter.ViewHolder>() {
        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_skill, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]
            holder.tvTitle.text = skill.title
            holder.tvCategory.text = skill.category
            holder.tvUserName.text = "By: ${skill.userName}"
            holder.itemView.setOnClickListener { onItemClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}