package com.example.sgp

import android.content.Context
import android.content.Intent
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

class AboutActivity : BaseActivity() {

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
    private val lightDivider = Color.parseColor("#EAE1DA")
    private val lightBrandDivider = Color.parseColor("#D2C1B6")
    private val lightIconBgNeutral = Color.parseColor("#EAE1DA")
    private val lightIconTintNeutral = Color.parseColor("#1B3C53")
    private val lightChevronTint = Color.parseColor("#456882")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")
    private val lightSectionBar = Color.parseColor("#1B3C53")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkDivider = Color.parseColor("#2A3B48")
    private val darkBrandDivider = Color.parseColor("#2A3B48")
    private val darkIconBgNeutral = Color.parseColor("#26343F")
    private val darkIconTintNeutral = Color.parseColor("#C9D6DE")
    private val darkChevronTint = Color.parseColor("#6E8598")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")
    private val darkSectionBar = Color.parseColor("#456882")

    // Header title / back icon stay cream in both themes (header bg is always dark)
    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        applyTheme(isDarkModeEnabled)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<View>(R.id.layoutAboutPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        findViewById<View>(R.id.layoutAboutTerms).setOnClickListener {
            startActivity(Intent(this, TermsOfServiceActivity::class.java))
        }
        findViewById<View>(R.id.layoutAboutHelp).setOnClickListener {
            startActivity(Intent(this, HelpSupportActivity::class.java))
        }
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
        val divider = if (isDark) darkDivider else lightDivider
        val brandDivider = if (isDark) darkBrandDivider else lightBrandDivider
        val iconBgNeutral = if (isDark) darkIconBgNeutral else lightIconBgNeutral
        val iconTintNeutral = if (isDark) darkIconTintNeutral else lightIconTintNeutral
        val chevronTint = if (isDark) darkChevronTint else lightChevronTint
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg
        val sectionBar = if (isDark) darkSectionBar else lightSectionBar

        // Root + header
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)
        findViewById<ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(headerTitleColor)
        findViewById<TextView>(R.id.tvAboutTitle)?.setTextColor(headerTitleColor)

        // Brand card
        findViewById<MaterialCardView>(R.id.cardBrand)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.cardLogoCircle)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<TextView>(R.id.tvAppName)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvVersion)?.setTextColor(textSecondary)
        findViewById<View>(R.id.dividerBrand)?.setBackgroundColor(brandDivider)
        findViewById<TextView>(R.id.tvTagline)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvDescription)?.setTextColor(textSecondary)

        // Section "More"
        findViewById<View>(R.id.barSectionMore)?.setBackgroundColor(sectionBar)
        findViewById<TextView>(R.id.tvSectionMore)?.setTextColor(textPrimary)

        // Links card
        findViewById<MaterialCardView>(R.id.cardLinks)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Privacy row
        findViewById<MaterialCardView>(R.id.iconBgAboutPrivacy)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconAboutPrivacy)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvAboutPrivacy)?.setTextColor(textPrimary)
        findViewById<ImageView>(R.id.ivChevronAboutPrivacy)?.imageTintList = ColorStateList.valueOf(chevronTint)
        findViewById<View>(R.id.dividerAbout1)?.setBackgroundColor(divider)

        // Terms row
        findViewById<MaterialCardView>(R.id.iconBgAboutTerms)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconAboutTerms)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvAboutTerms)?.setTextColor(textPrimary)
        findViewById<ImageView>(R.id.ivChevronAboutTerms)?.imageTintList = ColorStateList.valueOf(chevronTint)
        findViewById<View>(R.id.dividerAbout2)?.setBackgroundColor(divider)

        // Help row
        findViewById<MaterialCardView>(R.id.iconBgAboutHelp)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconAboutHelp)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvAboutHelp)?.setTextColor(textPrimary)
        findViewById<ImageView>(R.id.ivChevronAboutHelp)?.imageTintList = ColorStateList.valueOf(chevronTint)

        // Footer
        findViewById<TextView>(R.id.tvFooter)?.setTextColor(textSecondary)

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }
}