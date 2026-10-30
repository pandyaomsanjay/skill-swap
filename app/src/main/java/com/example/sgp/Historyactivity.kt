package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.tabs.TabLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale

class HistoryActivity : BaseActivity() {

    companion object {
        const val EXTRA_START_TAB = "startTab"   // 0 = Swaps, 1 = Points
    }

    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var prefs: SharedPreferences

    private var isDark = false
    private var p: HistoryPalette = historyPalette(false)
    private var userId = ""

    // Data
    private var requesterSwaps: List<Trade> = emptyList()
    private var receiverSwaps: List<Trade> = emptyList()
    private var transactions: List<PointTransaction> = emptyList()

    // UI state
    private var currentTab = 0
    private var swapFilter: SwapState? = null      // null = All
    private var pointsFilter = 0                   // 0 = All, 1 = Earned, 2 = Spent

    // Listeners
    private var requesterListener: ListenerRegistration? = null
    private var receiverListener: ListenerRegistration? = null
    private var txListener: ListenerRegistration? = null

    // Views
    private lateinit var root: View
    private lateinit var toolbar: MaterialToolbar
    private lateinit var cardStats: MaterialCardView
    private lateinit var tabLayout: TabLayout
    private lateinit var hsvSwapChips: View
    private lateinit var hsvPointChips: View
    private lateinit var chipGroupSwaps: ChipGroup
    private lateinit var chipGroupPoints: ChipGroup
    private lateinit var rvSwaps: RecyclerView
    private lateinit var rvPoints: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var swapAdapter: SwapHistoryAdapter
    private lateinit var txAdapter: PointsHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        prefs = getSharedPreferences(Home.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        isDark = prefs.getBoolean(Home.KEY_DARK_MODE, false)
        p = historyPalette(isDark)
        userId = prefs.getString(Home.KEY_USER_EMAIL, null)
            ?: FirebaseAuth.getInstance().currentUser?.email
                    ?: ""

        bindViews()
        setupLists()
        setupTabs()
        setupChips()

        toolbar.setNavigationOnClickListener { finish() }

        applyTheme()
        listenForSwaps()
        listenForTransactions()

        val startTab = intent.getIntExtra(EXTRA_START_TAB, 0).coerceIn(0, 1)
        currentTab = startTab
        tabLayout.getTabAt(startTab)?.select()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        val savedDark = prefs.getBoolean(Home.KEY_DARK_MODE, false)
        if (savedDark != isDark) {
            isDark = savedDark
            p = historyPalette(isDark)
            applyTheme()
        }
        refresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        requesterListener?.remove()
        receiverListener?.remove()
        txListener?.remove()
    }

    // ---------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------
    private fun bindViews() {
        root = findViewById(R.id.historyRoot)
        toolbar = findViewById(R.id.toolbar)
        cardStats = findViewById(R.id.cardStats)
        tabLayout = findViewById(R.id.tabLayout)
        hsvSwapChips = findViewById(R.id.hsvSwapChips)
        hsvPointChips = findViewById(R.id.hsvPointChips)
        chipGroupSwaps = findViewById(R.id.chipGroupSwaps)
        chipGroupPoints = findViewById(R.id.chipGroupPoints)
        rvSwaps = findViewById(R.id.rvSwaps)
        rvPoints = findViewById(R.id.rvPoints)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun setupLists() {
        swapAdapter = SwapHistoryAdapter { trade ->
            startActivity(Intent(this, MyTradesActivity::class.java).putExtra("tradeId", trade.id))
        }
        txAdapter = PointsHistoryAdapter()

        rvSwaps.layoutManager = LinearLayoutManager(this)
        rvSwaps.adapter = swapAdapter
        rvPoints.layoutManager = LinearLayoutManager(this)
        rvPoints.adapter = txAdapter
    }

    private fun setupTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("Swaps"))
        tabLayout.addTab(tabLayout.newTab().setText("Points"))
        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                currentTab = tab.position
                refresh()
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }

    private fun setupChips() {
        chipGroupSwaps.setOnCheckedStateChangeListener { _, ids ->
            swapFilter = when (ids.firstOrNull()) {
                R.id.chipSwapCompleted -> SwapState.COMPLETED
                R.id.chipSwapPending -> SwapState.PENDING
                R.id.chipSwapCancelled -> SwapState.CANCELLED
                else -> null
            }
            refresh()
        }
        chipGroupPoints.setOnCheckedStateChangeListener { _, ids ->
            pointsFilter = when (ids.firstOrNull()) {
                R.id.chipPtsEarned -> 1
                R.id.chipPtsSpent -> 2
                else -> 0
            }
            refresh()
        }
    }

    // ---------------------------------------------------------------------
    // Firestore
    // ---------------------------------------------------------------------
    private fun listenForSwaps() {
        if (userId.isBlank()) return

        requesterListener = db.collection("trades")
            .whereEqualTo("requesterId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                requesterSwaps = snap.documents.mapNotNull { d ->
                    d.toObject(Trade::class.java)?.copy(id = d.id)
                }
                refresh()
            }

        receiverListener = db.collection("trades")
            .whereEqualTo("receiverId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                receiverSwaps = snap.documents.mapNotNull { d ->
                    d.toObject(Trade::class.java)?.copy(id = d.id)
                }
                refresh()
            }
    }

    private fun listenForTransactions() {
        if (userId.isBlank()) return

        // Needs a composite index: transactions -> userId (Asc) + timestamp (Desc)
        // Firestore prints a link in Logcat the first time; tap it to create the index.
        txListener = db.collection("transactions")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(200)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                transactions = snap.documents.mapNotNull { d ->
                    d.toObject(PointTransaction::class.java)?.copy(id = d.id)
                }
                refresh()
            }
    }

    // ---------------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------------
    private fun refresh() {
        if (!::swapAdapter.isInitialized) return

        val allSwaps = (requesterSwaps + receiverSwaps)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
        val swaps = swapFilter?.let { f -> allSwaps.filter { it.swapState() == f } } ?: allSwaps

        val txs = when (pointsFilter) {
            1 -> transactions.filter { it.amount > 0 }
            2 -> transactions.filter { it.amount < 0 }
            else -> transactions
        }

        swapAdapter.submit(swaps, p, userId)
        txAdapter.submit(txs, p)

        val onSwaps = currentTab == 0
        hsvSwapChips.visibility = if (onSwaps) View.VISIBLE else View.GONE
        hsvPointChips.visibility = if (onSwaps) View.GONE else View.VISIBLE

        val visibleCount = if (onSwaps) swaps.size else txs.size
        rvSwaps.visibility = if (onSwaps && visibleCount > 0) View.VISIBLE else View.GONE
        rvPoints.visibility = if (!onSwaps && visibleCount > 0) View.VISIBLE else View.GONE

        if (visibleCount == 0) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = when {
                onSwaps && allSwaps.isEmpty() -> "No swaps yet.\nYour past swaps will appear here."
                onSwaps -> "No swaps match this filter."
                transactions.isEmpty() -> "No point activity yet.\nPoints you earn or spend will appear here."
                else -> "Nothing matches this filter."
            }
        } else {
            tvEmpty.visibility = View.GONE
        }

        updateStats(allSwaps)
    }

    private fun updateStats(allSwaps: List<Trade>) {
        val v1 = findViewById<TextView>(R.id.tvStat1Value)
        val l1 = findViewById<TextView>(R.id.tvStat1Label)
        val v2 = findViewById<TextView>(R.id.tvStat2Value)
        val l2 = findViewById<TextView>(R.id.tvStat2Label)
        val v3 = findViewById<TextView>(R.id.tvStat3Value)
        val l3 = findViewById<TextView>(R.id.tvStat3Label)

        if (currentTab == 0) {
            val completed = allSwaps.count { it.swapState() == SwapState.COMPLETED }
            val rated = allSwaps.filter { it.rating > 0f }
            val avg = if (rated.isEmpty()) "—"
            else String.format(Locale.getDefault(), "%.1f", rated.map { it.rating.toDouble() }.average())

            v1.text = allSwaps.size.toString(); l1.text = "Total swaps"
            v2.text = completed.toString();     l2.text = "Completed"
            v3.text = avg;                      l3.text = "Avg rating"
        } else {
            val earned = transactions.filter { it.amount > 0 }.sumOf { it.amount }
            val spent = transactions.filter { it.amount < 0 }.sumOf { -it.amount }

            v1.text = prefs.getInt(Home.KEY_USER_POINTS, 0).toString(); l1.text = "Balance"
            v2.text = "+$earned";                                        l2.text = "Earned"
            v3.text = "-$spent";                                         l3.text = "Spent"
        }
    }

    // ---------------------------------------------------------------------
    // Theme (light / dark)
    // ---------------------------------------------------------------------
    private fun applyTheme() {
        root.setBackgroundColor(p.screenBg)

        toolbar.setBackgroundColor(p.headerBg)
        toolbar.setTitleTextColor(android.graphics.Color.parseColor("#F9F3EF"))

        cardStats.setCardBackgroundColor(p.cardBg)
        cardStats.strokeColor = p.cardStroke
        findViewById<View>(R.id.divider1).setBackgroundColor(p.cardStroke)
        findViewById<View>(R.id.divider2).setBackgroundColor(p.cardStroke)
        listOf(R.id.tvStat1Value, R.id.tvStat2Value, R.id.tvStat3Value).forEach {
            findViewById<TextView>(it).setTextColor(p.textPrimary)
        }
        listOf(R.id.tvStat1Label, R.id.tvStat2Label, R.id.tvStat3Label).forEach {
            findViewById<TextView>(it).setTextColor(p.textSecondary)
        }

        tabLayout.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        tabLayout.setTabTextColors(p.textSecondary, p.textPrimary)
        tabLayout.setSelectedTabIndicatorColor(p.accent)

        listOf(
            R.id.chipSwapAll, R.id.chipSwapCompleted, R.id.chipSwapPending, R.id.chipSwapCancelled,
            R.id.chipPtsAll, R.id.chipPtsEarned, R.id.chipPtsSpent
        ).forEach { id -> findViewById<Chip>(id)?.let { themeChip(it) } }

        tvEmpty.setTextColor(p.textSecondary)

        window.setBackgroundDrawable(ColorDrawable(p.screenBg))
        window.statusBarColor = p.headerBg
        window.navigationBarColor = p.screenBg
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = !isDark

        // Re-bind rows with the new palette
        if (::swapAdapter.isInitialized) refresh()
    }

    private fun themeChip(chip: Chip) {
        val checked = intArrayOf(android.R.attr.state_checked)
        val states = arrayOf(checked, intArrayOf())
        chip.chipBackgroundColor = ColorStateList(states, intArrayOf(p.accent, p.cardBg))
        chip.setTextColor(ColorStateList(states, intArrayOf(p.onAccent, p.textSecondary)))
        chip.chipStrokeColor = ColorStateList.valueOf(p.cardStroke)
        chip.chipStrokeWidth = resources.displayMetrics.density   // 1dp
    }
}