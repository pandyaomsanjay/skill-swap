package com.example.sgp

import android.app.PictureInPictureParams
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Rational
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Self-contained in-app player so admins can watch a reported video right
// inside SkillSwap. Themed to match the rest of the admin screens (navy
// #16263A header, cream text, same circular-badge language used in
// AdminReportsActivity's bottom sheets), with two YouTube-style extras:
//  - a fullscreen toggle: rotates to landscape, hides the report-details
//    section below the video, and expands the video container itself to
//    fill the entire screen — same as tapping fullscreen on a YouTube video.
//  - Picture-in-Picture: tapping the PiP button, or leaving the app while a
//    video is playing, shrinks it into a small floating window like YouTube's
//    mini player, so the admin can keep browsing elsewhere in the app.
class AdminVideoPlayerActivity : BaseActivity() {

    private lateinit var videoView: ResizableVideoView
    private lateinit var videoContainer: FrameLayout
    private lateinit var scrollContent: NestedScrollView
    private lateinit var topBar: View
    private lateinit var mediaController: MediaController
    private lateinit var tvFullscreenIcon: TextView

    private lateinit var btnResolve: TextView
    private lateinit var btnBlock: TextView
    private lateinit var tvStatusValue: TextView

    private val db: FirebaseFirestore by lazy { Firebase.firestore }

    private var isFullscreen = false
    // The video container's normal (embedded, 220dp-tall) height, captured
    // once at startup so fullscreen exit can snap back to exactly this.
    private var originalVideoContainerParams: ViewGroup.LayoutParams? = null

    private var reportId: String = ""
    private var reportedUserUid: String? = null
    private var reportedUserName: String = "this user"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_video_player)

        // Keep the screen on while a video is playing, same as any video app.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        videoView = findViewById(R.id.videoView)
        videoContainer = findViewById(R.id.videoContainer)
        scrollContent = findViewById(R.id.scrollContent)
        topBar = findViewById(R.id.topBar)
        val progressBar: ProgressBar = findViewById(R.id.progressBar)
        val tvTitle: TextView = findViewById(R.id.tvVideoTitle)
        val tvSubtitle: TextView = findViewById(R.id.tvVideoSubtitle)
        val btnClose: View = findViewById(R.id.btnClose)
        val btnFullscreen: View = findViewById(R.id.btnFullscreen)
        val btnPip: View = findViewById(R.id.btnPip)
        tvFullscreenIcon = findViewById(R.id.tvFullscreenIcon)
        btnResolve = findViewById(R.id.btnResolve)
        btnBlock = findViewById(R.id.btnBlock)

        // Stash the video container's original (220dp) layout params right
        // away, before anything can mutate them, so fullscreen exit always
        // has a clean baseline to snap back to.
        originalVideoContainerParams = videoContainer.layoutParams

        val videoUrl = intent.getStringExtra("videoUrl")
        val title = intent.getStringExtra("videoTitle")
        val subtitle = intent.getStringExtra("videoSubtitle")

        tvTitle.text = if (title.isNullOrBlank()) "Reported Video" else title
        if (!subtitle.isNullOrBlank()) {
            tvSubtitle.text = subtitle
            tvSubtitle.visibility = View.VISIBLE
        }

        populateReportDetails()
        setupResolveAndBlock()

        btnClose.setOnClickListener { finish() }
        btnFullscreen.setOnClickListener { toggleFullscreen() }
        btnPip.setOnClickListener { enterPip() }

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
            // Crop-fill the container instead of letterboxing (see ResizableVideoView).
            videoView.setVideoSize(mp.videoWidth, mp.videoHeight)
            videoView.start()
        }
        videoView.setOnErrorListener { _, _, _ ->
            progressBar.visibility = View.GONE
            Toast.makeText(this, "Unable to play this video", Toast.LENGTH_SHORT).show()
            true
        }
    }

    // ---------------- Report Details ----------------

    private fun populateReportDetails() {
        reportId = intent.getStringExtra("reportId") ?: ""
        reportedUserUid = intent.getStringExtra("reportedUserUid")
        reportedUserName = intent.getStringExtra("reportedUserName") ?: "this user"
        val reporterName = intent.getStringExtra("reporterName") ?: "-"
        val reason = intent.getStringExtra("reportReason") ?: "-"
        val status = intent.getStringExtra("reportStatus") ?: "-"
        val timestamp = intent.getLongExtra("reportTimestamp", 0L)
        val dateStr = if (timestamp > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(timestamp))
        } else "-"

        val llReportInfo: LinearLayout = findViewById(R.id.llReportInfo)
        llReportInfo.removeAllViews()

        addDetailRow(llReportInfo, "Report ID", reportId.ifBlank { "-" })
        addDetailRow(llReportInfo, "Reported User", reportedUserName)
        addDetailRow(llReportInfo, "Reporter", reporterName)
        addDetailRow(llReportInfo, "Reason", reason.ifBlank { "-" })
        tvStatusValue = addDetailRow(
            llReportInfo, "Status",
            status.ifBlank { "-" }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        )
        addDetailRow(llReportInfo, "Date", dateStr)

        if (status.equals("resolved", ignoreCase = true)) {
            markResolvedUi()
        }
    }

    private fun addDetailRow(container: LinearLayout, label: String, value: String): TextView {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(10) }
        }
        row.addView(TextView(this).apply {
            text = label
            setTextColor(0xFF456882.toInt())
            textSize = 12.5f
            layoutParams = LinearLayout.LayoutParams(dp(112), LinearLayout.LayoutParams.WRAP_CONTENT)
        })
        val valueView = TextView(this).apply {
            text = value
            setTextColor(0xFF1B3C53.toInt())
            textSize = 12.5f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(valueView)
        container.addView(row)
        return valueView
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ---------------- Resolve / Block (Firestore, mirrors AdminReportsActivity) ----------------

    private fun setupResolveAndBlock() {
        btnResolve.setOnClickListener {
            if (reportId.isBlank()) {
                Toast.makeText(this, "No report linked to this video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            db.collection("reports").document(reportId)
                .update("status", "resolved")
                .addOnSuccessListener {
                    Toast.makeText(this, "Report marked as resolved", Toast.LENGTH_SHORT).show()
                    markResolvedUi()
                }
                .addOnFailureListener {
                    Toast.makeText(this, "Failed to update report: ${it.message}", Toast.LENGTH_LONG).show()
                }
        }

        btnBlock.setOnClickListener {
            val uid = reportedUserUid
            if (uid.isNullOrBlank()) {
                Toast.makeText(this, "Could not find this user's account record", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AlertDialog.Builder(this)
                .setTitle("Block user")
                .setMessage("Block $reportedUserName? They will no longer be able to use SkillSwap.")
                .setPositiveButton("Block") { _, _ -> blockUser(uid) }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun blockUser(uid: String) {
        db.collection("users").document(uid)
            .update("blocked", true)
            .addOnSuccessListener {
                if (reportId.isNotBlank()) {
                    db.collection("reports").document(reportId).update("status", "resolved")
                }
                Toast.makeText(this, "User blocked", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to block user: ${it.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun markResolvedUi() {
        btnResolve.text = "✅ Resolved"
        btnResolve.isEnabled = false
        btnResolve.alpha = 0.55f
        if (::tvStatusValue.isInitialized) {
            tvStatusValue.text = "Resolved"
        }
    }

    // ---------------- Fullscreen (YouTube-style: rotate + expand the video, hide details) ----------------

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

    // Hides the details section entirely and stretches videoContainer (and
    // therefore videoView, which already crop-fills its parent) to occupy
    // the full screen — this is what actually makes the video fullscreen,
    // since videoView alone can't grow past its 220dp-tall parent otherwise.
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

    // ---------------- Picture-in-Picture (YouTube-style mini player) ----------------

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

    // Auto-shrink into the mini player when the admin navigates away (home
    // button, switching apps) while the video is still playing — mirrors
    // YouTube's behaviour exactly.
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (::videoView.isInitialized && videoView.isPlaying) {
            enterPip()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // The on-screen controller and top bar don't fit (and can't be tapped
        // reliably) in the tiny PiP window, so hide them there and restore
        // them once back to normal size.
        if (isInPictureInPictureMode) {
            topBar.visibility = View.GONE
            videoView.setMediaController(null)
        } else {
            topBar.visibility = if (isFullscreen) View.GONE else View.VISIBLE
            videoView.setMediaController(mediaController)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::videoView.isInitialized && videoView.isPlaying) {
            videoView.stopPlayback()
        }
    }

    override fun onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen()
            return
        }
        if (::videoView.isInitialized && videoView.isPlaying) {
            videoView.stopPlayback()
        }
        super.onBackPressed()
    }
}