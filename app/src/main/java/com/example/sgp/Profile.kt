package com.example.sgp

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class Profile : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var currentUserEmail: String
    private lateinit var currentUserId: String
    private var currentProfileImagePath: String? = null

    private lateinit var profileImageView: ImageView
    private lateinit var tvName: TextView
    private lateinit var tvMemberSince: TextView
    private lateinit var tvExchanges: TextView
    private lateinit var tvRating: TextView
    private lateinit var tvCredits: TextView
    private lateinit var skillsContainer: LinearLayout
    private lateinit var achievementsGrid: GridLayout

    private lateinit var sharedPreferences: SharedPreferences

    /** Tracks current theme so dialogs and re-renders stay consistent. */
    private var isDarkModeEnabled = false

    // ---- Fixed brand colors (unchanged across themes; live on the navy hero header) ----
    private val navyDark = Color.parseColor("#1B3C53")
    private val navyMed = Color.parseColor("#456882")
    private val cream = Color.parseColor("#F9F3EF")
    private val lightBg = Color.parseColor("#EAF1F5")
    private val tan = Color.parseColor("#D2C1B6")
    private val destructive = Color.parseColor("#DC2626")
    private val cardBorder = Color.parseColor("#DCE7ED")

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
    private val lightIconTintNeutral = Color.parseColor("#1B3C53")
    private val lightLogoutText = Color.parseColor("#C0392B")
    private val lightLogoutStroke = Color.parseColor("#E8C4C4")
    private val lightHeaderBg = Color.parseColor("#1B3C53")

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
    private val darkIconTintNeutral = Color.parseColor("#C9D6DE")
    private val darkLogoutText = Color.parseColor("#FF6961")
    private val darkLogoutStroke = Color.parseColor("#5C2A2A")
    private val darkHeaderBg = Color.parseColor("#0B1620")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        db = Firebase.firestore
        sharedPreferences = getSharedPreferences(SettingsActivity.PREFS_NAME, MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        initViews()
        applyTheme(isDarkModeEnabled)

        BottomNavHelper.setup(this, BottomNavItem.PROFILE)

        getUserFromPrefs()
        loadUserProfile()
    }

    override fun onResume() {
        super.onResume()
        // Dark mode may have been toggled from the Settings screen while we were away.
        isDarkModeEnabled = sharedPreferences.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        applyTheme(isDarkModeEnabled)
        loadUserProfile()
    }

    // ---------------------------------------------------------------------
    // THEME ENGINE
    // ---------------------------------------------------------------------

    /** Recolors every themed view on the page to match light/dark mode. */
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val cardBg = if (isDark) darkCardBg else lightCardBg
        val cardStroke = if (isDark) darkCardStroke else lightCardStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val divider = if (isDark) darkDivider else lightDivider
        val chevronTint = if (isDark) darkChevronTint else lightChevronTint
        val iconTintNeutral = if (isDark) darkIconTintNeutral else lightIconTintNeutral
        val logoutText = if (isDark) darkLogoutText else lightLogoutText
        val logoutStroke = if (isDark) darkLogoutStroke else lightLogoutStroke
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg

        // Root background + hero header (header stays dark navy in both themes, just deepens)
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<View>(R.id.headerSection)?.setBackgroundColor(headerBg)

        // Section headers
        findViewById<TextView>(R.id.tvMySkillsHeader)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvAchievementsHeader)?.setTextColor(textPrimary)
        findViewById<MaterialButton>(R.id.btnAddNewSkill)?.setTextColor(textSecondary)

        // Account list card
        findViewById<MaterialCardView>(R.id.cardAccountList)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = cardStroke
        }

        // Booking history row
        findViewById<ImageView>(R.id.ivIconBookingHistory)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvBookingHistory)?.setTextColor(textPrimary)
        findViewById<ImageView>(R.id.ivChevronBookingHistory)?.imageTintList = ColorStateList.valueOf(chevronTint)
        findViewById<View>(R.id.dividerBooking1)?.setBackgroundColor(divider)

        // Reviews row
        findViewById<ImageView>(R.id.ivIconReviews)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvReviews)?.setTextColor(textPrimary)
        findViewById<ImageView>(R.id.ivChevronReviews)?.imageTintList = ColorStateList.valueOf(chevronTint)
        findViewById<View>(R.id.dividerReviews1)?.setBackgroundColor(divider)

        // Feedback row
        findViewById<ImageView>(R.id.ivIconFeedback)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvFeedback)?.setTextColor(textPrimary)
        findViewById<ImageView>(R.id.ivChevronFeedback)?.imageTintList = ColorStateList.valueOf(chevronTint)
        findViewById<View>(R.id.dividerFeedback1)?.setBackgroundColor(divider)

        // Language row
        findViewById<TextView>(R.id.tvChangeLanguage)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvProfileLanguage)?.setTextColor(textSecondary)
        findViewById<ImageView>(R.id.ivChevronLanguage)?.imageTintList = ColorStateList.valueOf(chevronTint)
        findViewById<View>(R.id.dividerLanguage1)?.setBackgroundColor(divider)

        // Settings row
        findViewById<ImageView>(R.id.ivIconSettingsRow)?.imageTintList = ColorStateList.valueOf(iconTintNeutral)
        findViewById<TextView>(R.id.tvSettingsRow)?.setTextColor(textPrimary)
        findViewById<ImageView>(R.id.ivChevronSettings)?.imageTintList = ColorStateList.valueOf(chevronTint)

        // Logout card
        findViewById<MaterialCardView>(R.id.cardLogout)?.apply {
            setCardBackgroundColor(cardBg)
            strokeColor = logoutStroke
        }
        findViewById<ImageView>(R.id.ivIconLogout)?.imageTintList = ColorStateList.valueOf(logoutText)
        findViewById<TextView>(R.id.tvLogoutText)?.setTextColor(logoutText)
        findViewById<ImageView>(R.id.ivChevronLogout)?.imageTintList = ColorStateList.valueOf(chevronTint)

        // Recolor any already-inflated skill cards to match the new theme
        for (i in 0 until skillsContainer.childCount) {
            styleSkillCardView(skillsContainer.getChildAt(i), isDark)
        }

        // Status/nav bar contrast
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg
    }

    private fun currentDialogBg(): Int = if (isDarkModeEnabled) darkCardBg else Color.WHITE
    private fun currentTextPrimary(): Int = if (isDarkModeEnabled) darkTextPrimary else navyDark
    private fun currentTextSecondary(): Int = if (isDarkModeEnabled) darkTextSecondary else navyMed
    private fun currentSecondaryBtnBg(): Int = if (isDarkModeEnabled) darkCardStroke else lightBg
    private fun currentDividerColor(): Int = if (isDarkModeEnabled) darkDivider else lightBg

    private fun initViews() {
        profileImageView = findViewById(R.id.profileImage)
        tvName = findViewById(R.id.tvName)
        tvMemberSince = findViewById(R.id.tvMemberSince)
        tvExchanges = findViewById(R.id.tvExchanges)
        tvRating = findViewById(R.id.tvRating)
        tvCredits = findViewById(R.id.tvCredits)
        skillsContainer = findViewById(R.id.skillsContainer)
        achievementsGrid = findViewById(R.id.achievementsGrid)

        findViewById<ImageButton>(R.id.btnChangePhoto).setOnClickListener {
            showImagePickerDialog()
        }

        findViewById<MaterialButton>(R.id.btnEditProfile).setOnClickListener {
            if (currentUserEmail.isNotEmpty()) {
                val intent = Intent(this, EditProfileActivity::class.java).apply {
                    putExtra("email", currentUserEmail)
                }
                startActivity(intent)
            } else {
                Toast.makeText(this, "User email missing", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, Login::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
        }

        findViewById<MaterialButton>(R.id.btnAddNewSkill).setOnClickListener {
            startActivity(Intent(this, AddSkillActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutBookingHistory).setOnClickListener {
            Toast.makeText(this, "Booking History", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.layoutReviews).setOnClickListener {
            startActivity(Intent(this, RateUsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutSendFeedback).setOnClickListener {
            startActivity(Intent(this, SubmitFeedbackActivity::class.java))
        }

        findViewById<TextView>(R.id.tvProfileLanguage)?.text = LocaleHelper.getLanguageDisplayName(this)

        findViewById<LinearLayout>(R.id.layoutChangeLanguage)?.setOnClickListener {
            LocaleHelper.showLanguageDialog(this) { item ->
                findViewById<TextView>(R.id.tvProfileLanguage)?.text = LocaleHelper.getLanguageDisplayName(this)
                if (currentUserEmail.isNotEmpty()) {
                    db.collection("users").whereEqualTo("email", currentUserEmail).get()
                        .addOnSuccessListener { snapshot ->
                            if (!snapshot.isEmpty) {
                                snapshot.documents[0].reference.update("language", item.key)
                            }
                        }
                }
            }
        }

        findViewById<LinearLayout>(R.id.layoutSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutLogout).setOnClickListener {
            showLogoutConfirmation()
        }

        findViewById<ImageView>(R.id.btnProfileSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    private fun getUserFromPrefs() {
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        currentUserEmail = prefs.getString("user_email", "") ?: ""
        currentUserId = currentUserEmail
        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun loadUserProfile() {
        if (currentUserEmail.isEmpty()) return

        db.collection("users").whereEqualTo("email", currentUserEmail).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val user = snapshot.documents[0].toObject(Users::class.java)
                    user?.let { updateUI(it) }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading profile: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateUI(user: Users) {
        tvName.text = capitalizeName(user.name)
        tvExchanges.text = user.completedTrades.toString()
        tvRating.text = String.format("%.1f", user.rating)
        tvCredits.text = user.credits?.toString() ?: "0"
        currentProfileImagePath = user.profileImagePath

        val date = Date(user.joinedDate)
        val format = SimpleDateFormat("yyyy", Locale.getDefault())
        tvMemberSince.text = String.format(getString(R.string.member_since), format.format(date))

        if (!user.profileImage.isNullOrEmpty()) {
            Glide.with(this).load(user.profileImage).into(profileImageView)
        }

        loadUserSkills()
    }

    private fun loadUserSkills() {
        db.collection("skills")
            .whereEqualTo("userId", currentUserEmail)
            .get()
            .addOnSuccessListener { snapshot ->
                skillsContainer.removeAllViews()
                snapshot.documents.forEach { doc ->
                    doc.toObject(Skill::class.java)?.let { addSkillView(it) }
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Error loading skills: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun addSkillView(skill: Skill) {
        val view = layoutInflater.inflate(R.layout.item_profile_skill, skillsContainer, false)
        val ivThumbnail = view.findViewById<ImageView>(R.id.ivSkillThumbnail)
        val tvTitle = view.findViewById<TextView>(R.id.tvSkillTitle)
        val tvCredits = view.findViewById<TextView>(R.id.tvSkillCredits)
        val ivPlay = view.findViewById<ImageView>(R.id.ivPlayIcon)

        tvTitle.text = skill.title
        tvCredits.text = "${skill.credits} credits"

        // --- FIX: Load the correct thumbnail ---
        val thumbnailUrl = if (skill.skillType == "playlist") {
            skill.thumbnailUrl  // playlists use thumbnailUrl
        } else {
            skill.videoUrl      // single videos use videoUrl
        }

        if (!thumbnailUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(thumbnailUrl)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(ivThumbnail)
        } else {
            ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
        }
        // --------------------------

        // Apply card + theme-aware text/icon styling
        styleSkillCardView(view, isDarkModeEnabled)

        val vertPad = dp(12)
        view.setPadding(vertPad, vertPad, vertPad, vertPad)
        (view.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            lp.bottomMargin = dp(14)
            view.layoutParams = lp
        } ?: run {
            view.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(14) }
        }
        view.elevation = dp(1).toFloat()
        view.clipToOutline = true

        val clickListener = View.OnClickListener { openSkill(skill) }
        view.setOnClickListener(clickListener)
        ivPlay.setOnClickListener(clickListener)

        skillsContainer.addView(view)
    }

    /** Recolors a single inflated skill card row to match light/dark mode. */
    private fun styleSkillCardView(view: View, isDark: Boolean) {
        view.background = skillCardBackground(isDark)
        view.findViewById<TextView>(R.id.tvSkillTitle)?.setTextColor(
            if (isDark) darkTextPrimary else lightTextPrimary
        )
        view.findViewById<TextView>(R.id.tvSkillCredits)?.setTextColor(
            if (isDark) darkTextSecondary else lightTextSecondary
        )
        view.findViewById<ImageView>(R.id.ivPlayIcon)?.imageTintList = ColorStateList.valueOf(
            if (isDark) darkChevronTint else lightChevronTint
        )
    }

    // ---------- Open skill video (mirrors ExploreActivity.openSkill) ----------
    private fun openSkill(skill: Skill) {
        if (skill.skillType == "single" && !skill.videoUrl.isNullOrEmpty()) {
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("videoUrl", skill.videoUrl)
            intent.putExtra("skillId", skill.id)
            intent.putExtra("skillTitle", skill.title)
            intent.putExtra("skillCategory", skill.category)
            intent.putExtra("skillUserId", skill.userId)
            intent.putExtra("skillUserName", skill.userName)
            intent.putExtra("skillCredits", skill.credits)
            intent.putExtra("skillDescription", skill.description)
            startActivity(intent)
        } else if (skill.skillType == "playlist") {
            val intent = Intent(this, PlaylistActivity::class.java)
            intent.putExtra("skillId", skill.id)
            startActivity(intent)
        } else {
            Toast.makeText(this, "No video available", Toast.LENGTH_SHORT).show()
        }
    }

    // ---------- Themed image picker (replaces default list-style AlertDialog) ----------
    private fun showImagePickerDialog() {
        val root = dialogCard()
        root.addView(dialogTitle("Change Profile Picture"))
        root.addView(TextView(this).apply {
            text = "Choose how you'd like to update your photo"
            setTextColor(currentTextSecondary())
            textSize = 12.5f
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(dialogDivider())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        fun addOptionRow(emoji: String, label: String, action: () -> Unit) {
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                setPadding(dp(4), dp(14), dp(4), dp(14))
                setBackgroundResource(outValue.resourceId)
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            }
            row.addView(TextView(this).apply {
                text = emoji
                textSize = 18f
                layoutParams = LinearLayout.LayoutParams(dp(28), LinearLayout.LayoutParams.WRAP_CONTENT)
            })
            row.addView(TextView(this).apply {
                text = label
                textSize = 15f
                setTextColor(currentTextPrimary())
                setTypeface(typeface, Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginStart = dp(12) }
            })
            root.addView(row)
        }

        addOptionRow("🖼️", "Upload from Gallery") { galleryLauncher.launch("image/*") }
        addOptionRow("📷", "Take Photo") { cameraLauncher.launch(null) }

        root.addView(dialogDivider())
        root.addView(pillButton("Cancel", currentSecondaryBtnBg(), currentTextPrimary()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
            setOnClickListener { dialog.dismiss() }
        })

        dialog.show()
    }

    private val galleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                profileImageView.setImageURI(it)
                uploadImageToSupabase(it)
            }
        }

    private val cameraLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            bitmap?.let {
                val path = MediaStore.Images.Media.insertImage(
                    contentResolver,
                    bitmap,
                    "ProfileImage",
                    null
                )
                val uri = Uri.parse(path)
                profileImageView.setImageURI(uri)
                uploadImageToSupabase(uri)
            }
        }

    private fun uploadImageToSupabase(uri: Uri) {
        lifecycleScope.launch {
            try {
                val (downloadUrl, newPath) = SupabaseImageUploader.uploadProfileImage(
                    context = this@Profile,
                    imageUri = uri,
                    userId = currentUserId,
                    oldImagePath = currentProfileImagePath
                )

                db.collection("users").whereEqualTo("email", currentUserEmail).get()
                    .addOnSuccessListener { snapshot ->
                        if (!snapshot.isEmpty) {
                            val docId = snapshot.documents[0].id
                            db.collection("users").document(docId)
                                .update(
                                    mapOf(
                                        "profileImage" to downloadUrl,
                                        "profileImagePath" to newPath
                                    )
                                )
                                .addOnSuccessListener {
                                    currentProfileImagePath = newPath
                                    getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                                        .edit()
                                        .putString("user_profile_image", downloadUrl)
                                        .apply()
                                    Toast.makeText(
                                        this@Profile,
                                        "Profile picture updated",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this@Profile, "Failed to save image: ${e.message}", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@Profile, "User lookup failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            } catch (e: Exception) {
                Toast.makeText(this@Profile, "Upload failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ---------- Themed logout confirmation ----------
    private fun showLogoutConfirmation() {
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
        val btnCancel = pillButton(getString(R.string.cancel), currentSecondaryBtnBg(), currentTextSecondary()).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnLogout = pillButton(getString(R.string.logout), destructive, cream).apply {
            setOnClickListener {
                dialog.dismiss()
                logoutUser()
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnLogout, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun logoutUser() {
        getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        FirebaseAuth.getInstance().signOut()
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ---------- Themed-dialog helpers ----------
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

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun dialogDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(10)
                bottomMargin = dp(10)
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

    private fun skillCardBackground(isDark: Boolean): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(if (isDark) darkCardBg else Color.WHITE)
            setStroke(dp(1), if (isDark) darkCardStroke else cardBorder)
        }
    }
}