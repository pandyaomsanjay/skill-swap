package com.example.sgp

data class Users(
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
    var isLocationVerified: Boolean = false,
    var loginProvider: String = "",
    var createdAt: Long = System.currentTimeMillis(),
    var language: String = "",
    val profileImagePath: String? = null,

    var followersCount: Int = 0,
    var followingCount: Int = 0
)