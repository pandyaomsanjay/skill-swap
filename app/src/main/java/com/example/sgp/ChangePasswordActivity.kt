package com.example.sgp

import android.os.Bundle
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.EmailAuthProvider

class ChangePasswordActivity : BaseActivity() {

    private lateinit var etCurrentPassword: TextInputEditText
    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnUpdatePassword: MaterialButton
    private lateinit var progressBar: ProgressBar
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        auth = FirebaseAuth.getInstance()

        etCurrentPassword = findViewById(R.id.etCurrentPassword)
        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnUpdatePassword = findViewById(R.id.btnUpdatePassword)
        progressBar = findViewById(R.id.progressBar)

        btnUpdatePassword.setOnClickListener {
            changePassword()
        }
    }

    private fun changePassword() {
        val currentPwd = etCurrentPassword.text.toString().trim()
        val newPwd = etNewPassword.text.toString().trim()
        val confirmPwd = etConfirmPassword.text.toString().trim()

        if (currentPwd.isEmpty()) {
            etCurrentPassword.error = "Current password required"
            return
        }
        if (newPwd.isEmpty()) {
            etNewPassword.error = "New password required"
            return
        }
        if (newPwd.length < 6) {
            etNewPassword.error = "Password must be at least 6 characters"
            return
        }
        if (newPwd != confirmPwd) {
            etConfirmPassword.error = "Passwords do not match"
            return
        }

        val user = auth.currentUser
        if (user == null || user.email.isNullOrEmpty()) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        progressBar.visibility = ProgressBar.VISIBLE
        btnUpdatePassword.isEnabled = false

        // Re-authenticate before changing password
        val credential = EmailAuthProvider.getCredential(user.email!!, currentPwd)
        user.reauthenticate(credential)
            .addOnSuccessListener {
                user.updatePassword(newPwd)
                    .addOnSuccessListener {
                        progressBar.visibility = ProgressBar.GONE
                        Toast.makeText(this, "Password updated successfully", Toast.LENGTH_LONG).show()
                        finish()
                    }
                    .addOnFailureListener { e ->
                        progressBar.visibility = ProgressBar.GONE
                        btnUpdatePassword.isEnabled = true
                        Toast.makeText(this, "Update failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = ProgressBar.GONE
                btnUpdatePassword.isEnabled = true
                Toast.makeText(this, "Current password is incorrect", Toast.LENGTH_SHORT).show()
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