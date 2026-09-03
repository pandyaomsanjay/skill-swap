package com.example.sgp

/**
 * Firestore model for the "content_feedback" collection — ratings/comments a
 * user leaves on a specific video or playlist (skill), distinct from the
 * general app Feedback model used elsewhere in AdminFeedbackActivity.
 *
 * Adjust field names here if your Firestore schema differs; toObject() will
 * silently leave any unmatched field at its default.
 */
data class ContentFeedback(
    var id: String = "",
    var skillId: String = "",
    // Blank when the feedback targets a whole playlist/single video rather
    // than one specific video inside a playlist.
    var videoId: String = "",
    var reporterId: String = "",
    var reporterName: String = "",
    // The uploader/creator being reviewed (playlist.userId / skillUserId) —
    // set by PlaylistActivity.kt and VideoPlayerActivity.kt when submitting.
    var targetUserId: String = "",
    var rating: Int = 0,
    var comment: String = "",
    var timestamp: Long = 0L
)