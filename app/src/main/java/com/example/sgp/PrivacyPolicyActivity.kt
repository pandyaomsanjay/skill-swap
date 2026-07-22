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
            Name, email, location, profile picture, and the skills you offer or request.

            2. How We Use It
            To match skill trades, calculate credits and ratings, and send trade-related notifications.

            3. Data Sharing & Security
            We never sell your data. Only your name, skills, and rating are visible to other users. Data is stored securely with Firebase.

            4. Your Rights
            Edit or delete your profile anytime. Contact us to delete your account entirely.

            5. Contact Us
            skillswap23@gmail.com
        """.trimIndent()
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}