package com.example.sgp

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.onesignal.OneSignal
import java.io.ByteArrayOutputStream

class SettingsActivity : BaseActivity() {

    private lateinit var sharedPreferences: SharedPreferences
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val storage: FirebaseStorage by lazy { FirebaseStorage.getInstance() }

    private var isDarkModeEnabled = false
    private val themeSwitchDelayMs = 200L

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightDivider = Color.parseColor("#D2C1B6")
    private val lightChevronTint = Color.parseColor("#456882")
    private val lightIconBgNeutral = Color.parseColor("#EAE1DA")
    private val lightIconTintNeutral = Color.parseColor("#1B3C53")
    private val lightIconBgGreen = Color.parseColor("#DCEEE8")
    private val lightIconTintGreen = Color.parseColor("#2D8577")
    private val lightIconBgGold = Color.parseColor("#F3E7CE")
    private val lightIconTintGold = Color.parseColor("#B8860B")
    private val lightLogoutBg = Color.parseColor("#F6DEDE")
    private val lightLogoutText = Color.parseColor("#C0392B")
    private val lightLogoutStroke = Color.parseColor("#E8C4C4")
    private val lightSwitchTrackOn = Color.parseColor("#1B3C53")
    private val lightSwitchThumbOn = Color.parseColor("#F9F3EF")
    private val lightSwitchTrackOff = Color.parseColor("#C7CDD1")
    private val lightSwitchThumbOff = Color.parseColor("#FFFFFF")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightBackButtonBg = Color.parseColor("#456882")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkDivider = Color.parseColor("#2A3B48")
    private val darkChevronTint = Color.parseColor("#6E8598")
    private val darkIconBgNeutral = Color.parseColor("#26343F")
    private val darkIconTintNeutral = Color.parseColor("#C9D6DE")
    private val darkIconBgGreen = Color.parseColor("#163B34")
    private val darkIconTintGreen = Color.parseColor("#4FD8BB")
    private val darkIconBgGold = Color.parseColor("#3A2E14")
    private val darkIconTintGold = Color.parseColor("#E8C468")
    private val darkLogoutBg = Color.parseColor("#3A1E1E")
    private val darkLogoutText = Color.parseColor("#FF6961")
    private val darkLogoutStroke = Color.parseColor("#5C2A2A")
    private val darkSwitchTrackOn = Color.parseColor("#3FA7D6")
    private val darkSwitchThumbOn = Color.parseColor("#F5F7F8")
    private val darkSwitchTrackOff = Color.parseColor("#37454F")
    private val darkSwitchThumbOff = Color.parseColor("#9AA9B4")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkBackButtonBg = Color.parseColor("#22323D")

    private val navyDark = Color.parseColor("#1B3C53")
    private val cream = Color.parseColor("#F9F3EF")
    private val headerTitleColor = Color.parseColor("#F9F3EF")

    private val logoutConfirmBgLight = Color.parseColor("#DC2626")
    private val logoutConfirmBgDark = Color.parseColor("#B3261E")
    private val logoutConfirmText = Color.WHITE

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

    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            uploadProfileImageBytes(readBytesFromUri(uri))
        } else {
            showMessage("No image selected")
        }
    }

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            uploadProfileImageBytes(bitmapToBytes(bitmap))
        } else {
            showMessage("Photo capture cancelled")
        }
    }

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            takePhotoLauncher.launch(null)
        } else {
            showMessage("Camera permission is required to take a photo")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        isDarkModeEnabled = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        applyTheme(isDarkModeEnabled)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

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
        val chevronTint = if (isDark) darkChevronTint else lightChevronTint
        val iconBgNeutral = if (isDark) darkIconBgNeutral else lightIconBgNeutral
        val iconTintNeutral = if (isDark) darkIconTintNeutral else lightIconTintNeutral
        val iconBgGreen = if (isDark) darkIconBgGreen else lightIconBgGreen
        val iconTintGreen = if (isDark) darkIconTintGreen else lightIconTintGreen
        val iconBgGold = if (isDark) darkIconBgGold else lightIconBgGold
        val iconTintGold = if (isDark) darkIconTintGold else lightIconTintGold
        val logoutBg = if (isDark) darkLogoutBg else lightLogoutBg
        val logoutText = if (isDark) darkLogoutText else lightLogoutText
        val logoutStroke = if (isDark) darkLogoutStroke else lightLogoutStroke
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val backButtonBg = if (isDark) darkBackButtonBg else lightBackButtonBg

        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)

        findViewById<View>(R.id.headerBar)?.setBackgroundColor(headerBg)
        findViewById<MaterialCardView>(R.id.btnBack)?.setCardBackgroundColor(backButtonBg)
        findViewById<ImageView>(R.id.ivBackIcon)?.imageTintList = ColorStateList.valueOf(headerTitleColor)

        findViewById<TextView>(R.id.tvSettingsTitle)?.apply {
            setTextColor(headerTitleColor)
            visibility = View.VISIBLE
            alpha = 1f
        }

        listOf(R.id.cardProfile, R.id.cardSecurity, R.id.cardPreferences, R.id.cardSupport)
            .forEach { id ->
                findViewById<MaterialCardView>(id)?.apply {
                    setCardBackgroundColor(cardBg)
                    strokeColor = cardStroke
                }
            }

        listOf(R.id.tvSectionSecurity, R.id.tvSectionPreferences, R.id.tvSectionSupport)
            .forEach { id -> findViewById<TextView>(id)?.setTextColor(textPrimary) }

        findViewById<TextView>(R.id.tvUserName)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvUserEmail)?.setTextColor(textSecondary)
        findViewById<ImageView>(R.id.ivChevronProfile)?.imageTintList = ColorStateList.valueOf(chevronTint)

        findViewById<MaterialCardView>(R.id.iconBgChangePassword)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconChangePassword)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvChangePasswordTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvChangePasswordSubtitle)?.setTextColor(textSecondary)
        findViewById<ImageView>(R.id.ivChevronChangePassword)?.imageTintList = ColorStateList.valueOf(chevronTint)
        findViewById<View>(R.id.dividerPassword1)?.setBackgroundColor(divider)

        findViewById<MaterialCardView>(R.id.iconBgForgotPassword)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconForgotPassword)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvForgotPasswordTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvForgotPasswordSubtitle)?.setTextColor(textSecondary)
        findViewById<ImageView>(R.id.ivChevronForgotPassword)?.imageTintList = ColorStateList.valueOf(chevronTint)

        findViewById<MaterialCardView>(R.id.iconBgPush)?.setCardBackgroundColor(iconBgGreen)
        findViewById<ImageView>(R.id.ivIconPush)?.imageTintList = ColorStateList.valueOf(iconTintGreen)
        findViewById<TextView>(R.id.tvPushTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvPushSubtitle)?.setTextColor(textSecondary)
        findViewById<View>(R.id.dividerPref1)?.setBackgroundColor(divider)

        findViewById<MaterialCardView>(R.id.iconBgDark)?.setCardBackgroundColor(iconBgGold)
        findViewById<ImageView>(R.id.ivIconDark)?.imageTintList = ColorStateList.valueOf(iconTintGold)
        findViewById<TextView>(R.id.tvDarkTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvDarkSubtitle)?.setTextColor(textSecondary)
        findViewById<View>(R.id.dividerPref2)?.setBackgroundColor(divider)

        // ============================================================
        // Language row — pill background on the value for clarity
        // ============================================================
        findViewById<MaterialCardView>(R.id.iconBgLanguage)?.setCardBackgroundColor(iconBgNeutral)
        findViewById<ImageView>(R.id.ivIconLanguage)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvLanguageLabel)?.setTextColor(textPrimary)

        findViewById<TextView>(R.id.tvLanguage)?.apply {
            setTextColor(textPrimary)
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(iconBgNeutral)
            }
            setPadding(dp(12), dp(5), dp(12), dp(5))
        }

        findViewById<ImageView>(R.id.ivChevronLanguage)?.imageTintList = ColorStateList.valueOf(chevronTint)

        listOf(
            R.id.ivIconHelp, R.id.ivIconPrivacy, R.id.ivIconTerms,
            R.id.ivIconRate, R.id.ivIconAbout, R.id.ivIconVersion
        ).forEach { id -> findViewById<ImageView>(id)?.imageTintList = ColorStateList.valueOf(chevronTint) }

        listOf(
            R.id.tvHelpSupport, R.id.tvPrivacyPolicy, R.id.tvTermsOfService,
            R.id.tvRateUs, R.id.tvAboutSkillSwap, R.id.tvAppVersionLabel
        ).forEach { id -> findViewById<TextView>(id)?.setTextColor(textPrimary) }

        findViewById<TextView>(R.id.tvAppVersion)?.setTextColor(textSecondary)

        listOf(
            R.id.ivChevronHelp, R.id.ivChevronPrivacy, R.id.ivChevronTerms,
            R.id.ivChevronRate, R.id.ivChevronAbout
        ).forEach { id -> findViewById<ImageView>(id)?.imageTintList = ColorStateList.valueOf(chevronTint) }

        listOf(
            R.id.dividerSupport1, R.id.dividerSupport2, R.id.dividerSupport3,
            R.id.dividerSupport4, R.id.dividerSupport5
        ).forEach { id -> findViewById<View>(id)?.setBackgroundColor(divider) }

        applySwitchColors(findViewById(R.id.switchPushNotifications), isDark)
        applySwitchColors(findViewById(R.id.switchDarkMode), isDark)

        findViewById<MaterialButton>(R.id.btnLogout)?.apply {
            backgroundTintList = ColorStateList.valueOf(logoutBg)
            setTextColor(logoutText)
            strokeColor = ColorStateList.valueOf(logoutStroke)
            visibility = View.VISIBLE
            alpha = 1f
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg

        forceRedraw(R.id.rootLayout)
        forceRedraw(R.id.headerBar)
        forceRedraw(R.id.tvSettingsTitle)
        forceRedraw(R.id.ivBackIcon)
        forceRedraw(R.id.btnLogout)
    }

    private fun forceRedraw(viewId: Int) {
        val view = findViewById<View>(viewId) ?: return
        view.postInvalidate()
        view.requestLayout()
    }

    private fun applySwitchColors(switch: MaterialSwitch?, isDark: Boolean) {
        if (switch == null) return

        val trackOn = if (isDark) darkSwitchTrackOn else lightSwitchTrackOn
        val trackOff = if (isDark) darkSwitchTrackOff else lightSwitchTrackOff
        val thumbOn = if (isDark) darkSwitchThumbOn else lightSwitchThumbOn
        val thumbOff = if (isDark) darkSwitchThumbOff else lightSwitchThumbOff

        switch.trackTintList = ColorStateList.valueOf(if (switch.isChecked) trackOn else trackOff)
        switch.thumbTintList = ColorStateList.valueOf(if (switch.isChecked) thumbOn else thumbOff)
    }

    private fun currentDialogBg(): Int = if (isDarkModeEnabled) darkCardBg else Color.WHITE
    private fun currentTextPrimary(): Int = if (isDarkModeEnabled) darkTextPrimary else navyDark
    private fun currentTextSecondary(): Int = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary
    private fun currentSecondaryBtnBg(): Int = if (isDarkModeEnabled) darkCardStroke else lightScreenBg
    private fun currentSecondaryBtnText(): Int = if (isDarkModeEnabled) darkTextPrimary else lightTextSecondary
    private fun currentLogoutConfirmBg(): Int = if (isDarkModeEnabled) logoutConfirmBgDark else logoutConfirmBgLight

    private fun isEmailPasswordUser(): Boolean {
        val providerData = auth.currentUser?.providerData ?: return false
        return providerData.any { it.providerId == EmailAuthProvider.PROVIDER_ID }
    }

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

    private fun updatePasswordRowsForProvider() {
        val allowed = isEmailPasswordUser()
        val alpha = if (allowed) 1.0f else 0.5f

        findViewById<View>(R.id.layoutChangePassword)?.alpha = alpha
        findViewById<View>(R.id.layoutForgotPassword)?.alpha = alpha
    }

    private fun showProviderRestrictedDialog() {
        val provider = getSignInProviderLabel()
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "🚫"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        root.addView(TextView(this).apply {
            text = "Action Not Supported"
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "This feature is not available in SkillSwap for accounts signed in through $provider."
            setTextColor(currentTextSecondary())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(18)
            }
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        root.addView(
            pillButton("Got it", navyDark, cream).apply {
                setOnClickListener { dialog.dismiss() }
            }
        )

        dialog.show()
    }

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

        findViewById<MaterialSwitch>(R.id.switchDarkMode)?.isChecked = isDarkModeEnabled

        val language = LocaleHelper.getLanguageDisplayName(this)
        findViewById<TextView>(R.id.tvLanguage)?.text = language

        findViewById<TextView>(R.id.tvAppVersion)?.text = "v1.0.0"

        applySwitchColors(findViewById(R.id.switchPushNotifications), isDarkModeEnabled)
        applySwitchColors(findViewById(R.id.switchDarkMode), isDarkModeEnabled)
    }

    private fun setupClickListeners() {
        findViewById<ImageView>(R.id.ivAvatar)?.setOnClickListener {
            showImagePickerOptions()
        }

        findViewById<View>(R.id.layoutProfileHeader)?.setOnClickListener {
            openEditProfile()
        }

        findViewById<View>(R.id.layoutChangePassword)?.setOnClickListener {
            if (isEmailPasswordUser()) {
                startActivity(Intent(this, ChangePasswordActivity::class.java))
            } else {
                showProviderRestrictedDialog()
            }
        }

        findViewById<View>(R.id.layoutForgotPassword)?.setOnClickListener {
            if (!isEmailPasswordUser()) {
                showProviderRestrictedDialog()
                return@setOnClickListener
            }

            val email = auth.currentUser?.email
                ?: sharedPreferences.getString(KEY_USER_EMAIL, null)

            if (email.isNullOrEmpty()) {
                showMessage("No email found on this account")
                return@setOnClickListener
            }

            startActivity(Intent(this, ForgotPasswordEmailActivity::class.java).apply {
                putExtra("email", email)
            })
        }

        findViewById<MaterialSwitch>(R.id.switchPushNotifications)?.setOnCheckedChangeListener { switchView, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_PUSH_NOTIFICATIONS, isChecked).apply()
            applySwitchColors(switchView as MaterialSwitch, isDarkModeEnabled)

            switchView.postDelayed({
                showMessage("Push notifications ${if (isChecked) "enabled" else "disabled"}")
            }, themeSwitchDelayMs)
        }

        findViewById<MaterialSwitch>(R.id.switchDarkMode)?.setOnCheckedChangeListener { switchView, isChecked ->
            sharedPreferences.edit().putBoolean(KEY_DARK_MODE, isChecked).apply()
            applySwitchColors(switchView as MaterialSwitch, isDarkModeEnabled)

            switchView.postDelayed({
                applyTheme(isChecked)
                val mode = if (isChecked) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
                AppCompatDelegate.setDefaultNightMode(mode)
            }, themeSwitchDelayMs)
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
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "Change Profile Picture"
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(18) }
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        root.addView(
            pillButton("Choose from Gallery", navyDark, cream).apply {
                setOnClickListener {
                    dialog.dismiss()
                    pickImageLauncher.launch("image/*")
                }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(8) }
            }
        )
        root.addView(
            pillButton("Take Photo", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
                setOnClickListener {
                    dialog.dismiss()
                    checkCameraPermissionAndOpen()
                }
            }
        )

        dialog.show()
    }

    private fun checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            takePhotoLauncher.launch(null)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun readBytesFromUri(uri: android.net.Uri): ByteArray {
        return contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    }

    private fun bitmapToBytes(bitmap: Bitmap): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
        return stream.toByteArray()
    }

    private fun uploadProfileImageBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) {
            showMessage("Could not read image")
            return
        }

        val email = sharedPreferences.getString(KEY_USER_EMAIL, null) ?: auth.currentUser?.email
        if (email.isNullOrEmpty()) {
            showMessage("Unable to identify user account")
            return
        }

        showMessage("Uploading photo…")

        val fileName = "profile_images/${auth.currentUser?.uid ?: email}_${System.currentTimeMillis()}.jpg"
        val storageRef = storage.reference.child(fileName)

        storageRef.putBytes(bytes)
            .continueWithTask { task ->
                if (!task.isSuccessful) task.exception?.let { throw it }
                storageRef.downloadUrl
            }
            .addOnSuccessListener { downloadUri -> saveProfileImageUrl(email, downloadUri.toString()) }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to upload profile image", e)
                showMessage("Failed to upload photo: ${e.message}")
            }
    }

    private fun saveProfileImageUrl(email: String, url: String) {
        sharedPreferences.edit().putString(KEY_USER_PFP_URL, url).apply()
        loadAvatarInto(findViewById(R.id.ivAvatar), url)

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    snapshot.documents[0].reference.update("profileImage", url)
                        .addOnSuccessListener { showMessage("Profile picture updated") }
                        .addOnFailureListener { showMessage("Photo uploaded, but failed to save to profile") }
                } else {
                    showMessage("Profile picture updated")
                }
            }
            .addOnFailureListener { e -> Log.e(TAG, "Failed to find user doc to update photo", e) }
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
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "👋"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.logout_confirmation_title)
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = getString(R.string.logout_confirmation_message)
            setTextColor(currentTextSecondary())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(18)
            }
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }

        val btnCancel = pillButton(getString(R.string.cancel), currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
            setOnClickListener { dialog.dismiss() }
        }

        val btnLogout = pillButton(getString(R.string.logout), currentLogoutConfirmBg(), logoutConfirmText).apply {
            setOnClickListener {
                dialog.dismiss()
                performLogout()
            }
        }

        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dp(8)
            }
        )
        buttonRow.addView(
            btnLogout,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        )
        root.addView(buttonRow)

        dialog.show()
    }

    private fun performLogout() {
        auth.signOut()
        OneSignal.logout()
        clearUserData()
        navigateToLogin()
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

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(12), dp(16), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }
}