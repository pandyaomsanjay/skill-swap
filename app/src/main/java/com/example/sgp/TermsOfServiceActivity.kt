package com.example.sgp

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.widget.Toolbar

class TermsOfServiceActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_of_service)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val tvContent = findViewById<TextView>(R.id.tvTermsContent)
        tvContent.text = buildTermsText()
    }

    private fun buildTermsText(): String {
        return """
            Terms of Service for SkillSwap

            1. Acceptance of Terms
            By using SkillSwap, you agree to these terms. If you do not agree, please do not use the app.

            2. User Conduct
            - You must be at least 13 years old.
            - You may not post false, harmful, or illegal content.
            - SkillSwap is a platform for legitimate skill exchange. Fraud or abuse will lead to account termination.

            3. Credits and Trades
            Credits earned can be used to request skills. Credits have no monetary value. SkillSwap is not responsible for disputes between users; we encourage respectful communication.

            4. Intellectual Property
            You retain ownership of your profile content, but grant SkillSwap a license to display it within the app.

            5. Limitation of Liability
            SkillSwap is provided "as is". We are not liable for any damages arising from your use of the app.

            6. Termination
            We may suspend or terminate accounts that violate these terms.

            7. Changes
            We may update these terms. Continued use means acceptance.

            8. Contact
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