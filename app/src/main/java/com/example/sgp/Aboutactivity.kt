package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.view.View

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<View>(R.id.layoutAboutPrivacy).setOnClickListener {
            startActivity(Intent(this, PrivacyPolicyActivity::class.java))
        }
        findViewById<View>(R.id.layoutAboutTerms).setOnClickListener {
            startActivity(Intent(this, TermsOfServiceActivity::class.java))
        }
        findViewById<View>(R.id.layoutAboutHelp).setOnClickListener {
            startActivity(Intent(this, HelpSupportActivity::class.java))
        }
    }
}