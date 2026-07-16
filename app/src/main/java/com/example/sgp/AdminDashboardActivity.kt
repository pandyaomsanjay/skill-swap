package com.example.sgp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import com.google.firebase.Firebase

class AdminDashboardActivity : BaseActivity() {

    private lateinit var db: FirebaseFirestore
    private lateinit var tvTotalUsers: TextView
    private lateinit var tvTotalTrades: TextView
    private lateinit var tvTotalSkills: TextView
    private lateinit var tvPendingReports: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_dashboard)

        db = Firebase.firestore

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val userType = prefs.getString("user_type", "")
        if (userType != "admin") {
            Toast.makeText(this, "Unauthorized access", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initViews()
        loadStatistics()
    }

    private fun initViews() {
        tvTotalUsers = findViewById(R.id.tvTotalUsers)
        tvTotalTrades = findViewById(R.id.tvTotalTrades)
        tvTotalSkills = findViewById(R.id.tvTotalSkills)
        tvPendingReports = findViewById(R.id.tvPendingReports)

        findViewById<MaterialCardView>(R.id.cardManageUsers).setOnClickListener {
            startActivity(Intent(this, AdminUsersActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardManageSkills).setOnClickListener {
            startActivity(Intent(this, AdminSkillsActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardManageTrades).setOnClickListener {
            startActivity(Intent(this, AdminTradesActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardReports).setOnClickListener {
            startActivity(Intent(this, AdminReportsActivity::class.java))
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            performLogout()
        }
    }

    private fun loadStatistics() {
        db.collection("users").get()
            .addOnSuccessListener { snapshot ->
                tvTotalUsers.text = snapshot.size().toString()
            }
            .addOnFailureListener { /* ignore */ }

        db.collection("trades").get()
            .addOnSuccessListener { snapshot ->
                tvTotalTrades.text = snapshot.size().toString()
            }
            .addOnFailureListener { /* ignore */ }

        db.collection("skills").get()
            .addOnSuccessListener { snapshot ->
                tvTotalSkills.text = snapshot.size().toString()
            }
            .addOnFailureListener { /* ignore */ }

        // Pending reports (if collection exists)
        db.collection("reports").whereEqualTo("status", "pending").get()
            .addOnSuccessListener { snapshot ->
                tvPendingReports.text = snapshot.size().toString()
            }
            .addOnFailureListener {
                tvPendingReports.text = "0"
            }
    }

    private fun performLogout() {
        getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
            .edit().clear().apply()
        val intent = Intent(this, Login::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}