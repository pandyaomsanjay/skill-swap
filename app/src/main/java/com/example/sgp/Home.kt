package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.RippleDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.shape.CornerFamily
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.onesignal.OneSignal
import kotlinx.coroutines.launch

class Home : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var ivProfilePicture: ImageView
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private var requesterSwapsListener: ListenerRegistration? = null
    private var receiverSwapsListener: ListenerRegistration? = null
    private var skillsCategoryListener: ListenerRegistration? = null

    private var requesterSwaps: List<Trade> = emptyList()
    private var receiverSwaps: List<Trade> = emptyList()
    private var currentUserIdForSwaps: String = ""

    private var isDarkModeEnabled = false

    private val skillCategories = listOf(
        "TECHNOLOGY", "ARTS", "SPORTS", "HOME", "EDUCATION", "LIFESTYLE"
    )

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightNavDrawerBg = Color.parseColor("#EAF1F5")
    private val lightTonalBtnBg = Color.parseColor("#1B3C53")
    private val lightTonalBtnText = Color.parseColor("#F9F3EF")
    private val lightOutlineStroke = Color.parseColor("#D2C1B6")
    private val lightOutlineText = Color.parseColor("#456882")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkNavDrawerBg = Color.parseColor("#0F1B24")
    private val darkTonalBtnBg = Color.parseColor("#22323D")
    private val darkTonalBtnText = Color.parseColor("#F5F7F8")
    private val darkOutlineStroke = Color.parseColor("#2A3B48")
    private val darkOutlineText = Color.parseColor("#8FA6B8")

    private val cream = Color.parseColor("#F9F3EF")
    private val headerTitleColor = Color.parseColor("#F9F3EF")

    companion object {
        const val SHARED_PREFS_NAME = "SkillSwapPrefs"
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_LOCATION = "user_location"
        const val KEY_USER_POINTS = "user_points"
        const val KEY_USER_TOTAL_TRADES = "user_total_trades"
        const val KEY_USER_RATING = "user_rating"
        const val KEY_USER_TOTAL_SKILLS = "user_total_skills"
        const val KEY_USER_PROFILE_IMAGE = "user_profile_image"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_PUSH_NOTIFICATIONS = "push_notifications"

        private const val THEME_DARK_NAVY = "#1B3C53"
        private const val THEME_STEEL_BLUE = "#456882"
        private const val THEME_CREAM = "#F9F3EF"
        private const val THEME_BACKGROUND = "#EAF1F5"
        private const val THEME_STROKE = "#D2C1B6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(KEY_DARK_MODE, false)

        drawerLayout = findViewById(R.id.main)
        navigationView = findViewById(R.id.navigationView)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        toggle.drawerArrowDrawable.color = Color.parseColor(THEME_CREAM)
        toggle.drawerArrowDrawable.setColorFilter(Color.parseColor(THEME_CREAM), android.graphics.PorterDuff.Mode.SRC_ATOP)

        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                val contentView = drawerLayout.getChildAt(0)
                contentView?.apply {
                    translationX = drawerView.width * slideOffset * 0.3f
                    scaleX = 1f - (slideOffset * 0.05f)
                    scaleY = 1f - (slideOffset * 0.05f)
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                supportActionBar?.title = "Menu"
            }

            override fun onDrawerClosed(drawerView: View) {
                supportActionBar?.title = "SkillSwap"
            }

            override fun onDrawerStateChanged(newState: Int) { }
        })

        navigationView.setNavigationItemSelectedListener(this)
        navigationView.setCheckedItem(R.id.nav_home)

        ivProfilePicture.setOnClickListener {
            startActivity(Intent(this, Profile::class.java))
        }

        applyTheme(isDarkModeEnabled)

        setupQuickActions()
        listenForPopularSkillCategories()
        updateGreetingMessage()
        loadUserData()

        BottomNavHelper.setup(this, BottomNavItem.HOME)
    }

    override fun onStart() {
        super.onStart()
        syncOneSignalPushSubscription()
    }

    override fun onResume() {
        super.onResume()
        val savedDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        if (savedDarkMode != isDarkModeEnabled) {
            applyTheme(savedDarkMode)
        }
        loadUserData()
        navigationView.setCheckedItem(R.id.nav_home)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(isDarkModeEnabled)
    }

    /**
     * Links the current user to OneSignal, then respects their push preference
     * from the Settings screen.
     */
    private fun syncOneSignalPushSubscription() {
        val userEmail = sharedPreferences.getString(KEY_USER_EMAIL, null)
            ?: FirebaseAuth.getInstance().currentUser?.email

        if (!userEmail.isNullOrEmpty() && userEmail != "user@example.com") {
            OneSignal.login(userEmail)

            val pushEnabled = sharedPreferences.getBoolean(KEY_PUSH_NOTIFICATIONS, true)
            if (pushEnabled) {
                OneSignal.User.pushSubscription.optIn()
                lifecycleScope.launch {
                    OneSignal.Notifications.requestPermission(true)
                }
            } else {
                OneSignal.User.pushSubscription.optOut()
            }
        }
    }

    // ---------------------------------------------------------------------
    // THEME ENGINE
    // ---------------------------------------------------------------------
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val cardBg = if (isDark) darkCardBg else lightCardBg
        val cardStroke = if (isDark) darkCardStroke else lightCardStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val navDrawerBg = if (isDark) darkNavDrawerBg else lightNavDrawerBg
        val tonalBtnBg = if (isDark) darkTonalBtnBg else lightTonalBtnBg
        val tonalBtnText = if (isDark) darkTonalBtnText else lightTonalBtnText
        val outlineStroke = if (isDark) darkOutlineStroke else lightOutlineStroke
        val outlineText = if (isDark) darkOutlineText else lightOutlineText

        findViewById<View>(R.id.homeRootLayout)?.setBackgroundColor(screenBg)

        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.apply {
            setBackgroundColor(headerBg)
            title = "SkillSwap"
            setTitleTextColor(headerTitleColor)
        }

        findViewById<MaterialCardView>(R.id.cardProfilePoints)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<TextView>(R.id.tvGreeting)?.setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvUserName)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvPoints)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvPointsLabel)?.setTextColor(textSecondary)

        findViewById<TextView>(R.id.tvQuickActionsLabel)?.setTextColor(textSecondary)

        findViewById<MaterialButton>(R.id.btnNearby)?.apply {
            backgroundTintList = ColorStateList.valueOf(tonalBtnBg)
            setTextColor(tonalBtnText)
            iconTint = ColorStateList.valueOf(tonalBtnText)
        }
        findViewById<MaterialButton>(R.id.btnFilter)?.apply {
            strokeColor = ColorStateList.valueOf(outlineStroke)
            setTextColor(outlineText)
            iconTint = ColorStateList.valueOf(outlineText)
        }

        listOf(R.id.cardTileFind, R.id.cardTileTeach, R.id.cardTileCreate).forEach { id ->
            findViewById<MaterialCardView>(id)?.apply {
                setCardBackgroundColor(cardBg)
                strokeColor = cardStroke
            }
        }
        listOf(R.id.ivTileFindIcon, R.id.ivTileTeachIcon, R.id.ivTileCreateIcon).forEach { id ->
            findViewById<ImageView>(id)?.imageTintList = ColorStateList.valueOf(textPrimary)
        }
        listOf(R.id.tvTileFindTitle, R.id.tvTileTeachTitle, R.id.tvTileCreateTitle).forEach { id ->
            findViewById<TextView>(id)?.setTextColor(textPrimary)
        }
        listOf(R.id.tvTileFindSub, R.id.tvTileTeachSub, R.id.tvTileCreateSub).forEach { id ->
            findViewById<TextView>(id)?.setTextColor(textSecondary)
        }

        findViewById<TextView>(R.id.tvPopularSkillsTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvSeeAll)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvRecentSwapsTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvViewAllTrades)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvNoSwaps)?.setTextColor(textSecondary)

        // -------- Drawer styling --------
        styleDrawer(isDark, navDrawerBg)

        themeInflatedCategoryCards(textPrimary, textSecondary, cardBg, cardStroke, textPrimary)
        themeInflatedSwapCards(textPrimary, textSecondary, cardBg, cardStroke, textSecondary)
        themeBottomNavigation()

        forceRedraw(R.id.homeRootLayout)
        forceRedraw(R.id.toolbar)

        applyThemeSystemBars(headerBg, screenBg)
    }

    private fun styleDrawer(isDark: Boolean, navDrawerBg: Int) {
        val corner = dp(28).toFloat()

        // 1) Drawer panel: rounded on the right edge
        val panel = MaterialShapeDrawable(
            ShapeAppearanceModel.builder()
                .setTopRightCorner(CornerFamily.ROUNDED, corner)
                .setBottomRightCorner(CornerFamily.ROUNDED, corner)
                .build()
        ).apply { fillColor = ColorStateList.valueOf(navDrawerBg) }
        navigationView.background = panel

        // 2) Menu items: rounded highlight pill on the selected item + ripple on tap
        val pillColor = if (isDark) {
            Color.parseColor("#22323D")
        } else {
            ColorUtils.setAlphaComponent(Color.parseColor("#1B3C53"), 28)
        }
        val inset = intArrayOf(dp(12), dp(3), dp(12), dp(3))

        val checkedPill = InsetDrawable(
            GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(pillColor)
            },
            inset[0], inset[1], inset[2], inset[3]
        )
        val stateBg = StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), checkedPill)
            addState(intArrayOf(), ColorDrawable(Color.TRANSPARENT))
        }
        val rippleMask = InsetDrawable(
            GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(Color.WHITE)
            },
            inset[0], inset[1], inset[2], inset[3]
        )
        val rippleColor = ColorUtils.setAlphaComponent(
            if (isDark) Color.WHITE else Color.parseColor("#1B3C53"), 40
        )
        navigationView.itemBackground =
            RippleDrawable(ColorStateList.valueOf(rippleColor), stateBg, rippleMask)

        // 3) Text + icon colours (selected item is stronger)
        val unchecked = if (isDark) Color.parseColor("#C9D6E0") else Color.parseColor("#456882")
        val checked = if (isDark) Color.WHITE else Color.parseColor("#1B3C53")
        val states = arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf())
        val tint = ColorStateList(states, intArrayOf(checked, unchecked))
        navigationView.itemTextColor = tint
        navigationView.itemIconTintList = tint

        // 4) Logout in red
        navigationView.menu.findItem(R.id.nav_logout)?.let { item ->
            val red = if (isDark) Color.parseColor("#EF5350") else Color.parseColor("#C62828")
            val label = SpannableString(item.title.toString())
            label.setSpan(ForegroundColorSpan(red), 0, label.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            item.title = label
        }

        // 5) Header: gradient card with rounded bottom
        navigationView.getHeaderView(0)?.let { header ->
            val colors = if (isDark) {
                intArrayOf(Color.parseColor("#0B1620"), Color.parseColor("#1E3A4F"))
            } else {
                intArrayOf(Color.parseColor("#1B3C53"), Color.parseColor("#456882"))
            }
            header.background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
                cornerRadii = floatArrayOf(0f, 0f, corner, corner, corner, corner, corner, corner)
            }

            header.findViewById<TextView>(R.id.tvNavUserName)?.setTextColor(Color.WHITE)
            header.findViewById<TextView>(R.id.tvNavUserEmail)?.setTextColor(Color.parseColor("#CCFFFFFF"))
            header.findViewById<TextView>(R.id.tvNavUserPoints)?.setTextColor(Color.WHITE)
            listOf(R.id.nav_trades_value, R.id.nav_rating_value, R.id.nav_skills_value).forEach {
                header.findViewById<TextView>(it)?.setTextColor(Color.WHITE)
            }
            listOf(R.id.nav_trades_label, R.id.nav_rating_label, R.id.nav_skills_label).forEach {
                header.findViewById<TextView>(it)?.setTextColor(Color.parseColor("#B3FFFFFF"))
            }
        }
    }

    private fun themeInflatedCategoryCards(
        textPrimary: Int,
        textSecondary: Int,
        cardBg: Int,
        cardStroke: Int,
        iconTint: Int
    ) {
        val container = findViewById<LinearLayout>(R.id.popularSkillsLayout) ?: return
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i) as? MaterialCardView ?: continue
            card.setCardBackgroundColor(cardBg)
            card.strokeColor = cardStroke
            card.findViewById<ImageView>(R.id.ivCategoryIcon)?.imageTintList = ColorStateList.valueOf(iconTint)
            card.findViewById<TextView>(R.id.tvCategoryName)?.setTextColor(textPrimary)
            card.findViewById<TextView>(R.id.tvCategoryCount)?.setTextColor(textSecondary)
        }
    }

    private fun themeInflatedSwapCards(
        textPrimary: Int,
        textSecondary: Int,
        cardBg: Int,
        cardStroke: Int,
        iconTint: Int
    ) {
        val container = findViewById<LinearLayout>(R.id.recentSwapsContainer) ?: return
        for (i in 0 until container.childCount) {
            val card = container.getChildAt(i) as? MaterialCardView ?: continue
            card.setCardBackgroundColor(cardBg)
            card.strokeColor = cardStroke
            card.findViewById<ImageView>(R.id.ivSwapPartnerIcon)?.imageTintList = ColorStateList.valueOf(iconTint)
            card.findViewById<TextView>(R.id.tvSwapPartner)?.setTextColor(textPrimary)
            card.findViewById<TextView>(R.id.tvSwapDetail)?.setTextColor(textSecondary)
            card.findViewById<TextView>(R.id.tvSwapStatus)?.setTextColor(textSecondary)
            card.findViewById<TextView>(R.id.tvSwapRating)?.setTextColor(textSecondary)
            card.findViewById<TextView>(R.id.tvSwapTime)?.setTextColor(textSecondary)
        }
    }

    private fun themeBottomNavigation() {
        val selectedCircleBg = ContextCompat.getColor(this, R.color.bottom_nav_selected_circle)
        val selectedIconTint = ContextCompat.getColor(this, R.color.bottom_nav_selected_icon)
        val unselectedIconTint = ContextCompat.getColor(this, R.color.bottom_nav_unselected_icon)

        val currentItem = BottomNavItem.HOME

        val navItems = listOf(
            Pair(R.id.cardHomeIcon, R.id.ivHomeIcon) to BottomNavItem.HOME,
            Pair(R.id.cardExploreIcon, R.id.ivExploreIcon) to BottomNavItem.EXPLORE,
            Pair(R.id.cardAddSkillIcon, R.id.ivAddSkillIcon) to BottomNavItem.ADD_SKILL,
            Pair(R.id.cardTradesIcon, R.id.ivTradesIcon) to BottomNavItem.TRADES,
            Pair(R.id.cardProfileIcon, R.id.ivProfileIcon) to BottomNavItem.PROFILE
        )

        navItems.forEach { (ids, item) ->
            val isSelected = item == currentItem
            findViewById<MaterialCardView>(ids.first)?.setCardBackgroundColor(if (isSelected) selectedCircleBg else Color.TRANSPARENT)
            findViewById<ImageView>(ids.second)?.imageTintList = ColorStateList.valueOf(if (isSelected) selectedIconTint else unselectedIconTint)
        }
    }

    private fun forceRedraw(viewId: Int) {
        val view = findViewById<View>(viewId) ?: return
        view.postInvalidate()
        view.requestLayout()
    }

    private fun applyThemeSystemBars(headerBg: Int, screenBg: Int) {
        window.setBackgroundDrawable(ColorDrawable(screenBg))

        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = !isDarkModeEnabled
    }

    private fun setupQuickActions() {
        findViewById<MaterialButton>(R.id.btnNearby).setOnClickListener {
            showMessage("Show nearby skills")
        }

        findViewById<MaterialButton>(R.id.btnFilter).setOnClickListener {
            showMessage("Open filters")
        }

        findViewById<LinearLayout>(R.id.cardFindSkills).setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardTeachSkills).setOnClickListener {
            startActivity(Intent(this, AddSkillActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardCreateTrade).setOnClickListener {
            startActivity(Intent(this, CreateTradeActivity::class.java))
        }

        findViewById<TextView>(R.id.tvSeeAll).setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }

        findViewById<TextView>(R.id.tvViewAllTrades).setOnClickListener {
            startActivity(Intent(this, MyTradesActivity::class.java))
        }

        // ── Credit Store entry point ──────────────────────────────────────────
        val openCreditStore: (View) -> Unit = {
            startActivity(Intent(this, CreditStoreActivity::class.java))
        }
        findViewById<TextView>(R.id.tvPoints)?.setOnClickListener(openCreditStore)
        findViewById<TextView>(R.id.tvPointsLabel)?.setOnClickListener(openCreditStore)
        findViewById<MaterialCardView>(R.id.cardProfilePoints)?.let { card ->
            card.isClickable = false
        }
    }

    private fun listenForPopularSkillCategories() {
        skillsCategoryListener?.remove()

        skillsCategoryListener = db.collection("skills")
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener

                val counts = skillCategories.associateWith { 0 }.toMutableMap()
                for (doc in snapshot.documents) {
                    val category = doc.getString("category")?.trim()?.uppercase() ?: continue
                    if (counts.containsKey(category)) {
                        counts[category] = counts.getValue(category) + 1
                    }
                }
                renderPopularSkillCategories(counts)
            }
    }

    private fun renderPopularSkillCategories(counts: Map<String, Int>) {
        val container = findViewById<LinearLayout>(R.id.popularSkillsLayout)
        container.removeAllViews()

        val textPrimary = if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary
        val cardBg = if (isDarkModeEnabled) darkCardBg else lightCardBg
        val cardStroke = if (isDarkModeEnabled) darkCardStroke else lightCardStroke

        val inflater = LayoutInflater.from(this)
        for ((index, category) in skillCategories.withIndex()) {
            val card = inflater.inflate(R.layout.item_popular_skill_category, container, false) as MaterialCardView
            val count = counts[category] ?: 0

            card.setCardBackgroundColor(cardBg)
            card.strokeColor = cardStroke

            card.findViewById<ImageView>(R.id.ivCategoryIcon).apply {
                setImageResource(iconForCategory(category))
                imageTintList = ColorStateList.valueOf(textPrimary)
            }
            card.findViewById<TextView>(R.id.tvCategoryName).apply {
                text = formatCategoryName(category)
                setTextColor(textPrimary)
            }
            card.findViewById<TextView>(R.id.tvCategoryCount).apply {
                text = "$count expert${if (count == 1) "" else "s"}"
                setTextColor(textSecondary)
            }

            card.setOnClickListener {
                val exploreIntent = Intent(this, ExploreActivity::class.java)
                exploreIntent.putExtra("categoryFilter", category)
                startActivity(exploreIntent)
            }

            container.addView(card)

            if (index != skillCategories.lastIndex) {
                val spacer = View(this)
                spacer.layoutParams = LinearLayout.LayoutParams(12, LinearLayout.LayoutParams.MATCH_PARENT)
                container.addView(spacer)
            }
        }
    }

    private fun iconForCategory(category: String): Int = when (category) {
        "TECHNOLOGY" -> R.drawable.outline_code_24
        "ARTS" -> R.drawable.outline_design_services_24
        "SPORTS" -> R.drawable.outline_sports_soccer_24
        "HOME" -> R.drawable.outline_home_24
        "EDUCATION" -> R.drawable.outline_school_24
        "LIFESTYLE" -> R.drawable.outline_favorite_24
        else -> R.drawable.outline_search_24
    }

    private fun formatCategoryName(category: String): String =
        category.lowercase().replaceFirstChar { it.uppercase() }

    private fun loadUserData() {
        val userName = sharedPreferences.getString(KEY_USER_NAME, "Guest User") ?: "Guest User"
        val userEmail = sharedPreferences.getString(KEY_USER_EMAIL, "user@example.com") ?: "user@example.com"
        val userLocation = sharedPreferences.getString(KEY_USER_LOCATION, "Location not set") ?: "Location not set"
        val userPoints = sharedPreferences.getInt(KEY_USER_POINTS, 0)
        val userTotalTrades = sharedPreferences.getInt(KEY_USER_TOTAL_TRADES, 0)
        val userRating = sharedPreferences.getFloat(KEY_USER_RATING, 0.0f)
        val userTotalSkills = sharedPreferences.getInt(KEY_USER_TOTAL_SKILLS, 0)
        val userProfileImage = sharedPreferences.getString(KEY_USER_PROFILE_IMAGE, "") ?: ""

        val intentName = intent.getStringExtra("userName")
        val intentEmail = intent.getStringExtra("userEmail")
        val intentLocation = intent.getStringExtra("userLocation")

        if (intentName != null && intentEmail != null) {
            with(sharedPreferences.edit()) {
                putString(KEY_USER_NAME, intentName)
                putString(KEY_USER_EMAIL, intentEmail)
                putString(KEY_USER_LOCATION, intentLocation ?: "New York, USA")
                putInt(KEY_USER_POINTS, 1250)
                putInt(KEY_USER_TOTAL_TRADES, 0)
                putFloat(KEY_USER_RATING, 0.0f)
                putInt(KEY_USER_TOTAL_SKILLS, 0)
                apply()
            }

            updateUI(intentName, intentLocation ?: "New York, USA", 1250)
            updateNavigationHeader(intentName, intentEmail, 1250, 0, 0.0f, 0)
            loadProfileImage(userProfileImage)
            listenForRecentSwaps(intentEmail)
        } else {
            updateUI(userName, userLocation, userPoints)
            updateNavigationHeader(userName, userEmail, userPoints, userTotalTrades, userRating, userTotalSkills)
            loadProfileImage(userProfileImage)
            listenForRecentSwaps(userEmail)
        }

        refreshFromFirestore(userEmail)
    }

    private fun refreshFromFirestore(email: String) {
        if (email.isBlank() || email == "user@example.com") return

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val user = snapshot.documents[0].toObject(Users::class.java) ?: return@addOnSuccessListener

                    with(sharedPreferences.edit()) {
                        putString(KEY_USER_PROFILE_IMAGE, user.profileImage ?: "")
                        putInt(KEY_USER_TOTAL_TRADES, user.completedTrades)
                        putFloat(KEY_USER_RATING, user.rating.toFloat())
                        putInt(KEY_USER_POINTS, user.credits ?: 0)
                        apply()
                    }

                    loadProfileImage(user.profileImage ?: "")
                    findViewById<TextView>(R.id.tvPoints).text = (user.credits ?: 0).toString()
                    updateNavigationHeader(
                        user.name,
                        email,
                        user.credits ?: 0,
                        user.completedTrades,
                        user.rating.toFloat(),
                        sharedPreferences.getInt(KEY_USER_TOTAL_SKILLS, 0)
                    )
                }
            }
    }

    private fun loadProfileImage(url: String) {
        if (url.isNotEmpty()) {
            Glide.with(this)
                .load(url)
                .placeholder(R.drawable.ic_default_profile)
                .error(R.drawable.ic_default_profile)
                .circleCrop()
                .into(ivProfilePicture)
        } else {
            ivProfilePicture.setImageResource(R.drawable.ic_default_profile)
        }

        navigationView.getHeaderView(0)?.findViewById<ImageView>(R.id.imgNavProfile)?.let { navImg ->
            if (url.isNotEmpty()) {
                Glide.with(this)
                    .load(url)
                    .placeholder(R.drawable.ic_default_profile)
                    .error(R.drawable.ic_default_profile)
                    .circleCrop()
                    .into(navImg)
            } else {
                navImg.setImageResource(R.drawable.ic_default_profile)
            }
        }
    }

    private fun updateUI(userName: String, location: String, points: Int) {
        findViewById<TextView>(R.id.tvUserName).text = formatName(userName)
        findViewById<TextView>(R.id.tvPoints).text = points.toString()
    }

    private fun updateNavigationHeader(
        userName: String,
        userEmail: String,
        points: Int,
        totalTrades: Int,
        rating: Float,
        totalSkills: Int
    ) {
        val headerView = navigationView.getHeaderView(0) ?: return
        headerView.findViewById<TextView>(R.id.tvNavUserName)?.text = formatName(userName)
        headerView.findViewById<TextView>(R.id.tvNavUserEmail)?.text = userEmail
        val pointsLabel = getString(R.string.points)
        headerView.findViewById<TextView>(R.id.tvNavUserPoints)?.text = "🪙 $points $pointsLabel"

        headerView.findViewById<TextView>(R.id.nav_trades_value)?.text = totalTrades.toString()
        headerView.findViewById<TextView>(R.id.nav_rating_value)?.text = String.format("%.1f", rating)
        headerView.findViewById<TextView>(R.id.nav_skills_value)?.text = totalSkills.toString()
    }

    private fun updateGreetingMessage() {
        val greetingTextView = findViewById<TextView>(R.id.tvGreeting)
        val currentHour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val greeting = when {
            currentHour < 12 -> getString(R.string.good_morning)
            currentHour < 17 -> getString(R.string.good_afternoon)
            else -> getString(R.string.good_evening)
        }
        greetingTextView.text = greeting
    }

    private fun listenForRecentSwaps(userId: String) {
        requesterSwapsListener?.remove()
        receiverSwapsListener?.remove()
        requesterSwaps = emptyList()
        receiverSwaps = emptyList()
        currentUserIdForSwaps = userId

        if (userId.isBlank() || userId == "user@example.com") {
            renderRecentSwaps(emptyList())
            return
        }

        requesterSwapsListener = db.collection("trades")
            .whereEqualTo("requesterId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                requesterSwaps = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Trade::class.java)?.copy(id = doc.id)
                }
                mergeAndRenderSwaps()
            }

        receiverSwapsListener = db.collection("trades")
            .whereEqualTo("receiverId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) return@addSnapshotListener
                receiverSwaps = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(Trade::class.java)?.copy(id = doc.id)
                }
                mergeAndRenderSwaps()
            }
    }

    private fun mergeAndRenderSwaps() {
        val merged = (requesterSwaps + receiverSwaps)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
            .take(5)
        renderRecentSwaps(merged)
    }

    private fun renderRecentSwaps(swaps: List<Trade>) {
        val container = findViewById<LinearLayout>(R.id.recentSwapsContainer)
        val emptyView = findViewById<TextView>(R.id.tvNoSwaps)
        container.removeAllViews()

        if (swaps.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            container.visibility = View.GONE
            return
        }

        emptyView.visibility = View.GONE
        container.visibility = View.VISIBLE

        val textPrimary = if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary
        val cardBg = if (isDarkModeEnabled) darkCardBg else lightCardBg
        val cardStroke = if (isDarkModeEnabled) darkCardStroke else lightCardStroke

        val inflater = LayoutInflater.from(this)
        for (swap in swaps) {
            val card = inflater.inflate(R.layout.item_recent_swap, container, false) as MaterialCardView

            card.setCardBackgroundColor(cardBg)
            card.strokeColor = cardStroke

            val isCurrentUserRequester = swap.requesterId == currentUserIdForSwaps
            val otherName = if (isCurrentUserRequester) swap.receiverName else swap.requesterName
            val mySkill = if (isCurrentUserRequester) swap.requesterSkill else swap.receiverSkill
            val theirSkill = if (isCurrentUserRequester) swap.receiverSkill else swap.requesterSkill

            card.findViewById<ImageView>(R.id.ivSwapPartnerIcon)?.imageTintList = ColorStateList.valueOf(textSecondary)
            card.findViewById<TextView>(R.id.tvSwapPartner).apply {
                text = otherName.ifBlank { "Unknown user" }
                setTextColor(textPrimary)
            }
            card.findViewById<TextView>(R.id.tvSwapDetail).apply {
                text = "$mySkill ↔ $theirSkill"
                setTextColor(textSecondary)
            }
            card.findViewById<TextView>(R.id.tvSwapStatus).apply {
                text = swap.status.ifBlank { "Pending" }
                setTextColor(textSecondary)
            }
            card.findViewById<TextView>(R.id.tvSwapRating).apply {
                text = if (swap.rating > 0f) "★ ${String.format("%.1f", swap.rating)}" else "—"
                setTextColor(textSecondary)
            }
            card.findViewById<TextView>(R.id.tvSwapTime).apply {
                text = timeAgo(swap.timestamp)
                setTextColor(textSecondary)
            }

            card.setOnClickListener {
                val tradeIntent = Intent(this, MyTradesActivity::class.java)
                tradeIntent.putExtra("tradeId", swap.id)
                startActivity(tradeIntent)
            }

            container.addView(card)

            val spacer = View(this)
            spacer.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 12
            )
            container.addView(spacer)
        }
    }

    private fun timeAgo(timestampMillis: Long): String {
        if (timestampMillis <= 0) return ""
        val diff = System.currentTimeMillis() - timestampMillis
        val minutes = diff / 60000
        return when {
            minutes < 1 -> "just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            else -> "${minutes / 1440}d ago"
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {

        when (item.itemId) {

            R.id.nav_home -> {
                // Already on Home
            }

            R.id.nav_my_skills -> {
                startActivity(Intent(this, MySkillsActivity::class.java))
            }

            R.id.nav_my_trades -> {
                startActivity(Intent(this, MyTradesActivity::class.java))
            }

            R.id.nav_messages -> {
                startActivity(Intent(this, ChatListActivity::class.java))
            }

            R.id.nav_favorites -> {
                // Coming soon - remain on Home
            }

            R.id.nav_points -> {
                // CHANGED: Opens the new dedicated Points & Transactions screen
                startActivity(Intent(this, PointsTransactionsActivity::class.java))
            }

            R.id.nav_history -> {
                // CHANGED: Opens the Swaps-only History screen
                startActivity(Intent(this, HistoryActivity::class.java))
            }

            R.id.nav_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
            }

            R.id.nav_help -> {
                startActivity(Intent(this, HelpSupportActivity::class.java))
            }

            R.id.nav_about -> {
                startActivity(Intent(this, AboutActivity::class.java))
            }

            R.id.nav_logout -> {
                performLogout()
            }
        }

        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home_toolbar_menu, menu)
        menu.removeItem(R.id.action_notifications)

        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.itemId != R.id.action_credits) {
                item?.icon?.setTint(Color.parseColor(THEME_CREAM))
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_credits -> {
                CreditStoreActivity.start(this)
                true
            }
            R.id.action_search -> {
                showMessage("Search")
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun performLogout() {
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
            setTextColor(currentDialogTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.logout_confirmation_message)
            setTextColor(currentDialogTextSecondary())
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

        val btnCancel = pillButton(
            getString(R.string.cancel),
            currentDialogSecondaryBtnBg(),
            currentDialogSecondaryBtnText()
        ).apply {
            setOnClickListener { dialog.dismiss() }
        }

        val btnLogout = pillButton(
            getString(R.string.logout),
            if (isDarkModeEnabled) Color.parseColor("#B3261E") else Color.parseColor("#DC2626"),
            Color.WHITE
        ).apply {
            setOnClickListener {
                dialog.dismiss()
                logoutUser()
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

    private fun currentDialogTextPrimary(): Int = if (isDarkModeEnabled) darkTextPrimary else Color.parseColor(THEME_DARK_NAVY)
    private fun currentDialogTextSecondary(): Int = if (isDarkModeEnabled) darkTextSecondary else Color.parseColor(THEME_STEEL_BLUE)
    private fun currentDialogBg(): Int = if (isDarkModeEnabled) darkCardBg else Color.WHITE
    private fun currentDialogSecondaryBtnBg(): Int = if (isDarkModeEnabled) darkCardStroke else Color.parseColor(THEME_BACKGROUND)
    private fun currentDialogSecondaryBtnText(): Int = if (isDarkModeEnabled) darkTextPrimary else Color.parseColor(THEME_STEEL_BLUE)

    private fun logoutUser() {
        requesterSwapsListener?.remove()
        receiverSwapsListener?.remove()
        skillsCategoryListener?.remove()

        OneSignal.logout()

        FirebaseAuth.getInstance().signOut()
        sharedPreferences.edit().clear().apply()

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
                setColor(currentDialogBg())
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
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun showMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    private fun formatName(name: String): String {
        return name.split(" ").joinToString(" ") { word ->
            if (word.isNotEmpty()) {
                word.substring(0, 1).uppercase() + word.substring(1).lowercase()
            } else {
                word
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        requesterSwapsListener?.remove()
        receiverSwapsListener?.remove()
        skillsCategoryListener?.remove()
    }
}