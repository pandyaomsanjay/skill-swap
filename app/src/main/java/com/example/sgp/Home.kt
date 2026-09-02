package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
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
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

class Home : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var ivProfilePicture: ImageView
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    // Realtime listeners for the current user's swaps — must be removed in onDestroy
    private var requesterSwapsListener: ListenerRegistration? = null
    private var receiverSwapsListener: ListenerRegistration? = null
    private var skillsCategoryListener: ListenerRegistration? = null

    // Buffers so we can merge both queries (Firestore can't OR two different fields)
    private var requesterSwaps: List<Trade> = emptyList()
    private var receiverSwaps: List<Trade> = emptyList()
    private var currentUserIdForSwaps: String = ""

    private val skillCategories = listOf(
        "TECHNOLOGY", "ARTS", "SPORTS", "HOME", "EDUCATION", "LIFESTYLE"
    )

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

        // Theme palette — matches AdminSkillsActivity / the rest of the admin screens
        private const val THEME_DARK_NAVY = "#1B3C53"
        private const val THEME_STEEL_BLUE = "#456882"
        private const val THEME_CREAM = "#F9F3EF"
        private const val THEME_BACKGROUND = "#EAF1F5"
        private const val THEME_STROKE = "#D2C1B6"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        applyThemeSystemBars()

        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

        drawerLayout = findViewById(R.id.main)
        navigationView = findViewById(R.id.navigationView)
        ivProfilePicture = findViewById(R.id.ivProfilePicture)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        // Better padding for status bar
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarInsets = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            view.setPadding(view.paddingLeft, statusBarInsets.top, view.paddingRight, view.paddingBottom)
            insets
        }

        // Custom toggle with better styling and animation support
        val toggle = ActionBarDrawerToggle(
            this,
            drawerLayout,
            toolbar,
            R.string.navigation_drawer_open,
            R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Make the hamburger icon cream-colored on the navy toolbar
        toggle.drawerArrowDrawable.color = Color.parseColor(THEME_CREAM)
        toggle.drawerArrowDrawable.setColorFilter(Color.parseColor(THEME_CREAM), android.graphics.PorterDuff.Mode.SRC_ATOP)

        // Step 6: Add drawer listener for animations and effects (Option 3)
        drawerLayout.addDrawerListener(object : DrawerLayout.DrawerListener {
            override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                // Get the main content view (the LinearLayout inside DrawerLayout)
                val contentView = drawerLayout.getChildAt(0)
                contentView?.apply {
                    translationX = drawerView.width * slideOffset * 0.3f
                    scaleX = 1f - (slideOffset * 0.05f)
                    scaleY = 1f - (slideOffset * 0.05f)
                }
            }

            override fun onDrawerOpened(drawerView: View) {
                // Update UI when drawer opens
                supportActionBar?.title = "Menu"
            }

            override fun onDrawerClosed(drawerView: View) {
                // Update UI when drawer closes
                supportActionBar?.title = "SkillSwap"
            }

            override fun onDrawerStateChanged(newState: Int) {
                // Handle state changes if needed
            }
        })

        navigationView.setNavigationItemSelectedListener(this)

        ivProfilePicture.setOnClickListener {
            startActivity(Intent(this, Profile::class.java))
        }

        setupQuickActions()
        listenForPopularSkillCategories()
        updateGreetingMessage()
        loadUserData()

        BottomNavHelper.setup(this, BottomNavItem.HOME)
    }

    /** Extends the navy toolbar behind the status bar so its color is always correct,
     *  regardless of whether the OS honors window.statusBarColor (ignored on API 35+ edge-to-edge). */
    private fun applyThemeSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)

        window.statusBarColor = Color.parseColor(THEME_DARK_NAVY)
        window.navigationBarColor = Color.parseColor(THEME_BACKGROUND)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        insetsController.isAppearanceLightNavigationBars = true
    }

    private fun setupQuickActions() {
        // Material Buttons
        findViewById<MaterialButton>(R.id.btnNearby).setOnClickListener {
            showMessage("Show nearby skills")
        }

        findViewById<MaterialButton>(R.id.btnFilter).setOnClickListener {
            showMessage("Open filters")
        }

        // Quick action cards
        findViewById<LinearLayout>(R.id.cardFindSkills).setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardTeachSkills).setOnClickListener {
            startActivity(Intent(this, AddSkillActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.cardCreateTrade).setOnClickListener {
            startActivity(Intent(this, CreateTradeActivity::class.java))
        }

        // TextViews
        findViewById<TextView>(R.id.tvSeeAll).setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }

        findViewById<TextView>(R.id.tvViewAllTrades).setOnClickListener {
            startActivity(Intent(this, MyTradesActivity::class.java))
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

        val inflater = LayoutInflater.from(this)
        for ((index, category) in skillCategories.withIndex()) {
            val card = inflater.inflate(R.layout.item_popular_skill_category, container, false)
            val count = counts[category] ?: 0

            card.findViewById<ImageView>(R.id.ivCategoryIcon).setImageResource(iconForCategory(category))
            card.findViewById<TextView>(R.id.tvCategoryName).text = formatCategoryName(category)
            card.findViewById<TextView>(R.id.tvCategoryCount).text =
                "$count expert${if (count == 1) "" else "s"}"

            card.setOnClickListener {
                val exploreIntent = Intent(this, ExploreActivity::class.java)
                exploreIntent.putExtra("categoryFilter", category)
                startActivity(exploreIntent)
            }

            container.addView(card)

            // Spacing between cards (skip after the last one)
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
        headerView.findViewById<TextView>(R.id.tvNavUserPoints)?.text = "$points $pointsLabel"

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

    // ===================== Realtime "Recent swaps" (current user only) =====================

    /**
     * Sets up two realtime Firestore listeners scoped to [userId] — one for trades where
     * this user is the requester, one where they're the receiver — since Firestore can't
     * OR-query two different fields in a single query. Results are merged and re-rendered
     * on every snapshot from either listener.
     */
    private fun listenForRecentSwaps(userId: String) {
        // Tear down any previous listeners (e.g. on user switch / re-login / onResume)
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

        val inflater = LayoutInflater.from(this)
        for (swap in swaps) {
            val card = inflater.inflate(R.layout.item_recent_swap, container, false)

            // Show the *other* participant relative to the signed-in user
            val isCurrentUserRequester = swap.requesterId == currentUserIdForSwaps
            val otherName = if (isCurrentUserRequester) swap.receiverName else swap.requesterName
            val mySkill = if (isCurrentUserRequester) swap.requesterSkill else swap.receiverSkill
            val theirSkill = if (isCurrentUserRequester) swap.receiverSkill else swap.requesterSkill

            card.findViewById<TextView>(R.id.tvSwapPartner).text = otherName.ifBlank { "Unknown user" }
            card.findViewById<TextView>(R.id.tvSwapDetail).text = "$mySkill ↔ $theirSkill"
            card.findViewById<TextView>(R.id.tvSwapStatus).text = swap.status.ifBlank { "Pending" }
            card.findViewById<TextView>(R.id.tvSwapRating).text =
                if (swap.rating > 0f) "★ ${String.format("%.1f", swap.rating)}" else "—"
            card.findViewById<TextView>(R.id.tvSwapTime).text = timeAgo(swap.timestamp)

            card.setOnClickListener {
                val tradeIntent = Intent(this, MyTradesActivity::class.java)
                tradeIntent.putExtra("tradeId", swap.id)
                startActivity(tradeIntent)
            }

            container.addView(card)

            // Small spacer between cards
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

    // ===================== Nav drawer / menu / logout =====================

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> { }
            R.id.nav_my_skills -> showMessage("My Skills")
            R.id.nav_my_trades -> startActivity(Intent(this, MyTradesActivity::class.java))
            R.id.nav_messages -> startActivity(Intent(this, ChatListActivity::class.java))
            R.id.nav_notifications -> startActivity(Intent(this, NotificationsActivity::class.java))
            R.id.nav_favorites -> showMessage("Favorites")
            R.id.nav_history -> showMessage("History")
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_help -> startActivity(Intent(this, HelpSupportActivity::class.java))
            R.id.nav_about -> showMessage("About")
            R.id.nav_logout -> performLogout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home_toolbar_menu, menu)
        // Tint the toolbar's action icons cream so they read clearly on the navy background
        for (i in 0 until menu.size()) {
            menu.getItem(i)?.icon?.setTint(Color.parseColor(THEME_CREAM))
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_notifications -> {
                startActivity(Intent(this, NotificationsActivity::class.java))
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
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_confirmation_title))
            .setMessage(getString(R.string.logout_confirmation_message))
            .setPositiveButton(getString(R.string.logout)) { _, _ ->
                requesterSwapsListener?.remove()
                receiverSwapsListener?.remove()
                skillsCategoryListener?.remove()
                FirebaseAuth.getInstance().signOut()
                sharedPreferences.edit().clear().apply()
                val intent = Intent(this, Login::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
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

    override fun onResume() {
        super.onResume()
        loadUserData()
    }

    override fun onDestroy() {
        super.onDestroy()
        requesterSwapsListener?.remove()
        receiverSwapsListener?.remove()
        skillsCategoryListener?.remove()
    }
}