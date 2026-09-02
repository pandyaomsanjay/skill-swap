package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.*
import java.util.*

class AddPlaylistVideoActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private var selectedVideoUri: Uri? = null
    private var videoDuration: String = ""
    private var uploadedVideoUrl: String? = null
    private var videoOrder: Int = 0
    private var totalVideos: Int = 0

    // Views
    private lateinit var ivThumbnail: ImageView
    private lateinit var tvFileName: TextView
    private lateinit var tvFileSize: TextView
    private lateinit var etTitle: TextInputEditText
    private lateinit var etDescription: TextInputEditText
    private lateinit var etCategory: TextInputEditText
    private lateinit var etDuration: TextInputEditText
    private lateinit var tvCredits: TextView
    private lateinit var btnDecrement: ImageButton
    private lateinit var btnIncrement: ImageButton
    private lateinit var tvOrderNumber: TextView
    private lateinit var btnOrderDecrement: ImageButton
    private lateinit var btnOrderIncrement: ImageButton
    private lateinit var btnAdd: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var btnSelectVideo: Button

    private val videoPickerLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { handleVideoSelected(it) }
    }

    companion object {
        private const val MAX_VIDEO_SIZE_BYTES = 50 * 1024 * 1024L  // 50 MB
        private const val STORAGE_BUCKET = "skill-videos"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_playlist_video)

        db = Firebase.firestore
        totalVideos = intent.getIntExtra("videoCount", 0)
        videoOrder = totalVideos + 1

        bindViews()
        setupListeners()
        updateOrderHint()
    }

    private fun bindViews() {
        ivThumbnail = findViewById(R.id.ivVideoThumbnail)
        tvFileName = findViewById(R.id.tvFileName)
        tvFileSize = findViewById(R.id.tvFileSize)
        etTitle = findViewById(R.id.etVideoTitle)
        etDescription = findViewById(R.id.etVideoDescription)
        etCategory = findViewById(R.id.etVideoCategory)
        etDuration = findViewById(R.id.etVideoDuration)
        tvCredits = findViewById(R.id.tvVideoCredits)
        btnDecrement = findViewById(R.id.btnVideoDecrement)
        btnIncrement = findViewById(R.id.btnVideoIncrement)
        tvOrderNumber = findViewById(R.id.tvOrderNumber)
        btnOrderDecrement = findViewById(R.id.btnOrderDecrement)
        btnOrderIncrement = findViewById(R.id.btnOrderIncrement)
        btnAdd = findViewById(R.id.btnAddVideoToPlaylist)
        progressBar = findViewById(R.id.progressBar)
        btnSelectVideo = findViewById(R.id.btnSelectVideo)

        // Show current order
        tvOrderNumber.text = videoOrder.toString()
    }

    private fun setupListeners() {
        btnSelectVideo.setOnClickListener { videoPickerLauncher.launch("video/*") }

        // Credits controls
        var credits = 0
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

        // Order controls (range: 1 to totalVideos+1)
        btnOrderDecrement.setOnClickListener {
            if (videoOrder > 1) {
                videoOrder--
                tvOrderNumber.text = videoOrder.toString()
                updateOrderHint()
            }
        }
        btnOrderIncrement.setOnClickListener {
            if (videoOrder < totalVideos + 1) {
                videoOrder++
                tvOrderNumber.text = videoOrder.toString()
                updateOrderHint()
            }
        }

        btnAdd.setOnClickListener {
            val title = etTitle.text.toString().trim()
            val description = etDescription.text.toString().trim()
            if (title.isEmpty()) {
                etTitle.error = "Title is required"
                return@setOnClickListener
            }
            if (description.isEmpty()) {
                etDescription.error = "Description is required"
                return@setOnClickListener
            }
            if (selectedVideoUri == null && uploadedVideoUrl == null) {
                Toast.makeText(this, "Please select a video", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (credits <= 0) {
                Toast.makeText(this, "Credits must be greater than 0", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // If video is not uploaded yet, upload now
            if (uploadedVideoUrl == null && selectedVideoUri != null) {
                uploadVideoAndAdd(credits)
            } else {
                addVideoToPlaylist(credits)
            }
        }

        findViewById<Button>(R.id.btnCancel).setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    private fun updateOrderHint() {
        findViewById<TextView>(R.id.tvOrderHint).text = "This video will be added at position #$videoOrder"
    }

    private fun handleVideoSelected(uri: Uri) {
        val cursor = contentResolver.query(uri, null, null, null, null)
        val sizeIndex = cursor?.getColumnIndex(MediaStore.Video.VideoColumns.SIZE)
        val durationIndex = cursor?.getColumnIndex(MediaStore.Video.VideoColumns.DURATION)
        cursor?.moveToFirst()
        val fileSize = sizeIndex?.let { cursor.getLong(it) } ?: 0
        val durationMs = durationIndex?.let { cursor.getLong(it) } ?: 0
        cursor?.close()

        if (fileSize > MAX_VIDEO_SIZE_BYTES) {
            Toast.makeText(this, "Video too large (max 50 MB)", Toast.LENGTH_LONG).show()
            return
        }

        selectedVideoUri = uri
        tvFileName.text = uri.lastPathSegment ?: "Selected video"
        tvFileSize.text = "${fileSize / (1024 * 1024)} MB"

        videoDuration = formatDuration(durationMs)
        etDuration.setText(videoDuration)

        Glide.with(this).load(uri).into(ivThumbnail)
    }

    private fun formatDuration(ms: Long): String {
        val seconds = ms / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return if (minutes >= 60) {
            val hours = minutes / 60
            val mins = minutes % 60
            String.format("%d:%02d:%02d", hours, mins, remainingSeconds)
        } else {
            String.format("%d:%02d", minutes, remainingSeconds)
        }
    }

    private fun uploadVideoAndAdd(credits: Int) {
        btnAdd.isEnabled = false
        progressBar.visibility = ProgressBar.VISIBLE
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val fileName = "playlist_videos/${UUID.randomUUID()}.mp4"
                val url = uploadVideoToSupabase(selectedVideoUri!!, fileName)
                if (url == null) {
                    Toast.makeText(this@AddPlaylistVideoActivity, "Upload failed", Toast.LENGTH_LONG).show()
                    btnAdd.isEnabled = true
                    progressBar.visibility = ProgressBar.GONE
                    return@launch
                }
                uploadedVideoUrl = url
                addVideoToPlaylist(credits)
            } catch (e: Exception) {
                Toast.makeText(this@AddPlaylistVideoActivity, "Upload error: ${e.message}", Toast.LENGTH_LONG).show()
                btnAdd.isEnabled = true
                progressBar.visibility = ProgressBar.GONE
            }
        }
    }

    private suspend fun uploadVideoToSupabase(uri: Uri, fileName: String): String? {
        return try {
            val bucket = SupabaseClient.client.storage.from(STORAGE_BUCKET)
            val bytes = withContext(Dispatchers.IO) {
                contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: throw Exception("Could not read file")
            bucket.upload(path = fileName, data = bytes) {
                upsert = true
            }
            "https://ghrxltlstncjcizyyqfo.supabase.co/storage/v1/object/public/$STORAGE_BUCKET/$fileName"
        } catch (e: Exception) {
            null
        }
    }

    private fun addVideoToPlaylist(credits: Int) {
        val video = PlaylistVideo(
            id = UUID.randomUUID().toString(),
            title = etTitle.text.toString().trim(),
            description = etDescription.text.toString().trim(),
            videoUrl = uploadedVideoUrl ?: "",
            credits = credits,
            order = videoOrder,
            duration = videoDuration,
            createdAt = System.currentTimeMillis()
        )

        val intent = Intent()
        intent.putExtra("video", video)
        setResult(RESULT_OK, intent)
        finish()
    }
}