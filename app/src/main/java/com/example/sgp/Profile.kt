package com.example.sgp

import android.content.Intent
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
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
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

    // ---- Theme palette (matches AdminTradesActivity) ----
    private val navyDark = Color.parseColor("#1B3C53")
    private val navyMed = Color.parseColor("#456882")
    private val cream = Color.parseColor("#F9F3EF")
    private val lightBg = Color.parseColor("#EAF1F5")
    private val tan = Color.parseColor("#D2C1B6")
    private val destructive = Color.parseColor("#DC2626")
    private val cardBorder = Color.parseColor("#DCE7ED")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        db = Firebase.firestore

        initViews()

        BottomNavHelper.setup(this, BottomNavItem.PROFILE)

        getUserFromPrefs()
        loadUserProfile()
    }

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
        currentUserId = currentUserEmail // we'll use email as identifier; but Firestore doc uses UID; we'll query by email
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
        tvMemberSince.text = "Member since ${format.format(date)}"

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

        // Give each skill row its own bordered white card + spacing below it,
        // so "Cricket" and "Car driving" read as two distinct tiles instead of
        // one continuous list. Applied here in code so item_profile_skill.xml
        // doesn't need to change.
        view.background = skillCardBackground()
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

        if (!skill.videoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(skill.videoUrl)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(ivThumbnail)
        } else {
            ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
        }

        // Same open logic as ExploreActivity.openSkill(): single-video skills
        // launch VideoPlayerActivity with full context, playlists launch
        // PlaylistActivity, so each skill opens its own dedicated video.
        val clickListener = View.OnClickListener { openSkill(skill) }
        view.setOnClickListener(clickListener)
        ivPlay.setOnClickListener(clickListener)

        skillsContainer.addView(view)
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
            setTextColor(navyMed)
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
                setTextColor(navyDark)
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

        root.addView(pillButton("Cancel", lightBg, navyDark).apply {
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
            text = "Log Out"
            setTextColor(navyDark)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "Are you sure you want to logout?"
            setTextColor(navyMed)
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
        val btnCancel = pillButton("Cancel", lightBg, navyMed).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnLogout = pillButton("Logout", destructive, cream).apply {
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

    override fun onResume() {
        super.onResume()
        loadUserProfile()
    }

    // ---------- Themed-dialog helpers (shared white rounded card + pill buttons) ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(navyDark)
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
            setBackgroundColor(lightBg)
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

    // White rounded card with a light border — gives each skill row its own
    // visible boundary so different videos don't blur into one list.
    private fun skillCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1), cardBorder)
        }
    }
}