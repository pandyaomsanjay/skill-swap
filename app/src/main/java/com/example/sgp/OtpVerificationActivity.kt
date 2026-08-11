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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.sgp.api.AuthRepository
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class OtpVerificationActivity : BaseActivity() {

    private var email = ""

    private lateinit var progressBar: ProgressBar
    private lateinit var tvError: TextView
    private lateinit var tvTimer: TextView
    private lateinit var tvTimerLabel: TextView
    private lateinit var tvLockMessage: TextView
    private lateinit var timerPill: MaterialCardView
    private lateinit var cardMain: MaterialCardView
    private lateinit var otpBoxes: List<EditText>
    private lateinit var otpCards: List<MaterialCardView>
    private lateinit var tvResend: TextView
    private lateinit var tvEmailInfo: TextView
    private lateinit var toolbar: com.google.android.material.appbar.MaterialToolbar

    private var expiryTimer: CountDownTimer? = null
    private var lockoutTimer: CountDownTimer? = null

    private var wrongAttempts = 0
    private val maxAttempts = 5
    private val lockoutMinutes = 5L

    // ── Palette (matched to signup OTP screen) ──────────────────────
    private val C_CARD               = Color.parseColor("#FFFFFF")
    private val C_CARD_BORDER        = Color.parseColor("#D4A857")
    private val C_BOX_FILL           = Color.parseColor("#F5F5F5")
    private val C_BOX_EMPTY_BORDER   = Color.parseColor("#E0D5C1")
    private val C_BOX_ACTIVE_BORDER  = Color.parseColor("#1B3C53")
    private val C_BOX_FILLED_BORDER  = Color.parseColor("#D4A857")
    private val C_AMBER              = Color.parseColor("#D4A857")
    private val C_TIMER_GREEN_BG     = Color.parseColor("#E8F5E9")
    private val C_TIMER_GREEN_BORDER = Color.parseColor("#4CAF50")
    private val C_GREEN              = Color.parseColor("#4CAF50")
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
    }

    private var currentTimerState = TIMER_GREEN

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_otp_verification)

        email = intent.getStringExtra("email") ?: run {
            finish(); return
        }

        toolbar = findViewById(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }

        progressBar  = findViewById(R.id.progressBar)
        tvError      = findViewById(R.id.tvError)
        tvTimer      = findViewById(R.id.tvTimer)
        tvTimerLabel = findViewById(R.id.tvTimerLabel)
        tvLockMessage = findViewById(R.id.tvLockMessage)
        timerPill    = findViewById(R.id.timerPill)
        cardMain     = findViewById(R.id.cardMain)
        tvResend     = findViewById(R.id.tvResend)
        tvEmailInfo  = findViewById(R.id.tvEmailInfo)

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
        cardMain.strokeWidth = dp(1.5f).toInt()

        // Highlight the email in the subtitle
        val emailText = "Enter the 6-digit code sent to $email"
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
        tvEmailInfo.text = spannable

        // Disable resend initially — same cooldown as the timer itself
        tvResend.isEnabled = false
        tvResend.alpha = 0.5f

        applyTimerPillColor(TIMER_GREEN)
        otpBoxes.forEachIndexed { i, _ -> setBoxStyle(i, active = false, filled = false) }
        setBoxStyle(0, active = true, filled = false)
        setupOtpBoxes()
        startExpiryTimer()

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
            if (tvResend.isEnabled) resendOtp()
        }
    }

    // ─── Timer ──────────────────────────────────────────────

    private fun startExpiryTimer() {
        expiryTimer?.cancel()
        otpBoxes.forEach { it.isEnabled = true }
        findViewById<MaterialButton>(R.id.btnVerify).isEnabled = true
        tvResend.isEnabled = false
        tvResend.alpha = 0.5f

        expiryTimer = object : CountDownTimer(TIMER_DURATION, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                updateTimerDisplay(millisUntilFinished)
                updateTimerState(millisUntilFinished)
            }

            override fun onFinish() {
                updateTimerDisplay(0)
                tvResend.isEnabled = true
                tvResend.alpha = 1.0f
                applyTimerPillColor(TIMER_RED)
                tvTimerLabel.text = "Expired "
                tvTimerLabel.setTextColor(C_RED_LABEL)
                tvTimer.setTextColor(C_RED)
                otpBoxes.forEach { it.isEnabled = false }
                findViewById<MaterialButton>(R.id.btnVerify).isEnabled = false
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
            secondsLeft > 30 -> if (currentTimerState != TIMER_GREEN) applyTimerPillColor(TIMER_GREEN)
            secondsLeft > 10 -> if (currentTimerState != TIMER_AMBER) { applyTimerPillColor(TIMER_AMBER); pulseTimer() }
            else -> if (currentTimerState != TIMER_RED) { applyTimerPillColor(TIMER_RED); pulseTimer() }
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

    // ─── OTP Boxes ─────────────────────────────────

    private fun setupOtpBoxes() {
        otpBoxes.forEachIndexed { index, box ->
            box.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                override fun afterTextChanged(s: Editable?) {
                    val text = s?.toString() ?: ""

                    if (text.length > 1) {
                        box.removeTextChangedListener(this)
                        box.setText(text[0].toString())
                        box.setSelection(1)
                        box.addTextChangedListener(this)
                    }

                    if (text.isNotEmpty()) {
                        setBoxStyle(index, active = false, filled = true)
                        if (index < otpBoxes.size - 1) {
                            otpBoxes[index + 1].requestFocus()
                            setBoxStyle(index + 1, active = true, filled = otpBoxes[index + 1].text.toString().isNotEmpty())
                        } else {
                            box.clearFocus()
                            val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                    as android.view.inputmethod.InputMethodManager
                            imm.hideSoftInputFromWindow(box.windowToken, 0)

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
                setBoxStyle(index, active = hasFocus, filled = box.text.toString().isNotEmpty())
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

    private fun clearBoxes() {
        otpBoxes.forEachIndexed { i, box ->
            box.setText("")
            setBoxStyle(i, active = false, filled = false)
        }
        otpBoxes[0].requestFocus()
        setBoxStyle(0, active = true, filled = false)
    }

    // ─── Error helpers ─────────────────────────────────────────────

    private fun showError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun hideError() {
        tvError.visibility = View.GONE
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    // ─── Verify / Resend (Supabase built-in via AuthRepository) ────

    private fun verifyOtp(otp: String) {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = AuthRepository.verifyOtp(email, otp)
                setLoading(false)

                if (result.success) {
                    expiryTimer?.cancel()
                    val intent = Intent(this@OtpVerificationActivity, ResetPasswordActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    handleWrongOtp(result.message, result.lockedUntil)
                }
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@OtpVerificationActivity, "Something went wrong. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleWrongOtp(message: String, lockedUntil: Long?) {
        wrongAttempts++
        showError(message)
        shakeBoxes()
        clearBoxes()

        if (wrongAttempts >= maxAttempts || lockedUntil != null) {
            lockOtpEntry()
        }
    }

    private fun resendOtp() {
        setLoading(true)
        lifecycleScope.launch {
            try {
                val result = AuthRepository.resendOtp(email)
                setLoading(false)
                Toast.makeText(this@OtpVerificationActivity, result.message, Toast.LENGTH_SHORT).show()
                clearBoxes()
                wrongAttempts = 0
                tvLockMessage.visibility = View.GONE
                hideError()
                startExpiryTimer()
            } catch (e: Exception) {
                setLoading(false)
                Toast.makeText(this@OtpVerificationActivity, "Couldn't resend OTP. Try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ─── Lockout (5 min) ────────────────────────────────

    private fun lockOtpEntry() {
        expiryTimer?.cancel()
        otpBoxes.forEach { it.isEnabled = false }
        findViewById<MaterialButton>(R.id.btnVerify).isEnabled = false
        tvResend.isEnabled = false
        tvResend.alpha = 0.5f
        tvLockMessage.visibility = View.VISIBLE

        lockoutTimer = object : CountDownTimer(lockoutMinutes * 60 * 1000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val mins = millisUntilFinished / 1000 / 60
                val secs = (millisUntilFinished / 1000) % 60
                tvLockMessage.text = "Too many attempts. Try again in $mins:${secs.toString().padStart(2, '0')}"
            }

            override fun onFinish() {
                tvLockMessage.visibility = View.GONE
                wrongAttempts = 0
                clearBoxes()
                startExpiryTimer()
            }
        }.start()
    }

    private fun setLoading(loading: Boolean) {
        progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        findViewById<MaterialButton>(R.id.btnVerify).isEnabled = !loading
    }

    override fun onDestroy() {
        super.onDestroy()
        expiryTimer?.cancel()
        lockoutTimer?.cancel()
    }
}