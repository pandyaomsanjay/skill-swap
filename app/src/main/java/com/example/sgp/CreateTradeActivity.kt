package com.example.sgp

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CreateTradeActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private lateinit var currentUserEmail: String
    private lateinit var currentUserName: String

    private lateinit var etYourSkill: TextInputEditText
    private lateinit var etPartnerEmail: TextInputEditText
    private lateinit var etPartnerSkill: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var btnPropose: MaterialButton

    private lateinit var yourSkillLayout: TextInputLayout
    private lateinit var partnerEmailLayout: TextInputLayout
    private lateinit var partnerSkillLayout: TextInputLayout
    private lateinit var messageLayout: TextInputLayout

    private var selectedYourSkill: Skill? = null
    private var selectedPartnerSkill: Skill? = null

    // ── Proposed schedule (millis). Required by Firestore rules: must be > now. ──
    private var selectedScheduleMillis = 0L

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
    private val darkDivider = Color.parseColor("#2A3B48")

    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_create_trade)

        db = Firebase.firestore
        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        // ── Live auth email — must match request.auth.token.email in Firestore rules exactly. ──
        // A cached SharedPreferences copy can silently drift from this and cause
        // PERMISSION_DENIED on trade creation, so we always read it live.
        currentUserEmail = FirebaseAuth.getInstance().currentUser?.email.orEmpty()
        currentUserName = sharedPreferences.getString("user_name", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (currentUserName.isBlank()) currentUserName = currentUserEmail

        initViews()
        applyTheme(isDarkModeEnabled)
        loadYourSkills()
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

        // Root + header
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)
        findViewById<ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(headerTitleColor)
        findViewById<TextView>(R.id.tvTradeTitle)?.setTextColor(headerTitleColor)

        // Intro card
        findViewById<MaterialCardView>(R.id.cardIntro)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgIntro)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconIntro)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvIntroTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvIntroSubtitle)?.setTextColor(textSecondary)

        // Section bar
        findViewById<View>(R.id.barSectionTrade)?.setBackgroundColor(sectionBar)
        findViewById<TextView>(R.id.tvSectionTrade)?.setTextColor(textPrimary)

        // Form card
        findViewById<MaterialCardView>(R.id.cardForm)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Field labels
        findViewById<TextView>(R.id.tvYourSkillLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvPartnerEmailLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvPartnerSkillLabel)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvMessageLabel)?.setTextColor(textPrimary)

        // Text input fields
        styleInput(yourSkillLayout, etYourSkill, cardStroke, textPrimary, textSecondary)
        styleInput(partnerEmailLayout, etPartnerEmail, cardStroke, textPrimary, textSecondary)
        styleInput(partnerSkillLayout, etPartnerSkill, cardStroke, textPrimary, textSecondary)
        styleInput(messageLayout, etMessage, cardStroke, textPrimary, textSecondary)

        // Propose button
        btnPropose.backgroundTintList = ColorStateList.valueOf(buttonBg)
        btnPropose.setTextColor(buttonText)

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    /** Applies theme colors to a TextInputLayout + its EditText. */
    private fun styleInput(
        layout: TextInputLayout,
        editText: TextView,
        strokeColor: Int,
        inputText: Int,
        hintAndIcon: Int
    ) {
        layout.setBoxStrokeColor(strokeColor)
        layout.defaultHintTextColor = ColorStateList.valueOf(hintAndIcon)
        layout.setStartIconTintList(ColorStateList.valueOf(hintAndIcon))
        layout.setEndIconTintList(ColorStateList.valueOf(hintAndIcon))
        editText.setTextColor(inputText)
        editText.setHintTextColor(hintAndIcon)
    }

    private fun initViews() {
        yourSkillLayout = findViewById(R.id.yourSkillLayout)
        partnerEmailLayout = findViewById(R.id.partnerEmailLayout)
        partnerSkillLayout = findViewById(R.id.partnerSkillLayout)
        messageLayout = findViewById(R.id.messageLayout)

        etYourSkill = findViewById(R.id.etYourSkill)
        etPartnerEmail = findViewById(R.id.etPartnerEmail)
        etPartnerSkill = findViewById(R.id.etPartnerSkill)
        etMessage = findViewById(R.id.etMessage)
        btnPropose = findViewById(R.id.btnPropose)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        etYourSkill.setOnClickListener { showSkillPicker("your") }

        etPartnerEmail.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus && etPartnerEmail.text?.isNotEmpty() == true) {
                loadPartnerSkills(etPartnerEmail.text.toString().trim())
            }
        }

        etPartnerSkill.setOnClickListener {
            val partnerEmail = etPartnerEmail.text.toString().trim()
            if (partnerEmail.isEmpty()) {
                partnerEmailLayout.error = "Enter partner's email first"
            } else {
                partnerEmailLayout.error = null
                showSkillPicker("partner", partnerEmail)
            }
        }

        btnPropose.setOnClickListener {
            // Schedule must be picked before we attempt the write — Firestore
            // rules reject any trade whose proposedSchedule isn't a future timestamp.
            if (selectedScheduleMillis <= System.currentTimeMillis()) {
                showDatePicker()
            } else {
                proposeTrade()
            }
        }
    }

    private fun loadYourSkills() {
        db.collection("skills")
            .whereEqualTo("userId", currentUserEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                val skills = mutableListOf<Skill>()
                snapshot.documents.forEach { doc ->
                    doc.toObject(Skill::class.java)?.copy(id = doc.id)?.let { skills.add(it) }
                }
                if (skills.isEmpty()) {
                    etYourSkill.setText("No skills found. Please add a skill first.")
                    etYourSkill.isEnabled = false
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to load skills", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================ Themed skill picker ============================

    private fun showSkillPicker(type: String, partnerEmail: String = "") {
        val query = if (type == "your") {
            db.collection("skills").whereEqualTo("userId", currentUserEmail)
        } else {
            db.collection("skills").whereEqualTo("userId", partnerEmail)
        }

        query.get()
            .addOnSuccessListener { snapshot ->
                val skills = mutableListOf<Skill>()
                snapshot.documents.forEach { doc ->
                    doc.toObject(Skill::class.java)?.copy(id = doc.id)?.let { skills.add(it) }
                }
                if (skills.isEmpty()) {
                    Toast.makeText(
                        this@CreateTradeActivity,
                        "No skills available",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@addOnSuccessListener
                }

                showThemedSkillDialog(type, skills)
            }
            .addOnFailureListener {
                Toast.makeText(
                    this@CreateTradeActivity,
                    "Error loading skills",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun showThemedSkillDialog(type: String, skills: List<Skill>) {
        val root = dialogCard()
        root.addView(
            dialogTitle(
                if (type == "your") "Select your skill" else "Select partner's skill"
            )
        )
        root.addView(
            TextView(this).apply {
                text = "Pick one to include in this trade"
                setTextColor(currentTextSecondary())
                textSize = 13f
                setPadding(0, dp(4), 0, dp(4))
            }
        )
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        skills.forEachIndexed { index, skill ->
            root.addView(skillRow(skill.title) {
                dialog.dismiss()
                if (type == "your") {
                    selectedYourSkill = skill
                    etYourSkill.setText(skill.title)
                    yourSkillLayout.error = null
                } else {
                    selectedPartnerSkill = skill
                    etPartnerSkill.setText(skill.title)
                    partnerSkillLayout.error = null
                }
            })
            if (index != skills.lastIndex) {
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

    private fun skillRow(text: String, onClick: () -> Unit): TextView {
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

    // ============================ End themed dialog helpers ============================

    private fun loadPartnerSkills(email: String) {
        db.collection("skills")
            .whereEqualTo("userId", email)
            .get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Toast.makeText(
                        this@CreateTradeActivity,
                        "Partner has no skills",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking partner skills", Toast.LENGTH_SHORT).show()
            }
    }

    // ============================ Schedule picker ============================
    // Firestore rules require proposedSchedule to be a real future timestamp,
    // so we collect one here before dispatching the trade (same UX pattern as
    // TradeRequestActivity's showDatePicker/showTimePicker).

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        DatePickerDialog(
            this,
            { _, y, m, d ->
                val picked = Calendar.getInstance().apply {
                    set(Calendar.YEAR, y)
                    set(Calendar.MONTH, m)
                    set(Calendar.DAY_OF_MONTH, d)
                }
                showTimePicker(picked)
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).apply {
            datePicker.minDate = System.currentTimeMillis() - 1000
        }.show()
    }

    private fun showTimePicker(dateCal: Calendar) {
        TimePickerDialog(
            this,
            { _, h, min ->
                dateCal.set(Calendar.HOUR_OF_DAY, h)
                dateCal.set(Calendar.MINUTE, min)
                dateCal.set(Calendar.SECOND, 0)
                dateCal.set(Calendar.MILLISECOND, 0)

                if (dateCal.timeInMillis <= System.currentTimeMillis()) {
                    Toast.makeText(this, "Please choose a future time", Toast.LENGTH_SHORT).show()
                    return@TimePickerDialog
                }
                selectedScheduleMillis = dateCal.timeInMillis

                val label = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault())
                    .format(Date(selectedScheduleMillis))
                Toast.makeText(this, "Meeting time set: $label", Toast.LENGTH_SHORT).show()

                // Time is picked — now actually run the proposal.
                proposeTrade()
            },
            dateCal.get(Calendar.HOUR_OF_DAY),
            dateCal.get(Calendar.MINUTE),
            false
        ).show()
    }

    // ============================ Propose ============================

    private fun proposeTrade() {
        val yourSkillObj = selectedYourSkill
        val partnerSkillObj = selectedPartnerSkill
        val partnerEmail = etPartnerEmail.text.toString().trim()
        val message = etMessage.text.toString().trim()

        if (yourSkillObj == null) {
            yourSkillLayout.error = "Select the skill you're offering"
            return
        }
        if (partnerEmail.isEmpty()) {
            partnerEmailLayout.error = "Enter partner's email"
            return
        }
        if (partnerSkillObj == null) {
            partnerSkillLayout.error = "Select the skill you want"
            return
        }
        if (selectedScheduleMillis <= System.currentTimeMillis()) {
            Toast.makeText(this, "Please pick a future meeting time", Toast.LENGTH_SHORT).show()
            return
        }

        partnerEmailLayout.error = null
        btnPropose.isEnabled = false

        db.collection("users").whereEqualTo("email", partnerEmail).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    btnPropose.isEnabled = true
                    partnerEmailLayout.error = "Partner not registered"
                    return@addOnSuccessListener
                }
                val partnerDoc = snapshot.documents[0]
                val partner = partnerDoc.toObject(Users::class.java)
                if (partner == null) {
                    btnPropose.isEnabled = true
                    partnerEmailLayout.error = "Partner not registered"
                    return@addOnSuccessListener
                }

                // ── Build the trade with the fields the Firestore rules actually require ──
                val trade = Trade(
                    requesterId = currentUserEmail,
                    requesterName = currentUserName,
                    receiverId = partnerEmail,
                    receiverName = partner.name,

                    // These three were previously left at their empty/zero
                    // defaults, which is exactly why every create was rejected
                    // by isValidTradeCreate() in firestore.rules.
                    requestedResourceId = partnerSkillObj.id,
                    requestedResourceType = partnerSkillObj.resourceTypeEnum.name,
                    requestedResourceTitle = partnerSkillObj.title,

                    offeredResourceId = yourSkillObj.id,
                    offeredResourceType = yourSkillObj.resourceTypeEnum.name,
                    offeredResourceTitle = yourSkillObj.title,

                    proposedSchedule = selectedScheduleMillis,
                    message = message,
                    status = TradeStatus.PENDING.value
                )

                // Route through TradeManager so we get the same duplicate-request
                // guard, availability check, and notification-on-create that
                // TradeRequestActivity already relies on, instead of a raw db.set().
                lifecycleScope.launch {
                    val result = TradeManager.createTrade(trade)
                    btnPropose.isEnabled = true

                    result.fold(
                        onSuccess = {
                            Toast.makeText(
                                this@CreateTradeActivity,
                                "Trade proposed!",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        },
                        onFailure = { e ->
                            android.util.Log.e("CreateTradeActivity", "Trade create failed", e)
                            Toast.makeText(
                                this@CreateTradeActivity,
                                e.message ?: "Failed to propose trade",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
            .addOnFailureListener {
                btnPropose.isEnabled = true
                Toast.makeText(this, "Error finding partner", Toast.LENGTH_SHORT).show()
            }
    }
}