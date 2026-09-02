package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.Toolbar
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.onesignal.OneSignal

class SettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    companion object {
        private const val TAG = "SettingsActivity"

        const val PREFS_NAME = "SkillSwapPrefs"
        const val KEY_PUSH_NOTIFICATIONS = "push_notifications"
        const val KEY_DARK_MODE = "dark_mode"
        const val KEY_LANGUAGE = "language"

        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_LOCATION = "user_location"
        const val KEY_USER_POINTS = "user_points"
        const val KEY_USER_TOTAL_TRADES = "user_total_trades"
        const val KEY_USER_RATING = "user_rating"
        const val KEY_USER_TOTAL_SKILLS = "user_total_skills"
        const val KEY_USER_PFP_URL = "user_profile_image"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val toolbar: Toolbar? = findViewById(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.setDisplayShowTitleEnabled(false)
        }

        loadProfileData()
        fetchUserDataFromFirestore()
        loadPreferences()
        setupClickListeners()
        updatePasswordRowsForProvider()
    }

    override fun onResume() {
        super.onResume()
        loadProfileData()
        fetchUserDataFromFirestore()
        updatePasswordRowsForProvider()
    }

    // ---------------------------------------------------------------------
    // Sign-in provider gating for Change Password / Forgot Password
    // ---------------------------------------------------------------------

    /** True only if this account has an email/password credential linked. */
    private fun isEmailPasswordUser(): Boolean {
        val providerData = auth.currentUser?.providerData ?: return false
        return providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
    }

    /** Friendly name of whatever provider the user *did* sign in with, for the popup message. */
    private fun getSignInProviderLabel(): String {
        val providerId = auth.currentUser?.providerData
            ?.map { it.providerId }
            ?.firstOrNull { it != "firebase" }
            ?: return "another sign-in method"

        return when (providerId) {
            "google.com" -> "Google"
            "facebook.com" -> "Facebook"
            "apple.com" -> "Apple"
            EmailAuthProvider.PROVIDER_ID -> "Email/Password"
            else -> providerId
        }
    }

    /**
     * Dims (but keeps tappable) the Change Password / Forgot Password rows
     * for non-email/password accounts, so it's visually obvious they're restricted
     * before the user even taps them.
     */
    private fun updatePasswordRowsForProvider() {
        val allowed = isEmailPasswordUser()
        val alpha = if (allowed) 1.0f else 0.5f

        findViewById<View>(R.id.layoutChangePassword)?.alpha = alpha
        findViewById<View>(R.id.layoutForgotPassword)?.alpha = alpha
    }

    private fun showProviderRestrictedDialog() {
        val provider = getSignInProviderLabel()

        MaterialAlertDialogBuilder(this)
            .setTitle("Action Not Supported 🚫")
            .setMessage(
                "This feature is not available in SkillSwap for accounts signed in through $provider.")
            .setPositiveButton("Got it", null)
            .show()
    }

    // ---------------------------------------------------------------------
    // Forgot password
    // ---------------------------------------------------------------------

    private fun handleForgotPasswordClick() {
        if (!isEmailPasswordUser()) {
            showProviderRestrictedDialog()
            return
        }

        val email = auth.currentUser?.email
            ?: sharedPreferences.getString(KEY_USER_EMAIL, null)

        if (email.isNullOrEmpty()) {
            showMessage("No email found on this account")
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Reset Password")
            .setMessage("Send a password reset link to $email?")
            .setPositiveButton("Send") { _, _ -> sendPasswordResetEmail(email) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun sendPasswordResetEmail(email: String) {
        auth.sendPasswordResetEmail(email)
            .addOnSuccessListener {
                showMessage("Reset link sent to $email")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to send password reset email", e)
                showMessage("Failed to send reset link: ${e.message}")
            }
    }

    // ---------------------------------------------------------------------
    // Profile loading (unchanged from before)
    // ---------------------------------------------------------------------

    private fun loadProfileData() {
        val currentUser = auth.currentUser

        val userName = sharedPreferences.getString(KEY_USER_NAME, null)
            ?: currentUser?.displayName
            ?: "Skill Swapper"

        val userEmail = sharedPreferences.getString(KEY_USER_EMAIL, null)
            ?: currentUser?.email
            ?: "No email found"

        val pfpUrl = sharedPreferences.getString(KEY_USER_PFP_URL, null)
            ?: currentUser?.photoUrl?.toString()

        findViewById<TextView>(R.id.tvUserName)?.text = userName
        findViewById<TextView>(R.id.tvUserEmail)?.text = userEmail

        loadAvatarInto(findViewById(R.id.ivAvatar), pfpUrl)
    }

    private fun fetchUserDataFromFirestore() {
        val email = sharedPreferences.getString(KEY_USER_EMAIL, null)
            ?: auth.currentUser?.email
            ?: return

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    Log.w(TAG, "No Firestore user found for email: $email")
                    return@addOnSuccessListener
                }

                val user = snapshot.documents[0].toObject(Users::class.java)
                if (user == null) {
                    Log.w(TAG, "Failed to parse Users doc for email: $email")
                    return@addOnSuccessListener
                }

                sharedPreferences.edit().apply {
                    putString(KEY_USER_NAME, user.name.ifEmpty { "Skill Swapper" })
                    putString(KEY_USER_EMAIL, user.email.ifEmpty { email })
                    putString(KEY_USER_PFP_URL, user.profileImage)
                    putInt(KEY_USER_POINTS, user.credits ?: 0)
                    putInt(KEY_USER_TOTAL_TRADES, user.completedTrades)
                    putFloat(KEY_USER_RATING, user.rating.toFloat())
                    apply()
                }

                findViewById<TextView>(R.id.tvUserName)?.text =
                    if (user.name.isNotEmpty()) user.name else "Skill Swapper"
                findViewById<TextView>(R.id.tvUserEmail)?.text =
                    if (user.email.isNotEmpty()) user.email else email

                loadAvatarInto(findViewById(R.id.ivAvatar), user.profileImage)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to read users doc for email $email: ${e.message}", e)
            }
    }

    private fun loadAvatarInto(imageView: ImageView?, rawUrl: String?) {
        if (imageView == null) return

        imageView.imageTintList = null

        val url = rawUrl?.trim()
        if (url.isNullOrEmpty()) {
            imageView.setImageResource(R.drawable.outline_person_24)
            return
        }

        val failureListener = object : RequestListener<android.graphics.drawable.Drawable> {
            override fun onLoadFailed(
                e: GlideException?,
                model: Any?,
                target: Target<android.graphics.drawable.Drawable>,
                isFirstResource: Boolean
            ): Boolean {
                Log.e(TAG, "Glide failed to load avatar from '$model'", e)
                return false
            }

            override fun onResourceReady(
                resource: android.graphics.drawable.Drawable,
                model: Any,
                target: Target<android.graphics.drawable.Drawable>?,
                dataSource: DataSource,
                isFirstResource: Boolean
            ): Boolean = false
        }

        Glide.with(this)
            .load(url)
            .listener(failureListener)
            .placeholder(R.drawable.outline_person_24)
            .error(R.drawable.outline_person_24)
            .circleCrop()
            .into(imageView)
    }

    private fun loadPreferences() {
        findViewById<MaterialSwitch>(R.id.switchPushNotifications)?.isChecked =
            sharedPreferences.getBoolean(KEY_PUSH_NOTIFICATIONS, true)

        val isDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        findViewById<MaterialSwitch>(R.id.switchDarkMode)?.isChecked = isDarkMode

        val language = LocaleHelper.getLanguageDisplayName(this)
        findViewById<TextView>(R.id.tvLanguage)?.text = language

        findViewById<TextView>(R.id.tvAppVersion)?.text = "v1.0.0"
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.ivAvatar)?.setOnClickListener {
            showImagePickerOptions()
        }

        findViewById<View>(R.id.layoutProfileHeader)?.setOnClickListener {
            openEditProfile()
        }

        // Change Password -> gated to Email/Password sign-in only
        findViewById<View>(R.id.layoutChangePassword)?.setOnClickListener {
            if (isEmailPasswordUser()) {
                startActivity(Intent(this, ChangePasswordActivity::class.java))
            } else {
                showProviderRestrictedDialog()
            }
        }

        // Forgot Password -> gated to Email/Password sign-in only
        findViewById<View>(R.id.layoutForgotPassword)?.setOnClickListener {
            handleForgotPasswordClick()
        }

        findViewById<MaterialSwitch>(R.id.switchPushNotifications)?.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_PUSH_NOTIFICATIONS, isChecked).apply()
            showMessage("Push notifications ${if (isChecked) "enabled" else "disabled"}")
        }

        findViewById<MaterialSwitch>(R.id.switchDarkMode)?.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            val mode = if (isChecked) {
                AppCompatDelegate.MODE_NIGHT_YES
            } else {
                AppCompatDelegate.MODE_NIGHT_NO
            }
            AppCompatDelegate.setDefaultNightMode(mode)
        }

        findViewById<View>(R.id.layoutLanguage)?.setOnClickListener {
            showLanguageDialog()
        }

        findViewById<View>(R.id.layoutHelpSupport)?.setOnClickListener {
            startActivity(Intent(this, HelpSupportActivity::class.java))
        }

        findViewById<View>(R.id.layoutPrivacyPolicy)?.setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }

        findViewById<View>(R.id.layoutTerms)?.setOnClickListener {
            startActivity(Intent(this, TermsOfServiceActivity::class.java))
        }

        findViewById<View>(R.id.layoutRateUs)?.setOnClickListener {
            startActivity(Intent(this, RateUsActivity::class.java))
        }

        findViewById<View>(R.id.layoutAbout)?.setOnClickListener {
            startActivity(Intent(this, AboutActivity::class.java))
        }

        findViewById<MaterialButton>(R.id.btnLogout)?.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun showImagePickerOptions() {
        val options = arrayOf("Choose from Gallery", "Take Photo")
        MaterialAlertDialogBuilder(this)
            .setTitle("Change Profile Picture")
            .setItems(options) { _, _ ->
                openEditProfile()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openEditProfile() {
        val email = sharedPreferences.getString(KEY_USER_EMAIL, "")
            .takeIf { !it.isNullOrEmpty() }
            ?: auth.currentUser?.email ?: ""

        if (email.isNotEmpty()) {
            startActivity(Intent(this, EditProfileActivity::class.java).apply {
                putExtra("email", email)
            })
        } else {
            showMessage("User email not found")
        }
    }

    private fun showLanguageDialog() {
        LocaleHelper.showLanguageDialog(this) { selected ->
            findViewById<TextView>(R.id.tvLanguage)?.text = selected.nativeName
            val email = sharedPreferences.getString(KEY_USER_EMAIL, "")
                ?.takeIf { it.isNotEmpty() }
                ?: auth.currentUser?.email
                ?: ""
            if (email.isNotEmpty()) {
                db.collection("users").whereEqualTo("email", email).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            snapshot.documents[0].reference.update("language", selected.key)
                        }
                    }
            }
            restartApp()
        }
    }

    private fun restartApp() {
        val intent = Intent(this, Home::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout_confirmation_title)
            .setMessage(R.string.logout_confirmation_message)
            .setPositiveButton(R.string.logout) { _, _ ->
                auth.signOut()
                OneSignal.logout()
                clearUserData()
                navigateToLogin()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun navigateToLogin() {
        val intent = Intent(this, Login::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun clearUserData() {
        sharedPreferences.edit().apply {
            remove(KEY_USER_NAME)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_LOCATION)
            remove(KEY_USER_POINTS)
            remove(KEY_USER_TOTAL_TRADES)
            remove(KEY_USER_RATING)
            remove(KEY_USER_TOTAL_SKILLS)
            remove(KEY_USER_PFP_URL)
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