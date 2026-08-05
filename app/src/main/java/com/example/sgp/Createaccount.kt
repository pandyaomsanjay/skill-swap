package com.example.sgp

import android.content.Intent
import android.os.Bundle
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
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import android.view.View

class Createaccount : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var googleSignInClient: GoogleSignInClient

    private lateinit var etEmail: TextInputEditText
    private lateinit var etPassword: TextInputEditText
    private lateinit var emailLayout: TextInputLayout
    private lateinit var passwordLayout: TextInputLayout
    private lateinit var termsCheckbox: CheckBox
    private lateinit var btnCreateAccount: MaterialButton
    private lateinit var btnGoogleSignIn: MaterialButton
    private lateinit var tvSignIn: TextView
    private lateinit var tvTermsLink: TextView

    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@(.+)$")

    // Register for Terms activity result
    private val termsLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            // User accepted terms from Terms page - CHECK THE BOX WITH ANIMATION
            termsCheckbox.isChecked = true
            // Apply custom checkmark style
            applyCheckedStyle()
            Toast.makeText(this, "Terms accepted ✓", Toast.LENGTH_SHORT).show()
        } else {
            // User pressed back without accepting
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
                        Log.e(
                            "GoogleSignIn",
                            "idToken is null — check default_web_client_id / SHA-1 config"
                        )
                        Toast.makeText(
                            this,
                            "Google sign-in failed: no ID token returned",
                            Toast.LENGTH_LONG
                        ).show()
                        return@registerForActivityResult
                    }
                    if (email == null) {
                        Log.e("GoogleSignIn", "email is null from Google account")
                        Toast.makeText(
                            this,
                            "Google sign-in failed: no email returned",
                            Toast.LENGTH_LONG
                        ).show()
                        return@registerForActivityResult
                    }

                    // Check if account exists before proceeding
                    checkIfAccountExistsThenProceed(email, idToken, account.displayName ?: "")
                } catch (e: ApiException) {
                    Log.e("GoogleSignIn", "ApiException code=${e.statusCode}", e)
                    Toast.makeText(
                        this,
                        "Google sign-in failed (code ${e.statusCode}): ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                } catch (e: Exception) {
                    Log.e("GoogleSignIn", "Unexpected exception", e)
                    Toast.makeText(this, "Google sign-in failed: ${e.message}", Toast.LENGTH_LONG)
                        .show()
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
        setupListeners()
        // Apply initial style
        applyUncheckedStyle()
    }

    private fun bindViews() {
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        emailLayout = findViewById(R.id.emailLayout)
        passwordLayout = findViewById(R.id.passwordLayout)
        termsCheckbox = findViewById(R.id.termsCheckbox)
        btnCreateAccount = findViewById(R.id.btnCreateAccount)
        btnGoogleSignIn = findViewById(R.id.btnGoogleSignIn)
        tvSignIn = findViewById(R.id.tvSignIn)
        tvTermsLink = findViewById(R.id.tvTermsLink)
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

        // Make "Terms & Conditions" text clickable to open Terms page
        tvTermsLink.setOnClickListener {
            openTermsPage()
        }

        // Checkbox click - if user tries to check, open Terms page first
        termsCheckbox.setOnClickListener {
            if (!termsCheckbox.isChecked) {
                // User is trying to check the box - open Terms page first
                // Keep it unchecked until they come back
                termsCheckbox.isChecked = false
                openTermsPage()
            } else {
                // User is trying to uncheck - allow it
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

    /**
     * Applies checked style to the checkbox with a visible tick
     */
    private fun applyCheckedStyle() {
        // Use the default checked state which shows a tick
        termsCheckbox.isChecked = true
        // Ensure the button tint is applied (brand_navy)
        termsCheckbox.buttonTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.brand_navy)
        )
        // Add a small animation effect
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

    /**
     * Applies unchecked style to the checkbox
     */
    private fun applyUncheckedStyle() {
        termsCheckbox.isChecked = false
        termsCheckbox.buttonTintList = android.content.res.ColorStateList.valueOf(
            ContextCompat.getColor(this, R.color.brand_navy)
        )
    }

    /**
     * Opens the Terms of Service page and waits for result
     */
    private fun openTermsPage() {
        val intent = Intent(this, TermsOfServiceActivity::class.java)
        termsLauncher.launch(intent)
    }

    private fun validateInputs(): Boolean {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()
        var isValid = true

        emailLayout.error = null
        passwordLayout.error = null

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
        } else if (password.length < 6) {
            passwordLayout.error = "Minimum 6 characters"
            isValid = false
        }

        return isValid
    }

    private fun signUpWithEmail() {
        val email = etEmail.text.toString().trim()
        val password = etPassword.text.toString().trim()

        db.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    emailLayout.error = "Email already registered"
                    Toast.makeText(this, "Email already exists. Please sign in.", Toast.LENGTH_LONG)
                        .show()
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
                    // Already registered — don't create a new account.
                    auth.signOut()
                    googleSignInClient.signOut()
                    Toast.makeText(
                        this,
                        "This account already exists. Please sign in instead.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(Intent(this, Login::class.java))
                    finish()
                } else {
                    // New user - proceed with Firebase auth and OTP
                    firebaseAuthWithGoogle(idToken, email, name)
                }
            }
            .addOnFailureListener { e ->
                Log.e("GoogleSignIn", "Firestore existence check failed", e)
                Toast.makeText(
                    this,
                    "Database error: ${e.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String, email: String, name: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    // Store Google name temporarily
                    val prefs = getSharedPreferences("TempPrefs", MODE_PRIVATE)
                    prefs.edit().putString("google_name", name).apply()

                    Toast.makeText(this, "Google sign‑in successful", Toast.LENGTH_SHORT).show()
                    sendSupabaseOtp(email, "", isGoogle = true)
                } else {
                    Log.e("GoogleSignIn", "Firebase auth failed", task.exception)
                    Toast.makeText(
                        this,
                        "Firebase auth with Google failed: ${task.exception?.message}",
                        Toast.LENGTH_SHORT
                    ).show()
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
                Toast.makeText(
                    this@Createaccount,
                    "Failed to send OTP: ${e.message ?: "Please try again"}",
                    Toast.LENGTH_LONG
                ).show()
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