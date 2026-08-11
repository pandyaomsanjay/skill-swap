package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat

class Createaccount : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var etConfirmPassword: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var confirmPasswordLayout: TextInputLayout
    private lateinit var tvMatchIndicator: TextView
    private lateinit var termsCheckbox: CheckBox
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var tvSignIn: TextView
    private lateinit var tvTermsLink: TextView

    private lateinit var ruleLength: TextView
    private lateinit var ruleUpper: TextView
    private lateinit var ruleNumber: TextView
    private lateinit var ruleSpecial: TextView

    private var lengthOk = false
    private var upperOk = false
    private var numberOk = false
    private var specialOk = false
    private var passwordsMatch = false

    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@(.+)$")

    private val termsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            termsCheckbox.isChecked = true
            applyCheckedStyle()
            Toast.makeText(this, "Terms accepted ✓", Toast.LENGTH_SHORT).show()
        } else {
            termsCheckbox.isChecked = false
            applyUncheckedStyle()
        }
    }

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account?.idToken
                    val email = account?.email

                    if (idToken == null) {
                        Log.e("GoogleSignIn", "idToken is null — check default_web_client_id / SHA-1 config")
                        Toast.makeText(this, "Google sign-in failed: no ID token returned", Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }
                    if (email == null) {
                        Log.e("GoogleSignIn", "email is null from Google account")
                        Toast.makeText(this, "Google sign-in failed: no email returned", Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }

                    checkIfAccountExistsThenProceed(email, idToken, account.displayName ?: "")
                } catch (e: ApiException) {
                    Log.e("GoogleSignIn", "ApiException code=${e.statusCode}", e)
                    Toast.makeText(this, "Google sign-in failed (code ${e.statusCode}): ${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("GoogleSignIn", "Unexpected exception", e)
                    Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("GoogleSignIn", "Result not OK, resultCode=${result.resultCode}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_createaccount)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        bindViews()
        setupPasswordValidation()
        setupListeners()
        applyUncheckedStyle()
    }

    private fun bindViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        etConfirmPassword = findViewById(R.id.etConfirmPassword)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        confirmPasswordLayout = findViewById(R.id.confirmPasswordLayout)
        tvMatchIndicator = findViewById(R.id.tvMatchIndicator)
        termsCheckbox = findViewById(R.id.termsCheckbox)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvSignIn = findViewById(R.id.tvSignIn)
        tvTermsLink = findViewById(R.id.tvTermsLink)

        ruleLength = findViewById(R.id.ruleLength)
        ruleUpper = findViewById(R.id.ruleUpper)
        ruleNumber = findViewById(R.id.ruleNumber)
        ruleSpecial = findViewById(R.id.ruleSpecial)
    }

    // ---------- Live password validation — same pattern as ResetPasswordActivity ----------

    private fun setupPasswordValidation() {
        etPassword.addTextChangedListener(object : TextWatcher {
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
    }

    private fun markRule(view: TextView, satisfied: Boolean) {
        view.setTextColor(if (satisfied) 0xFF4CAF50.toInt() else 0xFF9E9E9E.toInt())
    }

    private fun checkMatch() {
        val pass = etPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()

        if (confirm.isEmpty()) {
            tvMatchIndicator.visibility = android.view.View.GONE
            passwordsMatch = false
        } else {
            tvMatchIndicator.visibility = android.view.View.VISIBLE
            passwordsMatch = pass == confirm
            if (passwordsMatch) {
                tvMatchIndicator.text = "✓ Passwords match"
                tvMatchIndicator.setTextColor(0xFF4CAF50.toInt())
            } else {
                tvMatchIndicator.text = "✗ Passwords don't match"
                tvMatchIndicator.setTextColor(0xFFD32F2F.toInt())
            }
        }
    }

    private fun setupListeners() {
        btnCreateAccount.setOnClickListener {
            if (validateInputs() && termsCheckbox.isChecked) {
                signUpWithEmail()
            } else if (!termsCheckbox.isChecked) {
                Toast.makeText(this, "Please accept Terms & Conditions", Toast.LENGTH_SHORT).show()
                openTermsPage()
            }
        }

        tvTermsLink.setOnClickListener {
            openTermsPage()
        }

        termsCheckbox.setOnClickListener {
            if (!termsCheckbox.isChecked) {
                termsCheckbox.isChecked = false
                openTermsPage()
            } else {
                applyUncheckedStyle()
            }
        }

        btnGoogleSignIn.setOnClickListener {
            if (termsCheckbox.isChecked) {
                signInWithGoogle()
            } else {
                Toast.makeText(this, "Please accept Terms & Conditions first", Toast.LENGTH_SHORT).show()
                openTermsPage()
            }
        }

        tvSignIn.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
    }

    private fun applyCheckedStyle() {
        termsCheckbox.isChecked = true
        termsCheckbox.buttonTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.brand_navy)
        )
        termsCheckbox.animate()
            .scaleX(1.1f)
            .scaleY(1.1f)
            .setDuration(200)
            .withEndAction {
                termsCheckbox.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    private fun applyUncheckedStyle() {
        termsCheckbox.isChecked = false
        termsCheckbox.buttonTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.brand_navy)
        )
    }

    private fun openTermsPage() {
        val intent = Intent(this, TermsOfServiceActivity::class.java)
        termsLauncher.launch(intent)
    }

    private fun validateInputs(): Boolean {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()
        val confirm = etConfirmPassword.text.toString()
        var isValid = true

        emailLayout.error = null
        passwordLayout.error = null
        confirmPasswordLayout.error = null

        if (email.isEmpty()) {
            emailLayout.error = "Email is required"
            isValid = false
        } else if (!EMAIL_PATTERN.matches(email)) {
            emailLayout.error = "Enter a valid email"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordLayout.error = "Password is required"
            isValid = false
        } else if (!lengthOk || !upperOk || !numberOk || !specialOk) {
            passwordLayout.error = "Password doesn't meet all requirements"
            isValid = false
        }

        if (confirm.isEmpty()) {
            confirmPasswordLayout.error = "Please confirm your password"
            isValid = false
        } else if (!passwordsMatch) {
            confirmPasswordLayout.error = "Passwords don't match"
            isValid = false
        }

        return isValid
    }

    private fun signUpWithEmail() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString()

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    emailLayout.error = "Email already registered"
                    Toast.makeText(this, "Email already exists. Please sign in.", Toast.LENGTH_LONG).show()
                } else {
                    sendSupabaseOtp(email, password, isGoogle = false)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Database error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun checkIfAccountExistsThenProceed(email: String, idToken: String, name: String) {
        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    auth.signOut()
                    googleSignInClient.signOut()
                    Toast.makeText(this, "This account already exists. Please sign in instead.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, Login::class.java))
                    finish()
                } else {
                    firebaseAuthWithGoogle(idToken, email, name)
                }
            }
            .addOnFailureListener { e ->
                Log.e("GoogleSignIn", "Firestore existence check failed", e)
                Toast.makeText(this, "Database error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String, email: String, name: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val prefs = getSharedPreferences("TempPrefs", MODE_PRIVATE)
                    prefs.edit().putString("google_name", name).apply()

                    Toast.makeText(this, "Google sign‑in successful", Toast.LENGTH_SHORT).show()
                    sendSupabaseOtp(email, "", isGoogle = true)
                } else {
                    Log.e("GoogleSignIn", "Firebase auth failed", task.exception)
                    Toast.makeText(this, "Firebase auth with Google failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun sendSupabaseOtp(email: String, password: String, isGoogle: Boolean) {
        btnCreateAccount.isEnabled = false
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(OTP) {
                    this.email = email
                    createUser = true
                }
                Toast.makeText(this@Createaccount, "OTP sent to $email", Toast.LENGTH_LONG).show()
                navigateToOtp(email, password, isGoogle)
            } catch (e: Exception) {
                Log.e("GoogleSignIn", "Supabase OTP send failed", e)
                Toast.makeText(this@Createaccount, "Failed to send OTP: ${e.message ?: "Please try again"}", Toast.LENGTH_LONG).show()
            } finally {
                btnCreateAccount.isEnabled = true
            }
        }
    }

    private fun navigateToOtp(email: String, password: String, isGoogle: Boolean) {
        val intent = Intent(this, OtpActivity::class.java).apply {
            putExtra("email", email)
            putExtra("password", password)
            putExtra("isGoogle", isGoogle)
        }
        startActivity(intent)
        finish()
    }
}