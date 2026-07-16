package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.SwitchCompat
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    companion object {
        const val PREFS_NAME = "SkillSwapPrefs"
        const val KEY_PUSH_NOTIFICATIONS = "push_notifications"
        const val KEY_EMAIL_NOTIFICATIONS = "email_notifications"
        const val KEY_SMS_NOTIFICATIONS = "sms_notifications"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_LANGUAGE = "language"

        // User data keys (used for logout)
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_LOCATION = "user_location"
        const val KEY_USER_POINTS = "user_points"
        const val KEY_USER_TOTAL_TRADES = "user_total_trades"
        const val KEY_USER_RATING = "user_rating"
        const val KEY_USER_TOTAL_SKILLS = "user_total_skills"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        loadPreferences()
        setupClickListeners()
    }

    private fun loadPreferences() {
        findViewById<SwitchCompat>(R.id.switchPushNotifications).isChecked =
            sharedPreferences.getBoolean(KEY_PUSH_NOTIFICATIONS, true)
        findViewById<SwitchCompat>(R.id.switchEmailNotifications).isChecked =
            sharedPreferences.getBoolean(KEY_EMAIL_NOTIFICATIONS, true)
        findViewById<SwitchCompat>(R.id.switchSmsNotifications).isChecked =
            sharedPreferences.getBoolean(KEY_SMS_NOTIFICATIONS, false)
        findViewById<SwitchCompat>(R.id.switchDarkMode).isChecked =
            sharedPreferences.getBoolean(KEY_DARK_MODE, false)

        val language = sharedPreferences.getString(KEY_LANGUAGE, "English") ?: "English"
        findViewById<TextView>(R.id.tvLanguage).text = language
    }

    private fun setupClickListeners() {
        // Notification switches (no restart needed)
        findViewById<SwitchCompat>(R.id.switchPushNotifications).setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_PUSH_NOTIFICATIONS, isChecked).apply()
            showMessage("Push notifications ${if (isChecked) "enabled" else "disabled"}")
        }

        findViewById<SwitchCompat>(R.id.switchEmailNotifications).setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_EMAIL_NOTIFICATIONS, isChecked).apply()
            showMessage("Email notifications ${if (isChecked) "enabled" else "disabled"}")
        }

        findViewById<SwitchCompat>(R.id.switchSmsNotifications).setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_SMS_NOTIFICATIONS, isChecked).apply()
            showMessage("SMS notifications ${if (isChecked) "enabled" else "disabled"}")
        }

        // Dark mode – triggers app restart to apply globally
        findViewById<SwitchCompat>(R.id.switchDarkMode).setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            restartApp()
        }

        // Language selection – triggers app restart
        findViewById<View>(R.id.layoutLanguage).setOnClickListener {
            showLanguageDialog()
        }

        // Other click listeners
        findViewById<View>(R.id.layoutEditProfile).setOnClickListener {
            val email = sharedPreferences.getString(KEY_USER_EMAIL, "")
            if (!email.isNullOrEmpty()) {
                startActivity(Intent(this, EditProfileActivity::class.java).apply {
                    putExtra("email", email)
                })
            } else {
                showMessage("User email not found")
            }
        }

        findViewById<View>(R.id.layoutPrivacyPolicy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        findViewById<View>(R.id.layoutTerms).setOnClickListener {
            startActivity(Intent(this, TermsOfServiceActivity::class.java))
        }

        findViewById<View>(R.id.layoutRateUs).setOnClickListener {
            startActivity(Intent(this, RateUsActivity::class.java))
        }

        findViewById<View>(R.id.layoutChangePassword).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        findViewById<View>(R.id.layoutAppVersion).setOnClickListener {
            showMessage("App Version 1.0.0")
        }

        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showLanguageDialog() {
        val languages = arrayOf("English", "Spanish", "French", "German", "Hindi")
        val currentLanguage = sharedPreferences.getString(KEY_LANGUAGE, "English") ?: "English"
        val checkedItem = languages.indexOf(currentLanguage)

        MaterialAlertDialogBuilder(this)
            .setTitle("Select Language")
            .setSingleChoiceItems(languages, checkedItem) { dialog, which ->
                val selectedLanguage = languages[which]
                sharedPreferences.edit().putString(KEY_LANGUAGE, selectedLanguage).commit()
                dialog.dismiss()
                restartApp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun restartApp() {
        // Clear any pending intents and restart the app
        val intent = Intent(this, Home::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        // Force process death to ensure MyApp.attachBaseContext runs again
        android.os.Process.killProcess(android.os.Process.myPid())
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ ->
                // Sign out from Firebase Authentication
                FirebaseAuth.getInstance().signOut()
                // Clear local user data
                clearUserData()
                // Restart app – will land on Login because user data is cleared
                restartApp()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun clearUserData() {
        with(sharedPreferences.edit()) {
            remove(KEY_USER_NAME)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_LOCATION)
            remove(KEY_USER_POINTS)
            remove(KEY_USER_TOTAL_TRADES)
            remove(KEY_USER_RATING)
            remove(KEY_USER_TOTAL_SKILLS)
            apply()
        }
    }

    private fun showMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}