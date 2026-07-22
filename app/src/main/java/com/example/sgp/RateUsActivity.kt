package com.example.sgp

import android.os.Bundle
import android.widget.RatingBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class RateUsActivity : BaseActivity() {

    private lateinit var ratingBar: RatingBar
    private lateinit var tvRatingLabel: TextView
    private lateinit var btnRateNow: MaterialButton
    private lateinit var tvRemindLater: TextView

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rate_us)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        ratingBar = findViewById(R.id.ratingBar)
        tvRatingLabel = findViewById(R.id.tvRatingLabel)
        btnRateNow = findViewById(R.id.btnRateNow)
        tvRemindLater = findViewById(R.id.tvRemindLater)

        ratingBar.setOnRatingBarChangeListener { _, rating, _ ->
            tvRatingLabel.text = ratingLabel(rating)
        }

        btnRateNow.setOnClickListener {
            submitRating(ratingBar.rating)
        }

        tvRemindLater.setOnClickListener {
            recordRemindLater()
            Toast.makeText(this, "We'll remind you later", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    private fun ratingLabel(rating: Float): String = when (rating.toInt()) {
        5 -> "Excellent"
        4 -> "Good"
        3 -> "Okay"
        2 -> "Needs work"
        else -> "Poor"
    }

    private fun submitRating(rating: Float) {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            Toast.makeText(this, "Please log in to submit a rating", Toast.LENGTH_SHORT).show()
            return
        }

        val data = hashMapOf(
            "rating" to rating,
            "ratedAt" to FieldValue.serverTimestamp()
        )

        firestore.collection("users").document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Thanks for your feedback!", Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener { e ->
                android.util.Log.e("RateUs", "Firestore write failed", e)
                Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_LONG).show()
            }
    }

    private fun recordRemindLater() {
        val uid = auth.currentUser?.uid ?: return
        firestore.collection("users").document(uid)
            .set(
                hashMapOf("remindRatingLaterAt" to FieldValue.serverTimestamp()),
                SetOptions.merge()
            )
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}