package com.example.sgp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
import java.util.Locale

open class PlaylistActivity : AppCompatActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var skillId: String
    private lateinit var playlist: Skill
    private var currentUserEmail: String? = null
    private var currentUserCredits: Int = 0
    private var hasAccess = false
    private var isAdmin = false
    private var isOwner = false
    private var uid: String? = null

    private var completedVideoIds: Set<String> = emptySet()

    // Views
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

    private lateinit var progressIndicatorWatched: LinearProgressIndicator
    private lateinit var tvProgressLabel: TextView

    companion object {
        private const val EXTRA_SKILL_ID = "skillId"
        fun start(context: Context, skillId: String) {
            val intent = Intent(context, PlaylistActivity::class.java)
            intent.putExtra(EXTRA_SKILL_ID, skillId)
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_playlist)

        skillId = intent.getStringExtra(EXTRA_SKILL_ID) ?: run {
            Toast.makeText(this, "Invalid playlist", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        db = Firebase.firestore

        bindViews()
        setupToolbar()
        loadPlaylist()
    }

    override fun onResume() {
        super.onResume()
        if (::playlist.isInitialized && hasAccess && !isOwner) {
            loadProgressForLearner()
        }
    }

    private fun bindViews() {
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

        progressIndicatorWatched = findViewById(R.id.progressIndicatorWatched)
        tvProgressLabel = findViewById(R.id.tvProgressLabel)
        progressIndicatorWatched.visibility = View.GONE
        tvProgressLabel.visibility = View.GONE

        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""
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
            adapter = PlaylistVideoAdapter(videos, completedVideoIds) { video ->
                if (hasAccess) {
                    playVideo(video)
                } else {
                    Toast.makeText(this, "Please access the playlist first", Toast.LENGTH_SHORT).show()
                }
            }
            recyclerView.adapter = adapter
            emptyState.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        } else {
            emptyState.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        }
    }

    // ─────────────────────── Access Control ───────────────────────

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
        Log.d("PlaylistActivity", "currentUserEmail: $currentUserEmail")
        Log.d("PlaylistActivity", "uid: $uid")

        val resolvedUid = uid
        if (currentUserEmail == null || resolvedUid == null) {
            Log.e("PlaylistActivity", "❌ UID or email is null")
            showLoggedOutState()
            return
        }

        Log.d("PlaylistActivity", "✅ Continuing access check for user: $currentUserEmail, uid: $resolvedUid")

        // Check if user is the owner of this playlist
        if (currentUserEmail == playlist.userId) {
            isOwner = true
            hasAccess = true
            btnAccessPlaylist.text = "Your Upload — Free Access"
            btnAccessPlaylist.isEnabled = false
            btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            showOwnerAccessCount()
            bindVideosList()
            return
        }

        // Check if user is admin
        db.collection("users").document(resolvedUid).get()
            .addOnSuccessListener { doc ->
                val userType = doc.getString("userType") ?: "standard"
                isAdmin = userType == "admin"
                Log.d("PlaylistActivity", "User type: $userType, isAdmin: $isAdmin")
                if (isAdmin) {
                    hasAccess = true
                    btnAccessPlaylist.text = "Admin Access"
                    btnAccessPlaylist.isEnabled = false
                    btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_navy)
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
        Log.d("PlaylistActivity", "=== checkRegularAccess ===")
        val resolvedUid = uid ?: run {
            Log.e("PlaylistActivity", "UID is null in checkRegularAccess")
            showLoggedOutState()
            return
        }

        PlaylistManager.hasPurchased(resolvedUid, skillId) { purchased ->
            Log.d("PlaylistActivity", "Has purchased: $purchased")
            if (purchased) {
                hasAccess = true
                btnAccessPlaylist.text = "Access Granted"
                btnAccessPlaylist.isEnabled = false
                btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
                loadProgressForLearner()
            } else {
                db.collection("users").document(resolvedUid).get()
                    .addOnSuccessListener { doc ->
                        currentUserCredits = doc.getLong("credits")?.toInt() ?: 0
                        Log.d("PlaylistActivity", "User credits: $currentUserCredits")
                        updateAccessButton()
                    }
                    .addOnFailureListener { e ->
                        Log.e("PlaylistActivity", "Failed to get user credits: ${e.message}", e)
                        updateAccessButton()
                    }
            }
        }
    }

    private fun updateAccessButton() {
        Log.d("PlaylistActivity", "=== updateAccessButton ===")
        Log.d("PlaylistActivity", "currentUserCredits: $currentUserCredits, needed: ${playlist.credits}")

        if (currentUserCredits >= playlist.credits) {
            btnAccessPlaylist.text = "Access for ${playlist.credits} credits"
            btnAccessPlaylist.setOnClickListener { purchaseAccess() }
            btnAccessPlaylist.isEnabled = true
            Log.d("PlaylistActivity", "Button enabled: Access for ${playlist.credits} credits")
        } else {
            btnAccessPlaylist.text = "Not enough credits (need ${playlist.credits})"
            btnAccessPlaylist.isEnabled = false
            Log.d("PlaylistActivity", "Button disabled: Not enough credits")
        }
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
                        Toast.makeText(this, "Playlist unlocked!", Toast.LENGTH_SHORT).show()
                        btnAccessPlaylist.text = "Access Granted"
                        btnAccessPlaylist.isEnabled = false
                        btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
                        loadProgressForLearner()
                    },
                    onFailure = { e ->
                        btnAccessPlaylist.isEnabled = true
                        val message = when {
                            e.message?.contains("Insufficient credits") == true -> "Insufficient credits"
                            e.message?.contains("Already purchased") == true -> "Already unlocked"
                            else -> "Purchase failed: ${e.message}"
                        }
                        Log.e("PlaylistActivity", "Purchase error: ${e.message}", e)
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        updateAccessButton()
                    }
                )
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to check credits: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // ─────────────────────── Progress ───────────────────────

    private fun loadProgressForLearner() {
        val resolvedUid = uid ?: return
        if (isOwner) return

        PlaylistManager.getProgress(resolvedUid, skillId) { progress ->
            val completed = progress?.completedVideos?.toSet() ?: emptySet()
            completedVideoIds = completed

            val total = playlist.videoCount.takeIf { it > 0 } ?: 1
            val percent = (completed.size * 100) / total

            progressIndicatorWatched.visibility = View.VISIBLE
            tvProgressLabel.visibility = View.VISIBLE
            progressIndicatorWatched.progress = percent
            tvProgressLabel.text = "${completed.size} of ${playlist.videoCount} videos completed · $percent%"

            // 🟢 MAKE PROGRESS BAR GREEN WHEN 100% COMPLETE
            if (percent >= 100) {
                progressIndicatorWatched.setIndicatorColor(
                    ContextCompat.getColor(this, R.color.success)
                )
                tvProgressLabel.setTextColor(
                    ContextCompat.getColor(this, R.color.success)
                )
            } else {
                // Default brand color
                progressIndicatorWatched.setIndicatorColor(
                    ContextCompat.getColor(this, R.color.brand_navy)
                )
                tvProgressLabel.setTextColor(
                    ContextCompat.getColor(this, R.color.text_secondary)
                )
            }

            bindVideosList()
        }
    }

    // ─────────────────────── Video Playback ───────────────────────

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
        Log.d("PlaylistActivity", "========================================")
        Log.d("PlaylistActivity", "▶️ Playing video:")
        Log.d("PlaylistActivity", "  playlistId: ${playlist.id}")
        Log.d("PlaylistActivity", "  videoId: ${video.id}")
        Log.d("PlaylistActivity", "  totalVideos: ${playlist.videoCount}")
        Log.d("PlaylistActivity", "========================================")

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

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) finish()
        return super.onOptionsItemSelected(item)
    }
}