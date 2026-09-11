package com.example.sgp

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class EditProfileActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore

    /** Tracks current theme so anything built after onCreate stays consistent. */
    private var isDarkModeEnabled = false

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightIconChipNeutralBg = Color.parseColor("#EAE1DA")
    private val lightIconChipNeutralTint = Color.parseColor("#1B3C53")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")

    // Text-input specific (Settings/Profile don't have inputs, so these are
    // new but drawn from the same navy/cream family).
    private val lightInputBg = Color.parseColor("#F9FBFC")
    private val lightInputStroke = Color.parseColor("#1B3C53")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkIconChipNeutralBg = Color.parseColor("#26343F")
    private val darkIconChipNeutralTint = Color.parseColor("#C9D6DE")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")

    private val darkInputBg = Color.parseColor("#1C2B36")
    private val darkInputStroke = Color.parseColor("#3FA7D6")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        db = Firebase.firestore

        val sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        applyTheme(isDarkModeEnabled)

        var currentUserEmail = intent.getStringExtra("email")
        if (currentUserEmail.isNullOrEmpty()) {
            val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
            currentUserEmail = prefs.getString("user_email", "")
        }

        if (currentUserEmail.isNullOrEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeViews(currentUserEmail)
    }

    override fun onResume() {
        super.onResume()
        // Dark mode may have been toggled from the Settings screen while we were away.
        val sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
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
        val iconChipBg = if (isDark) darkIconChipNeutralBg else lightIconChipNeutralBg
        val iconChipTint = if (isDark) darkIconChipNeutralTint else lightIconChipNeutralTint
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg

        // Root + header (header stays dark navy in both themes, just deepens)
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)

        // Intro card
        findViewById<MaterialCardView>(R.id.cardIntro)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.iconBgIntro)?.setCardBackgroundColor(iconChipBg)
        findViewById<ImageView>(R.id.ivIconIntro)?.imageTintList = ColorStateList.valueOf(iconChipTint)
        findViewById<TextView>(R.id.tvIntroTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvIntroSubtitle)?.setTextColor(textSecondary)

        // Section headers
        findViewById<TextView>(R.id.tvSectionPersonalInfo)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvSectionSkills)?.setTextColor(textPrimary)

        // Personal Information + Skills cards
        findViewById<MaterialCardView>(R.id.cardPersonalInfo)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }
        findViewById<MaterialCardView>(R.id.cardSkills)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Text input fields
        listOf(R.id.name, R.id.phone, R.id.location, R.id.skillsTeach, R.id.skillsLearn)
            .forEach { id -> styleTextInputLayout(findViewById(id), isDark) }

        // Status/nav bar contrast
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    /** Recolors an outlined TextInputLayout (box, hint, stroke, and its EditText) for the theme. */
    private fun styleTextInputLayout(layout: TextInputLayout?, isDark: Boolean) {
        if (layout == null) return

        val inputBg = if (isDark) darkInputBg else lightInputBg
        val inputStroke = if (isDark) darkInputStroke else lightInputStroke
        val hintColor = if (isDark) darkTextSecondary else lightTextSecondary
        val textColor = if (isDark) darkTextPrimary else lightTextPrimary

        layout.boxBackgroundColor = inputBg
        layout.boxStrokeColor = inputStroke
        layout.hintTextColor = ColorStateList.valueOf(hintColor)
        layout.defaultHintTextColor = ColorStateList.valueOf(hintColor)
        layout.editText?.setTextColor(textColor)
        layout.editText?.setHintTextColor(hintColor)
    }

    private fun initializeViews(email: String) {
        val btnBack = findViewById<MaterialCardView>(R.id.btnBack)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val nameInput = findViewById<TextInputLayout>(R.id.name)
        val phoneInput = findViewById<TextInputLayout>(R.id.phone)
        val locationInput = findViewById<TextInputLayout>(R.id.location)
        val skillsTeachInput = findViewById<TextInputLayout>(R.id.skillsTeach)
        val skillsLearnInput = findViewById<TextInputLayout>(R.id.skillsLearn)

        loadCurrentData(email, nameInput, phoneInput, locationInput, skillsTeachInput, skillsLearnInput)

        btnBack.setOnClickListener {
            finish()
        }

        // Subtle press-down/release scale animation so tapping Save feels
        // tactile instead of a flat instant color-change.
        btnSave.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    view.animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                }
            }
            false // let the click listener still fire
        }

        btnSave.setOnClickListener {
            val name = nameInput.editText?.text.toString()
            val phone = phoneInput.editText?.text.toString()
            val location = locationInput.editText?.text.toString()
            val skillsTeach = skillsTeachInput.editText?.text.toString()
            val skillsLearn = skillsLearnInput.editText?.text.toString()

            updateProfile(email, name, phone, location, skillsTeach, skillsLearn)
        }
    }

    private fun loadCurrentData(
        email: String,
        nameInput: TextInputLayout,
        phoneInput: TextInputLayout,
        locationInput: TextInputLayout,
        skillsTeachInput: TextInputLayout,
        skillsLearnInput: TextInputLayout
    ) {
        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val user = snapshot.documents[0].toObject(Users::class.java)
                    user?.let {
                        nameInput.editText?.setText(it.name)
                        phoneInput.editText?.setText(it.phone)
                        locationInput.editText?.setText(it.location)
                        skillsTeachInput.editText?.setText(it.skillsTeach)
                        skillsLearnInput.editText?.setText(it.skillsLearn)
                    }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to load data: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateProfile(
        email: String,
        name: String,
        phone: String,
        location: String,
        skillsTeach: String,
        skillsLearn: String
    ) {
        if (name.isEmpty() || phone.isEmpty() || location.isEmpty()) {
            Toast.makeText(this, "Please fill all required fields", Toast.LENGTH_SHORT).show()
            return
        }

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val docId = snapshot.documents[0].id
                    val updates = hashMapOf<String, Any>(
                        "name" to name,
                        "phone" to phone,
                        "location" to location,
                        "skillsTeach" to skillsTeach,
                        "skillsLearn" to skillsLearn
                    )
                    db.collection("users").document(docId).update(updates)
                        .addOnSuccessListener {
                            val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                            prefs.edit().apply {
                                putString("user_name", name)
                                putString("user_location", location)
                                apply()
                            }
                            Toast.makeText(this, "Profile updated successfully!", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, "Failed to update profile: ${e.message}", Toast.LENGTH_SHORT).show()
                        }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "User not found: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}