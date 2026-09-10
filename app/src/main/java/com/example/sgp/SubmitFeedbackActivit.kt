package com.example.sgp

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.AutoCompleteTextView
import android.widget.LinearLayout
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
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

    private var selectedCategory: FeedbackCategory = FeedbackCategory.SUGGESTION

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_submit_feedback)

        db = Firebase.firestore

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        etTitle = findViewById(R.id.etTitle)
        etMessage = findViewById(R.id.etMessage)
        ratingBar = findViewById(R.id.ratingBar)
        tvRatingLabel = findViewById(R.id.tvRatingLabel)
        actvCategory = findViewById(R.id.actvCategory)

        tvRatingLabel.text = ratingLabel(ratingBar.rating)
        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvRatingLabel.text = ratingLabel(rating)
        }

        setupCategoryPicker()
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

    // ====================== Themed category picker ======================

    private fun setupCategoryPicker() {
        // Default selection so submission never fails if the user doesn't tap.
        selectedCategory = submittableCategories.first()
        actvCategory.setText(selectedCategory.label, false)

        actvCategory.setOnClickListener { showCategoryDialog() }
    }

    private fun showCategoryDialog() {
        val root = dialogCard()
        root.addView(dialogTitle("Select a category"))
        root.addView(TextView(this).apply {
            text = "Pick the option that best fits your feedback"
            setTextColor(Color.parseColor("#456882"))
            textSize = 13f
            setPadding(0, dp(4), 0, dp(4))
        })
        root.addView(dividerLine())

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        submittableCategories.forEachIndexed { index, category ->
            root.addView(optionRow(category.label) {
                dialog.dismiss()
                selectedCategory = category
                actvCategory.setText(category.label, false)
            })
            if (index != submittableCategories.lastIndex) {
                root.addView(dividerLine())
            }
        }

        val btnCancel = pillButton(
            "Cancel",
            Color.parseColor("#EAF1F5"),
            Color.parseColor("#456882")
        ).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(16)
                gravity = Gravity.END
            }
            setPadding(dp(28), dp(10), dp(28), dp(10))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnCancel)

        dialog.show()
    }

    // ---- Themed dialog helpers (same style as ExploreActivity) ----

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }
    }

    private fun dialogTitle(text: String): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
        }
    }

    private fun dividerLine(): View {
        return View(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(1)
            ).apply {
                topMargin = dp(12)
                bottomMargin = dp(12)
            }
            setBackgroundColor(Color.parseColor("#EAF1F5"))
        }
    }

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun optionRow(text: String, onClick: () -> Unit): TextView {
        val outValue = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return TextView(this).apply {
            this.text = text
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 15f
            setPadding(dp(4), dp(12), dp(4), dp(12))
            setBackgroundResource(outValue.resourceId)
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    // ====================== End themed helpers ======================

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

        if (title.isEmpty()) {
            etTitle.error = "Please add a short title"
            return
        }
        if (message.isEmpty()) {
            etMessage.error = "Please tell us more"
            return
        }
        if (currentUserEmail.isEmpty()) {
            Toast.makeText(
                this,
                "You need to be logged in to send feedback",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val feedbackData = hashMapOf(
            "userId" to currentUserEmail,
            "userName" to currentUserName.ifBlank { currentUserEmail },
            "userPhotoUrl" to currentUserPhotoUrl,
            "title" to title,
            "message" to message,
            "rating" to rating,
            "category" to selectedCategory.firestoreValue,
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