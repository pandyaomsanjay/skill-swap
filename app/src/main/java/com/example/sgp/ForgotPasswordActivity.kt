package com.example.sgp

import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth

class ForgotPasswordActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var etEmail: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var btnSend: MaterialButton
    private lateinit var tvBackToLogin: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        auth = FirebaseAuth.getInstance()

        // Setup toolbar
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        emailLayout = findViewById(R.id.emailLayout)
        etEmail = findViewById(R.id.etEmail)
        btnSend = findViewById(R.id.btnSend)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)

        btnSend.setOnClickListener {
            sendPasswordResetEmail()
        }

        tvBackToLogin.setOnClickListener {
            finish()
        }
    }

    private fun sendPasswordResetEmail() {
        val email = etEmail.text.toString().trim()
        emailLayout.error = null

        if (email.isEmpty()) {
            emailLayout.error = "Email is required"
            etEmail.requestFocus()
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter a valid email address"
            etEmail.requestFocus()
            return
        }

        btnSend.isEnabled = false
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                btnSend.isEnabled = true
                if (task.isSuccessful) {
                    Toast.makeText(
                        this,
                        "Password reset link sent to $email",
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                } else {
                    Toast.makeText(
                        this,
                        "Failed to send reset email: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}