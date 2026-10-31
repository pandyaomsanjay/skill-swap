package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Locale

class HistoryActivity : BaseActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var prefs: SharedPreferences

    private var isDark = false
    private var p: HistoryPalette = historyPalette(false)
    private var userId = ""

    private var requesterSwaps: List<Trade> = emptyList()
    private var receiverSwaps: List<Trade> = emptyList()
    private var swapFilter: SwapState? = null

    private var requesterListener: ListenerRegistration? = null
    private var receiverListener: ListenerRegistration? = null

    private lateinit var root: View
    private lateinit var headerBar: View
    private lateinit var btnBack: MaterialCardView
    private lateinit var ivBackIcon: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var cardStats: MaterialCardView
    private lateinit var chipGroupSwaps: ChipGroup
    private lateinit var rvSwaps: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var swapAdapter: SwapHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)

        prefs = getSharedPreferences(Home.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        isDark = prefs.getBoolean(Home.KEY_DARK_MODE, false)
        p = historyPalette(isDark)
        userId = prefs.getString(Home.KEY_USER_EMAIL, null) ?: FirebaseAuth.getInstance().currentUser?.email ?: ""

        bindViews()
        setupLists()
        setupChips()

        btnBack.setOnClickListener { finish() }

        applyTheme()
        listenForSwaps()
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
    }

    private fun bindViews() {
        root = findViewById(R.id.historyRoot)
        headerBar = findViewById(R.id.headerBar)
        btnBack = findViewById(R.id.btnBack)
        ivBackIcon = findViewById(R.id.ivBackIcon)
        tvTitle = findViewById(R.id.tvTitle)
        cardStats = findViewById(R.id.cardStats)
        chipGroupSwaps = findViewById(R.id.chipGroupSwaps)
        rvSwaps = findViewById(R.id.rvSwaps)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun setupLists() {
        swapAdapter = SwapHistoryAdapter { trade ->
            startActivity(Intent(this, MyTradesActivity::class.java).putExtra("tradeId", trade.id))
        }
        rvSwaps.layoutManager = LinearLayoutManager(this)
        rvSwaps.adapter = swapAdapter
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
    }

    private fun listenForSwaps() {
        if (userId.isBlank()) return

        requesterListener = db.collection("trades")
            .whereEqualTo("requesterId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                requesterSwaps = snap.documents.mapNotNull { d -> d.toObject(Trade::class.java)?.copy(id = d.id) }
                refresh()
            }

        receiverListener = db.collection("trades")
            .whereEqualTo("receiverId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(100)
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                receiverSwaps = snap.documents.mapNotNull { d -> d.toObject(Trade::class.java)?.copy(id = d.id) }
                refresh()
            }
    }

    private fun refresh() {
        if (!::swapAdapter.isInitialized) return

        val allSwaps = (requesterSwaps + receiverSwaps).distinctBy { it.id }.sortedByDescending { it.timestamp }
        val swaps = swapFilter?.let { f -> allSwaps.filter { it.swapState() == f } } ?: allSwaps

        swapAdapter.submit(swaps, p, userId)

        rvSwaps.visibility = if (swaps.isNotEmpty()) View.VISIBLE else View.GONE

        if (swaps.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            tvEmpty.text = if (allSwaps.isEmpty()) "No swaps yet.\nYour past swaps will appear here." else "No swaps match this filter."
        } else {
            tvEmpty.visibility = View.GONE
        }

        updateStats(allSwaps)
    }

    private fun updateStats(allSwaps: List<Trade>) {
        val completed = allSwaps.count { it.swapState() == SwapState.COMPLETED }
        val rated = allSwaps.filter { it.rating > 0f }
        val avg = if (rated.isEmpty()) "—" else String.format(Locale.getDefault(), "%.1f", rated.map { it.rating.toDouble() }.average())

        findViewById<TextView>(R.id.tvStat1Value).text = allSwaps.size.toString()
        findViewById<TextView>(R.id.tvStat1Label).text = "Total swaps"
        findViewById<TextView>(R.id.tvStat2Value).text = completed.toString()
        findViewById<TextView>(R.id.tvStat2Label).text = "Completed"
        findViewById<TextView>(R.id.tvStat3Value).text = avg
        findViewById<TextView>(R.id.tvStat3Label).text = "Avg rating"
    }

    private fun applyTheme() {
        root.setBackgroundColor(p.screenBg)

        // Custom Header Theme
        headerBar.setBackgroundColor(p.headerBg)
        btnBack.setCardBackgroundColor(if (isDark) Color.parseColor("#22323D") else Color.parseColor("#456882"))
        ivBackIcon.imageTintList = ColorStateList.valueOf(Color.parseColor("#F9F3EF"))
        tvTitle.setTextColor(Color.parseColor("#F9F3EF"))

        cardStats.setCardBackgroundColor(p.cardBg)
        cardStats.strokeColor = p.cardStroke
        findViewById<View>(R.id.divider1).setBackgroundColor(p.cardStroke)
        findViewById<View>(R.id.divider2).setBackgroundColor(p.cardStroke)

        listOf(R.id.tvStat1Value, R.id.tvStat2Value, R.id.tvStat3Value).forEach { findViewById<TextView>(it).setTextColor(p.textPrimary) }
        listOf(R.id.tvStat1Label, R.id.tvStat2Label, R.id.tvStat3Label).forEach { findViewById<TextView>(it).setTextColor(p.textSecondary) }

        listOf(R.id.chipSwapAll, R.id.chipSwapCompleted, R.id.chipSwapPending, R.id.chipSwapCancelled).forEach { id -> findViewById<Chip>(id)?.let { themeChip(it) } }
        tvEmpty.setTextColor(p.textSecondary)

        window.setBackgroundDrawable(ColorDrawable(p.screenBg))
        window.statusBarColor = p.headerBg
        window.navigationBarColor = p.screenBg
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = !isDark

        if (::swapAdapter.isInitialized) refresh()
    }

    private fun themeChip(chip: Chip) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chip.chipBackgroundColor = ColorStateList(states, intArrayOf(p.accent, p.cardBg))
        chip.setTextColor(ColorStateList(states, intArrayOf(p.onAccent, p.textSecondary)))
        chip.chipStrokeColor = ColorStateList.valueOf(p.cardStroke)
        chip.chipStrokeWidth = resources.displayMetrics.density
    }
}