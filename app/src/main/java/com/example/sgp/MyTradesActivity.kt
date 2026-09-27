package com.example.sgp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.card.MaterialCardView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class MyTradesActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private lateinit var rootLayout: View
    private lateinit var headerBar: View
    private lateinit var btnBack: MaterialCardView
    private lateinit var ivBackIcon: android.widget.ImageView
    private lateinit var tvTradeTitle: android.widget.TextView
    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2

    // Light Theme Palette
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")
    private val lightTabBg = Color.parseColor("#FFFFFF")
    private val lightTabSelectedText = Color.parseColor("#1B3C53")
    private val lightTabUnselectedText = Color.parseColor("#456882")
    private val lightTabIndicator = Color.parseColor("#1B3C53")
    private val headerTitleColor = Color.parseColor("#F9F3EF")

    // Dark Theme Palette
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")
    private val darkTabBg = Color.parseColor("#16232E")
    private val darkTabSelectedText = Color.parseColor("#F5F7F8")
    private val darkTabUnselectedText = Color.parseColor("#8FA6B8")
    private val darkTabIndicator = Color.parseColor("#8FA6B8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_trades)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        bindViews()

        btnBack.setOnClickListener { finish() }

        setupViewPager()

        applyTheme(isDarkModeEnabled)

        BottomNavHelper.setup(this, BottomNavItem.TRADES)
    }

    private fun setupViewPager() {
        viewPager.adapter = TradesPagerAdapter(this, isDarkModeEnabled)

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "Incoming"
                1 -> "Sent"
                2 -> "Active"
                3 -> "Completed"
                4 -> "Closed"
                else -> ""
            }
        }.attach()
    }

    override fun onResume() {
        super.onResume()
        val savedDark = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        if (savedDark != isDarkModeEnabled) {
            applyTheme(savedDark)
            // Recreate adapter to force fragments to update their theme
            setupViewPager()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(isDarkModeEnabled)
        setupViewPager()
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        headerBar = findViewById(R.id.headerBar)
        btnBack = findViewById(R.id.btnBack)
        ivBackIcon = findViewById(R.id.ivBackIcon)
        tvTradeTitle = findViewById(R.id.tvTradeTitle)
        tabLayout = findViewById(R.id.tabLayout)
        viewPager = findViewById(R.id.viewPager)
    }

    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg
        val tabBg = if (isDark) darkTabBg else lightTabBg
        val tabSelectedText = if (isDark) darkTabSelectedText else lightTabSelectedText
        val tabUnselectedText = if (isDark) darkTabUnselectedText else lightTabUnselectedText
        val tabIndicator = if (isDark) darkTabIndicator else lightTabIndicator

        rootLayout.setBackgroundColor(screenBg)
        headerBar.setBackgroundColor(headerBg)
        btnBack.setCardBackgroundColor(backButtonBg)
        ivBackIcon.imageTintList = ColorStateList.valueOf(headerTitleColor)
        tvTradeTitle.setTextColor(headerTitleColor)

        tabLayout.setBackgroundColor(tabBg)
        tabLayout.setSelectedTabIndicatorColor(tabIndicator)
        tabLayout.setTabTextColors(tabUnselectedText, tabSelectedText)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }

    class TradesPagerAdapter(
        activity: FragmentActivity,
        private val isDarkMode: Boolean
    ) : FragmentStateAdapter(activity) {

        override fun getItemCount() = 5

        override fun createFragment(position: Int): Fragment {
            val fragment = when (position) {
                0 -> IncomingTradesFragment()
                1 -> SentTradesFragment()
                2 -> ActiveTradesFragment()
                3 -> CompletedTradesFragment()
                4 -> ClosedTradesFragment()
                else -> throw IndexOutOfBoundsException()
            }
            // Pass the dark mode state to the fragment
            val args = Bundle().apply {
                putBoolean("isDarkMode", isDarkMode)
            }
            fragment.arguments = args
            return fragment
        }
    }
}