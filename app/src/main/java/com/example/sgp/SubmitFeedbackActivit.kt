package com.example.sgp

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class SubmitFeedbackActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var etTitle: TextInputEditText
    private lateinit var etMessage: TextInputEditText
    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingLabel: TextView
    private lateinit var actvCategory: AutoCompleteTextView

    private var currentUserEmail: String = ""
    private var currentUserName: String = ""
    private var currentUserPhotoUrl: String = ""

    // User-facing category labels map to the same firestoreValue used by
    // FeedbackCategory in FeedbackModels.kt (ALL is excluded — that's an
    // admin-side filter option, not something a user picks when submitting).
    private val submittableCategories = listOf(
        FeedbackCategory.SUGGESTION,
        FeedbackCategory.BUG_REPORT,
        FeedbackCategory.COMPLAINT,
        FeedbackCategory.FEATURE_REQUEST
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_feedback)

        db = Firebase.firestore

        val toolbar = findViewById<MaterialToolbar>(R.id.feedbackToolbar)
        toolbar.setNavigationOnClickListener { finish() }

        etTitle = findViewById(R.id.etTitle)
        etMessage = findViewById(R.id.etMessage)
        ratingBar = findViewById(R.id.ratingBar)
        tvRatingLabel = findViewById(R.id.tvRatingLabel)
        actvCategory = findViewById(R.id.actvCategory)

        tvRatingLabel.text = ratingLabel(ratingBar.rating)
        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvRatingLabel.text = ratingLabel(rating)
        }

        setupCategoryDropdown()
        loadCurrentUser()

        findViewById<MaterialButton>(R.id.btnSubmitFeedback).setOnClickListener {
            submitFeedback()
        }
    }

    private fun ratingLabel(rating: Float): String = when (rating.toInt()) {
        5 -> "Excellent"
        4 -> "Good"
        3 -> "Okay"
        2 -> "Needs work"
        else -> "Poor"
    }

    private fun setupCategoryDropdown() {
        val labels = submittableCategories.map { it.label }
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        actvCategory.setAdapter(adapter)
        // Default to the first option so submission never fails due to an
        // empty category if the user doesn't tap the dropdown.
        actvCategory.setText(labels.first(), false)
    }

    private fun loadCurrentUser() {
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        currentUserEmail = prefs.getString("user_email", "") ?: ""

        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "User not found", Toast.LENGTH_SHORT).show()
            return
        }

        // ASSUMPTION: same "users" collection / "email" field pattern used by Profile.kt
        db.collection("users").whereEqualTo("email", currentUserEmail).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    val user = snapshot.documents[0].toObject(Users::class.java)
                    currentUserName = user?.name ?: currentUserEmail
                    currentUserPhotoUrl = user?.profileImage ?: ""
                } else {
                    currentUserName = currentUserEmail
                }
            }
            .addOnFailureListener {
                currentUserName = currentUserEmail
            }
    }

    private fun submitFeedback() {
        val title = etTitle.text?.toString()?.trim().orEmpty()
        val message = etMessage.text?.toString()?.trim().orEmpty()
        val rating = ratingBar.rating.toInt()
        val selectedLabel = actvCategory.text?.toString().orEmpty()

        if (title.isEmpty()) {
            etTitle.error = "Please add a short title"
            return
        }
        if (message.isEmpty()) {
            etMessage.error = "Please tell us more"
            return
        }
        if (currentUserEmail.isEmpty()) {
            Toast.makeText(this, "You need to be logged in to send feedback", Toast.LENGTH_SHORT).show()
            return
        }

        val category = submittableCategories.firstOrNull { it.label == selectedLabel }
            ?: FeedbackCategory.SUGGESTION

        val feedbackData = hashMapOf(
            "userId" to currentUserEmail,
            "userName" to currentUserName.ifBlank { currentUserEmail },
            "userPhotoUrl" to currentUserPhotoUrl,
            "title" to title,
            "message" to message,
            "rating" to rating,
            "category" to category.firestoreValue,
            "status" to FeedbackStatus.NEW.firestoreValue,
            "timestamp" to System.currentTimeMillis()
        )

        db.collection("feedback").add(feedbackData)
            .addOnSuccessListener {
                Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to send: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}