package com.example.sgp

data class Feedback(
    val id: String = "",
    val userId: String = "",
    val userName: String = "",
    var userPhotoUrl: String = "",
    val title: String = "",
    val message: String = "",
    val rating: Int = 0,
    val category: String = "",
    val status: String = "new",
    val timestamp: Long = 0L
)

enum class FeedbackCategory(val label: String, val firestoreValue: String?) {
    ALL("All Feedback", null),
    SUGGESTION("Suggestions", "suggestion"),
    BUG_REPORT("Bug Reports", "bug_report"),
    COMPLAINT("Complaints", "complaint"),
    FEATURE_REQUEST("Feature Requests", "feature_request")
}

enum class FeedbackStatus(val label: String, val firestoreValue: String) {
    NEW("New", "new"),
    READ("Read", "read"),
    RESOLVED("Resolved", "resolved")
}