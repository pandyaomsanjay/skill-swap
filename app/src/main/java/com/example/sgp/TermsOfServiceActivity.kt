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

        findViewById<TextView>(R.id.tvLastUpdated).text = "Last updated: ${getCurrentDate()}"
        findViewById<TextView>(R.id.tvTermsContent).text = buildTermsText()
    }

    private fun buildTermsText(): String {
        return """
        1. Acceptance
        • By using SkillSwap, you agree to these terms.

        2. Conduct & Content
        • Must be 13+
        • Keep content truthful and legal
        • Fraud or abuse may lead to suspension or termination
        • You own your content but grant SkillSwap a license to display it in-app

        3. Credits & Trades
        • Credits are used to request skills and hold no monetary value
        • SkillSwap doesn't arbitrate disputes between users

        4. Liability & Changes
        • SkillSwap is provided "as is," with no liability for damages from use
        • Terms may be updated; continued use means acceptance
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