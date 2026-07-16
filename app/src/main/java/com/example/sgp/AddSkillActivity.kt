package com.example.sgp

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.*
import com.google.android.material.bottomnavigation.BottomNavigationView
import android.content.Intent
import io.github.jan.supabase.storage.storage
import com.google.firebase.Firebase

class AddSkillActivity : BaseActivity() {

    private var credits = 0
    private var selectedVideoUri: Uri? = null
    private var selectedType = "single"

    private val playlistVideos = mutableListOf<PlaylistVideoItem>()
    private val MAX_VIDEO_SIZE = 50 * 1024 * 1024
    private val STORAGE_BUCKET = "skill-videos"

    private lateinit var videoPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_skill)

        db = Firebase.firestore

        videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleSingleVideoSelected(it) }
        }

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val currentUserEmail = prefs.getString("user_email", "") ?: ""
        val currentUserName = prefs.getString("user_name", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Views
        val etTitle = findViewById<TextInputEditText>(R.id.etTitle)
        val actvCategory = findViewById<MaterialAutoCompleteTextView>(R.id.actvCategory)
        val etDescription = findViewById<TextInputEditText>(R.id.etDescription)
        val etDuration = findViewById<TextInputEditText>(R.id.etDuration)
        val tvCredits = findViewById<TextView>(R.id.tvCredits)
        val btnDecrement = findViewById<ImageButton>(R.id.btnDecrement)
        val btnIncrement = findViewById<ImageButton>(R.id.btnIncrement)
        val btnPublish = findViewById<MaterialButton>(R.id.btnPublishSkill)

        val toggleGroup = findViewById<MaterialButtonToggleGroup>(R.id.toggleSkillType)
        val singleVideoSection = findViewById<LinearLayout>(R.id.singleVideoSection)
        val playlistSection = findViewById<LinearLayout>(R.id.playlistSection)

        val btnAddVideo = findViewById<Button>(R.id.btnAddVideo)
        val playlistVideosContainer = findViewById<LinearLayout>(R.id.playlistVideosContainer)

        val btnSelectVideo = findViewById<Button>(R.id.btnSelectVideo)
        val tvVideoFileName = findViewById<TextView>(R.id.tvVideoFileName)
        val ivVideoThumbnail = findViewById<ImageView>(R.id.ivVideoThumbnail)
        val btnCancelVideo = findViewById<ImageButton>(R.id.btnCancelVideo)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarVideoUpload)

        // Category Dropdown
        val categories = resources.getStringArray(R.array.skill_categories)
        val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, categories)
        actvCategory.setAdapter(adapter)

        // Credits Logic
        tvCredits.text = credits.toString()

        btnDecrement.setOnClickListener {
            if (credits > 0) {
                credits--
                tvCredits.text = credits.toString()
            }
        }

        btnIncrement.setOnClickListener {
            if (credits < 100) {
                credits++
                tvCredits.text = credits.toString()
            }
        }

        // Toggle
        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                if (checkedId == R.id.btnSingleVideo) {
                    selectedType = "single"
                    singleVideoSection.visibility = View.VISIBLE
                    playlistSection.visibility = View.GONE
                } else {
                    selectedType = "playlist"
                    singleVideoSection.visibility = View.GONE
                    playlistSection.visibility = View.VISIBLE
                }
            }
        }
        toggleGroup.check(R.id.btnSingleVideo)

        btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        btnCancelVideo.setOnClickListener {
            selectedVideoUri = null
            tvVideoFileName.text = "No video selected"
            ivVideoThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            btnCancelVideo.visibility = View.GONE
        }

        btnAddVideo.setOnClickListener {
            addPlaylistVideoItem(playlistVideosContainer)
        }

        btnPublish.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val category = actvCategory.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val duration = etDuration.text.toString().trim()

            if (title.isEmpty() || category.isEmpty() || description.isEmpty() || duration.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (selectedVideoUri != null) {
                btnPublish.isEnabled = false
                progressBar.visibility = View.VISIBLE

                uploadSingleVideoAndSave(
                    currentUserEmail,
                    currentUserName,
                    title,
                    category,
                    description,
                    duration,
                    credits,
                    btnPublish,
                    progressBar
                )
            } else {
                Toast.makeText(this, "Select a video", Toast.LENGTH_SHORT).show()
            }
        }

        // Bottom Navigation
        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNavigation)
        bottomNav.selectedItemId = R.id.nav_add_skill
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
                R.id.nav_add_skill -> true
                R.id.nav_trades -> {
                    startActivity(Intent(this, MyTradesActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, Profile::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun handleSingleVideoSelected(uri: Uri) {
        // ... (unchanged)
        val cursor = contentResolver.query(uri, null, null, null, null)
        val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
        cursor?.moveToFirst()
        val fileSize = sizeIndex?.let { idx -> cursor.getLong(idx) } ?: 0
        cursor?.close()

        if (fileSize > MAX_VIDEO_SIZE) {
            Toast.makeText(this, "Video too large (max 50 MB)", Toast.LENGTH_LONG).show()
            return
        }

        selectedVideoUri = uri

        val tvVideoFileName = findViewById<TextView>(R.id.tvVideoFileName)
        val ivVideoThumbnail = findViewById<ImageView>(R.id.ivVideoThumbnail)
        val btnCancelVideo = findViewById<ImageButton>(R.id.btnCancelVideo)

        tvVideoFileName.text = uri.lastPathSegment ?: "Selected video"
        ivVideoThumbnail.setImageResource(R.drawable.baseline_check_circle_24)
        ivVideoThumbnail.setColorFilter(getColor(R.color.primary_green))
        btnCancelVideo.visibility = View.VISIBLE
    }

    private fun addPlaylistVideoItem(container: LinearLayout) {
        // ... (unchanged)
        val inflater = LayoutInflater.from(this)
        val itemView = inflater.inflate(R.layout.item_playlist_video, container, false)

        val etTitle = itemView.findViewById<TextInputEditText>(R.id.ettVideoTitle)
        val etDesc = itemView.findViewById<TextInputEditText>(R.id.ettVideoDesc)
        val tvCredits = itemView.findViewById<TextView>(R.id.tvvVideoCredits)
        val btnDecrement = itemView.findViewById<ImageButton>(R.id.VideoDecrement)
        val btnIncrement = itemView.findViewById<ImageButton>(R.id.VideoIncrement)
        val btnSelect = itemView.findViewById<Button>(R.id.SelectVideoFile)
        val tvFileName = itemView.findViewById<TextView>(R.id.tvVideoFileName)
        val btnRemove = itemView.findViewById<ImageButton>(R.id.btnRemoveVideo)
        val progressBar = itemView.findViewById<ProgressBar>(R.id.progressVideoUpload)

        var videoCredits = 0
        var selectedUri: Uri? = null

        tvCredits.text = videoCredits.toString()

        btnDecrement.setOnClickListener {
            if (videoCredits > 0) {
                videoCredits--
                tvCredits.text = videoCredits.toString()
            }
        }

        btnIncrement.setOnClickListener {
            if (videoCredits < 100) {
                videoCredits++
                tvCredits.text = videoCredits.toString()
            }
        }

        val launcher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let {
                selectedUri = it
                tvFileName.text = it.lastPathSegment ?: "Selected video"
            }
        }

        btnSelect.setOnClickListener {
            launcher.launch("video/*")
        }

        btnRemove.setOnClickListener {
            container.removeView(itemView)
        }

        val item = PlaylistVideoItem(
            view = itemView,
            titleEdit = etTitle,
            descEdit = etDesc,
            tvCredits = tvCredits,
            credits = { videoCredits },
            uri = { selectedUri },
            launcher = launcher,
            progressBar = progressBar
        )

        playlistVideos.add(item)
        container.addView(itemView)
    }

    private suspend fun uploadToSupabase(uri: Uri, fileName: String): String? {
        // ... (unchanged)
        return try {
            val bucket = SupabaseClient.client.storage.from(STORAGE_BUCKET)

            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.readBytes()
            } ?: throw Exception("File read failed")

            Log.d("UPLOAD_DEBUG", "File: $fileName, Size: ${bytes.size}")

            bucket.upload(
                path = fileName,
                data = bytes
            )

            val publicUrl =
                "https://ghrxltlstncjcizyyqfo.supabase.co/storage/v1/object/public/$STORAGE_BUCKET/$fileName"

            Log.d("UPLOAD_SUCCESS", publicUrl)

            return publicUrl

        } catch (e: Exception) {
            Log.e("UPLOAD_ERROR", "FAILED", e)
            null
        }
    }

    private fun uploadSingleVideoAndSave(
        userEmail: String,
        userName: String,
        title: String,
        category: String,
        description: String,
        duration: String,
        credits: Int,
        btnPublish: MaterialButton,
        progressBar: ProgressBar
    ) {
        val skillId = db.collection("skills").document().id
        val fileName = "single_videos/${skillId}.mp4"

        CoroutineScope(Dispatchers.Main).launch {
            btnPublish.isEnabled = false
            progressBar.visibility = View.VISIBLE

            val videoUrl = uploadToSupabase(selectedVideoUri!!, fileName)

            if (videoUrl == null) {
                Toast.makeText(this@AddSkillActivity, "Video upload failed", Toast.LENGTH_LONG).show()
                btnPublish.isEnabled = true
                progressBar.visibility = View.GONE
                return@launch
            }

            val userId = userEmail // we'll store email as userId; Firestore doc ID will be separate
            val skill = Skill(
                id = skillId,
                userId = userId,
                userName = userName,
                title = title,
                description = description,
                category = category,
                duration = duration,
                credits = credits,
                timestamp = System.currentTimeMillis(),
                videoUrl = videoUrl,
                skillType = "single",
                videos = null
            )

            db.collection("skills").document(skillId).set(skill)
                .addOnSuccessListener {
                    Toast.makeText(this@AddSkillActivity, "Skill published!", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this@AddSkillActivity, "DB error: ${e.message}", Toast.LENGTH_LONG).show()
                    btnPublish.isEnabled = true
                    progressBar.visibility = View.GONE
                }
        }
    }

    data class PlaylistVideoItem(
        val view: View,
        val titleEdit: TextInputEditText,
        val descEdit: TextInputEditText,
        val tvCredits: TextView,
        val credits: () -> Int,
        val uri: () -> Uri?,
        val launcher: androidx.activity.result.ActivityResultLauncher<String>,
        val progressBar: ProgressBar
    )
}