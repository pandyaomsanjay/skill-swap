package com.example.sgp

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.example.sgp.api.AuthRepository
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.launch

class ForgotPasswordEmailActivity : BaseActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var etEmail: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var btnSendOtp: MaterialButton
    private lateinit var tvBackToLogin: TextView
    private lateinit var progressBar: ProgressBar

    private var isDarkModeEnabled = false

    // ---------------------------------------------------------------------
    // LIGHT / DARK PALETTE (for the themed dialog)
    // ---------------------------------------------------------------------
    private val lightDialogBg = Color.WHITE
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightIconCircleBg = Color.parseColor("#F5EDE1")
    private val lightIconCircleStroke = Color.parseColor("#D4A857")

    private val darkDialogBg = Color.parseColor("#16232E")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkIconCircleBg = Color.parseColor("#3A2E14")
    private val darkIconCircleStroke = Color.parseColor("#E8C468")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password_email)

        isDarkModeEnabled = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        toolbar = findViewById(R.id.toolbar)
        emailLayout = findViewById(R.id.emailLayout)
        etEmail = findViewById(R.id.etEmail)
        btnSendOtp = findViewById(R.id.btnSendOtp)
        tvBackToLogin = findViewById(R.id.tvBackToLogin)
        progressBar = findViewById(R.id.progressBar)

        // ── Push the toolbar down past the status bar (+20dp for more breathing room).
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop + dp(20))
            insets
        }

        // ── Direct navigation click — always fires, regardless of inset overlap.
        toolbar.setNavigationOnClickListener { finish() }

        // Pre-fill when launched from a screen that already knows the user's email
        intent.getStringExtra("email")?.takeIf { it.isNotBlank() }?.let {
            etEmail.setText(it)
            etEmail.setSelection(it.length)
        }

        btnSendOtp.setOnClickListener { sendOtp() }
        tvBackToLogin.setOnClickListener { finish() }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        isDarkModeEnabled = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(SettingsActivity.KEY_DARK_MODE, false)
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

        val iconCircle = MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(14)
            }
            radius = dp(28).toFloat()
            cardElevation = 0f
            setCardBackgroundColor(if (isDarkModeEnabled) darkIconCircleBg else lightIconCircleBg)
            strokeColor = if (isDarkModeEnabled) darkIconCircleStroke else lightIconCircleStroke
            strokeWidth = dp(1)

            val iconWrapper = FrameLayout(this@ForgotPasswordEmailActivity)
            iconWrapper.addView(ImageView(this@ForgotPasswordEmailActivity).apply {
                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22)).apply {
                    gravity = Gravity.CENTER
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(android.R.drawable.ic_lock_idle_lock)
                setColorFilter(if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary)
            })
            addView(iconWrapper)
        }
        root.addView(iconCircle)

        root.addView(TextView(this).apply {
            text = "Password change not available"
            setTextColor(if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary)
            textSize = 15.5f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        root.addView(TextView(this).apply {
            text = "This account was created using $providerName Sign-In. " +
                    "You can't reset a password for it."
            setTextColor(if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary)
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

        val btnOk = pillButton(
            "OK",
            if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary,
            if (isDarkModeEnabled) Color.parseColor("#16232E") else Color.WHITE
        ).apply {
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
                setColor(if (isDarkModeEnabled) darkDialogBg else lightDialogBg)
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
}