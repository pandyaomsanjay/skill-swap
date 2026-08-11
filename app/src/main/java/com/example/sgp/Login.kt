package com.example.sgp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.example.sgp.api.AuthRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.Firebase
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.launch

class Login : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var progressBar: ProgressBar
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    val idToken = account?.idToken

                    if (idToken == null) {
                        Log.e("GoogleSignIn", "idToken is null — check default_web_client_id / SHA-1 config")
                        Toast.makeText(this, "Google sign-in failed: no ID token returned", Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }

                    firebaseAuthWithGoogle(idToken)
                } catch (e: ApiException) {
                    Log.e("GoogleSignIn", "ApiException code=${e.statusCode}", e)
                    Toast.makeText(this, "Google sign‑in failed (code ${e.statusCode}): ${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("GoogleSignIn", "Unexpected exception", e)
                    Toast.makeText(this, "Google sign‑in failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e("GoogleSignIn", "Result not OK, resultCode=${result.resultCode}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = Firebase.firestore
        progressBar = findViewById(R.id.progressBar)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Auto-login only covers Google sign-in sessions (Firebase-based).
        // Email/password sessions now live in Supabase, so this check
        // intentionally does NOT auto-login email/password users. See note
        // at the bottom of this file if you want that added later.
        if (auth.currentUser != null) {
            startActivity(Intent(this, Home::class.java))
            finish()
            return
        }

        initializeViews()
    }

    private fun initializeViews() {
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val btnGoogleSignIn = findViewById<Button>(R.id.btnGoogleLogin)

        val emailInput = findViewById<TextInputLayout>(R.id.email)
        val passwordInput = findViewById<TextInputLayout>(R.id.password)

        btnLogin.setOnClickListener {
            val email = emailInput.editText?.text.toString().trim()
            val password = passwordInput.editText?.text.toString()

            if (validateForm(email, password)) {
                loginUser(email, password)
            }
        }

        tvCreateAccount.setOnClickListener {
            startActivity(Intent(this, Createaccount::class.java))
        }

        tvForgotPassword.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordEmailActivity::class.java))
        }

        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            googleLauncher.launch(googleSignInClient.signInIntent)
        }
    }

    private fun validateForm(email: String, password: String): Boolean {
        var isValid = true

        val emailLayout = findViewById<TextInputLayout>(R.id.email)
        val passwordLayout = findViewById<TextInputLayout>(R.id.password)

        emailLayout.error = null
        passwordLayout.error = null

        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            emailLayout.error = "Enter a valid email"
            isValid = false
        }

        if (password.isEmpty()) {
            passwordLayout.error = "Password required"
            isValid = false
        }

        return isValid
    }

    // ---------- Email/password login — now via Supabase ----------

    private fun loginUser(email: String, password: String) {
        showLoading(true)
        lifecycleScope.launch {
            try {
                val result = AuthRepository.loginWithPassword(email, password)
                if (result.success) {
                    loadUserByEmailAndNavigate(result.email ?: email)
                } else {
                    showLoading(false)
                    showError(result.message)
                }
            } catch (e: Exception) {
                showLoading(false)
                showError("Login failed: ${e.message}")
            }
        }
    }

    /** Looks up the Firestore profile by email (used for Supabase-authenticated logins). */
    private fun loadUserByEmailAndNavigate(email: String) {
        db.collection("users").whereEqualTo("email", email).limit(1).get()
            .addOnSuccessListener { snapshot ->
                showLoading(false)
                val userData = snapshot.documents.firstOrNull()?.toObject(Users::class.java)
                if (userData != null) {
                    val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("user_name", userData.name)
                        .putString("user_email", userData.email)
                        .putString("user_location", userData.location)
                        .putString("user_type", userData.userType)
                        .apply()

                    Toast.makeText(this@Login, "Login successful", Toast.LENGTH_SHORT).show()

                    val intent = if (userData.userType == "admin") {
                        Intent(this@Login, AdminDashboardActivity::class.java)
                    } else {
                        Intent(this@Login, Home::class.java)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    showError("User data not found")
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                showError("Error fetching user data: ${e.message}")
            }
    }

    // ---------- Google login — unchanged, still Firebase ----------

    private fun firebaseAuthWithGoogle(idToken: String) {
        showLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        loadUserAndNavigate(user.uid, isGoogle = true)
                    } else {
                        showLoading(false)
                        showError("Google sign‑in succeeded but no user found")
                    }
                } else {
                    showLoading(false)
                    showError("Google sign‑in failed: ${task.exception?.message}")
                }
            }
    }

    private fun loadUserAndNavigate(uid: String, isGoogle: Boolean = false) {
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                showLoading(false)
                val userData = doc.toObject(Users::class.java)
                if (userData != null) {
                    val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
                    prefs.edit()
                        .putString("user_name", userData.name)
                        .putString("user_email", userData.email)
                        .putString("user_location", userData.location)
                        .putString("user_type", userData.userType)
                        .apply()

                    Toast.makeText(this@Login, "Login successful", Toast.LENGTH_SHORT).show()

                    val intent = if (userData.userType == "admin") {
                        Intent(this@Login, AdminDashboardActivity::class.java)
                    } else {
                        Intent(this@Login, Home::class.java)
                    }
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else if (isGoogle) {
                    auth.signOut()
                    googleSignInClient.signOut()
                    Toast.makeText(this@Login, "No account found for this Google user. Please create an account.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@Login, Createaccount::class.java))
                    finish()
                } else {
                    showError("User data not found")
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                showError("Error fetching user data: ${e.message}")
            }
    }

    private fun showError(message: String) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnLogin).isEnabled = !show
    }
}