package com.example.sgp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.example.sgp.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.progressindicator.CircularProgressIndicator
import io.github.jan.supabase.auth.auth
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.razorpay.Checkout
import com.razorpay.PaymentData
import com.razorpay.PaymentResultWithDataListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Credit Store — User-side screen for purchasing SkillSwap credits.
 *
 * Security & Data Model:
 * 1. Auth check:
 *    - Uses FirebaseAuth.getInstance() with AuthStateListener to guarantee the session
 *      is restored properly and never misreported as "Not signed in" if a valid Firebase user exists.
 *    - Logs currentUser?.uid and currentUser?.email to Logcat.
 *
 * 2. Credit balance:
 *    - Reads users/{currentUser.uid} -> field `credits`.
 *    - Real-time snapshot listener ensures balance is updated immediately when credits are added or deducted.
 *    - Displays actual integer values: 0 -> "0", 100 -> "100", 300 -> "300".
 *    - Does NOT use "-" as normal state; does not hardcode balance.
 *
 * 3. Package permissions:
 *    - Reads creditPackages from Firestore.
 *    - Android clients NEVER attempt to write to creditPackages directly (client writes are blocked by rules).
 *    - If creditPackages is empty, invokes server-side `seedCreditPackages` Cloud Function (Admin SDK) to populate.
 *
 * 4. Razorpay payment security:
 *    - Client never adds credits.
 *    - Only verifyRazorpayPayment Cloud Function verifies HMAC signature and performs atomic Firestore transaction.
 */
class CreditStoreActivity : BaseActivity(), PaymentResultWithDataListener {

    private val TAG = "CreditStoreActivity"

    // ─── Views ───────────────────────────────────────────────────────────────
    private lateinit var cardBalance: MaterialCardView
    private lateinit var tvBalanceLabel: TextView
    private lateinit var tvBalanceValue: TextView
    private lateinit var tvBalanceCreditsLabel: TextView
    private lateinit var packagesContainer: LinearLayout
    private lateinit var progressBar: CircularProgressIndicator
    private lateinit var tvEmptyPackages: TextView
    private lateinit var btnBuyCredits: com.google.android.material.button.MaterialButton

    // ─── Backend & Network ───────────────────────────────────────────────────
    private val db: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    private val edgeFunctionsBaseUrl = "https://ghrxltlstncjcizyyqfo.supabase.co/functions/v1"

    // ─── Listeners & State ────────────────────────────────────────────────────
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var userBalanceListener: ListenerRegistration? = null
    private var currentUserCredits: Int = 0

    private var pendingOrderId: String? = null
    private var pendingPackage: CreditPackage? = null

    // ─── Selection State ──────────────────────────────────────────────────────
    private var selectedPackage: CreditPackage? = null
    private val packageCardViews: MutableList<View> = mutableListOf()

    private var isDarkModeEnabled = false

    // ─── Palette (User-side Navy & Steel) ────────────────────────────────────
    private val lightScreenBg = Color.parseColor("#EAF1F5")
    private val lightCardBg = Color.parseColor("#FFFFFF")
    private val lightCardStroke = Color.parseColor("#D2C1B6")
    private val lightTextPrimary = Color.parseColor("#1B3C53")
    private val lightTextSecondary = Color.parseColor("#456882")
    private val lightHeaderBg = Color.parseColor("#1B3C53")

    private val darkScreenBg = Color.parseColor("#0F1B24")
    private val darkCardBg = Color.parseColor("#16232E")
    private val darkCardStroke = Color.parseColor("#2A3B48")
    private val darkTextPrimary = Color.parseColor("#F5F7F8")
    private val darkTextSecondary = Color.parseColor("#8FA6B8")
    private val darkHeaderBg = Color.parseColor("#0B1620")

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, CreditStoreActivity::class.java))
        }
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_credit_store)

        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkModeEnabled = prefs.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        bindViews()
        setupToolbar()
        applyTheme(isDarkModeEnabled)

        // Pre-warm Razorpay checkout
        Checkout.preload(this)

        // Setup FirebaseAuth state listener to handle both immediate and restoring auth sessions
        setupAuthListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        authStateListener?.let { auth.removeAuthStateListener(it) }
        authStateListener = null
        userBalanceListener?.remove()
        userBalanceListener = null
    }

    // ─── View Binding ─────────────────────────────────────────────────────────

    private fun bindViews() {
        cardBalance = findViewById(R.id.cardBalance)
        tvBalanceLabel = findViewById(R.id.tvBalanceLabel)
        tvBalanceValue = findViewById(R.id.tvBalanceValue)
        tvBalanceCreditsLabel = findViewById(R.id.tvBalanceCreditsLabel)
        packagesContainer = findViewById(R.id.packagesContainer)
        progressBar = findViewById(R.id.progressBar)
        tvEmptyPackages = findViewById(R.id.tvEmptyPackages)
        btnBuyCredits = findViewById(R.id.btnBuyCredits)

        btnBuyCredits.setOnClickListener {
            val pkg = selectedPackage ?: return@setOnClickListener
            if (getLoggedInEmail().isNullOrBlank()) {
                Toast.makeText(this, "Please sign in to buy credits", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, Login::class.java))
                return@setOnClickListener
            }
            // Route to native checkout screen (reusable for simulated demo gateway and future Razorpay)
            CheckoutActivity.start(
                context = this,
                packageId = pkg.packageId,
                previewName = pkg.getDisplayName(),
                previewPrice = pkg.priceInRupees,
                previewCredits = pkg.credits
            )
        }
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = "Credits"
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.navigationIcon?.setTint(Color.parseColor("#F9F3EF"))
        toolbar.setNavigationOnClickListener { finish() }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    // ─── Theme ───────────────────────────────────────────────────────────────

    private fun applyTheme(isDark: Boolean) {
        isDarkModeEnabled = isDark
        val screenBg = if (isDark) darkScreenBg else lightScreenBg
        val cardBg = if (isDark) darkCardBg else lightCardBg
        val cardStroke = if (isDark) darkCardStroke else lightCardStroke
        val textPrimary = if (isDark) darkTextPrimary else lightTextPrimary
        val textSecondary = if (isDark) darkTextSecondary else lightTextSecondary
        val headerBg = if (isDark) darkHeaderBg else lightHeaderBg

        findViewById<View>(R.id.creditStoreRootLayout)?.setBackgroundColor(screenBg)
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        toolbar?.setBackgroundColor(headerBg)
        toolbar?.navigationIcon?.setTint(Color.parseColor("#F9F3EF"))

        cardBalance.setCardBackgroundColor(cardBg)
        cardBalance.strokeColor = cardStroke

        tvBalanceLabel.setTextColor(textSecondary)
        tvBalanceValue.setTextColor(textPrimary)
        tvBalanceCreditsLabel.setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvBuyCreditsTitle)?.setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvBuyCreditsSubtitle)?.setTextColor(textSecondary)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = false
        @Suppress("DEPRECATION")
        window.statusBarColor = headerBg
        @Suppress("DEPRECATION")
        window.navigationBarColor = screenBg
    }

    // ─── 1. Auth & Session Management ─────────────────────────────────────────

    private fun getLoggedInEmail(): String? {
        val fbEmail = auth.currentUser?.email
        if (!fbEmail.isNullOrBlank()) return fbEmail

        try {
            val supaEmail = SupabaseClient.client.auth.currentSessionOrNull()?.user?.email
            if (!supaEmail.isNullOrBlank()) return supaEmail
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Supabase session: ${e.message}")
        }

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val prefEmail = prefs.getString("user_email", null)
        if (!prefEmail.isNullOrBlank()) return prefEmail

        return null
    }

    /**
     * Resolves the caller's active, verified authentication token:
     * - If Firebase user is signed in (e.g. Google user), fetches current ID Token.
     * - Otherwise, fetches Supabase session access token.
     */
    private suspend fun getActiveAuthToken(): String? {
        val fbUser = auth.currentUser
        if (fbUser != null) {
            try {
                val tokenResult = fbUser.getIdToken(false).await()
                val token = tokenResult.token
                if (!token.isNullOrBlank()) return token
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get Firebase ID token: ${e.message}")
            }
        }

        try {
            val supaToken = SupabaseClient.client.auth.currentAccessTokenOrNull()
            if (!supaToken.isNullOrBlank()) return supaToken
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get Supabase access token: ${e.message}")
        }

        return null
    }

    private fun getLoggedInUid(): String? {
        val fbUid = auth.currentUser?.uid
        if (!fbUid.isNullOrBlank()) return fbUid

        try {
            val supaUid = SupabaseClient.client.auth.currentSessionOrNull()?.user?.id
            if (!supaUid.isNullOrBlank()) return supaUid
        } catch (e: Exception) {
            Log.w(TAG, "Error checking Supabase uid: ${e.message}")
        }

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        return prefs.getString("user_id", null) ?: prefs.getString("uid", null)
    }

    private fun getLoggedInName(): String {
        val fbName = auth.currentUser?.displayName
        if (!fbName.isNullOrBlank()) return fbName

        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val prefName = prefs.getString("user_name", null)
        if (!prefName.isNullOrBlank()) return prefName

        val email = getLoggedInEmail() ?: ""
        return if (email.contains("@")) email.split("@")[0] else "User"
    }

    private fun setupAuthListener() {
        val loggedInEmail = getLoggedInEmail()
        val loggedInUid = getLoggedInUid()
        Log.d(TAG, "=== setupAuthListener ===")
        Log.d(TAG, "loggedInEmail = $loggedInEmail, loggedInUid = $loggedInUid")

        if (!loggedInEmail.isNullOrBlank()) {
            cardBalance.setOnClickListener(null)
            loadUserBalance(loggedInEmail, loggedInUid)
            loadCreditPackages()
        } else {
            showSignedOutState()
        }

        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val currentUser = firebaseAuth.currentUser
            Log.d(TAG, "=== AuthStateListener ===")
            Log.d(TAG, "currentUser?.uid = ${currentUser?.uid}, currentUser?.email = ${currentUser?.email}")

            val currentEmail = currentUser?.email ?: getLoggedInEmail()
            if (!currentEmail.isNullOrBlank()) {
                cardBalance.setOnClickListener(null)
                loadUserBalance(currentEmail, currentUser?.uid ?: getLoggedInUid())
                loadCreditPackages()
            } else {
                showSignedOutState()
            }
        }
        auth.addAuthStateListener(authStateListener!!)
    }

    private fun showSignedOutState() {
        // Only called when the user is genuinely not signed into the app
        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val cachedPoints = prefs.getInt("user_points", -1)
        if (cachedPoints >= 0) {
            tvBalanceValue.text = cachedPoints.toString()
            tvBalanceCreditsLabel.text = "Sign in to buy credits · Tap to sign in"
        } else {
            tvBalanceValue.text = "0"
            tvBalanceCreditsLabel.text = "Not signed in · Tap to sign in"
        }
        cardBalance.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }
        tvEmptyPackages.visibility = View.VISIBLE
        tvEmptyPackages.text = "Please sign in to view and purchase credit packages."
        tvEmptyPackages.setOnClickListener {
            startActivity(Intent(this, Login::class.java))
        }
        progressBar.visibility = View.GONE
    }

    // ─── 2. User Credit Balance (Firestore & SharedPreferences) ───────────────

    private fun loadUserBalance(email: String, uid: String?) {
        // Pre-fill with cached balance from prefs if available to avoid any flicker
        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        val cachedPoints = prefs.getInt("user_points", -1)
        if (cachedPoints >= 0) {
            currentUserCredits = cachedPoints
            tvBalanceValue.text = cachedPoints.toString()
            tvBalanceCreditsLabel.text = "credits"
            cardBalance.setOnClickListener(null)
        }

        userBalanceListener?.remove()

        // 1. If UID is present, attach snapshot listener to users/{uid}
        if (!uid.isNullOrBlank()) {
            val userRef = db.collection("users").document(uid)
            userBalanceListener = userRef.addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to users/$uid: ${error.message}", error)
                    queryBalanceByEmail(email, cachedPoints)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists() && snapshot.contains("credits")) {
                    val rawCredits = snapshot.get("credits")
                    currentUserCredits = when (rawCredits) {
                        is Number -> rawCredits.toInt()
                        is String -> rawCredits.toIntOrNull() ?: 0
                        else -> 0
                    }
                    Log.d(TAG, "users/$uid credits: $currentUserCredits")
                    tvBalanceValue.text = currentUserCredits.toString()
                    tvBalanceCreditsLabel.text = "credits"
                    cardBalance.setOnClickListener(null)
                    prefs.edit().putInt("user_points", currentUserCredits).apply()
                } else {
                    queryBalanceByEmail(email, cachedPoints)
                }
            }
        } else {
            queryBalanceByEmail(email, cachedPoints)
        }
    }

    private fun queryBalanceByEmail(email: String, cachedPoints: Int) {
        val prefs = getSharedPreferences("SkillSwapPrefs", Context.MODE_PRIVATE)
        db.collection("users").whereEqualTo("email", email).limit(1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error querying users by email $email: ${error.message}", error)
                    if (cachedPoints >= 0) {
                        tvBalanceValue.text = cachedPoints.toString()
                        tvBalanceCreditsLabel.text = "credits"
                    }
                    return@addSnapshotListener
                }

                val doc = snapshot?.documents?.firstOrNull()
                if (doc != null && doc.exists()) {
                    val rawCredits = doc.get("credits")
                    currentUserCredits = when (rawCredits) {
                        is Number -> rawCredits.toInt()
                        is String -> rawCredits.toIntOrNull() ?: 0
                        else -> 0
                    }
                    Log.d(TAG, "User by email $email has credits: $currentUserCredits")
                    tvBalanceValue.text = currentUserCredits.toString()
                    tvBalanceCreditsLabel.text = "credits"
                    cardBalance.setOnClickListener(null)
                    prefs.edit().putInt("user_points", currentUserCredits).apply()
                } else {
                    val lower = email.lowercase()
                    if (lower != email) {
                        db.collection("users").whereEqualTo("email", lower).limit(1).get()
                            .addOnSuccessListener { lowerSnap ->
                                val lowerDoc = lowerSnap.documents.firstOrNull()
                                if (lowerDoc != null && lowerDoc.exists()) {
                                    val rawCredits = lowerDoc.get("credits")
                                    currentUserCredits = when (rawCredits) {
                                        is Number -> rawCredits.toInt()
                                        is String -> rawCredits.toIntOrNull() ?: 0
                                        else -> 0
                                    }
                                    tvBalanceValue.text = currentUserCredits.toString()
                                    tvBalanceCreditsLabel.text = "credits"
                                    cardBalance.setOnClickListener(null)
                                    prefs.edit().putInt("user_points", currentUserCredits).apply()
                                }
                            }
                    }
                }
            }
    }

    // ─── 3. Credit Packages (Read-only for clients) ───────────────────────────

    private fun loadCreditPackages() {
        progressBar.visibility = View.VISIBLE
        tvEmptyPackages.visibility = View.GONE

        db.collection("creditPackages").get()
            .addOnSuccessListener { snapshot ->
                progressBar.visibility = View.GONE
                if (snapshot.isEmpty) {
                    Log.d(TAG, "creditPackages collection is empty. Falling back to default packages.")
                    renderPackagesList(getDefaultPackages())
                } else {
                    renderPackagesFromSnapshot(snapshot.documents)
                }
            }
            .addOnFailureListener { e ->
                progressBar.visibility = View.GONE
                val errorMsg = e.localizedMessage ?: "Unknown error"
                Log.w(TAG, "Failed to read creditPackages from Firestore ($errorMsg). Using default packages.")
                renderPackagesList(getDefaultPackages())
            }
    }

    private fun renderPackagesFromSnapshot(documents: List<DocumentSnapshot>) {
        val packages = documents.mapNotNull { doc ->
            doc.toObject(CreditPackage::class.java)
        }
        .filter { it.active && it.priceInRupees >= 50 }
        .sortedBy { it.displayOrder }

        if (packages.isEmpty()) {
            renderPackagesList(getDefaultPackages())
        } else {
            renderPackagesList(packages)
        }
    }

    private fun renderPackagesList(packages: List<CreditPackage>) {
        progressBar.visibility = View.GONE
        packagesContainer.removeAllViews()
        packageCardViews.clear()
        selectedPackage = null
        btnBuyCredits.isEnabled = false

        if (packages.isEmpty()) {
            tvEmptyPackages.visibility = View.VISIBLE
            tvEmptyPackages.text = "No packages available at this time."
        } else {
            tvEmptyPackages.visibility = View.GONE
            packages.forEach { pkg -> addPackageCard(pkg) }

            // Auto-select the first package
            if (packageCardViews.isNotEmpty()) {
                selectCard(packageCardViews.first(), packages.first())
            }
        }
    }

    private fun getDefaultPackages(): List<CreditPackage> {
        return listOf(
            CreditPackage("starter_50", "Starter", 50, 100, true, 1),
            CreditPackage("popular_100", "Popular", 100, 220, true, 2),
            CreditPackage("value_200", "Value", 200, 500, true, 3),
            CreditPackage("pro_500", "Pro", 500, 1400, true, 4)
        )
    }

    // ─── 4. Package Card View Inflation ───────────────────────────────────────

    private fun addPackageCard(pkg: CreditPackage) {
        val card = LayoutInflater.from(this)
            .inflate(R.layout.item_credit_package, packagesContainer, false)

        val cardView = card.findViewById<MaterialCardView>(R.id.cardPackage)
        cardView.setCardBackgroundColor(if (isDarkModeEnabled) darkCardBg else lightCardBg)
        cardView.strokeColor = if (isDarkModeEnabled) darkCardStroke else lightCardStroke

        // Package Name
        card.findViewById<TextView>(R.id.tvPackageName).apply {
            text = pkg.getDisplayName()
            setTextColor(if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary)
        }

        // Credits text
        card.findViewById<TextView>(R.id.tvPackageCredits).apply {
            val formatted = NumberFormat.getNumberInstance(Locale.US).format(pkg.credits)
            text = "$formatted Credits"
            setTextColor(if (isDarkModeEnabled) Color.parseColor("#90CAF9") else Color.parseColor("#0D47A1"))
        }

        // Promotional badge & bonus callouts based on package tier
        val tvBadge = card.findViewById<TextView>(R.id.tvPackageBadge)
        val tvSavings = card.findViewById<TextView>(R.id.tvPackageSavings)

        when (pkg.packageId.lowercase()) {
            "starter_50" -> {
                tvBadge.visibility = View.GONE
                tvSavings.visibility = View.VISIBLE
                tvSavings.text = "Standard · 2 credits / ₹1"
                tvSavings.setTextColor(if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary)
            }
            "popular_100" -> {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "MOST POPULAR"
                tvSavings.visibility = View.VISIBLE
                tvSavings.text = "🔥 +20 Bonus Credits (10% extra)"
                tvSavings.setTextColor(Color.parseColor("#E65100"))
            }
            "value_200" -> {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "SUPER VALUE"
                tvSavings.visibility = View.VISIBLE
                tvSavings.text = "⭐ +100 Bonus Credits (25% extra)"
                tvSavings.setTextColor(Color.parseColor("#2E7D32"))
            }
            "pro_500" -> {
                tvBadge.visibility = View.VISIBLE
                tvBadge.text = "BEST DEAL"
                tvSavings.visibility = View.VISIBLE
                tvSavings.text = "🚀 +400 Bonus Credits (40% extra)"
                tvSavings.setTextColor(Color.parseColor("#6A1B9A"))
            }
            else -> {
                tvBadge.visibility = View.GONE
                tvSavings.visibility = View.GONE
            }
        }

        // Price pill
        card.findViewById<TextView>(R.id.tvPackagePrice).apply {
            text = "₹${pkg.priceInRupees}"
            setTextColor(if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary)
        }

        // Tap anywhere on card to select
        cardView.setOnClickListener {
            selectCard(card, pkg)
        }

        packageCardViews.add(card)
        packagesContainer.addView(card)
    }

    /**
     * Updates selection visual state: highlights the tapped card, clears others,
     * stores the selected package, updates price pill background, and enables the Buy Credits button.
     */
    private fun selectCard(selectedView: View, pkg: CreditPackage) {
        selectedPackage = pkg
        btnBuyCredits.isEnabled = true

        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(pkg.credits)
        btnBuyCredits.text = "Buy $formattedCredits Credits for ₹${pkg.priceInRupees}"

        val selectedStroke = if (isDarkModeEnabled) Color.parseColor("#64B5F6") else Color.parseColor("#1B3C53")
        val defaultStroke  = if (isDarkModeEnabled) darkCardStroke else lightCardStroke
        val selectedBg     = if (isDarkModeEnabled) Color.parseColor("#1B2A38") else Color.parseColor("#F4F8FA")
        val defaultBg      = if (isDarkModeEnabled) darkCardBg else lightCardBg

        packageCardViews.forEach { cardRoot ->
            val isSelected = cardRoot === selectedView
            val cardView = cardRoot.findViewById<MaterialCardView>(R.id.cardPackage)
            val dot = cardRoot.findViewById<View>(R.id.selectionDot)
            val priceTv = cardRoot.findViewById<TextView>(R.id.tvPackagePrice)

            cardView.strokeColor = if (isSelected) selectedStroke else defaultStroke
            cardView.strokeWidth = if (isSelected) dp(2) else dp(1)
            cardView.setCardBackgroundColor(if (isSelected) selectedBg else defaultBg)
            cardView.cardElevation = if (isSelected) dp(4).toFloat() else dp(1).toFloat()
            dot?.visibility = if (isSelected) View.VISIBLE else View.GONE

            if (isSelected) {
                priceTv.setBackgroundResource(R.drawable.bg_price_pill_selected)
                priceTv.setTextColor(selectedStroke)
            } else {
                priceTv.setBackgroundResource(R.drawable.bg_price_pill)
                priceTv.setTextColor(if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary)
            }
        }
    }

    // ─── 5. Secure Payment Flow ───────────────────────────────────────────────

    // ─── 5. Secure Payment Flow (Powered by Supabase Edge Functions) ─────────

    /**
     * Step 1: Request order creation from server via Supabase Edge Function create-razorpay-order.
     */
    private fun initiatePayment(pkg: CreditPackage) {
        progressBar.visibility = View.VISIBLE
        setPackageButtonsEnabled(false)

        lifecycleScope.launch(Dispatchers.Main) {
            val token = getActiveAuthToken()
            if (token.isNullOrBlank()) {
                progressBar.visibility = View.GONE
                setPackageButtonsEnabled(true)
                showError("Authentication session expired. Please sign in again.")
                return@launch
            }

            try {
                val orderResult = withContext(Dispatchers.IO) {
                    val jsonBody = JSONObject().apply {
                        put("packageId", pkg.packageId)
                    }.toString()

                    val request = Request.Builder()
                        .url("$edgeFunctionsBaseUrl/create-razorpay-order")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val respBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val errMsg = try {
                                JSONObject(respBody).optString("error", "Server error ${response.code}")
                            } catch (_: Exception) {
                                "Server returned status ${response.code}"
                            }
                            throw Exception(errMsg)
                        }
                        JSONObject(respBody)
                    }
                }

                val orderId = orderResult.getString("orderId")
                val amount = orderResult.getLong("amount")
                val keyId = orderResult.getString("keyId")

                progressBar.visibility = View.GONE
                setPackageButtonsEnabled(true)

                pendingOrderId = orderId
                pendingPackage = pkg

                openRazorpayCheckout(
                    orderId = orderId,
                    amountInPaise = amount,
                    keyId = keyId,
                    userName = getLoggedInName(),
                    userEmail = getLoggedInEmail().orEmpty()
                )
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                setPackageButtonsEnabled(true)
                showError("Could not start payment: ${e.localizedMessage ?: e.message}")
            }
        }
    }

    /**
     * Step 2: Open Razorpay checkout sheet with server-generated order ID and key ID.
     */
    private fun openRazorpayCheckout(
        orderId: String,
        amountInPaise: Long,
        keyId: String,
        userName: String,
        userEmail: String
    ) {
        try {
            val checkout = Checkout()
            checkout.setKeyID(keyId)

            val options = JSONObject().apply {
                put("name", "SkillSwap")
                put("description", "Credits Purchase")
                put("order_id", orderId)
                put("amount", amountInPaise)
                put("currency", "INR")
                put("prefill", JSONObject().apply {
                    if (userEmail.isNotEmpty()) put("email", userEmail)
                    if (userName.isNotEmpty()) put("name", userName)
                })
                put("theme", JSONObject().apply {
                    put("color", "#1B3C53")
                })
            }

            checkout.open(this, options)
        } catch (e: Exception) {
            showError("Could not open payment screen: ${e.localizedMessage}")
            pendingOrderId = null
            pendingPackage = null
        }
    }

    /**
     * Step 3: Razorpay PaymentResultWithDataListener callback.
     *
     * SECURITY RULE: Never add credits directly on the client. Forward paymentId,
     * orderId, and signature to Supabase Edge Function for server-side verification and atomic credit increment.
     */
    override fun onPaymentSuccess(razorpayPaymentId: String?, paymentData: PaymentData?) {
        val resolvedPaymentId = razorpayPaymentId
            ?: paymentData?.paymentId
            ?: paymentData?.data?.optString("razorpay_payment_id")?.takeIf { it.isNotBlank() }

        val orderId = paymentData?.orderId
            ?: paymentData?.data?.optString("razorpay_order_id")?.takeIf { it.isNotBlank() }
            ?: pendingOrderId

        val signature = paymentData?.signature
            ?: paymentData?.data?.optString("razorpay_signature")?.takeIf { it.isNotBlank() }

        if (orderId == null || resolvedPaymentId == null || signature == null) {
            showError(
                "Payment data incomplete. Please contact support if payment was deducted. " +
                "Payment ID: ${resolvedPaymentId ?: "unknown"}"
            )
            pendingOrderId = null
            pendingPackage = null
            return
        }

        verifyPaymentServerSide(orderId, resolvedPaymentId, signature)
    }

    override fun onPaymentError(code: Int, response: String?, paymentData: PaymentData?) {
        pendingOrderId = null
        pendingPackage = null

        when {
            code == 0 || response?.contains("cancel", ignoreCase = true) == true ->
                Toast.makeText(this, "Payment cancelled.", Toast.LENGTH_SHORT).show()
            code == Checkout.NETWORK_ERROR ->
                Toast.makeText(
                    this, "Network error. Please check your connection and try again.",
                    Toast.LENGTH_LONG
                ).show()
            else ->
                Toast.makeText(this, "Payment failed. Please try again.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Step 4: Call verify-razorpay-payment Supabase Edge Function with orderId, paymentId, and signature.
     */
    private fun verifyPaymentServerSide(
        orderId: String,
        paymentId: String,
        signature: String
    ) {
        progressBar.visibility = View.VISIBLE
        setPackageButtonsEnabled(false)

        lifecycleScope.launch(Dispatchers.Main) {
            val token = getActiveAuthToken()
            if (token.isNullOrBlank()) {
                progressBar.visibility = View.GONE
                setPackageButtonsEnabled(true)
                showError("Authentication session expired. Please sign in again.")
                return@launch
            }

            try {
                val verifyResult = withContext(Dispatchers.IO) {
                    val jsonBody = JSONObject().apply {
                        put("razorpayOrderId", orderId)
                        put("razorpayPaymentId", paymentId)
                        put("razorpaySignature", signature)
                    }.toString()

                    val request = Request.Builder()
                        .url("$edgeFunctionsBaseUrl/verify-razorpay-payment")
                        .addHeader("Authorization", "Bearer $token")
                        .addHeader("Content-Type", "application/json")
                        .post(jsonBody.toRequestBody("application/json".toMediaType()))
                        .build()

                    httpClient.newCall(request).execute().use { response ->
                        val respBody = response.body?.string().orEmpty()
                        if (!response.isSuccessful) {
                            val errMsg = try {
                                JSONObject(respBody).optString("error", "Verification error ${response.code}")
                            } catch (_: Exception) {
                                "Verification returned status ${response.code}"
                            }
                            throw Exception(errMsg)
                        }
                        JSONObject(respBody)
                    }
                }

                progressBar.visibility = View.GONE
                setPackageButtonsEnabled(true)

                val creditsAdded = verifyResult.optInt("creditsAdded", 0)
                val newBalance = if (verifyResult.has("newBalance") && !verifyResult.isNull("newBalance")) {
                    verifyResult.getInt("newBalance")
                } else null

                pendingOrderId = null
                pendingPackage = null

                if (newBalance != null) {
                    currentUserCredits = newBalance
                    tvBalanceValue.text = newBalance.toString()
                }

                showPaymentSuccessDialog(creditsAdded, newBalance ?: currentUserCredits)
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                setPackageButtonsEnabled(true)
                pendingOrderId = null
                pendingPackage = null

                showError(
                    "Payment verification failed: ${e.localizedMessage ?: e.message}\n" +
                    "If money was deducted, please contact support with payment ID: $paymentId"
                )
            }
        }
    }

    // ─── Dialogs ─────────────────────────────────────────────────────────────

    private fun showPaymentSuccessDialog(creditsAdded: Int, newBalance: Int) {
        val root = dialogCard()

        root.addView(TextView(this).apply {
            text = "✅"
            textSize = 30f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        })

        root.addView(TextView(this).apply {
            text = "Payment Successful"
            setTextColor(currentTextPrimary())
            textSize = 17f
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        })

        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(creditsAdded)
        val formattedBalance = NumberFormat.getNumberInstance(Locale.US).format(newBalance)

        root.addView(TextView(this).apply {
            text = "$formattedCredits credits added to your balance.\nNew balance: $formattedBalance credits."
            setTextColor(currentTextSecondary())
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
                bottomMargin = dp(18)
            }
        })

        val dialog = AlertDialog.Builder(this).setView(root).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        root.addView(pillButton("Done", lightTextPrimary, Color.parseColor("#F9F3EF")).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { dialog.dismiss() }
        })

        dialog.show()
    }

    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    // ─── UI Helpers ──────────────────────────────────────────────────────────

    private fun setPackageButtonsEnabled(enabled: Boolean) {
        btnBuyCredits.isEnabled = enabled && selectedPackage != null
        packageCardViews.forEach { cardRoot ->
            cardRoot.findViewById<MaterialCardView>(R.id.cardPackage)?.isClickable = enabled
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun currentTextPrimary(): Int =
        if (isDarkModeEnabled) darkTextPrimary else lightTextPrimary

    private fun currentTextSecondary(): Int =
        if (isDarkModeEnabled) darkTextSecondary else lightTextSecondary

    private fun currentDialogBg(): Int =
        if (isDarkModeEnabled) darkCardBg else Color.WHITE

    private fun dialogCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(20))
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(currentDialogBg())
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
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                cornerRadius = dp(24).toFloat()
                setColor(bgColor)
            }
            isClickable = true
            isFocusable = true
        }
    }
}
