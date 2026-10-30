package com.example.sgp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.UUID

class CheckoutActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_ID = "extra_package_id"
        const val EXTRA_PREVIEW_NAME = "extra_preview_name"
        const val EXTRA_PREVIEW_PRICE = "extra_preview_price"
        const val EXTRA_PREVIEW_CREDITS = "extra_preview_credits"

        fun start(
            context: Context,
            packageId: String,
            previewName: String,
            previewPrice: Int,
            previewCredits: Int
        ) {
            val intent = Intent(context, CheckoutActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_ID, packageId)
                putExtra(EXTRA_PREVIEW_NAME, previewName)
                putExtra(EXTRA_PREVIEW_PRICE, previewPrice)
                putExtra(EXTRA_PREVIEW_CREDITS, previewCredits)
            }
            context.startActivity(intent)
        }
    }

    private lateinit var cardOrderSummary: MaterialCardView
    private lateinit var cardMethodUpi: MaterialCardView
    private lateinit var cardMethodCard: MaterialCardView
    private lateinit var dotMethodUpi: View
    private lateinit var dotMethodCard: View
    private lateinit var btnSimulatePayment: MaterialButton
    private lateinit var layoutProcessing: LinearLayout
    private lateinit var tvCheckoutPackageName: TextView
    private lateinit var tvCheckoutPackageCredits: TextView
    private lateinit var tvCheckoutPackagePrice: TextView

    private var packageId: String = ""
    private var packageName: String = ""
    private var packagePrice: Int = 0
    private var packageCredits: Int = 0

    // Selected simulation payment method
    private var selectedMethod: String? = null // "UPI_SIMULATED" or "CARD_SIMULATED"
    private var isProcessing = false
    private var isDarkMode = false

    private val paymentService: PaymentGatewayService by lazy {
        DemoPaymentGatewayService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)

        packageId = intent.getStringExtra(EXTRA_PACKAGE_ID).orEmpty()
        packageName = intent.getStringExtra(EXTRA_PREVIEW_NAME).orEmpty()
        packagePrice = intent.getIntExtra(EXTRA_PREVIEW_PRICE, 0)
        packageCredits = intent.getIntExtra(EXTRA_PREVIEW_CREDITS, 0)

        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        isDarkMode = prefs.getBoolean(SettingsActivity.KEY_DARK_MODE, false)

        bindViews()
        setupToolbar()
        applyTheme(isDarkMode)
        displayPackageInfo()
        loadAuthoritativePackageFromFirestore()
    }

    private fun bindViews() {
        cardOrderSummary = findViewById(R.id.cardOrderSummary)
        cardMethodUpi = findViewById(R.id.cardMethodUpi)
        cardMethodCard = findViewById(R.id.cardMethodCard)
        dotMethodUpi = findViewById(R.id.dotMethodUpi)
        dotMethodCard = findViewById(R.id.dotMethodCard)
        btnSimulatePayment = findViewById(R.id.btnSimulatePayment)
        layoutProcessing = findViewById(R.id.layoutProcessing)
        tvCheckoutPackageName = findViewById(R.id.tvCheckoutPackageName)
        tvCheckoutPackageCredits = findViewById(R.id.tvCheckoutPackageCredits)
        tvCheckoutPackagePrice = findViewById(R.id.tvCheckoutPackagePrice)

        cardMethodUpi.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            selectPaymentMethod("UPI_SIMULATED")
        }

        cardMethodCard.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            selectPaymentMethod("CARD_SIMULATED")
        }

        btnSimulatePayment.setOnClickListener {
            if (isProcessing) return@setOnClickListener
            val method = selectedMethod ?: return@setOnClickListener
            processDemoPayment(method)
        }
    }

    private fun setupToolbar() {
        val toolbar: androidx.appcompat.widget.Toolbar = findViewById(R.id.toolbarCheckout)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = "Complete Your Purchase"
            setDisplayHomeAsUpEnabled(true)
        }
        toolbar.setNavigationIcon(R.drawable.outline_arrow_back_24)
        toolbar.navigationIcon?.setTint(Color.parseColor("#F9F3EF"))
        toolbar.setNavigationOnClickListener { finish() }
    }

    private fun displayPackageInfo() {
        tvCheckoutPackageName.text = packageName.ifBlank { "Credit Package" }
        val formattedCredits = NumberFormat.getNumberInstance(Locale.US).format(packageCredits)
        tvCheckoutPackageCredits.text = "+$formattedCredits Credits"
        tvCheckoutPackagePrice.text = "₹$packagePrice"
        updateSimulateButtonLabel()
    }

    /**
     * Re-reads authoritative package info from Firestore to guarantee UI consistency.
     */
    private fun loadAuthoritativePackageFromFirestore() {
        if (packageId.isBlank()) return
        FirebaseFirestore.getInstance().collection("creditPackages").document(packageId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    val authName = doc.getString("name")
                    val authPrice = doc.getLong("priceInRupees")?.toInt()
                    val authCredits = doc.getLong("credits")?.toInt()

                    if (!authName.isNullOrBlank()) packageName = authName
                    if (authPrice != null && authPrice > 0) packagePrice = authPrice
                    if (authCredits != null && authCredits > 0) packageCredits = authCredits

                    displayPackageInfo()
                }
            }
            .addOnFailureListener {
                // If offline, continues with passed-in package details
            }
    }

    private fun selectPaymentMethod(method: String) {
        selectedMethod = method

        val selectedStroke = if (isDarkMode) Color.parseColor("#64B5F6") else Color.parseColor("#1B3C53")
        val defaultStroke = if (isDarkMode) Color.parseColor("#2A3B48") else Color.parseColor("#D2C1B6")
        val selectedBg = if (isDarkMode) Color.parseColor("#1B2A38") else Color.parseColor("#F4F8FA")
        val defaultBg = if (isDarkMode) Color.parseColor("#152430") else Color.WHITE

        if (method == "UPI_SIMULATED") {
            cardMethodUpi.strokeColor = selectedStroke
            cardMethodUpi.setCardBackgroundColor(selectedBg)
            dotMethodUpi.visibility = View.VISIBLE

            cardMethodCard.strokeColor = defaultStroke
            cardMethodCard.setCardBackgroundColor(defaultBg)
            dotMethodCard.visibility = View.GONE
        } else {
            cardMethodCard.strokeColor = selectedStroke
            cardMethodCard.setCardBackgroundColor(selectedBg)
            dotMethodCard.visibility = View.VISIBLE

            cardMethodUpi.strokeColor = defaultStroke
            cardMethodUpi.setCardBackgroundColor(defaultBg)
            dotMethodUpi.visibility = View.GONE
        }

        btnSimulatePayment.isEnabled = true
        updateSimulateButtonLabel()
    }

    private fun updateSimulateButtonLabel() {
        if (selectedMethod == null) {
            btnSimulatePayment.text = "Select a Payment Method"
            btnSimulatePayment.isEnabled = false
        } else {
            btnSimulatePayment.text = "Simulate Payment — ₹$packagePrice"
            btnSimulatePayment.isEnabled = !isProcessing
        }
    }

    private fun processDemoPayment(method: String) {
        isProcessing = true
        btnSimulatePayment.isEnabled = false
        layoutProcessing.visibility = View.VISIBLE

        // Client idempotency key: avoids duplicate processing on network retries
        val clientTransactionId = "client_${System.currentTimeMillis()}_${UUID.randomUUID().toString().substring(0, 8)}"

        lifecycleScope.launch {
            val result = paymentService.executePayment(
                context = this@CheckoutActivity,
                packageId = packageId,
                paymentMethod = method,
                clientTransactionId = clientTransactionId
            )

            isProcessing = false
            layoutProcessing.visibility = View.GONE
            btnSimulatePayment.isEnabled = true

            if (result.success) {
                // Navigate to Success Activity
                CheckoutSuccessActivity.start(
                    context = this@CheckoutActivity,
                    packageName = result.packageName ?: packageName,
                    creditsAdded = if (result.creditsAdded > 0) result.creditsAdded else packageCredits,
                    newBalance = result.newBalance ?: 0,
                    transactionId = result.transactionId ?: clientTransactionId
                )
                finish()
            } else {
                val error = result.errorMessage ?: "Transaction could not be completed."
                Toast.makeText(this@CheckoutActivity, error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun applyTheme(isDark: Boolean) {
        val root = findViewById<View>(R.id.checkoutRootLayout)
        val textPrimary = if (isDark) Color.parseColor("#F5F7F8") else Color.parseColor("#1B3C53")
        val textSecondary = if (isDark) Color.parseColor("#9BA8B0") else Color.parseColor("#456882")
        val cardBg = if (isDark) Color.parseColor("#152430") else Color.WHITE
        val cardStroke = if (isDark) Color.parseColor("#2A3B48") else Color.parseColor("#D2C1B6")

        root.setBackgroundColor(if (isDark) Color.parseColor("#0F1B24") else Color.parseColor("#EAF1F5"))

        cardOrderSummary.setCardBackgroundColor(cardBg)
        cardOrderSummary.strokeColor = cardStroke
        cardMethodUpi.setCardBackgroundColor(cardBg)
        cardMethodUpi.strokeColor = cardStroke
        cardMethodCard.setCardBackgroundColor(cardBg)
        cardMethodCard.strokeColor = cardStroke

        findViewById<TextView>(R.id.tvPackageSummaryHeader).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvCheckoutPackageName).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvCheckoutPackagePrice).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvCheckoutDescription).setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvPaymentMethodHeader).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvPaymentMethodSub).setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvTitleUpi).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvSubUpi).setTextColor(textSecondary)
        findViewById<TextView>(R.id.tvTitleCard).setTextColor(textPrimary)
        findViewById<TextView>(R.id.tvSubCard).setTextColor(textSecondary)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
    }
}
