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

        applyTimerPillColor(TIMER_GREEN)

        findViewById<View>(R.id.otpBoxesContainer).visibility = View.VISIBLE
        findViewById<MaterialButton>(R.id.btnVerify).text = "Verify OTP"
        findViewById<TextView>(R.id.tvResend).text = "Resend OTP"
        otpBoxes.forEachIndexed { i, box ->
            setBoxStyle(i, active = false, filled = false)
        }
        setBoxStyle(0, active = true, filled = false)
        setupOtpBoxes()
        startTimer()

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

        findViewById<TextView>(R.id.tvResend).setOnClickListener {
            resendOtp()
        }
    }

    // ─── Timer (unchanged) ──────────────────────────────────────────
    // (all timer functions remain exactly as before)

    private fun startTimer(totalMs: Long = 600_000L) { /* ... */ }
    private fun applyTimerPillColor(state: Int) { /* ... */ }
    private fun pulseTimer() { /* ... */ }

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
            }
            filled -> {
                card.strokeColor = C_BOX_FILLED_BORDER
                card.setCardBackgroundColor(C_BOX_FILL)
            }
            else -> {
                card.strokeColor = C_BOX_EMPTY_BORDER
                card.setCardBackgroundColor(C_BOX_FILL)
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
    private fun showError(msg: String) { /* ... */ }
    private fun hideError() { /* ... */ }
    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ─── Supabase OTP Verification ───────────────────────────────
    private fun verifyOtp(otp: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                // FIX: OTP was sent via signInWith(OTP) { createUser = true },
                // which issues an EMAIL-type code, not SIGNUP. Verifying with
                // the wrong type throws every time even for a correct code.
                SupabaseClient.client.auth.verifyEmailOtp(
                    type  = OtpType.Email.EMAIL,
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

    // ─── Common Navigation + Dual Save ──────────────────────────
    private fun proceedToProfile() {
        showLoading(true)

        // FIX: wait for the Firestore write to actually succeed before
        // navigating, instead of firing it and moving on immediately.
        val uid = auth.currentUser?.uid ?: email
        db.collection("users").document(uid).set(buildUserMap(uid))
            .addOnSuccessListener {
                // Supabase insert can stay fire-and-forget; it's a secondary
                // record and shouldn't block navigation if it's slow/fails.
                saveToSupabase(uid)

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
        // Insert a login/auth record into the "profiles" table in Supabase
        // Table "profiles" needs columns: id (uuid/text), email, login_provider, created_at
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
                // Insert result ignored; navigation isn't blocked by Supabase failures
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
    }
}