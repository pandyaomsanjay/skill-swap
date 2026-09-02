package com.example.sgp

import com.google.firebase.firestore.PropertyName

data class User(
    var uid: String = "",
    var name: String = "",
    var email: String = "",
    var phone: String = "",
    var location: String = "",
    var latitude: Double = 0.0,
    var longitude: Double = 0.0,
    var rating: Double = 0.0,
    var completedTrades: Int = 0,
    var profileImage: String = "",
    var userType: String = "standard",
    var joinedDate: Long = System.currentTimeMillis(),
    var skillsTeach: String = "",
    var skillsLearn: String = "",
    var credits: Int = 0,

    @get:PropertyName("isLocationVerified")
    @set:PropertyName("isLocationVerified")
    var isLocationVerified: Boolean = false,

    var loginProvider: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var bio: String = "",
    var fcmToken: String = "",

    @get:PropertyName("isOnline")
    @set:PropertyName("isOnline")
    var isOnline: Boolean = false,

    var lastActive: Long = System.currentTimeMillis(),

    @get:PropertyName("isBlocked")
    @set:PropertyName("isBlocked")
    var isBlocked: Boolean = false,

    @get:PropertyName("isEmailVerified")
    @set:PropertyName("isEmailVerified")
    var isEmailVerified: Boolean = false,

    @get:PropertyName("isReported")
    @set:PropertyName("isReported")
    var isReported: Boolean = false,

    var supportQuestions: List<HashMap<String, Any>> = arrayListOf(),

    // ---------- Follow system ----------
    // Denormalized counters so profile screens don't need to query the
    // "follows" collection just to show a number. Keep these in sync
    // whenever a follow/unfollow write succeeds (increment/decrement).
    var followersCount: Int = 0,
    var followingCount: Int = 0,
    // In User.kt, add:
    var completedPlaylistCount: Int = 0,      // number of completed playlists
    var lastRewardedCount: Int = 0            // the count at which last reward was given


)