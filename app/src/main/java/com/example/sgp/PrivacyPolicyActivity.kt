package com.example.sgp

import android.os.Bundle
import android.view.View
import android.widget.TextView
import com.google.android.material.card.MaterialCardView

class PrivacyPolicyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        findViewById<MaterialCardView>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvLastUpdated).text = "Last updated: ${getCurrentDate()}"
        findViewById<TextView>(R.id.tvPrivacyContent).text = buildPrivacyPolicyText()
    }

    private fun buildPrivacyPolicyText(): String {
        return """
        Privacy Policy for SkillSwap

        1. Information We Collect
        • Name, email, location, profile picture
        • The skills you offer or request

        2. How We Use It
        • To match skill trades
        • To calculate credits and ratings
        • To send trade-related notifications

        3. Data Sharing & Security
        • We never sell your data
        • Only your name, skills, and rating are visible to other users
        • Data is stored securely with Firebase

        4. Your Rights
        • Edit or delete your profile anytime
        • Contact us to delete your account entirely
    """.trimIndent()
    }

    private fun getCurrentDate(): String {
        val dateFormat = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }
}