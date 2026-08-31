package com.example.sgp

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object PlaylistManager {

    private const val REWARD_POINTS = 500
    private val db = FirebaseFirestore.getInstance()

    /**
     * Sends a notification to a user.
     */
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

    /**
     * Sends a notification for playlist creation to the creator.
     */
    fun notifyPlaylistCreated(userId: String, playlistTitle: String) {
        sendNotification(
            userId,
            "Playlist Created",
            "Your playlist '$playlistTitle' is now live!",
            "playlist_created"
        )
    }

    /**
     * Notify when a user unlocks a playlist.
     */
    private fun notifyPlaylistPurchased(userId: String, playlistTitle: String) {
        sendNotification(
            userId,
            "Playlist Unlocked",
            "You have unlocked '$playlistTitle'! Start learning.",
            "playlist_purchased"
        )
    }

    /**
     * Notify when a user completes a playlist.
     */
    private fun notifyPlaylistCompleted(userId: String, playlistTitle: String) {
        sendNotification(
            userId,
            "Playlist Completed",
            "Congratulations! You completed '$playlistTitle'.",
            "playlist_completed"
        )
    }

    /**
     * Notify when a user receives a reward for completing two playlists.
     */
    private fun notifyRewardReceived(userId: String, amount: Int) {
        sendNotification(
            userId,
            "Reward Earned",
            "You earned $amount points for completing 2 playlists!",
            "reward"
        )
    }

    /**
     * Called when a video from a playlist finishes playing.
     * Updates progress, marks playlist completed if all videos are done,
     * and grants reward points every 2 completed playlists.
     * Uses a Firestore transaction to ensure atomicity and prevent race conditions.
     */
    fun updateProgress(uid: String, playlistId: String, videoId: String, totalVideos: Int) {
        if (uid.isBlank() || playlistId.isBlank() || videoId.isBlank() || totalVideos <= 0) return

        val progressRef = db.collection("users").document(uid)
            .collection("playlistProgress").document(playlistId)
        val userRef = db.collection("users").document(uid)

        db.runTransaction { transaction ->
            // 1. Check if user has purchased this playlist
            val purchaseRef = userRef.collection("purchasedPlaylists").document(playlistId)
            val purchaseSnap = transaction.get(purchaseRef)
            if (!purchaseSnap.exists()) {
                // User hasn't purchased – do not track progress
                return@runTransaction
            }

            // 2. Get current progress
            val progressSnap = transaction.get(progressRef)
            val completedVideos = progressSnap.get("completedVideos") as? List<String> ?: emptyList()
            if (completedVideos.contains(videoId)) {
                // Already marked – no change
                return@runTransaction
            }

            // 3. Update completed list
            val newList = completedVideos.toMutableList().apply { add(videoId) }
            transaction.set(progressRef, mapOf("completedVideos" to newList), SetOptions.merge())

            // 4. If all videos are completed, mark playlist completed and handle reward
            if (newList.size >= totalVideos) {
                // Mark completed
                transaction.update(progressRef, "completed", true)
                transaction.update(progressRef, "completedAt", System.currentTimeMillis())

                // Update user's completedPlaylistCount
                val userSnap = transaction.get(userRef)
                val currentCount = userSnap.getLong("completedPlaylistCount")?.toInt() ?: 0
                val newCount = currentCount + 1
                transaction.update(userRef, "completedPlaylistCount", newCount)

                // Get playlist title for notifications (optional – can fetch separately)
                // We'll fetch playlist title outside transaction to avoid reading extra docs inside.
                // We'll do that after transaction.

                // Reward logic
                val lastRewarded = userSnap.getLong("lastRewardedCount")?.toInt() ?: 0
                if (newCount >= 2 && newCount % 2 == 0 && newCount != lastRewarded) {
                    // Grant reward points
                    val currentCredits = userSnap.getLong("credits")?.toInt() ?: 0
                    transaction.update(userRef, "credits", currentCredits + REWARD_POINTS)
                    transaction.update(userRef, "lastRewardedCount", newCount)
                }
            }
        }.addOnSuccessListener {
            // After successful transaction, fetch playlist title and send notifications
            db.collection("skills").document(playlistId).get()
                .addOnSuccessListener { doc ->
                    val title = doc.getString("title") ?: "Playlist"
                    // Check if completed and reward was granted
                    // We can re-read progress or user data to decide, but we already updated in transaction.
                    // For simplicity, we'll send notifications if progress is complete.
                    // We'll query progress again to be safe.
                    progressRef.get()
                        .addOnSuccessListener { progressDoc ->
                            val completed = progressDoc.getBoolean("completed") ?: false
                            if (completed) {
                                notifyPlaylistCompleted(uid, title)
                                // Check if reward was granted (we can check user's updated lastRewardedCount)
                                userRef.get()
                                    .addOnSuccessListener { userDoc ->
                                        val lastRewarded = userDoc.getLong("lastRewardedCount")?.toInt() ?: 0
                                        val completedCount = userDoc.getLong("completedPlaylistCount")?.toInt() ?: 0
                                        if (completedCount >= 2 && completedCount % 2 == 0 && completedCount == lastRewarded) {
                                            // Reward was granted
                                            notifyRewardReceived(uid, REWARD_POINTS)
                                        }
                                    }
                            }
                        }
                }
        }.addOnFailureListener { e ->
            android.util.Log.e("PlaylistManager", "Failed to update progress", e)
        }
    }

    /**
     * Checks if the user has purchased the playlist.
     */
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

    /**
     * Purchases a playlist: deducts credits, stores purchase record.
     * Uses a transaction to ensure atomicity.
     * Returns success/failure via callbacks.
     * Prevents duplicate purchase, insufficient credits, and points deduction on failure.
     */
    fun purchasePlaylist(uid: String, playlistId: String, price: Int, playlistTitle: String = "Playlist",
                         onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        if (uid.isBlank() || playlistId.isBlank() || price <= 0) {
            onFailure(IllegalArgumentException("Invalid arguments"))
            return
        }

        val userRef = db.collection("users").document(uid)
        val purchaseRef = userRef.collection("purchasedPlaylists").document(playlistId)

        db.runTransaction { transaction ->
            // 1. Check if already purchased
            val purchaseSnap = transaction.get(purchaseRef)
            if (purchaseSnap.exists()) {
                throw IllegalStateException("Already purchased")
            }

            // 2. Check credits
            val userSnap = transaction.get(userRef)
            val credits = userSnap.getLong("credits")?.toInt() ?: 0
            if (credits < price) {
                throw IllegalStateException("Insufficient credits")
            }

            // 3. Deduct credits
            transaction.update(userRef, "credits", credits - price)

            // 4. Save purchase record
            val purchaseData = mapOf(
                "purchasedAt" to System.currentTimeMillis(),
                "creditsPaid" to price
            )
            transaction.set(purchaseRef, purchaseData)
        }.addOnSuccessListener {
            // Send notification
            notifyPlaylistPurchased(uid, playlistTitle)
            onSuccess()
        }.addOnFailureListener { e ->
            // Transaction failed – no points deducted
            onFailure(e)
        }
    }
}