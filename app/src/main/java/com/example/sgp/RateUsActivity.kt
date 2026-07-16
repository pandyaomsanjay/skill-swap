package com.example.sgp

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton

class RateUsActivity : BaseActivity() {

    private lateinit var btnRateNow: MaterialButton
    private lateinit var tvRemindLater: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_us)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        btnRateNow = findViewById(R.id.btnRateNow)
        tvRemindLater = findViewById(R.id.tvRemindLater)

        btnRateNow.setOnClickListener {
            openPlayStoreForRating()
        }

        tvRemindLater.setOnClickListener {
            Toast.makeText(this, "We'll remind you later", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun openPlayStoreForRating() {
        val packageName = applicationContext.packageName
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: android.content.ActivityNotFoundException) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}