package com.example.sgp

import android.content.Intent
import android.os.Bundle
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
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.OTP
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope

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

    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.-]+@(.+)$")

    private val googleLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!, account.email!!, account.displayName ?: "")
            } catch (e: ApiException) {
                Toast.makeText(this, "Google sign‑in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
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
    }

    private fun setupListeners() {
        btnCreateAccount.setOnClickListener {
            if (validateInputs() && termsCheckbox.isChecked) {
                signUpWithEmail()
            } else if (!termsCheckbox.isChecked) {
                Toast.makeText(this, "Please accept Terms & Conditions", Toast.LENGTH_SHORT).show()
            }
        }

        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        tvSignIn.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
            finish()
        }
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
                    Toast.makeText(this, "Email already exists. Please sign in.", Toast.LENGTH_LONG).show()
                } else {
                    trySupabaseOtp(email, password, isGoogle = false)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Database error: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String, email: String, name: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Google sign‑in successful", Toast.LENGTH_SHORT).show()
                    val prefs = getSharedPreferences("TempPrefs", MODE_PRIVATE)
                    prefs.edit().putString("google_name", name).apply()
                    trySupabaseOtp(email, "", isGoogle = true)
                } else {
                    Toast.makeText(this, "Firebase auth with Google failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun trySupabaseOtp(email: String, password: String, isGoogle: Boolean) {
        lifecycleScope.launch {
            try {
                SupabaseClient.client.auth.signInWith(OTP) {
                    this.email = email
                    createUser = true
                }
                Toast.makeText(this@Createaccount, "OTP sent to $email", Toast.LENGTH_LONG).show()
                navigateToOtp(email, password, isGoogle, useFirebase = false)
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@Createaccount, "Supabase OTP unavailable, using email link", Toast.LENGTH_LONG).show()
                fallbackToFirebaseVerification(email, password, isGoogle)
            }
        }
    }

    private fun fallbackToFirebaseVerification(email: String, password: String, isGoogle: Boolean) {
        if (!isGoogle) {
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        sendVerificationAndNavigate(email, password, isGoogle)
                    } else {
                        if (task.exception is FirebaseAuthUserCollisionException) {
                            Toast.makeText(this, "Account already exists. Attempting sign‑in...", Toast.LENGTH_SHORT).show()
                            signInExistingUser(email, password, isGoogle)
                        } else {
                            Toast.makeText(this, "Firebase sign‑up failed: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
        } else {
            sendVerificationAndNavigate(email, password, isGoogle)
        }
    }

    private fun signInExistingUser(email: String, password: String, isGoogle: Boolean) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    sendVerificationAndNavigate(email, password, isGoogle)
                } else {
                    Toast.makeText(this, "Incorrect password. Please try again or reset your password.", Toast.LENGTH_LONG).show()
                    passwordLayout.error = "Incorrect password"
                }
            }
    }

    private fun sendVerificationAndNavigate(email: String, password: String, isGoogle: Boolean) {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "User not found. Please try again.", Toast.LENGTH_SHORT).show()
            return
        }
        if (!user.isEmailVerified) {
            user.sendEmailVerification()
                .addOnCompleteListener { verifyTask ->
                    if (verifyTask.isSuccessful) {
                        Toast.makeText(this, "Verification email sent to $email", Toast.LENGTH_LONG).show()
                        navigateToOtp(email, password, isGoogle, useFirebase = true)
                    } else {
                        Toast.makeText(this, "Failed to send verification: ${verifyTask.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        } else {
            Toast.makeText(this, "Email already verified. Proceeding...", Toast.LENGTH_SHORT).show()
            proceedToProfile(email, isGoogle)
        }
    }

    private fun proceedToProfile(email: String, isGoogle: Boolean) {
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        with(prefs.edit()) {
            putString("user_email", email)
            if (isGoogle) {
                val tempPrefs = getSharedPreferences("TempPrefs", MODE_PRIVATE)
                val googleName = tempPrefs.getString("google_name", "")
                if (!googleName.isNullOrEmpty()) putString("user_name", googleName)
                tempPrefs.edit().clear().apply()
            }
            putInt("user_points", 1250)
            apply()
        }
        startActivity(Intent(this, CompleteProfileActivity::class.java).apply {
            putExtra("email", email)
            putExtra("password", "")
            putExtra("isGoogle", isGoogle)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        finish()
    }

    private fun navigateToOtp(email: String, password: String, isGoogle: Boolean, useFirebase: Boolean) {
        val intent = Intent(this, OtpActivity::class.java).apply {
            putExtra("email", email)
            putExtra("password", password)
            putExtra("isGoogle", isGoogle)
            putExtra("useFirebase", useFirebase)
        }
        startActivity(intent)
        finish()
    }
}