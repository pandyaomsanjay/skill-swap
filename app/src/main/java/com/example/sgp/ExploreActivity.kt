package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.util.Locale

enum class SkillCategoryTab {
    ALL, TECHNOLOGY, ARTS, SPORTS, HOME, EDUCATION, LIFESTYLE
}

enum class SkillSourceTab {
    ALL, PURCHASED
}

data class LiveProgress(val completedCount: Int, val totalVideos: Int)

class ExploreActivity : BaseActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyState: View
    private lateinit var tvEmptyStateMessage: TextView
    private lateinit var etSearch: EditText
    private lateinit var adapter: SkillAdapter
    private lateinit var db: FirebaseFirestore
    private var listener: ListenerRegistration? = null

    private val allSkills = mutableListOf<Skill>()
    private val displayedSkills = mutableListOf<Skill>()

    private var currentTab = SkillCategoryTab.ALL
    private var currentQuery = ""

    private var currentSourceTab = SkillSourceTab.ALL
    private val purchasedPlaylistIds = mutableSetOf<String>()
    private var purchasedListener: ListenerRegistration? = null
    private var currentUserEmail: String? = null

    private var resolvedUid: String? = null
    private val liveProgressMap = mutableMapOf<String, LiveProgress>()
    private var progressListener: ListenerRegistration? = null

    private lateinit var btnChatInbox: View
    private lateinit var unreadBadge: View
    private var chatsListener: ListenerRegistration? = null

    private lateinit var tabAll: MaterialCardView
    private lateinit var tabTechnology: MaterialCardView
    private lateinit var tabArts: MaterialCardView
    private lateinit var tabSports: MaterialCardView
    private lateinit var tabHome: MaterialCardView
    private lateinit var tabEducation: MaterialCardView
    private lateinit var tabLifestyle: MaterialCardView

    private lateinit var tvTabAll: TextView
    private lateinit var tvTabTechnology: TextView
    private lateinit var tvTabArts: TextView
    private lateinit var tvTabSports: TextView
    private lateinit var tvTabHome: TextView
    private lateinit var tvTabEducation: TextView
    private lateinit var tvTabLifestyle: TextView

    private lateinit var tabSourceAll: MaterialCardView
    private lateinit var tabSourcePurchased: MaterialCardView
    private lateinit var tvTabSourceAll: TextView
    private lateinit var tvTabSourcePurchased: TextView

    // ---- Theme-affected views ----
    private lateinit var rootLayout: View
    private lateinit var headerBar: View
    private lateinit var searchCard: MaterialCardView
    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    companion object {
        const val PREFS_NAME = "SkillSwapPrefs"
        const val KEY_DARK_MODE = "dark_mode"
    }

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightSearchBg = Color.parseColor("#456882")
    private val lightTabUnselectedBg = Color.parseColor("#456882")
    private val lightTabUnselectedText = Color.parseColor("#D2C1B6")
    private val lightTabSelectedBg = Color.parseColor("#F9F3EF")
    private val lightTabSelectedText = Color.parseColor("#1B3C53")
    private val lightEmptyStateText = Color.parseColor("#456882")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkSearchBg = Color.parseColor("#22323D")
    private val darkTabUnselectedBg = Color.parseColor("#22323D")
    private val darkTabUnselectedText = Color.parseColor("#8FA6B8")
    private val darkTabSelectedBg = Color.parseColor("#16232E")
    private val darkTabSelectedText = Color.parseColor("#F5F7F8")
    private val darkEmptyStateText = Color.parseColor("#8FA6B8")

    private val reportReasons = arrayOf(
        "Inappropriate or Offensive Content",
        "Spam or Misleading Information",
        "Fake Skill or Scam",
        "Harassment or Abusive Behavior",
        "Copyright or Intellectual Property Violation"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_explore)

        db = Firebase.firestore
        currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(KEY_DARK_MODE, false)

        bindViews()
        setupChatInbox()
        setupSourceTabs()
        setupTabs()
        setupSearch()
        setupBottomNav()

        applyTheme(isDarkModeEnabled)

        loadSkills()
        resolveUidThenListenForPurchasedAndProgress()
    }

    override fun onResume() {
        super.onResume()
        // Pick up a dark-mode change made on the Settings screen
        val storedDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        if (storedDarkMode != isDarkModeEnabled) {
            applyTheme(storedDarkMode)
        }
        // Refresh progress when returning to this activity
        refreshProgress()
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        headerBar = findViewById(R.id.headerBar)
        searchCard = findViewById(R.id.searchCard)

        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)
        tvEmptyStateMessage = findViewById(R.id.tvEmptyStateMessage)
        etSearch = findViewById(R.id.etSearch)

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = SkillAdapter(
            displayedSkills,
            currentUserEmail = currentUserEmail,
            purchasedPlaylistIds = purchasedPlaylistIds,
            liveProgressMap = liveProgressMap,
            isDarkMode = isDarkModeEnabled,
            onItemClick = { skill -> openSkill(skill) },
            onReportClick = { skill -> showReportDialog(skill) },
            onUserClick = { skill -> openUserProfile(skill) },
            onPreviewClick = { skill -> playDemoVideo(skill) },
            onUnlockClick = { skill -> openPlaylist(skill) }
        )
        recyclerView.adapter = adapter

        btnChatInbox = findViewById(R.id.btnChatInbox)
        unreadBadge = findViewById(R.id.unreadBadge)

        tabSourceAll = findViewById(R.id.tabSourceAll)
        tabSourcePurchased = findViewById(R.id.tabSourcePurchased)
        tvTabSourceAll = findViewById(R.id.tvTabSourceAll)
        tvTabSourcePurchased = findViewById(R.id.tvTabSourcePurchased)

        tabAll = findViewById(R.id.tabAll)
        tabTechnology = findViewById(R.id.tabTechnology)
        tabArts = findViewById(R.id.tabArts)
        tabSports = findViewById(R.id.tabSports)
        tabHome = findViewById(R.id.tabHome)
        tabEducation = findViewById(R.id.tabEducation)
        tabLifestyle = findViewById(R.id.tabLifestyle)

        tvTabAll = findViewById(R.id.tvTabAll)
        tvTabTechnology = findViewById(R.id.tvTabTechnology)
        tvTabArts = findViewById(R.id.tvTabArts)
        tvTabSports = findViewById(R.id.tvTabSports)
        tvTabHome = findViewById(R.id.tvTabHome)
        tvTabEducation = findViewById(R.id.tvTabEducation)
        tvTabLifestyle = findViewById(R.id.tvTabLifestyle)
    }

    // ---------------------------------------------------------------------
    // THEME ENGINE
    // ---------------------------------------------------------------------
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val searchBg = if (isDark) darkSearchBg else lightSearchBg
        val emptyStateText = if (isDark) darkEmptyStateText else lightEmptyStateText

        rootLayout.setBackgroundColor(screenBg)
        headerBar.setBackgroundColor(headerBg)
        searchCard.setCardBackgroundColor(searchBg)
        searchCard.strokeColor = searchBg

        tvEmptyStateMessage.setTextColor(emptyStateText)

        // Re-apply currently selected tab states with theme-correct colors
        selectSourceTab(currentSourceTab)
        selectTab(currentTab)

        // Push the theme flag into the adapter and redraw items
        adapter.isDarkMode = isDark
        adapter.notifyDataSetChanged()
    }

    private fun setupChatInbox() {
        btnChatInbox.setOnClickListener {
            startActivity(Intent(this, ChatListActivity::class.java))
        }
        listenForUnreadChats()
    }

    private fun setupBottomNav() {
        BottomNavHelper.setup(this, BottomNavItem.EXPLORE)
    }

    private fun listenForUnreadChats() {
        val myEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        chatsListener = db.collection("chats")
            .whereArrayContains("participants", myEmail)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExploreActivity", "Unread chats listener error", error)
                    return@addSnapshotListener
                }
                try {
                    val hasUnread = snapshot?.documents?.any { doc ->
                        val unreadFor = doc.get("unreadFor") as? List<*>
                        unreadFor?.contains(myEmail) == true
                    } ?: false
                    unreadBadge.visibility = if (hasUnread) View.VISIBLE else View.GONE
                } catch (e: Exception) {
                    Log.e("ExploreActivity", "Failed processing unread chats", e)
                }
            }
    }

    private fun setupSourceTabs() {
        tabSourceAll.setOnClickListener { selectSourceTab(SkillSourceTab.ALL) }
        tabSourcePurchased.setOnClickListener { selectSourceTab(SkillSourceTab.PURCHASED) }
        selectSourceTab(SkillSourceTab.ALL)
    }

    private fun selectSourceTab(tab: SkillSourceTab) {
        currentSourceTab = tab

        val selectedBg = if (isDarkModeEnabled) darkTabSelectedBg else lightTabSelectedBg
        val selectedText = if (isDarkModeEnabled) darkTabSelectedText else lightTabSelectedText
        val unselectedBg = if (isDarkModeEnabled) darkTabUnselectedBg else lightTabUnselectedBg
        val unselectedText = if (isDarkModeEnabled) darkTabUnselectedText else lightTabUnselectedText

        if (tab == SkillSourceTab.ALL) {
            tabSourceAll.setCardBackgroundColor(selectedBg)
            tvTabSourceAll.setTextColor(selectedText)
            tabSourcePurchased.setCardBackgroundColor(unselectedBg)
            tvTabSourcePurchased.setTextColor(unselectedText)
        } else {
            tabSourcePurchased.setCardBackgroundColor(selectedBg)
            tvTabSourcePurchased.setTextColor(selectedText)
            tabSourceAll.setCardBackgroundColor(unselectedBg)
            tvTabSourceAll.setTextColor(unselectedText)
        }

        if (tab == SkillSourceTab.PURCHASED) {
            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(this, "Log in to see what you've purchased", Toast.LENGTH_SHORT).show()
            }
        }

        applyFilters()
    }

    /**
     * SIMPLIFIED: Now uses Firebase Auth for ALL users
     * (Firebase users are created for Supabase logins)
     */
    private fun resolveUidThenListenForPurchasedAndProgress() {
        val firebaseUser = FirebaseAuth.getInstance().currentUser
        if (firebaseUser != null) {
            resolvedUid = firebaseUser.uid
            currentUserEmail = firebaseUser.email
            Log.d("ExploreActivity", "✅ User found: ${firebaseUser.email}, uid: ${firebaseUser.uid}")
            listenForPurchasedPlaylists(firebaseUser.uid)
            listenForProgress(firebaseUser.uid)
        } else {
            Log.d("ExploreActivity", "❌ No user logged in")
        }
    }

    private fun listenForPurchasedPlaylists(uid: String) {
        purchasedListener = db.collection("users").document(uid)
            .collection("purchasedPlaylists")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExploreActivity", "Purchased playlists listener error", error)
                    return@addSnapshotListener
                }
                purchasedPlaylistIds.clear()
                snapshot?.documents?.forEach { doc ->
                    if (doc.id != "_init") {
                        purchasedPlaylistIds.add(doc.id)
                    }
                }
                Log.d("ExploreActivity", "Loaded ${purchasedPlaylistIds.size} purchased playlists")
                applyFilters()
            }
    }

    private fun listenForProgress(uid: String) {
        progressListener = db.collection("users").document(uid)
            .collection("playlistProgress")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e("ExploreActivity", "Progress listener error", error)
                    return@addSnapshotListener
                }
                liveProgressMap.clear()
                snapshot?.documents?.forEach { doc ->
                    if (doc.id != "_init") {
                        @Suppress("UNCHECKED_CAST")
                        val completedVideos = doc.get("completedVideos") as? List<String> ?: emptyList()
                        liveProgressMap[doc.id] = LiveProgress(
                            completedCount = completedVideos.size,
                            totalVideos = 0
                        )
                        Log.d("ExploreActivity", "Progress for ${doc.id}: ${completedVideos.size} videos")
                    }
                }
                adapter.notifyDataSetChanged()
            }
    }

    /**
     * Manually refresh progress data
     * Called when returning to this activity
     */
    private fun refreshProgress() {
        val uid = resolvedUid
        if (uid != null) {
            Log.d("ExploreActivity", "🔄 Manually refreshing progress for uid: $uid")
            db.collection("users").document(uid)
                .collection("playlistProgress")
                .get()
                .addOnSuccessListener { snapshot ->
                    liveProgressMap.clear()
                    snapshot.documents.forEach { doc ->
                        if (doc.id != "_init") {
                            @Suppress("UNCHECKED_CAST")
                            val completedVideos = doc.get("completedVideos") as? List<String> ?: emptyList()
                            liveProgressMap[doc.id] = LiveProgress(
                                completedCount = completedVideos.size,
                                totalVideos = 0
                            )
                            Log.d("ExploreActivity", "Progress for ${doc.id}: ${completedVideos.size} videos")
                        }
                    }
                    adapter.notifyDataSetChanged()
                    Log.d("ExploreActivity", "✅ Progress manually refreshed")
                }
                .addOnFailureListener { e ->
                    Log.e("ExploreActivity", "Failed to refresh progress: ${e.message}")
                }
        }
    }

    private fun setupTabs() {
        tabAll.setOnClickListener { selectTab(SkillCategoryTab.ALL) }
        tabTechnology.setOnClickListener { selectTab(SkillCategoryTab.TECHNOLOGY) }
        tabArts.setOnClickListener { selectTab(SkillCategoryTab.ARTS) }
        tabSports.setOnClickListener { selectTab(SkillCategoryTab.SPORTS) }
        tabHome.setOnClickListener { selectTab(SkillCategoryTab.HOME) }
        tabEducation.setOnClickListener { selectTab(SkillCategoryTab.EDUCATION) }
        tabLifestyle.setOnClickListener { selectTab(SkillCategoryTab.LIFESTYLE) }
        selectTab(SkillCategoryTab.ALL)
    }

    private fun selectTab(tab: SkillCategoryTab) {
        currentTab = tab

        val selectedBg = if (isDarkModeEnabled) darkTabSelectedBg else lightTabSelectedBg
        val selectedText = if (isDarkModeEnabled) darkTabSelectedText else lightTabSelectedText
        val unselectedText = if (isDarkModeEnabled) darkTabUnselectedText else lightTabUnselectedText

        val tabs = listOf(
            tabAll to tvTabAll,
            tabTechnology to tvTabTechnology,
            tabArts to tvTabArts,
            tabSports to tvTabSports,
            tabHome to tvTabHome,
            tabEducation to tvTabEducation,
            tabLifestyle to tvTabLifestyle
        )
        val selectedIndex = when (tab) {
            SkillCategoryTab.ALL -> 0
            SkillCategoryTab.TECHNOLOGY -> 1
            SkillCategoryTab.ARTS -> 2
            SkillCategoryTab.SPORTS -> 3
            SkillCategoryTab.HOME -> 4
            SkillCategoryTab.EDUCATION -> 5
            SkillCategoryTab.LIFESTYLE -> 6
        }
        tabs.forEachIndexed { index, (card, text) ->
            if (index == selectedIndex) {
                card.setCardBackgroundColor(selectedBg)
                text.setTextColor(selectedText)
            } else {
                card.setCardBackgroundColor(Color.TRANSPARENT)
                text.setTextColor(unselectedText)
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
        val categoryFiltered = allSkills.filter { skill ->
            when (currentTab) {
                SkillCategoryTab.ALL -> true
                SkillCategoryTab.TECHNOLOGY -> skill.category.equals("Technology", ignoreCase = true)
                SkillCategoryTab.ARTS -> skill.category.equals("Arts", ignoreCase = true)
                SkillCategoryTab.SPORTS -> skill.category.equals("Sports", ignoreCase = true)
                SkillCategoryTab.HOME -> skill.category.equals("Home", ignoreCase = true)
                SkillCategoryTab.EDUCATION -> skill.category.equals("Education", ignoreCase = true)
                SkillCategoryTab.LIFESTYLE -> skill.category.equals("Lifestyle", ignoreCase = true)
            }
        }

        val sourceFiltered = if (currentSourceTab == SkillSourceTab.ALL) {
            categoryFiltered
        } else {
            categoryFiltered.filter { skill -> purchasedPlaylistIds.contains(skill.id) }
        }

        val fullyFiltered = if (currentQuery.isBlank()) {
            sourceFiltered
        } else {
            sourceFiltered.filter {
                it.title.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.category.lowercase(Locale.getDefault()).contains(currentQuery) ||
                        it.description.lowercase(Locale.getDefault()).contains(currentQuery)
            }
        }

        displayedSkills.clear()
        displayedSkills.addAll(fullyFiltered)
        adapter.notifyDataSetChanged()

        tvEmptyStateMessage.text = if (currentSourceTab == SkillSourceTab.PURCHASED) {
            if (FirebaseAuth.getInstance().currentUser == null) {
                "Log in to see what you've purchased"
            } else {
                "You haven't purchased any playlist with points yet"
            }
        } else {
            "No skills found"
        }
        emptyState.visibility = if (displayedSkills.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun loadSkills() {
        listener = db.collection("skills")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, "Load failed: ${error.message}", Toast.LENGTH_LONG).show()
                    return@addSnapshotListener
                }
                allSkills.clear()
                snapshot?.documents?.forEach { doc ->
                    try {
                        doc.toObject(Skill::class.java)?.let {
                            it.id = doc.id
                            allSkills.add(it)
                        }
                    } catch (e: Exception) {
                        Log.e("ExploreActivity", "Skipping malformed skill doc: ${doc.id}", e)
                    }
                }
                applyFilters()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        listener?.remove()
        chatsListener?.remove()
        purchasedListener?.remove()
        progressListener?.remove()
    }

    // ---------- Actions ----------
    private fun openSkill(skill: Skill) {
        if (skill.skillType == "playlist") {
            openPlaylist(skill)
        } else if (skill.skillType == "single" && !skill.videoUrl.isNullOrEmpty()) {
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("videoUrl", skill.videoUrl)
            intent.putExtra("skillId", skill.id)
            intent.putExtra("skillTitle", skill.title)
            intent.putExtra("skillCategory", skill.category)
            intent.putExtra("skillUserId", skill.userId)
            intent.putExtra("skillUserName", skill.userName)
            intent.putExtra("skillCredits", skill.credits)
            intent.putExtra("skillDescription", skill.description)
            startActivity(intent)
        } else {
            Toast.makeText(this, "No video available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPlaylist(skill: Skill) {
        PlaylistActivity.start(this, skill.id)
    }

    private fun playDemoVideo(skill: Skill) {
        val demoUrl = skill.demoVideoUrl
        if (demoUrl.isNullOrEmpty()) {
            Toast.makeText(this, "No demo available", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("videoUrl", demoUrl)
        intent.putExtra("skillTitle", "Preview: ${skill.title}")
        intent.putExtra("skillCategory", skill.category)
        intent.putExtra("skillUserId", skill.userId)
        intent.putExtra("skillUserName", skill.userName)
        intent.putExtra("skillCredits", 0)
        intent.putExtra("skillDescription", "Demo video for playlist")
        startActivity(intent)
    }

    // ---------- Report dialog ----------
    private fun showReportDialog(skill: Skill) {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to report", Toast.LENGTH_SHORT).show()
            return
        }
        if (reporterEmail == skill.userId) {
            Toast.makeText(this, "You can't report your own skill", Toast.LENGTH_SHORT).show()
            return
        }

        val root = dialogCard()
        root.addView(dialogTitle("Report \"${skill.title}\""))
        root.addView(TextView(this).apply {
            text = "Help us understand what's wrong"
            setTextColor(currentTextSecondary())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        reportReasons.forEachIndexed { index, reason ->
            root.addView(reportReasonRow(reason) {
                dialog.dismiss()
                submitReport(skill, reason, reporterEmail)
            })
            if (index != reportReasons.lastIndex) {
                root.addView(dividerLine())
            }
        }

        val btnCancel = pillButton(
            "Cancel",
            currentSecondaryBtnBg(),
            currentSecondaryBtnText()
        ).apply {
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
        root.addView(btnCancel)

        dialog.show()
    }

    private fun reportReasonRow(text: String, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun submitReport(skill: Skill, reason: String, reporterEmail: String) {
        val docRef = db.collection("reports").document()
        val report = Report(
            id = docRef.id,
            reporterId = reporterEmail,
            reportedUserId = skill.userId,
            skillId = skill.id,
            reason = reason,
            description = "Reported skill: \"${skill.title}\" (Skill ID: ${skill.id})",
            status = "pending",
            timestamp = System.currentTimeMillis()
        )
        docRef.set(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted. Our team will review it.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ---------- Themed-dialog helpers ----------
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun currentDialogBg(): Int =
        if (isDarkModeEnabled) Color.parseColor("#16232E") else Color.WHITE

    private fun currentTextPrimary(): Int =
        if (isDarkModeEnabled) Color.parseColor("#F5F7F8") else Color.parseColor("#1B3C53")

    private fun currentTextSecondary(): Int =
        if (isDarkModeEnabled) Color.parseColor("#8FA6B8") else Color.parseColor("#456882")

    private fun currentSecondaryBtnBg(): Int =
        if (isDarkModeEnabled) Color.parseColor("#2A3B48") else Color.parseColor("#EAF1F5")

    private fun currentSecondaryBtnText(): Int =
        if (isDarkModeEnabled) Color.parseColor("#F5F7F8") else Color.parseColor("#456882")

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(currentDialogBg())
            }
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
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
            setBackgroundColor(if (isDarkModeEnabled) Color.parseColor("#2A3B48") else Color.parseColor("#EAF1F5"))
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

    private fun openUserProfile(skill: Skill) {
        val intent = Intent(this, UserProfileActivity::class.java)
        intent.putExtra("userId", skill.userId)
        intent.putExtra("userName", skill.userName)
        startActivity(intent)
    }

    // ---------- Adapter ----------
    class SkillAdapter(
        private val skills: List<Skill>,
        private val currentUserEmail: String?,
        private val purchasedPlaylistIds: Set<String>,
        private val liveProgressMap: Map<String, LiveProgress>,
        var isDarkMode: Boolean,
        private val onItemClick: (Skill) -> Unit,
        private val onReportClick: (Skill) -> Unit,
        private val onUserClick: (Skill) -> Unit,
        private val onPreviewClick: (Skill) -> Unit,
        private val onUnlockClick: (Skill) -> Unit
    ) : RecyclerView.Adapter<SkillAdapter.ViewHolder>() {

        // Light palette
        private val lightCardBg = Color.parseColor("#FFFFFF")
        private val lightCardStroke = Color.parseColor("#D2C1B6")
        private val lightTextPrimary = Color.parseColor("#1B3C53")
        private val lightTextSecondary = Color.parseColor("#456882")
        private val lightIconBgNeutral = Color.parseColor("#F9F3EF")
        private val lightBtnPreviewBg = Color.parseColor("#456882")
        private val lightBtnUnlockBg = Color.parseColor("#1B3C53")
        private val lightBtnViewProfileBg = Color.parseColor("#1B3C53")

        // Dark palette
        private val darkCardBg = Color.parseColor("#16232E")
        private val darkCardStroke = Color.parseColor("#2A3B48")
        private val darkTextPrimary = Color.parseColor("#F5F7F8")
        private val darkTextSecondary = Color.parseColor("#8FA6B8")
        private val darkIconBgNeutral = Color.parseColor("#26343F")
        private val darkBtnPreviewBg = Color.parseColor("#22323D")
        private val darkBtnUnlockBg = Color.parseColor("#0B1620")
        private val darkBtnViewProfileBg = Color.parseColor("#0B1620")

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val rootCard: MaterialCardView = itemView as MaterialCardView
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvCategory: TextView = itemView.findViewById(R.id.tvCategory)
            val tvUser: TextView = itemView.findViewById(R.id.tvUser)
            val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
            val btnReport: MaterialCardView = itemView.findViewById(R.id.btnReport)
            val ivReportIcon: ImageView = itemView.findViewById(R.id.ivReportIcon)
            val btnViewProfile: MaterialCardView = itemView.findViewById(R.id.btnViewProfile)
            val tvSwapsCount: TextView = itemView.findViewById(R.id.tvSwapsCount)
            val playlistInfoLayout: LinearLayout = itemView.findViewById(R.id.playlistInfoLayout)
            val tvVideoCount: TextView = itemView.findViewById(R.id.tvVideoCount)
            val tvTotalDuration: TextView = itemView.findViewById(R.id.tvTotalDuration)
            val btnPreview: MaterialCardView = itemView.findViewById(R.id.btnPreview)
            val tvBtnPreview: TextView = itemView.findViewById(R.id.tvBtnPreview)
            val btnUnlock: MaterialCardView = itemView.findViewById(R.id.btnUnlock)
            val tvBtnUnlock: TextView = itemView.findViewById(R.id.tvBtnUnlock)
            val singleInfoLayout: LinearLayout = itemView.findViewById(R.id.singleInfoLayout)
            val ivPlayIcon: ImageView = itemView.findViewById(R.id.ivPlayIcon)
            val tvAccessCount: TextView = itemView.findViewById(R.id.tvAccessCount)
            val tvCardProgressLabel: TextView = itemView.findViewById(R.id.tvCardProgressLabel)
            val progressIndicatorCard: LinearProgressIndicator = itemView.findViewById(R.id.progressIndicatorCard)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_explore_skill, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val skill = skills[position]

            val isOwnUpload = currentUserEmail != null && currentUserEmail == skill.userId
            val isPurchased = purchasedPlaylistIds.contains(skill.id)

            // ---- Theme colors for this bind ----
            val cardBg = if (isDarkMode) darkCardBg else lightCardBg
            val cardStroke = if (isDarkMode) darkCardStroke else lightCardStroke
            val textPrimary = if (isDarkMode) darkTextPrimary else lightTextPrimary
            val textSecondary = if (isDarkMode) darkTextSecondary else lightTextSecondary
            val iconBgNeutral = if (isDarkMode) darkIconBgNeutral else lightIconBgNeutral
            val btnPreviewBg = if (isDarkMode) darkBtnPreviewBg else lightBtnPreviewBg
            val btnUnlockBg = if (isDarkMode) darkBtnUnlockBg else lightBtnUnlockBg
            val btnViewProfileBg = if (isDarkMode) darkBtnViewProfileBg else lightBtnViewProfileBg

            holder.rootCard.setCardBackgroundColor(cardBg)
            holder.rootCard.strokeColor = cardStroke

            holder.tvTitle.setTextColor(textPrimary)
            holder.tvCategory.setTextColor(textSecondary)
            holder.tvVideoCount.setTextColor(textSecondary)
            holder.tvTotalDuration.setTextColor(textSecondary)
            holder.tvSwapsCount.setTextColor(textSecondary)
            holder.tvCardProgressLabel.setTextColor(textPrimary)

            holder.btnReport.setCardBackgroundColor(iconBgNeutral)
            holder.ivReportIcon.imageTintList = ColorStateList.valueOf(textSecondary)

            holder.btnViewProfile.setCardBackgroundColor(btnViewProfileBg)
            holder.btnPreview.setCardBackgroundColor(btnPreviewBg)
            holder.tvBtnPreview.setTextColor(Color.parseColor("#F9F3EF"))

            holder.tvTitle.text = skill.title
            holder.tvCategory.text = skill.category
            holder.tvUser.text = "by ${skill.userName.uppercase()}"

            val thumbnailUrl = when {
                skill.skillType == "playlist" && !skill.thumbnailUrl.isNullOrEmpty() -> skill.thumbnailUrl
                else -> skill.videoUrl
            }

            if (!thumbnailUrl.isNullOrEmpty()) {
                Glide.with(holder.itemView.context)
                    .load(thumbnailUrl)
                    .placeholder(R.drawable.baseline_videocam_24)
                    .error(R.drawable.baseline_videocam_24)
                    .into(holder.ivThumbnail)
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            }

            if (skill.skillType == "single") {
                holder.ivPlayIcon.visibility = View.VISIBLE
            } else {
                holder.ivPlayIcon.visibility = View.GONE
            }

            // Show report button for ALL skills except own uploads
            holder.btnReport.visibility = if (!isOwnUpload) View.VISIBLE else View.GONE
            holder.btnReport.setOnClickListener { onReportClick(skill) }

            holder.btnViewProfile.setOnClickListener { onUserClick(skill) }

            if (skill.skillType == "playlist") {
                holder.playlistInfoLayout.visibility = View.VISIBLE
                holder.singleInfoLayout.visibility = View.GONE

                holder.tvCredits.text = when {
                    isOwnUpload -> "Free • Your upload"
                    isPurchased -> "Unlocked"
                    else -> "${skill.credits} credits"
                }
                holder.tvVideoCount.text = "${skill.videoCount} videos"
                holder.tvTotalDuration.text = skill.totalDuration ?: "—"

                if (isOwnUpload) {
                    holder.tvAccessCount.visibility = View.VISIBLE
                    val count = skill.accessCount
                    holder.tvAccessCount.text = if (count == 1) {
                        "1 person has access"
                    } else {
                        "$count people have access"
                    }
                } else {
                    holder.tvAccessCount.visibility = View.GONE
                }

                // Green progress bar when 100% complete
                if (isPurchased && !isOwnUpload) {
                    val live = liveProgressMap[skill.id]
                    val completed = live?.completedCount ?: 0
                    val total = skill.videoCount.takeIf { it > 0 } ?: 1
                    val percent = (completed * 100) / total

                    holder.tvCardProgressLabel.visibility = View.VISIBLE
                    holder.progressIndicatorCard.visibility = View.VISIBLE
                    holder.tvCardProgressLabel.text = "$completed of ${skill.videoCount} watched · $percent%"
                    holder.progressIndicatorCard.progress = percent
                    holder.progressIndicatorCard.trackColor = cardStroke

                    if (percent >= 100) {
                        holder.progressIndicatorCard.setIndicatorColor(
                            ContextCompat.getColor(holder.itemView.context, R.color.success)
                        )
                        holder.tvCardProgressLabel.setTextColor(
                            ContextCompat.getColor(holder.itemView.context, R.color.success)
                        )
                    } else {
                        holder.progressIndicatorCard.setIndicatorColor(
                            ContextCompat.getColor(holder.itemView.context, R.color.brand_navy)
                        )
                        holder.tvCardProgressLabel.setTextColor(textPrimary)
                    }
                } else {
                    holder.tvCardProgressLabel.visibility = View.GONE
                    holder.progressIndicatorCard.visibility = View.GONE
                }

                val isUnlockedForUser = isOwnUpload || isPurchased

                if (isUnlockedForUser) {
                    holder.btnUnlock.visibility = View.GONE
                    (holder.btnPreview.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                        params.width = LinearLayout.LayoutParams.MATCH_PARENT
                        params.weight = 1f
                        holder.btnPreview.layoutParams = params
                    }
                } else {
                    holder.btnUnlock.visibility = View.VISIBLE
                    holder.btnUnlock.setCardBackgroundColor(btnUnlockBg)
                    holder.tvBtnUnlock.setTextColor(Color.parseColor("#F9F3EF"))
                    holder.tvBtnUnlock.text = "Unlock"
                    (holder.btnPreview.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
                        params.width = 0
                        params.weight = 1f
                        holder.btnPreview.layoutParams = params
                    }
                }

                holder.btnPreview.setOnClickListener { onPreviewClick(skill) }
                holder.btnUnlock.setOnClickListener { onUnlockClick(skill) }
            } else {
                holder.playlistInfoLayout.visibility = View.GONE
                holder.singleInfoLayout.visibility = View.VISIBLE
                holder.tvAccessCount.visibility = View.GONE
                holder.tvCardProgressLabel.visibility = View.GONE
                holder.progressIndicatorCard.visibility = View.GONE

                holder.tvCredits.text = if (isOwnUpload) "Free • Your upload" else "${skill.credits} swaps"
                holder.tvSwapsCount.text = "${skill.credits} swaps completed"
            }

            holder.itemView.setOnClickListener { onItemClick(skill) }
        }

        override fun getItemCount() = skills.size
    }
}