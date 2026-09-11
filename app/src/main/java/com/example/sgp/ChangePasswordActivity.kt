package com.example.sgp

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.sgp.api.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ChangePasswordActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var tilCurrentPassword: TextInputLayout
    private lateinit var tilNewPassword: TextInputLayout
    private lateinit var tilConfirmPassword: TextInputLayout
    private lateinit var btnUpdatePassword: MaterialButton
    private lateinit var progressBar: ProgressBar

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightFieldStroke = Color.parseColor("#D2C1B6")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")
    private val lightButtonBg = Color.parseColor("#1B3C53")
    private val lightButtonText = Color.parseColor("#F9F3EF")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkFieldStroke = Color.parseColor("#2A3B48")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")
    private val darkButtonBg = Color.parseColor("#22323D")
    private val darkButtonText = Color.parseColor("#F5F7F8")

    // Header stays cream in both themes (header bg is always dark navy)
    private val headerTitleColor = Color.parseColor("#F9F3EF")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        bindViews()
        applyTheme(isDarkModeEnabled)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }
        btnUpdatePassword.setOnClickListener { changePassword() }
    }

    override fun onResume() {
        super.onResume()
        // Re-apply theme in case the user toggled dark mode while away
        val savedDarkMode = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        if (savedDarkMode != isDarkModeEnabled) {
            applyTheme(savedDarkMode)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(isDarkModeEnabled)
    }

    private fun bindViews() {
        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        tilCurrentPassword = findViewById(R.id.tilCurrentPassword)
        tilNewPassword = findViewById(R.id.tilNewPassword)
        tilConfirmPassword = findViewById(R.id.tilConfirmPassword)
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword)
        progressBar = findViewById(R.id.progressBar)
    }

    // ---------------------------------------------------------------------
    // THEME ENGINE
    // ---------------------------------------------------------------------

    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val fieldStroke = if (isDark) darkFieldStroke else lightFieldStroke
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg
        val buttonBg = if (isDark) darkButtonBg else lightButtonBg
        val buttonText = if (isDark) darkButtonText else lightButtonText

        // Root background
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)

        // Header
        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)
        findViewById<android.widget.ImageView>(R.id.ivBackIcon)
            ?.imageTintList = ColorStateList.valueOf(headerTitleColor)
        findViewById<android.widget.TextView>(R.id.tvTitle)?.apply {
            setTextColor(headerTitleColor)
            visibility = View.VISIBLE
            alpha = 1f
        }

        // Text fields — outline, hint, text, end icon
        styleField(tilCurrentPassword, etCurrentPassword, fieldStroke, textPrimary, textSecondary)
        styleField(tilNewPassword, etNewPassword, fieldStroke, textPrimary, textSecondary)
        styleField(tilConfirmPassword, etConfirmPassword, fieldStroke, textPrimary, textSecondary)

        // Update button
        btnUpdatePassword.apply {
            backgroundTintList = ColorStateList.valueOf(buttonBg)
            setTextColor(buttonText)
        }

        // Progress bar tint
        progressBar.indeterminateTintList = ColorStateList.valueOf(textPrimary)

        // System bars
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false   // header is always dark → light icons
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    /**
     * Applies theme colors to a Material TextInputLayout + its EditText.
     *  - boxStroke      : outline of the field
     *  - inputText      : text the user types
     *  - hintAndIcon    : floating hint label and the password-toggle icon
     */
    private fun styleField(
        layout: TextInputLayout,
        editText: TextInputEditText,
        boxStroke: Int,
        inputText: Int,
        hintAndIcon: Int
    ) {
        layout.setBoxStrokeColor(boxStroke)
        layout.defaultHintTextColor = ColorStateList.valueOf(hintAndIcon)
        layout.setEndIconTintList(ColorStateList.valueOf(hintAndIcon))

        editText.setTextColor(inputText)
        editText.setHintTextColor(hintAndIcon)
    }

    // ---------------------------------------------------------------------
    // PASSWORD CHANGE
    // ---------------------------------------------------------------------

    private fun changePassword() {
        val currentPwd = etCurrentPassword.text.toString().trim()
        val newPwd = etNewPassword.text.toString().trim()
        val confirmPwd = etConfirmPassword.text.toString().trim()

        // Clear any prior errors
        tilCurrentPassword.error = null
        tilNewPassword.error = null
        tilConfirmPassword.error = null

        if (currentPwd.isEmpty()) {
            tilCurrentPassword.error = "Current password required"
            etCurrentPassword.requestFocus()
            return
        }
        if (newPwd.isEmpty()) {
            tilNewPassword.error = "New password required"
            etNewPassword.requestFocus()
            return
        }
        if (newPwd.length < 6) {
            tilNewPassword.error = "Password must be at least 6 characters"
            etNewPassword.requestFocus()
            return
        }
        if (newPwd != confirmPwd) {
            tilConfirmPassword.error = "Passwords do not match"
            etConfirmPassword.requestFocus()
            return
        }

        // Source of truth for the current session is Supabase, but the app
        // stores the signed-in email in SharedPreferences on login.
        val email = sharedPreferences.getString(SettingsActivity.KEY_USER_EMAIL, null)

        if (email.isNullOrEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = View.VISIBLE
        btnUpdatePassword.isEnabled = false

        lifecycleScope.launch {
            val result = AuthRepository.changePassword(email, currentPwd, newPwd)

            progressBar.visibility = View.GONE
            btnUpdatePassword.isEnabled = true

            if (result.success) {
                Toast.makeText(
                    this@ChangePasswordActivity,
                    "Password updated successfully",
                    Toast.LENGTH_LONG
                ).show()
                finish()
            } else {
                Toast.makeText(
                    this@ChangePasswordActivity,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
}