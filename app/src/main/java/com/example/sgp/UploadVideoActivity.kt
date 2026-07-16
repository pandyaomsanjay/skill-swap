package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.VideoView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.launch

class UploadVideoActivity : AppCompatActivity() {
    private lateinit var videoView: VideoView
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_upload_video)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        videoView = findViewById(R.id.videoView)
        db = FirebaseFirestore.getInstance()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 100 && resultCode == RESULT_OK) {
            val videoUri = data?.data ?: return
            uploadVideoToSupabase(videoUri)
        }
    }

    fun uploadVideoToSupabase(videoUri: Uri) {
        val bucket = SupabaseClient.client.storage.from("user-videos")

        val inputStream = contentResolver.openInputStream(videoUri)
        val videoBytes = inputStream?.readBytes() ?: return

        val fileName = "videos/${System.currentTimeMillis()}.mp4"

        lifecycleScope.launch {
            try {
                bucket.upload(
                    path = fileName,
                    data = videoBytes
                )

                val videoUrl = bucket.publicUrl(fileName)
                saveVideoUrlToFirestore(videoUrl)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun saveVideoUrlToFirestore(url: String) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return
        val uid = currentUser.uid
        val videoData = hashMapOf(
            "url" to url,
            "uploadedAt" to System.currentTimeMillis()
        )
        db.collection("videos")
            .document(uid)
            .collection("userVideos")
            .add(videoData)
            .addOnSuccessListener {
                // success
            }
            .addOnFailureListener {
                // failure
            }
    }
}