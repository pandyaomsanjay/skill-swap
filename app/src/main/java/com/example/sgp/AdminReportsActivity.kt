package com.example.sgp

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.firestore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.bumptech.glide.Glide

private enum class ReportTab { ALL, PENDING, RESOLVED }
private enum class ReportTypeTab { ALL, VIDEOS, PLAYLISTS }

// NEW: top-level split between reports filed against uploaded content
// (video/playlist) and reports filed against a user's profile directly.
private enum class ReportKindTab { CONTENT, USER }

class AdminReportsActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    // ---- List ----
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ReportAdapter
    private lateinit var emptyState: View
    private val allReports = mutableListOf<Report>()
    private val filteredReports = mutableListOf<Report>()

    // Cache of uid -> Users, built from the "users" collection so report
    // cards can show a name instead of a raw reportedUserId.
    private val userCache = mutableMapOf<String, Users>()

    // IMPORTANT: per your Firestore rules, reports/{id}.reportedUserId and
    // reporterId are stored as EMAIL addresses (rules compare them against
    // request.auth.token.email), while users/{uid} documents are keyed by
    // Firebase Auth uid, not email. This map bridges the two so we can look
    // up / update the right user document from a report's email-based ID.
    private val emailToUid = mutableMapOf<String, String>()

    // Cache of skillId -> Skill to know if it's a playlist or single video
    private val skillCache = mutableMapOf<String, Skill>()

    private fun resolveUid(reportedUserId: String): String? =
        emailToUid[reportedUserId] ?: userCache[reportedUserId]?.let { reportedUserId }

    private fun resolveUser(reportedUserId: String): Users? =
        emailToUid[reportedUserId]?.let { userCache[it] } ?: userCache[reportedUserId]

    // ---- Header ----
    private lateinit var etSearch: EditText
    private lateinit var btnExport: MaterialCardView
    private lateinit var tabAll: MaterialCardView
    private lateinit var tabPending: MaterialCardView
    private lateinit var tabResolved: MaterialCardView
    private lateinit var tvTabAll: TextView
    private lateinit var tvTabPending: TextView
    private lateinit var tvTabResolved: TextView
    private var currentTab = ReportTab.ALL
    private var searchQuery = ""

    // ---- Report Type Tabs (Videos / Playlists) ----
    private lateinit var tabTypeAll: MaterialCardView
    private lateinit var tabTypeVideos: MaterialCardView
    private lateinit var tabTypePlaylists: MaterialCardView
    private lateinit var tvTabTypeAll: TextView
    private lateinit var tvTabTypeVideos: TextView
    private lateinit var tvTabTypePlaylists: TextView
    private var currentTypeTab = ReportTypeTab.ALL

    // ---- NEW: Report Kind Tabs (Content Reports / User Reports) ----
    private lateinit var tabKindContent: MaterialCardView
    private lateinit var tabKindUser: MaterialCardView
    private lateinit var tvTabKindContent: TextView
    private lateinit var tvTabKindUser: TextView
    private var currentKindTab = ReportKindTab.CONTENT

    // ---- Analytics cards ----
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvActiveUsers: TextView
    private lateinit var tvTotalSkills: TextView
    private lateinit var tvTotalSwaps: TextView
    private lateinit var tvCompletedSwaps: TextView
    private lateinit var tvPendingSwaps: TextView
    private lateinit var tvReportedUsers: TextView
    private lateinit var tvAvgRating: TextView

    // ---- Charts ----
    private lateinit var userGrowthChartView: UserGrowthChartView
    private lateinit var llUserGrowthLabels: LinearLayout
    private lateinit var swapsByMonthChartView: SwapsTrendView

    private lateinit var tvSwapsByMonthPeakValue: TextView
    private lateinit var llSwapsByMonthLabels: LinearLayout

    // ---- Listeners ----
    private var reportsListener: ListenerRegistration? = null
    private var usersListener: ListenerRegistration? = null
    private var skillsListener: ListenerRegistration? = null
    private var tradesListener: ListenerRegistration? = null
    private var skillsCacheListener: ListenerRegistration? = null

    private var pendingExport: (() -> Unit)? = null
    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingExport?.invoke()
        else Toast.makeText(this, "Storage permission is required to export", Toast.LENGTH_SHORT).show()
        pendingExport = null
    }

    private val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
    private val TAG = "AdminReports"

    // Same dark palette as AdminTradesActivity's options sheet, so both screens feel identical.
    private val sheetBg = Color.parseColor("#16263A")
    private val sheetDivider = Color.parseColor("#28405A")
    private val sheetPrimaryText = Color.parseColor("#F5EDE4")
    private val sheetSecondaryText = Color.parseColor("#9FB3C8")
    private val sheetDestructive = Color.parseColor("#FF8A80")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_reports)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        if (prefs.getString("user_type", "") != "admin") {
            Toast.makeText(this, "Unauthorized", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = Firebase.firestore

        bindViews()
        loadSkillsCache()
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = ReportAdapter(
            filteredReports,
            ::resolveUser,
            ::getSkillType,
            ::extractVideoIdFromReport,
            onItemClick = { report -> showReportOptionsDialog(report) },
            onMenuClick = { report, anchor -> showReportOptionsDialog(report) }
        )
        recyclerView.adapter = adapter

        setupSearch()
        setupKindTabs()
        setupTabs()
        setupTypeTabs()
        setupExport()
        BottomNav.setup(this, BottomNav.REPORTS)

        loadUsers()
        loadReports()
        loadSkillsCount()
        loadTradesAndCharts()
    }

    override fun onDestroy() {
        super.onDestroy()
        reportsListener?.remove()
        usersListener?.remove()
        skillsListener?.remove()
        tradesListener?.remove()
        skillsCacheListener?.remove()
    }

    private fun bindViews() {
        recyclerView = findViewById(R.id.recyclerView)
        emptyState = findViewById(R.id.emptyState)

        etSearch = findViewById(R.id.etSearch)
        btnExport = findViewById(R.id.btnExport)
        tabAll = findViewById(R.id.tabAll)
        tabPending = findViewById(R.id.tabPending)
        tabResolved = findViewById(R.id.tabResolved)
        tvTabAll = findViewById(R.id.tvTabAll)
        tvTabPending = findViewById(R.id.tvTabPending)
        tvTabResolved = findViewById(R.id.tvTabResolved)

        // Type tabs
        tabTypeAll = findViewById(R.id.tabTypeAll)
        tabTypeVideos = findViewById(R.id.tabTypeVideos)
        tabTypePlaylists = findViewById(R.id.tabTypePlaylists)
        tvTabTypeAll = findViewById(R.id.tvTabTypeAll)
        tvTabTypeVideos = findViewById(R.id.tvTabTypeVideos)
        tvTabTypePlaylists = findViewById(R.id.tvTabTypePlaylists)

        // NEW: Kind tabs — needs adding to activity_admin_reports.xml above the
        // existing status-tab row, styled identically to tabAll/tabPending/tabResolved.
        tabKindContent = findViewById(R.id.tabKindContent)
        tabKindUser = findViewById(R.id.tabKindUser)
        tvTabKindContent = findViewById(R.id.tvTabKindContent)
        tvTabKindUser = findViewById(R.id.tvTabKindUser)

        tvTotalUsers = findViewById(R.id.tvTotalUsers)
        tvActiveUsers = findViewById(R.id.tvActiveUsers)
        tvTotalSkills = findViewById(R.id.tvTotalSkills)
        tvTotalSwaps = findViewById(R.id.tvTotalSwaps)
        tvCompletedSwaps = findViewById(R.id.tvCompletedSwaps)
        tvPendingSwaps = findViewById(R.id.tvPendingSwaps)
        tvReportedUsers = findViewById(R.id.tvReportedUsers)
        tvAvgRating = findViewById(R.id.tvAvgRating)

        userGrowthChartView = findViewById(R.id.userGrowthChartView)
        llUserGrowthLabels = findViewById(R.id.llUserGrowthLabels)
        swapsByMonthChartView = findViewById(R.id.swapsByMonthChartView)
        tvSwapsByMonthPeakValue = findViewById(R.id.tvSwapsByMonthPeakValue)
        llSwapsByMonthLabels = findViewById(R.id.llSwapsByMonthLabels)
    }

    // Load skills cache to know which reports are for playlists vs videos
    private fun loadSkillsCache() {
        skillsCacheListener = db.collection("skills")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Skills cache listener failed", error)
                    return@addSnapshotListener
                }
                skillCache.clear()
                snapshot?.documents?.forEach { doc ->
                    val skill = doc.toObject(Skill::class.java)
                    if (skill != null) {
                        skill.id = doc.id
                        skillCache[doc.id] = skill
                    }
                }
                // Refresh adapter to show correct icons
                adapter.notifyDataSetChanged()
            }
    }

    private fun getSkillType(skillId: String): String {
        return skillCache[skillId]?.skillType ?: "unknown"
    }

    // Extract video ID from report description
    private fun extractVideoIdFromReport(report: Report): String? {
        // Try to extract from description
        val match = Regex("Video ID:\\s*([^)]+)\\)").find(report.description)
        return match?.groupValues?.get(1)?.trim()
    }

    // ---------------- Search ----------------

    private fun setupSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim()?.lowercase(Locale.getDefault()) ?: ""
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    // ---------------- NEW: Kind Tabs (Content Reports / User Reports) ----------------

    private fun setupKindTabs() {
        tabKindContent.setOnClickListener { selectKindTab(ReportKindTab.CONTENT) }
        tabKindUser.setOnClickListener { selectKindTab(ReportKindTab.USER) }
        selectKindTab(ReportKindTab.CONTENT)
    }

    private fun selectKindTab(tab: ReportKindTab) {
        currentKindTab = tab
        val selectedBg = Color.parseColor("#F9F3EF")
        val selectedText = Color.parseColor("#1B3C53")
        val unselectedBg = Color.parseColor("#456882")
        val unselectedText = Color.parseColor("#FFFFFF")

        listOf(
            Triple(tabKindContent, tvTabKindContent, ReportKindTab.CONTENT),
            Triple(tabKindUser, tvTabKindUser, ReportKindTab.USER)
        ).forEach { (card, text, t) ->
            if (t == tab) {
                card.setCardBackgroundColor(selectedBg)
                card.strokeWidth = 0
                text.setTextColor(selectedText)
            } else {
                card.setCardBackgroundColor(unselectedBg)
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                card.strokeColor = Color.parseColor("#FFFFFF")
                text.setTextColor(unselectedText)
            }
        }

        // Video/Playlist type tabs only make sense for content reports —
        // hide the whole row when viewing user reports.
        val typeTabsRow = tabTypeAll.parent as? View
        typeTabsRow?.visibility = if (tab == ReportKindTab.USER) View.GONE else View.VISIBLE

        applyFilters()
    }

    // ---------------- Tabs (All / Pending / Resolved) ----------------

    private fun setupTabs() {
        tabAll.setOnClickListener { selectTab(ReportTab.ALL) }
        tabPending.setOnClickListener { selectTab(ReportTab.PENDING) }
        tabResolved.setOnClickListener { selectTab(ReportTab.RESOLVED) }
        selectTab(ReportTab.ALL)
    }

    private fun selectTab(tab: ReportTab) {
        currentTab = tab
        val selectedBg = Color.parseColor("#F9F3EF")
        val selectedText = Color.parseColor("#1B3C53")
        val unselectedBg = Color.parseColor("#456882")
        val unselectedText = Color.parseColor("#FFFFFF")

        val cards = listOf(
            Triple(tabAll, tvTabAll, ReportTab.ALL),
            Triple(tabPending, tvTabPending, ReportTab.PENDING),
            Triple(tabResolved, tvTabResolved, ReportTab.RESOLVED)
        )
        cards.forEach { (card, text, t) ->
            if (t == tab) {
                card.setCardBackgroundColor(selectedBg)
                card.strokeWidth = 0
                text.setTextColor(selectedText)
            } else {
                card.setCardBackgroundColor(unselectedBg)
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                card.strokeColor = Color.parseColor("#FFFFFF")
                text.setTextColor(unselectedText)
            }
        }
        applyFilters()
    }

    // ---------------- Type Tabs (All / Videos / Playlists) ----------------

    private fun setupTypeTabs() {
        tabTypeAll.setOnClickListener { selectTypeTab(ReportTypeTab.ALL) }
        tabTypeVideos.setOnClickListener { selectTypeTab(ReportTypeTab.VIDEOS) }
        tabTypePlaylists.setOnClickListener { selectTypeTab(ReportTypeTab.PLAYLISTS) }
        selectTypeTab(ReportTypeTab.ALL)
    }

    private fun selectTypeTab(tab: ReportTypeTab) {
        currentTypeTab = tab
        val selectedBg = Color.parseColor("#F9F3EF")
        val selectedText = Color.parseColor("#1B3C53")
        val unselectedBg = Color.parseColor("#456882")
        val unselectedText = Color.parseColor("#FFFFFF")

        val cards = listOf(
            Triple(tabTypeAll, tvTabTypeAll, ReportTypeTab.ALL),
            Triple(tabTypeVideos, tvTabTypeVideos, ReportTypeTab.VIDEOS),
            Triple(tabTypePlaylists, tvTabTypePlaylists, ReportTypeTab.PLAYLISTS)
        )
        cards.forEach { (card, text, t) ->
            if (t == tab) {
                card.setCardBackgroundColor(selectedBg)
                card.strokeWidth = 0
                text.setTextColor(selectedText)
            } else {
                card.setCardBackgroundColor(unselectedBg)
                card.strokeWidth = (1 * resources.displayMetrics.density).toInt()
                card.strokeColor = Color.parseColor("#FFFFFF")
                text.setTextColor(unselectedText)
            }
        }
        applyFilters()
    }

    private fun applyFilters() {
        // NEW: split by kind first — user-profile reports vs content reports.
        // Older report docs written before the `type` field existed decode
        // with type = "video" (the model's default), so they fall into
        // CONTENT automatically — nothing extra needed for backward compat.
        val kindFiltered = when (currentKindTab) {
            ReportKindTab.USER -> allReports.filter { it.type == "user" }
            ReportKindTab.CONTENT -> allReports.filter { it.type != "user" }
        }

        val statusFiltered = when (currentTab) {
            ReportTab.ALL -> kindFiltered
            ReportTab.PENDING -> kindFiltered.filter { it.status.equals("pending", ignoreCase = true) }
            ReportTab.RESOLVED -> kindFiltered.filter { it.status.equals("resolved", ignoreCase = true) }
        }

        // Filter by report type (video vs playlist) — not applicable to user reports
        val typeFiltered = if (currentKindTab == ReportKindTab.USER) {
            statusFiltered
        } else when (currentTypeTab) {
            ReportTypeTab.ALL -> statusFiltered
            ReportTypeTab.VIDEOS -> statusFiltered.filter { report ->
                val skillId = extractSkillId(report)
                val videoId = extractVideoIdFromReport(report)
                // If it has a videoId, it's a specific video report
                // Or if it's a single video skill
                videoId != null || skillCache[skillId]?.skillType == "single" || skillCache[skillId]?.skillType == null
            }
            ReportTypeTab.PLAYLISTS -> statusFiltered.filter { report ->
                val skillId = extractSkillId(report)
                val videoId = extractVideoIdFromReport(report)
                // Playlist reports are those with playlist skill type AND no specific video ID
                skillCache[skillId]?.skillType == "playlist" && videoId == null
            }
        }

        val searched = if (searchQuery.isBlank()) {
            typeFiltered
        } else {
            typeFiltered.filter { report ->
                val user = resolveUser(report.reportedUserId)
                val name = user?.name?.lowercase(Locale.getDefault()) ?: ""
                val email = user?.email?.lowercase(Locale.getDefault()) ?: ""
                val id = report.id.lowercase(Locale.getDefault())
                val reason = report.reason.lowercase(Locale.getDefault())
                name.contains(searchQuery) || email.contains(searchQuery) ||
                        id.contains(searchQuery) || reason.contains(searchQuery)
            }
        }

        filteredReports.clear()
        filteredReports.addAll(searched.sortedByDescending { it.timestamp })
        adapter.notifyDataSetChanged()
        emptyState.visibility = if (filteredReports.isEmpty()) View.VISIBLE else View.GONE
        recyclerView.visibility = if (filteredReports.isEmpty()) View.GONE else View.VISIBLE
    }

    // ---------------- Data loading ----------------

    private fun loadReports() {
        reportsListener = db.collection("reports")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Toast.makeText(this, error.message, Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }
                allReports.clear()
                snapshot?.documents?.forEach { doc ->
                    val report = doc.toObject(Report::class.java) ?: return@forEach
                    val fixedReport = if (report.id.isBlank()) report.copy(id = doc.id) else report
                    allReports.add(fixedReport)
                }
                tvReportedUsers.text = allReports.map { it.reportedUserId }.distinct().size.toString()
                applyFilters()
            }
    }

    private fun loadUsers() {
        usersListener = db.collection("users")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "users listener failed", error)
                    return@addSnapshotListener
                }
                userCache.clear()
                emailToUid.clear()
                var activeCount = 0
                var ratingSum = 0.0
                var ratingCount = 0
                snapshot?.documents?.forEach { doc ->
                    val user = doc.toObject(Users::class.java) ?: return@forEach
                    userCache[doc.id] = user
                    if (user.email.isNotBlank()) {
                        emailToUid[user.email] = doc.id
                    }
                    val blocked = doc.getBoolean("blocked") ?: false
                    if (!blocked) activeCount++
                    if (user.rating > 0.0) {
                        ratingSum += user.rating
                        ratingCount++
                    }
                }
                tvTotalUsers.text = (snapshot?.size() ?: 0).toString()
                tvActiveUsers.text = activeCount.toString()
                tvAvgRating.text = if (ratingCount > 0) {
                    String.format(Locale.getDefault(), "%.1f", ratingSum / ratingCount)
                } else "0.0"

                renderUserGrowthChart()
                applyFilters()
            }
    }

    private fun loadSkillsCount() {
        skillsListener = db.collection("skills")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "skills listener failed", error)
                    return@addSnapshotListener
                }
                tvTotalSkills.text = (snapshot?.size() ?: 0).toString()
            }
    }

    private fun loadTradesAndCharts() {
        tradesListener = db.collection("trades")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "trades listener failed", error)
                    return@addSnapshotListener
                }
                val docs = snapshot?.documents ?: emptyList()
                tvTotalSwaps.text = docs.size.toString()
                tvCompletedSwaps.text = docs.count {
                    (it.getString("status") ?: "").equals("completed", ignoreCase = true)
                }.toString()
                tvPendingSwaps.text = docs.count {
                    (it.getString("status") ?: "").equals("pending", ignoreCase = true)
                }.toString()

                renderSwapsByMonthChart(docs.mapNotNull { it.getLong("timestamp") })
            }
    }

    // ---------------- Charts: 6-month buckets ----------------

    private fun last6MonthBuckets(): List<Pair<Long, Long>> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.MONTH, -5)

        val buckets = mutableListOf<Pair<Long, Long>>()
        repeat(6) {
            val start = cal.timeInMillis
            val startCal = cal.clone() as Calendar
            cal.add(Calendar.MONTH, 1)
            val end = cal.timeInMillis - 1
            buckets.add(Pair(start, end))
        }
        return buckets
    }

    private fun renderUserGrowthChart() {
        val buckets = last6MonthBuckets()
        db.collection("users").get().addOnSuccessListener { snapshot ->
            val joinDates = snapshot.documents.mapNotNull { it.getLong("joinedDate") }
            var cumulative = 0
            val cumulativeCounts = mutableListOf<Float>()
            val labels = mutableListOf<String>()
            buckets.forEach { (start, end) ->
                cumulative += joinDates.count { it in start..end }
                cumulativeCounts.add(cumulative.toFloat())
                labels.add(monthFormat.format(Date(start)))
            }
            userGrowthChartView.setData(cumulativeCounts, labels)
            renderMonthLabels(llUserGrowthLabels, labels)
        }
    }

    private fun renderSwapsByMonthChart(timestamps: List<Long>) {
        val buckets = last6MonthBuckets()
        val counts = mutableListOf<Float>()
        val labels = mutableListOf<String>()
        buckets.forEach { (start, end) ->
            counts.add(timestamps.count { it in start..end }.toFloat())
            labels.add(monthFormat.format(Date(start)))
        }
        val peak = counts.maxOrNull() ?: 0f
        val axisTop = if (peak <= 4f) 4f else {
            val v = peak.toInt().coerceAtLeast(1)
            var top = ((v / 4) + 1) * 4
            top.toFloat()
        }
        swapsByMonthChartView.setData(counts, labels, axisTop)
        tvSwapsByMonthPeakValue.text = peak.toInt().toString()
        renderMonthLabels(llSwapsByMonthLabels, labels)
    }

    private fun renderMonthLabels(container: LinearLayout, labels: List<String>) {
        container.removeAllViews()
        labels.forEach { label ->
            val tv = TextView(this).apply {
                text = label
                setTextColor(Color.parseColor("#456882"))
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            container.addView(tv)
        }
    }

    // ---------------- Report actions (themed bottom sheet, matches Trades page) ----------------

    private fun showReportOptionsDialog(report: Report) {
        val dialog = BottomSheetDialog(this, R.style.DarkBottomSheetDialog)
        val user = resolveUser(report.reportedUserId)
        val reporter = resolveUser(report.reporterId)
        val skillType = getSkillType(extractSkillId(report))
        val videoId = extractVideoIdFromReport(report)
        val isSpecificVideo = videoId != null && skillType == "playlist"
        val isUserReport = report.type == "user"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(12))
            background = GradientDrawable().apply {
                val r = dp(20).toFloat()
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(sheetBg)
            }
        }

        // ---- Header: reported user's profile avatar + name + "Reported by" + report id ----
        val shortId = report.id.takeLast(6).ifBlank { report.id }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
        }

        // Profile avatar
        val initial = user?.name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
        header.addView(MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            radius = dp(22).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#28405A"))
            if (!user?.profileImage.isNullOrEmpty()) {
                val iv = android.widget.ImageView(this@AdminReportsActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }
                addView(iv)
                Glide.with(this@AdminReportsActivity).load(user!!.profileImage).into(iv)
            } else {
                addView(TextView(this@AdminReportsActivity).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    gravity = Gravity.CENTER
                    text = initial
                    setTextColor(sheetPrimaryText)
                    textSize = 16f
                    setTypeface(typeface, Typeface.BOLD)
                })
            }
        })

        val textCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        }
        textCol.addView(TextView(this).apply {
            text = user?.name?.ifBlank { report.reportedUserId } ?: report.reportedUserId
            setTextColor(sheetPrimaryText)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        // Who filed the report against this user
        textCol.addView(TextView(this).apply {
            text = "Reported by ${reporter?.name?.ifBlank { report.reporterId } ?: report.reporterId}"
            setTextColor(sheetSecondaryText)
            textSize = 12f
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, dp(2), 0, 0)
        })
        textCol.addView(TextView(this).apply {
            text = "Report #$shortId • ${
                when {
                    isUserReport -> "👤 User Report"
                    isSpecificVideo -> "🎬 Specific Video"
                    skillType == "playlist" -> "📋 Playlist"
                    else -> "🎬 Video"
                }
            }"
            setTextColor(sheetSecondaryText)
            textSize = 11f
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(textCol)
        root.addView(header)

        // ---- Divider ----
        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(sheetDivider)
        })

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

        addRow("📋", "View Details") { viewReportDetails(report) }
        // "View Reported Video/Playlist" has nothing to point to on a user report.
        if (!isUserReport) {
            addRow("🎬", "View Reported ${if (isSpecificVideo) "Video" else if (skillType == "playlist") "Playlist" else "Video"}") { viewReportedSkill(report) }
        }
        addRow("👤", "View User Profile") { viewUserProfile(report) }
        if (!report.status.equals("resolved", ignoreCase = true)) {
            addRow("✅", "Resolve Report", Color.parseColor("#34D399")) { updateReportStatus(report, "resolved") }
        }
        addRow("🚫", "Block User", sheetDestructive) { confirmBlockUser(report) }
        addRow("🗑️", "Delete Report", sheetDestructive) { confirmDeleteReport(report) }

        dialog.setContentView(root)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.show()
    }

    private fun viewReportDetails(report: Report) {
        val user = resolveUser(report.reportedUserId)
        val skillType = getSkillType(extractSkillId(report))
        val videoId = extractVideoIdFromReport(report)
        val isSpecificVideo = videoId != null && skillType == "playlist"
        val isUserReport = report.type == "user"
        val root = dialogCard()

        root.addView(dialogTitle("Report Details"))
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

        addDetailRow("Report ID", report.id)
        addDetailRow(
            "Type",
            when {
                isUserReport -> "👤 User Report"
                isSpecificVideo -> "🎬 Specific Video (in Playlist)"
                skillType == "playlist" -> "📋 Playlist"
                else -> "🎬 Video"
            }
        )
        if (isSpecificVideo) {
            addDetailRow("Video ID", videoId ?: "N/A")
        }
        addDetailRow("Reported User", user?.name ?: report.reportedUserId)
        addDetailRow("Reporter", report.reporterId)
        addDetailRow("Reason", report.reason)
        addDetailRow("Description", report.description)
        addDetailRow("Status", report.status.replaceFirstChar { it.uppercase() })
        addDetailRow("Date", SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(report.timestamp)))

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (!isUserReport) {
            val btnViewVideo = pillButton(
                if (isSpecificVideo) "View Video" else if (skillType == "playlist") "View Playlist" else "View Video",
                Color.parseColor("#EAF1F5"),
                Color.parseColor("#1B3C53")
            ).apply {
                setOnClickListener {
                    dialog.dismiss()
                    viewReportedSkill(report)
                }
            }
            buttonRow.addView(
                btnViewVideo,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    topMargin = dp(16)
                    marginEnd = dp(8)
                }
            )
        }
        val btnOk = pillButton("OK", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            setOnClickListener { dialog.dismiss() }
        }
        buttonRow.addView(
            btnOk,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { topMargin = dp(16) }
        )
        root.addView(buttonRow)

        dialog.show()
    }

    private fun viewUserProfile(report: Report) {
        val user = resolveUser(report.reportedUserId)
        val reporter = resolveUser(report.reporterId)
        val root = dialogCard()
        root.addView(dialogTitle("User Profile"))
        root.addView(dialogDivider())

        if (user == null) {
            root.addView(TextView(this).apply {
                text = "Profile not found for ${report.reportedUserId}"
                setTextColor(Color.parseColor("#456882"))
                textSize = 13f
            })
        } else {
            val topRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            val initial = user.name.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "?"
            val avatar = MaterialCardView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
                radius = dp(22).toFloat()
                cardElevation = 0f
                setCardBackgroundColor(Color.parseColor("#EAE1DA"))
                if (user.profileImage.isNotEmpty()) {
                    val iv = android.widget.ImageView(this@AdminReportsActivity).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                    }
                    addView(iv)
                    Glide.with(this@AdminReportsActivity).load(user.profileImage).into(iv)
                } else {
                    addView(TextView(this@AdminReportsActivity).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        gravity = Gravity.CENTER
                        text = initial
                        setTextColor(Color.parseColor("#1B3C53"))
                        textSize = 16f
                        setTypeface(typeface, Typeface.BOLD)
                    })
                }
            }
            val nameCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                }
            }
            nameCol.addView(TextView(this).apply {
                text = blankIfEmpty(user.name).ifBlank { "No name set" }
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
            })
            nameCol.addView(TextView(this).apply {
                text = blankIfEmpty(user.email)
                setTextColor(Color.parseColor("#456882"))
                textSize = 12f
                setPadding(0, dp(2), 0, 0)
            })
            topRow.addView(avatar)
            topRow.addView(nameCol)
            topRow.addView(TextView(this).apply {
                text = "⭐ ${String.format(Locale.getDefault(), "%.1f", user.rating)}"
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(10), dp(4), dp(10), dp(4))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.parseColor("#EAF1F5"))
                }
            })
            root.addView(topRow)

            root.addView(TextView(this).apply {
                text = "🚩 Reported by ${reporter?.name?.ifBlank { report.reporterId } ?: report.reporterId}"
                setTextColor(Color.parseColor("#B8860B"))
                textSize = 12f
                setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(10), dp(6), dp(10), dp(6))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(Color.parseColor("#FFF3E0"))
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(10) }
            })

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
                    layoutParams = LinearLayout.LayoutParams(dp(120), LinearLayout.LayoutParams.WRAP_CONTENT)
                })
                row.addView(TextView(this).apply {
                    text = value.ifBlank { "-" }
                    setTextColor(Color.parseColor("#1B3C53"))
                    textSize = 12.5f
                    setTypeface(typeface, Typeface.BOLD)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                })
                root.addView(row)
            }

            addDetailRow("Phone", blankIfEmpty(user.phone))
            addDetailRow("Can Teach", blankIfEmpty(user.skillsTeach))
            addDetailRow("Wants to Learn", blankIfEmpty(user.skillsLearn))
            addDetailRow("Completed Trades", user.completedTrades.toString())
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

    // ---------------- Reported video/playlist ----------------

    private fun extractSkillId(report: Report): String {
        if (report.skillId.isNotBlank()) return report.skillId
        val match = Regex("Skill ID:\\s*([^)]+)\\)").find(report.description)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun viewReportedSkill(report: Report) {
        val skillId = extractSkillId(report)
        if (skillId.isBlank()) {
            Toast.makeText(this, "No skill is linked to this report", Toast.LENGTH_SHORT).show()
            return
        }
        db.collection("skills").document(skillId).get()
            .addOnSuccessListener { doc ->
                val skill = doc.toObject(Skill::class.java)
                if (skill == null || !doc.exists()) {
                    Toast.makeText(this, "This skill has been deleted", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }
                skill.id = doc.id
                showReportedSkillDialog(report, skill)
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load skill: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showReportedSkillDialog(report: Report, skill: Skill) {
        val isPlaylist = skill.skillType == "playlist"
        val videoId = extractVideoIdFromReport(report)
        val isSpecificVideo = videoId != null && isPlaylist

        // If it's a specific video in a playlist, find that video
        var specificVideo: PlaylistVideo? = null
        if (isSpecificVideo && skill.videos != null) {
            specificVideo = skill.videos.find { it.id == videoId }
        }

        val root = dialogCard()
        root.addView(dialogTitle(
            if (isSpecificVideo) "Reported Video (in Playlist)"
            else if (isPlaylist) "Reported Playlist"
            else "Reported Video"
        ))
        root.addView(dialogDivider())

        // Thumbnail preview
        val thumbCard = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(160)
            ).apply { bottomMargin = dp(14) }
            radius = dp(12).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#EAF1F5"))
        }
        val thumbView = android.widget.ImageView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        }
        thumbCard.addView(thumbView)

        // Determine thumbnail URL - Use videoUrl as thumbnail since PlaylistVideo doesn't have thumbnailUrl
        var thumbnailUrl: String? = null

        if (isSpecificVideo && specificVideo != null) {
            // Specific video in playlist - use the video's URL
            if (specificVideo.videoUrl != null && specificVideo.videoUrl.isNotEmpty()) {
                thumbnailUrl = specificVideo.videoUrl
            } else if (skill.thumbnailUrl != null && skill.thumbnailUrl.isNotEmpty()) {
                thumbnailUrl = skill.thumbnailUrl
            }
        } else if (isPlaylist) {
            // Whole playlist - use playlist thumbnail
            if (skill.thumbnailUrl != null && skill.thumbnailUrl.isNotEmpty()) {
                thumbnailUrl = skill.thumbnailUrl
            }
        } else {
            // Single video - use video URL
            if (skill.videoUrl != null && skill.videoUrl.isNotEmpty()) {
                thumbnailUrl = skill.videoUrl
            }
        }

        // Load the thumbnail with Glide
        if (thumbnailUrl != null && thumbnailUrl.isNotEmpty()) {
            Glide.with(this)
                .load(thumbnailUrl)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(thumbView)
        } else {
            thumbView.setImageResource(R.drawable.baseline_videocam_24)
        }
        root.addView(thumbCard)

        // Title with type badge
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val displayTitle = if (isSpecificVideo && specificVideo != null) {
            "${specificVideo.title} (in ${skill.title})"
        } else {
            skill.title
        }
        titleRow.addView(TextView(this).apply {
            text = displayTitle
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        titleRow.addView(TextView(this).apply {
            text = if (isSpecificVideo) "🎬" else if (isPlaylist) "📋" else "🎬"
            textSize = 16f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(8) }
        })
        root.addView(titleRow)

        val subtitleText = if (isSpecificVideo && specificVideo != null) {
            "${skill.category} • By ${skill.userName} • From playlist: ${skill.title}"
        } else {
            "${skill.category} • By ${skill.userName} • ${skill.credits} credits" +
                    if (isPlaylist) " • ${skill.videoCount} videos" else ""
        }
        root.addView(TextView(this).apply {
            text = subtitleText
            setTextColor(Color.parseColor("#456882"))
            textSize = 12.5f
            setPadding(0, dp(4), 0, dp(10))
        })

        // Show specific video description if available
        if (isSpecificVideo && specificVideo != null && specificVideo.description != null && specificVideo.description.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = "📝 ${specificVideo.description}"
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 12.5f
                setPadding(0, 0, 0, dp(6))
            })
        } else if (skill.description != null && skill.description.isNotEmpty()) {
            root.addView(TextView(this).apply {
                text = skill.description
                setTextColor(Color.parseColor("#1B3C53"))
                textSize = 12.5f
                setPadding(0, 0, 0, dp(6))
            })
        }

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(10) }
        }
        val btnClose = pillButton("Close", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnPlay = pillButton(
            if (isSpecificVideo) "▶ Play Video" else if (isPlaylist) "Open Playlist" else "▶ Play Video",
            Color.parseColor("#1B3C53"),
            Color.WHITE
        ).apply {
            setOnClickListener {
                dialog.dismiss()
                when {
                    isSpecificVideo && specificVideo != null -> {
                        // Play the specific video from the playlist
                        playSpecificVideoFromPlaylist(report, skill, specificVideo)
                    }
                    isPlaylist -> {
                        // Open the entire playlist
                        val intent = Intent(this@AdminReportsActivity, PlaylistActivity::class.java)
                        intent.putExtra("skillId", skill.id)
                        startActivity(intent)
                    }
                    skill.videoUrl != null && skill.videoUrl.isNotEmpty() -> playReportedVideo(
                        report,
                        skill.videoUrl!!,
                        skill.title,
                        "${skill.category} • By ${skill.userName}"
                    )
                    else -> Toast.makeText(this@AdminReportsActivity, "No video available", Toast.LENGTH_SHORT).show()
                }
            }
        }
        buttonRow.addView(btnClose, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) })
        buttonRow.addView(btnPlay, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun playSpecificVideoFromPlaylist(report: Report, skill: Skill, video: PlaylistVideo) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("videoUrl", video.videoUrl)
        intent.putExtra("skillTitle", "${video.title} (from ${skill.title})")
        intent.putExtra("skillCategory", skill.category)
        intent.putExtra("skillUserId", skill.userId)
        intent.putExtra("skillUserName", skill.userName)
        intent.putExtra("skillCredits", 0)
        intent.putExtra("skillDescription", "Reported video from playlist")
        intent.putExtra("skillId", skill.id)
        intent.putExtra("playlistId", skill.id)
        intent.putExtra("videoId", video.id)
        intent.putExtra("totalVideos", skill.videoCount)
        // Pass report info for context
        intent.putExtra("reportId", report.id)
        intent.putExtra("isFromReport", true)
        startActivity(intent)
    }

    private fun playReportedVideo(report: Report, videoUrl: String, title: String, subtitle: String) {
        val user = resolveUser(report.reportedUserId)
        val reporter = resolveUser(report.reporterId)
        val uid = resolveUid(report.reportedUserId)

        val intent = Intent(this, AdminVideoPlayerActivity::class.java)
        intent.putExtra("videoUrl", videoUrl)
        intent.putExtra("videoTitle", title)
        intent.putExtra("videoSubtitle", subtitle)
        intent.putExtra("reportId", report.id)
        intent.putExtra("reportedUserName", user?.name?.ifBlank { report.reportedUserId } ?: report.reportedUserId)
        intent.putExtra("reporterName", reporter?.name?.ifBlank { report.reporterId } ?: report.reporterId)
        intent.putExtra("reportReason", report.reason)
        intent.putExtra("reportStatus", report.status)
        intent.putExtra("reportTimestamp", report.timestamp)
        intent.putExtra("reportedUserUid", uid)
        startActivity(intent)
    }

    private fun updateReportStatus(report: Report, status: String) {
        db.collection("reports").document(report.id)
            .update("status", status)
            .addOnSuccessListener {
                Toast.makeText(this, "Report status updated to $status", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed", Toast.LENGTH_SHORT).show()
            }
    }

    private fun confirmBlockUser(report: Report) {
        val user = resolveUser(report.reportedUserId)
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "🚫"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = "Block User"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Block ${user?.name ?: "this user"}? They will no longer be able to use SkillSwap."
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
        val btnBlock = pillButton("Block", Color.parseColor("#DC2626"), Color.WHITE).apply {
            setOnClickListener {
                dialog.dismiss()
                blockUser(report)
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnBlock, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun blockUser(report: Report) {
        val uid = resolveUid(report.reportedUserId)
        if (uid == null) {
            Toast.makeText(this, "Could not find this user's account record", Toast.LENGTH_LONG).show()
            return
        }
        db.collection("users").document(uid)
            .update("blocked", true)
            .addOnSuccessListener {
                Toast.makeText(this, "User blocked", Toast.LENGTH_SHORT).show()
                updateReportStatus(report, "resolved")
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to block user: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ---------------- NEW: Delete report (themed confirm dialog) ----------------

    private fun confirmDeleteReport(report: Report) {
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
            text = "Delete Report"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "This permanently removes this report. This can't be undone."
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
                db.collection("reports").document(report.id).delete()
                    .addOnSuccessListener {
                        Toast.makeText(this@AdminReportsActivity, "Report deleted", Toast.LENGTH_SHORT).show()
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@AdminReportsActivity, "Failed to delete", Toast.LENGTH_SHORT).show()
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

    // ---------------- Themed-dialog helpers ----------------

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

    // ---------------- Export ----------------

    private fun setupExport() {
        btnExport.setOnClickListener {
            showExportOptionsDialog()
        }
    }

    private fun showExportOptionsDialog() {
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
            text = "⬇️"
            textSize = 18f
            gravity = Gravity.CENTER
            setBackgroundResource(R.drawable.bg_status_trade_accepted)
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        })
        header.addView(TextView(this).apply {
            text = "Export PDF"
            setTextColor(sheetPrimaryText)
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(14)
            }
        })
        root.addView(header)

        root.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
            setBackgroundColor(sheetDivider)
        })

        fun addRow(emoji: String, label: String, subtitle: String, action: () -> Unit) {
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
            val textCol = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(14) }
            }
            textCol.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(sheetPrimaryText)
            })
            textCol.addView(TextView(this).apply {
                text = subtitle
                textSize = 12f
                setTextColor(sheetSecondaryText)
                setPadding(0, dp(2), 0, 0)
            })
            row.addView(textCol)
            root.addView(row)
        }

        addRow("🚩", "Reports Summary", "Export all reports with status & reason") {
            runWithStoragePermission { exportReportsSummaryToPdf() }
        }
        addRow("👥", "Users List", "Export all registered users & their skills") {
            runWithStoragePermission { exportUsersToPdf() }
        }

        dialog.setContentView(root)
        dialog.show()
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

    private fun blankIfEmpty(value: String): String = if (value.isBlank()) "" else value

    // ---------------- Export 1: Reports Summary PDF ----------------

    private fun exportReportsSummaryToPdf() {
        if (allReports.isEmpty()) {
            Toast.makeText(this, "No reports to export", Toast.LENGTH_SHORT).show()
            return
        }

        val fileName = "SkillSwap_Reports.pdf"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val pageWidth = 842
        val pageHeight = 595
        val leftMargin = 24f
        val bottomMargin = pageHeight - 30f
        val rowHeight = 22f
        val headerRowHeight = 24f
        val bannerHeight = 70f

        val headers = listOf("Report ID", "Type", "User Name", "Reason", "Status", "Date")
        val tableWidth = pageWidth - (leftMargin * 2)
        val columnWeights = listOf(0.14f, 0.08f, 0.16f, 0.30f, 0.12f, 0.20f)
        val columnWidths = columnWeights.map { it * tableWidth }

        val brandColor = Color.parseColor("#1B3C53")
        val brandColorDark = Color.parseColor("#102838")

        val bannerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bannerAccentPaint = Paint().apply { color = brandColorDark; style = Paint.Style.FILL }
        val titlePaint = Paint().apply {
            textSize = 22f; isFakeBoldText = true
            color = Color.parseColor("#F9F3EF"); textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f; color = Color.parseColor("#CBD8E1"); textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply {
            textSize = 10f; isFakeBoldText = true; color = Color.parseColor("#F9F3EF")
        }
        val headerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bodyPaint = Paint().apply { textSize = 9f; color = Color.parseColor("#1B3C53") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC"); style = Paint.Style.STROKE; strokeWidth = 0.7f
        }
        val altRowBgPaint = Paint().apply { color = Color.parseColor("#EAF1F5"); style = Paint.Style.FILL }
        val footerPaint = Paint().apply {
            textSize = 8f; color = Color.parseColor("#9CA3AF"); textAlign = Paint.Align.CENTER
        }

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        var canvas = page.canvas
        var y: Float

        fun drawBanner() {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, bannerBgPaint)
            canvas.drawRect(0f, bannerHeight, pageWidth.toFloat(), bannerHeight + 4f, bannerAccentPaint)
            val centerX = pageWidth / 2f
            canvas.drawText("SkillSwap — Reports Summary", centerX, bannerHeight / 2f, titlePaint)
            canvas.drawText("Generated: ${dateFormat.format(Date())}", centerX, bannerHeight / 2f + 20f, subtitlePaint)
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
            page = pdfDocument.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            drawBanner()
            y = drawTableHeader(bannerHeight + 24f)
        }

        drawBanner()
        y = drawTableHeader(bannerHeight + 24f)

        val sortedReports = allReports.sortedByDescending { it.timestamp }
        sortedReports.forEachIndexed { rowIndex, report ->
            if (y + rowHeight > bottomMargin) newPage()

            val userName = resolveUser(report.reportedUserId)?.name ?: report.reportedUserId
            val skillType = getSkillType(extractSkillId(report))
            val videoId = extractVideoIdFromReport(report)
            val typeLabel = when {
                report.type == "user" -> "User"
                videoId != null && skillType == "playlist" -> "Specific Video"
                skillType == "playlist" -> "Playlist"
                else -> "Video"
            }
            val rowValues = listOf(
                report.id,
                typeLabel,
                userName,
                report.reason,
                report.status,
                SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(report.timestamp))
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

    // ---------------- Export 2: Users List PDF ----------------

    private fun exportUsersToPdf() {
        db.collection("users").get()
            .addOnSuccessListener { usersSnapshot ->
                if (usersSnapshot.isEmpty) {
                    Toast.makeText(this, "No users to export", Toast.LENGTH_SHORT).show()
                    return@addOnSuccessListener
                }

                db.collection("trades").get()
                    .addOnSuccessListener { tradesSnapshot ->
                        val completedCountByEmail = mutableMapOf<String, Int>()
                        tradesSnapshot.documents.forEach { doc ->
                            val status = doc.getString("status") ?: ""
                            if (!status.equals("completed", ignoreCase = true)) return@forEach
                            val requesterId = doc.getString("requesterId")
                            val receiverId = doc.getString("receiverId")
                            requesterId?.let { completedCountByEmail[it] = (completedCountByEmail[it] ?: 0) + 1 }
                            receiverId?.let { completedCountByEmail[it] = (completedCountByEmail[it] ?: 0) + 1 }
                        }

                        val users = usersSnapshot.documents.mapNotNull { it.toObject(Users::class.java) }
                        generateUsersPdf(users, completedCountByEmail)
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, "Failed to load trades: ${e.message}", Toast.LENGTH_LONG).show()
                    }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load users: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun generateUsersPdf(users: List<Users>, completedCountByEmail: Map<String, Int>) {
        val fileName = "SkillSwap_Users.pdf"
        val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

        val pageWidth = 842
        val pageHeight = 595
        val leftMargin = 24f
        val bottomMargin = pageHeight - 30f
        val rowHeight = 22f
        val headerRowHeight = 24f
        val bannerHeight = 70f

        val headers = listOf(
            "Name", "Email", "Phone", "Skill I Can Teach",
            "Skill I Want To Learn", "Trades", "Review"
        )
        val tableWidth = pageWidth - (leftMargin * 2)
        val columnWeights = listOf(0.13f, 0.22f, 0.11f, 0.19f, 0.19f, 0.08f, 0.08f)
        val columnWidths = columnWeights.map { it * tableWidth }

        val brandColor = Color.parseColor("#1B3C53")
        val brandColorDark = Color.parseColor("#102838")

        val bannerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bannerAccentPaint = Paint().apply { color = brandColorDark; style = Paint.Style.FILL }
        val titlePaint = Paint().apply {
            textSize = 22f
            isFakeBoldText = true
            color = Color.parseColor("#F9F3EF")
            textAlign = Paint.Align.CENTER
        }
        val subtitlePaint = Paint().apply {
            textSize = 11f
            color = Color.parseColor("#CBD8E1")
            textAlign = Paint.Align.CENTER
        }
        val headerPaint = Paint().apply {
            textSize = 10f
            isFakeBoldText = true
            color = Color.parseColor("#F9F3EF")
        }
        val headerBgPaint = Paint().apply { color = brandColor; style = Paint.Style.FILL }
        val bodyPaint = Paint().apply { textSize = 9f; color = Color.parseColor("#1B3C53") }
        val borderPaint = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val altRowBgPaint = Paint().apply {
            color = Color.parseColor("#EAF1F5")
            style = Paint.Style.FILL
        }
        val footerPaint = Paint().apply {
            textSize = 8f
            color = Color.parseColor("#9CA3AF")
            textAlign = Paint.Align.CENTER
        }

        val pdfDocument = PdfDocument()
        var pageNumber = 1
        var page = pdfDocument.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas
        var y: Float

        fun blankIfZeroInt(value: Int): String = if (value == 0) "" else value.toString()
        fun blankIfZeroDouble(value: Double): String =
            if (value == 0.0) "" else String.format(Locale.getDefault(), "%.1f", value)

        fun drawBanner() {
            canvas.drawRect(0f, 0f, pageWidth.toFloat(), bannerHeight, bannerBgPaint)
            canvas.drawRect(0f, bannerHeight, pageWidth.toFloat(), bannerHeight + 4f, bannerAccentPaint)
            val centerX = pageWidth / 2f
            canvas.drawText("SkillSwap — User Report", centerX, bannerHeight / 2f, titlePaint)
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

        users.forEachIndexed { rowIndex, user ->
            if (y + rowHeight > bottomMargin) newPage()

            val realCompletedCount = completedCountByEmail[user.email] ?: 0
            val rowValues = listOf(
                blankIfEmpty(user.name),
                blankIfEmpty(user.email),
                blankIfEmpty(user.phone),
                blankIfEmpty(user.skillsTeach),
                blankIfEmpty(user.skillsLearn),
                blankIfZeroInt(realCompletedCount),
                blankIfZeroDouble(user.rating)
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

    private fun openDownloadsOutputStream(fileName: String, mimeType: String): OutputStream? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues) ?: return null
            contentResolver.openOutputStream(uri)
        } else {
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            val file = java.io.File(downloadsDir, fileName)
            java.io.FileOutputStream(file)
        }
    }

    // ---------------- Adapter ----------------

    class ReportAdapter(
        private val reports: List<Report>,
        private val resolveUser: (String) -> Users?,
        private val getSkillType: (String) -> String,
        private val getVideoId: (Report) -> String?,
        private val onItemClick: (Report) -> Unit,
        private val onMenuClick: (Report, View) -> Unit
    ) : RecyclerView.Adapter<ReportAdapter.ViewHolder>() {

        class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvReportId: TextView = itemView.findViewById(R.id.tvReportId)
            val tvTypeIcon: TextView = itemView.findViewById(R.id.tvTypeIcon)
            val tvUserName: TextView = itemView.findViewById(R.id.tvUserName)
            val tvReason: TextView = itemView.findViewById(R.id.tvReason)
            val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
            val cardStatus: MaterialCardView = itemView.findViewById(R.id.cardStatus)
            val tvDate: TextView = itemView.findViewById(R.id.tvDate)
            val ivMenu: View = itemView.findViewById(R.id.ivMenu)
        }

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        private val skillIdRegex = Regex("Skill ID:\\s*([^)]+)\\)")

        private fun extractSkillId(report: Report): String {
            if (report.skillId.isNotBlank()) return report.skillId
            val match = skillIdRegex.find(report.description)
            return match?.groupValues?.get(1)?.trim() ?: ""
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_admin_report, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val report = reports[position]
            val user = resolveUser(report.reportedUserId)
            val skillId = extractSkillId(report)
            val skillType = getSkillType(skillId)
            val videoId = getVideoId(report)
            val isSpecificVideo = videoId != null && skillType == "playlist"

            val typeIcon = when {
                report.type == "user" -> "👤"
                isSpecificVideo -> "🎬"
                skillType == "playlist" -> "📋"
                else -> "🎬"
            }

            holder.tvReportId.text = "Report #${report.id.takeLast(6).ifBlank { report.id }}"
            holder.tvTypeIcon.text = typeIcon
            holder.tvUserName.text = "Reported: ${user?.name?.ifBlank { report.reportedUserId } ?: report.reportedUserId}"
            holder.tvReason.text = report.reason
            holder.tvDate.text = dateFormat.format(Date(report.timestamp))
            holder.tvStatus.text = report.status.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
            }

            val (bg, text) = when (report.status.lowercase(Locale.getDefault())) {
                "pending" -> Color.parseColor("#FFF3E0") to Color.parseColor("#B8860B")
                "resolved" -> Color.parseColor("#E3F5E9") to Color.parseColor("#2E9E63")
                "dismissed" -> Color.parseColor("#FDEAEA") to Color.parseColor("#DC2626")
                else -> Color.parseColor("#EEEEEE") to Color.parseColor("#757575")
            }
            holder.cardStatus.setCardBackgroundColor(bg)
            holder.tvStatus.setTextColor(text)

            holder.itemView.setOnClickListener { onItemClick(report) }
            holder.ivMenu.setOnClickListener { onMenuClick(report, holder.ivMenu) }
        }

        override fun getItemCount() = reports.size
    }
}