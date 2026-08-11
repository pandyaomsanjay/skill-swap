package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.sgp.api.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.launch

class ResetPasswordActivity : BaseActivity() {

    private lateinit var etNewPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var btnResetPassword: MaterialButton
    private lateinit var tvMatchIndicator: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var ruleLength: TextView
    private lateinit var ruleUpper: TextView
    private lateinit var ruleNumber: TextView
    private lateinit var ruleSpecial: TextView

    private var lengthOk = false
    private var upperOk = false
    private var numberOk = false
    private var specialOk = false
    private var passwordsMatch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        // No token needed here anymore — reaching this screen means the user
        // already has a valid recovery session established by verifyOtp().

        etNewPassword = findViewById(R.id.etNewPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        btnResetPassword = findViewById(R.id.btnResetPassword)
        tvMatchIndicator = findViewById(R.id.tvMatchIndicator)
        progressBar = findViewById(R.id.progressBar)

        ruleLength = findViewById(R.id.ruleLength)
        ruleUpper = findViewById(R.id.ruleUpper)
        ruleNumber = findViewById(R.id.ruleNumber)
        ruleSpecial = findViewById(R.id.ruleSpecial)

        etNewPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                validatePasswordRules(s?.toString().orEmpty())
                checkMatch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etConfirmPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                checkMatch()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnResetPassword.setOnClickListener { submitNewPassword() }
    }

    private fun validatePasswordRules(password: String) {
        lengthOk = password.length >= 8
        upperOk = password.any { it.isUpperCase() }
        numberOk = password.any { it.isDigit() }
        specialOk = password.any { !it.isLetterOrDigit() }

        markRule(ruleLength, lengthOk)
        markRule(ruleUpper, upperOk)
        markRule(ruleNumber, numberOk)
        markRule(ruleSpecial, specialOk)

        updateSubmitState()
    }

    private fun markRule(view: TextView, satisfied: Boolean) {
        view.setTextColor(if (satisfied) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt())
    }

    private fun checkMatch() {
        val pass = etNewPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()

        if (confirm.isEmpty()) {
            tvMatchIndicator.visibility = View.GONE
            passwordsMatch = false
        } else {
            tvMatchIndicator.visibility = View.VISIBLE
            passwordsMatch = pass == confirm
            if (passwordsMatch) {
                tvMatchIndicator.text = "✓ Passwords match"
                tvMatchIndicator.setTextColor(0xFF4CAF50.toInt())
            } else {
                tvMatchIndicator.text = "✗ Passwords don't match"
                tvMatchIndicator.setTextColor(0xFFD32F2F.toInt())
            }
        }
        updateSubmitState()
    }

    private fun updateSubmitState() {
        btnResetPassword.isEnabled = lengthOk && upperOk && numberOk && specialOk && passwordsMatch
    }

    private fun submitNewPassword() {
        val newPassword = etNewPassword.text.toString()
        setLoading(true)

        lifecycleScope.launch {
            try {
                val result = AuthRepository.resetPassword(newPassword)
                setLoading(false)

                if (result.success) {
                    Toast.makeText(this@ResetPasswordActivity, result.message, Toast.LENGTH_LONG).show()

                    val intent = Intent(this@ResetPasswordActivity, Login::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    Toast.makeText(this@ResetPasswordActivity, result.message, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@ResetPasswordActivity, "Something went wrong. Try again.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        btnResetPassword.isEnabled = !loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }
}