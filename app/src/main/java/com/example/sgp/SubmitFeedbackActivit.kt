package com.example.sgp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class SubmitFeedbackActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private lateinit var etTitle: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var tilTitle: TextInputLayout
    private lateinit var tilMessage: TextInputLayout
    private lateinit var tilCategory: TextInputLayout
    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingLabel: TextView
    private lateinit var actvCategory: AutoCompleteTextView

    private var currentUserEmail: String = ""
    private var currentUserName: String = ""
    private var currentUserPhotoUrl: String = ""

    private val submittableCategories = listOf(
        FeedbackCategory.SUGGESTION,
        FeedbackCategory.BUG_REPORT,
        FeedbackCategory.COMPLAINT,
        FeedbackCategory.FEATURE_REQUEST
    )

    private var selectedCategory: FeedbackCategory = FeedbackCategory.SUGGESTION

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
    private val lightRatingFill = Color.parseColor("#1B3C53")
    private val lightRatingEmpty = Color.parseColor("#D2C1B6")
    private val lightDivider = Color.parseColor("#EAF1F5")

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
    private val darkDivider = Color.parseColor("#2A3B48")

    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_feedback)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        db = Firebase.firestore

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        etTitle = findViewById(R.id.etTitle)
        etMessage = findViewById(R.id.etMessage)
        tilTitle = findViewById(R.id.tilTitle)
        tilMessage = findViewById(R.id.tilMessage)
        tilCategory = findViewById(R.id.tilCategory)
        ratingBar = findViewById(R.id.ratingBar)
        tvRatingLabel = findViewById(R.id.tvRatingLabel)
        actvCategory = findViewById(R.id.actvCategory)

        applyTheme(isDarkModeEnabled)

        tvRatingLabel.text = ratingLabel(ratingBar.rating)
        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvRatingLabel.text = ratingLabel(rating)
        }

        setupCategoryPicker()
        loadCurrentUser()

        findViewById<MaterialButton>(R.id.btnSubmitFeedback).setOnClickListener {
            submitFeedback()
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
        findViewById<android.widget.ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(headerTitleColor)
        findViewById<TextView>(R.id.tvFeedbackTitle)?.setTextColor(headerTitleColor)

        // Intro card
        findViewById<MaterialCardView>(R.id.cardIntro)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgIntro)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<android.widget.ImageView>(R.id.ivIconIntro)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvIntroTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvIntroSubtitle)?.setTextColor(textSecondary)

        // Section bar
        findViewById<View>(R.id.barSectionForm)?.setBackgroundColor(sectionBar)
        findViewById<TextView>(R.id.tvSectionForm)?.setTextColor(textPrimary)

        // Form card
        findViewById<MaterialCardView>(R.id.cardForm)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Section labels
        findViewById<TextView>(R.id.tvCategoryLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvTitleLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvMessageLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvRatingSectionLabel)?.setTextColor(textPrimary)

        // Text input fields
        styleInput(tilCategory, actvCategory, cardStroke, textPrimary, textSecondary, textSecondary)
        styleInput(tilTitle, etTitle, cardStroke, textPrimary, textSecondary, textSecondary)
        styleInput(tilMessage, etMessage, cardStroke, textPrimary, textSecondary, textSecondary)

        // Rating bar
        ratingBar.progressTintList = ColorStateList.valueOf(ratingFill)
        ratingBar.secondaryProgressTintList = ColorStateList.valueOf(ratingEmpty)
        tvRatingLabel.setTextColor(textPrimary)

        // Submit button
        findViewById<MaterialButton>(R.id.btnSubmitFeedback)?.apply {
            backgroundTintList = ColorStateList.valueOf(buttonBg)
            setTextColor(buttonText)
        }

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    /** Applies themed colors to a TextInputLayout + its inner TextView. */
    private fun styleInput(
        layout: TextInputLayout,
        editText: TextView,
        strokeColor: Int,
        inputText: Int,
        hintAndIcon: Int,
        hintColor: Int
    ) {
        layout.setBoxStrokeColor(strokeColor)
        layout.defaultHintTextColor = ColorStateList.valueOf(hintColor)
        layout.setEndIconTintList(ColorStateList.valueOf(hintAndIcon))
        editText.setTextColor(inputText)
        editText.setHintTextColor(hintColor)
    }

    private fun ratingLabel(rating: Float): String = when (rating.toInt()) {
        5 -> "Excellent"
        4 -> "Good"
        3 -> "Okay"
        2 -> "Needs work"
        else -> "Poor"
    }

    // ====================== Themed category picker ======================

    private fun setupCategoryPicker() {
        selectedCategory = submittableCategories.first()
        actvCategory.setText(selectedCategory.label, false)
        actvCategory.setOnClickListener { showCategoryDialog() }
    }

    private fun showCategoryDialog() {
        val root = dialogCard()
        root.addView(dialogTitle("Select a category"))
        root.addView(TextView(this).apply {
            text = "Pick the option that best fits your feedback"
            setTextColor(currentTextSecondary())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        submittableCategories.forEachIndexed { index, category ->
            root.addView(optionRow(category.label) {
                dialog.dismiss()
                selectedCategory = category
                actvCategory.setText(category.label, false)
            })
            if (index != submittableCategories.lastIndex) {
                root.addView(dividerLine())
            }
        }

        val btnCancel = pillButton(
            "Cancel",
            currentSecondaryBtnBg(),
            currentTextSecondary()
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
                gravity = Gravity.END
            }
            setPadding(dp(28), dp(10), dp(28), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnCancel)

        dialog.show()
    }

    // ---- Themed dialog helpers ----

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun currentDialogBg(): Int = if (isDarkModeEnabled) darkCardBg else Color.WHITE
    private fun currentTextPrimary(): Int = if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary
    private fun currentTextSecondary(): Int = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary
    private fun currentSecondaryBtnBg(): Int = if (isDarkModeEnabled) darkCardStroke else lightScreenBg
    private fun currentDividerColor(): Int = if (isDarkModeEnabled) darkDivider else lightDivider

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

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun dividerLine(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(currentDividerColor())
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

    private fun optionRow(text: String, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
            textSize = 15f
            setPadding(dp(4), dp(12), dp(4), dp(12))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    // ====================== End themed helpers ======================

    private fun loadCurrentUser() {
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        currentUserEmail = prefs.getString("user_email", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").whereEqualTo("email", currentUserEmail).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val user = snapshot.documents[0].toObject(Users::class.java)
                    currentUserName = user?.name ?: currentUserEmail
                    currentUserPhotoUrl = user?.profileImage ?: ""
                } else {
                    currentUserName = currentUserEmail
                }
            }
            .addOnFailureListener {
                currentUserName = currentUserEmail
            }
    }

    private fun submitFeedback() {
        val title = etTitle.text?.toString()?.trim().orEmpty()
        val message = etMessage.text?.toString()?.trim().orEmpty()
        val rating = ratingBar.rating.toInt()

        tilTitle.error = null
        tilMessage.error = null

        if (title.isEmpty()) {
            tilTitle.error = "Please add a short title"
            return
        }
        if (message.isEmpty()) {
            tilMessage.error = "Please tell us more"
            return
        }
        if (currentUserEmail.isEmpty()) {
            Toast.makeText(
                this,
                "You need to be logged in to send feedback",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val feedbackData = hashMapOf(
            "userId" to currentUserEmail,
            "userName" to currentUserName.ifBlank { currentUserEmail },
            "userPhotoUrl" to currentUserPhotoUrl,
            "title" to title,
            "message" to message,
            "rating" to rating,
            "category" to selectedCategory.firestoreValue,
            "status" to FeedbackStatus.NEW.firestoreValue,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("feedback").add(feedbackData)
            .addOnSuccessListener {
                Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}