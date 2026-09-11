package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
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
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import java.util.*

class AddSkillActivity : BaseActivity() {

    // ─── Constants ──────────────────────────────────────────────────────────
    private companion object {
        private const val MAX_VIDEO_SIZE_MB = 100
        private const val MAX_VIDEO_SIZE_BYTES = MAX_VIDEO_SIZE_MB * 1024 * 1024L
        private const val STORAGE_BUCKET = "skill-videos"
        private const val MEDIA_BUCKET = "media"
        private const val REQUEST_ADD_VIDEO = 1001

        // Same prefs file / key SettingsActivity and Home write to, so the
        // dark mode choice made there is reflected here automatically.
        private const val PREFS_NAME = "SkillSwapPrefs"
        private const val KEY_DARK_MODE = "dark_mode"
    }

    private var credits = 0
    private var selectedVideoUri: Uri? = null          // for single video
    private var selectedDemoUri: Uri? = null           // for playlist demo
    private var selectedThumbnailUri: Uri? = null      // for playlist thumbnail
    private var selectedType = "single"

    private val playlistVideos = mutableListOf<PlaylistVideo>()
    private lateinit var playlistAdapter: PlaylistVideoListAdapter

    // Launchers
    private lateinit var videoPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var demoPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
    private lateinit var thumbnailPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>

    private lateinit var db: FirebaseFirestore
    private lateinit var sharedPreferences: SharedPreferences

    private var isDarkModeEnabled = false

    // ---------------------------------------------------------------------
    // LIGHT THEME PALETTE (same hex values as Settings/Home, for consistency)
    // ---------------------------------------------------------------------
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightChipBg = Color.parseColor("#EAF1F5")
    private val lightPillBg = Color.parseColor("#1B3C53")
    private val lightOutlineStroke = Color.parseColor("#9AA7B0")
    private val lightToggleUncheckedBg = Color.parseColor("#EAF1F5")
    private val lightDashedBg = Color.parseColor("#EAF1F5")

    // ---------------------------------------------------------------------
    // DARK THEME PALETTE
    // ---------------------------------------------------------------------
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkChipBg = Color.parseColor("#22323D")
    private val darkPillBg = Color.parseColor("#22323D")
    private val darkOutlineStroke = Color.parseColor("#2A3B48")
    private val darkToggleUncheckedBg = Color.parseColor("#16232E")
    private val darkDashedBg = Color.parseColor("#16232E")

    // ─── Category picker (bottom sheet) — computed live from current theme ────
    private fun pickerBg(): Int = if (isDarkModeEnabled) darkCardBg else Color.WHITE
    private fun pickerTitleColor(): Int = if (isDarkModeEnabled) darkTextPrimary else Color.parseColor("#1B3C53")
    private fun pickerSubtitleColor(): Int = if (isDarkModeEnabled) darkTextSecondary else Color.parseColor("#456882")
    private fun pickerDividerColor(): Int = if (isDarkModeEnabled) darkCardStroke else Color.parseColor("#EAF1F5")
    private fun pickerCancelBg(): Int = if (isDarkModeEnabled) darkChipBg else Color.parseColor("#EAF1F5")
    private fun pickerCancelText(): Int = if (isDarkModeEnabled) darkTextPrimary else Color.parseColor("#456882")

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ─── Views ─────────────────────────────────────────────────────────────
    private lateinit var etTitle: TextInputEditText
    private lateinit var actvCategory: MaterialAutoCompleteTextView
    private lateinit var etDescription: TextInputEditText
    private lateinit var etDuration: TextInputEditText
    private lateinit var tvCredits: TextView
    private lateinit var btnDecrement: ImageButton
    private lateinit var btnIncrement: ImageButton
    private lateinit var btnPublish: MaterialButton
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var btnSingleVideo: MaterialButton
    private lateinit var btnPlaylist: MaterialButton
    private lateinit var singleVideoSection: LinearLayout
    private lateinit var playlistSection: LinearLayout

    // Playlist views
    private lateinit var etPlaylistTitle: TextInputEditText
    private lateinit var actvPlaylistCategory: MaterialAutoCompleteTextView
    private lateinit var etPlaylistDescription: TextInputEditText
    private lateinit var etPlaylistDuration: TextInputEditText
    private lateinit var rvPlaylistVideos: RecyclerView
    private lateinit var btnAddVideo: TextView
    private lateinit var btnPublishPlaylist: MaterialButton
    private lateinit var tvVideoCount: TextView
    private lateinit var tvTotalIndividualPrice: TextView
    private lateinit var tvSavings: TextView
    private lateinit var llSavings: LinearLayout

    // Playlist demo & thumbnail
    private lateinit var btnSelectDemo: TextView
    private lateinit var tvDemoFileName: TextView
    private lateinit var btnRemoveDemo: ImageButton

    private lateinit var btnSelectThumbnail: TextView
    private lateinit var ivThumbnailPreview: ImageView
    private lateinit var btnRemoveThumbnail: ImageButton
    private lateinit var tvThumbnailFileName: TextView

    // Single video views
    private lateinit var btnSelectVideo: TextView
    private lateinit var tvVideoFileName: TextView
    private lateinit var ivVideoThumbnail: ImageView
    private lateinit var btnCancelVideo: ImageButton
    private lateinit var progressBarVideo: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_skill)

        db = Firebase.firestore
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(KEY_DARK_MODE, false)

        // ─── Launchers ──────────────────────────────────────────────────────
        videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleSingleVideoSelected(it) }
        }
        demoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleDemoSelected(it) }
        }
        thumbnailPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handleThumbnailSelected(it) }
        }

        // ─── Toolbar ──────────────────────────────────────────────────────
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // ─── User session ─────────────────────────────────────────────────
        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val currentUserEmail = prefs.getString("user_email", "") ?: ""
        val currentUserName = prefs.getString("user_name", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // ─── Bind views ──────────────────────────────────────────────────
        bindViews()

        applyTheme(isDarkModeEnabled)

        // ─── Category dropdown for single video ──────────────────────────
        val categories = resources.getStringArray(R.array.skill_categories)
        actvCategory.isFocusable = false
        actvCategory.isFocusableInTouchMode = false
        actvCategory.isClickable = true
        actvCategory.isCursorVisible = false
        actvCategory.keyListener = null
        actvCategory.setOnClickListener { showCategoryBottomSheet(actvCategory, categories) }
        actvCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showCategoryBottomSheet(actvCategory, categories)
        }

        // ─── Category dropdown for playlist ──────────────────────────────
        actvPlaylistCategory.isFocusable = false
        actvPlaylistCategory.isFocusableInTouchMode = false
        actvPlaylistCategory.isClickable = true
        actvPlaylistCategory.isCursorVisible = false
        actvPlaylistCategory.keyListener = null
        actvPlaylistCategory.setOnClickListener { showCategoryBottomSheet(actvPlaylistCategory, categories) }
        actvPlaylistCategory.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) showCategoryBottomSheet(actvPlaylistCategory, categories)
        }

        // ─── Credits counter ──────────────────────────────────────────────
        tvCredits.text = credits.toString()
        btnDecrement.setOnClickListener {
            if (credits > 0) { credits--; tvCredits.text = credits.toString() }
            updateSavings()
        }
        btnIncrement.setOnClickListener {
            if (credits < 100) { credits++; tvCredits.text = credits.toString() }
            updateSavings()
        }

        // ─── Skill type toggle ─────────────────────────────────────────────
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
                    updateSavings()
                }
            }
        }
        btnSingleVideo?.let { updateToggleButtonTheme(it, true) }
        btnPlaylist?.let { updateToggleButtonTheme(it, false) }
        toggleGroup.check(R.id.btnSingleVideo)

        // ─── Single video selection ──────────────────────────────────────
        btnSelectVideo.setOnClickListener { videoPickerLauncher.launch("video/*") }
        btnCancelVideo.setOnClickListener {
            selectedVideoUri = null
            tvVideoFileName.text = "No video selected"
            ivVideoThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            ivVideoThumbnail.clearColorFilter()
            ivVideoThumbnail.imageTintList = ColorStateList.valueOf(currentTextSecondary())
            btnCancelVideo.visibility = View.GONE
        }

        // ─── Playlist demo ────────────────────────────────────────────────
        btnSelectDemo.setOnClickListener { demoPickerLauncher.launch("video/*") }
        btnRemoveDemo.setOnClickListener {
            selectedDemoUri = null
            tvDemoFileName.text = "No demo video added"
            btnRemoveDemo.visibility = View.GONE
        }

        // ─── Playlist thumbnail ────────────────────────────────────────────
        btnSelectThumbnail.setOnClickListener { thumbnailPickerLauncher.launch("image/*") }
        btnRemoveThumbnail.setOnClickListener {
            selectedThumbnailUri = null
            ivThumbnailPreview.setImageResource(R.drawable.baseline_image_24)
            tvThumbnailFileName.text = "No image selected"
            btnRemoveThumbnail.visibility = View.GONE
        }

        // ─── Playlist RecyclerView ────────────────────────────────────────
        playlistAdapter = PlaylistVideoListAdapter(playlistVideos) { position ->
            playlistVideos.removeAt(position)
            playlistAdapter.notifyDataSetChanged()
            updateVideoCount()
            updateSavings()
        }
        rvPlaylistVideos.layoutManager = LinearLayoutManager(this)
        rvPlaylistVideos.adapter = playlistAdapter

        // Drag & drop reorder
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.Callback() {
            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
                return makeMovementFlags(dragFlags, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                if (from < 0 || to < 0 || from == to) return false
                Collections.swap(playlistVideos, from, to)
                playlistAdapter.notifyItemMoved(from, to)
                updateSavings()
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
        })
        itemTouchHelper.attachToRecyclerView(rvPlaylistVideos)

        // ─── Add video ────────────────────────────────────────────────────
        btnAddVideo.setOnClickListener {
            val intent = Intent(this, AddPlaylistVideoActivity::class.java)
            intent.putExtra("videoCount", playlistVideos.size)
            startActivityForResult(intent, REQUEST_ADD_VIDEO)
        }

        // ─── Publish single video ────────────────────────────────────────
        btnPublish.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val category = actvCategory.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val duration = etDuration.text.toString().trim()

            if (title.isEmpty() || category.isEmpty() || description.isEmpty() || duration.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedVideoUri == null) {
                Toast.makeText(this, "Select a video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            btnPublish.isEnabled = false
            progressBarVideo.visibility = View.VISIBLE
            uploadSingleVideoAndSave(
                currentUserEmail, currentUserName,
                title, category, description, duration, credits,
                btnPublish, progressBarVideo
            )
        }

        // ─── Publish playlist ─────────────────────────────────────────────
        btnPublishPlaylist.setOnClickListener {
            val title = etPlaylistTitle.text.toString().trim()
            val category = actvPlaylistCategory.text.toString().trim()
            val description = etPlaylistDescription.text.toString().trim()
            val duration = etPlaylistDuration.text.toString().trim()

            if (title.isEmpty() || category.isEmpty() || description.isEmpty() || duration.isEmpty()) {
                Toast.makeText(this, "Fill all playlist details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (selectedDemoUri == null) {
                Toast.makeText(this, "Please select a demo video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (playlistVideos.isEmpty()) {
                Toast.makeText(this, "Add at least one video to the playlist", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (credits <= 0) {
                Toast.makeText(this, "Set a valid credit price", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validate each video
            for (video in playlistVideos) {
                if (video.title.isBlank() || video.videoUrl.isBlank()) {
                    Toast.makeText(this, "All videos must have a title and URL", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            btnPublishPlaylist.isEnabled = false
            btnPublishPlaylist.text = "Uploading..."
            uploadPlaylistAndSave(
                currentUserEmail, currentUserName,
                title, category, description, duration, credits,
                btnPublishPlaylist
            )
        }

        // ─── Bottom navigation ────────────────────────────────────────────
        BottomNavHelper.setup(this, BottomNavItem.ADD_SKILL)

        updateVideoCount()
    }

    override fun onResume() {
        super.onResume()
        // Pick up a dark-mode change made on the Settings screen since we last showed this one
        val savedDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        if (savedDarkMode != isDarkModeEnabled) {
            applyTheme(savedDarkMode)
        }
    }

    /**
     * Fires when the SYSTEM's own light/dark setting changes while this screen is open
     * (requires android:configChanges="uiMode" on this activity in the manifest). We
     * ignore the system's switch and re-assert our own saved preference instead.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyTheme(isDarkModeEnabled)
    }

    // ─── Bind views ────────────────────────────────────────────────────────
    private fun bindViews() {
        // Single video views
        etTitle = findViewById(R.id.etTitle)
        actvCategory = findViewById(R.id.actvCategory)
        etDescription = findViewById(R.id.etDescription)
        etDuration = findViewById(R.id.etDuration)
        tvCredits = findViewById(R.id.tvCredits)
        btnDecrement = findViewById(R.id.btnDecrement)
        btnIncrement = findViewById(R.id.btnIncrement)
        btnPublish = findViewById(R.id.btnPublishSkill)
        toggleGroup = findViewById(R.id.toggleSkillType)
        btnSingleVideo = findViewById(R.id.btnSingleVideo)
        btnPlaylist = findViewById(R.id.btnPlaylist)
        singleVideoSection = findViewById(R.id.singleVideoSection)
        playlistSection = findViewById(R.id.playlistSection)

        // Playlist views
        etPlaylistTitle = findViewById(R.id.etPlaylistTitle)
        actvPlaylistCategory = findViewById(R.id.actvPlaylistCategory)
        etPlaylistDescription = findViewById(R.id.etPlaylistDescription)
        etPlaylistDuration = findViewById(R.id.etPlaylistDuration)
        rvPlaylistVideos = findViewById(R.id.rvPlaylistVideos)
        btnAddVideo = findViewById(R.id.btnAddVideo)
        btnPublishPlaylist = findViewById(R.id.btnPublishPlaylist)
        tvVideoCount = findViewById(R.id.tvVideoCount)
        tvTotalIndividualPrice = findViewById(R.id.tvTotalIndividualPrice)
        tvSavings = findViewById(R.id.tvSavings)
        llSavings = findViewById(R.id.llSavings)

        // Playlist demo & thumbnail
        btnSelectDemo = findViewById(R.id.btnSelectDemo)
        tvDemoFileName = findViewById(R.id.tvDemoFileName)
        btnRemoveDemo = findViewById(R.id.btnRemoveDemo)

        btnSelectThumbnail = findViewById(R.id.btnSelectThumbnail)
        ivThumbnailPreview = findViewById(R.id.ivThumbnailPreview)
        btnRemoveThumbnail = findViewById(R.id.btnRemoveThumbnail)
        tvThumbnailFileName = findViewById(R.id.tvThumbnailFileName)

        // Single video selection
        btnSelectVideo = findViewById(R.id.btnSelectVideo)
        tvVideoFileName = findViewById(R.id.tvVideoFileName)
        ivVideoThumbnail = findViewById(R.id.ivVideoThumbnail)
        btnCancelVideo = findViewById(R.id.btnCancelVideo)
        progressBarVideo = findViewById(R.id.progressBarVideoUpload)
    }

    // ---------------------------------------------------------------------
    // THEME ENGINE
    // ---------------------------------------------------------------------

    private fun currentTextPrimary(): Int = if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary
    private fun currentTextSecondary(): Int = if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary

    /** Recolors every themed view on the page to match light/dark mode. */
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val cardBg = if (isDark) darkCardBg else lightCardBg
        val cardStroke = if (isDark) darkCardStroke else lightCardStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val chipBg = if (isDark) darkChipBg else lightChipBg
        val pillBg = if (isDark) darkPillBg else lightPillBg
        val outlineStroke = if (isDark) darkOutlineStroke else lightOutlineStroke
        val toggleUncheckedBg = if (isDark) darkToggleUncheckedBg else lightToggleUncheckedBg
        val dashedBg = if (isDark) darkDashedBg else lightDashedBg

        // Root + toolbar
        findViewById<View>(R.id.rootLayout)?.setBackgroundColor(screenBg)
        findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)?.setBackgroundColor(headerBg)

        // All outer cards get the same background/stroke treatment
        listOf(
            R.id.cardSkillDetails, R.id.cardVideoUpload, R.id.cardVideoSelectRow,
            R.id.cardPlaylistDetails, R.id.cardThumbnailSelect,
            R.id.cardDemoSelect, R.id.cardDemoSelectRow, R.id.cardPlaylistVideosCard,
            R.id.cardPricing
        ).forEach { id ->
            findViewById<MaterialCardView>(id)?.apply {
                setCardBackgroundColor(cardBg)
                strokeColor = cardStroke
            }
        }

        // Credits counter chip
        findViewById<MaterialCardView>(R.id.cardCreditsCounter)?.setCardBackgroundColor(chipBg)

        // Primary (bold, navy/cream) titles and labels
        listOf(
            R.id.tvSkillTypeLabel, R.id.tvSkillDetailsTitle, R.id.tvSelectVideoTitle,
            R.id.tvPlaylistDetailsTitle, R.id.tvDemoTitle,
            R.id.tvPlaylistVideosTitle, R.id.tvPricingTitle, R.id.tvCreditsRequiredLabel,
            R.id.tvCredits, R.id.tvTotalIndividualPrice
        ).forEach { id -> findViewById<TextView>(id)?.setTextColor(textPrimary) }

        // Secondary (steel-blue/gray) labels and hint text
        listOf(
            R.id.tvSelectVideoDescription, R.id.tvThumbnailLabel, R.id.tvThumbnailHint,
            R.id.tvDemoDescription, R.id.tvDemoSizeHint, R.id.tvPricingSubtitle,
            R.id.tvVideoCount, R.id.tvTotalIndividualLabel,
            R.id.tvVideoFileName, R.id.tvThumbnailFileName, R.id.tvDemoFileName
        ).forEach { id -> findViewById<TextView>(id)?.setTextColor(textSecondary) }

        // Icons that sit next to file-select rows
        listOf(R.id.ivVideoThumbnail, R.id.ivThumbnailPreview).forEach { id ->
            findViewById<ImageView>(id)?.imageTintList = ColorStateList.valueOf(textSecondary)
        }
        listOf(R.id.btnDecrement, R.id.btnIncrement).forEach { id ->
            findViewById<ImageButton>(id)?.imageTintList = ColorStateList.valueOf(textPrimary)
        }

        // All outlined TextInputLayouts get the same box stroke / hint color; the
        // EditText/AutoCompleteTextView inside gets the primary text color.
        listOf(
            R.id.titleLayout, R.id.categoryLayout, R.id.descriptionLayout, R.id.durationLayout,
            R.id.playlistTitleLayout, R.id.playlistCategoryLayout,
            R.id.playlistDescriptionLayout, R.id.playlistDurationLayout
        ).forEach { id ->
            findViewById<TextInputLayout>(id)?.apply {
                boxStrokeColor = textSecondary
                hintTextColor = ColorStateList.valueOf(textPrimary)
                defaultHintTextColor = ColorStateList.valueOf(textSecondary)
            }
        }
        listOf(
            R.id.etTitle, R.id.actvCategory, R.id.etDescription, R.id.etDuration,
            R.id.etPlaylistTitle, R.id.actvPlaylistCategory, R.id.etPlaylistDescription, R.id.etPlaylistDuration
        ).forEach { id -> (findViewById<View>(id) as? TextView)?.setTextColor(textPrimary) }

        // "Select" / "Upload" navy pill buttons — tint the pill_navy_bg drawable itself
        listOf(R.id.btnSelectVideo, R.id.btnSelectThumbnail, R.id.btnSelectDemo).forEach { id ->
            findViewById<TextView>(id)?.let { ViewCompat.setBackgroundTintList(it, ColorStateList.valueOf(pillBg)) }
        }

        // "+ Add Video" dashed button
        findViewById<TextView>(R.id.btnAddVideo)?.apply {
            setTextColor(textPrimary)
            ViewCompat.setBackgroundTintList(this, ColorStateList.valueOf(dashedBg))
        }

        // Toggle buttons re-themed with current palette
        findViewById<MaterialButton>(R.id.btnSingleVideo)?.let {
            updateToggleButtonTheme(it, toggleGroup.checkedButtonId == R.id.btnSingleVideo)
        }
        findViewById<MaterialButton>(R.id.btnPlaylist)?.let {
            updateToggleButtonTheme(it, toggleGroup.checkedButtonId == R.id.btnPlaylist)
        }

        // Status bar / nav bar to match theme
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
        insetsController.isAppearanceLightNavigationBars = !isDark
        window.statusBarColor = headerBg
        window.navigationBarColor = screenBg

        // Elevated header can keep a stale cached layer after a config change
        // without a full Activity recreation — force it to redraw.
        forceRedraw(R.id.rootLayout)
        forceRedraw(R.id.toolbar)
    }

    private fun forceRedraw(viewId: Int) {
        val view = findViewById<View>(viewId) ?: return
        view.invalidate()
        view.requestLayout()
    }

    // ─── Update video count ──────────────────────────────────────────────
    private fun updateVideoCount() {
        tvVideoCount.text = "${playlistVideos.size} videos"
    }

    // ─── Update savings ──────────────────────────────────────────────────
    private fun updateSavings() {
        val totalIndividual = playlistVideos.sumOf { it.credits }
        val playlistPrice = credits

        if (playlistVideos.isNotEmpty()) {
            llSavings.visibility = View.VISIBLE
            tvTotalIndividualPrice.text = "$totalIndividual credits"
            if (playlistPrice > 0 && playlistPrice < totalIndividual) {
                val saved = totalIndividual - playlistPrice
                tvSavings.visibility = View.VISIBLE
                tvSavings.text = "You save $saved credits"
            } else {
                tvSavings.visibility = View.GONE
            }
        } else {
            llSavings.visibility = View.GONE
        }
    }

    // ─── Handle result from AddPlaylistVideoActivity ────────────────────
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ADD_VIDEO && resultCode == RESULT_OK) {
            // Use getParcelableExtra (since PlaylistVideo is Parcelable)
            val video = data?.getParcelableExtra<PlaylistVideo>("video")
            if (video != null) {
                playlistVideos.add(video)
                playlistAdapter.notifyItemInserted(playlistVideos.size - 1)
                updateVideoCount()
                updateSavings()
            } else {
                Toast.makeText(this, "Failed to add video", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Toggle button theme ──────────────────────────────────────────────
    private fun updateToggleButtonTheme(button: MaterialButton, isChecked: Boolean) {
        val checkedBg = currentTextPrimary()
        val uncheckedBg = if (isDarkModeEnabled) darkToggleUncheckedBg else lightToggleUncheckedBg
        val checkedText = if (isDarkModeEnabled) darkScreenBg else Color.parseColor("#EAF1F5")
        val uncheckedText = currentTextSecondary()
        val uncheckedStroke = if (isDarkModeEnabled) darkOutlineStroke else lightOutlineStroke

        if (isChecked) {
            button.backgroundTintList = ColorStateList.valueOf(checkedBg)
            button.setTextColor(checkedText)
            button.strokeColor = ColorStateList.valueOf(checkedBg)
        } else {
            button.backgroundTintList = ColorStateList.valueOf(uncheckedBg)
            button.setTextColor(uncheckedText)
            button.strokeColor = ColorStateList.valueOf(uncheckedStroke)
        }
        button.rippleColor = ColorStateList.valueOf(Color.parseColor("#D2C1B6"))
    }

    // ─── Category bottom sheet ────────────────────────────────────────────
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
                setColor(pickerBg())
                cornerRadii = floatArrayOf(
                    dp(20).toFloat(), dp(20).toFloat(),
                    dp(20).toFloat(), dp(20).toFloat(),
                    0f, 0f, 0f, 0f
                )
            }
        }
        rootLayout.addView(TextView(this).apply {
            text = "Select Category"
            setTextColor(pickerTitleColor())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        })
        rootLayout.addView(TextView(this).apply {
            text = "Choose the category that best fits your skill"
            setTextColor(pickerSubtitleColor())
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

        val btnCancel = pillButton("Cancel", pickerCancelBg(), pickerCancelText()).apply {
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
        dialog.setOnDismissListener { actvCategory.clearFocus() }
        dialog.show()
    }

    private fun pickerCategoryRow(text: String, isSelected: Boolean, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(pickerTitleColor())
            setTypeface(typeface, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun pickerDivider(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(pickerDividerColor())
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

    // ─── File size validation ──────────────────────────────────────────────
    private fun checkFileSize(uri: Uri, maxBytes: Long = MAX_VIDEO_SIZE_BYTES): Boolean {
        val cursor = contentResolver.query(uri, null, null, null, null)
        val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
        cursor?.moveToFirst()
        val fileSize = sizeIndex?.let { cursor.getLong(it) } ?: 0
        cursor?.close()
        return fileSize <= maxBytes
    }

    private fun getFileSizeMB(uri: Uri): Long {
        val cursor = contentResolver.query(uri, null, null, null, null)
        val sizeIndex = cursor?.getColumnIndex(android.provider.OpenableColumns.SIZE)
        cursor?.moveToFirst()
        val fileSize = sizeIndex?.let { cursor.getLong(it) } ?: 0
        cursor?.close()
        return fileSize / (1024 * 1024)
    }

    // ─── Single video selection ──────────────────────────────────────────
    private fun handleSingleVideoSelected(uri: Uri) {
        if (!checkFileSize(uri)) {
            Toast.makeText(
                this,
                "Video too large (max $MAX_VIDEO_SIZE_MB MB). Current: ${getFileSizeMB(uri)} MB",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        selectedVideoUri = uri
        tvVideoFileName.text = uri.lastPathSegment ?: "Selected video"
        ivVideoThumbnail.setImageResource(R.drawable.baseline_check_circle_24)
        ivVideoThumbnail.setColorFilter(currentTextPrimary())
        btnCancelVideo.visibility = View.VISIBLE
    }

    // ─── Playlist demo selection ──────────────────────────────────────────
    private fun handleDemoSelected(uri: Uri) {
        if (!checkFileSize(uri)) {
            Toast.makeText(
                this,
                "Demo too large (max $MAX_VIDEO_SIZE_MB MB). Current: ${getFileSizeMB(uri)} MB",
                Toast.LENGTH_LONG
            ).show()
            return
        }
        selectedDemoUri = uri
        tvDemoFileName.text = uri.lastPathSegment ?: "Demo selected"
        btnRemoveDemo.visibility = View.VISIBLE
    }

    // ─── Playlist thumbnail selection ─────────────────────────────────────
    private fun handleThumbnailSelected(uri: Uri) {
        selectedThumbnailUri = uri
        Glide.with(this).load(uri).into(ivThumbnailPreview)
        tvThumbnailFileName.text = uri.lastPathSegment ?: "Image selected"
        btnRemoveThumbnail.visibility = View.VISIBLE
    }

    // ─── Upload helpers ──────────────────────────────────────────────────
    private suspend fun uploadVideoToSupabase(uri: Uri, fileName: String): String? {
        return try {
            val bucket = SupabaseClient.client.storage.from(STORAGE_BUCKET)
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: throw Exception("Could not read file")
            if (bytes.size > MAX_VIDEO_SIZE_BYTES) {
                throw Exception("File too large (max $MAX_VIDEO_SIZE_MB MB)")
            }
            bucket.upload(path = fileName, data = bytes) {
                upsert = true
            }
            "https://ghrxltlstncjcizyyqfo.supabase.co/storage/v1/object/public/$STORAGE_BUCKET/$fileName"
        } catch (e: Exception) {
            Log.e("UPLOAD_ERROR", "Failed to upload $fileName", e)
            throw Exception("${e.message ?: "Unknown error"}")
        }
    }

    private suspend fun uploadImageToSupabase(uri: Uri, fileName: String): String? {
        return try {
            val bucket = SupabaseClient.client.storage.from(MEDIA_BUCKET)
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: throw Exception("Could not read image")
            val MAX_IMAGE_SIZE = 20 * 1024 * 1024L
            if (bytes.size > MAX_IMAGE_SIZE) {
                throw Exception("Image too large (max 20 MB)")
            }
            bucket.upload(path = fileName, data = bytes) {
                upsert = true
            }
            "https://ghrxltlstncjcizyyqfo.supabase.co/storage/v1/object/public/$MEDIA_BUCKET/$fileName"
        } catch (e: Exception) {
            Log.e("UPLOAD_ERROR", "Failed to upload image $fileName", e)
            throw Exception("${e.message ?: "Unknown error"}")
        }
    }

    // ─── Upload single video ──────────────────────────────────────────────
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
            try {
                val videoUrl = uploadVideoToSupabase(selectedVideoUri!!, fileName)
                if (videoUrl == null) {
                    Toast.makeText(this@AddSkillActivity, "Video upload failed", Toast.LENGTH_LONG).show()
                    btnPublish.isEnabled = true
                    progressBar.visibility = View.GONE
                    return@launch
                }
                val skill = Skill(
                    id = skillId,
                    userId = userEmail,
                    userName = userName,
                    title = title,
                    description = description,
                    category = category,
                    duration = duration,
                    credits = credits,
                    timestamp = System.currentTimeMillis(),
                    videoUrl = videoUrl,
                    skillType = "single",
                    videos = null,
                    thumbnailUrl = null,
                    demoVideoUrl = null,
                    videoCount = 0,
                    totalDuration = duration
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
            } catch (e: Exception) {
                Toast.makeText(this@AddSkillActivity, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                btnPublish.isEnabled = true
                progressBar.visibility = View.GONE
            }
        }
    }

    // ─── Upload playlist ──────────────────────────────────────────────────
    private fun uploadPlaylistAndSave(
        userEmail: String,
        userName: String,
        title: String,
        category: String,
        description: String,
        duration: String,
        credits: Int,
        btnPublish: MaterialButton
    ) {
        val skillId = db.collection("skills").document().id

        CoroutineScope(Dispatchers.Main).launch {
            btnPublish.isEnabled = false
            btnPublish.text = "Uploading..."

            try {
                // 1. Upload demo video
                val demoFileName = "playlist_demos/${skillId}_demo.mp4"
                val demoUrl = uploadVideoToSupabase(selectedDemoUri!!, demoFileName)
                if (demoUrl == null) {
                    Toast.makeText(this@AddSkillActivity, "Demo upload failed", Toast.LENGTH_LONG).show()
                    btnPublish.isEnabled = true
                    btnPublish.text = "Create Playlist"
                    return@launch
                }

                // 2. Upload thumbnail (if any)
                var thumbUrl: String? = null
                if (selectedThumbnailUri != null) {
                    try {
                        val thumbFileName = "playlist_thumbs/${skillId}.jpg"
                        thumbUrl = uploadImageToSupabase(selectedThumbnailUri!!, thumbFileName)
                    } catch (e: Exception) {
                        Toast.makeText(
                            this@AddSkillActivity,
                            "Thumbnail upload failed: ${e.message}. Continuing without it.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                // 3. Build skill object (videos already have URLs from AddPlaylistVideoActivity)
                val skill = Skill(
                    id = skillId,
                    userId = userEmail,
                    userName = userName,
                    title = title,
                    description = description,
                    category = category,
                    duration = duration,
                    credits = credits,
                    timestamp = System.currentTimeMillis(),
                    videoUrl = null,
                    skillType = "playlist",
                    videos = playlistVideos.toList(),
                    thumbnailUrl = thumbUrl,
                    demoVideoUrl = demoUrl,
                    videoCount = playlistVideos.size,
                    totalDuration = duration
                )

                db.collection("skills").document(skillId).set(skill)
                    .addOnSuccessListener {
                        Toast.makeText(this@AddSkillActivity, "Playlist published!", Toast.LENGTH_SHORT).show()
                        PlaylistManager.notifyPlaylistCreated(userEmail, title)
                        finish()
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this@AddSkillActivity, "DB error: ${e.message}", Toast.LENGTH_LONG).show()
                        btnPublish.isEnabled = true
                        btnPublish.text = "Create Playlist"
                    }
            } catch (e: Exception) {
                Toast.makeText(this@AddSkillActivity, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
                btnPublish.isEnabled = true
                btnPublish.text = "Create Playlist"
            }
        }
    }

    // ─── Inner Adapter for Playlist Videos ──────────────────────────────
    inner class PlaylistVideoListAdapter(
        private val videos: List<PlaylistVideo>,
        private val onDelete: (Int) -> Unit
    ) : RecyclerView.Adapter<PlaylistVideoListAdapter.ViewHolder>() {

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivThumbnail: ImageView = itemView.findViewById(R.id.ivThumbnail)
            val tvTitle: TextView = itemView.findViewById(R.id.tvTitle)
            val tvDuration: TextView = itemView.findViewById(R.id.tvDuration)
            val tvCredits: TextView = itemView.findViewById(R.id.tvCredits)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btnDelete)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_playlist_video_list, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val video = videos[position]
            holder.tvTitle.text = video.title
            holder.tvDuration.text = video.duration.ifBlank { "--" }
            holder.tvCredits.text = "${video.credits} credits"

            holder.tvTitle.setTextColor(currentTextPrimary())
            holder.tvDuration.setTextColor(currentTextSecondary())
            holder.tvCredits.setTextColor(currentTextSecondary())

            // Placeholder thumbnail – load from videoUrl if available
            if (video.videoUrl.isNotBlank()) {
                Glide.with(holder.itemView.context)
                    .load(video.videoUrl)
                    .placeholder(R.drawable.baseline_videocam_24)
                    .error(R.drawable.baseline_videocam_24)
                    .into(holder.ivThumbnail)
            } else {
                holder.ivThumbnail.setImageResource(R.drawable.baseline_videocam_24)
            }

            holder.btnDelete.setOnClickListener { onDelete(position) }
        }

        override fun getItemCount(): Int = videos.size
    }
}