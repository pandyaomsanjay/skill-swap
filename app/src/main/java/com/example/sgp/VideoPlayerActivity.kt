package com.example.sgp

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Rational
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.MediaController
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class VideoPlayerActivity : AppCompatActivity() {

    private lateinit var videoView: ResizableVideoView
    private lateinit var videoContainer: FrameLayout
    private lateinit var scrollContent: NestedScrollView
    private lateinit var topBar: View
    private lateinit var mediaController: MediaController
    private lateinit var tvFullscreenIcon: TextView
    private lateinit var progressBar: ProgressBar

    private val db: FirebaseFirestore by lazy { Firebase.firestore }

    private var isFullscreen = false
    private var originalVideoContainerParams: ViewGroup.LayoutParams? = null

    // Playlist tracking
    private var playlistId: String? = null
    private var videoId: String? = null
    private var totalVideos: Int = 0
    private var progressMarked = false
    private var videoCompleted = false

    private val reportReasons = arrayOf(
        "Inappropriate or Offensive Content",
        "Spam or Misleading Information",
        "Fake Skill or Scam",
        "Harassment or Abusive Behavior",
        "Copyright or Intellectual Property Violation"
    )

    private var skillId: String = ""
    private var skillTitle: String = ""
    private var skillUserId: String = ""
    private var skillUserName: String = ""

    // Handler for delayed progress update
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_player)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        videoView = findViewById(R.id.videoView)
        videoContainer = findViewById(R.id.videoContainer)
        scrollContent = findViewById(R.id.scrollContent)
        topBar = findViewById(R.id.topBar)
        progressBar = findViewById(R.id.progressBar)
        val tvTitle: TextView = findViewById(R.id.tvVideoTitle)
        val tvSubtitle: TextView = findViewById(R.id.tvVideoSubtitle)
        val tvDescription: TextView = findViewById(R.id.tvVideoDescription)
        val btnClose: View = findViewById(R.id.btnClose)
        val btnFullscreen: View = findViewById(R.id.btnFullscreen)
        val btnPip: View = findViewById(R.id.btnPip)
        val btnReport: View = findViewById(R.id.btnReport)
        tvFullscreenIcon = findViewById(R.id.tvFullscreenIcon)

        originalVideoContainerParams = videoContainer.layoutParams

        val videoUrl = intent.getStringExtra("videoUrl")
        skillId = intent.getStringExtra("skillId") ?: ""
        skillTitle = intent.getStringExtra("skillTitle") ?: "Skill Video"
        skillUserId = intent.getStringExtra("skillUserId") ?: ""
        skillUserName = intent.getStringExtra("skillUserName") ?: "Unknown"
        val category = intent.getStringExtra("skillCategory") ?: ""
        val credits = intent.getIntExtra("skillCredits", 0)
        val description = intent.getStringExtra("skillDescription") ?: ""

        // Playlist progress tracking extras
        playlistId = intent.getStringExtra("playlistId")
        videoId = intent.getStringExtra("videoId")
        totalVideos = intent.getIntExtra("totalVideos", 0)

        Log.d("VideoPlayerActivity", "========================================")
        Log.d("VideoPlayerActivity", "🎬 Video Player Created")
        Log.d("VideoPlayerActivity", "  playlistId: $playlistId")
        Log.d("VideoPlayerActivity", "  videoId: $videoId")
        Log.d("VideoPlayerActivity", "  totalVideos: $totalVideos")
        Log.d("VideoPlayerActivity", "  videoUrl: ${videoUrl?.take(50)}...")
        Log.d("VideoPlayerActivity", "========================================")

        tvTitle.text = skillTitle
        tvSubtitle.text = listOfNotNull(
            category.ifBlank { null },
            "By $skillUserName",
            "$credits credits"
        ).joinToString(" • ")
        tvDescription.text = description.ifBlank { "No description provided." }

        btnClose.setOnClickListener {
            // If video was playing and progress not marked, try to mark it
            if (!progressMarked && videoId != null && playlistId != null) {
                Log.d("VideoPlayerActivity", "Close button pressed - marking progress")
                markProgressIfNeeded()
            }
            finish()
        }
        btnFullscreen.setOnClickListener { toggleFullscreen() }
        btnPip.setOnClickListener { enterPip() }
        btnReport.setOnClickListener { showReportDialog() }

        if (videoUrl.isNullOrBlank()) {
            Toast.makeText(this, "No video available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        videoView.setVideoURI(Uri.parse(videoUrl))

        videoView.setOnPreparedListener { mp ->
            progressBar.visibility = View.GONE
            mp.isLooping = false
            videoView.setVideoSize(mp.videoWidth, mp.videoHeight)
            videoView.start()
            Log.d("VideoPlayerActivity", "Video prepared, starting playback")
        }

        videoView.setOnErrorListener { _, _, _ ->
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Unable to play this video", Toast.LENGTH_SHORT).show()
            Log.e("VideoPlayerActivity", "Video playback error")
            true
        }

        // Track video completion for playlist progress
        videoView.setOnCompletionListener {
            Log.d("VideoPlayerActivity", "========================================")
            Log.d("VideoPlayerActivity", "🎬 Video COMPLETED! (via completion listener)")
            Log.d("VideoPlayerActivity", "========================================")
            videoCompleted = true
            // Delay slightly to ensure everything is ready
            handler.postDelayed({
                markProgressIfNeeded()
            }, 500)
        }
    }

    private fun getCurrentUid(): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        Log.d("VideoPlayerActivity", "Current UID: $uid")
        return uid
    }

    private fun markProgressIfNeeded() {
        Log.d("VideoPlayerActivity", "========================================")
        Log.d("VideoPlayerActivity", "📊 markProgressIfNeeded called")
        Log.d("VideoPlayerActivity", "  progressMarked: $progressMarked")
        Log.d("VideoPlayerActivity", "  playlistId: $playlistId")
        Log.d("VideoPlayerActivity", "  videoId: $videoId")
        Log.d("VideoPlayerActivity", "  totalVideos: $totalVideos")
        Log.d("VideoPlayerActivity", "========================================")

        if (progressMarked) {
            Log.d("VideoPlayerActivity", "⚠️ Progress already marked, skipping")
            return
        }

        val pid = playlistId
        val vid = videoId
        if (pid.isNullOrEmpty() || vid.isNullOrEmpty() || totalVideos <= 0) {
            Log.d("VideoPlayerActivity", "⚠️ Missing playlist info, skipping progress")
            Log.d("VideoPlayerActivity", "  pid: $pid, vid: $vid, totalVideos: $totalVideos")
            return
        }

        val uid = getCurrentUid()
        if (uid == null) {
            Log.d("VideoPlayerActivity", "⚠️ No user logged in, skipping progress")
            Toast.makeText(this, "Please log in to track progress", Toast.LENGTH_SHORT).show()
            return
        }

        Log.d("VideoPlayerActivity", "✅ Updating progress for playlist: $pid, video: $vid, total: $totalVideos")
        progressMarked = true

        // Call PlaylistManager to update progress
        PlaylistManager.updateProgress(uid, pid, vid, totalVideos)

        // Show toast to indicate progress was saved
        Toast.makeText(this, "✅ Progress saved!", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("VideoPlayerActivity", "onDestroy called")
        Log.d("VideoPlayerActivity", "  videoCompleted: $videoCompleted, progressMarked: $progressMarked")

        // If video was completed but progress not marked, try to mark it
        if (videoCompleted && !progressMarked) {
            Log.d("VideoPlayerActivity", "Video completed but progress not marked - marking now in onDestroy")
            markProgressIfNeeded()
        }

        // Remove any pending handler callbacks
        handler.removeCallbacksAndMessages(null)

        if (::videoView.isInitialized && videoView.isPlaying) {
            videoView.stopPlayback()
        }
    }

    override fun onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen()
            return
        }
        // If video was playing and progress not marked, try to mark it
        if (!progressMarked && videoId != null && playlistId != null) {
            Log.d("VideoPlayerActivity", "Back pressed - marking progress")
            markProgressIfNeeded()
        }
        if (::videoView.isInitialized && videoView.isPlaying) {
            videoView.stopPlayback()
        }
        super.onBackPressed()
    }

    // ---------------- Report (themed dialog) ----------------
    private fun showReportDialog() {
        val reporterEmail = FirebaseAuth.getInstance().currentUser?.email
        if (reporterEmail.isNullOrEmpty()) {
            Toast.makeText(this, "Please log in to report", Toast.LENGTH_SHORT).show()
            return
        }
        if (reporterEmail == skillUserId) {
            Toast.makeText(this, "You can't report your own skill", Toast.LENGTH_SHORT).show()
            return
        }

        val root = dialogCard()
        root.addView(dialogTitle("Report \"$skillTitle\""))
        root.addView(TextView(this).apply {
            text = "Help us understand what's wrong"
            setTextColor(Color.parseColor("#456882"))
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

        val btnCancel = pillButton("Cancel", Color.parseColor("#EAF1F5"), Color.parseColor("#456882")).apply {
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

    private fun reportReasonRow(text: String, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 15f
            setPadding(dp(4), dp(8), dp(4), dp(8))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    private fun submitReport(reason: String, reporterEmail: String) {
        val docRef = db.collection("reports").document()
        val report = Report(
            id = docRef.id,
            reporterId = reporterEmail,
            reportedUserId = skillUserId,
            skillId = skillId,
            reason = reason,
            description = "Reported skill: \"$skillTitle\" (Skill ID: $skillId)",
            status = "pending",
            timestamp = System.currentTimeMillis()
        )
        docRef.set(report)
            .addOnSuccessListener {
                Toast.makeText(this, "Report submitted. Our team will review it.", Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to submit report: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    // ---------------- Themed-dialog helpers ----------------
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
            setTextColor(Color.parseColor("#1B3C53"))
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
            setBackgroundColor(Color.parseColor("#EAF1F5"))
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

    // ---------------- Fullscreen ----------------
    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            topBar.visibility = View.GONE
            tvFullscreenIcon.text = "⤢"
            hideSystemBars()
            enterFullscreenVideoLayout()
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            topBar.visibility = View.VISIBLE
            tvFullscreenIcon.text = "⛶"
            showSystemBars()
            exitFullscreenVideoLayout()
        }
    }

    private fun enterFullscreenVideoLayout() {
        scrollContent.visibility = View.GONE
        val params = videoContainer.layoutParams
        params.height = ViewGroup.LayoutParams.MATCH_PARENT
        videoContainer.layoutParams = params
    }

    private fun exitFullscreenVideoLayout() {
        originalVideoContainerParams?.let {
            videoContainer.layoutParams = it
        }
        scrollContent.visibility = View.VISIBLE
    }

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
    }

    @Suppress("DEPRECATION")
    private fun showSystemBars() {
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    // ---------------- Picture-in-Picture ----------------
    private fun buildPipParams(): PictureInPictureParams {
        return PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .build()
    }

    private fun enterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Toast.makeText(this, "Picture-in-picture needs Android 8.0 or newer", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            enterPictureInPictureMode(buildPipParams())
        } catch (e: Exception) {
            Toast.makeText(this, "Picture-in-picture isn't supported on this device", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::videoView.isInitialized && videoView.isPlaying) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (isInPictureInPictureMode) {
            topBar.visibility = View.GONE
            videoView.setMediaController(null)
        } else {
            topBar.visibility = if (isFullscreen) View.GONE else View.VISIBLE
            videoView.setMediaController(mediaController)
        }
    }
}