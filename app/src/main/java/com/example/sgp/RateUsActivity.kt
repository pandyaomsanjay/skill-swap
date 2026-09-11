package com.example.sgp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class RateUsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingLabel: TextView
    private lateinit var btnRateNow: MaterialButton
    private lateinit var tvRemindLater: TextView

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

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
    private val lightSectionBar = Color.parseColor("#1B3C53")
    private val lightButtonBg = Color.parseColor("#1B3C53")
    private val lightButtonText = Color.parseColor("#F9F3EF")
    private val lightRatingFill = Color.parseColor("#456882")
    private val lightRatingEmpty = Color.parseColor("#D2C1B6")

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
    private val darkSectionBar = Color.parseColor("#456882")
    private val darkButtonBg = Color.parseColor("#22323D")
    private val darkButtonText = Color.parseColor("#F5F7F8")
    private val darkRatingFill = Color.parseColor("#E8C468")
    private val darkRatingEmpty = Color.parseColor("#2A3B48")

    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_us)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        ratingBar = findViewById(R.id.ratingBar)
        tvRatingLabel = findViewById(R.id.tvRatingLabel)
        btnRateNow = findViewById(R.id.btnRateNow)
        tvRemindLater = findViewById(R.id.tvRemindLater)

        applyTheme(isDarkModeEnabled)

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvRatingLabel.text = ratingLabel(rating)
        }

        btnRateNow.setOnClickListener {
            submitRating(ratingBar.rating)
        }

        tvRemindLater.setOnClickListener {
            recordRemindLater()
            Toast.makeText(this, "We'll remind you later", Toast.LENGTH_SHORT).show()
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
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg
        val sectionBar = if (isDark) darkSectionBar else lightSectionBar
        val buttonBg = if (isDark) darkButtonBg else lightButtonBg
        val buttonText = if (isDark) darkButtonText else lightButtonText
        val ratingFill = if (isDark) darkRatingFill else lightRatingFill
        val ratingEmpty = if (isDark) darkRatingEmpty else lightRatingEmpty

        // Root + header
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)
        findViewById<ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(headerTitleColor)
        findViewById<TextView>(R.id.tvRateUsTitle)?.setTextColor(headerTitleColor)

        // Intro card
        findViewById<MaterialCardView>(R.id.cardIntro)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgIntro)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconIntro)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvIntroTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvIntroSubtitle)?.setTextColor(textSecondary)

        // Section bar + title
        findViewById<View>(R.id.barSectionRating)?.setBackgroundColor(sectionBar)
        findViewById<TextView>(R.id.tvSectionRating)?.setTextColor(textPrimary)

        // Rating card
        findViewById<MaterialCardView>(R.id.cardRating)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Rating bar tints
        ratingBar.progressTintList = ColorStateList.valueOf(ratingFill)
        ratingBar.secondaryProgressTintList = ColorStateList.valueOf(ratingEmpty)

        tvRatingLabel.setTextColor(textPrimary)

        // Button
        btnRateNow.backgroundTintList = ColorStateList.valueOf(buttonBg)
        btnRateNow.setTextColor(buttonText)
        btnRateNow.iconTint = ColorStateList.valueOf(buttonText)

        // Remind later link
        tvRemindLater.setTextColor(textSecondary)

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    private fun ratingLabel(rating: Float): String = when (rating.toInt()) {
        5 -> "Excellent"
        4 -> "Good"
        3 -> "Okay"
        2 -> "Needs work"
        else -> "Poor"
    }

    private fun submitRating(rating: Float) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please log in to submit a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "rating" to rating,
            "ratedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RateUs", "Firestore write failed", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun recordRemindLater() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .set(
                hashMapOf("remindRatingLaterAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
    }
}