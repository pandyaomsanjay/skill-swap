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
import com.onesignal.OneSignal
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.postgrest.Postgrest
import kotlinx.coroutines.*

class OtpActivity : BaseActivity() {

    private var email = ""
    private var password = ""
    private var isGoogle = false

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
    private lateinit var tvResend: TextView

    // ── Palette (matched to SkillSwap design) ──────────────────────
    private val C_CARD               = Color.parseColor("#FFFFFF") // Surface color
    private val C_CARD_BORDER        = Color.parseColor("#D4A857") // Brand tan
    private val C_BOX_FILL           = Color.parseColor("#F5F5F5") // Light surface
    private val C_BOX_EMPTY_BORDER   = Color.parseColor("#E0D5C1") // Light tan
    private val C_BOX_ACTIVE_BORDER  = Color.parseColor("#1B3C53") // Brand navy
    private val C_BOX_FILLED_BORDER  = Color.parseColor("#D4A857") // Brand tan
    private val C_AMBER              = Color.parseColor("#D4A857") // Brand tan
    private val C_GOLDEN             = Color.parseColor("#C27803") // Darker gold
    private val C_TIMER_GREEN_BG     = Color.parseColor("#E8F5E9")
    private val C_TIMER_GREEN_BORDER = Color.parseColor("#4CAF50")
    private val C_GREEN              = Color.parseColor("#4CAF50")
    private val C_GREEN_LIGHT        = Color.parseColor("#86EFAC")
    private val C_TIMER_AMBER_BG     = Color.parseColor("#FFF3E0")
    private val C_TIMER_AMBER_BORDER = Color.parseColor("#FF9800")
    private val C_AMBER_LABEL        = Color.parseColor("#FF9800")
    private val C_TIMER_RED_BG       = Color.parseColor("#FFEBEE")
    private val C_TIMER_RED_BORDER   = Color.parseColor("#F44336")
    private val C_RED                = Color.parseColor("#F44336")
    private val C_RED_LABEL          = Color.parseColor("#F44336")

    companion object {
        const val TIMER_GREEN = 0
        const val TIMER_AMBER = 1
        const val TIMER_RED   = 2
        const val TIMER_DURATION = 60000L // 60 seconds

        // How many times to retry setting the Supabase password if the
        // session hasn't propagated yet right after OTP verification.
        private const val PASSWORD_SET_MAX_RETRIES = 3
        private const val PASSWORD_SET_RETRY_DELAY_MS = 600L
    }

    private var currentTimerState = TIMER_GREEN
    private var remainingTime = TIMER_DURATION
    private var isTimerRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp)

        email    = intent.getStringExtra("email") ?: ""
        password = intent.getStringExtra("password") ?: ""
        isGoogle = intent.getBooleanExtra("isGoogle", false)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        // ── Bind views ──────────────────────────────────────────────
        progressBar  = findViewById(R.id.progressBar)
        tvError      = findViewById(R.id.tvError)
        tvTimer      = findViewById(R.id.tvTimer)
        tvTimerLabel = findViewById(R.id.tvTimerLabel)
        timerPill    = findViewById(R.id.timerPill)
        cardMain     = findViewById(R.id.cardMain)
        tvResend     = findViewById(R.id.tvResend)

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

        // Set card styles
        cardMain.setCardBackgroundColor(C_CARD)
        cardMain.strokeColor = C_CARD_BORDER
        cardMain.strokeWidth = dp(1.5f).toInt()

        // Set email info with highlighted email
        val emailText = if (isGoogle) "OTP sent to $email (Google sign‑in)" else "OTP sent to $email"
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

        // Disable resend initially
        tvResend.isEnabled = false
        tvResend.alpha = 0.5f

        // Initialize timer display
        applyTimerPillColor(TIMER_GREEN)
        updateTimerDisplay(TIMER_DURATION)
        startTimer()

        // Setup OTP boxes
        findViewById<View>(R.id.otpBoxesContainer).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btnVerify).text = "Verify OTP"
        tvResend.text = "Resend OTP"

        otpBoxes.forEachIndexed { i, box ->
            setBoxStyle(i, active = false, filled = false)
        }
        setBoxStyle(0, active = true, filled = false)
        setupOtpBoxes()

        // ── Button Click Listeners ──────────────────────────────────
        findViewById<MaterialButton>(R.id.btnVerify).setOnClickListener {
            val otp = otpBoxes.joinToString("") { it.text.toString().trim() }
            if (otp.length != 6) {
                showError("Please enter the complete 6-digit code")
                shakeBoxes()
                return@setOnClickListener
            }
            hideError()
            verifyOtp(otp)
        }

        tvResend.setOnClickListener {
            if (isTimerRunning) {
                Toast.makeText(this, "Please wait for the timer to expire", Toast.LENGTH_SHORT).show()
            } else {
                resendOtp()
            }
        }
    }

    // ─── Timer Implementation ──────────────────────────────────────────

    private fun startTimer() {
        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }

        remainingTime = TIMER_DURATION
        isTimerRunning = true
        tvResend.isEnabled = false
        tvResend.alpha = 0.5f

        countDownTimer = object : CountDownTimer(TIMER_DURATION, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                remainingTime = millisUntilFinished
                updateTimerDisplay(millisUntilFinished)
                updateTimerState(millisUntilFinished)
            }

            override fun onFinish() {
                remainingTime = 0
                updateTimerDisplay(0)
                isTimerRunning = false
                tvResend.isEnabled = true
                tvResend.alpha = 1.0f
                applyTimerPillColor(TIMER_RED)

                // Update label
                tvTimerLabel.text = "Expired "
                tvTimerLabel.setTextColor(C_RED_LABEL)
                tvTimer.setTextColor(C_RED)
            }
        }.start()
    }

    private fun updateTimerDisplay(millisUntilFinished: Long) {
        val seconds = (millisUntilFinished / 1000).toInt()
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        tvTimer.text = String.format("%02d:%02d", minutes, remainingSeconds)
    }

    private fun updateTimerState(millisUntilFinished: Long) {
        val secondsLeft = millisUntilFinished / 1000
        when {
            secondsLeft > 30 -> {
                if (currentTimerState != TIMER_GREEN) {
                    applyTimerPillColor(TIMER_GREEN)
                }
            }
            secondsLeft > 10 -> {
                if (currentTimerState != TIMER_AMBER) {
                    applyTimerPillColor(TIMER_AMBER)
                    pulseTimer()
                }
            }
            else -> {
                if (currentTimerState != TIMER_RED) {
                    applyTimerPillColor(TIMER_RED)
                    pulseTimer()
                }
            }
        }
    }

    private fun applyTimerPillColor(state: Int) {
        currentTimerState = state
        when (state) {
            TIMER_GREEN -> {
                timerPill.setCardBackgroundColor(C_TIMER_GREEN_BG)
                timerPill.strokeColor = C_TIMER_GREEN_BORDER
                tvTimerLabel.setTextColor(C_GREEN)
                tvTimer.setTextColor(C_GREEN)
                tvTimerLabel.text = "Expires in "
            }
            TIMER_AMBER -> {
                timerPill.setCardBackgroundColor(C_TIMER_AMBER_BG)
                timerPill.strokeColor = C_TIMER_AMBER_BORDER
                tvTimerLabel.setTextColor(C_AMBER_LABEL)
                tvTimer.setTextColor(C_AMBER_LABEL)
                tvTimerLabel.text = "Expires in "
            }
            TIMER_RED -> {
                timerPill.setCardBackgroundColor(C_TIMER_RED_BG)
                timerPill.strokeColor = C_TIMER_RED_BORDER
                tvTimerLabel.setTextColor(C_RED_LABEL)
                tvTimer.setTextColor(C_RED_LABEL)
                tvTimerLabel.text = "Expired "
            }
        }
    }

    private fun pulseTimer() {
        val anim = TranslateAnimation(0f, 0f, 0f, -4f).apply {
            duration = 500
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        timerPill.startAnimation(anim)
    }

    // ─── OTP Box Logic ─────────────────────────────────

    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { index, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString() ?: ""

                    // If user pastes/types more than one char, keep only the first
                    if (text.length > 1) {
                        box.removeTextChangedListener(this)
                        box.setText(text[0].toString())
                        box.setSelection(1)
                        box.addTextChangedListener(this)
                    }

                    if (text.isNotEmpty()) {
                        setBoxStyle(index, active = false, filled = true)
                        if (index < otpBoxes.size - 1) {
                            // Move to next box
                            otpBoxes[index + 1].requestFocus()
                            setBoxStyle(index + 1, active = true, filled = otpBoxes[index + 1].text.toString().isNotEmpty())
                        } else {
                            // Last box filled — drop focus / hide keyboard
                            box.clearFocus()
                            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                    as android.view.inputmethod.InputMethodManager
                            imm.hideSoftInputFromWindow(box.windowToken, 0)

                            // Auto-verify once all 6 digits are entered
                            val otp = otpBoxes.joinToString("") { it.text.toString().trim() }
                            if (otp.length == 6) {
                                hideError()
                                verifyOtp(otp)
                            }
                        }
                    } else {
                        setBoxStyle(index, active = true, filled = false)
                    }
                }
            })

            box.setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_DEL && event.action == KeyEvent.ACTION_DOWN) {
                    if (box.text.toString().isEmpty() && index > 0) {
                        // Empty box + backspace → go back and clear previous box
                        val prev = otpBoxes[index - 1]
                        prev.requestFocus()
                        prev.setText("")
                        setBoxStyle(index - 1, active = true, filled = false)
                        setBoxStyle(index, active = false, filled = false)
                        return@setOnKeyListener true
                    }
                }
                false
            }

            box.setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    setBoxStyle(index, active = true, filled = box.text.toString().isNotEmpty())
                } else {
                    setBoxStyle(index, active = false, filled = box.text.toString().isNotEmpty())
                }
            }
        }
    }

    private fun setBoxStyle(index: Int, active: Boolean, filled: Boolean) {
        val card = otpCards[index]
        when {
            active -> {
                card.strokeColor = C_BOX_ACTIVE_BORDER
                card.setCardBackgroundColor(C_BOX_FILL)
                card.strokeWidth = dp(2.5f).toInt()
            }
            filled -> {
                card.strokeColor = C_BOX_FILLED_BORDER
                card.setCardBackgroundColor(C_BOX_FILL)
                card.strokeWidth = dp(1.5f).toInt()
            }
            else -> {
                card.strokeColor = C_BOX_EMPTY_BORDER
                card.setCardBackgroundColor(C_BOX_FILL)
                card.strokeWidth = dp(1.5f).toInt()
            }
        }
    }

    private fun shakeBoxes() {
        otpCards.forEach { card ->
            val anim = TranslateAnimation(0f, 12f, 0f, 0f).apply {
                duration = 400
                interpolator = CycleInterpolator(5f)
            }
            card.startAnimation(anim)
        }
    }

    // ─── Error Helpers ─────────────────────────────────────────────

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ─── Supabase OTP Verification ───────────────────────────────

    private fun verifyOtp(otp: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.verifyEmailOtp(
                    type  = OtpType.Email.EMAIL,
                    email = email,
                    token = otp
                )

                if (!isGoogle && password.isNotEmpty()) {
                    // verifyEmailOtp() above creates/authenticates the Supabase
                    // user passwordless. Without explicitly setting a password
                    // here, email/password login will NEVER work for this
                    // account — Supabase has no password on file to check
                    // against. This step is therefore required, not optional:
                    // if it fails, we must stop and let the user retry rather
                    // than silently continuing into a half-created account.
                    val passwordSet = setSupabasePasswordWithRetry(password)

                    if (!passwordSet) {
                        showLoading(false)
                        showError(
                            "We verified your code, but couldn't finish setting up " +
                                    "your account password. Please try again."
                        )
                        // Let them re-trigger verification (session from the OTP
                        // is still valid) instead of pushing them into Firebase
                        // creation with a broken Supabase credential.
                        return@launch
                    }

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

    /**
     * Sets the password on the just-verified Supabase Auth user.
     *
     * Immediately after verifyEmailOtp() succeeds, the SDK's in-memory
     * session can occasionally lag a beat before updateUser() is able to
     * use it (session propagation race). We retry a few times with a short
     * delay before giving up, and log every failure so it's visible instead
     * of silently swallowed.
     *
     * @return true if the password was set successfully, false otherwise.
     */
    private suspend fun setSupabasePasswordWithRetry(newPassword: String): Boolean {
        repeat(PASSWORD_SET_MAX_RETRIES) { attempt ->
            val session = SupabaseClient.client.auth.currentSessionOrNull()
            if (session == null) {
                android.util.Log.w(
                    "OtpActivity",
                    "No active Supabase session yet (attempt ${attempt + 1}/$PASSWORD_SET_MAX_RETRIES) — retrying"
                )
            } else {
                try {
                    SupabaseClient.client.auth.updateUser {
                        this.password = newPassword
                    }
                    return true
                } catch (e: Exception) {
                    android.util.Log.e(
                        "OtpActivity",
                        "Failed to set Supabase password (attempt ${attempt + 1}/$PASSWORD_SET_MAX_RETRIES): ${e.message}",
                        e
                    )
                }
            }
            if (attempt < PASSWORD_SET_MAX_RETRIES - 1) {
                delay(PASSWORD_SET_RETRY_DELAY_MS)
            }
        }
        return false
    }

    private fun resendOtp() {
        lifecycleScope.launch {
            try {
                // Reset timer first
                if (::countDownTimer.isInitialized) {
                    countDownTimer.cancel()
                }

                SupabaseClient.client.auth.signInWith(OTP) {
                    this.email = this@OtpActivity.email
                    createUser = true
                }

                // Clear OTP boxes
                otpBoxes.forEachIndexed { i, box ->
                    box.setText("")
                    setBoxStyle(i, active = false, filled = false)
                }
                otpBoxes[0].requestFocus()
                setBoxStyle(0, active = true, filled = false)
                hideError()

                // Restart timer
                startTimer()

                Toast.makeText(this@OtpActivity, "New OTP sent to $email ✓", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@OtpActivity, "Failed to resend OTP. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Common Navigation + Dual Save ──────────────────────────

    private fun proceedToProfile() {
        showLoading(true)

        val uid = auth.currentUser?.uid ?: email
        db.collection("users").document(uid).set(buildUserMap(uid))
            .addOnSuccessListener {
                saveToSupabase(uid)

                // Tie this device's push subscription to the new user
                OneSignal.login(uid)

                val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                with(prefs.edit()) {
                    putString("user_email", email)
                    putInt("user_points", 1250)
                    apply()
                }

                showLoading(false)
                startActivity(Intent(this, CompleteProfileActivity::class.java).apply {
                    putExtra("email", email)
                    putExtra("password", password)
                    putExtra("isGoogle", isGoogle)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .addOnFailureListener { e ->
                showLoading(false)
                showError("Failed to create profile: ${e.message}")
            }
    }

    private fun buildUserMap(uid: String): HashMap<String, Any> {
        return hashMapOf(
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
    }

    private fun saveToSupabase(uid: String) {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.postgrest["profiles"].insert(
                    mapOf(
                        "id" to uid,
                        "email" to email,
                        "name" to "",
                        "login_provider" to if (isGoogle) "google" else "email",
                        "created_at" to System.currentTimeMillis()
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnVerify).isEnabled = !show
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::countDownTimer.isInitialized) {
            countDownTimer.cancel()
        }
    }
}