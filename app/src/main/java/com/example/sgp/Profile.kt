package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
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
import com.google.android.material.bottomnavigation.BottomNavigationView
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
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        db = Firebase.firestore

        initViews()
        setupToolbar()
        setupBottomNavigation()

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
        bottomNav = findViewById(R.id.bottomNavigation)

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

        findViewById<LinearLayout>(R.id.layoutSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.layoutLogout).setOnClickListener {
            showLogoutConfirmation()
        }
    }

    private fun setupToolbar() {
        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.profileToolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.my_profile)
        toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupBottomNavigation() {
        bottomNav.selectedItemId = R.id.nav_profile
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, Home::class.java))
                    finish()
                    true
                }
                R.id.nav_explore -> {
                    startActivity(Intent(this, ExploreActivity::class.java))
                    true
                }
                R.id.nav_add_skill -> {
                    startActivity(Intent(this, AddSkillActivity::class.java))
                    true
                }
                R.id.nav_trades -> {
                    startActivity(Intent(this, MyTradesActivity::class.java))
                    true
                }
                R.id.nav_profile -> true
                else -> false
            }
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

        if (!skill.videoUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(skill.videoUrl)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(ivThumbnail)
        } else {
            ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
        }

        val clickListener = View.OnClickListener {
            if (!skill.videoUrl.isNullOrEmpty()) {
                playVideo(skill.videoUrl)
            } else if (skill.skillType == "playlist" && !skill.videos.isNullOrEmpty()) {
                playVideo(skill.videos!![0].videoUrl)
            } else {
                Toast.makeText(this, "No video available", Toast.LENGTH_SHORT).show()
            }
        }
        view.setOnClickListener(clickListener)
        ivPlay.setOnClickListener(clickListener)

        skillsContainer.addView(view)
    }

    private fun showImagePickerDialog() {
        val options = arrayOf("Upload from Gallery", "Take Photo")
        AlertDialog.Builder(this)
            .setTitle("Change Profile Picture")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> galleryLauncher.launch("image/*")
                    1 -> cameraLauncher.launch(null)
                }
            }
            .show()
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

    private fun showLogoutConfirmation() {
        AlertDialog.Builder(this)
            .setTitle("Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Logout") { _, _ -> logoutUser() }
            .setNegativeButton("Cancel", null)
            .show()
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

    private fun playVideo(videoUrl: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(videoUrl))
            intent.setDataAndType(Uri.parse(videoUrl), "video/*")
            startActivity(Intent.createChooser(intent, "Play video with"))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot play video: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}