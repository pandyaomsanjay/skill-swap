package com.example.sgp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
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
    private var uid: String? = null

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
        currentUserEmail = FirebaseAuth.getInstance().currentUser?.email
        uid = FirebaseAuth.getInstance().currentUser?.uid

        bindViews()
        setupToolbar()
        loadPlaylist()
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

        recyclerView.layoutManager = LinearLayoutManager(this)
        // Adapter will be set after data loads
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

        // Thumbnail
        if (!playlist.thumbnailUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(playlist.thumbnailUrl)
                .placeholder(R.drawable.baseline_videocam_24)
                .error(R.drawable.baseline_videocam_24)
                .into(ivThumbnail)
        }

        // Demo button
        if (!playlist.demoVideoUrl.isNullOrEmpty()) {
            btnPlayDemo.visibility = View.VISIBLE
            btnPlayDemo.setOnClickListener { playDemoVideo() }
        } else {
            btnPlayDemo.visibility = View.GONE
        }

        // Videos list
        val videos = playlist.videos ?: emptyList()
        if (videos.isNotEmpty()) {
            adapter = PlaylistVideoAdapter(videos) { video ->
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
        if (currentUserEmail == null || uid == null) {
            btnAccessPlaylist.text = "Log in to Access"
            btnAccessPlaylist.setOnClickListener {
                startActivity(Intent(this, Login::class.java))
            }
            return
        }

        // First check if user is admin
        db.collection("users").document(uid!!).get()
            .addOnSuccessListener { doc ->
                val userType = doc.getString("userType") ?: "standard"
                isAdmin = userType == "admin"
                if (isAdmin) {
                    // Admin has full access – no purchase needed
                    hasAccess = true
                    btnAccessPlaylist.text = "Admin Access"
                    btnAccessPlaylist.isEnabled = false
                    btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.brand_navy)
                    // If videos were already loaded, we can now enable them (adapter already set)
                    return@addOnSuccessListener
                }
                // Else proceed with regular check
                checkRegularAccess()
            }
            .addOnFailureListener {
                // Fallback to regular check
                checkRegularAccess()
            }
    }

    private fun checkRegularAccess() {
        // Check if already purchased
        PlaylistManager.hasPurchased(uid!!, skillId) { purchased ->
            if (purchased) {
                hasAccess = true
                btnAccessPlaylist.text = "Access Granted"
                btnAccessPlaylist.isEnabled = false
                btnAccessPlaylist.backgroundTintList = ContextCompat.getColorStateList(this, R.color.success)
            } else {
                // Check credits
                db.collection("users").document(uid!!).get()
                    .addOnSuccessListener { doc ->
                        currentUserCredits = doc.getLong("credits")?.toInt() ?: 0
                        updateAccessButton()
                    }
                    .addOnFailureListener { updateAccessButton() }
            }
        }
    }

    private fun updateAccessButton() {
        if (currentUserCredits >= playlist.credits) {
            btnAccessPlaylist.text = "Access for ${playlist.credits} credits"
            btnAccessPlaylist.setOnClickListener { purchaseAccess() }
            btnAccessPlaylist.isEnabled = true
        } else {
            btnAccessPlaylist.text = "Not enough credits (need ${playlist.credits})"
            btnAccessPlaylist.isEnabled = false
        }
    }

    private fun purchaseAccess() {
        val userUid = uid ?: return
        val price = playlist.credits

        // Show loading state
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
            },
            onFailure = { e ->
                btnAccessPlaylist.isEnabled = true
                val message = when {
                    e.message?.contains("Insufficient credits") == true -> "Insufficient credits"
                    e.message?.contains("Already purchased") == true -> "Already unlocked"
                    else -> "Purchase failed: ${e.message}"
                }
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                // Refresh button state
                updateAccessButton()
            }
        )
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
            // No playlist tracking for demo
            startActivity(intent)
        } ?: run {
            Toast.makeText(this, "Demo video not available", Toast.LENGTH_SHORT).show()
        }
    }

    private fun playVideo(video: PlaylistVideo) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("videoUrl", video.videoUrl)
        intent.putExtra("skillTitle", playlist.title)
        intent.putExtra("skillCategory", playlist.category)
        intent.putExtra("skillUserId", playlist.userId)
        intent.putExtra("skillUserName", playlist.userName)
        intent.putExtra("skillCredits", 0)
        intent.putExtra("skillDescription", "Playlist video")
        // Pass playlist tracking data
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