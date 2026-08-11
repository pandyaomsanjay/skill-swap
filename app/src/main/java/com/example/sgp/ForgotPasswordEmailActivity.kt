package com.example.sgp

import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import com.example.sgp.api.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ForgotPasswordEmailActivity : BaseActivity() {

    private lateinit var etEmail: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var btnSendOtp: MaterialButton
    private lateinit var tvBackToLogin: TextView
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password_email)

        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowTitleEnabled(false)

        emailLayout = findViewById(R.id.emailLayout)
        etEmail = findViewById(R.id.etEmail)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)
        progressBar = findViewById(R.id.progressBar)

        btnSendOtp.setOnClickListener { sendOtp() }
        tvBackToLogin.setOnClickListener { finish() }
    }

    private fun sendOtp() {
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

        setLoading(true)
        lifecycleScope.launch {
            try {
                // Step 1: check how this account was created
                val loginProvider = AuthRepository.getLoginProvider(email)

                val isSocialLogin = loginProvider != null &&
                        loginProvider.isNotBlank() &&
                        !loginProvider.equals("email", ignoreCase = true) &&
                        !loginProvider.equals("password", ignoreCase = true)

                if (isSocialLogin) {
                    setLoading(false)
                    showSocialLoginDialog(loginProvider!!)
                    return@launch
                }

                // Step 2: normal email/password flow — proceed to OTP
                val result = AuthRepository.forgotPassword(email)
                setLoading(false)

                Toast.makeText(
                    this@ForgotPasswordEmailActivity,
                    result.message,
                    Toast.LENGTH_LONG
                ).show()

                val intent = Intent(this@ForgotPasswordEmailActivity, OtpVerificationActivity::class.java)
                intent.putExtra("email", email)
                startActivity(intent)
            } catch (e: Exception) {
                setLoading(false)
                android.util.Log.e("ForgotPassword", "sendOtp failed: ${e.message}", e)
                Toast.makeText(
                    this@ForgotPasswordEmailActivity,
                    "Something went wrong. Please check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    // ---------- Themed "Password change not available" dialog ----------

    private fun showSocialLoginDialog(loginProvider: String) {
        val providerName = when (loginProvider.lowercase()) {
            "google" -> "Google"
            "apple" -> "Apple"
            else -> loginProvider.replaceFirstChar { it.uppercase() }
        }

        val root = dialogCard()

        // Lock icon in a tinted circle, sized down and centered properly
        val iconCircle = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            }
            radius = dp(28).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(Color.parseColor("#F5EDE1"))
            strokeColor = Color.parseColor("#D4A857")
            strokeWidth = dp(1)

            val iconWrapper = FrameLayout(this@ForgotPasswordEmailActivity)
            iconWrapper.addView(ImageView(this@ForgotPasswordEmailActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22)).apply {
                    gravity = Gravity.CENTER
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                // TODO: replace with your own lock drawable, e.g. R.drawable.ic_lock_outline
                setImageResource(android.R.drawable.ic_lock_idle_lock)
                setColorFilter(Color.parseColor("#1B3C53"))
            })
            addView(iconWrapper)
        }
        root.addView(iconCircle)

        root.addView(TextView(this).apply {
            text = "Password change not available"
            setTextColor(Color.parseColor("#1B3C53"))
            textSize = 15.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "This account was created using $providerName Sign-In. " +
                    "You can't reset a password for it."
            setTextColor(Color.parseColor("#456882"))
            textSize = 12f
            gravity = Gravity.CENTER
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(16)
            }
        })

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnOk = pillButton("OK", Color.parseColor("#1B3C53"), Color.WHITE).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            textSize = 13.5f
            setPadding(0, dp(11), 0, dp(11))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(btnOk)

        dialog.show()
    }

    // ---------- Themed-dialog helpers ----------

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(20), dp(22), dp(18))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(Color.WHITE)
            }
        }
    }

    private fun pillButton(text: String, bgColor: Int, textColor: Int): TextView {
        return TextView(this).apply {
            this.text = text
            setTextColor(textColor)
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }

    private fun setLoading(loading: Boolean) {
        btnSendOtp.isEnabled = !loading
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}