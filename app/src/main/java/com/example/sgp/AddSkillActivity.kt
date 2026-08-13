package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*

class AddSkillActivity : BaseActivity() {

    private var credits = 0
    private var selectedVideoUri: Uri? = null
    private var selectedType = "single"

    private val playlistVideos = mutableListOf<PlaylistVideoItem>()
    private val MAX_VIDEO_SIZE = 50 * 1024 * 1024
    private val STORAGE_BUCKET = "skill-videos"

    private lateinit var videoPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var db: FirebaseFirestore

    // Theme color palette matching AdminUsersActivity
    private val sheetBg = Color.parseColor("#16263A")
    private val sheetDivider = Color.parseColor("#28405A")
    private val sheetPrimaryText = Color.parseColor("#F5EDE4")

    // Bottom sheet picker palette — matches ExploreActivity's report dialog theme exactly
    private val pickerBg = Color.WHITE
    private val pickerTitleColor = Color.parseColor("#1B3C53")
    private val pickerSubtitleColor = Color.parseColor("#456882")
    private val pickerDividerColor = Color.parseColor("#EAF1F5")
    private val pickerCancelBg = Color.parseColor("#EAF1F5")
    private val pickerCancelText = Color.parseColor("#456882")

    // Helper method to convert DP to PX locally
    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

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
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

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
        val btnSingleVideo = findViewById<MaterialButton>(R.id.btnSingleVideo)
        val btnPlaylist = findViewById<MaterialButton>(R.id.btnPlaylist)
        val singleVideoSection = findViewById<LinearLayout>(R.id.singleVideoSection)
        val playlistSection = findViewById<LinearLayout>(R.id.playlistSection)

        val btnAddVideo = findViewById<TextView>(R.id.btnAddVideo)
        val playlistVideosContainer = findViewById<LinearLayout>(R.id.playlistVideosContainer)

        val btnSelectVideo = findViewById<TextView>(R.id.btnSelectVideo)
        val tvVideoFileName = findViewById<TextView>(R.id.tvVideoFileName)
        val ivVideoThumbnail = findViewById<ImageView>(R.id.ivVideoThumbnail)
        val btnCancelVideo = findViewById<ImageButton>(R.id.btnCancelVideo)
        val progressBar = findViewById<ProgressBar>(R.id.progressBarVideoUpload)

        // Category picker (bottom sheet, styled like the report dialog)
        val categories = resources.getStringArray(R.array.skill_categories)

        // Make the field behave like a button that opens the sheet instead of a text input
        actvCategory.isFocusable = false
        actvCategory.isFocusableInTouchMode = false
        actvCategory.isClickable = true
        actvCategory.isCursorVisible = false
        actvCategory.keyListener = null
        actvCategory.setOnClickListener {
            showCategoryBottomSheet(actvCategory, categories)
        }
        actvCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                showCategoryBottomSheet(actvCategory, categories)
            }
        }

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

        // --- Skill Type Toggle Listener with Dynamic Colors ---
        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            val checkedButton = group.findViewById<MaterialButton>(checkedId)
            checkedButton?.let { updateToggleButtonTheme(it, isChecked) }

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

        // Initialize default colors and check state
        btnSingleVideo?.let { updateToggleButtonTheme(it, true) }
        btnPlaylist?.let { updateToggleButtonTheme(it, false) }
        toggleGroup.check(R.id.btnSingleVideo)

        btnSelectVideo.setOnClickListener {
            videoPickerLauncher.launch("video/*")
        }

        btnCancelVideo.setOnClickListener {
            selectedVideoUri = null
            tvVideoFileName.text = "No video selected"
            ivVideoThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            ivVideoThumbnail.clearColorFilter()
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
        BottomNavHelper.setup(this, BottomNavItem.ADD_SKILL)
    }

    /**
     * Dynamically updates button styling according to state and theme palette:
     * Checked: BG #1B3C53 | Text #EAF1F5 | Stroke #1B3C53
     * Unchecked: BG #EAF1F5 | Text #456882 | Stroke #9AA7B0
     */
    private fun updateToggleButtonTheme(button: MaterialButton, isChecked: Boolean) {
        if (isChecked) {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#1B3C53"))
            button.setTextColor(Color.parseColor("#EAF1F5"))
            button.strokeColor = ColorStateList.valueOf(Color.parseColor("#1B3C53"))
        } else {
            button.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#EAF1F5"))
            button.setTextColor(Color.parseColor("#456882"))
            button.strokeColor = ColorStateList.valueOf(Color.parseColor("#9AA7B0"))
        }
        button.rippleColor = ColorStateList.valueOf(Color.parseColor("#D2C1B6"))
    }

    /**
     * Shows a bottom sheet category picker styled like the app's "Report" dialog:
     * white rounded sheet, title + subtitle, list of items with hairline dividers,
     * and a pill-shaped Cancel button at the bottom.
     */
    private fun showCategoryBottomSheet(
        actvCategory: MaterialAutoCompleteTextView,
        categories: Array<String>
    ) {
        val dialog = BottomSheetDialog(this)

        val scrollView = android.widget.ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(pickerBg)
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(),
                    dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
        }

        // Title
        rootLayout.addView(TextView(this).apply {
            text = "Select Category"
            setTextColor(pickerTitleColor)
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        })

        // Subtitle
        rootLayout.addView(TextView(this).apply {
            text = "Choose the category that best fits your skill"
            setTextColor(pickerSubtitleColor)
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        })

        rootLayout.addView(pickerDivider())

        val currentSelection = actvCategory.text.toString()

        categories.forEachIndexed { index, category ->
            rootLayout.addView(pickerCategoryRow(category, category == currentSelection) {
                actvCategory.setText(category, false)
                dialog.dismiss()
            })
            if (index != categories.lastIndex) {
                rootLayout.addView(pickerDivider())
            }
        }

        // Cancel pill button
        val btnCancel = pillButton("Cancel", pickerCancelBg, pickerCancelText).apply {
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
        rootLayout.addView(btnCancel)

        scrollView.addView(rootLayout)
        dialog.setContentView(scrollView)

        dialog.setOnDismissListener {
            actvCategory.clearFocus()
        }

        dialog.show()
    }

    /** A single tappable category row */
    private fun pickerCategoryRow(text: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(pickerTitleColor)
            setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    /** Hairline divider */
    private fun pickerDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(pickerDividerColor)
        }
    }

    /** Pill-shaped text "button" */
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

    private fun handleSingleVideoSelected(uri: Uri) {
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
        ivVideoThumbnail.setColorFilter(Color.parseColor("#1B3C53"))
        btnCancelVideo.visibility = View.VISIBLE
    }

    private fun addPlaylistVideoItem(container: LinearLayout) {
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

            val userId = userEmail
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