package com.example.sgp

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import java.text.NumberFormat
import java.util.Locale

class CheckoutSuccessActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PACKAGE_NAME = "extra_package_name"
        const val EXTRA_CREDITS_ADDED = "extra_credits_added"
        const val EXTRA_NEW_BALANCE = "extra_new_balance"
        const val EXTRA_TRANSACTION_ID = "extra_transaction_id"

        fun start(
            context: Context,
            packageName: String,
            creditsAdded: Int,
            newBalance: Int,
            transactionId: String
        ) {
            val intent = Intent(context, CheckoutSuccessActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE_NAME, packageName)
                putExtra(EXTRA_CREDITS_ADDED, creditsAdded)
                putExtra(EXTRA_NEW_BALANCE, newBalance)
                putExtra(EXTRA_TRANSACTION_ID, transactionId)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout_success)

        val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: "Credits Package"
        val creditsAdded = intent.getIntExtra(EXTRA_CREDITS_ADDED, 0)
        val newBalance = intent.getIntExtra(EXTRA_NEW_BALANCE, 0)
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID).orEmpty()

        val prefs = getSharedPreferences(SettingsActivity.PREFS_NAME, Context.MODE_PRIVATE)
        val isDarkMode = prefs.getBoolean(SettingsActivity.KEY_DARK_MODE, false)
        applyTheme(isDarkMode)

        findViewById<TextView>(R.id.tvSuccessPackageName).text = packageName

        val formattedAdded = NumberFormat.getNumberInstance(Locale.US).format(creditsAdded)
        findViewById<TextView>(R.id.tvSuccessCreditsAdded).text = "+$formattedAdded Credits"

        val formattedBalance = NumberFormat.getNumberInstance(Locale.US).format(newBalance)
        findViewById<TextView>(R.id.tvSuccessUpdatedBalance).text = "$formattedBalance Credits"

        val tvTxId = findViewById<TextView>(R.id.tvSuccessTransactionId)
        val layoutTxRow = findViewById<View>(R.id.layoutTxIdRow)
        if (transactionId.isNotBlank()) {
            tvTxId.text = transactionId
            layoutTxRow.visibility = View.VISIBLE
        } else {
            layoutTxRow.visibility = View.GONE
        }

        findViewById<MaterialButton>(R.id.btnBackToCreditStore).setOnClickListener {
            finish()
        }

        findViewById<MaterialButton>(R.id.btnContinueLearning).setOnClickListener {
            val homeIntent = Intent(this, Home::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            startActivity(homeIntent)
            finish()
        }
    }

    private fun applyTheme(isDark: Boolean) {
        val root = findViewById<View>(R.id.successRootLayout)
        val cardReceipt = findViewById<MaterialCardView>(R.id.cardReceipt)
        val cardTx = findViewById<MaterialCardView>(R.id.cardTransactionDetails)
        val tvTitle = findViewById<TextView>(R.id.tvSuccessTitle)
        val tvSubtitle = findViewById<TextView>(R.id.tvSuccessSubtitle)
        val tvPkg = findViewById<TextView>(R.id.tvSuccessPackageName)
        val tvBal = findViewById<TextView>(R.id.tvSuccessUpdatedBalance)
        val tvCategoryLabel = findViewById<TextView>(R.id.tvPackageCategoryLabel)
        val tvBalanceLabel = findViewById<TextView>(R.id.tvBalanceLabel)
        val divider = findViewById<View>(R.id.dividerReceipt)
        val tvTxTitle = findViewById<TextView>(R.id.tvTxSectionTitle)
        val tvTxIdLabel = findViewById<TextView>(R.id.tvTxIdLabel)
        val tvTxId = findViewById<TextView>(R.id.tvSuccessTransactionId)
        val ivTxIcon = findViewById<ImageView>(R.id.ivTxInfoIcon)
        val btnContinue = findViewById<MaterialButton>(R.id.btnContinueLearning)
        val layoutCreditsPill = findViewById<LinearLayout>(R.id.layoutCreditsAddedPill)

        if (isDark) {
            root.setBackgroundColor(Color.parseColor("#0F1B24"))
            cardReceipt.setCardBackgroundColor(Color.parseColor("#152430"))
            cardReceipt.strokeColor = Color.parseColor("#2A3B48")
            cardTx.setCardBackgroundColor(Color.parseColor("#111F2B"))
            cardTx.strokeColor = Color.parseColor("#2A3B48")

            tvTitle.setTextColor(Color.parseColor("#F5F7F8"))
            tvSubtitle.setTextColor(Color.parseColor("#9BA8B0"))
            tvPkg.setTextColor(Color.parseColor("#F5F7F8"))
            tvBal.setTextColor(Color.parseColor("#F5F7F8"))
            tvCategoryLabel.setTextColor(Color.parseColor("#64B5F6"))
            tvBalanceLabel.setTextColor(Color.parseColor("#9BA8B0"))
            divider.setBackgroundColor(Color.parseColor("#2A3B48"))

            tvTxTitle.setTextColor(Color.parseColor("#9BA8B0"))
            tvTxIdLabel.setTextColor(Color.parseColor("#6A7C89"))
            tvTxId.setTextColor(Color.parseColor("#9BA8B0"))
            ivTxIcon.setColorFilter(Color.parseColor("#9BA8B0"))

            btnContinue.setTextColor(Color.parseColor("#64B5F6"))
            btnContinue.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#64B5F6"))
            layoutCreditsPill.setBackgroundColor(Color.parseColor("#0E2E1B"))
        } else {
            root.setBackgroundColor(Color.parseColor("#EAF1F5"))
            cardReceipt.setCardBackgroundColor(Color.WHITE)
            cardReceipt.strokeColor = Color.parseColor("#D2C1B6")
            cardTx.setCardBackgroundColor(Color.parseColor("#F4F8FA"))
            cardTx.strokeColor = Color.parseColor("#D2C1B6")

            tvTitle.setTextColor(Color.parseColor("#1B3C53"))
            tvSubtitle.setTextColor(Color.parseColor("#456882"))
            tvPkg.setTextColor(Color.parseColor("#1B3C53"))
            tvBal.setTextColor(Color.parseColor("#1B3C53"))
            tvCategoryLabel.setTextColor(Color.parseColor("#456882"))
            tvBalanceLabel.setTextColor(Color.parseColor("#456882"))
            divider.setBackgroundColor(Color.parseColor("#E8EEF2"))

            tvTxTitle.setTextColor(Color.parseColor("#456882"))
            tvTxIdLabel.setTextColor(Color.parseColor("#456882"))
            tvTxId.setTextColor(Color.parseColor("#456882"))
            ivTxIcon.setColorFilter(Color.parseColor("#456882"))

            btnContinue.setTextColor(Color.parseColor("#1B3C53"))
            btnContinue.strokeColor = android.content.res.ColorStateList.valueOf(Color.parseColor("#1B3C53"))
        }

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = !isDark
    }
}
