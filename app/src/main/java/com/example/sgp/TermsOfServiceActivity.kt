package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class TermsOfServiceActivity : BaseActivity() {

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
    private val lightChevronTint = Color.parseColor("#456882")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")
    private val lightSectionBar1 = Color.parseColor("#1B3C53")
    private val lightSectionBar2 = Color.parseColor("#456882")
    private val lightButtonBg = Color.parseColor("#1B3C53")
    private val lightButtonText = Color.parseColor("#F9F3EF")

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
    private val darkChevronTint = Color.parseColor("#6E8598")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")
    private val darkSectionBar1 = Color.parseColor("#456882")
    private val darkSectionBar2 = Color.parseColor("#456882")
    private val darkButtonBg = Color.parseColor("#22323D")
    private val darkButtonText = Color.parseColor("#F5F7F8")

    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_of_service)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        applyTheme(isDarkModeEnabled)

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        findViewById<TextView>(R.id.tvLastUpdated).text = "Last updated: ${getCurrentDate()}"
        findViewById<TextView>(R.id.tvTermsContent).text = buildTermsText()

        findViewById<View>(R.id.layoutEmailSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:skillswap23@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "SkillSwap Terms Question")
            }
            startActivity(Intent.createChooser(intent, "Contact Support"))
        }

        val btnAccept = findViewById<MaterialButton>(R.id.btnAccept)
        btnAccept.setOnClickListener {
            val intent = Intent()
            intent.putExtra("terms_accepted", true)
            setResult(RESULT_OK, intent)
            finish()
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
        val iconBgNeutral = if (isDark) darkIconBgNeutral else lightIconBgNeutral
        val iconTintNeutral = if (isDark) darkIconTintNeutral else lightIconTintNeutral
        val chevronTint = if (isDark) darkChevronTint else lightChevronTint
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg
        val sectionBar1 = if (isDark) darkSectionBar1 else lightSectionBar1
        val sectionBar2 = if (isDark) darkSectionBar2 else lightSectionBar2
        val buttonBg = if (isDark) darkButtonBg else lightButtonBg
        val buttonText = if (isDark) darkButtonText else lightButtonText

        // Root + header
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)
        findViewById<ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(headerTitleColor)
        findViewById<TextView>(R.id.tvTermsTitle)?.setTextColor(headerTitleColor)

        // Intro card
        findViewById<MaterialCardView>(R.id.cardIntro)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgIntro)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconIntro)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvIntroTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvLastUpdated)?.setTextColor(textSecondary)

        // Terms section
        findViewById<View>(R.id.barSectionTerms)?.setBackgroundColor(sectionBar1)
        findViewById<TextView>(R.id.tvSectionTerms)?.setTextColor(textPrimary)
        findViewById<MaterialCardView>(R.id.cardTermsContent)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<TextView>(R.id.tvTermsContent)?.setTextColor(textPrimary)

        // Contact section
        findViewById<View>(R.id.barSectionContact)?.setBackgroundColor(sectionBar2)
        findViewById<TextView>(R.id.tvSectionContact)?.setTextColor(textPrimary)
        findViewById<MaterialCardView>(R.id.cardContact)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgEmail)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconEmail)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvEmailLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvEmailValue)?.setTextColor(textSecondary)
        findViewById<ImageView>(R.id.ivChevronEmail)?.imageTintList = ColorStateList.valueOf(chevronTint)

        // Accept button
        val btnAccept = findViewById<MaterialButton>(R.id.btnAccept)
        btnAccept.backgroundTintList = ColorStateList.valueOf(buttonBg)
        btnAccept.setTextColor(buttonText)

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
    private fun buildTermsText(): String {
        return """
            1. Acceptance
            • By using SkillSwap, you agree to these terms.

            2. Conduct & Content
            • Must be 13+
            • Keep content truthful and legal
            • Fraud or abuse may lead to suspension or termination
            • You own your content but grant SkillSwap a license to display it in-app

            3. Credits & Trades
            • Credits are used to request skills and hold no monetary value
            • SkillSwap doesn't arbitrate disputes between users

            4. Liability & Changes
            • SkillSwap is provided "as is," with no liability for damages from use
            • Terms may be updated; continued use means acceptance
        """.trimIndent()
    }

    private fun getCurrentDate(): String {
        val dateFormat = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }
}