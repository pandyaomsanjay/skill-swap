package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView

class TermsOfServiceActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_terms_of_service)

        findViewById<View>(R.id.btnBack)?.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        findViewById<TextView>(R.id.tvLastUpdated).text = "Last updated: ${getCurrentDate()}"
        findViewById<TextView>(R.id.tvTermsContent).text = buildTermsText()

        findViewById<View>(R.id.layoutEmailSupport).setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:skillswap23@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "SkillSwap Terms Question")
            }
            startActivity(Intent.createChooser(intent, "Contact Support"))
        }

        // Accept button - go back to Create Account with terms accepted
        val btnAccept = findViewById<Button>(R.id.btnAccept)
        btnAccept.setOnClickListener {
            val intent = Intent()
            intent.putExtra("terms_accepted", true)
            setResult(RESULT_OK, intent)
            finish()
        }
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
}