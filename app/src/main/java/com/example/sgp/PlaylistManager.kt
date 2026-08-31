package com.example.sgp

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object PlaylistManager {

    private const val REWARD_POINTS = 500
    private val db = FirebaseFirestore.getInstance()

    private fun sendNotification(userId: String, title: String, message: String, type: String = "playlist") {
        if (userId.isBlank()) return
        val data = hashMapOf(
            "userId" to userId,
            "title" to title,
            "message" to message,
            "timestamp" to System.currentTimeMillis(),
            "type" to type,
            "read" to false
        )
        db.collection("notifications").add(data)
            .addOnFailureListener { e ->
                android.util.Log.e("PlaylistManager", "Failed to send notification", e)
            }
    }

    fun notifyPlaylistCreated(userId: String, playlistTitle: String) {
        sendNotification(
            userId,
            "Playlist Created",
            "Your playlist '$playlistTitle' is now live!",
            "playlist_created"
        )
    }

    private fun notifyPlaylistPurchased(userId: String, playlistTitle: String) {
        sendNotification(
            userId,
            "Playlist Unlocked",
            "You have unlocked '$playlistTitle'! Start learning.",
            "playlist_purchased"
        )
    }

    private fun notifyPlaylistCompleted(userId: String, playlistTitle: String) {
        sendNotification(
            userId,
            "Playlist Completed",
            "Congratulations! You completed '$playlistTitle'.",
            "playlist_completed"
        )
    }

    private fun notifyRewardReceived(userId: String, amount: Int) {
        sendNotification(
            userId,
            "Reward Earned",
            "You earned $amount points for completing 2 playlists!",
            "reward"
        )
    }

    fun updateProgress(
        uid: String,
        playlistId: String,
        videoId: String,
        totalVideos: Int,
        onComplete: ((Boolean) -> Unit)? = null
    ) {
        android.util.Log.d("PlaylistManager", "=== updateProgress ===")
        android.util.Log.d("PlaylistManager", "uid: $uid, playlistId: $playlistId, videoId: $videoId, totalVideos: $totalVideos")

        if (uid.isBlank() || playlistId.isBlank() || videoId.isBlank() || totalVideos <= 0) {
            android.util.Log.d("PlaylistManager", "Invalid parameters, returning")
            onComplete?.invoke(false)
            return
        }

        val progressRef = db.collection("users").document(uid)
            .collection("playlistProgress").document(playlistId)
        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            // ─── ALL READS FIRST ────────────────────────────────────────

            // 1. Check if user has purchased this playlist
            val purchaseRef = userRef.collection("purchasedPlaylists").document(playlistId)
            val purchaseSnap = transaction.get(purchaseRef)
            if (!purchaseSnap.exists()) {
                android.util.Log.d("PlaylistManager", "Playlist not purchased, skipping")
                return@runTransaction
            }

            // 2. Get current progress
            val progressSnap = transaction.get(progressRef)
            val completedVideos = progressSnap.get("completedVideos") as? List<String> ?: emptyList()
            android.util.Log.d("PlaylistManager", "Current completed videos: $completedVideos")

            if (completedVideos.contains(videoId)) {
                android.util.Log.d("PlaylistManager", "Video already marked as completed")
                return@runTransaction
            }

            // 3. Calculate new state (but don't write yet)
            val newList = completedVideos.toMutableList().apply { add(videoId) }
            val willComplete = newList.size >= totalVideos
            android.util.Log.d("PlaylistManager", "New completed videos: $newList")

            // 4. READ user document BEFORE any writes (CRITICAL FIX)
            val userSnap = transaction.get(userRef)

            // ─── ALL WRITES AFTER ALL READS ──────────────────────────

            // 5. Update the completed videos list
            transaction.set(progressRef, mapOf("completedVideos" to newList), SetOptions.merge())

            // 6. If all videos are completed, mark playlist completed and handle reward
            if (willComplete) {
                android.util.Log.d("PlaylistManager", "🎉 Playlist COMPLETED!")
                transaction.update(progressRef, "completed", true)
                transaction.update(progressRef, "completedAt", System.currentTimeMillis())

                val currentCount = userSnap.getLong("completedPlaylistCount")?.toInt() ?: 0
                val newCount = currentCount + 1
                transaction.update(userRef, "completedPlaylistCount", newCount)

                val lastRewarded = userSnap.getLong("lastRewardedCount")?.toInt() ?: 0
                if (newCount >= 2 && newCount % 2 == 0 && newCount != lastRewarded) {
                    val currentCredits = userSnap.getLong("credits")?.toInt() ?: 0
                    transaction.update(userRef, "credits", currentCredits + REWARD_POINTS)
                    transaction.update(userRef, "lastRewardedCount", newCount)
                    android.util.Log.d("PlaylistManager", "✅ Reward granted: $REWARD_POINTS points")
                }
            }
        }.addOnSuccessListener {
            android.util.Log.d("PlaylistManager", "✅ Progress updated successfully!")

            // Check if playlist is completed and call callback
            db.collection("skills").document(playlistId).get()
                .addOnSuccessListener { doc ->
                    val title = doc.getString("title") ?: "Playlist"
                    progressRef.get()
                        .addOnSuccessListener { progressDoc ->
                            val completed = progressDoc.getBoolean("completed") ?: false

                            // 🔥 CALLBACK - NOTIFY UI THAT PLAYLIST IS COMPLETE
                            onComplete?.invoke(completed)

                            if (completed) {
                                notifyPlaylistCompleted(uid, title)
                                userRef.get()
                                    .addOnSuccessListener { userDoc ->
                                        val lastRewarded = userDoc.getLong("lastRewardedCount")?.toInt() ?: 0
                                        val completedCount = userDoc.getLong("completedPlaylistCount")?.toInt() ?: 0
                                        if (completedCount >= 2 && completedCount % 2 == 0 && completedCount == lastRewarded) {
                                            notifyRewardReceived(uid, REWARD_POINTS)
                                        }
                                    }
                            }
                        }
                }
        }.addOnFailureListener { e ->
            android.util.Log.e("PlaylistManager", "❌ Failed to update progress: ${e.message}", e)
            onComplete?.invoke(false)
        }
    }

    data class PlaylistProgress(
        val completedVideos: List<String> = emptyList(),
        val completed: Boolean = false,
        val completedAt: Long? = null
    )

    fun getProgress(uid: String, playlistId: String, onResult: (PlaylistProgress?) -> Unit) {
        if (uid.isBlank() || playlistId.isBlank()) {
            onResult(null)
            return
        }
        db.collection("users").document(uid)
            .collection("playlistProgress").document(playlistId)
            .get()
            .addOnSuccessListener { doc ->
                if (!doc.exists()) {
                    onResult(null)
                    return@addOnSuccessListener
                }
                @Suppress("UNCHECKED_CAST")
                val completedVideos = doc.get("completedVideos") as? List<String> ?: emptyList()
                onResult(
                    PlaylistProgress(
                        completedVideos = completedVideos,
                        completed = doc.getBoolean("completed") ?: false,
                        completedAt = doc.getLong("completedAt")
                    )
                )
            }
            .addOnFailureListener { onResult(null) }
    }

    fun hasPurchased(uid: String, playlistId: String, onResult: (Boolean) -> Unit) {
        if (uid.isBlank() || playlistId.isBlank()) {
            onResult(false)
            return
        }
        val ref = db.collection("users").document(uid)
            .collection("purchasedPlaylists").document(playlistId)
        ref.get()
            .addOnSuccessListener { doc -> onResult(doc.exists()) }
            .addOnFailureListener { onResult(false) }
    }

    fun purchasePlaylist(uid: String, playlistId: String, price: Int, playlistTitle: String = "Playlist",
                         onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        if (uid.isBlank() || playlistId.isBlank() || price <= 0) {
            onFailure(IllegalArgumentException("Invalid arguments"))
            return
        }

        val userRef = db.collection("users").document(uid)
        val purchaseRef = userRef.collection("purchasedPlaylists").document(playlistId)
        val skillRef = db.collection("skills").document(playlistId)

        db.runTransaction { transaction ->
            // ─── ALL READS FIRST ────────────────────────────────────────
            val purchaseSnap = transaction.get(purchaseRef)
            if (purchaseSnap.exists()) {
                throw IllegalStateException("Already purchased")
            }

            val userSnap = transaction.get(userRef)
            val credits = userSnap.getLong("credits")?.toInt() ?: 0
            if (credits < price) {
                throw IllegalStateException("Insufficient credits")
            }

            // ─── ALL WRITES AFTER ALL READS ──────────────────────────
            transaction.update(userRef, "credits", credits - price)

            val purchaseData = mapOf(
                "purchasedAt" to System.currentTimeMillis(),
                "creditsPaid" to price
            )
            transaction.set(purchaseRef, purchaseData)

            transaction.update(skillRef, "accessCount", FieldValue.increment(1))
        }.addOnSuccessListener {
            notifyPlaylistPurchased(uid, playlistTitle)
            onSuccess()
        }.addOnFailureListener { e ->
            onFailure(e)
        }
    }
}