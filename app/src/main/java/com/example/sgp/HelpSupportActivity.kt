package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class HelpSupportActivity : BaseActivity() {

    private data class Faq(val question: String, val answer: String)

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private val db = FirebaseFirestore.getInstance()

    private val faqs = listOf(
        Faq(
            "How do I start a trade?",
            "• Go to Explore and pick a skill you're interested in\n" +
                    "• Open the user's profile\n" +
                    "• Tap \"Request Trade\"\n" +
                    "• They'll get a notification to accept or decline"
        ),
        Faq(
            "What are credits and how do I earn them?",
            "• Credits are SkillSwap's internal currency for skill exchanges\n" +
                    "• You earn credits by teaching or offering a skill to another user\n" +
                    "• They have no monetary value"
        ),
        Faq(
            "Is SkillSwap free to use?",
            "• Yes, SkillSwap is completely free\n" +
                    "• There are no subscription fees\n" +
                    "• The app runs entirely on the credit-based trade system"
        ),
        Faq(
            "How do I report a user?",
            "• Open the user's profile\n" +
                    "• Tap the three-dot menu in the top right\n" +
                    "• Select \"Report User\"\n" +
                    "• Our team reviews every report within 48 hours"
        ),
        Faq(
            "How do I delete my account?",
            "• Go to Settings > Edit Profile > Delete Account\n" +
                    "• Or email us directly\n" +
                    "• We'll remove your data within 7 days"
        ),
        Faq(
            "How do I cancel or decline a trade request?",
            "• Open Notifications or your Trades tab\n" +
                    "• Find the pending trade request\n" +
                    "• Tap \"Decline\" to reject it, or \"Cancel\" if you sent it\n" +
                    "• The other user will be notified automatically"
        ),
        Faq(
            "I forgot my password. How do I reset it?",
            "• Tap \"Forgot Password?\" on the login screen\n" +
                    "• Enter the email linked to your account\n" +
                    "• Check your inbox for a reset link\n" +
                    "• Follow the link to set a new password"
        )
    )

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
    private val lightDivider = Color.parseColor("#EAE1DA")
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
    private val darkDivider = Color.parseColor("#2A3B48")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")
    private val darkSectionBar1 = Color.parseColor("#456882")
    private val darkSectionBar2 = Color.parseColor("#456882")
    private val darkButtonBg = Color.parseColor("#22323D")
    private val darkButtonText = Color.parseColor("#F5F7F8")

    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_help_support)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        applyTheme(isDarkModeEnabled)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        buildFaqList()
        setupAskQuestionBox()

        findViewById<View>(R.id.layoutEmailSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:skillswap23@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "SkillSwap Support Request")
            }
            startActivity(Intent.createChooser(intent, "Contact Support"))
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
        val divider = if (isDark) darkDivider else lightDivider
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
        findViewById<TextView>(R.id.tvHelpTitle)?.setTextColor(headerTitleColor)

        // Intro card
        findViewById<MaterialCardView>(R.id.cardIntro)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgIntro)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconIntro)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvIntroTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvIntroSubtitle)?.setTextColor(textSecondary)

        // FAQ section
        findViewById<View>(R.id.barSectionFaq)?.setBackgroundColor(sectionBar1)
        findViewById<TextView>(R.id.tvSectionFaq)?.setTextColor(textPrimary)
        findViewById<MaterialCardView>(R.id.cardFaq)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Re-theme already-inflated FAQ rows
        themeInflatedFaqRows(textPrimary, textSecondary, iconBgNeutral, iconTintNeutral, chevronTint, divider)

        // Ask section
        findViewById<View>(R.id.barSectionAsk)?.setBackgroundColor(sectionBar2)
        findViewById<TextView>(R.id.tvSectionAsk)?.setTextColor(textPrimary)
        findViewById<MaterialCardView>(R.id.cardAsk)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<TextView>(R.id.tvAskSubtitle)?.setTextColor(textSecondary)

        val tilQuestion = findViewById<TextInputLayout>(R.id.tilQuestion)
        val etQuestion = findViewById<TextInputEditText>(R.id.etUserQuestion)
        tilQuestion?.setBoxStrokeColor(cardStroke)
        tilQuestion?.defaultHintTextColor = ColorStateList.valueOf(textSecondary)
        etQuestion?.setTextColor(textPrimary)
        etQuestion?.setHintTextColor(textSecondary)

        findViewById<MaterialButton>(R.id.btnSubmitQuestion)?.apply {
            backgroundTintList = ColorStateList.valueOf(buttonBg)
            setTextColor(buttonText)
        }

        // Contact section
        findViewById<View>(R.id.barSectionContact)?.setBackgroundColor(sectionBar2)
        findViewById<TextView>(R.id.tvSectionContact)?.setTextColor(textPrimary)
        findViewById<MaterialCardView>(R.id.cardContact)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgContact)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconContact)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvContactLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvContactValue)?.setTextColor(textSecondary)
        findViewById<ImageView>(R.id.ivChevronContact)?.imageTintList = ColorStateList.valueOf(chevronTint)

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    /** Recolors already-inflated FAQ rows so toggling theme live re-skins them. */
    private fun themeInflatedFaqRows(
        textPrimary: Int,
        textSecondary: Int,
        iconBg: Int,
        iconTint: Int,
        chevronTint: Int,
        dividerColor: Int
    ) {
        val container = findViewById<LinearLayout>(R.id.layoutFaqContainer) ?: return
        for (i in 0 until container.childCount) {
            val row = container.getChildAt(i)
            row.findViewById<MaterialCardView>(R.id.iconBgFaq)?.setCardBackgroundColor(iconBg)
            row.findViewById<ImageView>(R.id.ivFaqIcon)?.imageTintList = ColorStateList.valueOf(iconTint)
            row.findViewById<TextView>(R.id.tvQuestion)?.setTextColor(textPrimary)
            row.findViewById<TextView>(R.id.tvAnswer)?.setTextColor(textSecondary)
            row.findViewById<ImageView>(R.id.ivChevron)?.imageTintList = ColorStateList.valueOf(chevronTint)
            row.findViewById<View>(R.id.dividerFaqItem)?.setBackgroundColor(dividerColor)

            // Hide the divider on the last row — cleaner bottom edge
            val isLast = (i == container.childCount - 1)
            row.findViewById<View>(R.id.dividerFaqItem)?.visibility =
                if (isLast) View.GONE else View.VISIBLE
        }
    }

    // ---------------------------------------------------------------------
    // FAQ list
    // ---------------------------------------------------------------------
    private fun buildFaqList() {
        val container = findViewById<LinearLayout>(R.id.layoutFaqContainer)
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        val textPrimary = if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary
        val iconBg = if (isDarkModeEnabled) darkIconBgNeutral else lightIconBgNeutral
        val iconTint = if (isDarkModeEnabled) darkIconTintNeutral else lightIconTintNeutral
        val chevronTint = if (isDarkModeEnabled) darkChevronTint else lightChevronTint
        val dividerColor = if (isDarkModeEnabled) darkDivider else lightDivider

        faqs.forEachIndexed { index, faq ->
            val row = inflater.inflate(R.layout.item_faq, container, false)

            val tvQuestion = row.findViewById<TextView>(R.id.tvQuestion)
            val tvAnswer = row.findViewById<TextView>(R.id.tvAnswer)
            val ivChevron = row.findViewById<ImageView>(R.id.ivChevron)
            val header = row.findViewById<View>(R.id.layoutQuestion)
            val divider = row.findViewById<View>(R.id.dividerFaqItem)

            tvQuestion.text = faq.question
            tvQuestion.setTextColor(textPrimary)

            tvAnswer.text = faq.answer
            tvAnswer.setTextColor(textSecondary)

            row.findViewById<MaterialCardView>(R.id.iconBgFaq)?.setCardBackgroundColor(iconBg)
            row.findViewById<ImageView>(R.id.ivFaqIcon)?.imageTintList = ColorStateList.valueOf(iconTint)
            ivChevron.imageTintList = ColorStateList.valueOf(chevronTint)
            divider.setBackgroundColor(dividerColor)

            // Hide the divider on the last row
            divider.visibility = if (index == faqs.lastIndex) View.GONE else View.VISIBLE

            header.setOnClickListener {
                val expanded = tvAnswer.visibility == View.VISIBLE
                tvAnswer.visibility = if (expanded) View.GONE else View.VISIBLE
                ivChevron.rotation = if (expanded) 270f else 90f
            }

            container.addView(row)
        }
    }

    // ---------------------------------------------------------------------
    // Ask a question
    // ---------------------------------------------------------------------
    private fun setupAskQuestionBox() {
        val etQuestion = findViewById<EditText>(R.id.etUserQuestion)
        val btnSubmit = findViewById<Button>(R.id.btnSubmitQuestion)

        btnSubmit.setOnClickListener {
            val questionText = etQuestion.text.toString().trim()

            if (questionText.isEmpty()) {
                Toast.makeText(this, "Please type your question first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val currentUser = FirebaseAuth.getInstance().currentUser
            if (currentUser == null) {
                Toast.makeText(this, "Please log in to submit a question", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val questionMap = hashMapOf<String, Any>(
                "question" to questionText,
                "status" to "pending",
                "createdAt" to System.currentTimeMillis()
            )

            btnSubmit.isEnabled = false

            db.collection("users").document(currentUser.uid)
                .update("supportQuestions", FieldValue.arrayUnion(questionMap))
                .addOnSuccessListener {
                    btnSubmit.isEnabled = true
                    etQuestion.text.clear()
                    Toast.makeText(this, "Your question has been submitted!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    btnSubmit.isEnabled = true
                    Toast.makeText(this, "Failed to submit: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }
}