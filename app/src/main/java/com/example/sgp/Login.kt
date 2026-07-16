package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore

class Login : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = Firebase.firestore
        progressBar = findViewById(R.id.progressBar)

        // Auto-login check using Firebase Auth
        if (auth.currentUser != null) {
            startActivity(Intent(this, Home::class.java))
            finish()
            return
        }

        initializeViews()
    }

    private fun initializeViews() {
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<Button>(R.id.tvCreateAccount)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)

        val emailInput = findViewById<TextInputLayout>(R.id.email)
        val passwordInput = findViewById<TextInputLayout>(R.id.password)

        btnLogin.setOnClickListener {
            val email = emailInput.editText?.text.toString().trim()
            val password = passwordInput.editText?.text.toString()

            if (validateForm(email, password)) {
                loginUser(email, password)
            }
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, Createaccount::class.java))
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }
    }

    private fun validateForm(email: String, password: String): Boolean {
        var isValid = true

        val emailLayout = findViewById<TextInputLayout>(R.id.email)
        val passwordLayout = findViewById<TextInputLayout>(R.id.password)

        emailLayout.error = null
        passwordLayout.error = null

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter a valid email"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordLayout.error = "Password required"
            isValid = false
        }

        return isValid
    }

    private fun loginUser(email: String, password: String) {
        showLoading(true)

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                showLoading(false)
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        // Fetch user data from Firestore
                        db.collection("users").document(user.uid).get()
                            .addOnSuccessListener { doc ->
                                val userData = doc.toObject(Users::class.java)
                                if (userData != null) {
                                    // Save session in SharedPreferences
                                    val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                                    prefs.edit()
                                        .putString("user_name", userData.name)
                                        .putString("user_email", userData.email)
                                        .putString("user_location", userData.location)
                                        .putString("user_type", userData.userType)
                                        .apply()

                                    Toast.makeText(this@Login, "Login successful", Toast.LENGTH_SHORT).show()

                                    val intent = if (userData.userType == "admin") {
                                        Intent(this@Login, AdminDashboardActivity::class.java)
                                    } else {
                                        Intent(this@Login, Home::class.java)
                                    }
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    startActivity(intent)
                                    finish()
                                } else {
                                    showError("User data not found")
                                }
                            }
                            .addOnFailureListener { e ->
                                showError("Error fetching user data: ${e.message}")
                            }
                    }
                } else {
                    showError("Authentication failed: ${task.exception?.message}")
                }
            }
    }

    private fun showError(message: String) {
        Snackbar.make(
            findViewById(android.R.id.content),
            message,
            Snackbar.LENGTH_LONG
        ).show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnLogin).isEnabled = !show
    }
}