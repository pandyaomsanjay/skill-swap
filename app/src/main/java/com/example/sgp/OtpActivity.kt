package com.example.sgp

import android.animation.ValueAnimator
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.CountDownTimer
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.animation.CycleInterpolator
import android.view.animation.TranslateAnimation
import android.widget.EditText
import io.github.jan.supabase.postgrest.postgrest
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.auth.OtpType
import kotlinx.coroutines.tasks.await
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.*

class OtpActivity : BaseActivity() {

    private var email = ""
    private var password = ""
    private var isGoogle = false
    private var useFirebase = false

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvTimerLabel: TextView
    private lateinit var timerPill: MaterialCardView
    private lateinit var cardMain: MaterialCardView
    private lateinit var otpBoxes: List<EditText>
    private lateinit var otpCards: List<MaterialCardView>
    private lateinit var countDownTimer: CountDownTimer
    private var checkJob: Job? = null

    // ── Palette (unchanged) ──────────────────────────────────────
    private val C_CARD               = Color.parseColor("#1E293B")
    private val C_CARD_BORDER        = Color.parseColor("#C27803")
    private val C_BOX_FILL           = Color.parseColor("#131F2E")
    private val C_BOX_EMPTY_BORDER   = Color.parseColor("#1E2240")
    private val C_BOX_ACTIVE_BORDER  = Color.parseColor("#FBBF24")
    private val C_BOX_FILLED_BORDER  = Color.parseColor("#C27803")
    private val C_AMBER              = Color.parseColor("#FBBF24")
    private val C_GOLDEN             = Color.parseColor("#C27803")
    private val C_TIMER_GREEN_BG     = Color.parseColor("#0D2218")
    private val C_TIMER_GREEN_BORDER = Color.parseColor("#166534")
    private val C_GREEN              = Color.parseColor("#4ADE80")
    private val C_GREEN_LIGHT        = Color.parseColor("#86EFAC")
    private val C_TIMER_AMBER_BG     = Color.parseColor("#1C1500")
    private val C_TIMER_AMBER_BORDER = Color.parseColor("#C27803")
    private val C_AMBER_LABEL        = Color.parseColor("#FDE68A")
    private val C_TIMER_RED_BG       = Color.parseColor("#2A0D0D")
    private val C_TIMER_RED_BORDER   = Color.parseColor("#7F1D1D")
    private val C_RED                = Color.parseColor("#F87171")
    private val C_RED_LABEL          = Color.parseColor("#FCA5A5")

    companion object {
        const val TIMER_GREEN = 0
        const val TIMER_AMBER = 1
        const val TIMER_RED   = 2
    }

    private var currentTimerState = TIMER_GREEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        email    = intent.getStringExtra("email") ?: ""
        password = intent.getStringExtra("password") ?: ""
        isGoogle = intent.getBooleanExtra("isGoogle", false)
        useFirebase = intent.getBooleanExtra("useFirebase", false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // ── Bind views ──────────────────────────────────────────────
        progressBar  = findViewById(R.id.progressBar)
        tvError      = findViewById(R.id.tvError)
        tvTimer      = findViewById(R.id.tvTimer)
        tvTimerLabel = findViewById(R.id.tvTimerLabel)
        timerPill    = findViewById(R.id.timerPill)
        cardMain     = findViewById(R.id.cardMain)

        otpCards = listOf(
            findViewById(R.id.cardOtp1),
            findViewById(R.id.cardOtp2),
            findViewById(R.id.cardOtp3),
            findViewById(R.id.cardOtp4),
            findViewById(R.id.cardOtp5),
            findViewById(R.id.cardOtp6)
        )

        otpBoxes = listOf(
            findViewById(R.id.otp1), findViewById(R.id.otp2), findViewById(R.id.otp3),
            findViewById(R.id.otp4), findViewById(R.id.otp5), findViewById(R.id.otp6)
        )

        cardMain.setCardBackgroundColor(C_CARD)
        cardMain.strokeColor = C_CARD_BORDER
        cardMain.strokeWidth = dp(1f).toInt()

        val emailText = if (useFirebase) {
            "Verification link sent to $email"
        } else {
            if (isGoogle) "OTP sent to $email (Google sign‑in)" else "OTP sent to $email"
        }
        findViewById<TextView>(R.id.tvEmailInfo).apply {
            val spannable = android.text.SpannableString(emailText)
            val start = emailText.indexOf(email)
            if (start >= 0) {
                spannable.setSpan(
                    android.text.style.ForegroundColorSpan(C_AMBER),
                    start, start + email.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    android.text.style.StyleSpan(android.graphics.Typeface.BOLD),
                    start, start + email.length,
                    android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            setText(spannable)
        }

        applyTimerPillColor(TIMER_GREEN)

        if (useFirebase) {
            findViewById<View>(R.id.otpBoxesContainer).visibility = View.GONE
            findViewById<TextView>(R.id.tvTimerLabel).text = "Check your email"
            tvTimer.text = "Click the link to verify"
            findViewById<MaterialButton>(R.id.btnVerify).text = "I've verified"
            findViewById<TextView>(R.id.tvResend).text = "Resend Link"
            startTimer(60_000L)
            startFirebaseCheck()
        } else {
            findViewById<View>(R.id.otpBoxesContainer).visibility = View.VISIBLE
            findViewById<MaterialButton>(R.id.btnVerify).text = "Verify OTP"
            findViewById<TextView>(R.id.tvResend).text = "Resend OTP"
            otpBoxes.forEachIndexed { i, box ->
                setBoxStyle(i, active = false, filled = false)
            }
            setBoxStyle(0, active = true, filled = false)
            setupOtpBoxes()
            startTimer()
        }

        findViewById<MaterialButton>(R.id.btnVerify).setOnClickListener {
            if (useFirebase) {
                checkFirebaseVerification()
            } else {
                val otp = otpBoxes.joinToString("") { it.text.toString().trim() }
                if (otp.length != 6) {
                    showError("Please enter the complete 6-digit code")
                    shakeBoxes()
                    return@setOnClickListener
                }
                hideError()
                verifyOtp(otp)
            }
        }

        findViewById<TextView>(R.id.tvResend).setOnClickListener {
            if (useFirebase) resendFirebaseLink() else resendOtp()
        }
    }

    // ─── Timer (unchanged) ──────────────────────────────────────────
    // (all timer functions remain exactly as before)

    private fun startTimer(totalMs: Long = 600_000L) { /* ... */ }
    private fun applyTimerPillColor(state: Int) { /* ... */ }
    private fun pulseTimer() { /* ... */ }

    // ─── OTP Box Logic (unchanged) ─────────────────────────────────
    private fun setupOtpBoxes() { /* ... */ }
    private fun setBoxStyle(index: Int, active: Boolean, filled: Boolean) { /* ... */ }
    private fun shakeBoxes() { /* ... */ }

    // ─── Error Helpers ─────────────────────────────────────────────
    private fun showError(msg: String) { /* ... */ }
    private fun hideError() { /* ... */ }
    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ─── Supabase OTP Verification ───────────────────────────────
    private fun verifyOtp(otp: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.verifyEmailOtp(
                    type  = OtpType.Email.SIGNUP,
                    email = email,
                    token = otp
                )

                if (!isGoogle && password.isNotEmpty()) {
                    auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (!task.isSuccessful) {
                                showLoading(false)
                                showError("Firebase registration failed: ${task.exception?.message}")
                                return@addOnCompleteListener
                            }
                            proceedToProfile()
                        }
                } else {
                    proceedToProfile()
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("Invalid or expired OTP. Please try again.")
                shakeBoxes()
                otpBoxes.forEach { it.setText("") }
                otpBoxes[0].requestFocus()
            }
        }
    }

    private fun resendOtp() {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(OTP) {
                    this.email = this@OtpActivity.email
                    createUser = true
                }
                otpBoxes.forEachIndexed { i, box ->
                    box.setText("")
                    setBoxStyle(i, active = false, filled = false)
                }
                otpBoxes[0].requestFocus()
                setBoxStyle(0, active = true, filled = false)
                hideError()
                startTimer()
                Toast.makeText(this@OtpActivity, "New OTP sent to $email ✓", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@OtpActivity, "Failed to resend OTP. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Firebase Email Verification ─────────────────────────────
    private fun startFirebaseCheck() {
        checkJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(3000)
                val user = auth.currentUser
                user?.reload()?.await()
                if (user?.isEmailVerified == true) {
                    withContext(Dispatchers.Main) {
                        onEmailVerified()
                    }
                    cancel()
                }
            }
        }
    }

    private suspend fun FirebaseAuth.reload(): Boolean {
        val user = currentUser ?: return false
        return try {
            user.reload().await()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun checkFirebaseVerification() {
        val user = auth.currentUser
        user?.reload()?.addOnCompleteListener { task ->
            if (task.isSuccessful && user.isEmailVerified) {
                onEmailVerified()
            } else {
                showError("Email not verified yet. Please check your inbox and click the link.")
            }
        }
    }

    private fun onEmailVerified() {
        Toast.makeText(this, "Email verified! 🎉", Toast.LENGTH_SHORT).show()
        proceedToProfile()
    }

    private fun resendFirebaseLink() {
        val user = auth.currentUser
        user?.sendEmailVerification()
            ?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Verification link resent", Toast.LENGTH_SHORT).show()
                    startTimer(60_000L)
                } else {
                    Toast.makeText(this, "Failed to resend: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    // ─── Common Navigation + Dual Save ──────────────────────────
    private fun proceedToProfile() {
        showLoading(false)
        // Save to Firestore
        saveToFirestore()
        // Save to Supabase (profiles table)
        saveToSupabase()

        // Save session
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("user_email", email)
            putInt("user_points", 1250)
            apply()
        }

        startActivity(Intent(this, CompleteProfileActivity::class.java).apply {
            putExtra("email", email)
            putExtra("password", password)
            putExtra("isGoogle", isGoogle)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun saveToFirestore() {
        val uid = auth.currentUser?.uid ?: email
        val userMap = hashMapOf(
            "email" to email,
            "name" to "",
            "profileImage" to "",
            "location" to "",
            "latitude" to 0.0,
            "longitude" to 0.0,
            "isLocationVerified" to false,
            "loginProvider" to if (isGoogle) "google" else "email",
            "createdAt" to System.currentTimeMillis(),
            "rating" to 0.0,
            "completedTrades" to 0,
            "credits" to 1250,
            "userType" to "standard"
        )
        db.collection("users").document(uid).set(userMap)
            .addOnSuccessListener { /* success */ }
            .addOnFailureListener { e -> e.printStackTrace() }
    }

    private fun saveToSupabase() {
        // Insert a row into the "profiles" table in Supabase
        // You need to have a table named "profiles" with columns: id (uuid), email, name, etc.
        lifecycleScope.launch {
            try {
                val uid = auth.currentUser?.uid ?: email
                SupabaseClient.client.postgrest["profiles"].insert(
                    mapOf(
                        "id" to uid,
                        "email" to email,
                        "name" to "",
                        "login_provider" to if (isGoogle) "google" else "email",
                        "created_at" to System.currentTimeMillis()
                    )
                )
                // You can ignore the response or check for success
            } catch (e: Exception) {
                e.printStackTrace()
                // Don't block navigation if Supabase insert fails
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnVerify).isEnabled = !show
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::countDownTimer.isInitialized) countDownTimer.cancel()
        checkJob?.cancel()
    }
}