package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.bumptech.glide.Glide
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Calendar

class Home : BaseActivity(), NavigationView.OnNavigationItemSelectedListener {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView
    private lateinit var bottomNavigationView: BottomNavigationView
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var ivProfilePicture: ImageView
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        sharedPreferences = getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)

        drawerLayout = findViewById(R.id.main)
        navigationView = findViewById(R.id.navigationView)
        bottomNavigationView = findViewById(R.id.bottomNavigation)
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

        navigationView.setNavigationItemSelectedListener(this)
        bottomNavigationView.setOnNavigationItemSelectedListener(bottomNavListener)

        ivProfilePicture.setOnClickListener {
            startActivity(Intent(this, Profile::class.java))
        }

        setupQuickActions()
        setupPopularSkills()
        updateGreetingMessage()
        loadUserData()

        bottomNavigationView.selectedItemId = R.id.nav_home
    }

    private val bottomNavListener = BottomNavigationView.OnNavigationItemSelectedListener { item ->
        when (item.itemId) {
            R.id.nav_home -> true
            R.id.nav_explore -> {
                startActivity(Intent(this, ExploreActivity::class.java))
                true
            }
            R.id.nav_add_skill -> {
                startActivity(Intent(this, AddSkillActivity::class.java))
                true
            }
            R.id.nav_trades -> {
                startActivity(Intent(this, MyTradesActivity::class.java))
                true
            }
            R.id.nav_profile -> {
                val intent = Intent(this, Profile::class.java)
                intent.putExtra("email", sharedPreferences.getString(KEY_USER_EMAIL, ""))
                startActivity(intent)
                true
            }
            else -> false
        }
    }

    private fun setupQuickActions() {
        findViewById<MaterialCardView>(R.id.cardFindSkills).setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardTeachSkills).setOnClickListener {
            startActivity(Intent(this, AddSkillActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardCreateTrade).setOnClickListener {
            startActivity(Intent(this, CreateTradeActivity::class.java))
        }
        findViewById<TextView>(R.id.tvSeeAll).setOnClickListener {
            startActivity(Intent(this, ExploreActivity::class.java))
        }
        findViewById<TextView>(R.id.tvViewAllTrades).setOnClickListener {
            startActivity(Intent(this, MyTradesActivity::class.java))
        }

        findViewById<View>(R.id.btnNearby).setOnClickListener {
            showMessage("Show nearby skills")
        }
        findViewById<View>(R.id.btnFilter).setOnClickListener {
            showMessage("Open filters")
        }
    }

    private fun setupPopularSkills() {
        val popularSkillsLayout = findViewById<ViewGroup>(R.id.popularSkillsLayout)
        for (i in 0 until popularSkillsLayout.childCount) {
            val child = popularSkillsLayout.getChildAt(i)
            child.setOnClickListener {
                when (i) {
                    0 -> showMessage("Programming skills selected")
                    1 -> showMessage("Design skills selected")
                    2 -> showMessage("Music skills selected")
                }
            }
        }
    }

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
        } else {
            updateUI(userName, userLocation, userPoints)
            updateNavigationHeader(userName, userEmail, userPoints, userTotalTrades, userRating, userTotalSkills)
            loadProfileImage(userProfileImage)
        }

        // Refresh from Firestore so a profile picture (or stats) updated on
        // another screen shows up here without waiting for a fresh login.
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
        // Silently ignore failures here — the cached SharedPreferences values
        // (already applied above) are a fine fallback if this fetch fails.
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

        // Also update the nav drawer header image
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
        findViewById<TextView>(R.id.tvUserName).text = capitalizeName(userName)
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
        headerView.findViewById<TextView>(R.id.tvNavUserName)?.text = capitalizeName(userName)
        headerView.findViewById<TextView>(R.id.tvNavUserEmail)?.text = userEmail
        headerView.findViewById<TextView>(R.id.tvNavUserPoints)?.text = "$points Points"

        headerView.findViewById<TextView>(R.id.nav_trades_value)?.text = totalTrades.toString()
        headerView.findViewById<TextView>(R.id.nav_rating_value)?.text = String.format("%.1f", rating)
        headerView.findViewById<TextView>(R.id.nav_skills_value)?.text = totalSkills.toString()
    }

    private fun updateGreetingMessage() {
        val greetingTextView = findViewById<TextView>(R.id.tvGreeting)
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            currentHour < 12 -> "Good Morning,"
            currentHour < 17 -> "Good Afternoon,"
            else -> "Good Evening,"
        }
        greetingTextView.text = greeting
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> { /* already on home */ }
            R.id.nav_my_skills -> showMessage("My Skills")
            R.id.nav_my_trades -> startActivity(Intent(this, MyTradesActivity::class.java))
            R.id.nav_messages -> showMessage("Messages")
            R.id.nav_notifications -> startActivity(Intent(this, NotificationsActivity::class.java))
            R.id.nav_favorites -> showMessage("Favorites")
            R.id.nav_history -> showMessage("History")
            R.id.nav_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            R.id.nav_help -> startActivity(Intent(this, HelpSupportActivity::class.java))   // was showMessage(...)
            R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))        // was showMessage(...)
            R.id.nav_logout -> performLogout()
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.home_toolbar_menu, menu)
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
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                // Sign out from Firebase Authentication
                FirebaseAuth.getInstance().signOut()
                // Clear local SharedPreferences
                sharedPreferences.edit().clear().apply()
                showMessage("Logged out successfully")
                // Navigate to Login
                val intent = Intent(this, Login::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showMessage(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        loadUserData()
    }
}