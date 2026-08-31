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
import com.onesignal.OneSignal
import kotlinx.coroutines.launch

class Login : BaseActivity() {

    companion object {
        private const val TAG = "LoginDebug"
    }

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
                        Log.e(TAG, "Google idToken is null — check default_web_client_id / SHA-1 config")
                        Toast.makeText(this, "Google sign-in failed: no ID token returned", Toast.LENGTH_LONG).show()
                        return@registerForActivityResult
                    }

                    Log.d(TAG, "Google idToken received, length=${idToken.length}")
                    firebaseAuthWithGoogle(idToken)
                } catch (e: ApiException) {
                    Log.e(TAG, "Google sign-in ApiException code=${e.statusCode}", e)
                    Toast.makeText(this, "Google sign‑in failed (code ${e.statusCode}): ${e.message}", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e(TAG, "Google sign-in unexpected exception", e)
                    Toast.makeText(this, "Google sign‑in failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } else {
                Log.e(TAG, "Google sign-in result not OK, resultCode=${result.resultCode}")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = Firebase.firestore
        progressBar = findViewById(R.id.progressBar)

        Log.d(TAG, "onCreate: Firebase project=${auth.app.options.projectId}, currentUser=${auth.currentUser?.uid}")

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Auto-login check for Google-authenticated users
        if (auth.currentUser != null) {
            Log.d(TAG, "Auto-login: currentUser already set, uid=${auth.currentUser?.uid}")
            startActivity(Intent(this, Home::class.java))
            finish()
            return
        }

        initializeViews()
        requestNotificationPermission()
    }

    /**
     * Prompts for notification permission here on the login screen, instead of
     * at app startup (MyApp.onCreate), so the OS popup doesn't appear over the
     * splash screen. Skipped entirely when auto-login above short-circuits
     * onCreate before this point.
     */
    private fun requestNotificationPermission() {
        lifecycleScope.launch {
            OneSignal.Notifications.requestPermission(true)
        }
    }

    private fun initializeViews() {
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val tvCreateAccount = findViewById<TextView>(R.id.tvCreateAccount)
        val tvForgotPassword = findViewById<TextView>(R.id.tvForgotPassword)
        val btnGoogleSignIn = findViewById<Button>(R.id.btnGoogleLogin)

        val emailInput = findViewById<TextInputLayout>(R.id.email)
        val passwordInput = findViewById<TextInputLayout>(R.id.password)

        btnLogin.setOnClickListener {
            val email = emailInput.editText?.text.toString().trim().lowercase()
            val password = passwordInput.editText?.text.toString()

            Log.d(TAG, "Login button clicked, email='$email', passwordLength=${password.length}")

            if (validateForm(email, password)) {
                loginUser(email, password)
            } else {
                Log.d(TAG, "Form validation failed")
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

    // ---------- Email/password login via Supabase ----------
    private fun loginUser(email: String, password: String) {
        showLoading(true)
        Log.d(TAG, "loginUser: calling AuthRepository.loginWithPassword for '$email'")

        lifecycleScope.launch {
            val result = AuthRepository.loginWithPassword(email, password)

            if (result.success) {
                Log.d(TAG, "AuthRepository.loginWithPassword SUCCESS for '${result.email}'")

                // ⭐ CRITICAL: Create Firebase user for this Supabase user
                createFirebaseUserForSupabase(email, password)
            } else {
                showLoading(false)
                Log.e(TAG, "AuthRepository.loginWithPassword FAILED: locked=${result.isLocked}, " +
                        "attemptsRemaining=${result.attemptsRemaining}, message=${result.message}")
                showError(result.message)
            }
        }
    }

    /**
     * ⭐ NEW: Creates a Firebase user for Supabase-authenticated users
     * This ensures Firestore rules work correctly (request.auth.uid will exist)
     */
    private fun createFirebaseUserForSupabase(email: String, password: String) {
        Log.d(TAG, "createFirebaseUserForSupabase: creating Firebase user for $email")

        // Try to sign in with Firebase first (in case user already exists in Firebase)
        auth.signInWithEmailAndPassword(email, password)
            .addOnSuccessListener { authResult ->
                Log.d(TAG, "Firebase signIn successful for $email, uid=${authResult.user?.uid}")
                loadUserAndNavigateByEmail(email, authResult.user?.uid)
            }
            .addOnFailureListener { signInError ->
                Log.d(TAG, "Firebase signIn failed: ${signInError.message}, trying to create new user")

                // User doesn't exist in Firebase, create them
                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener { createResult ->
                        Log.d(TAG, "✅ Firebase user CREATED for $email, uid=${createResult.user?.uid}")
                        loadUserAndNavigateByEmail(email, createResult.user?.uid)
                    }
                    .addOnFailureListener { createError ->
                        showLoading(false)
                        Log.e(TAG, "❌ Failed to create Firebase user: ${createError.message}")

                        // Even if Firebase user creation fails, we still have Supabase user
                        // Load user by email (but Firestore rules might not work)
                        Toast.makeText(
                            this,
                            "Login successful, but some features may be limited",
                            Toast.LENGTH_LONG
                        ).show()
                        loadUserAndNavigateByEmail(email, null)
                    }
            }
    }

    /**
     * Loads user and navigates. If firebaseUid is provided, use it for Firestore lookup.
     */
    private fun loadUserAndNavigateByEmail(email: String, firebaseUid: String? = null) {
        Log.d(TAG, "loadUserAndNavigateByEmail: querying users where email == $email")

        db.collection("users")
            .whereEqualTo("email", email)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                showLoading(false)
                val doc = snapshot.documents.firstOrNull()
                Log.d(TAG, "Firestore email lookup: found=${doc != null}, id=${doc?.id}")

                if (doc != null) {
                    val userData = doc.toObject(Users::class.java)
                    if (userData != null) {
                        // Update the user document with Firebase UID if available
                        if (firebaseUid != null && doc.id != firebaseUid) {
                            // The user document ID might be different from Firebase UID
                            // Update the document to include Firebase UID
                            db.collection("users").document(doc.id)
                                .update("firebaseUid", firebaseUid)
                                .addOnSuccessListener {
                                    Log.d(TAG, "✅ Updated user document with firebaseUid: $firebaseUid")
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Failed to update firebaseUid: ${e.message}")
                                }
                        }

                        saveUserDataAndNavigate(userData)
                    } else {
                        Log.e(TAG, "Failed to parse user data")
                        showError("Failed to parse user data")
                    }
                } else {
                    // User exists in Supabase but not in Firestore
                    // Create Firestore profile
                    Log.d(TAG, "Creating Firestore profile for email=$email")
                    createFirestoreProfileForUser(email, firebaseUid)
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Firestore lookup failed: ${e.message}", e)
                showError("Error fetching user data: ${e.message}")
            }
    }

    /**
     * Creates a Firestore profile for a user who exists in Supabase but not Firestore
     */
    private fun createFirestoreProfileForUser(email: String, firebaseUid: String? = null) {
        val userDocId = firebaseUid ?: email.replace(".", "_").replace("@", "_at_")

        val userData = hashMapOf(
            "email" to email,
            "name" to email.split("@")[0],
            "userType" to "standard",
            "credits" to 100,
            "location" to "",
            "createdAt" to System.currentTimeMillis(),
            "lastLogin" to System.currentTimeMillis(),
            "authProvider" to "supabase",
            "firebaseUid" to firebaseUid
        )

        db.collection("users")
            .document(userDocId)
            .set(userData)
            .addOnSuccessListener {
                Log.d(TAG, "✅ Firestore profile created with ID: $userDocId")

                val users = Users().apply {
                    this.email = email
                    this.name = email.split("@")[0]
                    this.userType = "standard"
                    this.credits = 100
                    this.location = ""
                }
                saveUserDataAndNavigate(users)
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "❌ Failed to create Firestore profile: ${e.message}", e)
                showError("Failed to create user profile: ${e.message}")
            }
    }

    /**
     * Saves user data to SharedPreferences and navigates to appropriate activity
     */
    private fun saveUserDataAndNavigate(userData: Users) {
        val prefs = getSharedPreferences("SkillSwapPrefs", MODE_PRIVATE)
        prefs.edit()
            .putString("user_name", userData.name)
            .putString("user_email", userData.email)
            .putString("user_location", userData.location)
            .putString("user_type", userData.userType)
            .apply()

        // Tie this device's push subscription to the logged-in user
        OneSignal.login(userData.email)

        Toast.makeText(this@Login, "Login successful", Toast.LENGTH_SHORT).show()

        val intent = if (userData.userType == "admin") {
            Intent(this@Login, AdminDashboardActivity::class.java)
        } else {
            Intent(this@Login, Home::class.java)
        }
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    // ---------- Google login ----------
    private fun loadUserAndNavigate(uid: String, isGoogle: Boolean = false) {
        Log.d(TAG, "loadUserAndNavigate: querying users/$uid (isGoogle=$isGoogle)")
        db.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                showLoading(false)
                Log.d(TAG, "Firestore lookup success: exists=${doc.exists()}, id=${doc.id}")
                val userData = doc.toObject(Users::class.java)

                if (userData != null) {
                    saveUserDataAndNavigate(userData)
                } else if (isGoogle) {
                    Log.e(TAG, "No Firestore profile for Google user uid=$uid")
                    auth.signOut()
                    googleSignInClient.signOut()
                    Toast.makeText(this@Login, "No account found for this Google user. Please create an account.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this@Login, Createaccount::class.java))
                    finish()
                } else {
                    Log.e(TAG, "No usable Firestore profile for uid=$uid")
                    auth.signOut()
                    showError("Profile not found. Please contact support.")
                }
            }
            .addOnFailureListener { e ->
                showLoading(false)
                Log.e(TAG, "Firestore lookup FAILED for uid=$uid: ${e.message}", e)
                showError("Error fetching user data: ${e.message}")
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        showLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    Log.d(TAG, "Google signInWithCredential SUCCESS, uid=${user?.uid}")
                    if (user != null) {
                        loadUserAndNavigate(user.uid, isGoogle = true)
                    } else {
                        showLoading(false)
                        Log.e(TAG, "Google signInWithCredential succeeded but currentUser is null")
                        showError("Google sign‑in succeeded but no user found")
                    }
                } else {
                    showLoading(false)
                    Log.e(TAG, "Google signInWithCredential FAILED: ${task.exception?.message}", task.exception)
                    showError("Google sign‑in failed: ${task.exception?.message}")
                }
            }
    }

    private fun showError(message: String) {
        Log.e(TAG, "showError: $message")
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show()
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        findViewById<Button>(R.id.btnLogin).isEnabled = !show
    }
}