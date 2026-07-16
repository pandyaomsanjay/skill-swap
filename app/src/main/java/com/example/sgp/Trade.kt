package com.example.sgp

data class Trade(
    val id: String = "",
    val requesterId: String = "",
    val receiverId: String = "",
    val requesterSkill: String = "",
    val receiverSkill: String = "",
    val status: String = "",
    val requesterName: String = "",
    val receiverName: String = "",
    val timestamp: Long = 0,
    val uploaderName: String = "",
    val uploaderAvatar: Int = R.drawable.ic_default_profile,
    val skillOffered: String = "",
    val skillRequested: String = "",
    val rating: Float = 0f,
    val videoUrl: String = "",
    val isActive: Boolean = true

)
