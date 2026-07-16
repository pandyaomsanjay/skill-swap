package com.example.sgp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.widget.Toolbar

class PrivacyPolicyActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy_policy)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val tvContent = findViewById<TextView>(R.id.tvPrivacyContent)
        tvContent.text = buildPrivacyPolicyText()
    }

    private fun buildPrivacyPolicyText(): String {
        return """
            Privacy Policy for SkillSwap

            1. Information We Collect
            We collect personal information such as your name, email address, location, profile picture, and skills you offer or request. Usage data including trades, ratings, and credits is also stored to improve your experience.

            2. How We Use Your Information
            - To facilitate skill exchanges and matching.
            - To calculate credits, ratings, and trade history.
            - To send notifications about your trades, messages, and updates.
            - To improve app features and security.

            3. Data Sharing
            We do not sell your personal data. Limited data (e.g., your name, skills, rating) may be shown to other users for trade purposes.

            4. Data Security
            Your data is stored securely with Firebase. We implement reasonable measures to protect your information from unauthorized access.

            5. Your Rights
            You may edit or delete your profile information at any time. Contact us if you wish to delete your account.

            6. Changes to This Policy
            We may update this policy occasionally. Continued use of the app constitutes acceptance.

            7. Contact Us
            skillswap@example.com
        """.trimIndent()
    }

    private fun getCurrentDate(): String {
        val dateFormat = java.text.SimpleDateFormat("MMMM dd, yyyy", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date())
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}