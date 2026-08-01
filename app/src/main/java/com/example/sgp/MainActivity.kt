package com.example.sgp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportActionBar?.hide()

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        Handler(Looper.getMainLooper()).postDelayed({
            routeUser()
        }, 3000) // splash delay
    }

    private fun routeUser() {
        val currentUser = auth.currentUser

        if (currentUser == null) {
            goTo(Login::class.java)
            return
        }

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val cachedUserType = prefs.getString("user_type", null)

        if (cachedUserType != null) {
            // Fast path — trust the cached value
            routeByUserType(cachedUserType)
        } else {
            // Cache missing (e.g. prefs cleared some other way) — confirm from Firestore
            db.collection("users").document(currentUser.uid).get()
                .addOnSuccessListener { doc ->
                    val userData = doc.toObject(Users::class.java)
                    if (userData != null) {
                        prefs.edit()
                            .putString("user_name", userData.name)
                            .putString("user_email", userData.email)
                            .putString("user_location", userData.location)
                            .putString("user_type", userData.userType)
                            .apply()
                        routeByUserType(userData.userType)
                    } else {
                        // No profile found — treat as logged out
                        auth.signOut()
                        goTo(Login::class.java)
                    }
                }
                .addOnFailureListener {
                    // Can't confirm role right now — safest default is Login,
                    // not a silent drop into either dashboard.
                    goTo(Login::class.java)
                }
        }
    }

    private fun routeByUserType(userType: String?) {
        if (userType == "admin") {
            goTo(AdminDashboardActivity::class.java)
        } else {
            goTo(Home::class.java)
        }
    }

    private fun goTo(activity: Class<*>) {
        val intent = Intent(this, activity)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}