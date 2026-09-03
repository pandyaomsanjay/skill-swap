package com.example.sgp

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.firestore
import com.onesignal.OneSignal
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class DashboardRange { TODAY, WEEK, MONTH, CUSTOM, ALL }

class AdminDashboardActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    // Header / stats
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvTotalTrades: TextView
    private lateinit var tvTotalSkills: TextView
    private lateinit var tvPendingReports: TextView

    // Chart
    private lateinit var tvPeakValue: TextView
    private lateinit var swapsTrendView: SwapsTrendView
    private lateinit var llXAxisLabels: LinearLayout
    private lateinit var tvYAxis4: TextView
    private lateinit var tvYAxis3: TextView
    private lateinit var tvYAxis2: TextView
    private lateinit var tvYAxis1: TextView
    private lateinit var tvYAxis0: TextView

    // Overview chips
    private lateinit var cardOvToday: MaterialCardView
    private lateinit var cardOvWeek: MaterialCardView
    private lateinit var cardOvMonth: MaterialCardView
    private lateinit var cardOvCustom: MaterialCardView
    private lateinit var cardOvAll: MaterialCardView
    private lateinit var tvOvToday: TextView
    private lateinit var tvOvWeek: TextView
    private lateinit var tvOvMonth: TextView
    private lateinit var tvOvCustom: TextView
    private lateinit var tvOvAll: TextView

    // Swaps chips
    private lateinit var cardSwToday: MaterialCardView
    private lateinit var cardSwWeek: MaterialCardView
    private lateinit var cardSwMonth: MaterialCardView
    private lateinit var cardSwCustom: MaterialCardView
    private lateinit var cardSwAll: MaterialCardView
    private lateinit var tvSwToday: TextView
    private lateinit var tvSwWeek: TextView
    private lateinit var tvSwMonth: TextView
    private lateinit var tvSwCustom: TextView
    private lateinit var tvSwAll: TextView

    // Range state
    private var overviewRange = DashboardRange.TODAY
    private var overviewCustomStart: Long = 0L
    private var overviewCustomEnd: Long = 0L

    private var swapsRange = DashboardRange.WEEK
    private var swapsCustomStart: Long = 0L
    private var swapsCustomEnd: Long = 0L

    // Live Firestore listeners
    private var usersListener: ListenerRegistration? = null
    private var tradesOverviewListener: ListenerRegistration? = null
    private var skillsListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null
    private var swapsChartListener: ListenerRegistration? = null

    // Theme palette
    private val navyDark = Color.parseColor("#1B3C53")
    private val navyMed = Color.parseColor("#456882")
    private val cream = Color.parseColor("#F9F3EF")
    private val lightBg = Color.parseColor("#EAF1F5")
    private val destructive = Color.parseColor("#DC2626")

    private val colorChipSelectedBg = Color.parseColor("#1B3C53")
    private val colorChipUnselectedBg = Color.parseColor("#F9F3EF")
    private val colorChipSelectedText = Color.parseColor("#F9F3EF")
    private val colorChipUnselectedText = Color.parseColor("#456882")
    private val colorChipUnselectedStroke = Color.parseColor("#D2C1B6")

    private val chipLabelFormat = SimpleDateFormat("d MMM", Locale.getDefault())

    private val TAG = "AdminDashboard"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        db = Firebase.firestore

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val userType = prefs.getString("user_type", "")
        if (userType != "admin") {
            Toast.makeText(this, "Unauthorized access", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Bind admin's email or UID to OneSignal for targeted pushes & alerts
        val adminEmail = prefs.getString("user_email", "") ?: ""
        if (adminEmail.isNotBlank()) {
            OneSignal.login(adminEmail.trim().lowercase())
        }

        bindViews()

        setupOverviewChips()
        setupSwapsChips()
        setupNavigation()

        applyOverviewRange(DashboardRange.TODAY)
        applySwapsRange(DashboardRange.WEEK)
    }

    override fun onDestroy() {
        super.onDestroy()
        usersListener?.remove()
        tradesOverviewListener?.remove()
        skillsListener?.remove()
        reportsListener?.remove()
        swapsChartListener?.remove()
    }

    private fun bindViews() {
        tvTotalUsers = findViewById(R.id.tvTotalUsers)
        tvTotalTrades = findViewById(R.id.tvTotalTrades)
        tvTotalSkills = findViewById(R.id.tvTotalSkills)
        tvPendingReports = findViewById(R.id.tvPendingReports)

        tvPeakValue = findViewById(R.id.tvPeakValue)
        swapsTrendView = findViewById(R.id.swapsTrendView)
        llXAxisLabels = findViewById(R.id.llXAxisLabels)
        tvYAxis4 = findViewById(R.id.tvYAxis4)
        tvYAxis3 = findViewById(R.id.tvYAxis3)
        tvYAxis2 = findViewById(R.id.tvYAxis2)
        tvYAxis1 = findViewById(R.id.tvYAxis1)
        tvYAxis0 = findViewById(R.id.tvYAxis0)

        cardOvToday = findViewById(R.id.cardOvToday)
        cardOvWeek = findViewById(R.id.cardOvWeek)
        cardOvMonth = findViewById(R.id.cardOvMonth)
        cardOvCustom = findViewById(R.id.cardOvCustom)
        cardOvAll = findViewById(R.id.cardOvAll)
        tvOvToday = findViewById(R.id.tvOvToday)
        tvOvWeek = findViewById(R.id.tvOvWeek)
        tvOvMonth = findViewById(R.id.tvOvMonth)
        tvOvCustom = findViewById(R.id.tvOvCustom)
        tvOvAll = findViewById(R.id.tvOvAll)

        cardSwToday = findViewById(R.id.cardSwToday)
        cardSwWeek = findViewById(R.id.cardSwWeek)
        cardSwMonth = findViewById(R.id.cardSwMonth)
        cardSwCustom = findViewById(R.id.cardSwCustom)
        cardSwAll = findViewById(R.id.cardSwAll)
        tvSwToday = findViewById(R.id.tvSwToday)
        tvSwWeek = findViewById(R.id.tvSwWeek)
        tvSwMonth = findViewById(R.id.tvSwMonth)
        tvSwCustom = findViewById(R.id.tvSwCustom)
        tvSwAll = findViewById(R.id.tvSwAll)

        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            showLogoutDialog()
        }

        findViewById<View>(R.id.btnMenu).setOnClickListener {
            openSettings()
        }

        findViewById<View>(R.id.btnVideos).setOnClickListener {
            startActivity(Intent(this, AdminVideosActivity::class.java))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        }
    }

    private fun setupNavigation() {
        findViewById<View>(R.id.navDashboard).setOnClickListener { }
        findViewById<View>(R.id.navUsers).setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        }
        findViewById<View>(R.id.navSwaps).setOnClickListener {
            startActivity(Intent(this, AdminTradesActivity::class.java))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        }
        findViewById<View>(R.id.navReports).setOnClickListener {
            startActivity(Intent(this, AdminReportsActivity::class.java))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        }
        findViewById<View>(R.id.navFeedback).setOnClickListener {
            openFeedback()
        }
    }

    private fun openSettings() {
        try {
            startActivity(Intent(this, AdminSettingsActivity::class.java))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        } catch (e: Exception) {
            Toast.makeText(this, "Settings screen coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openFeedback() {
        try {
            startActivity(Intent(this, AdminFeedbackActivity::class.java))
            overridePendingTransition(R.anim.nav_enter, R.anim.nav_exit)
        } catch (e: Exception) {
            Toast.makeText(this, "Feedback screen coming soon", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupOverviewChips() {
        cardOvToday.setOnClickListener { applyOverviewRange(DashboardRange.TODAY) }
        cardOvWeek.setOnClickListener { applyOverviewRange(DashboardRange.WEEK) }
        cardOvMonth.setOnClickListener { applyOverviewRange(DashboardRange.MONTH) }
        cardOvCustom.setOnClickListener { pickCustomRange(isOverview = true) }
        cardOvAll.setOnClickListener { applyOverviewRange(DashboardRange.ALL) }
    }

    private fun setupSwapsChips() {
        cardSwToday.setOnClickListener { applySwapsRange(DashboardRange.TODAY) }
        cardSwWeek.setOnClickListener { applySwapsRange(DashboardRange.WEEK) }
        cardSwMonth.setOnClickListener { applySwapsRange(DashboardRange.MONTH) }
        cardSwCustom.setOnClickListener { pickCustomRange(isOverview = false) }
        cardSwAll.setOnClickListener { applySwapsRange(DashboardRange.ALL) }
    }

    private fun pickCustomRange(isOverview: Boolean) {
        val picker = MaterialDatePicker.Builder.dateRangePicker()
            .setTitleText("Select date range")
            .build()

        picker.addOnPositiveButtonClickListener { selection ->
            val start = selection.first ?: return@addOnPositiveButtonClickListener
            val end = selection.second ?: return@addOnPositiveButtonClickListener
            val endOfDay = end + (24 * 60 * 60 * 1000L) - 1
            val label = "${chipLabelFormat.format(Date(start))} - ${chipLabelFormat.format(Date(endOfDay))}"

            if (isOverview) {
                overviewCustomStart = start
                overviewCustomEnd = endOfDay
                tvOvCustom.text = label
                applyOverviewRange(DashboardRange.CUSTOM)
            } else {
                swapsCustomStart = start
                swapsCustomEnd = endOfDay
                tvSwCustom.text = label
                applySwapsRange(DashboardRange.CUSTOM)
            }
        }

        picker.show(supportFragmentManager, "date_range_picker")
    }

    private fun applyOverviewRange(range: DashboardRange) {
        overviewRange = range
        updateChipStyles(
            listOf(
                cardOvToday to tvOvToday,
                cardOvWeek to tvOvWeek,
                cardOvMonth to tvOvMonth,
                cardOvCustom to tvOvCustom,
                cardOvAll to tvOvAll
            ),
            rangeIndex(range)
        )
        val (start, end) = rangeToMillis(range, overviewCustomStart, overviewCustomEnd)
        attachOverviewListeners(start, end, range)
    }

    private fun applySwapsRange(range: DashboardRange) {
        swapsRange = range
        updateChipStyles(
            listOf(
                cardSwToday to tvSwToday,
                cardSwWeek to tvSwWeek,
                cardSwMonth to tvSwMonth,
                cardSwCustom to tvSwCustom,
                cardSwAll to tvSwAll
            ),
            rangeIndex(range)
        )
        val (start, end) = rangeToMillis(range, swapsCustomStart, swapsCustomEnd)
        attachSwapsChartListener(start, end, range)
    }

    private fun rangeIndex(range: DashboardRange) = when (range) {
        DashboardRange.TODAY -> 0
        DashboardRange.WEEK -> 1
        DashboardRange.MONTH -> 2
        DashboardRange.CUSTOM -> 3
        DashboardRange.ALL -> 4
    }

    private fun updateChipStyles(chips: List<Pair<MaterialCardView, TextView>>, selectedIndex: Int) {
        val strokeWidthPx = (1 * resources.displayMetrics.density).toInt()
        chips.forEachIndexed { index, (card, text) ->
            if (index == selectedIndex) {
                card.setCardBackgroundColor(colorChipSelectedBg)
                card.strokeWidth = 0
                text.setTextColor(colorChipSelectedText)
            } else {
                card.setCardBackgroundColor(colorChipUnselectedBg)
                card.strokeWidth = strokeWidthPx
                card.strokeColor = colorChipUnselectedStroke
                text.setTextColor(colorChipUnselectedText)
            }
        }
    }

    private fun rangeToMillis(range: DashboardRange, customStart: Long, customEnd: Long): Pair<Long, Long> {
        val now = System.currentTimeMillis()
        return when (range) {
            DashboardRange.TODAY -> {
                val cal = Calendar.getInstance()
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                Pair(cal.timeInMillis, now)
            }
            DashboardRange.WEEK -> Pair(now - 7L * 24 * 60 * 60 * 1000, now)
            DashboardRange.MONTH -> Pair(now - 30L * 24 * 60 * 60 * 1000, now)
            DashboardRange.CUSTOM -> {
                if (customStart == 0L || customEnd == 0L) Pair(now - 7L * 24 * 60 * 60 * 1000, now)
                else Pair(customStart, customEnd)
            }
            DashboardRange.ALL -> Pair(0L, now)
        }
    }

    private fun attachOverviewListeners(startMillis: Long, endMillis: Long, range: DashboardRange) {
        usersListener?.remove()
        tradesOverviewListener?.remove()
        skillsListener?.remove()
        reportsListener?.remove()

        val isAll = range == DashboardRange.ALL

        var usersQuery: Query = db.collection("users")
        if (!isAll) {
            usersQuery = usersQuery
                .whereGreaterThanOrEqualTo("joinedDate", startMillis)
                .whereLessThanOrEqualTo("joinedDate", endMillis)
        }
        usersListener = usersQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "users listener failed", error)
                return@addSnapshotListener
            }
            tvTotalUsers.text = (snapshot?.size() ?: 0).toString()
        }

        var tradesQuery: Query = db.collection("trades")
        if (!isAll) {
            tradesQuery = tradesQuery
                .whereGreaterThanOrEqualTo("timestamp", startMillis)
                .whereLessThanOrEqualTo("timestamp", endMillis)
        }
        tradesOverviewListener = tradesQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "trades listener failed", error)
                return@addSnapshotListener
            }
            tvTotalTrades.text = (snapshot?.size() ?: 0).toString()
        }

        var skillsQuery: Query = db.collection("skills")
        if (!isAll) {
            skillsQuery = skillsQuery
                .whereGreaterThanOrEqualTo("timestamp", startMillis)
                .whereLessThanOrEqualTo("timestamp", endMillis)
        }
        skillsListener = skillsQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "skills listener failed", error)
                return@addSnapshotListener
            }
            tvTotalSkills.text = (snapshot?.size() ?: 0).toString()
        }

        var reportsQuery: Query = db.collection("reports").whereEqualTo("status", "pending")
        if (!isAll) {
            reportsQuery = reportsQuery
                .whereGreaterThanOrEqualTo("timestamp", startMillis)
                .whereLessThanOrEqualTo("timestamp", endMillis)
        }
        reportsListener = reportsQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "reports listener failed", error)
                tvPendingReports.text = "0"
                return@addSnapshotListener
            }
            tvPendingReports.text = (snapshot?.size() ?: 0).toString()
        }
    }

    private fun attachSwapsChartListener(startMillis: Long, endMillis: Long, range: DashboardRange) {
        swapsChartListener?.remove()

        val isAll = range == DashboardRange.ALL

        var chartQuery: Query = db.collection("trades")
        if (!isAll) {
            chartQuery = chartQuery
                .whereGreaterThanOrEqualTo("timestamp", startMillis)
                .whereLessThanOrEqualTo("timestamp", endMillis)
        }
        chartQuery = chartQuery.orderBy("timestamp", Query.Direction.ASCENDING)

        swapsChartListener = chartQuery.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e(TAG, "swaps chart listener failed", error)
                return@addSnapshotListener
            }
            val docTimestamps = snapshot?.documents?.mapNotNull {
                it.getLong("timestamp")
            } ?: emptyList()

            if (isAll) {
                if (docTimestamps.isEmpty()) {
                    renderChart(0L, 1L, emptyList(), 7, range)
                } else {
                    val actualStart = docTimestamps.min()
                    var actualEnd = docTimestamps.max()
                    val bucketCount = 8

                    val minSpan = bucketCount.toLong() * 24L * 60 * 60 * 1000L
                    if (actualEnd - actualStart < minSpan) {
                        actualEnd = actualStart + minSpan
                    }
                    renderChart(actualStart, actualEnd, docTimestamps, bucketCount, range)
                }
            } else {
                val bucketCount = when (range) {
                    DashboardRange.TODAY -> 6
                    DashboardRange.WEEK -> 7
                    DashboardRange.MONTH -> 6
                    DashboardRange.CUSTOM -> 7
                    DashboardRange.ALL -> 8
                }
                renderChart(startMillis, endMillis, docTimestamps, bucketCount, range)
            }
        }
    }

    private fun renderChart(
        startMillis: Long,
        endMillis: Long,
        timestamps: List<Long>,
        bucketCount: Int,
        range: DashboardRange
    ) {
        val safeEnd = if (endMillis <= startMillis) startMillis + 1 else endMillis
        val bucketSize = (safeEnd - startMillis) / bucketCount
        val counts = FloatArray(bucketCount)

        for (t in timestamps) {
            var idx = if (bucketSize == 0L) 0 else ((t - startMillis) / bucketSize).toInt()
            if (idx >= bucketCount) idx = bucketCount - 1
            if (idx < 0) idx = 0
            counts[idx] = counts[idx] + 1
        }

        val countsList = counts.toList()

        val sdfDay = SimpleDateFormat("d MMM", Locale.getDefault())
        val sdfHour = SimpleDateFormat("h a", Locale.getDefault())
        val sdfMonth = SimpleDateFormat("MMM yy", Locale.getDefault())
        val bucketLabels = (0 until bucketCount).map { i ->
            val bucketStart = startMillis + (bucketSize * i)
            when {
                range == DashboardRange.TODAY -> sdfHour.format(Date(bucketStart))
                range == DashboardRange.ALL -> sdfMonth.format(Date(bucketStart))
                else -> sdfDay.format(Date(bucketStart))
            }
        }

        val peak = countsList.maxOrNull() ?: 0f
        val topValue = computeAxisTop(peak)

        swapsTrendView.setData(countsList, bucketLabels, topValue)

        tvPeakValue.text = peak.toInt().toString()

        tvYAxis4.text = formatAxisValue(topValue)
        tvYAxis3.text = formatAxisValue(topValue * 3f / 4f)
        tvYAxis2.text = formatAxisValue(topValue / 2f)
        tvYAxis1.text = formatAxisValue(topValue / 4f)
        tvYAxis0.text = "0"

        llXAxisLabels.removeAllViews()
        for (i in 0 until bucketCount) {
            val tv = TextView(this).apply {
                text = bucketLabels[i]
                setTextColor(Color.parseColor("#456882"))
                textSize = 10f
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            llXAxisLabels.addView(tv)
        }
    }

    private fun computeAxisTop(peak: Float): Float {
        if (peak <= 0f) return 4f
        if (peak <= 4f) return peak
        val v = peak.toInt().coerceAtLeast(1)
        val magnitude = Math.pow(10.0, Math.log10(v.toDouble()).toInt().toDouble()).toInt().coerceAtLeast(1)
        var top = ((v / magnitude) + 1) * magnitude
        if (top % 4 != 0) top += (4 - top % 4)
        return top.toFloat()
    }

    private fun formatAxisValue(v: Float): String {
        return if (v == v.toInt().toFloat()) {
            v.toInt().toString()
        } else {
            String.format(Locale.getDefault(), "%.2f", v).trimEnd('0').trimEnd('.')
        }
    }

    private fun showLogoutDialog() {
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "👋"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.logout_confirmation_title)
            setTextColor(navyDark)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.logout_confirmation_message)
            setTextColor(navyMed)
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

        val btnCancel = pillButton(getString(R.string.cancel), lightBg, navyMed).apply {
            setOnClickListener { dialog.dismiss() }
        }

        val btnLogout = pillButton(getString(R.string.logout), destructive, cream).apply {
            setOnClickListener {
                dialog.dismiss()
                performLogout()
            }
        }

        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        )
        buttonRow.addView(
            btnLogout,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(buttonRow)

        dialog.show()
    }

    private fun performLogout() {
        // Disassociate device from the admin user in OneSignal
        OneSignal.logout()

        FirebaseAuth.getInstance().signOut()

        getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()

        val intent = Intent(this, Login::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

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
}

class SwapsTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var points: List<Float> = listOf(0f, 0f)
    private var labels: List<String> = listOf("", "")
    private var axisMax: Float = -1f

    private var barCenters: FloatArray = floatArrayOf()
    private var barTops: FloatArray = floatArrayOf()
    private var barLefts: FloatArray = floatArrayOf()
    private var barRights: FloatArray = floatArrayOf()

    private var selectedIndex: Int = -1

    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B3C53")
        style = Paint.Style.FILL
    }

    private val barSelectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#456882")
        style = Paint.Style.FILL
    }

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D2C1B6")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tooltipBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1B3C53")
        style = Paint.Style.FILL
    }

    private val tooltipBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#456882")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val tooltipValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F9F3EF")
        textSize = 30f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private val tooltipLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#D2C1B6")
        textSize = 22f
        textAlign = Paint.Align.CENTER
    }

    fun setData(newPoints: List<Float>, newLabels: List<String> = emptyList(), newAxisMax: Float = -1f) {
        points = if (newPoints.size < 2) listOf(0f, 0f) else newPoints
        labels = if (newLabels.size == points.size) newLabels else List(points.size) { "" }
        axisMax = newAxisMax
        selectedIndex = -1
        invalidate()
    }

    override fun onTouchEvent(event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                if (barCenters.isEmpty()) return true
                val touchX = event.x
                var closest = 0
                var closestDist = Float.MAX_VALUE
                for (i in barCenters.indices) {
                    val d = Math.abs(barCenters[i] - touchX)
                    if (d < closestDist) {
                        closestDist = d
                        closest = i
                    }
                }
                selectedIndex = closest
                invalidate()
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (points.size < 2 || width == 0 || height == 0) return

        val w = width.toFloat()
        val h = height.toFloat()
        val topPad = h * 0.16f
        val bottomPad = h * 0.04f
        val plotTop = topPad
        val plotBottom = h - bottomPad
        val plotHeight = plotBottom - plotTop

        val localMax = (points.maxOrNull() ?: 0f).let { if (it <= 0f) 1f else it }
        val maxVal = if (axisMax > 0f) axisMax else localMax

        val gridRows = 4
        for (i in 0..gridRows) {
            val y = plotTop + (plotHeight / gridRows) * i
            canvas.drawLine(0f, y, w, y, gridPaint)
        }

        val slotWidth = w / points.size
        val barWidth = slotWidth * 0.55f
        val cornerRadius = (barWidth / 2f).coerceAtMost(10f)

        barCenters = FloatArray(points.size)
        barTops = FloatArray(points.size)
        barLefts = FloatArray(points.size)
        barRights = FloatArray(points.size)

        points.forEachIndexed { i, value ->
            val center = slotWidth * i + slotWidth / 2f
            val barHeight = (value / maxVal) * plotHeight
            barCenters[i] = center
            barLefts[i] = center - barWidth / 2f
            barRights[i] = center + barWidth / 2f
            barTops[i] = plotBottom - barHeight
        }

        for (i in points.indices) {
            val paint = if (i == selectedIndex) barSelectedPaint else barPaint
            val rect = android.graphics.RectF(barLefts[i], barTops[i], barRights[i], plotBottom)
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, paint)
        }

        if (selectedIndex in points.indices) {
            drawTooltip(canvas, selectedIndex, w)
        }
    }

    private fun drawTooltip(canvas: Canvas, index: Int, viewWidth: Float) {
        val value = points[index].toInt().toString()
        val label = labels.getOrElse(index) { "" }

        val boxWidth = 130f
        val boxHeight = if (label.isNotBlank()) 74f else 50f
        var boxLeft = barCenters[index] - boxWidth / 2f
        if (boxLeft < 4f) boxLeft = 4f
        if (boxLeft + boxWidth > viewWidth - 4f) boxLeft = viewWidth - 4f - boxWidth
        val boxTop = (barTops[index] - boxHeight - 18f).coerceAtLeast(4f)

        val rect = android.graphics.RectF(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
        canvas.drawRoundRect(rect, 16f, 16f, tooltipBgPaint)
        canvas.drawRoundRect(rect, 16f, 16f, tooltipBorderPaint)

        val centerX = boxLeft + boxWidth / 2f
        canvas.drawText(value, centerX, boxTop + 34f, tooltipValuePaint)
        if (label.isNotBlank()) {
            canvas.drawText(label, centerX, boxTop + 60f, tooltipLabelPaint)
        }
    }
}