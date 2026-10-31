package com.example.sgp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PointsTransactionsActivity : BaseActivity() {

    private val db by lazy { FirebaseFirestore.getInstance() }
    private lateinit var prefs: SharedPreferences

    private var isDark = false
    private var p: HistoryPalette = historyPalette(false)

    private var userEmail = ""
    private var userUid = ""

    private var normalTransactions: List<PointTransaction> = emptyList()
    private var demoTransactions: List<PointTransaction> = emptyList()
    private var allTransactions: List<PointTransaction> = emptyList()

    // Stores the raw data for demo transactions so we can show details in the dialog
    private val rawDemoData = mutableMapOf<String, Map<String, Any>>()

    private var pointsFilter = 0 // 0 = All, 1 = Earned, 2 = Spent, 3 = Purchases

    private var txListener: ListenerRegistration? = null
    private var demoListener: ListenerRegistration? = null

    private lateinit var root: View
    private lateinit var headerBar: View
    private lateinit var btnBack: MaterialCardView
    private lateinit var ivBackIcon: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var cardStats: MaterialCardView
    private lateinit var chipGroupPoints: ChipGroup
    private lateinit var rvPoints: RecyclerView
    private lateinit var tvEmpty: TextView

    private lateinit var txAdapter: PointsHistoryAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_points_transactions)

        prefs = getSharedPreferences(Home.SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        isDark = prefs.getBoolean(Home.KEY_DARK_MODE, false)
        p = historyPalette(isDark)

        val firebaseUser = FirebaseAuth.getInstance().currentUser
        userEmail = firebaseUser?.email ?: prefs.getString(Home.KEY_USER_EMAIL, "") ?: ""
        userUid = firebaseUser?.uid ?: ""

        bindViews()
        setupLists()
        setupChips()

        btnBack.setOnClickListener { finish() }

        applyTheme()
        listenForTransactions()
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
        txListener?.remove()
        demoListener?.remove()
    }

    private fun bindViews() {
        root = findViewById(R.id.pointsRoot)
        headerBar = findViewById(R.id.headerBar)
        btnBack = findViewById(R.id.btnBack)
        ivBackIcon = findViewById(R.id.ivBackIcon)
        tvTitle = findViewById(R.id.tvTitle)
        cardStats = findViewById(R.id.cardStats)
        chipGroupPoints = findViewById(R.id.chipGroupPoints)
        rvPoints = findViewById(R.id.rvPoints)
        tvEmpty = findViewById(R.id.tvEmpty)
    }

    private fun setupLists() {
        // Here we attach the click listener to open the detail dialog
        txAdapter = PointsHistoryAdapter { transaction ->
            showTransactionDetailDialog(transaction)
        }
        rvPoints.layoutManager = LinearLayoutManager(this)
        rvPoints.adapter = txAdapter
    }

    private fun setupChips() {
        chipGroupPoints.setOnCheckedStateChangeListener { _, ids ->
            pointsFilter = when (ids.firstOrNull()) {
                R.id.chipPtsEarned -> 1
                R.id.chipPtsSpent -> 2
                R.id.chipPtsPurchases -> 3
                else -> 0
            }
            refresh()
        }
    }

    private fun listenForTransactions() {
        if (userEmail.isBlank() && userUid.isBlank()) return

        val normalId = if (userEmail.isNotBlank()) userEmail else userUid
        txListener = db.collection("transactions")
            .whereEqualTo("userId", normalId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("PointsActivity", "Error txListener: ${err.message}", err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                normalTransactions = snap.documents.mapNotNull { d ->
                    d.toObject(PointTransaction::class.java)?.copy(id = d.id)
                }
                mergeAndRefresh()
            }

        val searchIds = listOf(userEmail, userUid).filter { it.isNotBlank() }

        demoListener = db.collection("demoTransactions")
            .whereIn("userId", searchIds)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    Log.e("PointsActivity", "Error demoListener: ${err.message}", err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener

                demoTransactions = snap.documents.mapNotNull { d ->
                    // Save the raw data for the dialog
                    rawDemoData[d.id] = d.data ?: emptyMap()

                    val creditsNum = d.get("credits") ?: d.get("amount") ?: d.get("creditsPurchased")
                    val credits = when (creditsNum) {
                        is Long -> creditsNum.toInt()
                        is Double -> creditsNum.toInt()
                        is Number -> creditsNum.toInt()
                        is String -> creditsNum.toIntOrNull() ?: 0
                        else -> 0
                    }

                    if (credits <= 0) return@mapNotNull null

                    val pkgId = d.getString("packageId") ?: d.getString("packageName") ?: "Credit"
                    val title = "Purchased ${pkgId.replaceFirstChar { it.uppercase() }} Package"

                    val tsObj = d.get("timestamp") ?: d.get("createdAt") ?: d.get("purchaseTimestamp")
                    val timestamp = when (tsObj) {
                        is Long -> tsObj
                        is Number -> tsObj.toLong()
                        is com.google.firebase.Timestamp -> tsObj.toDate().time
                        else -> System.currentTimeMillis()
                    }

                    PointTransaction(
                        id = d.id,
                        userId = d.getString("userId") ?: normalId,
                        amount = credits,
                        title = title,
                        type = "PURCHASE",
                        timestamp = timestamp
                    )
                }
                mergeAndRefresh()
            }
    }

    private fun mergeAndRefresh() {
        allTransactions = (normalTransactions + demoTransactions)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
        refresh()
    }

    private fun refresh() {
        if (!::txAdapter.isInitialized) return

        val demoIds = demoTransactions.map { it.id }.toSet()

        val txs = when (pointsFilter) {
            1 -> allTransactions.filter { it.amount > 0 && !demoIds.contains(it.id) }
            2 -> allTransactions.filter { it.amount < 0 }
            3 -> allTransactions.filter { demoIds.contains(it.id) }
            else -> allTransactions
        }

        txAdapter.submit(txs, p)

        if (txs.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            rvPoints.visibility = View.GONE
            tvEmpty.text = if (allTransactions.isEmpty()) {
                "No point activity yet.\nPoints you earn or spend will appear here."
            } else {
                "Nothing matches this filter."
            }
        } else {
            tvEmpty.visibility = View.GONE
            rvPoints.visibility = View.VISIBLE
        }

        updateStats()
    }

    private fun updateStats() {
        val earned = allTransactions.filter { it.amount > 0 }.sumOf { it.amount }
        val spent = allTransactions.filter { it.amount < 0 }.sumOf { -it.amount }

        findViewById<TextView>(R.id.tvStat1Value).text = prefs.getInt(Home.KEY_USER_POINTS, 0).toString()
        findViewById<TextView>(R.id.tvStat1Label).text = "Balance"
        findViewById<TextView>(R.id.tvStat2Value).text = "+$earned"
        findViewById<TextView>(R.id.tvStat2Label).text = "Earned"
        findViewById<TextView>(R.id.tvStat3Value).text = "-$spent"
        findViewById<TextView>(R.id.tvStat3Label).text = "Spent"
    }

    // ---------------------------------------------------------------------
    // THEMED TRANSACTION DETAIL DIALOG
    // ---------------------------------------------------------------------
    private fun showTransactionDetailDialog(tx: PointTransaction) {
        val rootView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(if (isDark) Color.parseColor("#16232E") else Color.WHITE)
            }
        }

        // Title
        rootView.addView(TextView(this).apply {
            text = "Transaction Details"
            setTextColor(if (isDark) Color.parseColor("#F5F7F8") else Color.parseColor("#1B3C53"))
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(16) }
        })

        // Amount Highlight
        rootView.addView(TextView(this).apply {
            val earned = tx.amount >= 0
            text = if (earned) "+${tx.amount} Credits" else "${tx.amount} Credits"
            setTextColor(if (earned) Color.parseColor("#2E7D32") else Color.parseColor("#C62828"))
            textSize = 24f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(20) }
        })

        val dateFmt = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault())
        val dateStr = if (tx.timestamp > 0) dateFmt.format(Date(tx.timestamp)) else "N/A"

        // Add rows
        fun addDetailRow(label: String, value: String) {
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(TextView(this).apply {
                text = label
                setTextColor(if (isDark) Color.parseColor("#8FA6B8") else Color.parseColor("#456882"))
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            row.addView(TextView(this).apply {
                text = value
                setTextColor(if (isDark) Color.parseColor("#F5F7F8") else Color.parseColor("#1B3C53"))
                textSize = 14f
                setTypeface(typeface, Typeface.BOLD)
                gravity = Gravity.END
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            rootView.addView(row)

            // Divider
            rootView.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(1))
                setBackgroundColor(if (isDark) Color.parseColor("#2A3B48") else Color.parseColor("#EAF1F5"))
            })
        }

        addDetailRow("Description", tx.title)
        addDetailRow("Date", dateStr)
        addDetailRow("Transaction ID", tx.id)

        // Add specific details if it's a demo purchase
        if (rawDemoData.containsKey(tx.id)) {
            val raw = rawDemoData[tx.id]
            val method = raw?.get("paymentMethod")?.toString() ?: "Simulated"
            val status = raw?.get("status")?.toString() ?: "Success"
            addDetailRow("Payment Method", method.replaceFirstChar { it.uppercase() })
            addDetailRow("Status", status.replaceFirstChar { it.uppercase() })
        }

        // Close Button
        rootView.addView(TextView(this).apply {
            text = "Close"
            setTextColor(Color.parseColor("#F9F3EF"))
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(if (isDark) Color.parseColor("#22323D") else Color.parseColor("#1B3C53"))
            }
            isClickable = true
            isFocusable = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(16) }
        })

        val dialog = AlertDialog.Builder(this).setView(rootView).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        // Set click listener on the Close button (last child)
        rootView.getChildAt(rootView.childCount - 1).setOnClickListener { dialog.dismiss() }

        dialog.show()
    }

    private fun applyTheme() {
        root.setBackgroundColor(p.screenBg)

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

        listOf(R.id.chipPtsAll, R.id.chipPtsEarned, R.id.chipPtsSpent, R.id.chipPtsPurchases).forEach { id -> findViewById<Chip>(id)?.let { themeChip(it) } }
        tvEmpty.setTextColor(p.textSecondary)

        window.setBackgroundDrawable(ColorDrawable(p.screenBg))
        window.statusBarColor = p.headerBg
        window.navigationBarColor = p.screenBg
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = !isDark

        if (::txAdapter.isInitialized) refresh()
    }

    private fun themeChip(chip: Chip) {
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        chip.chipBackgroundColor = ColorStateList(states, intArrayOf(p.accent, p.cardBg))
        chip.setTextColor(ColorStateList(states, intArrayOf(p.onAccent, p.textSecondary)))
        chip.chipStrokeColor = ColorStateList.valueOf(p.cardStroke)
        chip.chipStrokeWidth = resources.displayMetrics.density
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}