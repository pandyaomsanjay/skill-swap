package com.example.sgp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.card.MaterialCardView

class PrivacyPolicyActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightIconBgNeutral = Color.parseColor("#EAE1DA")
    private val lightIconTintNeutral = Color.parseColor("#1B3C53")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")
    private val lightSectionBar1 = Color.parseColor("#1B3C53")
    private val lightSectionBar2 = Color.parseColor("#456882")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkIconBgNeutral = Color.parseColor("#26343F")
    private val darkIconTintNeutral = Color.parseColor("#C9D6DE")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")
    private val darkSectionBar1 = Color.parseColor("#456882")
    private val darkSectionBar2 = Color.parseColor("#456882")

    // Header title / back icon stay cream in both themes
    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        applyTheme(isDarkModeEnabled)

        findViewById<MaterialCardView>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvLastUpdated).text = "Last updated: ${getCurrentDate()}"
        findViewById<TextView>(R.id.tvPrivacyContent).text = buildPrivacyPolicyText()
    }

    override fun onResume() {
        super.onResume()
        val savedDark = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        if (savedDark != isDarkModeEnabled) applyTheme(savedDark)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(isDarkModeEnabled)
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
        val iconBgNeutral = if (isDark) darkIconBgNeutral else lightIconBgNeutral
        val iconTintNeutral = if (isDark) darkIconTintNeutral else lightIconTintNeutral
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg
        val sectionBar1 = if (isDark) darkSectionBar1 else lightSectionBar1
        val sectionBar2 = if (isDark) darkSectionBar2 else lightSectionBar2

        // Root + header
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)
        findViewById<ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(headerTitleColor)
        findViewById<TextView>(R.id.tvPrivacyTitle)?.setTextColor(headerTitleColor)

        // Intro card
        findViewById<MaterialCardView>(R.id.cardIntro)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgIntro)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconIntro)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvIntroTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvLastUpdated)?.setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvIntroSubtitle)?.setTextColor(textSecondary)

        // Policy section
        findViewById<View>(R.id.barSectionPolicy)?.setBackgroundColor(sectionBar1)
        findViewById<TextView>(R.id.tvSectionPolicy)?.setTextColor(textPrimary)
        findViewById<MaterialCardView>(R.id.cardPolicyContent)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<TextView>(R.id.tvPrivacyContent)?.setTextColor(textPrimary)

        // Contact section
        findViewById<View>(R.id.barSectionContact)?.setBackgroundColor(sectionBar2)
        findViewById<TextView>(R.id.tvSectionContact)?.setTextColor(textPrimary)
        findViewById<MaterialCardView>(R.id.cardContact)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgContact)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconContact)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvContactEmail)?.setTextColor(textPrimary)

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    // ---------------------------------------------------------------------
    // CONTENT
    // ---------------------------------------------------------------------
    private fun buildPrivacyPolicyText(): String {
        return """
        Privacy Policy for SkillSwap

        1. Information We Collect
        • Name, email, location, profile picture
        • The skills you offer or request

        2. How We Use It
        • To match skill trades
        • To calculate credits and ratings
        • To send trade-related notifications

        3. Data Sharing & Security
        • We never sell your data
        • Only your name, skills, and rating are visible to other users
        • Data is stored securely with Firebase

        4. Your Rights
        • Edit or delete your profile anytime
        • Contact us to delete your account entirely
    """.trimIndent()
    }

    private fun getCurrentDate(): String {
        val dateFormat = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }
}