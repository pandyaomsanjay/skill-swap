package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

open class PlaylistActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var skillId: String
    private lateinit var playlist: Skill
    private var currentUserEmail: String? = null
    private var currentUserCredits: Int = 0
    private var hasAccess = false          // full playlist access (owner/admin/purchased)
    private var isAdmin = false
    private var isOwner = false
    private var uid: String? = null

    // NEW: individual video ownership state
    private val ownedVideoIds: MutableSet<String> = mutableSetOf()

    private var completedVideoIds: Set<String> = emptySet()

    // Views
    private lateinit var rootLayout: View
    private lateinit var thumbnailCard: MaterialCardView
    private lateinit var ivThumbnail: ImageView
    private lateinit var tvTitle: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvCreator: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvVideoCount: TextView
    private lateinit var tvTotalDuration: TextView
    private lateinit var tvCreditsRequired: TextView
    private lateinit var btnPlayDemo: Button
    private lateinit var btnAccessPlaylist: Button
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PlaylistVideoAdapter
    private lateinit var emptyState: View
    private lateinit var tvEmptyVideosMessage: TextView
    private lateinit var dividerBeforeVideos: View
    private lateinit var tvVideosTitle: TextView

    private lateinit var btnReport: View
    private lateinit var btnFeedback: View

    private lateinit var progressIndicatorWatched: LinearProgressIndicator
    private lateinit var tvProgressLabel: TextView

    private lateinit var sharedPreferences: SharedPreferences
    private var isDarkModeEnabled = false

    companion object {
        private const val EXTRA_SKILL_ID = "skillId"
        private const val PREFS_NAME = "SkillSwapPrefs"
        private const val KEY_DARK_MODE = "dark_mode"

        fun start(context: Context, skillId: String) {
            val intent = Intent(context, PlaylistActivity::class.java)
            intent.putExtra(EXTRA_SKILL_ID, skillId)
            context.startActivity(intent)
        }
    }

    // Light palette
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightHeaderBg = Color.parseColor("#1B3C53")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightNavIconTint = Color.BLACK

    // Dark palette
    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkHeaderBg = Color.parseColor("#0B1620")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkNavIconTint = Color.WHITE

    // Report
    private val reportReasons = arrayOf(
        "Inappropriate or Offensive Content",
        "Spam or Misleading Information",
        "Fake Skill or Scam",
        "Harassment or Abusive Behavior",
        "Copyright or Intellectual Property Violation"
    )

    // Feedback
    private var selectedFeedbackRating = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        skillId = intent.getStringExtra(EXTRA_SKILL_ID) ?: run {
            Toast.makeText(this, "Invalid playlist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = Firebase.firestore
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = sharedPreferences.getBoolean(KEY_DARK_MODE, false)

        bindViews()
        setupToolbar()
        applyTheme(isDarkModeEnabled)
        loadPlaylist()
    }

    override fun onResume() {
        super.onResume()
        val storedDarkMode = sharedPreferences.getBoolean(KEY_DARK_MODE, false)
        if (storedDarkMode != isDarkModeEnabled) {
            applyTheme(storedDarkMode)
        }
        if (::playlist.isInitialized && (hasAccess || ownedVideoIds.isNotEmpty()) && !isOwner) {
            loadProgressForLearner()
        }
    }

    private fun bindViews() {
        rootLayout = findViewById(R.id.rootLayout)
        thumbnailCard = findViewById(R.id.thumbnailCard)
        ivThumbnail = findViewById(R.id.ivPlaylistThumbnail)
        tvTitle = findViewById(R.id.tvPlaylistTitle)
        tvCategory = findViewById(R.id.tvPlaylistCategory)
        tvCreator = findViewById(R.id.tvPlaylistCreator)
        tvDescription = findViewById(R.id.tvPlaylistDescription)
        tvVideoCount = findViewById(R.id.tvPlaylistVideoCount)
        tvTotalDuration = findViewById(R.id.tvPlaylistTotalDuration)
        tvCreditsRequired = findViewById(R.id.tvPlaylistCreditsRequired)
        btnPlayDemo = findViewById(R.id.btnPlayDemo)
        btnAccessPlaylist = findViewById(R.id.btnAccessPlaylist)
        progressBar = findViewById(R.id.progressBar)
        recyclerView = findViewById(R.id.recyclerViewPlaylistVideos)
        emptyState = findViewById(R.id.emptyStateVideos)
        tvEmptyVideosMessage = findViewById(R.id.tvEmptyVideosMessage)
        dividerBeforeVideos = findViewById(R.id.dividerBeforeVideos)
        tvVideosTitle = findViewById(R.id.tvVideosTitle)
        btnReport = findViewById(R.id.btnReport)
        btnFeedback = findViewById(R.id.btnFeedback)

        progressIndicatorWatched = findViewById(R.id.progressIndicatorWatched)
        tvProgressLabel = findViewById(R.id.tvProgressLabel)
        progressIndicatorWatched.visibility = View.GONE
        tvProgressLabel.visibility = View.GONE

        recyclerView.layoutManager = LinearLayoutManager(this)

        btnReport.setOnClickListener {
            if (::playlist.isInitialized) {
                showReportDialog()
            } else {
                Toast.makeText(this, "Please wait for the playlist to load", Toast.LENGTH_SHORT).show()
            }
        }

        btnFeedback.setOnClickListener {
            if (::playlist.isInitialized) {
                showFeedbackDialog()
            } else {
                Toast.makeText(this, "Please wait for the playlist to load", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // THEME
    // ─────────────────────────────────────────────────────────────
    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark

        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg
        val cardStroke = if (isDark) darkCardStroke else lightCardStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val navIconTint = if (isDark) darkNavIconTint else lightNavIconTint

        rootLayout.setBackgroundColor(screenBg)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar?.apply {
            setBackgroundColor(headerBg)
            navigationIcon?.setTint(navIconTint)
        }

        thumbnailCard.strokeColor = cardStroke

        tvTitle.setTextColor(textPrimary)
        tvCategory.setTextColor(textSecondary)
        tvDescription.setTextColor(textSecondary)
        tvVideoCount.setTextColor(textPrimary)
        tvTotalDuration.setTextColor(textPrimary)
        tvProgressLabel.setTextColor(textPrimary)
        dividerBeforeVideos.setBackgroundColor(cardStroke)
        tvVideosTitle.setTextColor(textPrimary)
        tvEmptyVideosMessage.setTextColor(textSecondary)
        progressIndicatorWatched.trackColor = cardStroke

        if (::adapter.isInitialized) {
            adapter.isDarkMode = isDark
        }
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(false)
        supportActionBar?.title = ""
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadPlaylist() {
        progressBar.visibility = View.VISIBLE
        db.collection("skills").document(skillId).get()
            .addOnSuccessListener { doc ->
                val skill = doc.toObject(Skill::class.java)
                if (skill == null || skill.skillType != "playlist") {
                    Toast.makeText(this, "Playlist not found", Toast.LENGTH_SHORT).show()
                    finish()
                    return@addOnSuccessListener
                }
                playlist = skill
                bindPlaylistData()
                checkAccess()
                progressBar.visibility = View.GONE

                val firebaseUser = FirebaseAuth.getInstance().currentUser
                val isOwnUpload = firebaseUser?.email != null && firebaseUser.email == playlist.userId
                btnReport.visibility = if (isOwnUpload) View.GONE else View.VISIBLE
                btnFeedback.visibility = if (isOwnUpload) View.GONE else View.VISIBLE
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                Toast.makeText(this, "Failed to load playlist: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun bindPlaylistData() {
        tvTitle.text = playlist.title
        tvCategory.text = playlist.category
        tvCreator.text = "by ${playlist.userName}"
        tvDescription.text = playlist.description.ifBlank { "No description" }
        tvVideoCount.text = "${playlist.videoCount} videos"
        tvTotalDuration.text = playlist.totalDuration ?: "—"
        tvCreditsRequired.text = "${playlist.credits} credits"

        if (!playlist.thumbnailUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(playlist.thumbnailUrl)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(ivThumbnail)
        }

        if (!playlist.demoVideoUrl.isNullOrEmpty()) {
            btnPlayDemo.visibility = View.VISIBLE
            btnPlayDemo.setOnClickListener { playDemoVideo() }
        } else {
            btnPlayDemo.visibility = View.GONE
        }

        bindVideosList()
    }

    private fun bindVideosList() {
        val videos = playlist.videos ?: emptyList()
        if (videos.isNotEmpty()) {
            adapter = PlaylistVideoAdapter(
                videos = videos,
                completedVideoIds = completedVideoIds,
                isOwner = isOwner,
                onVideoClick = { video -> playVideo(video) },
                onReportClick = { video -> showReportDialogForVideo(video) },
                onFeedbackClick = { video -> showFeedbackDialogForVideo(video) },
                onBuyClick = { video -> showPurchaseVideoDialog(video) }
            )

            adapter.isDarkMode = isDarkModeEnabled
            adapter.fullPlaylistOwned = hasAccess
            adapter.ownedVideoIds = ownedVideoIds.toSet()

            recyclerView.adapter = adapter
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    // ═════════════════════════════════════════════════════════════
    // ACCESS CONTROL
    // ═════════════════════════════════════════════════════════════

    private fun checkAccess() {
        Log.d("PlaylistActivity", "=== checkAccess START ===")

        val firebaseUser = FirebaseAuth.getInstance().currentUser

        if (firebaseUser != null) {
            currentUserEmail = firebaseUser.email
            uid = firebaseUser.uid
            Log.d("PlaylistActivity", "✅ User found: ${firebaseUser.email}, uid: ${firebaseUser.uid}")
            continueAccessCheckAfterIdentityResolved()
        } else {
            Log.d("PlaylistActivity", "❌ No Firebase user found")
            showLoggedOutState()
        }
    }

    private fun showLoggedOutState() {
        Log.d("PlaylistActivity", "=== showLoggedOutState ===")
        btnAccessPlaylist.text = "Log in to Access"
        btnAccessPlaylist.isEnabled = true
        btnAccessPlaylist.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }
    }

    private fun continueAccessCheckAfterIdentityResolved() {
        Log.d("PlaylistActivity", "=== continueAccessCheckAfterIdentityResolved ===")

        val resolvedUid = uid
        if (currentUserEmail == null || resolvedUid == null) {
            Log.e("PlaylistActivity", "❌ UID or email is null")
            showLoggedOutState()
            return
        }

        if (currentUserEmail == playlist.userId) {
            isOwner = true
            hasAccess = true
            btnAccessPlaylist.text = "Your Upload — Free Access"
            btnAccessPlaylist.isEnabled = false
            btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            showOwnerAccessCount()
            // Still fetch individual ownership for consistency (should be empty)
            loadOwnedVideoIds()
            return
        }

        db.collection("users").document(resolvedUid).get()
            .addOnSuccessListener { doc ->
                val userType = doc.getString("userType") ?: "standard"
                isAdmin = userType == "admin"
                if (isAdmin) {
                    hasAccess = true
                    btnAccessPlaylist.text = "Admin Access"
                    btnAccessPlaylist.isEnabled = false
                    btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_navy)
                    loadOwnedVideoIds()
                    loadProgressForLearner()
                    return@addOnSuccessListener
                }
                checkRegularAccess()
            }
            .addOnFailureListener { e ->
                Log.e("PlaylistActivity", "Failed to get user type: ${e.message}", e)
                checkRegularAccess()
            }
    }

    private fun showOwnerAccessCount() {
        val count = playlist.accessCount
        tvCreditsRequired.text = if (count == 1) {
            "1 person has access"
        } else {
            "$count people have access"
        }
    }

    private fun checkRegularAccess() {
        val resolvedUid = uid ?: run {
            showLoggedOutState()
            return
        }

        PlaylistManager.hasPurchased(resolvedUid, skillId) { purchased ->
            if (purchased) {
                hasAccess = true
                btnAccessPlaylist.text = "Access Granted"
                btnAccessPlaylist.isEnabled = false
                btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
                // Fetch individual ownership, then load progress
                loadOwnedVideoIds {
                    loadProgressForLearner()
                }
            } else {
                // Not a full playlist owner — still fetch individual ownership
                loadOwnedVideoIds {
                    if (ownedVideoIds.isNotEmpty()) {
                        loadProgressForLearner()
                    } else {
                        // No access at all — check credits so we can show the buy button
                        db.collection("users").document(resolvedUid).get()
                            .addOnSuccessListener { doc ->
                                currentUserCredits = doc.getLong("credits")?.toInt() ?: 0
                                updateAccessButton()
                            }
                            .addOnFailureListener { e ->
                                Log.e("PlaylistActivity", "Failed to get user credits: ${e.message}", e)
                                updateAccessButton()
                            }
                    }
                }
            }
        }
    }

    /** Loads the set of individually purchased video IDs for this playlist. */
    private fun loadOwnedVideoIds(onDone: (() -> Unit)? = null) {
        val resolvedUid = uid ?: return
        PlaylistManager.getOwnedVideoIds(resolvedUid, skillId) { ids ->
            ownedVideoIds.clear()
            ownedVideoIds.addAll(ids)
            // Re-bind so adapter picks up the new ownership state
            if (::playlist.isInitialized) {
                bindVideosList()
            }
            onDone?.invoke()
        }
    }

    private fun updateAccessButton() {
        if (currentUserCredits >= playlist.credits) {
            btnAccessPlaylist.text = "Access for ${playlist.credits} credits"
            btnAccessPlaylist.setOnClickListener { showPurchaseConfirmDialog() }
            btnAccessPlaylist.isEnabled = true
        } else {
            btnAccessPlaylist.text = "Not enough credits — Buy Credits"
            btnAccessPlaylist.isEnabled = true
            btnAccessPlaylist.setOnClickListener {
                startActivity(Intent(this, CreditStoreActivity::class.java))
            }
        }
    }

    // ═════════════════════════════════════════════════════════════
    // FULL PLAYLIST PURCHASE (existing flow preserved)
    // ═════════════════════════════════════════════════════════════

    private fun showPurchaseConfirmDialog() {
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "🔓"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = "Unlock \"${playlist.title}\"?"
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })
        root.addView(TextView(this).apply {
            text = "This will use ${playlist.credits} credits from your account. This action cannot be undone."
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
        val btnCancel = pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
            setOnClickListener { dialog.dismiss() }
        }
        val btnPurchase = pillButton("Purchase", Color.parseColor("#2E9E63"), Color.WHITE).apply {
            setOnClickListener {
                dialog.dismiss()
                purchaseAccess()
            }
        }
        buttonRow.addView(
            btnCancel,
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
        )
        buttonRow.addView(btnPurchase, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(buttonRow)

        dialog.show()
    }

    private fun purchaseAccess() {
        if (isOwner) {
            hasAccess = true
            return
        }

        val userUid = uid ?: run {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }
        val price = playlist.credits

        db.collection("users").document(userUid).get()
            .addOnSuccessListener { doc ->
                val credits = doc.getLong("credits")?.toInt() ?: 0
                if (credits < price) {
                    Toast.makeText(this, "Insufficient credits! You have $credits, need $price", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }

                btnAccessPlaylist.isEnabled = false
                btnAccessPlaylist.text = "Processing..."

                PlaylistManager.purchasePlaylist(
                    uid = userUid,
                    playlistId = playlist.id,
                    price = price,
                    playlistTitle = playlist.title,
                    onSuccess = {
                        hasAccess = true
                        currentUserCredits -= price

                        PointsLedger.recordSpent(
                            userId = currentUserEmail ?: FirebaseAuth.getInstance().currentUser?.email ?: "",
                            points = price,
                            title = "Bought playlist: ${playlist.title}",
                            refId = playlist.id
                        )

                        Toast.makeText(this, "Playlist unlocked!", Toast.LENGTH_SHORT).show()
                        btnAccessPlaylist.text = "Access Granted"
                        btnAccessPlaylist.isEnabled = false
                        btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)

                        // Re-bind so every video now shows as owned
                        bindVideosList()
                        loadProgressForLearner()
                    },
                    onFailure = { e ->
                        btnAccessPlaylist.isEnabled = true
                        val message = when {
                            e.message?.contains("Insufficient credits") == true -> "Insufficient credits"
                            e.message?.contains("Already purchased") == true -> "Already unlocked"
                            else -> "Purchase failed: ${e.message}"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        updateAccessButton()
                    }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to check credits: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ═════════════════════════════════════════════════════════════
    // INDIVIDUAL VIDEO PURCHASE  (new)
    // ═════════════════════════════════════════════════════════════

    private fun showPurchaseVideoDialog(video: PlaylistVideo) {
        val userUid = uid ?: run {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show()
            return
        }

        // Free videos — grant access immediately (defensive; adapter should
        // already treat them as watchable)
        if (video.credits <= 0) {
            ownedVideoIds.add(video.id)
            bindVideosList()
            Toast.makeText(this, "Video is free to watch", Toast.LENGTH_SHORT).show()
            return
        }

        if (video.id in ownedVideoIds) {
            Toast.makeText(this, "You already own this video", Toast.LENGTH_SHORT).show()
            return
        }

        // Pre-check balance before showing the dialog so we can warn early
        db.collection("users").document(userUid).get()
            .addOnSuccessListener { doc ->
                val balance = doc.getLong("credits")?.toInt() ?: 0
                if (balance < video.credits) {
                    Toast.makeText(
                        this,
                        "Not enough credits. You have $balance, need ${video.credits}.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@addOnSuccessListener
                }

                val root = dialogCard()

                root.addView(TextView(this).apply {
                    text = "🔓"
                    textSize = 30f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8) }
                })
                root.addView(TextView(this).apply {
                    text = "Unlock this video?"
                    setTextColor(currentTextPrimary())
                    textSize = 17f
                    setTypeface(typeface, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
                root.addView(TextView(this).apply {
                    text = video.title.ifBlank { "Video" }
                    setTextColor(currentTextPrimary())
                    textSize = 14f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { topMargin = dp(4) }
                })
                root.addView(TextView(this).apply {
                    text = "This will use ${video.credits} credits from your account.\n\nYour balance: $balance credits"
                    setTextColor(currentTextSecondary())
                    textSize = 13f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(10)
                        bottomMargin = dp(18)
                    }
                })

                val dialog = AlertDialog.Builder(this).setView(root).create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                val buttonRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                val btnCancel = pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
                    setOnClickListener { dialog.dismiss() }
                }
                val btnBuy = pillButton("Buy for ${video.credits}", Color.parseColor("#7C3AED"), Color.WHITE).apply {
                    setOnClickListener {
                        dialog.dismiss()
                        performVideoPurchase(video, video.credits)
                    }
                }
                buttonRow.addView(
                    btnCancel,
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
                )
                buttonRow.addView(btnBuy, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                root.addView(buttonRow)

                dialog.show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to check balance: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun performVideoPurchase(video: PlaylistVideo, price: Int) {
        val userUid = uid ?: return
        val userEmail = currentUserEmail ?: FirebaseAuth.getInstance().currentUser?.email.orEmpty()

        // Disable all buy buttons during the network call by disabling the
        // adapter temporarily — simplest way to prevent double-clicks.
        btnAccessPlaylist.isEnabled = false

        PlaylistManager.purchaseVideo(
            uid = userUid,
            playlistId = skillId,
            videoId = video.id,
            price = price,
            videoTitle = video.title,
            onSuccess = {
                runOnUiThread {
                    btnAccessPlaylist.isEnabled = true

                    // Update local state
                    ownedVideoIds.add(video.id)
                    currentUserCredits -= price

                    // Record in points ledger (matches purchasePlaylist pattern)
                    PointsLedger.recordSpent(
                        userId = userEmail,
                        points = price,
                        title = "Bought video: ${video.title}",
                        refId = "${skillId}__${video.id}"
                    )

                    // Re-bind so the new ownership state is reflected
                    bindVideosList()
                    loadProgressForLearner()

                    Toast.makeText(this, "Video unlocked!", Toast.LENGTH_SHORT).show()
                }
            },
            onFailure = { e ->
                runOnUiThread {
                    btnAccessPlaylist.isEnabled = true
                    val message = when {
                        e.message?.contains("Insufficient", ignoreCase = true) == true ->
                            "Not enough credits for this video"
                        e.message?.contains("Already purchased", ignoreCase = true) == true ->
                            "Already purchased"
                        else -> "Purchase failed: ${e.message}"
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    // ═════════════════════════════════════════════════════════════
    // PROGRESS
    // ═════════════════════════════════════════════════════════════

    private fun loadProgressForLearner() {
        val resolvedUid = uid ?: return
        if (isOwner) return

        // Only show progress if the user has some access
        if (!hasAccess && ownedVideoIds.isEmpty()) {
            progressIndicatorWatched.visibility = View.GONE
            tvProgressLabel.visibility = View.GONE
            return
        }

        PlaylistManager.getProgress(resolvedUid, skillId) { progress ->
            val completed = progress?.completedVideos?.toSet() ?: emptySet()
            completedVideoIds = completed

            val total = playlist.videoCount.takeIf { it > 0 } ?: 1
            val percent = (completed.size * 100) / total

            progressIndicatorWatched.visibility = View.VISIBLE
            tvProgressLabel.visibility = View.VISIBLE
            progressIndicatorWatched.progress = percent
            progressIndicatorWatched.trackColor = if (isDarkModeEnabled) darkCardStroke else lightCardStroke
            tvProgressLabel.text = "${completed.size} of ${playlist.videoCount} videos completed · $percent%"

            if (percent >= 100) {
                progressIndicatorWatched.setIndicatorColor(ContextCompat.getColor(this, R.color.success))
                tvProgressLabel.setTextColor(ContextCompat.getColor(this, R.color.success))
            } else {
                progressIndicatorWatched.setIndicatorColor(ContextCompat.getColor(this, R.color.brand_navy))
                tvProgressLabel.setTextColor(currentTextPrimary())
            }

            bindVideosList()
        }
    }

    // ═════════════════════════════════════════════════════════════
    // VIDEO PLAYBACK  (ownership-gated)
    // ═════════════════════════════════════════════════════════════

    private fun canWatch(video: PlaylistVideo): Boolean {
        if (isOwner) return true
        if (hasAccess) return true
        if (video.id in ownedVideoIds) return true
        if (video.credits <= 0) return true
        return false
    }

    private fun playDemoVideo() {
        playlist.demoVideoUrl?.let { url ->
            val intent = Intent(this, VideoPlayerActivity::class.java)
            intent.putExtra("videoUrl", url)
            intent.putExtra("skillTitle", "Preview: ${playlist.title}")
            intent.putExtra("skillCategory", playlist.category)
            intent.putExtra("skillUserId", playlist.userId)
            intent.putExtra("skillUserName", playlist.userName)
            intent.putExtra("skillCredits", 0)
            intent.putExtra("skillDescription", "Demo video for playlist")
            intent.putExtra("playlistId", playlist.id)
            intent.putExtra("videoId", "demo")
            intent.putExtra("totalVideos", 0)
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "Demo video not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playVideo(video: PlaylistVideo) {
        // Ownership check — the row/button also gates this, but repeat here
        // so any direct call can't bypass it.
        if (!canWatch(video)) {
            Toast.makeText(this, "Purchase this video to watch it", Toast.LENGTH_SHORT).show()
            showPurchaseVideoDialog(video)
            return
        }

        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("videoUrl", video.videoUrl)
        intent.putExtra("skillTitle", playlist.title)
        intent.putExtra("skillCategory", playlist.category)
        intent.putExtra("skillUserId", playlist.userId)
        intent.putExtra("skillUserName", playlist.userName)
        intent.putExtra("skillCredits", 0)
        intent.putExtra("skillDescription", "Playlist video")
        intent.putExtra("skillId", playlist.id)
        intent.putExtra("playlistId", playlist.id)
        intent.putExtra("videoId", video.id)
        intent.putExtra("totalVideos", playlist.videoCount)
        startActivity(intent)
    }

    // ═════════════════════════════════════════════════════════════
    // REPORT / FEEDBACK (existing)
    // ═════════════════════════════════════════════════════════════

    private fun showReportDialog() {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to report", Toast.LENGTH_SHORT).show()
            return
        }
        if (reporterEmail == playlist.userId) {
            Toast.makeText(this, "You can't report your own playlist", Toast.LENGTH_SHORT).show()
            return
        }

        val root = dialogCard()
        root.addView(dialogTitle("Report \"${playlist.title}\""))
        root.addView(TextView(this).apply {
            text = "Help us understand what's wrong"
            setTextColor(currentTextSecondary())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        reportReasons.forEachIndexed { index, reason ->
            root.addView(reportReasonRow(reason) {
                dialog.dismiss()
                submitReport(reason, reporterEmail)
            })
            if (index != reportReasons.lastIndex) {
                root.addView(dividerLine())
            }
        }

        val btnCancel = pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
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
        root.addView(btnCancel)

        dialog.show()
    }

    private fun submitReport(reason: String, reporterEmail: String) {
        val docRef = db.collection("reports").document()
        val report = Report(
            id = docRef.id,
            reporterId = reporterEmail,
            reportedUserId = playlist.userId,
            skillId = playlist.id,
            reason = reason,
            description = "Reported playlist: \"${playlist.title}\" (Playlist ID: ${playlist.id})",
            status = "pending",
            timestamp = System.currentTimeMillis(),
            type = "playlist"
        )
        docRef.set(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted. Our team will review it.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun showReportDialogForVideo(video: PlaylistVideo) {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to report", Toast.LENGTH_SHORT).show()
            return
        }
        if (reporterEmail == playlist.userId) {
            Toast.makeText(this, "You can't report your own video", Toast.LENGTH_SHORT).show()
            return
        }

        val root = dialogCard()
        root.addView(dialogTitle("Report \"${video.title}\""))
        root.addView(TextView(this).apply {
            text = "Help us understand what's wrong with this video"
            setTextColor(currentTextSecondary())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        reportReasons.forEachIndexed { index, reason ->
            root.addView(reportReasonRow(reason) {
                dialog.dismiss()
                submitVideoReport(video, reason, reporterEmail)
            })
            if (index != reportReasons.lastIndex) {
                root.addView(dividerLine())
            }
        }

        val btnCancel = pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
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
        root.addView(btnCancel)

        dialog.show()
    }

    private fun submitVideoReport(video: PlaylistVideo, reason: String, reporterEmail: String) {
        val docRef = db.collection("reports").document()
        val report = Report(
            id = docRef.id,
            reporterId = reporterEmail,
            reportedUserId = playlist.userId,
            skillId = playlist.id,
            reason = reason,
            description = "Reported video \"${video.title}\" (Video ID: ${video.id}) in playlist \"${playlist.title}\" (Playlist ID: ${playlist.id})",
            status = "pending",
            timestamp = System.currentTimeMillis(),
            type = "video"
        )
        docRef.set(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted. Our team will review it.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun reportReasonRow(text: String, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(currentTextPrimary())
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    // ═════════════════════════════════════════════════════════════
    // FEEDBACK (existing)
    // ═════════════════════════════════════════════════════════════

    private fun showFeedbackDialog() {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to leave feedback", Toast.LENGTH_SHORT).show()
            return
        }
        if (reporterEmail == playlist.userId) {
            Toast.makeText(this, "You can't leave feedback on your own playlist", Toast.LENGTH_SHORT).show()
            return
        }
        showFeedbackDialogInternal(
            title = "Give Feedback",
            subtitle = "How was \"${playlist.title}\"?",
            onSubmit = { rating, comment ->
                submitContentFeedback(reporterEmail, videoId = "", rating = rating, comment = comment)
            }
        )
    }

    private fun showFeedbackDialogForVideo(video: PlaylistVideo) {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to leave feedback", Toast.LENGTH_SHORT).show()
            return
        }
        if (reporterEmail == playlist.userId) {
            Toast.makeText(this, "You can't leave feedback on your own video", Toast.LENGTH_SHORT).show()
            return
        }
        showFeedbackDialogInternal(
            title = "Give Feedback",
            subtitle = "How was \"${video.title}\"?",
            onSubmit = { rating, comment ->
                submitContentFeedback(reporterEmail, videoId = video.id, rating = rating, comment = comment)
            }
        )
    }

    private fun showFeedbackDialogInternal(title: String, subtitle: String, onSubmit: (rating: Int, comment: String) -> Unit) {
        selectedFeedbackRating = 0

        val root = dialogCard()
        root.addView(dialogTitle(title))
        root.addView(TextView(this).apply {
            text = subtitle
            setTextColor(currentTextSecondary())
            textSize = 13f
            setPadding(0, dp(4), 0, dp(10))
        })

        val starRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        }
        val starViews = mutableListOf<TextView>()
        for (i in 1..5) {
            val star = TextView(this).apply {
                text = "☆"
                textSize = 28f
                setTextColor(Color.parseColor("#D2C1B6"))
                setPadding(dp(4), 0, dp(4), 0)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedFeedbackRating = i
                    starViews.forEachIndexed { idx, sv ->
                        sv.text = if (idx < i) "★" else "☆"
                        sv.setTextColor(if (idx < i) currentTextPrimary() else Color.parseColor("#D2C1B6"))
                    }
                }
            }
            starViews.add(star)
            starRow.addView(star)
        }
        root.addView(starRow)

        val etComment = EditText(this).apply {
            hint = "Comment (optional)"
            setTextColor(currentTextPrimary())
            setHintTextColor(currentTextSecondary())
            textSize = 13f
            setPadding(dp(4), dp(10), dp(4), dp(10))
            minLines = 2
            maxLines = 4
        }
        root.addView(etComment)
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(4) }
        }
        row.addView(pillButton("Cancel", currentSecondaryBtnBg(), currentSecondaryBtnText()).apply {
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener { dialog.dismiss() }
        })
        row.addView(pillButton("Submit", currentTextPrimary(), Color.parseColor("#F9F3EF")).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = dp(10) }
            setPadding(dp(24), dp(10), dp(24), dp(10))
            setOnClickListener {
                dialog.dismiss()
                onSubmit(selectedFeedbackRating, etComment.text?.toString()?.trim().orEmpty())
            }
        })
        root.addView(row)

        dialog.show()
    }

    private fun submitContentFeedback(reporterEmail: String, videoId: String, rating: Int, comment: String) {
        val reporterName = FirebaseAuth.getInstance().currentUser?.displayName?.ifBlank { reporterEmail } ?: reporterEmail
        val docRef = db.collection("content_feedback").document()
        val feedback = ContentFeedback(
            id = docRef.id,
            skillId = playlist.id,
            videoId = videoId,
            reporterId = reporterEmail,
            reporterName = reporterName,
            targetUserId = playlist.userId,
            rating = rating,
            comment = comment,
            timestamp = System.currentTimeMillis()
        )
        docRef.set(feedback)
            .addOnSuccessListener {
                Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit feedback: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ─────────────────────────────────────────────────────────────
    // Themed-dialog helpers
    // ─────────────────────────────────────────────────────────────
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun currentDialogBg(): Int =
        if (isDarkModeEnabled) Color.parseColor("#16232E") else Color.WHITE

    private fun currentTextPrimary(): Int =
        if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary

    private fun currentTextSecondary(): Int =
        if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary

    private fun currentSecondaryBtnBg(): Int =
        if (isDarkModeEnabled) darkCardStroke else Color.parseColor("#EAF1F5")

    private fun currentSecondaryBtnText(): Int =
        if (isDarkModeEnabled) darkTextPrimary else lightTextSecondary

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

    private fun dividerLine(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(if (isDarkModeEnabled) darkCardStroke else Color.parseColor("#EAF1F5"))
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
}